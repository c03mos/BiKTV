package com.hu3h.biktv.data.ncm

import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class NcmUserInfo(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?
)

data class NcmRefreshResult(
    val body: String,
    val newCookie: String?
)

class NcmApiClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build()
        }

        fun shared(): NcmApiClient = NcmApiClient(sharedClient)

        private const val USER_AGENT_WEAPI =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"
        private const val USER_AGENT_API =
            "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
    }

    private data class CacheEntry(
        val body: String,
        val expiresAtMs: Long
    )

    private val cache = mutableMapOf<String, CacheEntry>()

    private fun cacheGet(key: String): String? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAtMs) {
            cache.remove(key)
            return null
        }
        return entry.body
    }

    private fun cachePut(key: String, body: String, ttlMs: Long) {
        cache[key] = CacheEntry(body, System.currentTimeMillis() + ttlMs)
    }
    suspend fun fetchUserInfo(cookieHeader: String): NcmUserInfo = withContext(Dispatchers.IO) {
        val body = weapiPost("/api/nuser/account/get", JSONObject(), cookieHeader)
        val json = JSONObject(body)
        val profile = json.optJSONObject("profile")
            ?: error("User info missing profile")
        val userId = profile.optLong("userId", 0L)
        val nickname = profile.optString("nickname", "")
        val avatarUrl = profile.optString("avatarUrl").ifBlank { null }
        NcmUserInfo(userId = userId, nickname = nickname, avatarUrl = avatarUrl)
    }

    suspend fun fetchHotSearch(cookieHeader: String): String = withContext(Dispatchers.IO) {
        apiPost("/api/search/hot", JSONObject().put("type", 1111), cookieHeader)
    }

    suspend fun fetchHotSearchDetail(cookieHeader: String): String = withContext(Dispatchers.IO) {
        weapiPost("/api/hotsearchlist/get", JSONObject(), cookieHeader)
    }

    suspend fun fetchSearch(
        keywords: String,
        type: Int,
        limit: Int,
        offset: Int,
        cookieHeader: String
    ): String = withContext(Dispatchers.IO) {
        apiPost(
            "/api/search/get",
            JSONObject()
                .put("s", keywords)
                .put("type", type)
                .put("limit", limit)
                .put("offset", offset),
            cookieHeader
        )
    }

    suspend fun fetchToplist(cookieHeader: String): String = withContext(Dispatchers.IO) {
        cacheGet("toplist") ?: apiPost("/api/toplist", JSONObject(), cookieHeader).also {
            cachePut("toplist", it, 86_400_000)
        }
    }

    suspend fun fetchToplistDetail(cookieHeader: String): String = withContext(Dispatchers.IO) {
        cacheGet("toplist_detail") ?: weapiPost("/api/toplist/detail", JSONObject(), cookieHeader).also {
            cachePut("toplist_detail", it, 86_400_000)
        }
    }

    suspend fun fetchToplistDetailV2(cookieHeader: String): String = withContext(Dispatchers.IO) {
        cacheGet("toplist_detail_v2") ?: weapiPost("/api/toplist/detail/v2", JSONObject(), cookieHeader).also {
            cachePut("toplist_detail_v2", it, 86_400_000)
        }
    }

    suspend fun fetchToplistById(id: Long, cookieHeader: String): String = withContext(Dispatchers.IO) {
        val key = "toplist_id_$id"
        cacheGet(key) ?: apiPost(
            "/api/playlist/v4/detail",
            JSONObject().put("id", id).put("n", "500").put("s", "0"),
            cookieHeader
        ).also { cachePut(key, it, 86_400_000) }
    }

    suspend fun fetchArtistDetail(artistId: Long, cookieHeader: String): String =
        withContext(Dispatchers.IO) {
            apiPost(
                "/api/artist/head/info/get",
                JSONObject().put("id", artistId),
                cookieHeader
            )
        }

    suspend fun fetchArtistList(
        type: Int,
        area: Int?,
        initial: String?,
        offset: Int,
        limit: Int,
        cookieHeader: String
    ): String = withContext(Dispatchers.IO) {
        val key = "artist_list_${type}_${area}_${initial}_${offset}_${limit}"
        cacheGet(key)?.let { return@withContext it }
        val initialCode = initial?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.let {
            it[0].code
        }
        val payload = JSONObject()
            .put("offset", offset)
            .put("limit", limit)
            .put("total", true)
            .put("type", type.toString())
        if (area != null) payload.put("area", area)
        if (initialCode != null) payload.put("initial", initialCode)
        weapiPost("/api/v1/artist/list", payload, cookieHeader).also {
            cachePut(key, it, 2_592_000_000)
        }
    }

    suspend fun fetchArtistSongs(
        artistId: Long,
        order: String,
        offset: Int,
        limit: Int,
        cookieHeader: String
    ): String = withContext(Dispatchers.IO) {
        apiPost(
            "/api/v1/artist/songs",
            JSONObject()
                .put("id", artistId)
                .put("private_cloud", "true")
                .put("work_type", 1)
                .put("order", order)
                .put("offset", offset)
                .put("limit", limit),
            cookieHeader
        )
    }

    suspend fun refreshLoginToken(cookieHeader: String): NcmRefreshResult = withContext(Dispatchers.IO) {
        val cookieMap = parseCookie(cookieHeader)
        ensureBaseCookies(cookieMap)
        val url = "https://interface.music.163.com/api/login/token/refresh"
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .addHeader("User-Agent", USER_AGENT_API)
            .addHeader("Accept", "*/*")
            .addHeader("Cookie", buildCookieHeader(cookieMap))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@use NcmRefreshResult(body = body, newCookie = null)
            }
            val setCookie = response.headers.values("Set-Cookie")
            if (setCookie.isNotEmpty()) {
                val merged = mergeCookies(cookieMap, setCookie)
                return@use NcmRefreshResult(body = body, newCookie = buildCookieHeader(merged))
            }
            NcmRefreshResult(body = body, newCookie = null)
        }
    }

    private fun weapiPost(path: String, data: JSONObject, cookieHeader: String): String {
        val cookieMap = parseCookie(cookieHeader)
        ensureBaseCookies(cookieMap)
        val csrf = cookieMap["__csrf"].orEmpty()
        if (!data.has("csrf_token")) {
            data.put("csrf_token", csrf)
        }
        val encrypted = NcmEncryptTools.encryptParams(data.toString(), randomSecKey())
        val formBody = FormBody.Builder()
            .add("params", encrypted.params)
            .add("encSecKey", encrypted.encSecKey)
            .build()

        val url = "https://music.163.com/weapi/" + path.removePrefix("/api/")
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .addHeader("User-Agent", USER_AGENT_WEAPI)
            .addHeader("Referer", "https://music.163.com/")
            .addHeader("Origin", "https://music.163.com")
            .addHeader("Accept", "*/*")
            .addHeader("Cookie", buildCookieHeader(cookieMap))
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("NCM weapi failed: HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }

    private fun apiPost(path: String, data: JSONObject, cookieHeader: String): String {
        val cookieMap = parseCookie(cookieHeader)
        ensureBaseCookies(cookieMap)
        val formBody = FormBody.Builder().apply {
            data.keys().forEach { key ->
                val value = data.opt(key)
                add(key, value?.toString() ?: "")
            }
        }.build()

        val url = "https://music.163.com$path"
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .addHeader("User-Agent", USER_AGENT_API)
            .addHeader("Accept", "*/*")
            .addHeader("Cookie", buildCookieHeader(cookieMap))
            .addHeader("X-Real-IP", randomChineseIp())
            .addHeader("X-Forwarded-For", randomChineseIp())
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("NCM api failed: HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }

    private fun parseCookie(cookieHeader: String): MutableMap<String, String> {
        val map = linkedMapOf<String, String>()
        cookieHeader.split(";").forEach { part ->
            val trimmed = part.trim()
            val idx = trimmed.indexOf("=")
            if (idx > 0) {
                val name = trimmed.substring(0, idx)
                val value = trimmed.substring(idx + 1)
                map[name] = value
            }
        }
        return map
    }

    private fun buildCookieHeader(cookieMap: Map<String, String>): String {
        if (cookieMap.isEmpty()) return ""
        return cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun mergeCookies(
        base: MutableMap<String, String>,
        setCookieHeaders: List<String>
    ): MutableMap<String, String> {
        val map = LinkedHashMap(base)
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

    private fun ensureBaseCookies(cookieMap: MutableMap<String, String>) {
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

    private fun randomSecKey(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val rnd = SecureRandom()
        val sb = StringBuilder(16)
        repeat(16) { sb.append(chars[rnd.nextInt(chars.length)]) }
        return sb.toString()
    }

    private fun generateDeviceId(): String {
        val hex = "0123456789ABCDEF"
        val sb = StringBuilder(52)
        repeat(52) { sb.append(hex[SecureRandom().nextInt(hex.length)]) }
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

}
