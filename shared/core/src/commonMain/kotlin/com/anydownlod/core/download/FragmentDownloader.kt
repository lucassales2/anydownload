/*
 * Fragment downloader — AnyDownload (T-073)
 *
 * Translation of the non-FFmpeg paths of `yt_dlp/downloader/fragment.py`
 * and `yt_dlp/downloader/hls.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-24; the T-135
 * bounded-concurrency and skip-unavailable slice was re-read 2026-09-29.
 * Unlicense; see shared/core/NOTICE.md. Fragments are fetched through the
 * request port with at most a small window in flight, consumed in playlist
 * order, AES-128-CBC fragments are decrypted in Kotlin, and retries are
 * bounded; there is no merge, mux, or FFmpeg step.
 */
package com.anydownlod.core.download

import com.anydownlod.core.cookies.withActiveCookie
import com.anydownlod.core.extract.MediaFragment
import com.anydownlod.core.platform.HttpMethods
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

sealed interface FragmentOutcome {
    /** [skipped] lists the 0-based media-fragment indexes that were unavailable. */
    data class Completed(val skipped: List<Int> = emptyList()) : FragmentOutcome
    data object Cancelled : FragmentOutcome
    data class Failed(val reason: String) : FragmentOutcome
}

class FragmentDownloader(
    private val transfer: HttpTransfer,
    private val maxRetries: Int = 2,
    /**
     * At most this many fragment requests are in flight. `1` restores the
     * strictly sequential request order; results are consumed in fragment
     * order regardless.
     */
    private val concurrency: Int = DEFAULT_CONCURRENCY,
    /**
     * Upstream `skip_unavailable_fragments` (`fragment.py` defaults it to
     * true for VOD). Engine callers pass the upstream default explicitly.
     */
    private val skipUnavailableFragments: Boolean = true,
    /** T-017: waits this long before each fragment hop; 0 = off. */
    private val requestDelayMillis: Long = 0,
) {

    companion object {
        /** A small in-flight window for the shared downloader (T-135). */
        const val DEFAULT_CONCURRENCY = 4
    }

    /**
     * Fetches the init segment first, then the media fragments with at most
     * [concurrency] requests in flight. Results are consumed in playlist
     * order, so `onChunk` sees the same byte stream the sequential reader
     * produced. When [skipUnavailableFragments] is on, a fragment whose
     * retries are exhausted is recorded in [FragmentOutcome.Completed.skipped]
     * and the download continues; when it is off the outcome is
     * [FragmentOutcome.Failed]. Cancellation stops new requests and returns
     * [FragmentOutcome.Cancelled]; the caller still discards its temp.
     */
    suspend fun download(
        fragments: List<MediaFragment>,
        initSegment: MediaFragment? = null,
        key: Aes128KeyInfo? = null,
        onChunk: suspend (ByteArray) -> Unit,
        onProgress: suspend (completed: Int, total: Int, bytes: Long) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): FragmentOutcome {
        val total = fragments.size + if (initSegment != null) 1 else 0
        var completed = 0
        var bytes = 0L
        val keyBytes = if (key != null) {
            when (val result = fetchKey(key.uri)) {
                is FetchResult.Bytes -> result.data
                is FetchResult.Failed -> return FragmentOutcome.Failed(result.reason)
            }
        } else {
            null
        }
        if (initSegment != null) {
            if (isCancelled()) return FragmentOutcome.Cancelled
            when (val result = fetchFragment(initSegment)) {
                is FetchResult.Bytes -> {
                    onChunk(result.data)
                    bytes += result.data.size
                    completed++
                    onProgress(completed, total, bytes)
                }

                is FetchResult.Failed -> return FragmentOutcome.Failed(result.reason)
            }
        }
        if (fragments.isEmpty()) return FragmentOutcome.Completed()
        if (isCancelled()) return FragmentOutcome.Cancelled

        val windowSize = concurrency.coerceAtLeast(1)
        val skipped = mutableListOf<Int>()
        val early: FragmentOutcome? = try {
            coroutineScope {
                val window = ArrayDeque<Pair<Int, Deferred<FetchResult>>>()
                var nextToLaunch = 0
                while (nextToLaunch < fragments.size && window.size < windowSize) {
                    val index = nextToLaunch
                    window += index to async { fetchFragment(fragments[index]) }
                    nextToLaunch++
                }
                while (window.isNotEmpty()) {
                    if (isCancelled()) throw Stop(FragmentOutcome.Cancelled)
                    val (index, deferred) = window.removeFirst()
                    when (val result = deferred.await()) {
                        is FetchResult.Bytes -> {
                            val fragment = fragments[index]
                            val data = if (keyBytes != null) {
                                val iv = key?.iv ?: sequenceIv(fragment.sequence ?: (completed.toLong() - 1))
                                try {
                                    Aes128Cbc.decrypt(keyBytes, iv, result.data)
                                } catch (error: IllegalArgumentException) {
                                    throw Stop(FragmentOutcome.Failed("The encrypted fragment is not block-aligned."))
                                }
                            } else {
                                result.data
                            }
                            onChunk(data)
                            bytes += data.size
                        }

                        is FetchResult.Failed -> {
                            if (!skipUnavailableFragments) {
                                throw Stop(FragmentOutcome.Failed(result.reason))
                            }
                            skipped += index
                        }
                    }
                    completed++
                    onProgress(completed, total, bytes)
                    if (isCancelled()) throw Stop(FragmentOutcome.Cancelled)
                    if (nextToLaunch < fragments.size) {
                        val next = nextToLaunch
                        window += next to async { fetchFragment(fragments[next]) }
                        nextToLaunch++
                    }
                }
            }
            null
        } catch (stop: Stop) {
            stop.outcome
        }
        return early ?: FragmentOutcome.Completed(skipped = skipped)
    }

    private suspend fun fetchKey(uri: String): FetchResult {
        return when (val result = fetch(uri, null)) {
            is FetchResult.Bytes -> if (result.data.size == 16) {
                result
            } else {
                FetchResult.Failed("The HLS AES-128 key is not 16 bytes.")
            }

            is FetchResult.Failed -> result
        }
    }

    private suspend fun fetchFragment(fragment: MediaFragment): FetchResult {
        var last: FetchResult = FetchResult.Failed("The fragment could not be fetched.")
        for (attempt in 0..maxRetries) {
            val result = fetch(fragment.url, fragment.rangeStart?.let { start -> start..(fragment.rangeEnd ?: start) })
            if (result is FetchResult.Bytes) return result
            last = result
        }
        return last
    }

    private suspend fun fetch(url: String, range: LongRange?): FetchResult {
        var current = url
        for (hop in 0..5) {
            if (requestDelayMillis > 0) delay(requestDelayMillis)
            val request = HttpRequest(current, method = HttpMethods.GET, range = range).withActiveCookie()
            when (val response = transfer.execute(request)) {
                is HttpResponse.Redirect -> current = response.location
                is HttpResponse.Final -> {
                    if (response.statusCode !in 200..299) {
                        return FetchResult.Failed("The server answered ${response.statusCode} for a fragment.")
                    }
                    val body = response.body ?: return FetchResult.Failed("The fragment response had no body.")
                    return FetchResult.Bytes(readAll(body))
                }

                is HttpResponse.Failed -> return FetchResult.Failed("The fragment request failed.")
                is HttpResponse.Unavailable -> return FetchResult.Failed("The fragment transport is unavailable.")
            }
        }
        return FetchResult.Failed("The fragment request redirected too many times.")
    }

    private suspend fun readAll(body: com.anydownlod.core.platform.HttpBody): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        var total = 0
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = body.readNext(buffer)
            if (read < 0) break
            if (read > 0) {
                chunks += buffer.copyOf(read)
                total += read
            }
        }
        body.close()
        val result = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    private fun sequenceIv(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var value = sequence
        for (index in 15 downTo 0) {
            iv[index] = (value and 0xff).toByte()
            value = value shr 8
        }
        return iv
    }

    private sealed interface FetchResult {
        data class Bytes(val data: ByteArray) : FetchResult
        data class Failed(val reason: String) : FetchResult
    }

    /** Internal early exit that unwinds the window without failing the caller. */
    private class Stop(val outcome: FragmentOutcome) : Exception()
}
