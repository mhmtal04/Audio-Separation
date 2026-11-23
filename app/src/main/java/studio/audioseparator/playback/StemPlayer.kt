package studio.audioseparator.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Dört WAV kanalını örnek-hassasiyetinde senkron karıştırıp AudioTrack'e verir.
 * Kanal başına ses seviyesi, sessize alma ve solo desteklenir (hepsi açık = MIX).
 */
class StemPlayer(files: Map<String, File>, private val onEnded: () -> Unit) {
    companion object {
        const val SR = 44100
        const val HEADER = 44
        const val CHUNK = 4096
    }

    private val raf = files.mapValues { RandomAccessFile(it.value, "r") }
    val totalFrames: Long = (files.values.first().length() - HEADER) / 4
    @Volatile var positionFrames = 0L
    val volume = ConcurrentHashMap<String, Float>()
    val muted: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val solo: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile var isPlaying = false
        private set
    private var thread: Thread? = null

    fun play() {
        if (isPlaying) return
        if (positionFrames >= totalFrames) positionFrames = 0
        isPlaying = true
        thread = Thread({ loop() }, "stem-player").also { it.start() }
    }

    fun pause() {
        isPlaying = false
        thread?.join(1500)
    }

    fun seek(frame: Long) {
        positionFrames = frame.coerceIn(0, totalFrames)
    }

    fun release() {
        pause()
        raf.values.forEach { try { it.close() } catch (_: Exception) {} }
    }

    private fun gain(name: String, anySolo: Boolean): Float {
        val v = volume[name] ?: 1f
        return if (anySolo) (if (name in solo) v else 0f) else (if (name in muted) 0f else v)
    }

    private fun loop() {
        val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
            )
            .setBufferSizeInBytes(max(minBuf, CHUNK * 4) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val raw = ByteArray(CHUNK * 4)
        val acc = FloatArray(CHUNK * 2)
        val out = ShortArray(CHUNK * 2)
        var finished = false
        try {
            track.play()
            while (isPlaying) {
                val pos = positionFrames
                val n = min(CHUNK.toLong(), totalFrames - pos).toInt()
                if (n <= 0) { finished = true; break }
                acc.fill(0f)
                val anySolo = solo.isNotEmpty()
                for ((name, f) in raf) {
                    val g = gain(name, anySolo)
                    if (g == 0f) continue
                    f.seek(HEADER + pos * 4)
                    f.readFully(raw, 0, n * 4)
                    for (i in 0 until n * 2) {
                        val s = (raw[2 * i].toInt() and 0xFF) or (raw[2 * i + 1].toInt() shl 8)
                        acc[i] += s.toFloat() * g
                    }
                }
                for (i in 0 until n * 2) out[i] = acc[i].coerceIn(-32768f, 32767f).toInt().toShort()
                track.write(out, 0, n * 2)
                if (positionFrames == pos) positionFrames = pos + n
            }
            if (finished) track.stop()
        } catch (_: Exception) {
            // dosya okunamadı vb.: oynatmayı durdur
        } finally {
            track.release()
            if (finished) positionFrames = 0
            val wasPlaying = isPlaying
            isPlaying = false
            if (finished && wasPlaying) onEnded()
        }
    }
}
