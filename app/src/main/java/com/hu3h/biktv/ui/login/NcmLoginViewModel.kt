package com.hu3h.biktv.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hu3h.biktv.data.ncm.NcmApiClient
import com.hu3h.biktv.data.ncm.NcmQrLoginApi
import com.hu3h.biktv.data.ncm.NcmQrPollStatus
import com.hu3h.biktv.data.session.NcmSession
import com.hu3h.biktv.data.session.NcmSessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

class NcmLoginViewModel(
    private val sessionStore: NcmSessionStore,
    private val qrLoginApi: NcmQrLoginApi,
    private val apiClient: NcmApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(NcmLoginUiState())
    val uiState: StateFlow<NcmLoginUiState> = _uiState.asStateFlow()
    private var pollingJob: Job? = null

    fun loadSession() {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                _uiState.update {
                    val status = if (session == null) NcmLoginStatus.Idle else NcmLoginStatus.Success
                    it.copy(session = session, status = status)
                }
                if (session != null && session.nickname.isNullOrBlank()) {
                    try {
                        val info = apiClient.fetchUserInfo(session.cookie)
                        val updated = session.copy(
                            userId = info.userId.toString(),
                            nickname = info.nickname,
                            avatarUrl = info.avatarUrl
                        )
                        sessionStore.saveSession(updated)
                        _uiState.update {
                            it.copy(
                                session = updated,
                                message = "已获取用户信息",
                                debugInfo = "用户信息: ${info.nickname} (${info.userId})"
                            )
                        }
                    } catch (t: Throwable) {
                        _uiState.update { it.copy(message = "获取用户信息失败：${t.message}") }
                    }
                }
            }
        }
    }

    fun startQrLogin() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    status = NcmLoginStatus.QrReady,
                    message = "正在生成二维码..."
                )
            }
            try {
                val qr = qrLoginApi.generateQrCode()
                _uiState.update {
                    it.copy(
                        status = NcmLoginStatus.QrReady,
                        qrImageUrl = qr.url,
                        qrKey = qr.key,
                        message = "请使用网易云 App 扫码..."
                    )
                }
                startPolling(qr.key)
            } catch (t: Throwable) {
                onLoginError(t.message ?: "二维码生成失败")
            }
        }
    }

    private suspend fun startPolling(qrKey: String) {
        while (true) {
            val result = qrLoginApi.pollLogin(qrKey)
            _uiState.update {
                it.copy(
                    debugInfo = buildString {
                        append("poll code=")
                        append(result.rawCode ?: "-")
                        append("\n")
                        append(result.rawBody ?: "")
                    }
                )
            }
            when (result.status) {
                NcmQrPollStatus.NotScanned -> {
                    _uiState.update { it.copy(status = NcmLoginStatus.QrReady, message = "等待扫码...") }
                }
                NcmQrPollStatus.ScannedNotConfirmed -> {
                    _uiState.update { it.copy(status = NcmLoginStatus.Polling, message = "已扫码，等待确认...") }
                }
                NcmQrPollStatus.Expired -> {
                    onLoginError("二维码已失效，请重新获取")
                    return
                }
                NcmQrPollStatus.Success -> {
                    val cookieHeader = buildCookieHeader(result.cookieMap)
                    val session = NcmSession(
                        cookie = cookieHeader,
                        nickname = result.nickname,
                        avatarUrl = result.avatarUrl
                    )
                    _uiState.update {
                        it.copy(
                            status = NcmLoginStatus.Success,
                            session = session,
                            message = "登录成功",
                            qrImageUrl = null,
                            qrKey = null
                        )
                    }
                    pollingJob?.cancel()
                    pollingJob = null
                    onLoginSuccess(session)
                    return
                }
                NcmQrPollStatus.Error -> {
                    onLoginError(result.message.ifBlank { "登录失败" })
                    return
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun onLoginSuccess(session: NcmSession) {
        viewModelScope.launch {
            sessionStore.saveSession(session)
            _uiState.update {
                it.copy(status = NcmLoginStatus.Success, session = session, message = "登录成功")
            }
            try {
                val info = apiClient.fetchUserInfo(session.cookie)
                val updated = session.copy(
                    userId = info.userId.toString(),
                    nickname = info.nickname,
                    avatarUrl = info.avatarUrl
                )
                sessionStore.saveSession(updated)
                _uiState.update {
                    it.copy(
                        session = updated,
                        message = "已获取用户信息",
                        debugInfo = "用户信息: ${info.nickname} (${info.userId})"
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(message = "获取用户信息失败：${t.message}") }
            }
        }
    }

    fun onLoginError(reason: String) {
        _uiState.update { it.copy(status = NcmLoginStatus.Error(reason), message = reason) }
    }

    fun logout() {
        viewModelScope.launch {
            pollingJob?.cancel()
            sessionStore.clear()
            _uiState.update { it.copy(status = NcmLoginStatus.Idle, session = null) }
        }
    }

    fun refreshCookie() {
        viewModelScope.launch {
            val session = uiState.value.session
            if (session == null) {
                onLoginError("尚未登录")
                return@launch
            }
            try {
                _uiState.update { it.copy(message = "正在刷新 Cookie...") }
                val result = apiClient.refreshLoginToken(session.cookie)
                val newCookie = result.newCookie
                if (!newCookie.isNullOrBlank()) {
                    val updated = session.copy(cookie = newCookie)
                    sessionStore.saveSession(updated)
                    _uiState.update {
                        it.copy(session = updated, message = "Cookie 刷新完成")
                    }
                } else {
                    _uiState.update { it.copy(message = "Cookie 刷新完成") }
                }
            } catch (t: Throwable) {
                onLoginError(t.message ?: "Cookie 刷新失败")
            }
        }
    }

    fun fetchToplist() {
        viewModelScope.launch {
            val session = uiState.value.session ?: return@launch
            try {
                val body = apiClient.fetchToplist(session.cookie)
                _uiState.update { it.copy(debugInfo = "榜单列表:\n$body") }
            } catch (t: Throwable) {
                _uiState.update { it.copy(message = "获取榜单失败：${t.message}") }
            }
        }
    }

    fun fetchToplistDetail() {
        viewModelScope.launch {
            val session = uiState.value.session ?: return@launch
            try {
                val body = apiClient.fetchToplistDetail(session.cookie)
                _uiState.update { it.copy(debugInfo = "榜单详情:\n$body") }
            } catch (t: Throwable) {
                _uiState.update { it.copy(message = "获取榜单详情失败：${t.message}") }
            }
        }
    }

    fun fetchToplistDetailV2() {
        viewModelScope.launch {
            val session = uiState.value.session ?: return@launch
            try {
                val body = apiClient.fetchToplistDetailV2(session.cookie)
                _uiState.update { it.copy(debugInfo = "榜单详情V2:\n$body") }
            } catch (t: Throwable) {
                _uiState.update { it.copy(message = "获取榜单详情V2失败：${t.message}") }
            }
        }
    }

    fun fetchToplistById(id: Long) {
        viewModelScope.launch {
            val session = uiState.value.session ?: return@launch
            try {
                val body = apiClient.fetchToplistById(id, session.cookie)
                _uiState.update { it.copy(debugInfo = "榜单ID($id):\n$body") }
            } catch (t: Throwable) {
                _uiState.update { it.copy(message = "获取榜单ID失败：${t.message}") }
            }
        }
    }

    fun fetchArtistList(type: Int, area: Int?, initial: String?) {
        viewModelScope.launch {
            val session = uiState.value.session ?: return@launch
            try {
                val body = apiClient.fetchArtistList(
                    type = type,
                    area = area,
                    initial = initial,
                    offset = 0,
                    limit = 30,
                    cookieHeader = session.cookie
                )
                _uiState.update {
                    it.copy(debugInfo = "歌手列表(type=$type, area=$area, initial=$initial):\n$body")
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(message = "获取歌手列表失败：${t.message}") }
            }
        }
    }

    fun fetchArtistSongsSummary(artistId: Long) {
        viewModelScope.launch {
            val session = uiState.value.session ?: return@launch
            try {
                val body = apiClient.fetchArtistSongs(
                    artistId = artistId,
                    order = "hot",
                    offset = 0,
                    limit = 50,
                    cookieHeader = session.cookie
                )
                val summary = buildSongArtistSummary(body)
                _uiState.update { it.copy(debugInfo = "歌曲-歌手:\n$summary") }
            } catch (t: Throwable) {
                _uiState.update { it.copy(message = "获取歌手歌曲失败：${t.message}") }
            }
        }
    }

    private fun buildSongArtistSummary(jsonBody: String): String {
        return try {
            val json = JSONObject(jsonBody)
            val songs = json.optJSONArray("songs") ?: return jsonBody
            val sb = StringBuilder()
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                val name = song.optString("name")
                val artists = song.optJSONArray("artists")
                val artistNames = if (artists != null) {
                    val list = mutableListOf<String>()
                    for (j in 0 until artists.length()) {
                        val a = artists.optJSONObject(j)
                        if (a != null) list.add(a.optString("name"))
                    }
                    list.joinToString("/")
                } else {
                    ""
                }
                sb.append(name).append(" - ").append(artistNames).append("\n")
            }
            sb.toString()
        } catch (t: Throwable) {
            jsonBody
        }
    }

    private fun buildCookieHeader(cookieMap: Map<String, String>): String {
        if (cookieMap.isEmpty()) return ""
        return cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2000L
    }
}
