package com.hu3h.biktv.data.bili

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class BiliWbiKeys(
    val imgKey: String,
    val subKey: String,
    val mixinKey: String,
    val updatedAtMs: Long
)

object BiliWbiSigner {
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32,
        15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19,
        29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61,
        26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63,
        57, 62, 11, 36, 20, 34, 44, 52
    )

    fun signParams(params: Map<String, String>, wbiKeys: BiliWbiKeys): Map<String, String> {
        val now = (System.currentTimeMillis() / 1000).toString()
        val filtered = params.mapValues { sanitizeValue(it.value) }.toMutableMap()
        val withTs = filtered.toMutableMap()
        withTs["wts"] = now
        val query = withTs.toSortedMap().map { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }.joinToString("&")
        val wRid = md5(query + wbiKeys.mixinKey)
        withTs["w_rid"] = wRid
        return withTs
    }

    fun buildMixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        val sb = StringBuilder()
        for (i in 0 until 32) {
            sb.append(raw[MIXIN_KEY_ENC_TAB[i]])
        }
        return sb.toString()
    }

    private fun encode(value: String): String = encodeURIComponent(value)

    fun encodeForQuery(value: String): String = encodeURIComponent(value)

    private fun encodeURIComponent(value: String): String {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val sb = StringBuilder(bytes.size * 3)
        for (b in bytes) {
            val c = b.toInt() and 0xff
            val ch = c.toChar()
            val unreserved = (ch in 'A'..'Z') ||
                (ch in 'a'..'z') ||
                (ch in '0'..'9') ||
                ch == '-' || ch == '_' || ch == '.' || ch == '~'
            if (unreserved) {
                sb.append(ch)
            } else {
                sb.append('%')
                sb.append(String.format("%02X", c))
            }
        }
        return sb.toString()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sanitizeValue(value: String): String {
        return value.replace(Regex("[!'()*]"), "")
    }
}
