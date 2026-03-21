package com.hu3h.biktv.ui.login

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.hu3h.biktv.data.ncm.NcmApiClient
import com.hu3h.biktv.data.ncm.NcmQrLoginApi
import com.hu3h.biktv.data.session.NcmSessionStoreImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@Composable
fun NcmLoginScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: NcmLoginViewModel = viewModel(
        factory = NcmLoginViewModelFactory(context)
    )
    val uiState by viewModel.uiState.collectAsState()
    val loggedIn = uiState.session != null || uiState.status == NcmLoginStatus.Success

    LaunchedEffect(Unit) {
        viewModel.loadSession()
    }
    LaunchedEffect(uiState.session, uiState.status, uiState.qrImageUrl) {
        if (!loggedIn &&
            uiState.status == NcmLoginStatus.Idle &&
            uiState.qrImageUrl.isNullOrBlank()
        ) {
            viewModel.startQrLogin()
        }
    }

    val qrSize = 220.dp
    val qrSizePx = with(LocalDensity.current) { qrSize.roundToPx() }
    val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = uiState.qrImageUrl) {
        val data = uiState.qrImageUrl
        value = if (data.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.Default) { generateQrBitmap(data, qrSizePx) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1114))
            .padding(24.dp)
    ) {
        if (onBack != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                onClick = onBack
            ) {
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                }
            }
        }

        if (loggedIn) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!uiState.session?.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = uiState.session?.avatarUrl,
                        contentDescription = "用户头像",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = uiState.session?.nickname ?: "已登录",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { viewModel.refreshCookie() }) { Text("刷新 Cookie") }
                    Button(onClick = { viewModel.logout() }) { Text("退出登录") }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val bitmap = qrBitmap
                        if (bitmap == null) {
                            Text("二维码加载中", color = Color.White)
                            Text("请稍候…", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
                        } else {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "NCM QR Code",
                                modifier = Modifier.size(qrSize)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = { viewModel.startQrLogin() }) { Text("刷新二维码") }
                        }
                    }
                }
            }
        }
    }
}

private class NcmLoginViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NcmLoginViewModel::class.java)) {
            return NcmLoginViewModel(
                sessionStore = NcmSessionStoreImpl(context),
                qrLoginApi = NcmQrLoginApi(OkHttpClient()),
                apiClient = NcmApiClient(OkHttpClient())
            ) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
