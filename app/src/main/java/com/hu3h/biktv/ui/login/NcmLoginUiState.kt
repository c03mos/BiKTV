package com.hu3h.biktv.ui.login

import com.hu3h.biktv.data.session.NcmSession

data class NcmLoginUiState(
    val status: NcmLoginStatus = NcmLoginStatus.Idle,
    val qrImageUrl: String? = null,
    val qrKey: String? = null,
    val session: NcmSession? = null,
    val message: String? = null,
    val debugInfo: String? = null
)

sealed interface NcmLoginStatus {
    data object Idle : NcmLoginStatus
    data object QrReady : NcmLoginStatus
    data object Polling : NcmLoginStatus
    data object Success : NcmLoginStatus
    data class Error(val reason: String) : NcmLoginStatus
}
