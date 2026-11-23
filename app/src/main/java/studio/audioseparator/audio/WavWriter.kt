package studio.audioseparator.audio

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16-bit PCM stereo WAV, akış halinde yazılır; kapanışta başlık güncellenir. */
class WavWriter(private val file: File, private val sampleRate: Int) : Closeable {
    private val out = BufferedOutputStream(FileOutputStream(file), 1 shl 16)
    private var dataBytes = 0L

    init { out.write(ByteArray(HEADER)) }

    /** interleaved float (-1..1), count = float sayısı. */
    fun write(interleaved: FloatArray, count: Int) {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val s = Math.round(interleaved[i] * 32767f).coerceIn(-32768, 32767)
            bytes[2 * i] = (s and 0xFF).toByte()
            bytes[2 * i + 1] = (s shr 8).toByte()
        }
        out.write(bytes)
        dataBytes += bytes.size
    }

    override fun close() {
        out.flush()
        out.close()
        val h = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()); h.putInt((36 + dataBytes).toInt())
        h.put("WAVE".toByteArray()); h.put("fmt ".toByteArray()); h.putInt(16)
        h.putShort(1); h.putShort(2); h.putInt(sampleRate); h.putInt(sampleRate * 4)
        h.putShort(4); h.putShort(16)
        h.put("data".toByteArray()); h.putInt(dataBytes.toInt())
        RandomAccessFile(file, "rw").use { it.seek(0); it.write(h.array()) }
    }

    companion object { const val HEADER = 44 }
}
