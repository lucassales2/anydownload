/*
 * Desktop browser cookie stores — AnyDownload (T-018)
 *
 * Translation of the cookie-store reading in yt-dlp `cookies.py` /
 * `browser_cookie3` behavior at the pin 2026.08.19, read 2026-09-30.
 * Unlicense; see shared/core/NOTICE.md. Only the desktop host uses this file.
 *
 * Every reader takes a snapshot copy of the database and a plain `Path`; no
 * class here locates a profile on its own, keeps a connection open, or reads
 * a live browser database per request. Chrome values on macOS/Linux are
 * AES-128-CBC with the browser's own OS secret; Firefox values are plain
 * text; Safari uses the binarycookies format.
 */
package com.anydownload.desktop.cookies

import com.anydownload.core.cookies.NetscapeCookie
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** One row read from a browser store, before Netscape serialization. */
data class BrowserCookie(
    val host: String,
    val includeSubdomains: Boolean,
    val path: String,
    val secure: Boolean,
    val expiresAtEpochSeconds: Long,
    val name: String,
    val value: String,
) {
    /** The Netscape row this browser cookie becomes. */
    fun toNetscape(): NetscapeCookie = NetscapeCookie(
        domain = if (includeSubdomains && !host.startsWith(".")) ".$host" else host,
        includeSubdomains = includeSubdomains,
        path = path.ifEmpty { "/" },
        secure = secure,
        expiresAtEpochSeconds = expiresAtEpochSeconds,
        name = name,
        value = value,
    )
}

/** Firefox `cookies.sqlite`: `moz_cookies` keeps values as plain text. */
object FirefoxCookieStore {
    fun read(path: Path): List<BrowserCookie> =
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT host, path, expiry, name, value, isSecure FROM moz_cookies",
                ).use { rows ->
                    buildList {
                        while (rows.next()) {
                            val host = rows.getString(1)?.trim().orEmpty()
                            if (host.isEmpty()) continue
                            val cookiePath = rows.getString(2)?.trim().orEmpty().ifEmpty { "/" }
                            val expiry = rows.getLong(3).coerceAtLeast(0L)
                            val name = rows.getString(4) ?: continue
                            val value = rows.getString(5) ?: ""
                            add(
                                BrowserCookie(
                                    host = host,
                                    includeSubdomains = host.startsWith("."),
                                    path = cookiePath,
                                    secure = rows.getInt(6) != 0,
                                    expiresAtEpochSeconds = expiry,
                                    name = name,
                                    value = value,
                                ),
                            )
                        }
                    }
                }
            }
        }
}

/**
 * Chrome `Cookies` SQLite: values are `v10`/`v11` AES-128-CBC with the
 * browser's own secret on macOS and Linux. Windows DPAPI is not implemented
 * and is a recorded gap; a null secret fails the snapshot before any write.
 */
object ChromeCookieStore {
    /** Seconds between the Chrome epoch (1601-01-01) and the Unix epoch. */
    private const val CHROME_EPOCH_OFFSET_SECONDS = 11_644_473_600L

    fun read(path: Path, secret: String?): List<BrowserCookie> =
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT host_key, path, expires_utc, name, encrypted_value, value, is_secure " +
                        "FROM cookies",
                ).use { rows ->
                    buildList {
                        while (rows.next()) {
                            val host = rows.getString(1)?.trim().orEmpty()
                            if (host.isEmpty()) continue
                            val cookiePath = rows.getString(2)?.trim().orEmpty().ifEmpty { "/" }
                            val chromeExpires = rows.getLong(3)
                            val expiry = if (chromeExpires <= 0L) {
                                0L
                            } else {
                                chromeExpires / 1_000_000L - CHROME_EPOCH_OFFSET_SECONDS
                            }
                            val name = rows.getString(4) ?: continue
                            val encrypted = rows.getBytes(5)
                            val plain = rows.getString(6) ?: ""
                            val value = if (encrypted != null && encrypted.isNotEmpty()) {
                                decrypt(encrypted, secret) ?: continue
                            } else {
                                plain
                            }
                            add(
                                BrowserCookie(
                                    host = host,
                                    includeSubdomains = host.startsWith("."),
                                    path = cookiePath,
                                    secure = rows.getInt(7) != 0,
                                    expiresAtEpochSeconds = expiry.coerceAtLeast(0L),
                                    name = name,
                                    value = value,
                                ),
                            )
                        }
                    }
                }
            }
        }

    /**
     * Chrome prefixes ciphertext with `v10` (macOS/Linux) or `v11`; an
     * un-prefixed value is already plain text. The key is PBKDF2-HMAC-SHA1
     * with the fixed `saltysalt` and 1003 iterations; the IV is 16 spaces.
     * Returns null when the secret is missing or the ciphertext is unusable.
     */
    fun decrypt(encrypted: ByteArray, secret: String?): String? {
        if (encrypted.size < 3) return null
        val prefix = encrypted.decodeToString(0, 3)
        if (prefix != "v10" && prefix != "v11") {
            return runCatching { encrypted.decodeToString() }.getOrNull()
        }
        if (secret.isNullOrEmpty()) return null
        return runCatching {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            val keyBytes = factory.generateSecret(
                PBEKeySpec(secret.toCharArray(), "saltysalt".toByteArray(), 1003, 128),
            ).encoded
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                IvParameterSpec(ByteArray(16) { 0x20 }),
            )
            cipher.doFinal(encrypted, 3, encrypted.size - 3).decodeToString()
        }.getOrNull()
    }
}

