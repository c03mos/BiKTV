package com.hu3h.biktv.player

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.hu3h.biktv.data.ResourceIndex
import com.hu3h.biktv.server.KtvWsPayloads
import com.hu3h.biktv.server.KtorServer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object KtvPlayerManager {
    private var initialized = false
    private lateinit var app: Application
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val current = MutableStateFlow<QueueItem?>(null)
    private val queue = MutableStateFlow<List<QueueItem>>(emptyList())
    private val status = MutableStateFlow(PlayerStatus.Idle)
    private val positionMs = MutableStateFlow(0L)
    private val durationMs = MutableStateFlow(0L)
    private val error = MutableStateFlow<String?>(null)
    private val resources = MutableStateFlow<List<ResourceItem>>(emptyList())
    private val resourcesLoading = MutableStateFlow(false)

    val uiState: StateFlow<PlayerUiState> = combine(
        current, queue, status, positionMs, durationMs
    ) { c, q, s, p, d ->
        PlayerUiState(
            current = c,
            queue = q,
            status = s,
            positionMs = p,
            durationMs = d,
            error = null
        )
    }.combine(error) { base, err ->
        base.copy(error = err)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        PlayerUiState(null, emptyList(), PlayerStatus.Idle, 0, 0, null)
    )

    val resourcesState: StateFlow<List<ResourceItem>> =
        resources.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val resourcesLoadingState: StateFlow<Boolean> =
        resourcesLoading.stateIn(scope, SharingStarted.Eagerly, false)

    fun init(context: Context) {
        if (initialized) return
        app = context.applicationContext as Application
        player = ExoPlayer.Builder(app).build()
        ResourceIndex.load(app)
        initPlayerListeners()
        scope.launch {
            while (true) {
                if (player.isPlaying) {
                    positionMs.value = player.currentPosition
                    durationMs.value = player.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
        rescanResources()
        initialized = true
    }

    fun getPlayer(): ExoPlayer = player

    fun rescanResources() {
        scope.launch(Dispatchers.IO) {
            resourcesLoading.value = true
            val dir = getResourceDir()
            val list = dir.listFiles()?.filter { it.isFile }?.map {
                val rawName = it.nameWithoutExtension
                val uuidFromName = if (isUuid(rawName)) rawName else null
                val metaFromIndex = uuidFromName?.let { id -> ResourceIndex.get(id) }
                val displayTitle = metaFromIndex?.title ?: rawName
                val displayArtist = metaFromIndex?.artist
                val displayAudio = metaFromIndex?.audioPath
                val uuid = metaFromIndex?.uuid
                    ?: UUID.nameUUIDFromBytes(rawName.toByteArray(Charsets.UTF_8)).toString()
                if (ResourceIndex.get(uuid) == null) {
                    ResourceIndex.put(
                        app,
                        com.hu3h.biktv.data.ResourceMeta(
                            uuid = uuid,
                            title = displayTitle,
                            artist = displayArtist,
                            filePath = it.absolutePath,
                            audioPath = displayAudio,
                            sizeBytes = it.length()
                        )
                    )
                }
                ResourceItem(
                    name = displayTitle,
                    filePath = it.absolutePath,
                    sizeBytes = it.length()
                )
            }?.sortedBy { it.name.lowercase() } ?: emptyList()
            resources.value = list
            resourcesLoading.value = false

            if (current.value == null && list.isNotEmpty()) {
                val first = list.first()
                val meta = ResourceIndex.findByFilePath(first.filePath)
                val queueItem = QueueItem(
                    uuid = UUID.randomUUID().toString(),
                    title = first.name,
                    artist = null,
                    filePath = first.filePath,
                    audioPath = meta?.audioPath
                )
                scope.launch {
                    mutex.withLock {
                        current.value = queueItem
                    }
                    playCurrentInternal()
                    broadcastState()
                    broadcastQueue()
                }
            }
        }
    }

    fun addResourceToQueue(item: ResourceItem, top: Boolean = false) {
        val meta = ResourceIndex.findByFilePath(item.filePath)
        val queueItem = QueueItem(
            uuid = UUID.randomUUID().toString(),
            title = item.name,
            artist = null,
            filePath = item.filePath,
            audioPath = meta?.audioPath
        )
        addToQueue(queueItem, top)
    }

    fun addQueueItem(title: String, artist: String?, filePath: String, uuid: String) {
        val item = QueueItem(
            uuid = uuid,
            title = title,
            artist = artist,
            filePath = filePath,
            audioPath = null
        )
        addToQueue(item, top = false)
    }

    fun addQueueItem(
        title: String,
        artist: String?,
        filePath: String,
        audioPath: String?,
        uuid: String
    ) {
        val item = QueueItem(
            uuid = uuid,
            title = title,
            artist = artist,
            filePath = filePath,
            audioPath = audioPath
        )
        addToQueue(item, top = false)
    }

    fun addToQueue(item: QueueItem, top: Boolean = false) {
        scope.launch {
            mutex.withLock {
                if (current.value == null) {
                    current.value = item
                    playCurrentInternal()
                    broadcastState()
                    broadcastQueue()
                    return@withLock
                }
                val list = queue.value.toMutableList()
                list.removeAll { it.uuid == item.uuid }
                if (top) {
                    list.add(0, item)
                } else {
                    list.add(item)
                }
                queue.value = list
                broadcastQueue()
            }
        }
    }

    fun topQueueItem(uuid: String) {
        scope.launch {
            mutex.withLock {
                val list = queue.value.toMutableList()
                val idx = list.indexOfFirst { it.uuid == uuid }
                if (idx >= 0) {
                    val item = list.removeAt(idx)
                    list.add(0, item)
                    queue.value = list
                    broadcastQueue()
                }
            }
        }
    }

    fun removeQueueItem(uuid: String) {
        scope.launch {
            mutex.withLock {
                val list = queue.value.toMutableList()
                list.removeAll { it.uuid == uuid }
                queue.value = list
                broadcastQueue()
            }
        }
    }

    fun clearQueue() {
        scope.launch {
            mutex.withLock {
                queue.value = emptyList()
                broadcastQueue()
            }
        }
    }

    fun skip() {
        scope.launch { playNextInternal() }
    }

    fun playPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    private fun initPlayerListeners() {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> status.value = PlayerStatus.Idle
                    Player.STATE_BUFFERING -> status.value = PlayerStatus.Loading
                    Player.STATE_READY -> {
                        status.value = if (player.isPlaying) PlayerStatus.Playing else PlayerStatus.Paused
                        durationMs.value = player.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        status.value = PlayerStatus.Ended
                        scope.launch { playNextInternal() }
                    }
                }
                broadcastState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                status.value = if (isPlaying) PlayerStatus.Playing else PlayerStatus.Paused
                broadcastState()
            }

            override fun onPlayerError(errorObj: androidx.media3.common.PlaybackException) {
                error.value = errorObj.message
                status.value = PlayerStatus.Error
                broadcastState()
                scope.launch { playNextInternal() }
            }
        })
    }

    private suspend fun playCurrentInternal() {
        val item = current.value ?: return
        val file = File(item.filePath)
        if (!file.exists()) {
            error.value = "FILE_NOT_FOUND"
            status.value = PlayerStatus.Error
            broadcastState()
            playNextInternal()
            return
        }
        val audioFile = item.audioPath?.let { File(it) }?.takeIf { it.exists() }
        error.value = null
        status.value = PlayerStatus.Loading
        broadcastState()
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(app)
        val videoSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.fromFile(file)))
        val mediaSource = if (audioFile != null) {
            val audioSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.fromFile(audioFile)))
            androidx.media3.exoplayer.source.MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }
        player.setMediaSource(mediaSource, true)
        player.prepare()
        player.play()
    }

    private suspend fun playNextInternal() {
        mutex.withLock {
            val list = queue.value.toMutableList()
            val next = if (list.isNotEmpty()) list.removeAt(0) else null
            queue.value = list
            current.value = next
            if (next == null) {
                player.stop()
                status.value = PlayerStatus.Idle
                broadcastState()
                broadcastQueue()
                return
            }
        }
        playCurrentInternal()
        broadcastQueue()
    }

    private fun getResourceDir(): File {
        val base = app.getExternalFilesDir(null) ?: app.filesDir
        return File(base, "BiKTV/song").apply { if (!exists()) mkdirs() }
    }

    private fun isUuid(value: String): Boolean {
        return value.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))
    }

    private fun broadcastQueue() {
        KtorServer.broadcast(KtvWsPayloads.buildQueueUpdate())
    }

    private fun broadcastState() {
        KtorServer.broadcast(KtvWsPayloads.buildPlayerState())
    }
}
