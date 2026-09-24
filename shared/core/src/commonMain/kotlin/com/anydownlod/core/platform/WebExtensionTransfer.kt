package com.anydownlod.core.platform

import com.anydownlod.core.engine.WebExtensionBridge
import com.anydownlod.core.engine.WebFailureCode
import com.anydownlod.core.engine.WebFetch

/**
 * Web (Compose/Wasm) [HttpTransfer] over the browser extension.
 *
 * The page must never fetch arbitrary origins (CORS makes it futile and the
 * extension owns host permissions), so every request goes through
 * [WebExtensionBridge.fetch]; without a bridge this returns a typed
 * [HttpResponse.Unavailable] and no request is sent. The extension applies
 * its own `checkUrl` policy and reports the effective request header set it
 * actually sent.
 */
class WebExtensionTransfer(
    private val bridge: WebExtensionBridge? = null,
    private val onDroppedHeaders: (List<String>) -> Unit = {},
) : HttpTransfer {

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val active = bridge
            ?: return HttpResponse.Unavailable("The browser extension is required to download files.")
        val sanitized = request.sanitized()
        return when (val fetched = active.fetch(sanitized.request)) {
            is WebFetch.Final -> {
                // Names the allowlist or MV3's fetch dropped before the request
                // left the browser; values never cross this boundary.
                val refused = (sanitized.droppedHeaders +
                    (sanitized.request.headers.keys - fetched.sentHeaders.keys))
                    .distinct()
                    .sorted()
                if (refused.isNotEmpty()) onDroppedHeaders(refused)
                HttpResponse.Final(
                    statusCode = fetched.statusCode,
                    contentType = fetched.contentType,
                    totalBytes = fetched.totalBytes,
                    body = ByteArrayHttpBody(fetched.body),
                    contentRange = fetched.contentRange,
                    headers = fetched.headers,
                )
            }

            is WebFetch.Failed -> {
                if (sanitized.droppedHeaders.isNotEmpty()) onDroppedHeaders(sanitized.droppedHeaders)
                HttpResponse.Failed(fetched.code.toFailureReason(), fetched.message)
            }
        }
    }
}

private fun WebFailureCode.toFailureReason(): HttpFailureReason = when (this) {
    WebFailureCode.PERMISSION -> HttpFailureReason.PERMISSION
    WebFailureCode.BLOCKED_DESTINATION -> HttpFailureReason.BLOCKED_DESTINATION
    WebFailureCode.TIMEOUT -> HttpFailureReason.TIMEOUT
    WebFailureCode.NETWORK -> HttpFailureReason.NETWORK
    WebFailureCode.OTHER -> HttpFailureReason.OTHER
}
