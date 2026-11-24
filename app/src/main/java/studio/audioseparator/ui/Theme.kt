package studio.audioseparator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Studio {
    val Bg = Color(0xFF06040E)
    val Panel = Color(0xFF130E24)
    val Violet = Color(0xFF7C3AED)
    val Purple = Color(0xFF8B5CF6)
    val Neon = Color(0xFFB07CFF)
    val Blue = Color(0xFF4F8CFF)
    val Dim = Color(0xFFA59CC0)
    val Gradient: Brush = Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF9B5CFF)))
    val Disabled: Brush = Brush.horizontalGradient(listOf(Color(0xFF2A2440), Color(0xFF2A2440)))
}

fun stemColor(n: String) = when (n) {
    "vocals" -> Color(0xFFA78BFA)
    "drums" -> Color(0xFFFF8A3D)
    "bass" -> Color(0xFF3DDC84)
    else -> Color(0xFF3B9BFF)
}

fun stemEmoji(n: String) = when (n) {
    "vocals" -> "🎤"
    "drums" -> "🥁"
    "bass" -> "🎸"
    else -> "🎵"
}

fun stemLabel(n: String) = when (n) {
    "vocals" -> "Vocals"
    "drums" -> "Drums"
    "bass" -> "Bass"
    else -> "Other"
}

@Composable
fun StudioBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Studio.Bg).drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0x557C3AED), Color.Transparent),
                    center = Offset(size.width * 0.85f, 0f), radius = size.width * 1.1f,
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0x334F8CFF), Color.Transparent),
                    center = Offset(0f, size.height), radius = size.width,
                )
            )
        },
        content = content,
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = Studio.Purple,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    var m = modifier
        .clip(shape)
        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), Studio.Panel)), shape)
        .border(1.dp, accent.copy(alpha = 0.55f), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(14.dp), content = content)
}
