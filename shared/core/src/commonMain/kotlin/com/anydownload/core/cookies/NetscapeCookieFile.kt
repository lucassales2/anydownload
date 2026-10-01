/*
 * Netscape cookie file parser — AnyDownload
 *
 * Translation of the cookie-file reading behavior in `yt_dlp/cookies.py`
 * (`YoutubeDLCookieJar`, `_parse_cookie_file`) at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 * Unlicense; see shared/core/NOTICE.md. `cookies.py` is not vendored.
 *
 * This file holds no I/O: hosts read the 1 MiB-capped text, then call
 * [NetscapeCookieFile.validate] and [NetscapeCookieFile.parse]. Cookie text,
 * names, values, and the user's source path never enter logs, job documents,
 * settings JSON, history, fixtures, or the public vault.
 */
package com.anydownload.core.cookies

/**
 * One row of a Netscape cookie file.
 *
 * [domain] is the row's domain exactly as stored (a leading `.` still means
 * "include subdomains"). [includeSubdomains] is the file's second column.
 * [expiresAtEpochSeconds] of `0` (or a negative value) is a session cookie,
 * which never expires inside one app run. The name and value are credential
 * material: [toString] deliberately omits both.
 */
data class NetscapeCookie(
    val domain: String,
    val includeSubdomains: Boolean,
    val path: String,
    val secure: Boolean,
    val expiresAtEpochSeconds: Long,
    val name: String,
    val value: String,
) {
    /** Session cookies (no absolute expiry) are the only non-positive case. */
    fun isExpired(nowEpochSeconds: Long): Boolean =
        expiresAtEpochSeconds > 0 && expiresAtEpochSeconds <= nowEpochSeconds

    /** True when this row may be sent to [host] (case-insensitive, exact or subdomain). */
    fun matchesHost(host: String): Boolean {
        val target = host.trim().lowercase().removeSuffix(".")
        val cookieDomain = domain.trim().lowercase().removePrefix(".").removeSuffix(".")
        if (target.isEmpty() || cookieDomain.isEmpty()) return false
        if (target == cookieDomain) return true
        val subdomainAllowed = includeSubdomains || domain.trim().startsWith(".")
        return subdomainAllowed && target.endsWith(".$cookieDomain")
    }

    /** RFC 6265 path matching; an empty stored path behaves like `/`. */
    fun matchesPath(requestPath: String): Boolean {
        val path = requestPath.ifEmpty { "/" }
        val cookiePath = this.path.ifEmpty { "/" }
        if (path == cookiePath) return true
        if (!path.startsWith(cookiePath)) return false
        return cookiePath.endsWith("/") || path.getOrNull(cookiePath.length) == '/'
    }

    /** Never print the name or the value; the jar is credential material. */
    override fun toString(): String =
        "NetscapeCookie(domain=$domain, path=$path, secure=$secure, expires=$expiresAtEpochSeconds)"
}

/** Why a text is not an acceptable Netscape cookie file. Carries no contents. */
enum class NetscapeCookieFileError {
    /** The text is blank or holds only whitespace. */
    EMPTY,

    /** The text is larger than the 1 MiB owner cap. */
    TOO_LARGE,

    /** No header line and no non-comment tab-separated row with 7+ fields. */
    NOT_A_COOKIE_FILE,
}

/**
 * Validation and parsing for the Netscape cookie file the app accepts.
 *
 * The owner decision of 2026-09-30 fixes the rules: accept a leading
 * `# Netscape HTTP Cookie File` or `# HTTP Cookie File`, or a non-comment
 * tab-separated row with at least seven fields; reject anything else; never
 * partially import. `#HttpOnly_` rows are parsed with the marker removed, as
 * `http.cookiejar.MozillaCookieJar` does.
 */
object NetscapeCookieFile {
    /** Owner cap for the stored file and for the text handed to [validate]. */
    const val MAX_BYTES: Long = 1024L * 1024L

    private const val HEADER_NETSCAPE = "# Netscape HTTP Cookie File"
    private const val HEADER_HTTP = "# HTTP Cookie File"
    private const val HTTP_ONLY_PREFIX = "#HttpOnly_"
    private const val FIELD_COUNT = 7

