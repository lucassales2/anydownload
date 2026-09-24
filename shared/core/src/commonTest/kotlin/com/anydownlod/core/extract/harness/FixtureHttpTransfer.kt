package com.anydownlod.core.extract.harness

import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.HttpBody
import com.anydownlod.core.platform.HttpFailureReason
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer

/** Reads a recorded fixture by its repository-relative resource path. */
fun interface FixtureStore {
    fun read(path: String): String?

    companion object {
        val Empty: FixtureStore = FixtureStore { null }

        /** An in-memory store for synthesized fixtures; the default CI path. */
        fun of(entries: Map<String, String>): FixtureStore = FixtureStore { entries[it] }
    }
}

/**
 * One recorded response. Either [body] (synthesized inline) or [bodyResource]
 * (a redacted file under `shared/core/src/commonTest/resources/`) is used;
 * [urlPattern] is matched against the request URL with `*` wildcards.
 */
data class FixtureRoute(
    val urlPattern: String,
    val method: String = "GET",
    val statusCode: Int = 200,
    val contentType: String? = null,
    val body: String? = null,
    val bodyResource: String? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
) {
    internal fun matches(request: HttpRequest): Boolean =
        method.equals(request.method, ignoreCase = true) && glob(urlPattern).matches(request.url)

    internal fun bodyBytes(store: FixtureStore): ByteArray? =
        body?.encodeToByteArray() ?: bodyResource?.let { store.read(it) }?.encodeToByteArray()

    private fun glob(pattern: String): Regex = Regex(
        "^" + pattern.split("*").joinToString(".*") { Regex.escape(it) } + "$",
    )
}

/** A fixture lookup failed; the message names the method and path only. */
class FixtureMissingException(message: String) : IllegalStateException(message)

/**
 * A one-hop transport backed by [routes]. A request that matches no route
 * fails with a [FixtureMissingException] so a missing fixture is obvious;
 * the transfer never touches the network.
 */
class FixtureHttpTransfer(
    private val routes: List<FixtureRoute>,
    private val store: FixtureStore = FixtureStore.Empty,
) : HttpTransfer {

    val requests: MutableList<HttpRequest> = mutableListOf()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        val route = routes.firstOrNull { it.matches(request) }
            ?: throw FixtureMissingException("No fixture for ${request.method} ${redactedPath(request.url)}")
        val bytes = route.bodyBytes(store)
            ?: throw FixtureMissingException("Fixture resource missing: ${route.bodyResource ?: route.urlPattern}")
        val body: HttpBody? = if (bytes.isEmpty()) null else ByteArrayHttpBody(bytes)
        return HttpResponse.Final(
            statusCode = route.statusCode,
            contentType = route.contentType,
            totalBytes = bytes.size.toLong(),
            body = body,
            headers = route.responseHeaders,
        )
    }

    private fun redactedPath(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        val hostAndPath = withoutScheme.substringBefore('?').substringBefore('#')
        return if (url.contains("://")) hostAndPath else withoutScheme.take(80)
    }
}
