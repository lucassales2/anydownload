package com.anydownlod.core.platform

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * In-process [HttpTransfer] for Android built on `java.net`, mirroring the
 * JVM host. One request per call with redirects off: a 3xx is surfaced as
 * [HttpResponse.Redirect] with an absolute, resolved `Location`; the shared
 * engine re-validates every destination. Bodies stream in the caller's buffer
 * size; nothing is buffered whole-file.
 */
class JavaNetHttpTransfer(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) : HttpTransfer {

    override suspend fun execute(url: String): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept-Encoding", "identity")

            val status = connection.responseCode
            if (status in 300..399) {
                val rawLocation = connection.getHeaderField("Location")
                if (rawLocation.isNullOrBlank()) {
                    return HttpResponse.Final(statusCode = status)
                }
                val resolved = runCatching { URI(url).resolve(rawLocation).toString() }
                    .getOrElse { rawLocation }
                return HttpResponse.Redirect(resolved)
            }

            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = input?.let { InputStreamBody(it, onClosed = { connection.disconnect() }) }
            return HttpResponse.Final(
                statusCode = status,
                contentType = connection.contentType,
                totalBytes = connection.contentLengthLong.takeIf { it >= 0L },
                body = body,
            )
        } catch (failure: Throwable) {
            connection.disconnect()
            throw failure
        }
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