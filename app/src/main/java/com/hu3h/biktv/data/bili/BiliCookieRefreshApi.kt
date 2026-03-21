package com.hu3h.biktv.data.bili

import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class BiliCookieInfo(
    val needRefresh: Boolean,
    val timestampMs: Long
)

data class BiliCookieRefreshResult(
    val cookieMap: Map<String, String>,
    val refreshToken: String?
)

class BiliCookieRefreshApi(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun checkNeedRefresh(cookieHeader: String, csrf: String?): BiliCookieInfo =
        withContext(Dispatchers.IO) {
            val url = if (csrf.isNullOrBlank()) {
                COOKIE_INFO_URL
            } else {
                "$COOKIE_INFO_URL?csrf=$csrf"
            }
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Cookie", cookieHeader)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Cookie info failed: HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val code = json.optInt("code", -1)
                if (code == -101) {
                    error("NOT_LOGGED_IN")
                }
                if (code != 0) {
                    error("Cookie info failed: code=$code")
                }
                val data = json.getJSONObject("data")
                val refresh = data.optBoolean("refresh", false)
                val timestamp = data.optLong("timestamp", 0L)
                BiliCookieInfo(needRefresh = refresh, timestampMs = timestamp)
            }
        }

    fun buildCorrespondPath(timestampMs: Long): String {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(
                Base64.decode(
                    PUBLIC_KEY_PEM
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replace("\n", "")
                        .trim(),
                    Base64.DEFAULT
                )
            )
        )

        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding").apply {
            init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
                )
            )
        }

        val encrypted = cipher.doFinal("refresh_$timestampMs".toByteArray())
        return encrypted.joinToString("") { "%02x".format(it) }
    }

    suspend fun fetchRefreshCsrf(correspondPath: String, cookieHeader: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$CORRESPOND_URL$correspondPath")
                .get()
                .addHeader("Cookie", cookieHeader)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Fetch refresh_csrf failed: HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val match = REFRESH_CSRF_REGEX.find(body)
                match?.groupValues?.getOrNull(1)
                    ?: error("refresh_csrf not found in HTML")
            }
        }

    suspend fun refreshCookie(
        cookieHeader: String,
        csrf: String,
        refreshCsrf: String,
        refreshToken: String
    ): BiliCookieRefreshResult = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("csrf", csrf)
            .add("refresh_csrf", refreshCsrf)
            .add("source", "main_web")
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url(COOKIE_REFRESH_URL)
            .post(formBody)
            .addHeader("Cookie", cookieHeader)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Cookie refresh failed: HTTP ${response.code}")
            }
            val cookieMap = parseSetCookie(response.headers.values("Set-Cookie"))
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val data = json.getJSONObject("data")
            val newRefreshToken = data.optString("refresh_token").ifBlank { null }
            BiliCookieRefreshResult(cookieMap = cookieMap, refreshToken = newRefreshToken)
        }
    }

    suspend fun confirmRefresh(
        cookieHeader: String,
        csrf: String,
        oldRefreshToken: String
    ) = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("csrf", csrf)
            .add("refresh_token", oldRefreshToken)
            .build()

        val request = Request.Builder()
            .url(CONFIRM_REFRESH_URL)
            .post(formBody)
            .addHeader("Cookie", cookieHeader)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Confirm refresh failed: HTTP ${response.code}")
            }
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
        private const val COOKIE_INFO_URL =
            "https://passport.bilibili.com/x/passport-login/web/cookie/info"
        private const val CORRESPOND_URL = "https://www.bilibili.com/correspond/1/"
        private const val COOKIE_REFRESH_URL =
            "https://passport.bilibili.com/x/passport-login/web/cookie/refresh"
        private const val CONFIRM_REFRESH_URL =
            "https://passport.bilibili.com/x/passport-login/web/confirm/refresh"
        private const val PUBLIC_KEY_PEM = """
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDLgd2OAkcGVtoE3ThUREbio0Eg
Uc/prcajMKXvkCKFCWhJYJcLkcM2DKKcSeFpD/j6Boy538YXnR6VhcuUJOhH2x71
nzPjfdTcqMz7djHum0qSZA0AyCBDABUqCrfNgCiJ00Ra7GmRj+YCK1NJEuewlb40
JNrRuoEUXpabUzGB8QIDAQAB
-----END PUBLIC KEY-----
"""
        private val REFRESH_CSRF_REGEX =
            Regex("<div\\s+id=[\"']1-name[\"']>([^<]+)</div>")
    }
}
