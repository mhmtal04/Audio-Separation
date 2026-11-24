package studio.audioseparator.data

import android.content.Context
import java.io.File
import org.json.JSONObject

data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val durationMs: Long,
    val format: String,
    val dir: File,
    val sizeBytes: Long,
    val stems: Map<String, File>,
    val cover: File?,
)

object ProjectStore {
    val ORDER = listOf("vocals", "drums", "bass", "other")

    fun root(ctx: Context) = File(ctx.filesDir, "projects").also { it.mkdirs() }

    fun writeMeta(dir: File, name: String, createdAt: Long, durationMs: Long, format: String) {
        File(dir, "meta.json").writeText(
            JSONObject().put("name", name).put("createdAt", createdAt)
                .put("durationMs", durationMs).put("format", format).toString()
        )
    }

    fun load(dir: File): Project? {
        val j = try { JSONObject(File(dir, "meta.json").readText()) } catch (_: Exception) { return null }
        val stems = ORDER.mapNotNull { n -> File(dir, "$n.wav").takeIf { it.exists() }?.let { n to it } }.toMap()
        if (stems.size < ORDER.size) return null
        val size = dir.listFiles()?.sumOf { it.length() } ?: 0L
        return Project(
            id = dir.name,
            name = j.optString("name", dir.name),
            createdAt = j.optLong("createdAt", dir.name.toLongOrNull() ?: 0L),
            durationMs = j.optLong("durationMs", 0L),
            format = j.optString("format", "?"),
            dir = dir,
            sizeBytes = size,
            stems = stems,
            cover = File(dir, "cover.bin").takeIf { it.exists() },
        )
    }

    fun list(ctx: Context): List<Project> =
        root(ctx).listFiles()?.filter { it.isDirectory }?.mapNotNull { load(it) }
            ?.sortedByDescending { it.createdAt } ?: emptyList()
}

data class ArchiveItem(
    val id: String,
    val name: String,
    val createdAt: Long,
    val durationMs: Long,
    val file: File,
    val sizeBytes: Long,
)

object ArchiveStore {
    fun dir(ctx: Context) = File(ctx.filesDir, "archive").also { it.mkdirs() }

    fun writeMeta(ctx: Context, id: String, name: String, createdAt: Long, durationMs: Long) {
        File(dir(ctx), "$id.json").writeText(
            JSONObject().put("name", name).put("createdAt", createdAt).put("durationMs", durationMs).toString()
        )
    }

    fun list(ctx: Context): List<ArchiveItem> =
        dir(ctx).listFiles()?.filter { it.extension == "json" }?.mapNotNull { mf ->
            val id = mf.nameWithoutExtension
            val wav = File(mf.parentFile, "$id.wav")
            if (!wav.exists()) return@mapNotNull null
            try {
                val j = JSONObject(mf.readText())
                ArchiveItem(id, j.optString("name", id), j.optLong("createdAt"), j.optLong("durationMs"), wav, wav.length())
            } catch (_: Exception) { null }
        }?.sortedByDescending { it.createdAt } ?: emptyList()

    fun delete(item: ArchiveItem) {
        item.file.delete()
        File(item.file.parentFile, "${item.id}.json").delete()
    }
}
