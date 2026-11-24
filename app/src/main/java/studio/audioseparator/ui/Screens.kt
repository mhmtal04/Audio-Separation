package studio.audioseparator.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import studio.audioseparator.data.ArchiveItem
import studio.audioseparator.data.Project

private val SCREEN_PAD = 20.dp

// ============================ ANA SAYFA ============================
@Composable
fun HomeScreen(vm: SeparatorViewModel, onPick: () -> Unit) {
    val ui = vm.ui
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_PAD, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(Modifier.size(58.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("AUDIO", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("SEPARATOR", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("STUDIO", color = Studio.Neon, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        HeroWave(Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp))
        Text(
            "Müziğin tüm katmanlarını\nkeşfet, istediğini çıkar!",
            Modifier.fillMaxWidth(), color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center, lineHeight = 26.sp,
        )
        Spacer(Modifier.height(22.dp))
        HomeCard(
            "AI Separation",
            if (ui.modelBusy) "Model hazırlanıyor…" else "Sesleri yapay zeka ile ayır",
            IconKind.Wave, Studio.Purple,
        ) { if (ui.modelReady) onPick() }
        Spacer(Modifier.height(12.dp))
        HomeCard("Projelerim", "Kaydedilen çalışmaların", IconKind.Folder, Studio.Violet) { vm.goTab(Screen.Projects) }
        Spacer(Modifier.height(12.dp))
        HomeCard("Ses Arşivim", "Oluşturduğun mix'ler", IconKind.Note, Studio.Blue) { vm.goTab(Screen.Archive) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HomeCard(title: String, sub: String, icon: IconKind, accent: Color, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, Studio.Neon, 38.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = Studio.Dim, fontSize = 13.sp)
            }
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                Ic(IconKind.Chevron, Color.White, 18.dp)
            }
        }
    }
}

@Composable
private fun HeroWave(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "hero")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0x667C3AED), Color.Transparent)),
            radius = size.minDimension * 0.9f,
        )
        val n = 41
        val gap = size.width / n
        for (i in 0 until n) {
            val x = i * gap + gap / 2
            val env = 1f - abs(i - (n - 1) / 2f) / ((n - 1) / 2f)
            val amp = (0.25f + 0.75f * abs(sin(phase + i * 0.35f))) * env.pow(1.2f)
            val h = max(8f, amp * size.height * 0.92f)
            val col = lerp(Studio.Neon, Studio.Blue, i / (n - 1f))
            drawLine(col, Offset(x, size.height / 2 - h / 2), Offset(x, size.height / 2 + h / 2),
                strokeWidth = gap * 0.5f, cap = StrokeCap.Round)
        }
    }
}

