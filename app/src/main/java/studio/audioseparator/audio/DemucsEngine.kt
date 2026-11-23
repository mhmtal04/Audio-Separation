package studio.audioseparator.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CancellationException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import studio.audioseparator.dsp.DemucsSpec

/**
 * htdemucs ONNX çekirdeği (python/export_htdemucs_onnx.py ile üretilir):
 *   girdi  mix  (1,2,SEG)          ham stereo segment (normalize edilmiş)
 *          cac  (1,4,FREQ,T)       [L.re, L.im, R.re, R.im] spektrogram
 *   çıktı  freq (1,4,4,FREQ,T)     kaynak başına CaC maske çıktısı (denormalize)
 *          time (1,4,2,SEG)        kaynak başına zaman dalı
 * STFT/iSTFT, ağırlıklı overlap-add ve normalizasyon burada (Kotlin) yapılır.
 */
class DemucsEngine(modelFile: File) : AutoCloseable {
    companion object {
        const val SR = 44100
        const val SEG = 343980          // 7.8 sn @ 44.1 kHz (htdemucs eğitim segmenti)
        const val FREQ = DemucsSpec.FREQ
        const val T = 336
        const val OVERLAP = 0.25
        val SOURCES = listOf("drums", "bass", "other", "vocals") // htdemucs.sources sırası
    }

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val o = OrtSession.SessionOptions()
        o.setIntraOpNumThreads(max(2, Runtime.getRuntime().availableProcessors() / 2))
        o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        session = env.createSession(modelFile.absolutePath, o)
        val names = session.inputNames
        require("mix" in names && "cac" in names) {
            "Model beklenen formatta değil. python/export_htdemucs_onnx.py ile üretilmiş model gerekir."
        }
    }

    private fun directBuf(floats: Int): FloatBuffer =
        ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    /** pcm: 44.1 kHz stereo interleaved. Dönüş: kaynak adı -> WAV dosyası. */
    fun separate(
        pcm: ShortArray,
        outDir: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): Map<String, File> {
        val frames = pcm.size / 2
        require(frames > 0) { "Boş ses." }
        outDir.mkdirs()

        // demucs: ref = wav.mean(0); wav = (wav - ref.mean()) / ref.std()
        var sum = 0.0
        for (i in 0 until frames) sum += (pcm[2 * i] + pcm[2 * i + 1]) / 65536.0
        val mean = sum / frames
        var sq = 0.0
        for (i in 0 until frames) {
            val d = (pcm[2 * i] + pcm[2 * i + 1]) / 65536.0 - mean
            sq += d * d
        }
        var std = sqrt(sq / max(1, frames - 1))
        if (std < 1e-6) std = 1.0
        val meanF = mean.toFloat()
        val stdF = std.toFloat()

        val stride = (SEG * (1 - OVERLAP)).toInt()
        val nSeg = if (frames <= SEG) 1 else ceil((frames - SEG).toDouble() / stride).toInt() + 1

        val half = SEG / 2
        val w = FloatArray(SEG) { (if (it < half) it + 1 else SEG - it).toFloat() / half }

        val acc = Array(SOURCES.size) { Array(2) { FloatArray(SEG) } }
        val wsum = FloatArray(SEG)
        val files = SOURCES.associateWith { File(outDir, "$it.wav") }
        val writers = SOURCES.associateWith { WavWriter(files.getValue(it), SR) }

        val spec = DemucsSpec()
        val inCh = Array(2) { FloatArray(SEG) }
        val cRe = FloatArray(FREQ * T)
        val cIm = FloatArray(FREQ * T)
        val tmp = FloatArray(SEG)
        val timeArr = FloatArray(SEG)
        val mixBuf = directBuf(2 * SEG)
        val cacBuf = directBuf(4 * FREQ * T)
        var ok = false
        try {
            for (k in 0 until nSeg) {
                if (isCancelled()) throw CancellationException("İptal edildi")
                val start = k * stride
                val last = k == nSeg - 1

                for (i in 0 until SEG) {
                    val idx = start + i
                    if (idx < frames) {
                        inCh[0][i] = (pcm[2 * idx] / 32768f - meanF) / stdF
                        inCh[1][i] = (pcm[2 * idx + 1] / 32768f - meanF) / stdF
                    } else {
                        inCh[0][i] = 0f; inCh[1][i] = 0f
                    }
                }
                mixBuf.clear(); mixBuf.put(inCh[0]); mixBuf.put(inCh[1]); mixBuf.rewind()
                cacBuf.clear()
                for (c in 0 until 2) {
                    spec.stft(inCh[c], cRe, cIm)
                    cacBuf.put(cRe); cacBuf.put(cIm)
                }
                cacBuf.rewind()

                val mixT = OnnxTensor.createTensor(env, mixBuf, longArrayOf(1, 2, SEG.toLong()))
                val cacT = OnnxTensor.createTensor(env, cacBuf, longArrayOf(1, 4, FREQ.toLong(), T.toLong()))
                try {
                    session.run(mapOf("mix" to mixT, "cac" to cacT)).use { res ->
                        val freq = (res.get(0) as OnnxTensor).floatBuffer
                        val time = (res.get(1) as OnnxTensor).floatBuffer
                        for (s in SOURCES.indices) for (c in 0 until 2) {
                            freq.position((s * 4 + c * 2) * FREQ * T); freq.get(cRe)
                            freq.position((s * 4 + c * 2 + 1) * FREQ * T); freq.get(cIm)
                            spec.istft(cRe, cIm, T, SEG, tmp)
                            time.position((s * 2 + c) * SEG); time.get(timeArr)
                            val a = acc[s][c]
                            for (i in 0 until SEG) a[i] += (tmp[i] + timeArr[i]) * w[i]
                        }
                    }
                } finally {
                    mixT.close(); cacT.close()
                }
                for (i in 0 until SEG) wsum[i] += w[i]

                // Kesinleşen örnekleri yaz: son segmentte kalan hepsi, aksi halde ilk `stride` örnek.
                val n = if (last) min(SEG, frames - start) else stride
                val outBuf = FloatArray(n * 2)
                for (s in SOURCES.indices) {
                    for (i in 0 until n) {
                        val inv = 1f / wsum[i]
                        outBuf[2 * i] = acc[s][0][i] * inv * stdF + meanF
                        outBuf[2 * i + 1] = acc[s][1][i] * inv * stdF + meanF
                    }
                    writers.getValue(SOURCES[s]).write(outBuf, n * 2)
                }
                if (!last) {
                    for (s in SOURCES.indices) for (c in 0 until 2) {
                        val a = acc[s][c]
                        System.arraycopy(a, stride, a, 0, SEG - stride)
                        java.util.Arrays.fill(a, SEG - stride, SEG, 0f)
                    }
                    System.arraycopy(wsum, stride, wsum, 0, SEG - stride)
                    java.util.Arrays.fill(wsum, SEG - stride, SEG, 0f)
                }
                onProgress((k + 1f) / nSeg)
            }
            ok = true
        } finally {
            writers.values.forEach { try { it.close() } catch (_: Exception) {} }
            if (!ok) files.values.forEach { it.delete() }
        }
        return files
    }

    override fun close() {
        session.close()
    }
}
