package com.hu3h.biktv.data.bili

import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class BiliSearchResult(
    val bvid: String?,
    val aid: Long?,
    val title: String
)

data class BiliDownloadResult(
    val uuid: String,
    val filePath: String,
    val audioPath: String?,
    val bvid: String?,
    val aid: Long?,
    val title: String
)

class BiliVideoApi(
    private val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
        private const val SEARCH_URL = "https://api.bilibili.com/x/web-interface/wbi/search/type"
        private const val VIEW_URL = "https://api.bilibili.com/x/web-interface/view"
        private const val PLAYURL = "https://api.bilibili.com/x/player/wbi/playurl"
    }

    private var cachedKeys: BiliWbiKeys? = null
    private var cachedCookie: String? = null

    suspend fun searchFirstVideo(keyword: String): BiliSearchResult = withContext(Dispatchers.IO) {
        val keys = getWbiKeys()
        val params = mapOf(
            "search_type" to "video",
            "keyword" to keyword,
            "page" to "1",
            "page_size" to "1"
        )
        val signed = BiliWbiSigner.signParams(params, keys)
        val url = buildUrl(SEARCH_URL, signed)
        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "Mozilla/5.0")
            .apply {
                cachedCookie?.let { addHeader("Cookie", it) }
            }
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("search failed: HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val code = json.optInt("code", -1)
            if (code != 0) {
                val msg = json.optString("message").ifBlank { json.optString("msg") }
                error("search failed: code=$code message=$msg body=$body")
            }
            val data = json.optJSONObject("data") ?: error("search missing data body=$body")
            val result = when (val r = data.opt("result")) {
                is org.json.JSONArray -> r
                is JSONObject -> r.optJSONArray("result")
                else -> null
            } ?: error("search missing result body=$body")
            if (result.length() == 0) error("search empty")
            val first = result.getJSONObject(0)
            val bvid = first.optString("bvid").ifBlank { null }
            val aid = first.optLong("aid").takeIf { it > 0 }
            val title = first.optString("title")
            BiliSearchResult(bvid = bvid, aid = aid, title = title)
        }
    }

    suspend fun downloadFirstVideo(
        keyword: String,
        outputDir: File,
        fixedUuid: String? = null,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): BiliDownloadResult = withContext(Dispatchers.IO) {
        val result = searchFirstVideo(keyword)
        val (cid, title) = fetchCidAndTitle(result)
        val playUrl = fetchPlayUrl(result, cid)
        val uuid = fixedUuid ?: UUID.randomUUID().toString()
        if (!outputDir.exists()) outputDir.mkdirs()
        val videoFile = File(outputDir, "$uuid.mp4")
        var audioFile: File? = null
        if (playUrl.isDash && playUrl.videoUrl != null) {
            if (!videoFile.exists()) {
                downloadToFile(playUrl.videoUrl, videoFile, onProgress)
            }
            if (!playUrl.audioUrl.isNullOrBlank()) {
                audioFile = File(outputDir, "$uuid.m4a")
                if (!audioFile.exists()) {
                    downloadToFile(playUrl.audioUrl, audioFile, onProgress)
                }
            }
        } else if (!videoFile.exists()) {
            downloadToFile(playUrl.singleUrl ?: error("playurl missing url"), videoFile, onProgress)
        }
        BiliDownloadResult(
            uuid = uuid,
            filePath = videoFile.absolutePath,
            audioPath = audioFile?.absolutePath,
            bvid = result.bvid,
            aid = result.aid,
            title = title
        )
    }

    private fun fetchCidAndTitle(result: BiliSearchResult): Pair<Long, String> {
        val keys = getWbiKeys()
        val params = mutableMapOf<String, String>()
        if (!result.bvid.isNullOrBlank()) params["bvid"] = result.bvid!!
        if (result.aid != null) params["aid"] = result.aid.toString()
        val signed = BiliWbiSigner.signParams(params, keys)
        val url = buildUrl(VIEW_URL, signed)
        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "Mozilla/5.0")
            .apply {
                cachedCookie?.let { addHeader("Cookie", it) }
            }
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("view failed: HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: error("view missing data")
            val cid = data.optLong("cid")
            val title = data.optString("title")
            if (cid <= 0) error("view missing cid")
            return cid to title
        }
    }

    private data class PlayUrlResult(
        val isDash: Boolean,
        val singleUrl: String?,
        val videoUrl: String?,
        val audioUrl: String?
    )

    private fun fetchPlayUrl(result: BiliSearchResult, cid: Long): PlayUrlResult {
        val keys = getWbiKeys()
        val params = mutableMapOf(
            "cid" to cid.toString(),
            "qn" to "120",
            "fnval" to "4048",
            "fourk" to "0"
        )
        if (!result.bvid.isNullOrBlank()) params["bvid"] = result.bvid!!
        if (result.aid != null) params["aid"] = result.aid.toString()
        val signed = BiliWbiSigner.signParams(params, keys)
        val url = buildUrl(PLAYURL, signed)
        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "Mozilla/5.0")
            .apply {
                cachedCookie?.let { addHeader("Cookie", it) }
            }
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("playurl failed: HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: error("playurl missing data")
            val dash = data.optJSONObject("dash")
            if (dash != null) {
                val videoArr = dash.optJSONArray("video")
                val audioArr = dash.optJSONArray("audio")
                val videoUrl = selectBestDashUrl(videoArr)
                val audioUrl = selectBestDashUrl(audioArr)
                if (!videoUrl.isNullOrBlank()) {
                    return PlayUrlResult(
                        isDash = true,
                        singleUrl = null,
                        videoUrl = videoUrl,
                        audioUrl = audioUrl
                    )
                }
            }
            val durl = data.optJSONArray("durl") ?: error("playurl missing durl")
            if (durl.length() == 0) error("playurl empty")
            val first = durl.getJSONObject(0)
            val urlStr = first.optString("url").ifBlank { null }
            return PlayUrlResult(
                isDash = false,
                singleUrl = urlStr ?: error("playurl missing url"),
                videoUrl = null,
                audioUrl = null
            )
        }
    }

    private fun selectBestDashUrl(arr: org.json.JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        var bestUrl: String? = null
        var bestBw = -1
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val bw = obj.optInt("bandwidth", 0)
            val url = obj.optString("baseUrl")
                .ifBlank { obj.optString("base_url") }
                .ifBlank {
                    val backup = obj.optJSONArray("backupUrl") ?: obj.optJSONArray("backup_url")
                    backup?.optString(0)
                }
            if (!url.isNullOrBlank() && bw >= bestBw) {
                bestBw = bw
                bestUrl = url
            }
        }
        return bestUrl
    }

    private fun downloadToFile(
        url: String,
        file: File,
        onProgress: ((downloaded: Long, total: Long) -> Unit)?
    ) {
        val req = Request.Builder()
            .url(url)
            .get()
            .addHeader("Referer", "https://www.bilibili.com")
            .addHeader("User-Agent", "Mozilla/5.0")
            .apply {
                cachedCookie?.let { addHeader("Cookie", it) }
            }
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("download failed: HTTP ${resp.code}")
            val body = resp.body ?: error("download empty body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress?.invoke(downloaded, total)
                    }
                }
            }
        }
    }

    private fun getWbiKeys(): BiliWbiKeys {
        val cached = cachedKeys
        if (cached != null && System.currentTimeMillis() - cached.updatedAtMs < 10 * 60 * 1000) {
            return cached
        }
        val req = Request.Builder()
            .url(NAV_URL)
            .get()
            .addHeader("User-Agent", "Mozilla/5.0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("nav failed: HTTP ${resp.code}")
            val buvid = extractBuvid(resp.headers.values("Set-Cookie"))
            val body = resp.body?.string().orEmpty()
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: error("nav missing data")
            val wbiImg = data.optJSONObject("wbi_img") ?: error("nav missing wbi_img")
            val imgUrl = wbiImg.optString("img_url")
            val subUrl = wbiImg.optString("sub_url")
            val imgKey = imgUrl.substringAfterLast("/").substringBefore(".")
            val subKey = subUrl.substringAfterLast("/").substringBefore(".")
            val mixin = BiliWbiSigner.buildMixinKey(imgKey, subKey)
            val keys = BiliWbiKeys(imgKey, subKey, mixin, System.currentTimeMillis())
            cachedKeys = keys
            if (buvid != null) {
                cachedCookie = "buvid3=$buvid"
            }
            return keys
        }
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val query = params.toSortedMap().map { (k, v) ->
            "${BiliWbiSigner.encodeForQuery(k)}=${BiliWbiSigner.encodeForQuery(v)}"
        }.joinToString("&")
        return "$base?$query"
    }

    private fun extractBuvid(setCookieHeaders: List<String>): String? {
        for (header in setCookieHeaders) {
            val part = header.substringBefore(";")
            val idx = part.indexOf("=")
            if (idx <= 0) continue
            val name = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (name.equals("buvid3", ignoreCase = true) && value.isNotBlank()) {
                return value
            }
        }
        return null
    }
}