    /**
     * Null when [text] is acceptable; otherwise the refusal reason. The
     * message a store shows is chosen by the store; it never includes the
     * text, a name, a value, or a path.
     */
    fun validate(text: String): NetscapeCookieFileError? {
        if (text.isBlank()) return NetscapeCookieFileError.EMPTY
        if (text.encodeToByteArray().size.toLong() > MAX_BYTES) return NetscapeCookieFileError.TOO_LARGE
        if (!looksLikeFile(text)) return NetscapeCookieFileError.NOT_A_COOKIE_FILE
        return null
    }

    /**
     * The format check alone: a leading header line, or any non-comment row
     * with at least seven tab-separated fields. Blank lines are ignored.
     */
    fun looksLikeFile(text: String): Boolean {
        var firstChecked = false
        var hasHeader = false
        var hasTabbedRow = false
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            if (!firstChecked) {
                firstChecked = true
                hasHeader = line.startsWith(HEADER_NETSCAPE) || line.startsWith(HEADER_HTTP)
            }
            val row = rowContent(line)
            if (row != null && row.split('\t').size >= FIELD_COUNT) hasTabbedRow = true
            if (hasHeader && hasTabbedRow) break
        }
        return hasHeader || hasTabbedRow
    }

    /**
     * Parses every well-formed row. Comment lines, blank lines, short rows,
     * non-numeric expiries, and rows whose name or value would corrupt a
     * `Cookie` header are skipped: a malformed row never becomes a request
     * header. The returned order is the file order.
     */
    fun parse(text: String): List<NetscapeCookie> {
        val cookies = mutableListOf<NetscapeCookie>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            val row = rowContent(line) ?: continue
            val fields = row.split('\t')
            if (fields.size < FIELD_COUNT) continue
            val domain = fields[0].trim()
            if (domain.isEmpty()) continue
            val includeSubdomains = fields[1].trim().equals("TRUE", ignoreCase = true)
            val path = fields[2].trim()
            val secure = fields[3].trim().equals("TRUE", ignoreCase = true)
            val expires = fields[4].trim().toLongOrNull() ?: continue
            val name = fields[5]
            val value = fields[6]
            if (!isLegalName(name) || !isLegalValue(value)) continue
            cookies += NetscapeCookie(
                domain = domain,
                includeSubdomains = includeSubdomains,
                path = path,
                secure = secure,
                expiresAtEpochSeconds = expires,
                name = name,
                value = value,
            )
        }
        return cookies
    }

    /**
     * Serializes parsed or browser-derived cookies back into Netscape text.
     * Rows whose name or value would corrupt the file (or a later `Cookie`
     * header) are skipped, exactly as on parse. The header line is always
     * written, so even an empty list produces a valid file the store can
     * reject as `EMPTY` at the jar gate.
     */
    fun format(cookies: List<NetscapeCookie>): String = buildString {
        append(HEADER_NETSCAPE).append('\n')
        for (cookie in cookies) {
            if (!isLegalName(cookie.name) || !isLegalValue(cookie.value)) continue
            append(cookie.domain).append('\t')
            append(if (cookie.includeSubdomains) "TRUE" else "FALSE").append('\t')
            append(cookie.path.ifEmpty { "/" }).append('\t')
            append(if (cookie.secure) "TRUE" else "FALSE").append('\t')
            append(cookie.expiresAtEpochSeconds).append('\t')
            append(cookie.name).append('\t')
            append(cookie.value).append('\n')
        }
    }

    /** The row content when [line] is a data row, or null for a comment. */
    private fun rowContent(line: String): String? = when {
        line.startsWith(HTTP_ONLY_PREFIX) -> line.removePrefix(HTTP_ONLY_PREFIX)
        line.startsWith("#") -> null
        else -> line
    }

    /** RFC 6265 `token`: letters, digits, and the listed punctuation. */
    private fun isLegalName(name: String): Boolean {
        if (name.isEmpty()) return false
        for (character in name) {
            val code = character.code
            if (character.isLetterOrDigit()) continue
            if (code !in 0x21..0x7e) return false
            if (character in "()<>@,;:\\\"/[]?={}") return false
        }
        return true
    }

    /** RFC 6265 `cookie-octet`: no controls, whitespace, comma, semicolon, or quote. */
    private fun isLegalValue(value: String): Boolean = value.all { character ->
        val code = character.code
        code == 0x21 || code in 0x23..0x2b || code in 0x2d..0x3a || code in 0x3c..0x5b || code in 0x5d..0x7e
    }
}
