package com.hu3h.biktv.data

import android.content.Context
import com.hu3h.biktv.data.bili.BiliVideoApi
import com.hu3h.biktv.player.KtvPlayerManager
import com.hu3h.biktv.server.KtvWsPayloads
import com.hu3h.biktv.server.KtorServer
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DownloadTask(
    val uuid: String,
    val title: String,
    val artist: String?,
    val keyword: String,
    val status: String,
    val progress: Int,
    val error: String? = null
)

object KtvDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = ConcurrentHashMap<String, DownloadTask>()
    private val mutex = Mutex()
    private val api = BiliVideoApi()

    fun snapshot(): List<DownloadTask> = tasks.values.sortedBy { it.title.lowercase() }

    fun enqueue(
        context: Context,
        title: String,
        artist: String?,
        keyword: String,
        onReady: (String, String, String?, String, String?) -> Unit
    ): DownloadTask {
        val uuid = UUID.nameUUIDFromBytes("${title.trim()}|${artist.orEmpty().trim()}".toByteArray())
            .toString()
        val existing = tasks[uuid]
        if (existing != null) return existing

        val outputDir = getOutputDir(context)
        val file = File(outputDir, "$uuid.mp4")
        if (file.exists()) {
            val audioFile = File(outputDir, "$uuid.m4a").takeIf { it.exists() }
            val meta = ResourceMeta(
                uuid = uuid,
                title = title,
                artist = artist,
                filePath = file.absolutePath,
                audioPath = audioFile?.absolutePath,
                sizeBytes = file.length()
            )
            ResourceIndex.put(context, meta)
            onReady(uuid, title, artist, file.absolutePath, audioFile?.absolutePath)
            val done = DownloadTask(uuid, title, artist, keyword, "done", 100)
            tasks[uuid] = done
            broadcast()
            return done
        }

        val queued = DownloadTask(uuid, title, artist, keyword, "queued", 0)
        tasks[uuid] = queued
        broadcast()
        scope.launch {
            mutex.withLock {
                tasks[uuid] = queued.copy(status = "downloading", progress = 1)
                broadcast()
            }
            runCatching {
                val result = api.downloadFirstVideo(
                    keyword = keyword,
                    outputDir = outputDir,
                    fixedUuid = uuid
                ) { downloaded, total ->
                    val p = if (total <= 0L) 1 else ((downloaded * 100) / total).toInt().coerceIn(1, 99)
                    tasks[uuid] = tasks[uuid]?.copy(status = "downloading", progress = p) ?: queued
                    broadcast()
                }
                val fileSize = File(result.filePath).length()
                val meta = ResourceMeta(
                    uuid = uuid,
                    title = title,
                    artist = artist,
                    filePath = result.filePath,
                    audioPath = result.audioPath,
                    sizeBytes = fileSize
                )
                ResourceIndex.put(context, meta)
                onReady(uuid, title, artist, result.filePath, result.audioPath)
                tasks[uuid] = DownloadTask(uuid, title, artist, keyword, "done", 100)
                broadcast()
                KtvPlayerManager.rescanResources()
            }.onFailure { err ->
                tasks[uuid] = DownloadTask(uuid, title, artist, keyword, "error", 0, err.message)
                broadcast()
            }
        }
        return queued
    }

    private fun broadcast() {
        KtorServer.broadcast(KtvWsPayloads.buildDownloadUpdate())
    }

    private fun getOutputDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "BiKTV/song").apply { if (!exists()) mkdirs() }
    }
}
