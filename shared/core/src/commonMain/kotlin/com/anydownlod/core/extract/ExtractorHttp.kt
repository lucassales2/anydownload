/*
 * Extractor HTTP helper — AnyDownload
 *
 * Translation of the request behavior extractors use from
 * `yt_dlp/extractor/common.py` (`_download_webpage`, `_download_json` and
 * their redirect handling) at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-24. Unlicense; see
 * shared/core/NOTICE.md. `common.py` is not vendored.
 */
package com.anydownlod.core.extract

import com.anydownlod.core.platform.HttpBody
import com.anydownlod.core.platform.HttpFailureReason
import com.anydownlod.core.platform.HttpMethods
import com.anydownlod.core.platform.HttpRedirects
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.platform.RedirectDecision
import kotlinx.serialization.json.JsonElement

/**
 * The HTTP seam every extractor depends on.
 *
 * Reads are bounded by [maxBytes] (512 KiB by default; the caller passes a
 * larger cap for player JavaScript). Redirects are followed here with the
 * shared [HttpRedirects] rule — `303` becomes a body-less GET, a GET keeps its
 * headers, a non-GET on any other 3xx fails typed. Response bodies are never
 * logged and errors carry only a redacted message and the HTTP status.
 */
class ExtractorHttp(
    private val transfer: HttpTransfer,
    private val maxRedirects: Int = 5,
    val defaultMaxBytes: Int = DEFAULT_MAX_BYTES,
) {
    companion object {
        const val DEFAULT_MAX_BYTES: Int = 512 * 1024
        private const val CHUNK_BYTES = 64 * 1024
    }

    /** Upstream `_download_webpage`: a bounded GET, decoded as text. */
    suspend fun downloadWebpage(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxBytes: Int = defaultMaxBytes,
        authorization: String? = null,
    ): String =
        downloadBytes(HttpRequest(url = url, headers = headers, authorization = authorization), maxBytes)
            .decodeToString()

    /**
     * Upstream `_download_json`: a request whose response must be JSON. The
     * body is sent with `Content-Type: application/json` unless the caller
     * declared one. [authorization] is a full `Authorization` value for the
     * trusted metadata clients; extractors leave it null.
     */
    suspend fun downloadJson(
        url: String,
        method: String = HttpMethods.GET,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        maxBytes: Int = defaultMaxBytes,
        authorization: String? = null,
    ): JsonElement {
        val withContentType = if (body != null && headers.keys.none { it.equals("content-type", true) }) {
            headers + ("content-type" to "application/json")
        } else {
            headers
        }
        val text = downloadBytes(
            HttpRequest(url, method, withContentType, body, authorization = authorization),
            maxBytes,
        ).decodeToString()
        return ExtractorUtils.parseJson(text, fatal = true)
            ?: throw ExtractionError.Malformed("The response was empty.")
    }

    /** A bounded body for callers that decode it themselves (player JS, XML). */
    suspend fun downloadBytes(request: HttpRequest, maxBytes: Int = defaultMaxBytes): ByteArray {
        var current = request
        var hops = 0
        while (true) {
            when (val response = transfer.execute(current)) {
                is HttpResponse.Final -> {
                    val body = response.body
                    if (response.statusCode !in 200..299) {
                        runCatching { body?.close() }
                        throw errorForStatus(response.statusCode)
                    }
                    if (body == null) throw ExtractionError.Malformed("The response was empty.")
                    return readBounded(body, maxBytes)
                }

                is HttpResponse.Redirect -> {
                    hops++
                    if (hops > maxRedirects) throw ExtractionError.Malformed("Too many redirects.")
                    when (
                        val decision = HttpRedirects.afterRedirect(
                            current,
                            response.statusCode ?: 302,
                            response.location,
                        )
                    ) {
                        is RedirectDecision.Follow -> current = decision.request
                        is RedirectDecision.Fails -> throw ExtractionError.Unavailable(
                            "The source redirected in a way this app will not follow.",
                        )
                    }
                }

                is HttpResponse.Unavailable -> throw ExtractionError.Unavailable(response.reason)

                is HttpResponse.Failed -> throw when (response.reason) {
                    HttpFailureReason.PERMISSION -> ExtractionError.Unavailable(response.message)
                    HttpFailureReason.BLOCKED_DESTINATION -> ExtractionError.UnsupportedUrl(response.message)
                    HttpFailureReason.TIMEOUT -> ExtractionError.Unavailable("The source timed out.")
                    else -> ExtractionError.Unavailable("The source could not be reached.")
                }
            }
        }
    }

    private suspend fun readBounded(body: HttpBody, maxBytes: Int): ByteArray {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val buffer = ByteArray(minOf(maxBytes, CHUNK_BYTES))
        var out = ByteArray(minOf(maxBytes, CHUNK_BYTES))
        var total = 0
        try {
            while (total < maxBytes) {
                val count = body.readNext(buffer)
                if (count == -1) break
                if (count == 0) continue
                val keep = minOf(count, maxBytes - total)
                if (total + keep > out.size) {
                    out = out.copyOf(minOf(maxBytes, maxOf(out.size * 2, total + keep)))
                }
                buffer.copyInto(out, total, 0, keep)
                total += keep
            }
        } finally {
            runCatching { body.close() }
        }
        return out.copyOf(total)
    }

    private fun errorForStatus(status: Int): ExtractionError = when (status) {
        401, 407 -> ExtractionError.LoginRequired()
        403 -> ExtractionError.Unavailable("The source refused access.")
        404, 410, 451 -> ExtractionError.Unavailable("This source is unavailable or was removed.")
        429 -> ExtractionError.Unavailable("The source rate-limited the request.")
        else -> ExtractionError.Unavailable("The source returned HTTP $status.")
    }
}
