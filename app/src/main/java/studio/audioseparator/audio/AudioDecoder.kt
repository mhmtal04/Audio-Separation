package studio.audioseparator.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

data class AudioInfo(
    val name: String,
    val format: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
)

/** Stereo, interleaved (L,R,L,R...) 16-bit PCM, kaynak örnekleme oranında. */
class DecodedAudio(val samples: ShortArray, val sampleRate: Int)

object AudioDecoder {

    private fun audioTrack(ex: MediaExtractor): Pair<Int, MediaFormat> {
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return i to f
        }
        throw IllegalArgumentException("Dosyada ses akışı bulunamadı.")
    }

    fun probe(ctx: Context, uri: Uri, name: String): AudioInfo {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            val (_, f) = audioTrack(ex)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: "?"
            return AudioInfo(
                name = name,
                format = mime.substringAfter('/').uppercase(),
                durationMs = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) / 1000 else 0,
                sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
            )
        } finally {
            ex.release()
        }
    }

    fun decode(
        ctx: Context,
        uri: Uri,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): DecodedAudio {
        val ex = MediaExtractor()
        ex.setDataSource(ctx, uri, null)
        val (track, fmt) = audioTrack(ex)
        ex.selectTrack(track)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false

        val estimate = if (durUs > 0) (durUs * sr / 1_000_000L * 2L).toInt() + 65536 else 1 shl 22
        var buf = ShortArray(estimate)
        var n = 0

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outDone = false
        try {
            while (!outDone) {
                if (isCancelled()) throw CancellationException("İptal edildi")
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val ib = codec.getInputBuffer(i)!!
                        val sz = ex.readSampleData(ib, 0)
                        if (sz < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, sz, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = f.containsKey("pcm-encoding") && f.getInteger("pcm-encoding") == 4 // ENCODING_PCM_FLOAT
                } else if (o >= 0) {
                    val ob = codec.getOutputBuffer(o)!!
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    ob.order(ByteOrder.nativeOrder())
                    val perSample = if (floatPcm) 4 else 2
                    val frames = info.size / perSample / ch
                    if (n + frames * 2 > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, n + frames * 2))
                    if (floatPcm) {
                        val fb = ob.asFloatBuffer()
                        for (k in 0 until frames) {
                            val l = fb.get()
                            var r = l
                            if (ch > 1) { r = fb.get(); for (e in 2 until ch) fb.get() }
                            buf[n++] = toShort(l); buf[n++] = toShort(r)
                        }
                    } else {
                        val sb = ob.asShortBuffer()
                        for (k in 0 until frames) {
                            val l = sb.get()
                            var r = l
                            if (ch > 1) { r = sb.get(); for (e in 2 until ch) sb.get() }
                            buf[n++] = l; buf[n++] = r
                        }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    if (durUs > 0) onProgress((n / 2f / sr * 1_000_000f / durUs).coerceIn(0f, 1f))
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            ex.release()
        }
        if (n == 0) throw IllegalStateException("Ses çözülemedi (0 örnek).")
        return DecodedAudio(buf.copyOf(n), sr)
    }

    private fun toShort(f: Float): Short =
        (f * 32767f).toInt().coerceIn(-32768, 32767).toShort()
}
