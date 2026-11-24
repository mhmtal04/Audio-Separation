package studio.audioseparator.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun fmtBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    else -> "%d KB".format(b / 1024)
}

fun fmtDate(ms: Long): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ms))

fun fmtEta(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, s) else "%02d:%02d".format(m, s)
}

fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_")

fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var s = 1
    while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx) s *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
}

@Composable
fun CoverArt(bitmap: Bitmap?, size: Dp, shape: Shape = RoundedCornerShape(14.dp)) {
    if (bitmap != null) {
        Image(
            bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(
                Brush.linearGradient(listOf(Studio.Violet, Studio.Blue, Color(0xFF2A1458)))
            ),
            contentAlignment = Alignment.Center,
        ) { Ic(IconKind.Note, Color.White, size * 0.5f) }
    }
}

@Composable
fun rememberCover(file: File?, px: Int = 256): Bitmap? = remember(file?.path) {
    try { file?.takeIf { it.exists() }?.readBytes()?.let { decodeSampled(it, px) } } catch (_: Throwable) { null }
}
