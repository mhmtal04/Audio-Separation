package studio.audioseparator.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * htdemucs'in `_spec` / `_ispec` adımlarının birebir karşılığı (nfft=4096, hop=1024,
 * periyodik Hann, normalized=True, center=True, reflect pad).
 * Referans: python/spec_reference.py (numpy) ve demucs/htdemucs.py.
 * Dizilim: [f * T + t]. Tek iş parçacığı için (iç tamponlar paylaşılır).
 */
class DemucsSpec {
    companion object {
        const val NFFT = 4096
        const val HOP = 1024
        const val FREQ = 2048
        const val PAD = HOP / 2 * 3 // 1536
    }

    private val window = FloatArray(NFFT) { (0.5 - 0.5 * cos(2.0 * PI * it / NFFT)).toFloat() }
    private val fft = Fft(NFFT)
    private val fr = FloatArray(NFFT)
    private val fi = FloatArray(NFFT)
    private var padded = FloatArray(0)
    private var raw = FloatArray(0)
    private var env = FloatArray(0)
    private var envFrames = -1

    fun frames(length: Int) = (length + HOP - 1) / HOP

    private fun reflect(i: Int, n: Int): Int {
        var k = if (i < 0) -i else i
        if (k >= n) k = 2 * (n - 1) - k
        return k
    }

    /** x: tek kanal (uzunluk L). re/im: FREQ*T boyutlu çıktı. */
    fun stft(x: FloatArray, re: FloatArray, im: FloatArray) {
        val len = x.size
        val t = frames(len)
        val p1 = 2 * PAD + t * HOP
        val total = p1 + NFFT
        if (padded.size != total) padded = FloatArray(total)
        for (i in 0 until total) {
            val i1 = reflect(i - NFFT / 2, p1)
            padded[i] = x[reflect(i1 - PAD, len)]
        }
        val scale = 1f / sqrt(NFFT.toFloat())
        for (tt in 0 until t) {
            val s = (tt + 2) * HOP
            for (k in 0 until NFFT) {
                fr[k] = padded[s + k] * window[k]
                fi[k] = 0f
            }
            fft.transform(fr, fi)
            for (f in 0 until FREQ) {
                re[f * t + tt] = fr[f] * scale
                im[f * t + tt] = fi[f] * scale
            }
        }
    }

    /** re/im: FREQ*T girdi; out: outLen (=L) uzunluklu çıktı. */
    fun istft(re: FloatArray, im: FloatArray, t: Int, outLen: Int, out: FloatArray) {
        val nFrames = t + 4
        val rawLen = (nFrames - 1) * HOP + NFFT
        if (raw.size != rawLen) raw = FloatArray(rawLen) else raw.fill(0f)
        if (envFrames != t) {
            env = FloatArray(rawLen)
            for (j in 0 until nFrames) for (k in 0 until NFFT) env[j * HOP + k] += window[k] * window[k]
            envFrames = t
        }
        val scale = 1f / sqrt(NFFT.toFloat())
        for (tt in 0 until t) {
            for (f in 0 until FREQ) {
                fr[f] = re[f * t + tt]
                fi[f] = im[f * t + tt]
            }
            fr[FREQ] = 0f; fi[FREQ] = 0f
            for (f in 1 until FREQ) {
                fr[NFFT - f] = fr[f]
                fi[NFFT - f] = -fi[f]
            }
            fft.transform(fr, fi, inverse = true)
            val s = (tt + 2) * HOP
            for (k in 0 until NFFT) raw[s + k] += fr[k] * scale * window[k]
        }
        val a = NFFT / 2 + PAD
        for (i in 0 until outLen) out[i] = raw[a + i] / env[a + i]
    }
}
