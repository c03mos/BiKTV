package com.hu3h.biktv.data.ncm

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class CacheEntry(
    val body: String,
    val savedAtMs: Long
)

object NcmCacheStore {
    private val memory = ConcurrentHashMap<String, CacheEntry>()

    fun get(context: Context, key: String): CacheEntry? {
        memory[key]?.let { return it }
        val file = getFile(context, key)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val idx = text.indexOf('\n')
        if (idx <= 0) return null
        val ts = text.substring(0, idx).toLongOrNull() ?: return null
        val body = text.substring(idx + 1)
        return CacheEntry(body, ts).also { memory[key] = it }
    }

    fun put(context: Context, key: String, body: String) {
        val entry = CacheEntry(body, System.currentTimeMillis())
        memory[key] = entry
        val file = getFile(context, key)
        file.parentFile?.mkdirs()
        runCatching { file.writeText("${entry.savedAtMs}\n$body") }
    }

    private fun getFile(context: Context, key: String): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "BiKTV/cache")
        val name = sha256(key) + ".cache"
        return File(dir, name)
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