// ============================ AYRIŞTIRMA ============================
@Composable
fun SeparateScreen(vm: SeparatorViewModel, onPick: () -> Unit) {
    val ui = vm.ui
    val info = ui.info
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_PAD)) {
        TopBar("Ses Ayrıştırma", onBack = if (ui.running) null else ({ vm.back(); Unit }))
        if (info == null) {
            Text("Dosya okunuyor…", color = Studio.Dim, modifier = Modifier.padding(top = 24.dp))
        } else {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(ui.cover, 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(info.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val br = if (info.bitrate > 0) " · ${info.bitrate / 1000} kbps" else ""
                        Text("${info.format} · ${fmtTime(info.durationMs)}$br", color = Studio.Dim, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                WaveBars(ui.sourceWave, if (ui.running) ui.progress else 1f, Studio.Purple, Modifier.fillMaxWidth().height(70.dp))
            }
            Spacer(Modifier.height(24.dp))
            if (!ui.running) {
                PrimaryButton("Ayrıştırmayı Başlat", { vm.startSeparation() }, Modifier.fillMaxWidth(), ui.modelReady, IconKind.Wave)
                if (!ui.modelReady) {
                    Text("Model hazırlanıyor…", color = Studio.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                GhostButton("Başka dosya seç", onPick, Modifier.fillMaxWidth())
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProgressRing((ui.progress), Modifier.size(200.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%${(ui.progress * 100).toInt()}", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                            Text("işleniyor…", color = Studio.Dim, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(ui.stage, color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    val eta = ui.etaSec
                    Text(
                        if (eta != null) "Tahmini kalan süre: ${fmtEta(eta)}" else "Süre hesaplanıyor…",
                        color = Studio.Dim, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                    GhostButton("İptal Et", { vm.cancel() }, Modifier.fillMaxWidth(), IconKind.Close)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ============================ SONUÇLAR ============================
@Composable
fun ResultsScreen(vm: SeparatorViewModel) {
    val p = vm.current
    Column(Modifier.fillMaxSize().padding(horizontal = SCREEN_PAD)) {
        TopBar("Sonuçlar", onBack = { if (!vm.back()) vm.goTab(Screen.Home) })
        if (p == null) {
            Text("Proje bulunamadı.", color = Studio.Dim)
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                GlassCard(Modifier.fillMaxWidth(), Color(0xFF3DDC84)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2FB36B)), contentAlignment = Alignment.Center) {
                            Ic(IconKind.Check, Color.White, 22.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Ayrıştırma Tamamlandı!", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(p.name, color = Studio.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                p.stems.forEach { (name, file) ->
                    Spacer(Modifier.height(12.dp))
                    StemCard(vm, name, file)
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Tümünü Dışa Aktar", { vm.exportTab = 1; vm.navigate(Screen.Export) }, Modifier.weight(1f), IconKind.Download)
                GhostButton("Mix Oynat", { vm.playMix(); vm.navigate(Screen.Player) }, Modifier.weight(1f), IconKind.Play)
            }
        }
    }
}

@Composable
private fun StemCard(vm: SeparatorViewModel, name: String, file: File) {
    val color = stemColor(name)
    LaunchedEffect(file.path) { vm.ensureWave(file) }
    val peaks = vm.waves[file.path]
    val active = vm.soloStem == name
    val isPlaying = active && vm.playing
    val prog = if (active && vm.totalFrames > 0) vm.positionFrames.toFloat() / vm.totalFrames else 1f
    GlassCard(Modifier.fillMaxWidth(), color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(stemEmoji(name), fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stemLabel(name), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("WAV · ${fmtBytes(file.length())}", color = Studio.Dim, fontSize = 12.sp)
            }
            CircleButton(if (isPlaying) IconKind.Pause else IconKind.Play, color) { vm.playStem(name) }
        }
        Spacer(Modifier.height(8.dp))
        WaveBars(peaks, prog, color, Modifier.fillMaxWidth().height(44.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(IconKind.Volume, Studio.Dim, 18.dp)
            VolumeSlider(vm, name, color, Modifier.weight(1f))
        }
    }
}

@Composable
private fun VolumeSlider(vm: SeparatorViewModel, name: String, color: Color, modifier: Modifier) {
    val v = vm.volumes[name] ?: 1f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = v, onValueChange = { vm.setVolume(name, it) }, valueRange = 0f..1.5f,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = Color(0x33FFFFFF)),
        )
        Text("%${(v * 100).toInt()}", color = Studio.Dim, fontSize = 12.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
    }
}

// ============================ MIX PLAYER ============================
@Composable
fun PlayerScreen(vm: SeparatorViewModel) {
    val p = vm.current
    val cover = rememberCover(p?.cover, 512)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_PAD)) {
        TopBar("Mix Player", onBack = { vm.back() })
        if (p == null) {
            Text("Proje bulunamadı.", color = Studio.Dim)
        } else {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(214.dp).clip(CircleShape).border(
                        4.dp, Brush.sweepGradient(listOf(Studio.Blue, Studio.Purple, Studio.Neon, Studio.Blue)), CircleShape
                    ).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) { CoverArt(cover, 190.dp, CircleShape) }
            }
            Spacer(Modifier.height(14.dp))
            Text(p.name, Modifier.fillMaxWidth(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(fmtTime(p.durationMs), Modifier.fillMaxWidth(), color = Studio.Dim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Slider(
                value = if (vm.totalFrames > 0) vm.positionFrames.toFloat() / vm.totalFrames else 0f,
                onValueChange = { vm.seek(it) },
                colors = SliderDefaults.colors(thumbColor = Studio.Neon, activeTrackColor = Studio.Purple, inactiveTrackColor = Color(0x33FFFFFF)),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmtTime(vm.positionFrames * 1000 / 44100), color = Studio.Dim, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(fmtTime(vm.totalFrames * 1000 / 44100), color = Studio.Dim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircleButton(IconKind.Prev, Studio.Dim, 48.dp) { vm.seekBy(-10) }
                    Text("10 sn", color = Studio.Dim, fontSize = 11.sp)
                }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Studio.Gradient, CircleShape)
                        .clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) { Ic(if (vm.playing) IconKind.Pause else IconKind.Play, Color.White, 36.dp) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircleButton(IconKind.Next, Studio.Dim, 48.dp) { vm.seekBy(10) }
                    Text("10 sn", color = Studio.Dim, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Kanal Kontrolleri", color = Color.White, fontWeight = FontWeight.SemiBold)
                p.stems.keys.forEach { ChannelRow(vm, it) }
            }
            Spacer(Modifier.height(12.dp))
            GhostButton("Bu karışımı kaydet", { vm.exportTab = 2; vm.navigate(Screen.Export) }, Modifier.fillMaxWidth(), IconKind.Download)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChannelRow(vm: SeparatorViewModel, name: String) {
    val color = stemColor(name)
    val on = vm.muted[name] != true
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) color else Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
            if (on) Ic(IconKind.Check, Color.White, 14.dp)
        }
        Spacer(Modifier.width(8.dp))
        Text(stemLabel(name), color = Color.White, fontSize = 14.sp, modifier = Modifier.width(54.dp))
        VolumeSlider(vm, name, color, Modifier.weight(1f))
        Switch(
            checked = on, onCheckedChange = { vm.setEnabled(name, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = color, checkedThumbColor = Color.White),
        )
    }
}

// ============================ PROJELERİM ============================
@Composable
fun ProjectsScreen(vm: SeparatorViewModel, onPick: () -> Unit) {
    var toDelete by remember { mutableStateOf<Project?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = SCREEN_PAD)) {
        TopBar("Projelerim", onBack = null)
        StorageSummary(vm)
        Spacer(Modifier.height(12.dp))
        if (vm.projects.isEmpty()) {
            EmptyState("Henüz proje yok", "Bir şarkıyı ayrıştırdığında burada görünür.", "Şarkı Ayrıştır", onPick)
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.projects.forEach { p ->
                    val cover = rememberCover(p.cover, 160)
                    GlassCard(Modifier.fillMaxWidth(), onClick = { vm.openProject(p, Screen.Results) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverArt(cover, 56.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${fmtDate(p.createdAt)} · ${fmtTime(p.durationMs)}", color = Studio.Dim, fontSize = 12.sp)
                                Text(fmtBytes(p.sizeBytes), color = Studio.Dim, fontSize = 12.sp)
                            }
                            CircleButton(IconKind.Play, Studio.Purple, 40.dp) { vm.openProject(p, Screen.Player, autoplay = true) }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { toDelete = p }, contentAlignment = Alignment.Center) {
                                Ic(IconKind.Trash, Studio.Dim, 22.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    toDelete?.let { p ->
        ConfirmDelete("\"${p.name}\" silinsin mi?", "Ayrıştırılmış dört kanal (${fmtBytes(p.sizeBytes)}) cihazdan kalıcı olarak silinir.",
            onConfirm = { vm.deleteProject(p); toDelete = null }, onDismiss = { toDelete = null })
    }
}

@Composable
private fun StorageSummary(vm: SeparatorViewModel) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("Kullanılan alan", color = Studio.Dim, fontSize = 12.sp)
                Text(fmtBytes(vm.usedBytes), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Cihazda boş alan", color = Studio.Dim, fontSize = 12.sp)
                Text(fmtBytes(vm.freeBytes), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, sub: String, button: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LogoMark(Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(sub, color = Studio.Dim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp))
        if (button != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(button, onClick, Modifier.fillMaxWidth(0.7f))
        }
    }
}

@Composable
private fun ConfirmDelete(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Studio.Panel,
        titleContentColor = Color.White,
        textContentColor = Studio.Dim,
        title = { Text(title, fontSize = 17.sp) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Sil", color = Color(0xFFFF6B6B)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = Studio.Neon) } },
    )
}

// ============================ ARŞİV ============================
@Composable
fun ArchiveScreen(vm: SeparatorViewModel, onSave: (File, String) -> Unit) {
    var toDelete by remember { mutableStateOf<ArchiveItem?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = SCREEN_PAD)) {
        TopBar("Ses Arşivim", onBack = null)
        if (vm.archive.isEmpty()) {
            EmptyState(
                "Arşiv boş",
                "Bir projeden vokalsiz (karaoke), akapella ya da özel bir karışım oluşturduğunda burada görünür.",
                null, {},
            )
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.archive.forEach { a ->
                    val isThis = vm.loadedKey == "a:${a.id}"
                    GlassCard(Modifier.fillMaxWidth(), Studio.Blue) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverArt(null, 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${fmtDate(a.createdAt)} · ${fmtTime(a.durationMs)} · ${fmtBytes(a.sizeBytes)}", color = Studio.Dim, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircleButton(if (isThis && vm.playing) IconKind.Pause else IconKind.Play, Studio.Blue, 40.dp) { vm.playArchive(a) }
                            CircleButton(IconKind.Download, Studio.Purple, 40.dp) { onSave(a.file, safeName(a.name) + ".wav") }
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { toDelete = a }, contentAlignment = Alignment.Center) {
                                Ic(IconKind.Trash, Studio.Dim, 22.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    toDelete?.let { a ->
        ConfirmDelete("\"${a.name}\" silinsin mi?", "Bu mix cihazdan kalıcı olarak silinir.",
            onConfirm = { vm.deleteArchive(a); toDelete = null }, onDismiss = { toDelete = null })
    }
}

// ============================ DIŞA AKTAR ============================
@Composable
fun ExportScreen(vm: SeparatorViewModel, onSaveWav: (File, String) -> Unit, onSaveZip: (String) -> Unit) {
    val p = vm.current
    var single by remember { mutableStateOf("vocals") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SCREEN_PAD)) {
        TopBar("Dışa Aktar", onBack = { vm.back() })
        if (p == null) {
            Text("Proje bulunamadı.", color = Studio.Dim)
        } else {
            Segmented(listOf("Tek Kanal", "Tüm Kanallar", "Özel Mix"), vm.exportTab) { vm.exportTab = it }
            Spacer(Modifier.height(16.dp))
            when (vm.exportTab) {
                0 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.stems.keys.forEach { n -> StemChip(n, single == n, { single = n }, Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(14.dp))
                    val f = p.stems[single]
                    GlassCard(Modifier.fillMaxWidth(), stemColor(single)) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stemEmoji(single), fontSize = 40.sp)
                            Text(stemLabel(single), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("WAV · ${fmtBytes(f?.length() ?: 0)}", color = Studio.Dim, fontSize = 13.sp)
                        }
                    }
                    FormatNote()
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("İndir", { f?.let { onSaveWav(it, "${safeName(p.name)}_$single.wav") } }, Modifier.fillMaxWidth(), true, IconKind.Download)
                }
                1 -> {
                    GlassCard(Modifier.fillMaxWidth()) {
                        p.stems.forEach { (n, f) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${stemEmoji(n)}  ${stemLabel(n)}", color = Color.White, modifier = Modifier.weight(1f))
                                Text(fmtBytes(f.length()), color = Studio.Dim, fontSize = 13.sp)
                            }
                        }
                    }
                    FormatNote()
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("ZIP olarak indir", { onSaveZip("${safeName(p.name)}_stems.zip") }, Modifier.fillMaxWidth(), true, IconKind.Download)
                }
                else -> {
                    Text("Hazır karışımlar", color = Studio.Dim, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Karaoke", { vm.applyPreset("karaoke") }, Modifier.weight(1f))
                        GhostButton("Akapella", { vm.applyPreset("acapella") }, Modifier.weight(1f))
                        GhostButton("Davulsuz", { vm.applyPreset("nodrums") }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    GlassCard(Modifier.fillMaxWidth()) {
                        p.stems.keys.forEach { ChannelRow(vm, it) }
                    }
                    Text("Karışım Arşiv sekmesine eklenir; oradan cihaza kaydedebilirsin.", color = Studio.Dim, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(if (vm.ui.rendering) "Hazırlanıyor…" else "Mix Oluştur", { vm.renderMix() },
                        Modifier.fillMaxWidth(), !vm.ui.rendering, IconKind.Wave)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FormatNote() {
    Text("WAV · 16-bit · 44,1 kHz · kayıpsız", color = Studio.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
}
