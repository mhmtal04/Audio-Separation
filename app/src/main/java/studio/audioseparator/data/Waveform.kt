package studio.audioseparator.data

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object Waveform {
    fun fromPcm(pcm: ShortArray, bars: Int): FloatArray {
        val frames = pcm.size / 2
        val out = FloatArray(bars)
        if (frames == 0) return out
        val per = max(1, frames / bars)
        val step = max(1, per / 512)
        for (b in 0 until bars) {
            var m = 0
            var i = b * per
            val end = min(frames, i + per)
            while (i < end) {
                val v = abs(pcm[2 * i].toInt())
                if (v > m) m = v
                i += step
            }
            out[b] = m / 32768f
        }
        return normalize(out)
    }

    /** 16-bit stereo WAV (44 baytlık başlık). Her çubuk için bölgeden bir blok örneklenir. */
    fun fromWav(file: File, bars: Int): FloatArray {
        val out = FloatArray(bars)
        RandomAccessFile(file, "r").use { raf ->
            val frames = (raf.length() - 44) / 4
            if (frames <= 0) return out
            val per = max(1L, frames / bars)
            val take = min(per, 16384L).toInt()
            val buf = ByteArray(take * 4)
            for (b in 0 until bars) {
                raf.seek(44 + b * per * 4)
                val n = raf.read(buf, 0, buf.size)
                var m = 0
                var i = 0
                while (i + 1 < n) {
                    val s = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
                    if (abs(s) > m) m = abs(s)
                    i += 4
                }
                out[b] = m / 32768f
            }
        }
        return normalize(out)
    }

    private fun normalize(a: FloatArray): FloatArray {
        val mx = a.maxOrNull() ?: 0f
        if (mx > 0f) for (i in a.indices) a[i] = sqrt(a[i] / mx)
        return a
    }
}
