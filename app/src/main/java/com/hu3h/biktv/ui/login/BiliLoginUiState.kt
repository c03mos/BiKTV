package com.hu3h.biktv.ui.login

import com.hu3h.biktv.data.session.BiliSession

data class BiliLoginUiState(
    val status: BiliLoginStatus = BiliLoginStatus.Idle,
    val qrImageUrl: String? = null,
    val qrKey: String? = null,
    val session: BiliSession? = null,
    val message: String? = null
)

sealed interface BiliLoginStatus {
    data object Idle : BiliLoginStatus
    data object QrReady : BiliLoginStatus
    data object Polling : BiliLoginStatus
    data object Success : BiliLoginStatus
    data class Error(val reason: String) : BiliLoginStatus
}
