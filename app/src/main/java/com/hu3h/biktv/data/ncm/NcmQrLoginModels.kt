package com.hu3h.biktv.data.ncm

data class NcmQrCode(
    val url: String,
    val key: String
)

data class NcmQrPollResult(
    val status: NcmQrPollStatus,
    val message: String,
    val cookieMap: Map<String, String>,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val rawCode: Int? = null,
    val rawBody: String? = null
)

enum class NcmQrPollStatus {
    NotScanned,
    ScannedNotConfirmed,
    Expired,
    Success,
    Error
}
