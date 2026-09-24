package com.anydownlod.web

import com.anydownlod.core.engine.WebDownload
import com.anydownlod.core.engine.WebExtensionBridge
import com.anydownlod.core.engine.WebFailureCode
import com.anydownlod.core.engine.WebFetch
import com.anydownlod.core.engine.WebPage
import com.anydownlod.core.engine.WebProbe
import com.anydownlod.core.platform.HttpRequest
import kotlinx.browser.window
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.MessageEvent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.JsString
import kotlin.js.toJsReference
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The page side of the extension bridge over `window.postMessage`, payloads
 * exchanged as JSON strings (Kotlin/Wasm has no `dynamic` type).
 *
 * This class never calls `fetch`; the Manifest V3 extension (see
 * `apps/web-extension`) is the only thing that touches the network. Without
 * the extension's content script, [available] is false and the engine refuses
 * a submit before any message is sent — Add never pretends a download
 * started, and the page performs no cross-origin fetch.
 */
class WindowExtensionBridge : WebExtensionBridge {

    private data class Pending(val messages: Channel<ExtensionMessage>)

    private val pending = mutableMapOf<String, Pending>()
    private var requestCounter = 0
    private var listenerAttached = false

    private fun ensureListener() {
        if (listenerAttached) return
        listenerAttached = true
        window.addEventListener("message") { event ->
            val data = (event as MessageEvent).data
            if (data == null) return@addEventListener
            val text = (data as? JsString)?.toString() ?: return@addEventListener
            val message = runCatching { Json.decodeFromString<ExtensionMessage>(text) }.getOrNull()
                ?: return@addEventListener
            if (message.source != "anydownload-extension") return@addEventListener
            val pendingFor = pending[message.requestId] ?: return@addEventListener
            pendingFor.messages.trySend(message)
        }
    }

    override val available: Boolean
        get() = extensionMarkerPresent()

    override suspend fun probe(url: String): WebProbe {
        ensureListener()
        val requestId = nextRequestId()
        val channel = Pending(Channel(capacity = Channel.UNLIMITED)).also { pending[requestId] = it }
        return try {
            withTimeout<WebProbe>(30.seconds) {
                postMessage(
                    ExtensionMessage(
                        source = "anydownload-page",
                        type = "probe",
                        requestId = requestId,
                        url = url,
                    )
                )
                while (true) {
                    val reply = channel.messages.receive()
                    if (reply.type == "probe-reply") return@withTimeout parseProbeReply(reply)
                }
                error("Unreachable")
            }
        } catch (timeout: Throwable) {
            pending.remove(requestId)
            WebProbe.Failed(WebFailureCode.TIMEOUT, "The extension did not answer in time.")
        }
    }

    override suspend fun download(
        url: String,
        jobId: String,
        headers: Map<String, String>,
        saveViaBlob: Boolean,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): WebDownload {
        ensureListener()
        val requestId = nextRequestId()
        val channel = Pending(Channel(capacity = Channel.UNLIMITED)).also { pending[requestId] = it }
        return try {
            withTimeout<WebDownload>(10.minutes) {
                postMessage(
                    ExtensionMessage(
                        source = "anydownload-page",
                        type = "download",
                        requestId = requestId,
                        url = url,
                        jobId = jobId,
                        headers = headers.ifEmpty { null },
                        saveViaBlob = saveViaBlob.takeIf { it },
                    )
                )
                while (true) {
                    val reply = channel.messages.receive()
                    when (reply.type) {
                        "progress" -> onProgress(reply.downloadedBytes ?: 0L, reply.totalBytes)
                        "download-reply" -> return@withTimeout parseDownloadReply(reply)
                    }
                }
                error("Unreachable")
            }
        } catch (timeout: Throwable) {
            pending.remove(requestId)
            WebDownload.Failed(WebFailureCode.TIMEOUT, "The download took too long.")
        }
    }

