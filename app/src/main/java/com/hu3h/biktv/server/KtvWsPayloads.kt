package com.hu3h.biktv.server

import com.hu3h.biktv.data.KtvDownloadManager
import com.hu3h.biktv.player.KtvPlayerManager
import com.hu3h.biktv.player.PlayerStatus
import org.json.JSONArray
import org.json.JSONObject

object KtvWsPayloads {
    @JvmStatic
    fun buildSnapshot(): String {
        val obj = JSONObject()
        obj.put("type", "snapshot")
        obj.put("player", buildPlayerStateJson())
        obj.put("queue", buildQueueJson())
        obj.put("downloads", buildDownloadsJson())
        return obj.toString()
    }

    @JvmStatic
    fun buildPlayerState(): String {
        val obj = JSONObject()
        obj.put("type", "player_state")
        obj.put("player", buildPlayerStateJson())
        return obj.toString()
    }

    @JvmStatic
    fun buildQueueUpdate(): String {
        val obj = JSONObject()
        obj.put("type", "queue_updated")
        obj.put("queue", buildQueueJson())
        return obj.toString()
    }

    @JvmStatic
    fun buildDownloadUpdate(): String {
        val obj = JSONObject()
        obj.put("type", "download_progress")
        obj.put("downloads", buildDownloadsJson())
        return obj.toString()
    }

    @JvmStatic
    fun buildQueueJson(): JSONObject {
        val state = KtvPlayerManager.uiState.value
        val obj = JSONObject()
        obj.put("current", state.current?.let { item ->
            JSONObject()
                .put("title", item.title)
                .put("artist", item.artist ?: "")
        })
        val arr = JSONArray()
        state.queue.forEach { item ->
            arr.put(
                JSONObject()
                    .put("title", item.title)
                    .put("artist", item.artist ?: "")
            )
        }
        obj.put("list", arr)
        return obj
    }

    @JvmStatic
    fun buildPlayerStateJson(): JSONObject {
        val state = KtvPlayerManager.uiState.value
        return JSONObject()
            .put("status", statusText(state.status))
            .put("positionMs", state.positionMs)
            .put("durationMs", state.durationMs)
    }

    @JvmStatic
    fun buildDownloadsJson(): JSONArray {
        val arr = JSONArray()
        KtvDownloadManager.snapshot().forEach { task ->
            val obj = JSONObject()
            obj.put("title", task.title)
            obj.put("artist", task.artist ?: "")
            obj.put("status", task.status)
            obj.put("progress", task.progress)
            arr.put(obj)
        }
        return arr
    }

    private fun statusText(status: PlayerStatus): String = when (status) {
        PlayerStatus.Idle -> "idle"
        PlayerStatus.Loading -> "loading"
        PlayerStatus.Playing -> "playing"
        PlayerStatus.Paused -> "paused"
        PlayerStatus.Ended -> "ended"
        PlayerStatus.Error -> "error"
    }
}
