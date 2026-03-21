package com.hu3h.biktv.data.ncm

import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class NcmQrLoginApi(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun generateQrCode(): NcmQrCode = withContext(Dispatchers.IO) {
        ensureBaseCookies()
        val formBody = FormBody.Builder()
            .add("type", "3")
            .build()

        val request = Request.Builder()
            .url("$UNIQUE_KEY_URL?timestamp=${System.currentTimeMillis()}")
            .post(formBody)
            .addHeader("User-Agent", USER_AGENT_API)
            .addHeader("Accept", "*/*")
            .addHeader("Cookie", buildCookieHeader())
            .addHeader("X-Real-IP", randomChineseIp())
            .addHeader("X-Forwarded-For", randomChineseIp())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("NCM QR generate failed: HTTP ${response.code}")
            }
            updateCookieMap(response.headers.values("Set-Cookie"))
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val code = json.optInt("code", -1)
            if (code != 200) {
                error("NCM QR generate failed: code=$code, body=$body")
            }
            val data = json.optJSONObject("data")
            val key = (data?.optString("unikey") ?: json.optString("unikey")).ifBlank {
                error("NCM QR generate failed: missing unikey, body=$body")
            }
            val chainId = generateChainId()
            val url = "https://music.163.com/login?codekey=$key&chainId=$chainId"
            NcmQrCode(url = url, key = key)
        }
    }

    suspend fun pollLogin(qrKey: String): NcmQrPollResult = withContext(Dispatchers.IO) {
        ensureBaseCookies()
        val formBody = FormBody.Builder()
            .add("key", qrKey)
            .add("type", "3")
            .build()

        val request = Request.Builder()
            .url("$QR_CHECK_URL?timestamp=${System.currentTimeMillis()}")
            .post(formBody)
            .addHeader("User-Agent", USER_AGENT_API)
            .addHeader("Accept", "*/*")
            .addHeader("Cookie", buildCookieHeader())
            .addHeader("X-Real-IP", randomChineseIp())
            .addHeader("X-Forwarded-For", randomChineseIp())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext NcmQrPollResult(
                    status = NcmQrPollStatus.Error,
                    message = "HTTP ${response.code}",
                    cookieMap = emptyMap()
                )
            }
            updateCookieMap(response.headers.values("Set-Cookie"))

            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val code = json.optInt("code", -1)
            val message = json.optString("message", "")
            val nickname = json.optString("nickname").ifBlank { null }
            val avatarUrl = json.optString("avatarUrl").ifBlank { null }

            val status = when (code) {
                801 -> NcmQrPollStatus.NotScanned
                802 -> NcmQrPollStatus.ScannedNotConfirmed
                800 -> NcmQrPollStatus.Expired
                803 -> NcmQrPollStatus.Success
                else -> NcmQrPollStatus.Error
            }

            NcmQrPollResult(
                status = status,
                message = message,
                cookieMap = cookieMap.toMap(),
                nickname = nickname,
                avatarUrl = avatarUrl,
                rawCode = code,
                rawBody = body
            )
        }
    }

    private val cookieMap = linkedMapOf<String, String>()

    private fun updateCookieMap(setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        for (header in setCookieHeaders) {
            val part = header.substringBefore(";")
            val idx = part.indexOf("=")
            if (idx <= 0) continue
            val name = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) {
                cookieMap[name] = value
            }
        }
    }

    private fun buildCookieHeader(): String {
        if (cookieMap.isEmpty()) return ""
        return cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun ensureBaseCookies() {
        if (cookieMap["os"].isNullOrBlank()) cookieMap["os"] = "pc"
        if (cookieMap["appver"].isNullOrBlank()) cookieMap["appver"] = "3.1.17.204416"
        if (cookieMap["osver"].isNullOrBlank()) cookieMap["osver"] =
            "Microsoft-Windows-10-Professional-build-19045-64bit"
        if (cookieMap["channel"].isNullOrBlank()) cookieMap["channel"] = "netease"
        if (cookieMap["deviceId"].isNullOrBlank()) cookieMap["deviceId"] = generateDeviceId()
        if (cookieMap["sDeviceId"].isNullOrBlank()) cookieMap["sDeviceId"] =
            "unknown-${SecureRandom().nextInt(1_000_000)}"
        if (cookieMap["_ntes_nuid"].isNullOrBlank()) cookieMap["_ntes_nuid"] = randomHex(32)
        if (cookieMap["_ntes_nnid"].isNullOrBlank()) cookieMap["_ntes_nnid"] =
            "${cookieMap["_ntes_nuid"]},${System.currentTimeMillis()}"
        if (cookieMap["WNMCID"].isNullOrBlank()) {
            cookieMap["WNMCID"] = "${randomAlpha(6)}.${System.currentTimeMillis()}.01.0"
        }
        if (cookieMap["WEVNSM"].isNullOrBlank()) cookieMap["WEVNSM"] = "1.0.0"
        if (cookieMap["__remember_me"].isNullOrBlank()) cookieMap["__remember_me"] = "true"
        if (cookieMap["ntes_kaola_ad"].isNullOrBlank()) cookieMap["ntes_kaola_ad"] = "1"
    }

    private fun generateChainId(): String {
        val version = "v1"
        val deviceId = cookieMap["sDeviceId"] ?: "unknown-${SecureRandom().nextInt(1_000_000)}"
        val platform = "web"
        val action = "login"
        val timestamp = System.currentTimeMillis()
        return "${version}_${deviceId}_${platform}_${action}_${timestamp}"
    }

    private fun generateDeviceId(): String {
        val hex = "0123456789ABCDEF"
        val sb = StringBuilder(52)
        repeat(52) {
            sb.append(hex[SecureRandom().nextInt(hex.length)])
        }
        return sb.toString()
    }

    private fun randomAlpha(len: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz"
        val sb = StringBuilder(len)
        repeat(len) { sb.append(chars[SecureRandom().nextInt(chars.length)]) }
        return sb.toString()
    }

    private fun randomHex(len: Int): String {
        val chars = "0123456789abcdef"
        val sb = StringBuilder(len)
        repeat(len) { sb.append(chars[SecureRandom().nextInt(chars.length)]) }
        return sb.toString()
    }

    private fun randomChineseIp(): String {
        val a = 116
        val b = 10 + SecureRandom().nextInt(80)
        val c = 1 + SecureRandom().nextInt(254)
        val d = 1 + SecureRandom().nextInt(254)
        return "$a.$b.$c.$d"
    }

    companion object {
        private const val UNIQUE_KEY_URL = "https://music.163.com/api/login/qrcode/unikey"
        private const val QR_CHECK_URL = "https://music.163.com/api/login/qrcode/client/login"
        private const val USER_AGENT_API =
            "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
    }
}
