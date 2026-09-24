package com.anydownlod.desktop.engine

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/**
 * Fetches a preview thumbnail. Only http and https are accepted, redirects are
 * not followed, and the body is capped so a huge response cannot be held.
 */
internal object ThumbnailBytes {
    const val MAX_BYTES = 2_000_000

    fun fetch(url: String): ByteArray? {
        if (!allowed(url)) return null
        val connection = runCatching { URI(url).toURL().openConnection() as HttpURLConnection }.getOrNull()
            ?: return null
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "AnyDownload")
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val declared = connection.contentLengthLong
            if (declared > MAX_BYTES) return null
            connection.inputStream.use { input -> readLimited(input, MAX_BYTES) }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun allowed(url: String): Boolean {
        val scheme = runCatching { URI(url).scheme }.getOrNull()?.lowercase() ?: return false
        return scheme == "https" || scheme == "http"
    }

    fun readLimited(input: InputStream, maxBytes: Int): ByteArray? {
        val buffer = ByteArray(8192)
        val out = java.io.ByteArrayOutputStream()
        while (out.size() <= maxBytes) {
            val count = input.read(buffer)
            if (count < 0) break
            if (out.size() + count > maxBytes) return null
            out.write(buffer, 0, count)
        }
        return if (out.size() == 0) null else out.toByteArray()
    }
}
