package com.hu3h.biktv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class ResourceMeta(
    val uuid: String,
    val title: String,
    val artist: String?,
    val filePath: String,
    val audioPath: String? = null,
    val sizeBytes: Long,
    val updatedAtMs: Long = System.currentTimeMillis()
)

object ResourceIndex {
    private val cache = ConcurrentHashMap<String, ResourceMeta>()
    private var loaded = false

    fun get(uuid: String): ResourceMeta? = cache[uuid]

    fun findByTitleArtist(title: String, artist: String?): ResourceMeta? {
        val keyTitle = title.trim()
        val keyArtist = artist?.trim().orEmpty()
        return cache.values.firstOrNull {
            it.title.equals(keyTitle, ignoreCase = true) &&
                it.artist.orEmpty().equals(keyArtist, ignoreCase = true)
        }
    }

    fun findByFilePath(filePath: String): ResourceMeta? {
        val key = filePath.trim()
        return cache.values.firstOrNull { it.filePath == key }
    }

    fun all(): List<ResourceMeta> = cache.values.sortedBy { it.title.lowercase() }

    fun put(context: Context, meta: ResourceMeta) {
        cache[meta.uuid] = meta
        save(context)
    }

    fun remove(context: Context, uuid: String) {
        cache.remove(uuid)
        save(context)
    }

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val file = getIndexFile(context)
        if (!file.exists()) return
        runCatching {
            val text = file.readText()
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val meta = ResourceMeta(
                    uuid = obj.optString("uuid"),
                    title = obj.optString("title"),
                    artist = obj.optString("artist").ifBlank { null },
                    filePath = obj.optString("filePath"),
                    audioPath = obj.optString("audioPath").ifBlank { null },
                    sizeBytes = obj.optLong("sizeBytes"),
                    updatedAtMs = obj.optLong("updatedAtMs")
                )
                if (meta.uuid.isNotBlank() && meta.filePath.isNotBlank()) {
                    cache[meta.uuid] = meta
                }
            }
        }
    }

    private fun save(context: Context) {
        val file = getIndexFile(context)
        val arr = JSONArray()
        cache.values.forEach { meta ->
            val obj = JSONObject()
            obj.put("uuid", meta.uuid)
            obj.put("title", meta.title)
            obj.put("artist", meta.artist ?: "")
            obj.put("filePath", meta.filePath)
            obj.put("audioPath", meta.audioPath ?: "")
            obj.put("sizeBytes", meta.sizeBytes)
            obj.put("updatedAtMs", meta.updatedAtMs)
            arr.put(obj)
        }
        file.parentFile?.mkdirs()
        file.writeText(arr.toString())
    }

    private fun getIndexFile(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "BiKTV/resource_index.json")
    }
}
