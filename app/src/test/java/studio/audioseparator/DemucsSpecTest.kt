package studio.audioseparator

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.audioseparator.dsp.DemucsSpec
import studio.audioseparator.dsp.Fft

class DemucsSpecTest {
    @Test
    fun fftMatchesNaiveDft() {
        val n = 64
        val re = FloatArray(n) { sin(it * 0.3f) + 0.1f * it }
        val im = FloatArray(n)
        val expRe = DoubleArray(n); val expIm = DoubleArray(n)
        for (k in 0 until n) for (j in 0 until n) {
            val a = -2.0 * PI * k * j / n
            expRe[k] += re[j] * kotlin.math.cos(a); expIm[k] += re[j] * sin(a)
        }
        Fft(n).transform(re, im)
        for (k in 0 until n) {
            assertEquals(expRe[k], re[k].toDouble(), 1e-3)
            assertEquals(expIm[k], im[k].toDouble(), 1e-3)
        }
    }

    @Test
    fun stftIstftRoundTripInterior() {
        val len = 343980
        val spec = DemucsSpec()
        val t = spec.frames(len)
        assertEquals(336, t)
        val x = FloatArray(len) { (0.5 * sin(it * 0.01) + 0.3 * sin(it * 0.173)).toFloat() }
        val re = FloatArray(DemucsSpec.FREQ * t)
        val im = FloatArray(DemucsSpec.FREQ * t)
        spec.stft(x, re, im)
        val y = FloatArray(len)
        spec.istft(re, im, t, len, y)
        var maxErr = 0f
        for (i in 4096 until len - 4096) maxErr = maxOf(maxErr, abs(x[i] - y[i]))
        assertTrue("max err $maxErr", maxErr < 1e-3f)
    }
}
