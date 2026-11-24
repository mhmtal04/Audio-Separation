package studio.audioseparator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: IconKind? = null,
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier.height(54.dp).clip(shape)
            .background(if (enabled) Studio.Gradient else Studio.Disabled, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Ic(icon, size = 20.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (enabled) Color.White else Studio.Dim, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: IconKind? = null) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier.height(50.dp).clip(shape)
            .background(Color(0x14FFFFFF), shape)
            .border(1.dp, Studio.Purple.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Ic(icon, size = 18.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun CircleButton(icon: IconKind, color: Color = Studio.Purple, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(color.copy(alpha = 0.25f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.8f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ic(icon, size = size * 0.5f) }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, trailing: @Composable (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        if (onBack != null) {
            Box(
                Modifier.align(Alignment.CenterStart).size(40.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Ic(IconKind.Back, size = 22.dp) }
        }
        Text(title, Modifier.align(Alignment.Center), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x22FFFFFF)).padding(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Studio.Gradient else SolidColor(Color.Transparent))
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (sel) Color.White else Studio.Dim, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
fun StemChip(name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = stemColor(name)
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.clip(shape)
            .background(if (selected) c.copy(alpha = 0.28f) else Color(0x14FFFFFF), shape)
            .border(1.dp, if (selected) c else Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) { Text("${stemEmoji(name)} ${stemLabel(name)}", color = Color.White, fontSize = 13.sp) }
}

@Composable
fun WaveBars(peaks: FloatArray?, progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = peaks?.size ?: 64
        val gap = size.width / n
        val bw = gap * 0.55f
        val dim = Color(0x40FFFFFF)
        for (i in 0 until n) {
            val p = peaks?.getOrNull(i) ?: 0.06f
            val bh = max(4f, p * size.height)
            val x = i * gap + gap / 2
            val col = if ((i + 0.5f) / n <= progress) color else dim
            drawLine(
                col, Offset(x, size.height / 2 - bh / 2), Offset(x, size.height / 2 + bh / 2),
                strokeWidth = bw, cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun ProgressRing(progress: Float, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 14.dp.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(Color(0x22FFFFFF), 0f, 360f, false, tl, sz, style = Stroke(sw))
            rotate(-90f) {
                drawArc(
                    Brush.sweepGradient(listOf(Studio.Blue, Studio.Purple, Studio.Neon, Studio.Blue)),
                    0f, 360f * progress.coerceIn(0f, 1f), false, tl, sz,
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
fun BottomNav(current: Screen, onSelect: (Screen) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xF0100B1E)).border(1.dp, Color(0x228B5CF6)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem("Ana Sayfa", IconKind.Home, current == Screen.Home) { onSelect(Screen.Home) }
        NavItem("Projelerim", IconKind.Folder, current == Screen.Projects) { onSelect(Screen.Projects) }
        NavItem("Arşiv", IconKind.Note, current == Screen.Archive) { onSelect(Screen.Archive) }
    }
}

@Composable
private fun NavItem(label: String, icon: IconKind, active: Boolean, onClick: () -> Unit) {
    val c = if (active) Studio.Neon else Studio.Dim
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Ic(icon, c, 24.dp)
        Text(label, color = c, fontSize = 11.sp)
        Box(
            Modifier.padding(top = 4.dp).width(28.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(if (active) Studio.Gradient else SolidColor(Color.Transparent))
        )
    }
}
