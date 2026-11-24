package studio.audioseparator.ui

import android.app.ActivityManager
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.audioseparator.audio.AudioDecoder
import studio.audioseparator.audio.AudioInfo
import studio.audioseparator.audio.DemucsEngine
import studio.audioseparator.audio.MixRenderer
import studio.audioseparator.audio.ModelStore
import studio.audioseparator.audio.Resampler
import studio.audioseparator.data.ArchiveItem
import studio.audioseparator.data.ArchiveStore
import studio.audioseparator.data.Project
import studio.audioseparator.data.ProjectStore
import studio.audioseparator.data.Waveform
import studio.audioseparator.playback.StemPlayer
import studio.audioseparator.service.ProcessingService

enum class Screen { Home, Separate, Results, Player, Projects, Archive, Export }

data class UiState(
    val modelReady: Boolean = false,
    val modelBusy: Boolean = false,
    val modelBundled: Boolean = false,
    val uri: Uri? = null,
    val info: AudioInfo? = null,
    val cover: Bitmap? = null,
    val running: Boolean = false,
    val stage: String = "",
    val progress: Float = 0f,
    val etaSec: Long? = null,
    val sourceWave: FloatArray? = null,
    val rendering: Boolean = false,
    val message: String? = null,
)

class SeparatorViewModel(app: Application) : AndroidViewModel(app) {
    var ui by mutableStateOf(UiState(modelReady = ModelStore.isInstalled(app)))
        private set
    var screen by mutableStateOf(Screen.Home)
        private set
    private val backStack = ArrayDeque<Screen>()

    val projects = mutableStateListOf<Project>()
    val archive = mutableStateListOf<ArchiveItem>()
    var current by mutableStateOf<Project?>(null)
        private set
    var usedBytes by mutableLongStateOf(0L)
        private set
    var freeBytes by mutableLongStateOf(0L)
        private set
    val waves = mutableStateMapOf<String, FloatArray>()
    private val waveLoading = ConcurrentHashMap.newKeySet<String>()

    var exportTab by mutableIntStateOf(0)

    // oynatma
    var playing by mutableStateOf(false)
        private set
    var positionFrames by mutableLongStateOf(0L)
        private set
    var totalFrames by mutableLongStateOf(0L)
        private set
    var soloStem by mutableStateOf<String?>(null)
        private set
    var loadedKey by mutableStateOf<String?>(null)
        private set
    val volumes = mutableStateMapOf<String, Float>()
    val muted = mutableStateMapOf<String, Boolean>()

    private var player: StemPlayer? = null
    private var job: Job? = null
    private var pendingSave: File? = null
    private val ctx get() = getApplication<Application>()
    private val marker get() = File(ctx.filesDir, "running.marker")

