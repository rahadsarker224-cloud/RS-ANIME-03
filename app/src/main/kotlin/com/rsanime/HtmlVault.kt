package com.rsanime

import android.content.Context
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.InflaterInputStream
import java.io.ByteArrayInputStream

internal object HtmlVault {
    private val KA = byteArrayOf(-119, -78, -117, -75, -25, -42, 99, 108)
    private val KB = byteArrayOf(98, 84, 76, -114, -67, -39, -109, 59)
    private val KC = byteArrayOf(61, 102, -24, 74, 1, -14, -55, 6)
    private val KD = byteArrayOf(-65, -69, 83, -109, -69, 119, 50, 113)
    // No hardcoded package name here — BuildConfig.APPLICATION_ID always
    // reflects whatever applicationId Gradle set at build time (which itself
    // is auto-read from google-services.json), so this check stays valid
    // no matter who builds the project or what package name they use.
    private const val DATA = "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"

    fun getHtml(context: Context): String? {
        if (context.packageName != BuildConfig.APPLICATION_ID) return null
        return try {
            val key = KA + KB + KC + KD
            val raw = Base64.decode(DATA, Base64.NO_WRAP)
            val iv  = raw.copyOfRange(0, 16)
            val ct  = raw.copyOfRange(16, raw.size)
            val c   = Cipher.getInstance("AES/CBC/PKCS5Padding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            InflaterInputStream(ByteArrayInputStream(c.doFinal(ct)))
                .readBytes().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
