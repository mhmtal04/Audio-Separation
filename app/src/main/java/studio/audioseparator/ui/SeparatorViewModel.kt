package studio.audioseparator.ui

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import studio.audioseparator.audio.AudioDecoder
import studio.audioseparator.audio.AudioInfo
import studio.audioseparator.audio.DemucsEngine
import studio.audioseparator.audio.ModelStore
import studio.audioseparator.audio.Resampler
import studio.audioseparator.playback.StemPlayer

data class UiState(
    val modelReady: Boolean = false,
    val modelBusy: Boolean = false,
    val modelBundled: Boolean = false,
    val uri: Uri? = null,
    val info: AudioInfo? = null,
    val running: Boolean = false,
    val stage: String = "",
    val progress: Float = 0f,
    val stems: Map<String, File>? = null,
    val message: String? = null,
)

class SeparatorViewModel(app: Application) : AndroidViewModel(app) {
    var ui by mutableStateOf(UiState(modelReady = ModelStore.isInstalled(app)))
        private set
    var playing by mutableStateOf(false)
        private set
    var positionFrames by mutableLongStateOf(0L)
        private set
    var totalFrames by mutableLongStateOf(0L)
        private set
    val volumes = mutableStateMapOf<String, Float>()
    val muted = mutableStateMapOf<String, Boolean>()
    val soloed = mutableStateMapOf<String, Boolean>()

    private var player: StemPlayer? = null
    private var job: Job? = null
    private var pendingStem: String? = null
    private val ctx get() = getApplication<Application>()
    private val marker get() = File(ctx.filesDir, "running.marker")

