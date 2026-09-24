package com.anydownlod.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionDelegateProtocol
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.appendBytes
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.math.min

/**
 * In-process iOS [HttpTransfer] built on NSURLSession.
 *
 * One hop per [execute]: the data-task delegate never follows redirects, so a
 * 3xx is surfaced as [HttpResponse.Redirect] with an absolute resolved
 * `Location` and the caller re-validates every destination (per-hop policy
 * holds on iOS too). T-056 adds method, allowlisted request headers, a
 * request body (`setHTTPBody`), and a byte range. Refused header names are
 * reported through [onDroppedHeaders] by name only; response headers are
 * filtered through [HttpHeaders.RESPONSE_ALLOWLIST], so a `Set-Cookie` never
 * crosses this boundary. 2xx bodies stream through a channel into the
 * caller's fixed-size buffer; a whole file is never buffered in memory.
 * Cancelling the coroutine cancels the underlying data task.
 */
@OptIn(ExperimentalForeignApi::class)
class IosHttpTransfer(
    private val timeoutSeconds: Double = 15.0,
    private val onDroppedHeaders: (List<String>) -> Unit = {},
) : HttpTransfer {

    private val delegate = IosSessionDelegate()
    private val delegateQueue = NSOperationQueue().apply { maxConcurrentOperationCount = 1 }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val sanitized = request.sanitized()
        if (sanitized.droppedHeaders.isNotEmpty()) onDroppedHeaders(sanitized.droppedHeaders)
        val outgoing = sanitized.request

        val baseUrl = NSURL.URLWithString(outgoing.url)
            ?: return HttpResponse.Failed(HttpFailureReason.NETWORK, "This URL is malformed.")
        val urlRequest = NSMutableURLRequest.requestWithURL(baseUrl).apply {
            setHTTPMethod(outgoing.method)
            for ((name, value) in outgoing.headers) {
                setValue(value, forHTTPHeaderField = name)
            }
            outgoing.body?.let { setHTTPBody(it.toNSData()) }
            setTimeoutInterval(timeoutSeconds)
        }
        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration(),
            delegate,
            delegateQueue,
        )
        val task = session.dataTaskWithRequest(urlRequest)
        val state = delegate.register(task, baseUrl)
        task.resume()
        return state.awaitResponse()
    }

    private class TaskState(val task: NSURLSessionDataTask, val baseUrl: NSURL) {
        val settled = CompletableDeferred<Unit>()
        val chunks = Channel<ByteArray?>(capacity = Channel.UNLIMITED)
        var statusCode: Int? = null
        var contentType: String? = null
        var totalBytes: Long? = null
        var contentRange: String? = null
        var headers: Map<String, String> = emptyMap()
        var redirectLocation: String? = null
        var failure: Throwable? = null
    }

    private suspend fun TaskState.awaitResponse(): HttpResponse {
        // A hard upper bound: NSURLSession can leave a request without any
        // delegate callback, so waiting on [settled] alone can hang forever.
        val settledInTime = withTimeoutOrNull(
            (timeoutSeconds * 1_000).toLong().coerceAtLeast(5_000) + 5_000,
        ) {
            settled.await()
            true
        } ?: false
        if (!settledInTime) {
            task.cancel()
            return HttpResponse.Failed(HttpFailureReason.TIMEOUT, "The request timed out.")
        }
        val redirect = redirectLocation
        if (redirect != null) {
            val resolved = NSURL.URLWithString(redirect, relativeToURL = baseUrl)?.absoluteString ?: redirect
            return HttpResponse.Redirect(resolved, statusCode)
        }
        val status = statusCode ?: 0
        if (status !in 200..299) {
            return HttpResponse.Final(statusCode = status, headers = headers)
        }
        return HttpResponse.Final(
            statusCode = status,
            contentType = contentType,
            totalBytes = totalBytes,
            body = IosHttpBody(this),
            contentRange = contentRange,
            headers = headers,
        )
    }

    private inner class IosSessionDelegate : NSObject(),
        NSURLSessionDelegateProtocol,
        NSURLSessionTaskDelegateProtocol,
        NSURLSessionDataDelegateProtocol {

        private val tasks = mutableMapOf<Long, TaskState>()

        fun register(task: NSURLSessionDataTask, baseUrl: NSURL): TaskState =
            TaskState(task, baseUrl).also { tasks[it.task.taskIdentifier.toLong()] = it }

        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveResponse: NSURLResponse,
            completionHandler: (Long) -> Unit,
        ) {
            val state = tasks[dataTask.taskIdentifier.toLong()]
            if (state == null) {
                completionHandler(NSURLSessionResponseAllow)
                return
            }
            val http = didReceiveResponse as? NSHTTPURLResponse
            state.statusCode = http?.statusCode?.toInt()
            val headerFields = linkedMapOf<String, String>()
            http?.allHeaderFields?.forEach { (key, value) ->
                val name = key as? String ?: return@forEach
                val text = value as? String ?: return@forEach
                headerFields[name] = text
            }
            state.headers = HttpHeaders.filterResponse(headerFields)
            state.contentType = state.headers["content-type"]
            state.contentRange = state.headers["content-range"]
            state.totalBytes = ContentRange.totalBytes(state.contentRange)
                ?: http?.expectedContentLength?.toLong()?.takeIf { it >= 0 }
            val status = state.statusCode ?: 0
            if (status in 300..399) {
                state.redirectLocation = state.headers["location"]
                state.task.cancel()
            }
            completionHandler(NSURLSessionResponseAllow)
            state.settled.complete(Unit)
        }

        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveData: NSData,
        ) {
            val state = tasks[dataTask.taskIdentifier.toLong()] ?: return
            val length = didReceiveData.length.toInt()
            if (length == 0) return
            val bytes = ByteArray(length)
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), didReceiveData.bytes, didReceiveData.length)
            }
            state.chunks.trySend(bytes)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            val state = tasks.remove(task.taskIdentifier.toLong()) ?: return
            // A transport error after the response headers (for example a
            // dropped connection mid-body) must surface on the body read so a
            // truncated file is never published as complete. Redirects and
            // non-2xx responses never expose the body, so the stored failure
            // is simply unused there.
            if (didCompleteWithError != null) {
                state.failure = IllegalStateException("network")
            }
            state.chunks.trySend(null)
        }

        override fun URLSession(session: NSURLSession, didBecomeInvalidWithError: NSError?) = Unit

        // Never follow a redirect automatically; the caller decides per hop.
        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit,
        ) {
            completionHandler(null)
        }
    }

    private inner class IosHttpBody(private val state: TaskState) : HttpBody {
        private var closed = false

        override suspend fun readNext(buffer: ByteArray): Int {
            val chunk = try {
                state.chunks.receive()
            } catch (c: CancellationException) {
                state.task.cancel()
                throw c
            }
            if (chunk == null) {
                state.failure?.let { throw it }
                return -1
            }
            val count = min(chunk.size, buffer.size)
            chunk.copyInto(buffer, 0, 0, count)
            currentCoroutineContext().ensureActive()
            return count
        }

        override suspend fun close() {
            if (closed) return
            closed = true
            state.task.cancel()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    val data = NSMutableData()
    if (isNotEmpty()) {
        usePinned { pinned -> data.appendBytes(pinned.addressOf(0), size.toULong()) }
    }
    return data
}