/**
 * Safari `Cookies.binarycookies`. The parser reads a page-based record with
 * little-endian fields and Mac-absolute-time expiry (seconds since
 * 2001-01-01); the file bytes are already a snapshot copied by the caller.
 */
object SafariCookieStore {
    private const val MAC_EPOCH_OFFSET_SECONDS = 978_307_200L

    fun read(path: Path): List<BrowserCookie> = read(Files.readAllBytes(path))

    fun read(bytes: ByteArray): List<BrowserCookie> {
        if (bytes.size < 8) return emptyList()
        if (bytes.decodeToString(0, 4) != "cook") return emptyList()
        val pageCount = readIntBigEndian(bytes, 4)
        if (pageCount <= 0 || pageCount > 10_000) return emptyList()
        var cursor = 8
        val pageSizes = ArrayList<Int>(pageCount)
        repeat(pageCount) {
            if (cursor + 4 > bytes.size) return emptyList()
            pageSizes += readIntBigEndian(bytes, cursor)
            cursor += 4
        }

        val cookies = mutableListOf<BrowserCookie>()
        for (pageSize in pageSizes) {
            if (cursor + pageSize > bytes.size || pageSize < 8) break
            val pageStart = cursor
            val page = bytes.copyOfRange(pageStart, pageStart + pageSize)
            val count = readIntLittleEndian(page, 4)
            if (count < 0 || count > 100_000) break
            val offsets = ArrayList<Int>(count)
            var offsetCursor = 8
            repeat(count) {
                if (offsetCursor + 4 > page.size) return cookies
                offsets += readIntLittleEndian(page, offsetCursor)
                offsetCursor += 4
            }
            for (offset in offsets) {
                readCookie(page, offset)?.let { cookies += it }
            }
            cursor += pageSize
        }
        return cookies
    }

    private fun readCookie(page: ByteArray, offset: Int): BrowserCookie? {
        if (offset < 0 || offset + 56 > page.size) return null
        val flags = readIntLittleEndian(page, offset + 8)
        val domainOffset = readIntLittleEndian(page, offset + 16)
        val nameOffset = readIntLittleEndian(page, offset + 20)
        val pathOffset = readIntLittleEndian(page, offset + 24)
        val valueOffset = readIntLittleEndian(page, offset + 28)
        val expiry = readDoubleLittleEndian(page, offset + 40)

        val host = readCString(page, offset + domainOffset) ?: return null
        val name = readCString(page, offset + nameOffset) ?: return null
        val path = readCString(page, offset + pathOffset) ?: return null
        val value = readCString(page, offset + valueOffset) ?: return null
        if (host.isEmpty() || name.isEmpty()) return null

        val expirySeconds = if (expiry <= 0.0) {
            0L
        } else {
            expiry.toLong() + MAC_EPOCH_OFFSET_SECONDS
        }
        return BrowserCookie(
            host = host,
            includeSubdomains = host.startsWith("."),
            path = path.ifEmpty { "/" },
            secure = flags and 1 != 0,
            expiresAtEpochSeconds = expirySeconds.coerceAtLeast(0L),
            name = name,
            value = value,
        )
    }

    private fun readCString(data: ByteArray, offset: Int): String? {
        if (offset <= 0 || offset >= data.size) return null
        var end = offset
        while (end < data.size && data[end] != 0.toByte()) end++
        return data.decodeToString(offset, end)
    }

    private fun readIntBigEndian(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return -1
        return ((data[offset].toInt() and 0xff) shl 24) or
            ((data[offset + 1].toInt() and 0xff) shl 16) or
            ((data[offset + 2].toInt() and 0xff) shl 8) or
            (data[offset + 3].toInt() and 0xff)
    }

    private fun readIntLittleEndian(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return -1
        return (data[offset].toInt() and 0xff) or
            ((data[offset + 1].toInt() and 0xff) shl 8) or
            ((data[offset + 2].toInt() and 0xff) shl 16) or
            ((data[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun readDoubleLittleEndian(data: ByteArray, offset: Int): Double {
        if (offset + 8 > data.size) return 0.0
        var bits = 0L
        for (index in 7 downTo 0) {
            bits = (bits shl 8) or (data[offset + index].toLong() and 0xff)
        }
        return Double.fromBits(bits)
    }
}
