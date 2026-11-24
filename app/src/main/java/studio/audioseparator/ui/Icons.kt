package studio.audioseparator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class IconKind { Play, Pause, Close, Back, Chevron, Trash, Home, Folder, Note, Volume, Download, Check, Prev, Next, Wave }

@Composable
fun Ic(kind: IconKind, tint: Color = Color.White, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawIcon(kind, tint) }
}

private fun DrawScope.drawIcon(kind: IconKind, c: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.09f
    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(c, Offset(w * x1, h * y1), Offset(w * x2, h * y2), strokeWidth = sw, cap = StrokeCap.Round)

    when (kind) {
        IconKind.Play -> drawPath(Path().apply {
            moveTo(w * 0.32f, h * 0.2f); lineTo(w * 0.32f, h * 0.8f); lineTo(w * 0.8f, h * 0.5f); close()
        }, c)
        IconKind.Pause -> {
            drawRoundRect(c, Offset(w * 0.27f, h * 0.2f), Size(w * 0.17f, h * 0.6f), CornerRadius(w * 0.04f))
            drawRoundRect(c, Offset(w * 0.56f, h * 0.2f), Size(w * 0.17f, h * 0.6f), CornerRadius(w * 0.04f))
        }
        IconKind.Close -> { line(0.27f, 0.27f, 0.73f, 0.73f); line(0.73f, 0.27f, 0.27f, 0.73f) }
        IconKind.Back -> drawPath(Path().apply {
            moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.32f, h * 0.5f); lineTo(w * 0.62f, h * 0.8f)
        }, c, style = st)
        IconKind.Chevron -> drawPath(Path().apply {
            moveTo(w * 0.38f, h * 0.22f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.38f, h * 0.78f)
        }, c, style = st)
        IconKind.Trash -> {
            drawRoundRect(c, Offset(w * 0.3f, h * 0.36f), Size(w * 0.4f, h * 0.46f), CornerRadius(w * 0.05f), style = st)
            line(0.2f, 0.28f, 0.8f, 0.28f); line(0.4f, 0.18f, 0.6f, 0.18f)
        }
        IconKind.Home -> drawPath(Path().apply {
            moveTo(w * 0.5f, h * 0.16f); lineTo(w * 0.86f, h * 0.48f); lineTo(w * 0.86f, h * 0.84f)
            lineTo(w * 0.14f, h * 0.84f); lineTo(w * 0.14f, h * 0.48f); close()
        }, c, style = st)
        IconKind.Folder -> {
            drawRoundRect(c, Offset(w * 0.12f, h * 0.32f), Size(w * 0.76f, h * 0.5f), CornerRadius(w * 0.08f), style = st)
            line(0.14f, 0.32f, 0.14f, 0.24f); line(0.14f, 0.24f, 0.4f, 0.24f); line(0.4f, 0.24f, 0.48f, 0.32f)
        }
        IconKind.Note -> {
            drawCircle(c, w * 0.15f, Offset(w * 0.36f, h * 0.72f))
            line(0.5f, 0.72f, 0.5f, 0.2f); line(0.5f, 0.2f, 0.78f, 0.32f)
        }
        IconKind.Volume -> {
            drawPath(Path().apply {
                moveTo(w * 0.18f, h * 0.4f); lineTo(w * 0.34f, h * 0.4f); lineTo(w * 0.55f, h * 0.22f)
                lineTo(w * 0.55f, h * 0.78f); lineTo(w * 0.34f, h * 0.6f); lineTo(w * 0.18f, h * 0.6f); close()
            }, c)
            drawArc(c, -45f, 90f, false, Offset(w * 0.45f, h * 0.3f), Size(w * 0.32f, h * 0.4f), style = st)
        }
        IconKind.Download -> {
            line(0.5f, 0.18f, 0.5f, 0.66f); line(0.3f, 0.48f, 0.5f, 0.68f); line(0.7f, 0.48f, 0.5f, 0.68f)
            line(0.24f, 0.84f, 0.76f, 0.84f)
        }
        IconKind.Check -> drawPath(Path().apply {
            moveTo(w * 0.2f, h * 0.52f); lineTo(w * 0.42f, h * 0.72f); lineTo(w * 0.8f, h * 0.3f)
        }, c, style = st)
        IconKind.Prev -> {
            drawPath(Path().apply {
                moveTo(w * 0.78f, h * 0.22f); lineTo(w * 0.78f, h * 0.78f); lineTo(w * 0.36f, h * 0.5f); close()
            }, c)
            drawRoundRect(c, Offset(w * 0.2f, h * 0.22f), Size(w * 0.09f, h * 0.56f), CornerRadius(w * 0.03f))
        }
        IconKind.Next -> {
            drawPath(Path().apply {
                moveTo(w * 0.22f, h * 0.22f); lineTo(w * 0.22f, h * 0.78f); lineTo(w * 0.64f, h * 0.5f); close()
            }, c)
            drawRoundRect(c, Offset(w * 0.71f, h * 0.22f), Size(w * 0.09f, h * 0.56f), CornerRadius(w * 0.03f))
        }
        IconKind.Wave -> {
            val hs = floatArrayOf(0.3f, 0.55f, 0.85f, 0.55f, 0.3f)
            for (i in hs.indices) {
                val x = 0.2f + i * 0.15f
                line(x, 0.5f - hs[i] / 2f, x, 0.5f + hs[i] / 2f)
            }
        }
    }
}

/** Marka işareti: ortası yüksek, mor→mavi geçişli 7 çubuk. */
@Composable
fun LogoMark(modifier: Modifier = Modifier.size(56.dp)) {
    Canvas(modifier) {
        val hs = floatArrayOf(0.34f, 0.58f, 0.82f, 1f, 0.82f, 0.58f, 0.34f)
        val gap = size.width / hs.size
        for (i in hs.indices) {
            val x = gap * i + gap / 2
            val bh = size.height * hs[i]
            drawLine(
                Brush.verticalGradient(
                    listOf(Studio.Neon, Studio.Violet, Studio.Blue),
                    startY = (size.height - bh) / 2, endY = (size.height + bh) / 2,
                ),
                Offset(x, (size.height - bh) / 2), Offset(x, (size.height + bh) / 2),
                strokeWidth = gap * 0.52f, cap = StrokeCap.Round,
            )
        }
    }
}
