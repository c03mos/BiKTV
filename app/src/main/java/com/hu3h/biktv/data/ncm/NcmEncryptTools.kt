package com.hu3h.biktv.data.ncm

import android.util.Base64
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class NcmEncryptedParams(
    val params: String,
    val encSecKey: String
)

object NcmEncryptTools {
    private const val IV = "0102030405060708"
    private const val NONCE = "0CoJUm6Qyw8W8jud"
    private const val PUB_KEY = "010001"
    private const val MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"

    fun encryptParams(text: String, secKey: String): NcmEncryptedParams {
        val params = encrypt(encrypt(text, NONCE), secKey)
        val encSecKey = rsaEncrypt(secKey)
        return NcmEncryptedParams(params = params, encSecKey = encSecKey)
    }

    fun encrypt(text: String, secKey: String): String {
        val raw = secKey.toByteArray(StandardCharsets.UTF_8)
        val skeySpec = SecretKeySpec(raw, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = IvParameterSpec(IV.toByteArray(StandardCharsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv)
        val encrypted = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun rsaEncrypt(secKey: String): String {
        val reversed = secKey.reversed()
        val hex = hexEncode(reversed.toByteArray(StandardCharsets.UTF_8))
        val bigInt1 = BigInteger(hex, 16)
        val bigInt2 = BigInteger(PUB_KEY, 16)
        val bigInt3 = BigInteger(MODULUS, 16)
        val bigInt4 = bigInt1.modPow(bigInt2, bigInt3)
        val encSecKey = hexEncode(bigInt4.toByteArray())
        return zfill(encSecKey, 256)
    }

    fun zfill(result: String, n: Int): String {
        if (result.length >= n) {
            return result.substring(result.length - n, result.length)
        }
        val sb = StringBuilder()
        for (i in n downTo result.length + 1) {
            sb.append('0')
        }
        sb.append(result)
        return sb.toString()
    }

    fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(text.toByteArray(StandardCharsets.UTF_8))
        return hexEncode(bytes)
    }

    private fun hexEncode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }
}
