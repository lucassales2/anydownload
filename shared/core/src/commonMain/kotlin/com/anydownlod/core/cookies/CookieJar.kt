/*
 * Cookie jar — AnyDownload
 *
 * Translation of the request-time filtering in `yt_dlp/cookies.py` and the
 * standard-library `http.cookiejar` policy at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 * Unlicense; see shared/core/NOTICE.md.
 *
 * The jar never writes anything and holds no file path. The header it builds
 * leaves the device through the trusted `HttpRequest` cookie field, after the
 * shared header allowlist; the header text itself is never logged.
 */
package com.anydownlod.core.cookies

/** The state of one stored cookie file at a moment in time. */
enum class CookieJarState {
    /** At least one row is still usable. */
    READY,

    /** Rows exist, but every one is expired at the given time. */
    ALL_EXPIRED,

    /** The file holds no parsable row (for example a header-only export). */
    EMPTY,
}

/**
 * The parsed cookies of one device's `cookies.txt`.
 *
 * Matching is per request: expired rows are skipped, the host must match the
 * domain (with the subdomain rule), the path must match, and a secure cookie
 * is only sent over `https`. The built header is deterministic: longer paths
 * first, then name order.
 */
class CookieJar(private val cookies: List<NetscapeCookie>) {

    /** The cookies that may be sent to [url] at [nowEpochSeconds], in header order. */
    fun matching(url: String, nowEpochSeconds: Long): List<NetscapeCookie> {
        val parts = UrlParts.parse(url) ?: return emptyList()
        return cookies
            .filter { !it.isExpired(nowEpochSeconds) }
            .filter { it.matchesHost(parts.host) }
            .filter { it.matchesPath(parts.path) }
            .filter { !it.secure || parts.secure }
            .sortedWith(compareByDescending<NetscapeCookie> { it.path.length }.thenBy { it.name })
    }

    /**
     * The `Cookie` header for [url], or null when no live row matches. The
     * returned value is credential material: never log it, never store it.
     */
    fun headerFor(url: String, nowEpochSeconds: Long): String? {
        val selected = matching(url, nowEpochSeconds)
        if (selected.isEmpty()) return null
        return selected.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /** The jar's state at [nowEpochSeconds]; `ALL_EXPIRED` is an error state. */
    fun stateAt(nowEpochSeconds: Long): CookieJarState = when {
        cookies.isEmpty() -> CookieJarState.EMPTY
        cookies.any { !it.isExpired(nowEpochSeconds) } -> CookieJarState.READY
        else -> CookieJarState.ALL_EXPIRED
    }

    companion object {
        /** A jar from an accepted file text. Malformed rows are skipped by the parser. */
        fun fromText(text: String): CookieJar = CookieJar(NetscapeCookieFile.parse(text))
    }
}

/** Scheme, host, path, and the HTTPS flag of one request URL. */
private data class UrlParts(val host: String, val path: String, val secure: Boolean) {
    companion object {
        fun parse(url: String): UrlParts? {
            val trimmed = url.trim()
            val schemeEnd = trimmed.indexOf("://")
            if (schemeEnd <= 0) return null
            val scheme = trimmed.substring(0, schemeEnd).lowercase()
            if (scheme != "http" && scheme != "https") return null
            val afterScheme = trimmed.substring(schemeEnd + 3)
            val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
            val authority = if (authorityEnd < 0) afterScheme else afterScheme.substring(0, authorityEnd)
            val rest = if (authorityEnd < 0) "" else afterScheme.substring(authorityEnd)
            val hostPort = authority.substringAfterLast('@')
            val host = when {
                hostPort.startsWith("[") -> hostPort.substringAfter('[').substringBefore(']')
                else -> hostPort.substringBefore(':')
            }.lowercase().removeSuffix(".")
            if (host.isEmpty()) return null
            val path = rest.substringBefore('?').substringBefore('#').ifEmpty { "/" }
            return UrlParts(host = host, path = path, secure = scheme == "https")
        }
    }
}
