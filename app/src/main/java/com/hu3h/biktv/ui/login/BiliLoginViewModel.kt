package com.hu3h.biktv.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hu3h.biktv.data.bili.BiliCookieRefreshApi
import com.hu3h.biktv.data.bili.BiliQrLoginApi
import com.hu3h.biktv.data.bili.BiliQrPollStatus
import com.hu3h.biktv.data.bili.BiliUserInfoApi
import com.hu3h.biktv.data.session.BiliSession
import com.hu3h.biktv.data.session.BiliSessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BiliLoginViewModel(
    private val sessionStore: BiliSessionStore,
    private val qrLoginApi: BiliQrLoginApi,
    private val cookieRefreshApi: BiliCookieRefreshApi,
    private val userInfoApi: BiliUserInfoApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(BiliLoginUiState())
    val uiState: StateFlow<BiliLoginUiState> = _uiState.asStateFlow()
    private var pollingJob: Job? = null

    fun loadSession() {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                _uiState.update {
                    val status = if (session == null) {
                        BiliLoginStatus.Idle
                    } else {
                        BiliLoginStatus.Success
                    }
                    it.copy(session = session, status = status)
                }
            }
        }
    }

    fun startQrLogin() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    status = BiliLoginStatus.QrReady,
                    message = "正在生成二维码..."
                )
            }
            try {
                val qr = qrLoginApi.generateQrCode()
                _uiState.update {
                    it.copy(
                        status = BiliLoginStatus.QrReady,
                        qrImageUrl = qr.url,
                        qrKey = qr.key,
                        message = "请使用 Bilibili App 扫码..."
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
            when (result.status) {
                BiliQrPollStatus.NotScanned -> {
                    _uiState.update { it.copy(status = BiliLoginStatus.QrReady, message = "等待扫码...") }
                }
                BiliQrPollStatus.ScannedNotConfirmed -> {
                    _uiState.update { it.copy(status = BiliLoginStatus.Polling, message = "已扫码，等待确认...") }
                }
                BiliQrPollStatus.Expired -> {
                    onLoginError("二维码已失效，请重新获取")
                    return
                }
                BiliQrPollStatus.Success -> {
                    val cookieHeader = buildCookieHeader(result.cookieMap)
                    val csrf = result.cookieMap["bili_jct"]
                    val userId = result.cookieMap["DedeUserID"]
                    val session = BiliSession(
                        cookie = cookieHeader,
                        csrf = csrf,
                        userId = userId,
                        refreshToken = result.refreshToken,
                        expiresAt = result.timestampMs
                    )
                    onLoginSuccess(session)
                    return
                }
                BiliQrPollStatus.Error -> {
                    onLoginError(result.message.ifBlank { "登录失败" })
                    return
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    fun onLoginSuccess(session: BiliSession) {
        viewModelScope.launch {
            sessionStore.saveSession(session)
            _uiState.update {
                it.copy(status = BiliLoginStatus.Success, session = session, message = "登录成功")
            }
            pollingJob?.cancel()
            pollingJob = null
            try {
                val info = userInfoApi.fetchUserInfo(session.cookie)
                val updated = session.copy(
                    userId = info.mid,
                    nickname = info.nickname,
                    avatarUrl = info.avatarUrl,
                    level = info.level
                )
                sessionStore.saveSession(updated)
                _uiState.update { it.copy(session = updated, message = "已获取用户信息") }
            } catch (t: Throwable) {
                _uiState.update { it.copy(message = "获取用户信息失败：${t.message}") }
            }
        }
    }

    fun onLoginError(reason: String) {
        val friendly = if (reason.contains("StandaloneCoroutine was cancelled")) {
            "操作已取消"
        } else {
            reason
        }
        _uiState.update { it.copy(status = BiliLoginStatus.Error(friendly), message = friendly) }
    }

    fun logout() {
        viewModelScope.launch {
            pollingJob?.cancel()
            sessionStore.clear()
            _uiState.update { it.copy(status = BiliLoginStatus.Idle, session = null) }
        }
    }

    fun refreshCookie(force: Boolean = false) {
        viewModelScope.launch {
            val session = uiState.value.session
            if (session == null) {
                onLoginError("尚未登录")
                return@launch
            }
            val refreshToken = session.refreshToken
            if (refreshToken.isNullOrBlank()) {
                onLoginError("缺少 refresh_token")
                return@launch
            }
            val csrf = session.csrf ?: extractCookieValue(session.cookie, "bili_jct")
            if (csrf.isNullOrBlank()) {
                onLoginError("缺少 csrf")
                return@launch
            }

            try {
                _uiState.update { it.copy(message = "检查 Cookie 是否需要刷新...") }
                val info = cookieRefreshApi.checkNeedRefresh(session.cookie, csrf)
                if (!info.needRefresh && !force) {
                    _uiState.update { it.copy(message = "当前无需刷新 Cookie") }
                    return@launch
                }
                if (info.timestampMs <= 0L) {
                    onLoginError("刷新时间戳无效")
                    return@launch
                }

                _uiState.update { it.copy(message = "正在刷新 Cookie...") }
                val correspondPath = cookieRefreshApi.buildCorrespondPath(info.timestampMs)
                val refreshCsrf = cookieRefreshApi.fetchRefreshCsrf(correspondPath, session.cookie)
                val oldRefreshToken = refreshToken
                val refreshResult = cookieRefreshApi.refreshCookie(
                    cookieHeader = session.cookie,
                    csrf = csrf,
                    refreshCsrf = refreshCsrf,
                    refreshToken = refreshToken
                )

                val cookieMap = refreshResult.cookieMap
                val newCookieHeader = buildCookieHeader(cookieMap).ifBlank { session.cookie }
                val newCsrf = cookieMap["bili_jct"] ?: csrf
                val newUserId = cookieMap["DedeUserID"] ?: session.userId
                val newRefreshToken = refreshResult.refreshToken ?: refreshToken

                val newSession = session.copy(
                    cookie = newCookieHeader,
                    csrf = newCsrf,
                    userId = newUserId,
                    refreshToken = newRefreshToken
                )
                sessionStore.saveSession(newSession)

                try {
                    cookieRefreshApi.confirmRefresh(newCookieHeader, newCsrf, oldRefreshToken)
                } catch (t: Throwable) {
                    _uiState.update { it.copy(message = "Cookie 已刷新，但确认失败：${t.message}") }
                    return@launch
                }

                _uiState.update { it.copy(message = "Cookie 刷新完成") }
            } catch (t: Throwable) {
                if (t.message == "NOT_LOGGED_IN") {
                    sessionStore.clear()
                    _uiState.update {
                        it.copy(
                            status = BiliLoginStatus.Idle,
                            session = null,
                            qrImageUrl = null,
                            qrKey = null,
                            message = "登录已失效，请重新扫码"
                        )
                    }
                } else {
                    onLoginError(t.message ?: "Cookie 刷新失败")
                }
            }
        }
    }

    private fun extractCookieValue(cookieHeader: String, name: String): String? {
        val parts = cookieHeader.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("$name=")) {
                return trimmed.substringAfter("=").ifBlank { null }
            }
        }
        return null
    }

    private fun buildCookieHeader(cookieMap: Map<String, String>): String {
        if (cookieMap.isEmpty()) return ""
        val names = listOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")
        return names.mapNotNull { name ->
            cookieMap[name]?.let { "$name=$it" }
        }.joinToString("; ")
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2000L
    }
}
