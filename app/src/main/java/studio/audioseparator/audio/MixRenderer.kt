package studio.audioseparator.audio

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CancellationException
import kotlin.math.min

/** Seçilen kanalları kazançlarıyla toplayıp tek bir 16-bit WAV'a yazar. */
object MixRenderer {
    private const val CHUNK = 16384

    fun render(
        stems: Map<String, File>,
        gains: Map<String, Float>,
        out: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        val active = stems.filter { (gains[it.key] ?: 0f) > 0f }
        require(active.isNotEmpty()) { "En az bir kanal açık olmalı." }
        val files = active.mapValues { RandomAccessFile(it.value, "r") }
        var ok = false
        try {
            val total = (active.values.first().length() - WavWriter.HEADER) / 4
            val raw = ByteArray(CHUNK * 4)
            val acc = FloatArray(CHUNK * 2)
            WavWriter(out, DemucsEngine.SR).use { w ->
                var pos = 0L
                while (pos < total) {
                    if (isCancelled()) throw CancellationException("İptal edildi")
                    val n = min(CHUNK.toLong(), total - pos).toInt()
                    acc.fill(0f)
                    for ((name, f) in files) {
                        val g = gains[name] ?: 0f
                        f.seek(WavWriter.HEADER + pos * 4)
                        f.readFully(raw, 0, n * 4)
                        for (i in 0 until n * 2) {
                            val s = (raw[2 * i].toInt() and 0xFF) or (raw[2 * i + 1].toInt() shl 8)
                            acc[i] += s / 32767f * g
                        }
                    }
                    w.write(acc, n * 2)
                    pos += n
                    onProgress(pos.toFloat() / total)
                }
            }
            ok = true
        } finally {
            files.values.forEach { try { it.close() } catch (_: Exception) {} }
            if (!ok) out.delete()
        }
    }
}