    override suspend fun fetchPage(url: String): WebPage {
        ensureListener()
        val requestId = nextRequestId()
        val channel = Pending(Channel(capacity = Channel.UNLIMITED)).also { pending[requestId] = it }
        return try {
            withTimeout<WebPage>(60.seconds) {
                postMessage(
                    ExtensionMessage(
                        source = "anydownload-page",
                        type = "fetch-page",
                        requestId = requestId,
                        url = url,
                    )
                )
                while (true) {
                    val reply = channel.messages.receive()
                    if (reply.type == "fetch-page-reply") return@withTimeout parsePageReply(reply)
                }
                error("Unreachable")
            }
        } catch (timeout: Throwable) {
            pending.remove(requestId)
            WebPage.Failed(WebFailureCode.TIMEOUT, "The extension did not answer in time.")
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun fetch(request: HttpRequest): WebFetch {
        ensureListener()
        val requestId = nextRequestId()
        val channel = Pending(Channel(capacity = Channel.UNLIMITED)).also { pending[requestId] = it }
        return try {
            withTimeout<WebFetch>(2.minutes) {
                postMessage(
                    ExtensionMessage(
                        source = "anydownload-page",
                        type = "fetch-request",
                        requestId = requestId,
                        url = request.url,
                        method = request.method,
                        headers = request.headers,
                        bodyBase64 = request.body?.let { Base64.encode(it) },
                        range = request.range?.let { "bytes=${it.first}-${it.last}" },
                    )
                )
                while (true) {
                    val reply = channel.messages.receive()
                    if (reply.type == "fetch-request-reply") return@withTimeout parseFetchReply(reply)
                }
                error("Unreachable")
            }
        } catch (timeout: Throwable) {
            pending.remove(requestId)
            WebFetch.Failed(WebFailureCode.TIMEOUT, "The extension did not answer in time.")
        }
    }
    private fun parsePageReply(reply: ExtensionMessage): WebPage = when (reply.kind) {
        "final" -> WebPage.Final(
            finalUrl = reply.finalUrl ?: "",
            html = reply.html ?: "",
        )

        else -> WebPage.Failed(
            code = failureCode(reply.code),
            message = reply.message ?: "The extension could not fetch this page.",
        )
    }

    override suspend fun cancelDownload(jobId: String) {
        ensureListener()
        postMessage(ExtensionMessage(source = "anydownload-page", type = "cancel", jobId = jobId))
    }

    private fun parseProbeReply(reply: ExtensionMessage): WebProbe = when (reply.kind) {
        "final" -> WebProbe.Final(
            statusCode = reply.status ?: 0,
            contentType = reply.contentType,
            totalBytes = reply.totalBytes,
            finalUrl = reply.finalUrl ?: "",
        )

        else -> WebProbe.Failed(
            code = failureCode(reply.code),
            message = reply.message ?: "The extension could not probe this URL.",
        )
    }

    private fun parseFetchReply(reply: ExtensionMessage): WebFetch = when (reply.kind) {
        "final" -> WebFetch.Final(
            statusCode = reply.status ?: 0,
            contentType = reply.contentType,
            totalBytes = reply.totalBytes,
            contentRange = reply.contentRange,
            headers = reply.responseHeaders ?: emptyMap(),
            body = decodeBase64(reply.bodyBase64),
            finalUrl = reply.finalUrl ?: "",
            sentHeaders = reply.sentHeaders ?: emptyMap(),
        )

        else -> WebFetch.Failed(
            code = failureCode(reply.code),
            message = reply.message ?: "The extension could not complete this request.",
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64(value: String?): ByteArray {
        if (value.isNullOrEmpty()) return ByteArray(0)
        return runCatching { Base64.decode(value) }.getOrDefault(ByteArray(0))
    }

    private fun parseDownloadReply(reply: ExtensionMessage): WebDownload = when (reply.kind) {
        "completed" -> WebDownload.Completed(
            fileName = reply.fileName ?: "download",
            sizeBytes = reply.sizeBytes,
        )

        "aborted" -> WebDownload.Aborted(reply.message ?: "aborted")

        else -> WebDownload.Failed(
            code = failureCode(reply.code),
            message = reply.message ?: "The download failed.",
        )
    }

    private fun failureCode(code: String?): WebFailureCode = when (code) {
        "permission" -> WebFailureCode.PERMISSION
        "blocked" -> WebFailureCode.BLOCKED_DESTINATION
        "timeout" -> WebFailureCode.TIMEOUT
        "network" -> WebFailureCode.NETWORK
        else -> WebFailureCode.OTHER
    }

    private fun nextRequestId(): String = "req-${++requestCounter}"

    private fun postMessage(message: ExtensionMessage) {
        window.postMessage(Json.encodeToString(message).toJsReference(), "*")
    }
}

/**
 * Single-expression check for the content-script marker (js() must be the
 * whole expression in Kotlin/Wasm). The extension sets a string marker, so an
 * unqualified read is safe: undefined is null here.
 */
private fun extensionMarkerPresent(): Boolean =
    js("window.__anydownloadExtension !== undefined")

/**
 * One flat wire message for both directions. Every field is optional so a
 * single serializer class can carry probe replies, progress, and outcomes.
 */
@Serializable
internal data class ExtensionMessage(
    /** "anydownload-page" or "anydownload-extension". */
    val source: String = "",
    val type: String = "",
    val requestId: String = "",
    val url: String? = null,
    val jobId: String? = null,
    /** "final", "completed", "failed", "aborted". */
    val kind: String = "",
    val status: Int? = null,
    val contentType: String? = null,
    val totalBytes: Long? = null,
    val finalUrl: String? = null,
    val downloadedBytes: Long? = null,
    val fileName: String? = null,
    val sizeBytes: Long? = null,
    /** Bounded page text from the extension (fetch-page reply). */
    val html: String? = null,
    /** Request fields carried to the extension (fetch-request). */
    val method: String? = null,
    val headers: Map<String, String>? = null,
    val bodyBase64: String? = null,
    val range: String? = null,
    /** Ask the extension to fetch the media itself and save a Blob URL. */
    val saveViaBlob: Boolean? = null,
    /** Response fields returned by the extension (fetch-request reply). */
    val contentRange: String? = null,
    val responseHeaders: Map<String, String>? = null,
    val sentHeaders: Map<String, String>? = null,
    /** "permission", "blocked", "network", "timeout", "other". */
    val code: String? = null,
    val message: String? = null,
)