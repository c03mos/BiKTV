package com.hu3h.biktv.player

data class QueueItem(
    val uuid: String,
    val title: String,
    val artist: String?,
    val filePath: String,
    val audioPath: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class ResourceItem(
    val name: String,
    val filePath: String,
    val sizeBytes: Long
)

enum class PlayerStatus {
    Idle,
    Loading,
    Playing,
    Paused,
    Ended,
    Error
}

data class PlayerUiState(
    val current: QueueItem?,
    val queue: List<QueueItem>,
    val status: PlayerStatus,
    val positionMs: Long,
    val durationMs: Long,
    val error: String?
)
