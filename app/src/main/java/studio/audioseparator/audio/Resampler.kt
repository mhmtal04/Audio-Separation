package studio.audioseparator.audio

import java.util.concurrent.CancellationException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Hann pencereli sinc yeniden örnekleyici (stereo interleaved 16-bit). */
object Resampler {
    private const val HALF = 16
    private const val RES = 256

    fun resample(
        src: ShortArray,
        srcRate: Int,
        dstRate: Int,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): ShortArray {
        if (srcRate == dstRate) return src
        val inFrames = src.size / 2
        val outFrames = (inFrames.toLong() * dstRate / srcRate).toInt()
        val step = srcRate.toDouble() / dstRate
        val fc = min(1.0, dstRate.toDouble() / srcRate)
        val zw = HALF / fc
        val tableLen = (zw * RES).toInt() + 2
        val table = FloatArray(tableLen) { i ->
            val t = i.toDouble() / RES
            if (t >= zw) 0f else {
                val x = PI * fc * t
                val sinc = if (t == 0.0) 1.0 else sin(x) / x
                val win = 0.5 + 0.5 * cos(PI * t / zw)
                (fc * sinc * win).toFloat()
            }
        }
        val out = ShortArray(outFrames * 2)
        for (n in 0 until outFrames) {
            if ((n and 0xFFFF) == 0) {
                if (isCancelled()) throw CancellationException("İptal edildi")
                onProgress(n.toFloat() / outFrames)
            }
            val c = n * step
            val i0 = max(ceil(c - zw).toInt(), 0)
            val i1 = min(floor(c + zw).toInt(), inFrames - 1)
            var l = 0f
            var r = 0f
            for (i in i0..i1) {
                val d = abs(i - c) * RES
                val ti = d.toInt()
                val fr = (d - ti).toFloat()
                val h = table[ti] * (1f - fr) + table[ti + 1] * fr
                l += src[2 * i] * h
                r += src[2 * i + 1] * h
            }
            out[2 * n] = l.roundToInt().coerceIn(-32768, 32767).toShort()
            out[2 * n + 1] = r.roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}