    init {
        val bundled = ModelStore.hasBundled(app)
        ui = ui.copy(
            modelBundled = bundled,
            modelBusy = bundled && !ModelStore.isInstalled(app),
            message = previousSessionNote(),
        )
        refreshLists()
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
            sb.append("Cihaz RAM: %.1f GB, API %d".format(mi.totalMem / 1e9, Build.VERSION.SDK_INT))
        }
        return sb.toString().ifBlank { null }
    }

    fun clearMessage() { ui = ui.copy(message = null) }

    // ---- gezinme ----
    fun navigate(to: Screen) {
        if (to == screen) return
        backStack.addLast(screen)
        screen = to
    }

    fun goTab(to: Screen) {
        backStack.clear()
        screen = to
        if (to == Screen.Projects || to == Screen.Archive) refreshLists()
    }

    fun back(): Boolean {
        if (backStack.isEmpty()) return false
        screen = backStack.removeLast()
        return true
    }

    // ---- listeler ----
    fun refreshLists() {
        val ps = ProjectStore.list(ctx)
        projects.clear(); projects.addAll(ps)
        val ar = ArchiveStore.list(ctx)
        archive.clear(); archive.addAll(ar)
        usedBytes = ps.sumOf { it.sizeBytes } + ar.sumOf { it.sizeBytes }
        freeBytes = ctx.filesDir.usableSpace
    }

    fun ensureWave(file: File) {
        val k = file.path
        if (waves.containsKey(k) || !waveLoading.add(k)) return
        viewModelScope.launch(Dispatchers.IO) {
            try { waves[k] = Waveform.fromWav(file, 96) } catch (_: Throwable) {}
            waveLoading.remove(k)
        }
    }

    // ---- ses seçme ----
    private fun displayName(uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment ?: "audio"
    }

    fun onAudioPicked(uri: Uri) {
        if (ui.running) return
        ui = ui.copy(uri = uri, info = null, cover = null, sourceWave = null, progress = 0f, message = null)
        navigate(Screen.Separate)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = AudioDecoder.probe(ctx, uri, displayName(uri))
                val bmp = info.cover?.let { decodeSampled(it, 512) }
                ui = ui.copy(info = info, cover = bmp)
            } catch (e: Throwable) {
                ui = ui.copy(uri = null, message = "Dosya okunamadı: ${e.message}")
                withContext(Dispatchers.Main) { back() }
            }
        }
    }

    // ---- ayrıştırma ----
    fun cancel() { job?.cancel() }

    fun startSeparation() {
        val uri = ui.uri ?: return
        val info = ui.info ?: return
        if (ui.running || !ui.modelReady) return
        ui = ui.copy(running = true, stage = "Ses çözülüyor", progress = 0f, etaSec = null, sourceWave = null, message = null)
        marker.createNewFile()
        ProcessingService.start(ctx)
        releasePlayer()
        val createdAt = System.currentTimeMillis()
        val outDir = File(ProjectStore.root(ctx), createdAt.toString())
        var lastPct = -1
        var sepStart = 0L

        fun prog(p: Float) {
            var eta: Long? = null
            if (sepStart > 0 && p > 0.15f) {
                val frac = (p - 0.15f) / 0.85f
                if (frac > 0.0005f) eta = ((SystemClock.elapsedRealtime() - sepStart) / 1000f * (1 - frac) / frac).toLong()
            }
            ui = ui.copy(progress = p, etaSec = eta ?: ui.etaSec)
            val pct = (p * 100).toInt()
            if (pct != lastPct) { lastPct = pct; ProcessingService.update(ctx, pct, ui.stage) }
        }

        job = viewModelScope.launch(Dispatchers.Default) {
            val cancelled = { !isActive }
            try {
                val dec = AudioDecoder.decode(ctx, uri, { prog(it * 0.10f) }, cancelled)
                var pcm = dec.samples
                ui = ui.copy(sourceWave = Waveform.fromPcm(pcm, 96))
                if (dec.sampleRate != DemucsEngine.SR) {
                    ui = ui.copy(stage = "Yeniden örnekleniyor")
                    pcm = Resampler.resample(pcm, dec.sampleRate, DemucsEngine.SR, { prog(0.10f + it * 0.05f) }, cancelled)
                }
                ui = ui.copy(stage = "Model yükleniyor", progress = 0.15f)
                DemucsEngine(ModelStore.file(ctx)).use { engine ->
                    ui = ui.copy(stage = "Yapay zekâ ayrıştırıyor")
                    sepStart = SystemClock.elapsedRealtime()
                    engine.separate(pcm, outDir, { prog(0.15f + it * 0.85f) }, cancelled)
                }
                ui = ui.copy(stage = "Kaydediliyor")
                val durMs = pcm.size / 2 * 1000L / DemucsEngine.SR
                ProjectStore.writeMeta(outDir, info.name.substringBeforeLast('.'), createdAt, durMs, info.format)
                info.cover?.let { File(outDir, "cover.bin").writeBytes(it) }
                val proj = ProjectStore.load(outDir) ?: throw IllegalStateException("Proje kaydedilemedi.")
                withContext(Dispatchers.Main) {
                    refreshLists()
                    openProject(proj, null)
                    backStack.clear(); backStack.addLast(Screen.Home); screen = Screen.Results
                    ui = ui.copy(running = false, stage = "", progress = 1f)
                }
            } catch (e: CancellationException) {
                outDir.deleteRecursively()
                ui = ui.copy(running = false, stage = "", message = "İşlem iptal edildi.")
            } catch (e: OutOfMemoryError) {
                outDir.deleteRecursively()
                ui = ui.copy(running = false, stage = "", message = "Bellek yetersiz. Dosya çok uzun ya da cihazın RAM'i bu model için az.")
            } catch (e: Throwable) {
                outDir.deleteRecursively()
                ui = ui.copy(running = false, stage = "", message = "Hata: ${e.message}")
            } finally {
                marker.delete()
                ProcessingService.stop(ctx)
            }
        }
    }

    // ---- projeler ----
    fun openProject(p: Project, target: Screen?, autoplay: Boolean = false) {
        current = p
        ensurePlayer("p:${p.id}", p.stems)
        player?.solo?.clear(); soloStem = null
        if (target != null) navigate(target)
        if (autoplay) startPlayback()
    }

    fun deleteProject(p: Project) {
        if (loadedKey == "p:${p.id}") releasePlayer()
        if (current?.id == p.id) current = null
        p.dir.deleteRecursively()
        refreshLists()
    }

    fun deleteArchive(a: ArchiveItem) {
        if (loadedKey == "a:${a.id}") releasePlayer()
        ArchiveStore.delete(a)
        refreshLists()
    }

    // ---- oynatma ----
    private fun ensurePlayer(key: String, files: Map<String, File>) {
        if (loadedKey == key && player != null) return
        releasePlayer()
        val p = StemPlayer(files) { playing = false; positionFrames = 0; soloStem = null }
        player = p
        loadedKey = key
        totalFrames = p.totalFrames
        files.keys.forEach { volumes[it] = 1f; muted[it] = false; p.volume[it] = 1f }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        loadedKey = null
        playing = false
        positionFrames = 0
        totalFrames = 0
        soloStem = null
        volumes.clear(); muted.clear()
    }

    private fun startPlayback() {
        val p = player ?: return
        p.play()
        playing = true
        viewModelScope.launch {
            while (p.isPlaying) { positionFrames = p.positionFrames; delay(100) }
            positionFrames = p.positionFrames
            if (player === p) playing = false
        }
    }

    fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) { p.pause(); playing = false } else startPlayback()
    }

    fun playMix() {
        val p = player ?: return
        p.solo.clear(); soloStem = null
        if (!p.isPlaying) startPlayback()
    }

    fun playStem(stem: String) {
        val p = player ?: return
        if (p.isPlaying && soloStem == stem) { p.pause(); playing = false; return }
        p.solo.clear(); p.solo.add(stem); soloStem = stem
        if (!p.isPlaying) startPlayback()
    }

    fun playArchive(a: ArchiveItem) {
        val key = "a:${a.id}"
        if (loadedKey == key) { togglePlay(); return }
        ensurePlayer(key, mapOf("mix" to a.file))
        startPlayback()
    }

    fun seek(fraction: Float) {
        val p = player ?: return
        p.seek((fraction * p.totalFrames).toLong())
        positionFrames = p.positionFrames
    }

    fun seekBy(seconds: Int) {
        val p = player ?: return
        p.seek(p.positionFrames + seconds * 44100L)
        positionFrames = p.positionFrames
    }

    fun setVolume(stem: String, v: Float) { volumes[stem] = v; player?.volume?.put(stem, v) }

    fun setEnabled(stem: String, on: Boolean) {
        muted[stem] = !on
        player?.let { if (on) it.muted.remove(stem) else it.muted.add(stem) }
    }

    fun applyPreset(kind: String) {
        val names = current?.stems?.keys ?: return
        names.forEach { n ->
            val on = when (kind) {
                "karaoke" -> n != "vocals"
                "acapella" -> n == "vocals"
                "nodrums" -> n != "drums"
                else -> true
            }
            setEnabled(n, on)
            setVolume(n, 1f)
        }
    }

    // ---- dışa aktarma ----
    fun prepareSave(file: File) { pendingSave = file }

    fun exportSaveTo(uri: Uri) {
        val f = pendingSave ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ctx.contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o, 1 shl 16) } }
                ui = ui.copy(message = "Dosya kaydedildi.")
            } catch (e: Throwable) {
                ui = ui.copy(message = "Kaydetme hatası: ${e.message}")
            }
        }
    }

    fun exportZipTo(uri: Uri) {
        val p = current ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ZipOutputStream(ctx.contentResolver.openOutputStream(uri)!!.buffered()).use { z ->
                    p.stems.forEach { (name, f) ->
                        z.putNextEntry(ZipEntry("${safeName(p.name)}_$name.wav"))
                        f.inputStream().use { it.copyTo(z, 1 shl 16) }
                        z.closeEntry()
                    }
                }
                ui = ui.copy(message = "ZIP kaydedildi.")
            } catch (e: Throwable) {
                ui = ui.copy(message = "Kaydetme hatası: ${e.message}")
            }
        }
    }

    fun renderMix() {
        val p = current ?: return
        if (ui.rendering) return
        val gains = p.stems.keys.associateWith { if (muted[it] == true) 0f else (volumes[it] ?: 1f) }
        val enabled = gains.filter { it.value > 0f }.keys
        if (enabled.isEmpty()) { ui = ui.copy(message = "En az bir kanalı açın."); return }
        val label = when {
            enabled == setOf("vocals") -> "Akapella"
            enabled == p.stems.keys - "vocals" && gains.values.all { it == 0f || it == 1f } -> "Karaoke"
            enabled.size == p.stems.size && gains.values.all { it == 1f } -> "Mix"
            else -> "Özel Mix"
        }
        ui = ui.copy(rendering = true)
        val id = System.currentTimeMillis().toString()
        val out = File(ArchiveStore.dir(ctx), "$id.wav")
        viewModelScope.launch(Dispatchers.Default) {
            try {
                MixRenderer.render(p.stems, gains, out, {}, { false })
                ArchiveStore.writeMeta(ctx, id, "${p.name} · $label", id.toLong(), p.durationMs)
                withContext(Dispatchers.Main) {
                    refreshLists()
                    ui = ui.copy(rendering = false, message = "Mix oluşturuldu. Arşiv sekmesinde.")
                }
            } catch (e: Throwable) {
                ui = ui.copy(rendering = false, message = "Mix oluşturulamadı: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
        player?.release()
    }
}
