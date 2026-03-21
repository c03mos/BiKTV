package com.hu3h.biktv.server

import android.content.Context
import com.hu3h.biktv.data.KtvDownloadManager
import com.hu3h.biktv.data.ResourceIndex
import com.hu3h.biktv.data.ncm.NcmApiClient
import com.hu3h.biktv.data.ncm.NcmCacheStore
import com.hu3h.biktv.data.session.NcmSessionStoreImpl
import com.hu3h.biktv.player.KtvPlayerManager
import com.hu3h.biktv.server.KtvWsPayloads
import com.hu3h.biktv.asr.AsrTaskManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

object NcmServerHelper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000L

    @JvmStatic
    fun handle(context: Context, path: String, params: Map<String, String>, body: String?): NcmServerResult {
        return try {
            KtvPlayerManager.init(context)
            val needsNcm = path.startsWith("/ncm/")
            val session = if (needsNcm) {
                runBlocking { NcmSessionStoreImpl(context).sessionFlow.first() }
                    ?: return jsonError(401, "NOT_LOGGED_IN")
            } else {
                null
            }
            val client = NcmApiClient.shared()
            val cookie = session?.cookie.orEmpty()
            val jsonBody = parseJsonBody(body)

            when {
                path == "/ncm/health" -> jsonOk("""{"ok":true}""")
                path == "/ncm/toplist" -> {
                    val key = "ncm/toplist"
                    val bodyStr = getCachedOrSchedule(context, key, ONE_DAY_MS) {
                        client.fetchToplist(cookie)
                    } ?: return jsonPending()
                    jsonOk(simplifyToplist(bodyStr))
                }
                path == "/ncm/toplist/detail" -> {
                    val key = "ncm/toplist/detail"
                    val bodyStr = getCachedOrSchedule(context, key, ONE_DAY_MS) {
                        client.fetchToplistDetail(cookie)
                    } ?: return jsonPending()
                    jsonOk(simplifyToplistDetail(bodyStr))
                }
                path == "/ncm/toplist/detail/v2" -> {
                    val key = "ncm/toplist/detail/v2"
                    val bodyStr = getCachedOrSchedule(context, key, ONE_DAY_MS) {
                        client.fetchToplistDetailV2(cookie)
                    } ?: return jsonPending()
                    jsonOk(simplifyToplistDetail(bodyStr))
                }
                path == "/ncm/toplist/playlist" -> {
                    val id = params["id"]?.toLongOrNull()
                        ?: return jsonError(400, "MISSING_ID")
                    val key = "ncm/toplist/playlist?id=$id"
                    val bodyStr = getCachedOrSchedule(context, key, ONE_DAY_MS) {
                        client.fetchToplistById(id, cookie)
                    } ?: return jsonPending()
                    jsonOk(simplifyPlaylistDetail(bodyStr))
                }
                path == "/ncm/artist/list" -> {
                    val type = params["type"]?.toIntOrNull() ?: 1
                    val area = params["area"]?.toIntOrNull()
                    val initial = params["initial"]
                    val offset = params["offset"]?.toIntOrNull() ?: 0
                    val limit = params["limit"]?.toIntOrNull() ?: 30
                    val key = "ncm/artist/list?type=$type&area=$area&initial=$initial&offset=$offset&limit=$limit"
                    val bodyStr = getCachedOrSchedule(context, key, THIRTY_DAYS_MS) {
                        client.fetchArtistList(type, area, initial, offset, limit, cookie)
                    } ?: return jsonPending()
                    jsonOk(simplifyArtistList(bodyStr))
                }
                path == "/ncm/artist/detail" -> {
                    val id = params["id"]?.toLongOrNull()
                        ?: return jsonError(400, "MISSING_ID")
                    val key = "ncm/artist/detail?id=$id"
                    val bodyStr = getCachedOrSchedule(context, key, THIRTY_DAYS_MS) {
                        client.fetchArtistDetail(id, cookie)
                    } ?: return jsonPending()
                    jsonOk(simplifyArtistDetail(bodyStr))
                }
                path == "/ncm/artist/songs" -> {
                    val id = params["id"]?.toLongOrNull()
                        ?: return jsonError(400, "MISSING_ID")
                    val order = params["order"] ?: "hot"
                    val offset = params["offset"]?.toIntOrNull() ?: 0
                    val limit = params["limit"]?.toIntOrNull() ?: 50
                    val key = "ncm/artist/songs?id=$id&order=$order&offset=$offset&limit=$limit"
                    val bodyStr = getCachedOrSchedule(context, key, THIRTY_DAYS_MS) {
                        client.fetchArtistSongs(id, order, offset, limit, cookie)
                    } ?: return jsonPending()
                    jsonOk(simplifyArtistSongs(bodyStr))
                }
                path == "/ncm/artist/songs/summary" -> {
                    val id = params["id"]?.toLongOrNull()
                        ?: return jsonError(400, "MISSING_ID")
                    val key = "ncm/artist/songs/summary?id=$id"
                    val bodyStr = getCachedOrSchedule(context, key, THIRTY_DAYS_MS) {
                        client.fetchArtistSongs(id, "hot", 0, 50, cookie)
                    } ?: return jsonPending()
                    jsonOk(buildSongArtistSummary(bodyStr))
                }
                path == "/ncm/search" -> {
                    val keywords = (params["keywords"] ?: jsonBody?.optString("keywords")).orEmpty().trim()
                    if (keywords.isBlank()) return jsonError(400, "MISSING_KEYWORDS")
                    val type = (params["type"] ?: jsonBody?.optString("type")).orEmpty().toIntOrNull() ?: 1
                    val limit = (params["limit"] ?: jsonBody?.optString("limit")).orEmpty().toIntOrNull() ?: 30
                    val offset = (params["offset"] ?: jsonBody?.optString("offset")).orEmpty().toIntOrNull() ?: 0
                    val bodyStr = runBlocking { client.fetchSearch(keywords, type, limit, offset, cookie) }
                    jsonOk(simplifySearch(bodyStr))
                }
                path == "/ncm/cookie/refresh" -> {
                    val result = runBlocking { client.refreshLoginToken(cookie) }
                    val newCookie = result.newCookie
                    if (!newCookie.isNullOrBlank()) {
                        runBlocking {
                            NcmSessionStoreImpl(context).updateCookie(newCookie, null)
                        }
                    }
                    jsonOk(result.body)
                }
                path == "/bili/mv/download" -> {
                    val song = (params["song"] ?: jsonBody?.optString("song")).orEmpty().trim()
                    val artist = (params["artist"] ?: jsonBody?.optString("artist")).orEmpty().trim()
                    val keyword = (params["keyword"] ?: jsonBody?.optString("keyword")).orEmpty().trim()
                    val query = when {
                        song.isNotBlank() && artist.isNotBlank() -> normalizeMvKeyword("$song-$artist")
                        song.isNotBlank() -> normalizeMvKeyword(song)
                        keyword.isNotBlank() -> normalizeMvKeyword(keyword)
                        else -> return jsonError(400, "MISSING_KEYWORD")
                    }
                    val title = if (song.isNotBlank()) song else query
                    val uuidSeed = when {
                        song.isNotBlank() && artist.isNotBlank() -> "$song|$artist"
                        else -> parseSongArtistSeed(query) ?: query
                    }
                    val uuid = UUID.nameUUIDFromBytes(uuidSeed.toByteArray(Charsets.UTF_8)).toString()
                    val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                    val outputDir = java.io.File(baseDir, "BiKTV/song")
                    runBlocking {
                        KtvDownloadManager.enqueue(context, title, artist.ifBlank { null }, query) { u, t, a, file, audio ->
                            KtvPlayerManager.addQueueItem(t, a, file, audio, u)
                        }
                    }
                    val payload = JSONObject()
                        .put("uuid", uuid)
                        .put("title", title)
                        .put("artist", artist)
                        .put("status", "queued")
                        .toString()
                    jsonOk(payload)
                }
                path == "/queue/add" -> {
                    val title = (params["title"] ?: jsonBody?.optString("title")).orEmpty().trim()
                    val artist = (params["artist"] ?: jsonBody?.optString("artist")).orEmpty().trim()
                    val keyword = (params["keyword"] ?: jsonBody?.optString("keyword")).orEmpty().trim()
                    if (title.isBlank()) return jsonError(400, "MISSING_TITLE")
                    val query = if (artist.isNotBlank()) normalizeMvKeyword("$title-$artist")
                    else if (keyword.isNotBlank()) normalizeMvKeyword(keyword)
                    else normalizeMvKeyword(title)
                    val meta = ResourceIndex.findByTitleArtist(title, artist.ifBlank { null })
                    if (meta != null && java.io.File(meta.filePath).exists()) {
                        KtvPlayerManager.addQueueItem(meta.title, meta.artist, meta.filePath, meta.audioPath, meta.uuid)
                        return jsonOk(
                            JSONObject()
                                .put("title", meta.title)
                                .put("artist", meta.artist ?: "")
                                .put("status", "queued")
                                .toString()
                        )
                    }
                    KtvDownloadManager.enqueue(context, title, artist.ifBlank { null }, query) { u, t, a, file, audio ->
                        KtvPlayerManager.addQueueItem(t, a, file, audio, u)
                    }
                    jsonOk(
                        JSONObject()
                            .put("title", title)
                            .put("artist", artist)
                            .put("status", "downloading")
                            .toString()
                    )
                }
                path == "/queue" -> {
                    val payload = KtvWsPayloads.buildQueueJson().toString()
                    jsonOk(payload)
                }
                path == "/player/state" -> {
                    val payload = KtvWsPayloads.buildPlayerStateJson().toString()
                    jsonOk(payload)
                }
                path == "/asr/srt" -> {
                    val title = (params["title"] ?: jsonBody?.optString("title")).orEmpty().trim()
                    val artist = (params["artist"] ?: jsonBody?.optString("artist")).orEmpty().trim()
                    val filePath = (params["filePath"] ?: jsonBody?.optString("filePath")).orEmpty().trim()
                    val task = AsrTaskManager.startByMeta(
                        context,
                        title.ifBlank { null },
                        artist.ifBlank { null },
                        filePath.ifBlank { null }
                    )
                    if (task.status == "not_found") return jsonError(404, "NOT_FOUND")
                    jsonOk(AsrTaskManager.toJson(task))
                }
                path == "/asr/srt/status" -> {
                    val id = (params["id"] ?: jsonBody?.optString("id")).orEmpty().trim()
                    if (id.isBlank()) return jsonError(400, "MISSING_ID")
                    val task = AsrTaskManager.get(id) ?: return jsonError(404, "NOT_FOUND")
                    jsonOk(AsrTaskManager.toJson(task))
                }
                path == "/resource/list" -> {
                    val list = org.json.JSONArray()
                    ResourceIndex.all().forEach { meta ->
                        val obj = JSONObject()
                            .put("title", meta.title)
                            .put("artist", meta.artist ?: "")
                            .put("sizeBytes", meta.sizeBytes)
                            .put("filePath", meta.filePath)
                        list.put(obj)
                    }
                    jsonOk(JSONObject().put("list", list).toString())
                }
                path == "/resource/enqueue" -> {
                    val title = (params["title"] ?: jsonBody?.optString("title")).orEmpty().trim()
                    val artist = (params["artist"] ?: jsonBody?.optString("artist")).orEmpty().trim()
                    val filePath = (params["filePath"] ?: jsonBody?.optString("filePath")).orEmpty().trim()
                    val meta = when {
                        filePath.isNotBlank() -> ResourceIndex.findByFilePath(filePath)
                        title.isNotBlank() -> ResourceIndex.findByTitleArtist(title, artist.ifBlank { null })
                            ?: ResourceIndex.all().firstOrNull { it.title.equals(title, ignoreCase = true) }
                        else -> null
                    } ?: return jsonError(404, "NOT_FOUND")
                    if (java.io.File(meta.filePath).exists()) {
                        KtvPlayerManager.addQueueItem(meta.title, meta.artist, meta.filePath, meta.audioPath, meta.uuid)
                        return jsonOk(JSONObject().put("ok", true).toString())
                    }
                    jsonError(404, "NOT_FOUND")
                }
                path == "/resource/delete" -> {
                    val title = (params["title"] ?: jsonBody?.optString("title")).orEmpty().trim()
                    val artist = (params["artist"] ?: jsonBody?.optString("artist")).orEmpty().trim()
                    if (title.isBlank()) return jsonError(400, "MISSING_TITLE")
                    val meta = ResourceIndex.findByTitleArtist(title, artist.ifBlank { null })
                        ?: ResourceIndex.all().firstOrNull { it.title.equals(title, ignoreCase = true) }
                        ?: return jsonError(404, "NOT_FOUND")
                    val file = java.io.File(meta.filePath)
                    if (file.exists()) file.delete()
                    ResourceIndex.remove(context, meta.uuid)
                    KtvPlayerManager.rescanResources()
                    jsonOk(JSONObject().put("ok", true).toString())
                }
                else -> jsonError(404, "NOT_FOUND")
            }
        } catch (t: Throwable) {
            jsonError(500, t.message ?: "SERVER_ERROR")
        }
    }

    private fun getOrFetch(
        context: Context,
        key: String,
        ttlMs: Long,
        fetchBlock: () -> String
    ): String {
        val entry = NcmCacheStore.get(context, key)
        if (entry == null) {
            val body = fetchBlock()
            NcmCacheStore.put(context, key, body)
            return body
        }
        val age = System.currentTimeMillis() - entry.savedAtMs
        if (age <= ttlMs) return entry.body
        scope.launch {
            runCatching {
                val body = fetchBlock()
                NcmCacheStore.put(context, key, body)
            }
        }
        return entry.body
    }

    private fun getCachedOrSchedule(
        context: Context,
        key: String,
        ttlMs: Long,
        fetchBlock: suspend () -> String
    ): String? {
        val entry = NcmCacheStore.get(context, key)
        if (entry == null) {
            scope.launch {
                runCatching {
                    val body = fetchBlock()
                    NcmCacheStore.put(context, key, body)
                }
            }
            return null
        }
        val age = System.currentTimeMillis() - entry.savedAtMs
        if (age <= ttlMs) return entry.body
        scope.launch {
            runCatching {
                val body = fetchBlock()
                NcmCacheStore.put(context, key, body)
            }
        }
        return entry.body
    }

    private fun jsonOk(body: String) = NcmServerResult(200, body)

    private fun jsonError(code: Int, msg: String): NcmServerResult {
        val payload = JSONObject()
            .put("code", code)
            .put("message", msg)
            .toString()
        return NcmServerResult(code, payload)
    }

    private fun jsonPending(): NcmServerResult {
        val payload = JSONObject()
            .put("code", 202)
            .put("message", "FETCHING")
            .toString()
        return NcmServerResult(202, payload)
    }

    private fun parseSongArtistSeed(query: String): String? {
        val parts = query.split("-").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val last = parts.last().lowercase()
            if (last.contains("mv")) {
                return parts[0] + "|" + parts[1]
            }
        }
        return null
    }

    private fun normalizeMvKeyword(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return trimmed
        val lower = trimmed.lowercase()
        return if (lower.contains("mv")) trimmed else "$trimmed-mv"
    }

    private fun parseJsonBody(body: String?): JSONObject? {
        if (body.isNullOrBlank()) return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun buildSongArtistSummary(jsonBody: String): String {
        return try {
            val json = JSONObject(jsonBody)
            val songs = json.optJSONArray("songs") ?: return jsonBody
            val sb = StringBuilder()
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                val name = song.optString("name")
                val artists = song.optJSONArray("artists")
                val artistNames = if (artists != null) {
                    val list = mutableListOf<String>()
                    for (j in 0 until artists.length()) {
                        val a = artists.optJSONObject(j)
                        if (a != null) list.add(a.optString("name"))
                    }
                    list.joinToString("/")
                } else {
                    ""
                }
                sb.append(name).append(" - ").append(artistNames).append("\n")
            }
            sb.toString()
        } catch (t: Throwable) {
            jsonBody
        }
    }

    private fun simplifyToplist(jsonBody: String): String {
        return try {
            val json = JSONObject(jsonBody)
            val list = json.optJSONArray("list") ?: return jsonBody
            val out = org.json.JSONArray()
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val o = JSONObject()
                o.put("id", item.optLong("id"))
                o.put("name", item.optString("name"))
                o.put("coverImgUrl", item.optString("coverImgUrl"))
                o.put("updateFrequency", item.optString("updateFrequency"))
                o.put("trackCount", item.optInt("trackCount"))
                out.put(o)
            }
            JSONObject().put("list", out).toString()
        } catch (t: Throwable) {
            jsonBody
        }
    }

    private fun simplifyToplistDetail(jsonBody: String): String {
        return try {
            val json = JSONObject(jsonBody)
            val list = json.optJSONArray("list") ?: return jsonBody
            val out = org.json.JSONArray()
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val o = JSONObject()
                o.put("id", item.optLong("id"))
                o.put("name", item.optString("name"))
                o.put("coverImgUrl", item.optString("coverImgUrl"))
                o.put("updateFrequency", item.optString("updateFrequency"))
                val tracks = item.optJSONArray("tracks")
                if (tracks != null) {
                    val tOut = org.json.JSONArray()
                    for (j in 0 until tracks.length()) {
                        val t = tracks.optJSONObject(j) ?: continue
                        val to = JSONObject()
                        to.put("name", t.optString("name"))
                        val ar = t.optJSONArray("ar")
                        if (ar != null) {
                            val names = mutableListOf<String>()
                            for (k in 0 until ar.length()) {
                                names.add(ar.optJSONObject(k)?.optString("name") ?: "")
                            }
                            to.put("artists", names.joinToString("/"))
                        }
                        tOut.put(to)
                    }
                    o.put("tracks", tOut)
                }
                out.put(o)
            }
            JSONObject().put("list", out).toString()
        } catch (t: Throwable) {
            jsonBody
        }
    }

    private fun simplifyPlaylistDetail(jsonBody: String): String {
        return try {
            val json = JSONObject(jsonBody)
            val playlist = json.optJSONObject("playlist") ?: return jsonBody
            val out = JSONObject()
            out.put("id", playlist.optLong("id"))
            out.put("name", playlist.optString("name"))
            out.put("coverImgUrl", playlist.optString("coverImgUrl"))
            out.put("description", playlist.optString("description"))
            out.put("trackCount", playlist.optInt("trackCount"))
            val tracks = playlist.optJSONArray("tracks")
            if (tracks != null) {
                val tOut = org.json.JSONArray()
                for (i in 0 until tracks.length()) {
                    val t = tracks.optJSONObject(i) ?: continue
                    val to = JSONObject()
                    to.put("id", t.optLong("id"))
                    to.put("name", t.optString("name"))
                    to.put("duration", t.optLong("dt"))
                    val ar = t.optJSONArray("ar")
                    if (ar != null) {
                        val names = mutableListOf<String>()
                        for (k in 0 until ar.length()) {
                            names.add(ar.optJSONObject(k)?.optString("name") ?: "")
                        }
                        to.put("artists", names.joinToString("/"))
                    }
                    tOut.put(to)
                }
                out.put("tracks", tOut)
            }
            out.toString()
        } catch (t: Throwable) {
            jsonBody
        }
    }

    private fun simplifyArtistList(jsonBody: String): String {
        return try {
            val json = JSONObject(jsonBody)
            val artists = json.optJSONArray("artists") ?: return jsonBody
            val out = org.json.JSONArray()
            for (i in 0 until artists.length()) {
                val a = artists.optJSONObject(i) ?: continue
                val o = JSONObject()
                o.put("id", a.optLong("id"))
                o.put("name", a.optString("name"))
                o.put("picUrl", a.optString("picUrl"))
                out.put(o)
            }
            JSONObject().put("artists", out).toString()
        } catch (t: Throwable) {
            jsonBody
        }
    }

    private fun simplifyArtistDetail(jsonBody: String): String {
        return try {
            val json = JSONObject(jsonBody)
            val artist = json.optJSONObject("artist") ?: json.optJSONObject("data") ?: return jsonBody
            val out = JSONObject()
            out.put("id", artist.optLong("id"))
            out.put("name", artist.optString("name"))
            out.put("picUrl", artist.optString("picUrl"))
            out.put("briefDesc", artist.optString("briefDesc"))
            out.toString()
        } catch (t: Throwable) {
            jsonBody
        }
    }

    private fun simplifyArtistSongs(jsonBody: String): String {
        return try {
            val json = JSONObject(jsonBody)
            val songs = json.optJSONArray("songs") ?: return jsonBody
            val out = org.json.JSONArray()
            for (i in 0 until songs.length()) {
                val s = songs.optJSONObject(i) ?: continue
                val o = JSONObject()
                o.put("id", s.optLong("id"))
                o.put("name", s.optString("name"))
                o.put("duration", s.optLong("dt"))
                val ar = s.optJSONArray("artists")
                if (ar != null) {
                    val names = mutableListOf<String>()
                    for (k in 0 until ar.length()) {
                        names.add(ar.optJSONObject(k)?.optString("name") ?: "")
                    }
                    o.put("artists", names.joinToString("/"))
                }
                out.put(o)
            }
            JSONObject().put("songs", out).toString()
        } catch (t: Throwable) {
            jsonBody
        }
    }

    private fun simplifySearch(jsonBody: String): String {
        return try {
            val json = JSONObject(jsonBody)
            val result = json.optJSONObject("result") ?: return jsonBody
            val songs = result.optJSONArray("songs") ?: return jsonBody
            val out = org.json.JSONArray()
            for (i in 0 until songs.length()) {
                val s = songs.optJSONObject(i) ?: continue
                val o = JSONObject()
                o.put("id", s.optLong("id"))
                o.put("name", s.optString("name"))
                val ar = s.optJSONArray("artists") ?: s.optJSONArray("ar")
                if (ar != null) {
                    val names = mutableListOf<String>()
                    for (k in 0 until ar.length()) {
                        names.add(ar.optJSONObject(k)?.optString("name") ?: "")
                    }
                    o.put("artists", names.joinToString("/"))
                }
                out.put(o)
            }
            JSONObject().put("songs", out).toString()
        } catch (t: Throwable) {
            jsonBody
        }
    }
}
