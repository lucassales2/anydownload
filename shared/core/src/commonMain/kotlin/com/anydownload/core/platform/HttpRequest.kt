package com.anydownload.core.platform

/**
 * One extractor HTTP request (T-056).
 *
 * [method] is a plain string because that is what the extension bridge and
 * `java.net` take; [HttpMethods] names the two methods the port supports.
 * [headers] are request headers an extractor declares; every name is checked
 * against [HttpHeaders.ALLOWLIST] before transport (see [sanitized]).
 * [range] is the inclusive byte range; it becomes a `Range` header unless the
 * caller already declared one. [body] is sent only for non-GET methods.
 * [authorization] is a full `Authorization` header value that only trusted
 * metadata clients set (D6 Spotify); it never enters the [headers] map, so
 * extractor-declared headers still cannot carry credentials.
 * [cookie] is a full `Cookie` header value that only the local cookie jar
 * sets (D11 T-018); like [authorization] it is added after the header
 * allowlist and never enters [SanitizedRequest.droppedHeaders].
 */
data class HttpRequest(
    val url: String,
    val method: String = HttpMethods.GET,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val range: LongRange? = null,
    val authorization: String? = null,
    val cookie: String? = null,
) {
    /**
     * Applies the request-header allowlist and turns [range] into a `Range`
     * header. [authorization] and [cookie], when present, are added after the
     * allowlist so the trusted fields are the only way a credential leaves the
     * device; the values never appear in [SanitizedRequest.droppedHeaders] or
     * any diagnostic. The returned [SanitizedRequest.droppedHeaders] names are
     * safe to report in redacted diagnostics (names only, never values).
     */
    fun sanitized(): SanitizedRequest {
        val withRange = if (range != null && headers.keys.none { it.equals(HeaderNames.RANGE, ignoreCase = true) }) {
            headers + (HeaderNames.RANGE to "bytes=${range.first}-${range.last}")
        } else {
            headers
        }
        val sanitized = HttpHeaders.sanitize(withRange)
        var accepted = sanitized.accepted
        if (!authorization.isNullOrBlank()) {
            accepted = accepted + (HeaderNames.AUTHORIZATION to authorization)
        }
        if (!cookie.isNullOrBlank()) {
            accepted = accepted + (HeaderNames.COOKIE to cookie)
        }
        return SanitizedRequest(copy(headers = accepted, authorization = null, cookie = null), sanitized.dropped)
    }
}

/** A request as it will be sent, plus the header names that were refused. */
data class SanitizedRequest(
    val request: HttpRequest,
    val droppedHeaders: List<String>,
)

object HttpMethods {
    const val GET = "GET"
    const val POST = "POST"
}

object HeaderNames {
    const val RANGE = "range"
    const val CONTENT_RANGE = "content-range"
    const val CONTENT_TYPE = "content-type"
    const val CONTENT_LENGTH = "content-length"
    const val LOCATION = "location"
    const val AUTHORIZATION = "authorization"
    const val COOKIE = "cookie"
}

/**
 * The request- and response-header allowlists for the port.
 *
 * Only names in [ALLOWLIST] leave the device through [sanitize]. `Cookie`,
 * `Authorization` from the header map, and every `X-Goog-*` auth header are
 * refused; the visitor id is the one `x-goog-*` name upstream's innertube
 * clients may declare. The `x-disco-*` pair are the fixed public client
 * identifiers the Discovery API needs and carry no credential. The
 * `x-frontend-*`, `x-niconico-language`, and
 * `x-request-with` names are the fixed public client identifiers the
 * Niconico APIs need; `x-access-right-key` carries the short-lived key the
 * watch API itself returned for one video and is never persisted or logged.
 * `api-auth` carries the ZDF token the token endpoint itself returned for the
 * session, and `apollo-require-preflight` is a fixed boolean the ZDF GraphQL
 * endpoint expects; neither is persisted or logged. A trusted caller may set the explicit
 * `HttpRequest.authorization` field instead (D6 Spotify metadata) or the
 * `HttpRequest.cookie` field (D11 T-018 jar); those values are added after
 * this filter and never enter a diagnostic. Refused names are dropped, never
 * rewritten, and reported by name only.
 */
object HttpHeaders {
    val ALLOWLIST: Set<String> = setOf(
        "accept",
        "accept-language",
        "api-auth",
        "apollo-require-preflight",
        "content-type",
        "origin",
        "referer",
        "user-agent",
        "range",
        "x-disco-client",
        "x-disco-params",
        "x-access-right-key",
        "x-frontend-id",
        "x-frontend-version",
        "x-niconico-language",
        "x-request-with",
        "x-youtube-client-name",
        "x-youtube-client-version",
        "x-goog-visitor-id",
        "x-origin",
    )

