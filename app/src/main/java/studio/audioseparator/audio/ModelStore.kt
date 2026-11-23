package studio.audioseparator.audio

import android.content.Context
import android.net.Uri
import java.io.File

object ModelStore {
    private const val MIN_BYTES = 10L * 1024 * 1024

    fun file(ctx: Context) = File(File(ctx.filesDir, "models"), "htdemucs.onnx")

    fun isInstalled(ctx: Context) = file(ctx).let { it.exists() && it.length() > MIN_BYTES }

    /** Kullanıcının seçtiği .onnx dosyasını uygulama deposuna kopyalar. */
    fun import(ctx: Context, uri: Uri) {
        val dst = file(ctx)
        dst.parentFile!!.mkdirs()
        val tmp = File(dst.parentFile, "htdemucs.onnx.part")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
        if (tmp.length() < MIN_BYTES) {
            tmp.delete()
            throw IllegalArgumentException("Seçilen dosya bir model dosyası gibi görünmüyor (çok küçük).")
        }
        if (dst.exists()) dst.delete()
        if (!tmp.renameTo(dst)) throw IllegalStateException("Model kaydedilemedi.")
    }
}
