package com.anydownlod.core.platform

/**
 * One-hop HTTP access for the shared engine.
 *
 * Each [execute] performs a single request and returns at most one redirect
 * hop. The engine owns the redirect budget, re-validates the destination on
 * every hop (T-006 controls), and classifies the final response. The
 * implementation resolves a relative `Location` to an absolute URL before
 * returning it, because URL resolution belongs to the platform.
 *
 * T-039 wires per-target implementations (Ktor/OkHttp/NSURLSession/extension
 * bridge). This file is the contract only.
 */
interface HttpTransfer {
    suspend fun execute(url: String): HttpResponse
}

/** One request outcome; the caller decides what to do with it. */
sealed interface HttpResponse {

    /** A redirect the engine may follow after validating [location]. */
    data class Redirect(val location: String) : HttpResponse

    /** Any non-redirect status, including error statuses. */
    data class Final(
        val statusCode: Int,
        val contentType: String? = null,
        val totalBytes: Long? = null,
        val body: HttpBody? = null,
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