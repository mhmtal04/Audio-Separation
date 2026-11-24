package studio.audioseparator

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import studio.audioseparator.ui.ArchiveScreen
import studio.audioseparator.ui.BottomNav
import studio.audioseparator.ui.ExportScreen
import studio.audioseparator.ui.GlassCard
import studio.audioseparator.ui.HomeScreen
import studio.audioseparator.ui.PlayerScreen
import studio.audioseparator.ui.ProjectsScreen
import studio.audioseparator.ui.ResultsScreen
import studio.audioseparator.ui.Screen
import studio.audioseparator.ui.SeparateScreen
import studio.audioseparator.ui.SeparatorViewModel
import studio.audioseparator.ui.Studio
import studio.audioseparator.ui.StudioBackground

class MainActivity : ComponentActivity() {
    private val vm: SeparatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { java.io.File(cacheDir, "last_crash.txt").writeText(e.stackTraceToString().take(1500)) } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
        window.statusBarColor = 0xFF06040E.toInt()
        window.navigationBarColor = 0xFF06040E.toInt()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Studio.Purple, background = Studio.Bg, surface = Studio.Panel)
            ) { StudioApp(vm) }
        }
    }
}

@Composable
private fun StudioApp(vm: SeparatorViewModel) {
    val ui = vm.ui
    val ctx = LocalContext.current
    val view = LocalView.current
    DisposableEffect(ui.running) {
        view.keepScreenOn = ui.running
        onDispose { view.keepScreenOn = false }
    }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onAudioPicked(uri)
    }
    val saveWav = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        if (uri != null) vm.exportSaveTo(uri)
    }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportZipTo(uri)
    }

    val onPick: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        pickAudio.launch(arrayOf("audio/*", "application/ogg"))
    }
    val onSaveWav: (java.io.File, String) -> Unit = { f, name -> vm.prepareSave(f); saveWav.launch(name) }

    BackHandler {
        if (ui.running) return@BackHandler
        if (!vm.back()) {
            if (vm.screen != Screen.Home) vm.goTab(Screen.Home) else (ctx as? Activity)?.finish()
        }
    }

    StudioBackground {
        Column(Modifier.fillMaxSize()) {
            ui.message?.let { msg ->
                GlassCard(Modifier.fillMaxWidth().padding(12.dp), Color(0xFFFFC857)) {
                    Text(msg, color = Color.White, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.clearMessage() }) { Text("Tamam", color = Studio.Neon) }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (vm.screen) {
                    Screen.Home -> HomeScreen(vm, onPick)
                    Screen.Separate -> SeparateScreen(vm, onPick)
                    Screen.Results -> ResultsScreen(vm)
                    Screen.Player -> PlayerScreen(vm)
                    Screen.Projects -> ProjectsScreen(vm, onPick)
                    Screen.Archive -> ArchiveScreen(vm, onSaveWav)
                    Screen.Export -> ExportScreen(vm, onSaveWav) { name -> saveZip.launch(name) }
                }
            }
            if (vm.screen == Screen.Home || vm.screen == Screen.Projects || vm.screen == Screen.Archive) {
                BottomNav(vm.screen) { vm.goTab(it) }
            }
        }
    }
}
