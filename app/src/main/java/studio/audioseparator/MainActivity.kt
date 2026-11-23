package studio.audioseparator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.audioseparator.ui.SeparatorViewModel

private val Bg = Color(0xFF0B0B0D)
private val Card = Color(0xFF17171B)
private val Accent = Color(0xFF8B6CFF)
private val Dim = Color(0xFF9A9AA5)

private val STEM_ORDER = listOf("vocals", "drums", "bass", "other")
private fun stemColor(n: String) = when (n) {
    "vocals" -> Color(0xFFFF6B8B)
    "drums" -> Color(0xFFFFB454)
    "bass" -> Color(0xFF4DD0E1)
    else -> Color(0xFFA5D66B)
}

class MainActivity : ComponentActivity() {
    private val vm: SeparatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card)) {
                Screen(vm)
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Screen(vm: SeparatorViewModel) {
    val ui = vm.ui
    val view = LocalView.current
    DisposableEffect(ui.running) {
        view.keepScreenOn = ui.running
        onDispose { view.keepScreenOn = false }
    }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onAudioPicked(uri)
    }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }
    val saveWav = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        if (uri != null) vm.exportPendingTo(uri)
    }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportZipTo(uri)
    }

    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AUDIO SEPARATOR STUDIO", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Cihaz üzerinde, ücretsiz, API anahtarsız AI ayrıştırma", color = Dim, fontSize = 12.sp)

        // Model
        Box2 {
            Text("AI MODELİ (htdemucs · ONNX)", color = Dim, fontSize = 11.sp)
            if (ui.modelReady) {
                Text("Yüklü ✓", color = Color.White)
            } else {
                Text("Model yüklü değil. README'deki adımlarla model dosyasını üretin, sonra seçin.", color = Color.White, fontSize = 13.sp)
            }
            OutlinedButton(onClick = { pickModel.launch(arrayOf("*/*")) }, enabled = !ui.modelBusy && !ui.running) {
                Text(if (ui.modelBusy) "Kopyalanıyor…" else if (ui.modelReady) "Modeli değiştir" else "Model dosyasını seç (.onnx)")
            }
        }

        // Dosya
        Box2 {
            Text("SES DOSYASI", color = Dim, fontSize = 11.sp)
            val info = ui.info
            if (info != null) {
                Text(info.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    "${info.format} · ${fmtTime(info.durationMs)} · ${info.sampleRate} Hz · ${if (info.channels == 1) "mono" else "${info.channels} kanal"}",
                    color = Dim, fontSize = 13.sp,
                )
            }
            OutlinedButton(onClick = { pickAudio.launch(arrayOf("audio/*", "application/ogg")) }, enabled = !ui.running) {
                Text(if (info == null) "Ses dosyası seç" else "Başka dosya seç")
            }
            if (info != null) {
                Button(
                    onClick = { vm.startSeparation() },
                    enabled = ui.modelReady && !ui.running,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("AYRIŞTIR") }
                if (!ui.modelReady) Text("Önce modeli yükleyin.", color = Dim, fontSize = 12.sp)
            }
        }

        // İlerleme
        if (ui.running) {
            Box2 {
                Text(ui.stage, color = Color.White)
                LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth(), color = Accent)
                Text("%${(ui.progress * 100).toInt()} · Ekranı kapatmayın, cihaz ısınabilir.", color = Dim, fontSize = 12.sp)
                OutlinedButton(onClick = { vm.cancel() }) { Text("İptal") }
            }
        }

        ui.message?.let { Text(it, color = Color(0xFFFFC857), fontSize = 13.sp) }

        // Sonuç
        val stems = ui.stems
        if (stems != null) {
            Box2 {
                Text("MIX", color = Dim, fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.togglePlay() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text(if (vm.playing) "Durdur" else "Dinle")
                    }
                    Text("${fmtTime(vm.positionFrames * 1000 / 44100)} / ${fmtTime(vm.totalFrames * 1000 / 44100)}", color = Dim)
                }
                Slider(
                    value = if (vm.totalFrames > 0) vm.positionFrames.toFloat() / vm.totalFrames else 0f,
                    onValueChange = { vm.seek(it) },
                )
            }
            STEM_ORDER.filter { it in stems }.forEach { name ->
                Box2 {
                    Text(name.uppercase(), color = stemColor(name), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { vm.toggleMute(name) }) {
                            Text(if (vm.muted[name] == true) "Sessiz ✓" else "Sessiz")
                        }
                        OutlinedButton(onClick = { vm.toggleSolo(name) }) {
                            Text(if (vm.soloed[name] == true) "Solo ✓" else "Solo")
                        }
                    }
                    Text("Ses seviyesi", color = Dim, fontSize = 11.sp)
                    Slider(value = vm.volumes[name] ?: 1f, onValueChange = { vm.setVolume(name, it) }, valueRange = 0f..1.5f)
                    OutlinedButton(onClick = {
                        vm.requestExport(name)
                        saveWav.launch("${vm.baseName()}_$name.wav")
                    }) { Text("WAV olarak kaydet") }
                }
            }
            Button(onClick = { saveZip.launch("${vm.baseName()}_stems.zip") }, modifier = Modifier.fillMaxWidth()) {
                Text("Tümünü ZIP olarak kaydet")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Box2(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