    val RESPONSE_ALLOWLIST: Set<String> = setOf(
        "content-type",
        "content-length",
        "content-range",
        "accept-ranges",
        "location",
        "etag",
        "last-modified",
    )

    private val REFUSED = setOf(
        "cookie",
        "cookie2",
        "authorization",
        "proxy-authorization",
    )

    private val REFUSED_PREFIXES = setOf("x-goog-")

    private val REFUSED_PREFIX_EXCEPTIONS = setOf("x-goog-visitor-id")

    data class Sanitized(val accepted: Map<String, String>, val dropped: List<String>)

    /** Lowercases names, refuses anything not allowlisted, and reports names only. */
    fun sanitize(headers: Map<String, String>): Sanitized {
        val accepted = linkedMapOf<String, String>()
        val dropped = linkedSetOf<String>()
        for ((name, value) in headers) {
            val lower = name.trim().lowercase()
            if (lower.isEmpty() || value.isEmpty()) continue
            if (isAllowedRequestHeader(lower)) {
                accepted[lower] = value
            } else {
                dropped += lower
            }
        }
        return Sanitized(accepted, dropped.sorted())
    }

    fun isAllowedRequestHeader(name: String): Boolean {
        val lower = name.lowercase()
        if (lower in REFUSED) return false
        if (lower in REFUSED_PREFIX_EXCEPTIONS) return true
        if (REFUSED_PREFIXES.any { lower.startsWith(it) }) return false
        return lower in ALLOWLIST
    }

    /** Keeps the allowlisted response headers with lowercased names. */
    fun filterResponse(headers: Map<String, String>): Map<String, String> {
        val filtered = linkedMapOf<String, String>()
        for ((name, value) in headers) {
            val lower = name.trim().lowercase()
            if (lower in RESPONSE_ALLOWLIST && value.isNotEmpty()) filtered[lower] = value
        }
        return filtered
    }
}

/** Why a transfer could not produce a response. */
enum class HttpFailureReason {
    PERMISSION,
    BLOCKED_DESTINATION,
    TIMEOUT,
    NETWORK,
    OTHER,
}

/** Decision for one redirect hop; the caller owns the redirect budget. */
sealed interface RedirectDecision {
    data class Follow(val request: HttpRequest) : RedirectDecision
    data class Fails(val reason: RedirectFailure) : RedirectDecision
}

enum class RedirectFailure {
    MISSING_LOCATION,
    POST_REDIRECT_NEEDS_303,
}

/**
 * Redirect handling for the extractor HTTP port.
 *
 * A `303 See Other` becomes a body-less `GET`. Every other status keeps the
 * method: a `GET` follows with the same headers, and a non-GET fails typed,
 * because silently turning a `POST` into a `GET` can change what the server
 * answers.
 */
object HttpRedirects {
    fun afterRedirect(request: HttpRequest, statusCode: Int, location: String): RedirectDecision {
        if (location.isBlank()) return RedirectDecision.Fails(RedirectFailure.MISSING_LOCATION)
        if (statusCode == 303) return RedirectDecision.Follow(HttpRequest(url = location))
        if (request.method.equals(HttpMethods.GET, ignoreCase = true)) {
            return RedirectDecision.Follow(request.copy(url = location))
        }
        return RedirectDecision.Fails(RedirectFailure.POST_REDIRECT_NEEDS_303)
    }
}

/** A body over an in-memory byte array; used by the extension bridge and tests. */
class ByteArrayHttpBody(private val bytes: ByteArray) : HttpBody {
    private var offset = 0

    override suspend fun readNext(buffer: ByteArray): Int {
        if (offset >= bytes.size) return -1
        val count = minOf(bytes.size - offset, buffer.size)
        bytes.copyInto(buffer, 0, offset, offset + count)
        offset += count
        return count
    }

    override suspend fun close() = Unit
}

/** Shared parser for `Content-Range: bytes 0-1023/12345`. */
object ContentRange {
    /** An inclusive `bytes start-end/total` range; [total] is null for `*`. */
    data class Bytes(val start: Long, val end: Long, val total: Long?)

    fun totalBytes(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val total = value.substringAfterLast('/', "").trim()
        return total.toLongOrNull()?.takeIf { it >= 0 }
    }

    /** The announced inclusive range, or null when the header is malformed. */
    fun parse(value: String?): Bytes? {
        if (value.isNullOrBlank()) return null
        val range = value.trim().removePrefix("bytes").removePrefix("=").trim().substringBefore('/').trim()
        val parts = range.split('-', limit = 2)
        if (parts.size != 2) return null
        val start = parts[0].trim().toLongOrNull() ?: return null
        val end = parts[1].trim().toLongOrNull() ?: return null
        if (start < 0 || end < start) return null
        return Bytes(start, end, totalBytes(value))
    }

    /** The first byte of the announced range, or null when it is absent or malformed. */
    fun startByte(value: String?): Long? = parse(value)?.start
}
