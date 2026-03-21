package com.hu3h.biktv.asr

import android.content.Context
import com.hu3h.biktv.data.ResourceIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AsrTask(
    val id: String,
    val status: String,
    val srtPath: String? = null,
    val error: String? = null
)

object AsrTaskManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = ConcurrentHashMap<String, AsrTask>()

    fun startByMeta(
        context: Context,
        title: String?,
        artist: String?,
        filePath: String?
    ): AsrTask {
        val meta = when {
            !filePath.isNullOrBlank() -> ResourceIndex.findByFilePath(filePath)
            !title.isNullOrBlank() -> ResourceIndex.findByTitleArtist(title, artist)
                ?: ResourceIndex.all().firstOrNull { it.title.equals(title, ignoreCase = true) }
            else -> null
        } ?: return AsrTask(id = "", status = "not_found", error = "NOT_FOUND")

        val sourceFile = meta.audioPath?.let { File(it) }?.takeIf { it.exists() }
            ?: File(meta.filePath).takeIf { it.exists() }
            ?: return AsrTask(id = "", status = "not_found", error = "NOT_FOUND")

        val taskId = UUID.randomUUID().toString()
        val outFile = File(sourceFile.parentFile, "${meta.uuid}.srt")
        tasks[taskId] = AsrTask(id = taskId, status = "running")

        scope.launch {
            runCatching {
                val srt = SherpaStreamingAsr.transcribeToSrt(context, sourceFile)
                outFile.writeText(srt)
                tasks[taskId] = AsrTask(id = taskId, status = "done", srtPath = outFile.absolutePath)
            }.onFailure { err ->
                tasks[taskId] = AsrTask(id = taskId, status = "error", error = err.message)
            }
        }
        return tasks[taskId]!!
    }

    fun get(taskId: String): AsrTask? = tasks[taskId]

    fun toJson(task: AsrTask): String {
        val obj = JSONObject()
        obj.put("id", task.id)
        obj.put("status", task.status)
        obj.put("srtPath", task.srtPath ?: "")
        obj.put("error", task.error ?: "")
        return obj.toString()
    }
}
