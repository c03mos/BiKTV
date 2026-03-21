package com.hu3h.biktv.data.bili

data class BiliQrCode(
    val url: String,
    val key: String
)

data class BiliQrPollResult(
    val status: BiliQrPollStatus,
    val message: String,
    val refreshToken: String?,
    val timestampMs: Long?,
    val cookieMap: Map<String, String>
)

enum class BiliQrPollStatus {
    NotScanned,
    ScannedNotConfirmed,
    Expired,
    Success,
    Error
}
