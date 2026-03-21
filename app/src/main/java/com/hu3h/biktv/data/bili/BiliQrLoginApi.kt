package com.hu3h.biktv.data.bili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class BiliQrLoginApi(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun generateQrCode(): BiliQrCode = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GENERATE_URL)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("QR generate failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val data = json.getJSONObject("data")
            val url = data.getString("url")
            val key = data.getString("qrcode_key")
            BiliQrCode(url = url, key = key)
        }
    }

    suspend fun pollLogin(qrKey: String): BiliQrPollResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$POLL_URL?qrcode_key=$qrKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext BiliQrPollResult(
                    status = BiliQrPollStatus.Error,
                    message = "HTTP ${response.code}",
                    refreshToken = null,
                    timestampMs = null,
                    cookieMap = emptyMap()
                )
            }
            val cookieMap = parseSetCookie(response.headers.values("Set-Cookie"))

            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val data = json.getJSONObject("data")

            val code = data.optInt("code", -1)
            val message = data.optString("message", "")
            val refreshToken = data.optString("refresh_token").ifBlank { null }
            val timestampMs = data.optLong("timestamp").takeIf { it > 0 }

            val status = when (code) {
                86101 -> BiliQrPollStatus.NotScanned
                86090 -> BiliQrPollStatus.ScannedNotConfirmed
                86038 -> BiliQrPollStatus.Expired
                0 -> BiliQrPollStatus.Success
                else -> BiliQrPollStatus.Error
            }

            BiliQrPollResult(
                status = status,
                message = message,
                refreshToken = refreshToken,
                timestampMs = timestampMs,
                cookieMap = cookieMap
            )
        }
    }

    private fun parseSetCookie(setCookieHeaders: List<String>): Map<String, String> {
        if (setCookieHeaders.isEmpty()) return emptyMap()
        val map = linkedMapOf<String, String>()
        for (header in setCookieHeaders) {
            val part = header.substringBefore(";")
            val idx = part.indexOf("=")
            if (idx <= 0) continue
            val name = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) {
                map[name] = value
            }
        }
        return map
    }

    companion object {
        private const val GENERATE_URL =
            "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
        private const val POLL_URL =
            "https://passport.bilibili.com/x/passport-login/web/qrcode/poll"
    }
}
