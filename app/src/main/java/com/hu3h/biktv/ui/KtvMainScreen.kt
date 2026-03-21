package com.hu3h.biktv.ui

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.hu3h.biktv.player.KtvPlayerViewModel
import com.hu3h.biktv.player.PlayerStatus
import com.hu3h.biktv.ui.login.generateQrBitmap
import com.hu3h.biktv.util.NetworkUtils
import kotlinx.coroutines.delay

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun KtvMainScreen(
    modifier: Modifier = Modifier,
    viewModel: KtvPlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val resources by viewModel.resourcesState.collectAsState()
    val lottieComposition by rememberLottieComposition(
        LottieCompositionSpec.JsonString(EMPTY_STATE_LOTTIE)
    )
    val showControls = remember { mutableStateOf(true) }
    val showSettings = remember { mutableStateOf(false) }
    val showRemoteQr = remember { mutableStateOf(false) }
    val showQueue = remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val context = LocalContext.current
    val remoteUrl = remember {
        val ip = NetworkUtils.getLocalIp(context) ?: "0.0.0.0"
        "http://$ip:8889/remote/"
    }
    val qrSize = 220.dp
    val qrSizePx = with(LocalDensity.current) { qrSize.roundToPx() }
    val remoteQrBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = remoteUrl) {
        value = generateQrBitmap(remoteUrl, qrSizePx)
    }

    val bg = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )
    )

    if (showSettings.value) {
        SettingsScreen(modifier = modifier) { showSettings.value = false }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures { showControls.value = !showControls.value }
            }
    ) {
        if (resources.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LottieAnimation(
                    composition = lottieComposition,
                    iterations = Int.MAX_VALUE,
                    modifier = Modifier.size(180.dp)
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text("等待点歌", style = MaterialTheme.typography.titleLarge)
                Text(
                    "请通过局域网点歌或添加本地资源",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.size(16.dp))
                androidx.compose.material3.Button(onClick = { showRemoteQr.value = true }) {
                    Text("打开点歌二维码")
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.getPlayer()
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            val isPlaying = uiState.status == PlayerStatus.Playing

            LaunchedEffect(isPlaying, showControls.value) {
                if (isPlaying && showControls.value) {
                    delay(2500)
                    showControls.value = false
                }
            }

            AnimatedVisibility(
                visible = showControls.value,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(if (isLandscape) 28.dp else 18.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(
                                horizontal = if (isLandscape) 22.dp else 16.dp,
                                vertical = if (isLandscape) 12.dp else 10.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = {
                            val p = viewModel.getPlayer()
                            p.seekTo((p.currentPosition - 10_000L).coerceAtLeast(0L))
                        }) {
                            Icon(Icons.Outlined.FastRewind, contentDescription = "快退", tint = Color.White)
                        }
                        Surface(
                            modifier = Modifier.size(if (isLandscape) 52.dp else 46.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        ) {
                            IconButton(onClick = {
                                val p = viewModel.getPlayer()
                                if (p.isPlaying) p.pause() else p.play()
                            }) {
                                Icon(
                                    if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                    contentDescription = "播放暂停",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(onClick = {
                            val p = viewModel.getPlayer()
                            p.seekTo((p.currentPosition + 10_000L).coerceAtLeast(0L))
                        }) {
                            Icon(Icons.Outlined.FastForward, contentDescription = "快进", tint = Color.White)
                        }
                        IconButton(onClick = { viewModel.skip() }) {
                            Icon(Icons.Outlined.SkipNext, contentDescription = "切歌", tint = Color.White)
                        }
                        IconButton(onClick = { showRemoteQr.value = true }) {
                            Icon(Icons.Outlined.QrCode2, contentDescription = "点歌二维码", tint = Color.White)
                        }
                        IconButton(onClick = { showSettings.value = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (resources.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                tonalElevation = 2.dp
            ) {
                IconButton(onClick = { showQueue.value = true }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "播放列表", tint = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = showQueue.value,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .pointerInput(Unit) {
                        detectTapGestures { showQueue.value = false }
                    }
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .width(260.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("播放列表", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        val current = uiState.current
                        if (current != null) {
                            Text(
                                "当前：${current.title}${current.artist?.let { " - $it" } ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        } else {
                            Text("当前：无", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                        Spacer(modifier = Modifier.size(4.dp))
                        if (uiState.queue.isEmpty()) {
                            Text("队列为空", color = Color.White.copy(alpha = 0.7f))
                        } else {
                            uiState.queue.take(10).forEach { item ->
                                Text(
                                    "• ${item.title}${item.artist?.let { " - $it" } ?: ""}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showRemoteQr.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .pointerInput(Unit) {
                        detectTapGestures { showRemoteQr.value = false }
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val bitmap = remoteQrBitmap
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "点歌二维码",
                                modifier = Modifier.size(qrSize)
                            )
                        }
                        Text("扫码进入点歌页面", color = Color.White)
                        Text(
                            remoteUrl,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

private const val EMPTY_STATE_LOTTIE = """
{
  "v": "5.7.4",
  "fr": 30,
  "ip": 0,
  "op": 90,
  "w": 200,
  "h": 200,
  "nm": "pulse",
  "ddd": 0,
  "assets": [],
  "layers": [
    {
      "ddd": 0,
      "ind": 1,
      "ty": 4,
      "nm": "ring",
      "sr": 1,
      "ks": {
        "o": { "a": 0, "k": 70 },
        "r": { "a": 0, "k": 0 },
        "p": { "a": 0, "k": [100, 100, 0] },
        "a": { "a": 0, "k": [0, 0, 0] },
        "s": { "a": 1, "k": [
          { "t": 0, "s": [20, 20, 100] },
          { "t": 45, "s": [85, 85, 100] },
          { "t": 90, "s": [20, 20, 100] }
        ]}
      },
      "shapes": [
        {
          "ty": "el",
          "p": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [120, 120] },
          "nm": "Ellipse Path"
        },
        {
          "ty": "st",
          "c": { "a": 0, "k": [0.6, 0.65, 0.7, 1] },
          "o": { "a": 0, "k": 100 },
          "w": { "a": 0, "k": 8 },
          "lc": 2,
          "lj": 2,
          "nm": "Stroke"
        },
        {
          "ty": "tr",
          "p": { "a": 0, "k": [0, 0] },
          "a": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [100, 100] },
          "r": { "a": 0, "k": 0 },
          "o": { "a": 0, "k": 100 }
        }
      ],
      "ip": 0,
      "op": 90,
      "st": 0,
      "bm": 0
    }
  ]
}
"""
