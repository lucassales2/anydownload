package com.anydownlod.core.platform

/**
 * One-hop HTTP access for the shared engine and extractors.
 *
 * Each [execute] performs a single request and returns at most one redirect
 * hop. The caller owns the redirect budget ([HttpRedirects] decides whether a
 * hop is allowed to continue), re-validates the destination on every hop
 * (T-006 controls), and classifies the final response. The implementation
 * resolves a relative `Location` to an absolute URL before returning it,
 * because URL resolution belongs to the platform. Request headers pass
 * through [HttpRequest.sanitized] first; refused names never leave the
 * device.
 *
 * T-039 wires per-target implementations (java.net/NSURLSession/extension
 * bridge). T-056 adds method, headers, body, and byte ranges.
 */
interface HttpTransfer {
    /** Performs one [request]; see the class note for the hop contract. */
    suspend fun execute(request: HttpRequest): HttpResponse

    /** Convenience for the plain GETs the D2 direct-file path still sends. */
    suspend fun execute(url: String): HttpResponse = execute(HttpRequest(url))
}

/** One request outcome; the caller decides what to do with it. */
sealed interface HttpResponse {

    /** A redirect the caller may follow after validating [location]. */
    data class Redirect(
        val location: String,
        /** The 3xx status when the platform knows it; null when it does not. */
        val statusCode: Int? = null,
    ) : HttpResponse

    /** Any non-redirect status, including error statuses. */
    data class Final(
        val statusCode: Int,
        val contentType: String? = null,
        val totalBytes: Long? = null,
        val body: HttpBody? = null,
        /** Raw `Content-Range` header when the response was ranged. */
        val contentRange: String? = null,
        /** Allowlisted response headers with lowercased names; never cookies. */
        val headers: Map<String, String> = emptyMap(),
    ) : HttpResponse

    /**
     * The transport itself refused or could not complete the request
     * (permission, blocked destination, timeout, network). The engine maps
     * this to a typed job error; [message] is already redacted.
     */
    data class Failed(
        val reason: HttpFailureReason,
        val message: String,
    ) : HttpResponse

    /**
     * The host cannot perform requests at all (for example the web page
     * without the browser extension). The engine maps this to a typed
     * "engine unavailable" job error; the UI must never pretend a download
     * started. No request was sent when this is returned.
     */
    data class Unavailable(val reason: String) : HttpResponse
}

/**
 * A streamed response body. The engine reads it in fixed-size chunks and never
 * buffers a whole file in memory.
 *
 * [readNext] copies at most [buffer.size] bytes into [buffer] and returns the
 * number copied, or `-1` at end of body. Implementations must suspend
 * cooperatively so cancellation is observed, and must free resources in
 * [close]. Raw body content never reaches logs or jobs.
 */
interface HttpBody {
    suspend fun readNext(buffer: ByteArray): Int
    suspend fun close()
}
