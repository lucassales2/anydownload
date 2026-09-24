package com.anydownlod.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import platform.Foundation.NSError
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
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
import platform.Foundation.setHTTPMethod
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.math.min

/**
 * In-process iOS [HttpTransfer] built on NSURLSession.
 *
 * One hop per [execute]: the data-task delegate never follows redirects, so a
 * 3xx is surfaced as [HttpResponse.Redirect] with an absolute resolved
 * `Location` and the shared engine re-validates every destination (per-hop
 * policy holds on iOS too). 2xx bodies stream through a channel into the
 * engine's fixed-size buffer; a whole file is never buffered in memory.
 * Cancelling the coroutine cancels the underlying data task.
 */
@OptIn(ExperimentalForeignApi::class)
class IosHttpTransfer(
    private val timeoutSeconds: Double = 15.0,
) : HttpTransfer {

    private val delegate = IosSessionDelegate()
    private val delegateQueue = NSOperationQueue().apply { maxConcurrentOperationCount = 1 }

    override suspend fun execute(url: String): HttpResponse {
        val baseUrl = NSURL.URLWithString(url) ?: return HttpResponse.Final(statusCode = 0)
        val request = NSMutableURLRequest.requestWithURL(baseUrl).apply {
            setHTTPMethod("GET")
            setTimeoutInterval(timeoutSeconds)
        }
        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration(),
            delegate,
            delegateQueue,
        )
        val task = session.dataTaskWithRequest(request)
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
        var redirectLocation: String? = null
        var failure: Throwable? = null
    }

    private suspend fun TaskState.awaitResponse(): HttpResponse {
        settled.await()
        val redirect = redirectLocation
        if (redirect != null) {
            val resolved = NSURL.URLWithString(redirect, relativeToURL = baseUrl)?.absoluteString ?: redirect
            return HttpResponse.Redirect(resolved)
        }
        val status = statusCode ?: 0
        if (status !in 200..299) {
            return HttpResponse.Final(statusCode = status)
        }
        return HttpResponse.Final(
            statusCode = status,
            contentType = contentType,
            totalBytes = totalBytes,
            body = IosHttpBody(this),
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
            val headers = http?.allHeaderFields
            state.contentType = (headers?.get("Content-Type") as? String)
                ?: (headers?.get("Content-type") as? String)
            state.totalBytes = http?.expectedContentLength?.toLong()?.takeIf { it >= 0 }
            val status = state.statusCode ?: 0
            if (status in 300..399) {
                state.redirectLocation = (headers?.get("Location") as? String)
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
            if (didCompleteWithError != null) {
                state.failure = IllegalStateException("network")
            }
            state.chunks.trySend(null)
        }

        override fun URLSession(session: NSURLSession, didBecomeInvalidWithError: NSError?) = Unit

        // Never follow a redirect automatically; the engine decides per hop.
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