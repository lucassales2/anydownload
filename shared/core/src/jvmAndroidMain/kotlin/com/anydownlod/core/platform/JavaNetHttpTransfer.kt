package com.anydownlod.core.platform

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * In-process [HttpTransfer] for JVM and Android built on `java.net`.
 *
 * One request per call with redirects **off**: a 3xx is surfaced as
 * [HttpResponse.Redirect] with an absolute `Location` (relative locations are
 * resolved against the request URL here, because URL resolution belongs to
 * the platform). The caller drives the redirect budget ([HttpRedirects]) and
 * re-validates every destination, so this class never needs to know the
 * blocklist.
 *
 * T-056 adds method, allowlisted request headers, a fixed-length request
 * body, and a byte range. The request never carries a header outside
 * [HttpHeaders.ALLOWLIST]; refused names are reported through
 * [onDroppedHeaders] by name only. Response headers are filtered by the same
 * allowlist, so a `Set-Cookie` never crosses this boundary. Bodies stream in
 * the caller's buffer size; nothing is buffered whole-file. Timeouts keep a
 * stalled source from hanging a job forever.
 */
class JavaNetHttpTransfer(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
    private val onDroppedHeaders: (List<String>) -> Unit = {},
) : HttpTransfer {

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val sanitized = request.sanitized()
        if (sanitized.droppedHeaders.isNotEmpty()) onDroppedHeaders(sanitized.droppedHeaders)
        val outgoing = sanitized.request

        val connection = URL(outgoing.url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.requestMethod = outgoing.method
            // No compression so the caller's bytes are exactly the source bytes.
            connection.setRequestProperty("Accept-Encoding", "identity")
            for ((name, value) in outgoing.headers) {
                connection.setRequestProperty(name, value)
            }
            val requestBody = outgoing.body
            if (requestBody != null && !outgoing.method.equals(HttpMethods.GET, ignoreCase = true)) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(requestBody.size)
                connection.outputStream.use { it.write(requestBody) }
            }

            val status = connection.responseCode
            if (status in 300..399) {
                val rawLocation = connection.getHeaderField("Location")
                if (rawLocation.isNullOrBlank()) {
                    // The caller reports this as a network failure.
                    return HttpResponse.Final(
                        statusCode = status,
                        headers = allowlistedResponseHeaders(connection),
                    )
                }
                // Resource resolution happens on the platform. A malformed
                // Location falls back to the raw string; the caller policy
                // still validates it before the next hop.
                val resolved = runCatching { URI(outgoing.url).resolve(rawLocation).toString() }
                    .getOrElse { rawLocation }
                return HttpResponse.Redirect(resolved, status)
            }

            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = input?.let { InputStreamBody(it, onClosed = { connection.disconnect() }) }
            val contentRange = connection.getHeaderField("Content-Range")
            return HttpResponse.Final(
                statusCode = status,
                contentType = connection.contentType,
                totalBytes = ContentRange.totalBytes(contentRange)
                    ?: connection.contentLengthLong.takeIf { it >= 0L },
                body = body,
                contentRange = contentRange,
                headers = allowlistedResponseHeaders(connection),
            )
        } catch (failure: Throwable) {
            connection.disconnect()
            throw failure
        }
    }

    private fun allowlistedResponseHeaders(connection: HttpURLConnection): Map<String, String> {
        val collected = linkedMapOf<String, String>()
        for ((name, values) in connection.headerFields) {
            if (name == null || values.isEmpty()) continue
            collected[name] = values.first()
        }
        return HttpHeaders.filterResponse(collected)
    }
}

private class InputStreamBody(
    private val input: java.io.InputStream,
    private val onClosed: (() -> Unit)? = null,
) : HttpBody {
    override suspend fun readNext(buffer: ByteArray): Int =
        input.read(buffer, 0, buffer.size)

    override suspend fun close() {
        input.close()
        onClosed?.invoke()
    }
}
