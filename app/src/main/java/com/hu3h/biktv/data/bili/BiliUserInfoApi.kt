package com.hu3h.biktv.data.bili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class BiliUserInfo(
    val mid: String,
    val nickname: String,
    val avatarUrl: String?,
    val level: Int?
)

class BiliUserInfoApi(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchUserInfo(cookieHeader: String): BiliUserInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(NAV_URL)
            .get()
            .addHeader("Cookie", cookieHeader)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("User info failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val data = json.getJSONObject("data")
            val isLogin = data.optBoolean("isLogin", false)
            if (!isLogin) {
                error("Not logged in")
            }
            val mid = data.optLong("mid").toString()
            val nickname = data.optString("uname", "")
            val avatarUrl = data.optString("face").ifBlank { null }
            val levelInfo = data.optJSONObject("level_info")
            val level = levelInfo?.optInt("current_level")
            BiliUserInfo(
                mid = mid,
                nickname = nickname,
                avatarUrl = avatarUrl,
                level = level
            )
        }
    }

    companion object {
        private const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
    }
}
