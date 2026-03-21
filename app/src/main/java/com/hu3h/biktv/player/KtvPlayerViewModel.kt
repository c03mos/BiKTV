package com.hu3h.biktv.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.StateFlow

class KtvPlayerViewModel(app: Application) : AndroidViewModel(app) {
    init {
        KtvPlayerManager.init(app)
    }

    val uiState: StateFlow<PlayerUiState> = KtvPlayerManager.uiState
    val resourcesState: StateFlow<List<ResourceItem>> = KtvPlayerManager.resourcesState
    val resourcesLoadingState: StateFlow<Boolean> = KtvPlayerManager.resourcesLoadingState

    fun getPlayer(): ExoPlayer = KtvPlayerManager.getPlayer()

    fun rescanResources() = KtvPlayerManager.rescanResources()

    fun addResourceToQueue(item: ResourceItem, top: Boolean = false) =
        KtvPlayerManager.addResourceToQueue(item, top)

    fun addToQueue(item: QueueItem, top: Boolean = false) =
        KtvPlayerManager.addToQueue(item, top)

    fun topQueueItem(uuid: String) = KtvPlayerManager.topQueueItem(uuid)

    fun removeQueueItem(uuid: String) = KtvPlayerManager.removeQueueItem(uuid)

    fun clearQueue() = KtvPlayerManager.clearQueue()

    fun skip() = KtvPlayerManager.skip()

    fun playPause() = KtvPlayerManager.playPause()
}