    init {
        val bundled = ModelStore.hasBundled(app)
        ui = ui.copy(
            modelBundled = bundled,
            modelBusy = bundled && !ModelStore.isInstalled(app),
            message = previousSessionNote(),
        )
        if (bundled) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    ModelStore.installFromAssets(ctx)
                    ui = ui.copy(modelBusy = false, modelReady = true)
                } catch (e: Throwable) {
                    ui = ui.copy(modelBusy = false, message = "Model hazırlanamadı: ${e.message}")
                }
            }
        }
    }

    /** Önceki oturum çöktüyse nedenini toplar (JVM istisnası, işletim sistemi çıkış nedeni, RAM). */
    private fun previousSessionNote(): String? {
        val sb = StringBuilder()
        val crash = File(ctx.cacheDir, "last_crash.txt")
        if (crash.exists()) {
            sb.append("Önceki hata:\n").append(crash.readText().take(700)).append('\n')
            crash.delete()
        }
        if (marker.exists()) {
            marker.delete()
            sb.append("Önceki ayrıştırma, uygulama kapanarak sona erdi (büyük olasılıkla bellek yetersizliği).\n")
            if (Build.VERSION.SDK_INT >= 30) {
                val am = ctx.getSystemService(ActivityManager::class.java)
                am.getHistoricalProcessExitReasons(null, 0, 1).firstOrNull()?.let {
                    sb.append("Sistem çıkış nedeni kodu: ${it.reason} (3=düşük bellek, 4=JVM çökmesi, 5=yerel çökme) ${it.description ?: ""}\n")
                }
            }
            val mi = ActivityManager.MemoryInfo()
            ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
            sb.append("Cihaz RAM: %.1f GB, Android sürümü API %d".format(mi.totalMem / 1e9, Build.VERSION.SDK_INT))
        }
        return sb.toString().ifBlank { null }
    }

    private fun displayName(uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment ?: "audio"
    }

    fun baseName(): String = (ui.info?.name ?: "audio").substringBeforeLast('.')

    fun onAudioPicked(uri: Uri) {
        releasePlayer()
        ui = ui.copy(uri = uri, info = null, stems = null, message = null, progress = 0f)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = AudioDecoder.probe(ctx, uri, displayName(uri))
                ui = ui.copy(info = info)
            } catch (e: Throwable) {
                ui = ui.copy(uri = null, message = "Dosya okunamadı: ${e.message}")
            }
        }
    }

    fun importModel(uri: Uri) {
        ui = ui.copy(modelBusy = true, message = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ModelStore.import(ctx, uri)
                ui = ui.copy(modelBusy = false, modelReady = true, message = "Model yüklendi.")
            } catch (e: Throwable) {
                ui = ui.copy(modelBusy = false, message = "Model içe aktarılamadı: ${e.message}")
            }
        }
    }

    fun cancel() { job?.cancel() }

    fun startSeparation() {
        val uri = ui.uri ?: return
        if (ui.running) return
        releasePlayer()
        val root = File(ctx.filesDir, "projects")
        root.deleteRecursively()
        val outDir = File(root, System.currentTimeMillis().toString())
        ui = ui.copy(running = true, stems = null, message = null, stage = "Ses çözülüyor", progress = 0f)
        marker.createNewFile()

        job = viewModelScope.launch(Dispatchers.Default) {
            val cancelled = { !isActive }
            try {
                val dec = AudioDecoder.decode(ctx, uri, { p -> ui = ui.copy(progress = p * 0.10f) }, cancelled)
                var pcm = dec.samples
                if (dec.sampleRate != DemucsEngine.SR) {
                    ui = ui.copy(stage = "Yeniden örnekleniyor (${dec.sampleRate} → 44100 Hz)")
                    pcm = Resampler.resample(pcm, dec.sampleRate, DemucsEngine.SR,
                        { p -> ui = ui.copy(progress = 0.10f + p * 0.05f) }, cancelled)
                }
                ui = ui.copy(stage = "Model yükleniyor", progress = 0.15f)
                val stems = DemucsEngine(ModelStore.file(ctx)).use { engine ->
                    ui = ui.copy(stage = "Yapay zekâ ayrıştırıyor")
                    engine.separate(pcm, outDir,
                        { p -> ui = ui.copy(progress = 0.15f + p * 0.85f) }, cancelled)
                }
                val p = StemPlayer(stems) { playing = false; positionFrames = 0 }
                player = p
                totalFrames = p.totalFrames
                positionFrames = 0
                stems.keys.forEach { volumes[it] = 1f; muted[it] = false; soloed[it] = false; p.volume[it] = 1f }
                ui = ui.copy(running = false, stems = stems, stage = "", progress = 1f)
            } catch (e: CancellationException) {
                outDir.deleteRecursively()
                ui = ui.copy(running = false, stage = "", message = "İşlem iptal edildi.")
            } catch (e: OutOfMemoryError) {
                outDir.deleteRecursively()
                ui = ui.copy(running = false, stage = "",
                    message = "Bellek yetersiz. Dosya çok uzun ya da cihazın RAM'i bu model için az.")
            } catch (e: Throwable) {
                outDir.deleteRecursively()
                ui = ui.copy(running = false, stage = "", message = "Hata: ${e.message}")
            } finally {
                marker.delete()
            }
        }
    }

    // ---- oynatma ----
    fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause(); playing = false
        } else {
            p.play(); playing = true
            viewModelScope.launch {
                while (p.isPlaying) { positionFrames = p.positionFrames; delay(100) }
                positionFrames = p.positionFrames
                playing = false
            }
        }
    }

    fun seek(fraction: Float) {
        val p = player ?: return
        p.seek((fraction * p.totalFrames).toLong())
        positionFrames = p.positionFrames
    }

    fun setVolume(stem: String, v: Float) { volumes[stem] = v; player?.volume?.put(stem, v) }

    fun toggleMute(stem: String) {
        val m = !(muted[stem] ?: false)
        muted[stem] = m
        player?.let { if (m) it.muted.add(stem) else it.muted.remove(stem) }
    }

    fun toggleSolo(stem: String) {
        val s = !(soloed[stem] ?: false)
        soloed[stem] = s
        player?.let { if (s) it.solo.add(stem) else it.solo.remove(stem) }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        playing = false
        positionFrames = 0
        totalFrames = 0
    }

    // ---- dışa aktarma (WAV / ZIP) ----
    fun requestExport(stem: String) { pendingStem = stem }

    fun exportPendingTo(uri: Uri) {
        val f = pendingStem?.let { ui.stems?.get(it) } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ctx.contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o, 1 shl 16) } }
                ui = ui.copy(message = "WAV kaydedildi.")
            } catch (e: Throwable) {
                ui = ui.copy(message = "Dışa aktarma hatası: ${e.message}")
            }
        }
    }

    fun exportZipTo(uri: Uri) {
        val stems = ui.stems ?: return
        val base = baseName()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ZipOutputStream(ctx.contentResolver.openOutputStream(uri)!!.buffered()).use { z ->
                    stems.forEach { (name, f) ->
                        z.putNextEntry(ZipEntry("${base}_$name.wav"))
                        f.inputStream().use { it.copyTo(z, 1 shl 16) }
                        z.closeEntry()
                    }
                }
                ui = ui.copy(message = "ZIP kaydedildi.")
            } catch (e: Throwable) {
                ui = ui.copy(message = "Dışa aktarma hatası: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
        player?.release()
    }
}
