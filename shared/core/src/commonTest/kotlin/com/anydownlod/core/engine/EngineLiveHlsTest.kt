package com.anydownlod.core.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.FileHandle
import com.anydownlod.core.platform.FileStore
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-124 live HLS: a sliding-window media playlist is followed until
 * `#EXT-X-ENDLIST`, appending each fragment once; cancellation discards the
 * temp. Fixtures only.
 */
class EngineLiveHlsTest {

    private val playlistUrl = "https://media.example/live.m3u8"

    private class MemoryFileHandle : FileHandle {
        private val chunks = mutableListOf<ByteArray>()
        var discarded = false
        val bytes: ByteArray get() = chunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
        override fun write(bytes: ByteArray, length: Int) {
            chunks += bytes.copyOf(length)
        }

        override fun close() = Unit
        override fun discard() {
            discarded = true
        }
    }

    private class MemoryFileStore : FileStore {
        val created = mutableListOf<MemoryFileHandle>()
        val published = mutableMapOf<String, ByteArray>()
        override fun createTempFile(): FileHandle = MemoryFileHandle().also { created += it }
        override fun publish(temp: FileHandle, relativePath: String): String {
            published[relativePath] = (temp as MemoryFileHandle).bytes
            return relativePath
        }

        override fun delete(relativePath: String): Boolean = published.remove(relativePath) != null
        override fun size(relativePath: String): Long? = published[relativePath]?.size?.toLong()
    }

    private class LiveTransfer(
        private val playlists: List<String>,
        private val segments: Map<String, String>,
    ) : HttpTransfer {
        var playlistFetches = 0
        val segmentRequests = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            if (request.url.endsWith(".m3u8")) {
                val body = playlists[minOf(playlistFetches, playlists.lastIndex)]
                playlistFetches++
                val bytes = body.encodeToByteArray()
                return HttpResponse.Final(
                    statusCode = 200,
                    contentType = "application/vnd.apple.mpegurl",
                    totalBytes = bytes.size.toLong(),
                    body = ByteArrayHttpBody(bytes),
                )
            }
            segmentRequests += request.url
            val body = segments[request.url] ?: return HttpResponse.Final(statusCode = 404)
            val bytes = body.encodeToByteArray()
            return HttpResponse.Final(
                statusCode = 200,
                contentType = "video/mp2t",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

    private fun engine(scope: TestScope, transfer: HttpTransfer, store: FileStore): HttpDownloadEngine =
        HttpDownloadEngine(
            transfer = transfer,
            fileStore = store,
            settings = InMemorySettingsRepository(AppSettings(downloadRoot = "/tmp/anydownlod-live")),
            scope = scope,
            idGenerator = { "live-${++idCounter}" },
            ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        )

    private var idCounter = 0

    private fun request(key: String) = DownloadRequest(
        sourceUrl = playlistUrl,
        options = DownloadOptions(),
        idempotencyKey = key,
    )

    private fun playlist(sequence: Long, vararg segments: String): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:1")
        appendLine("#EXT-X-MEDIA-SEQUENCE:$sequence")
        for (segment in segments) {
            appendLine("#EXTINF:1.0,")
            appendLine(segment)
        }
    }

    @Test
    fun livePlaylistIsFollowedUntilEndListAndEachFragmentIsWrittenOnce() = runTest {
        val transfer = LiveTransfer(
            playlists = listOf(
                playlist(0, "a.ts", "b.ts"),
                playlist(2, "c.ts", "d.ts"),
                playlist(4, "e.ts") + "#EXT-X-ENDLIST\n",
            ),
            segments = mapOf(
                "https://media.example/a.ts" to "AAA",
                "https://media.example/b.ts" to "BBB",
                "https://media.example/c.ts" to "CCC",
                "https://media.example/d.ts" to "DDD",
                "https://media.example/e.ts" to "EEE",
            ),
        )
        val store = MemoryFileStore()
        val engine = engine(this, transfer, store)

        val job = engine.submit(request("live-until-end"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        val artifact = finished.artifacts.single()
        assertContentEquals("AAABBBCCCDDDEEE".encodeToByteArray(), store.published[artifact.relativePath])
        assertEquals(5, transfer.segmentRequests.size, "each segment is fetched once")
        assertEquals(3, transfer.playlistFetches, "initial page plus two polls")
        assertTrue(finished.progress?.downloadedBytes == 15L)
    }

    @Test
    fun cancellingALiveStreamDiscardsTheTemp() = runTest {
        // The same live window repeats, so the loop waits after the first batch.
        val repeated = playlist(0, "a.ts", "b.ts")
        val transfer = LiveTransfer(
            playlists = listOf(repeated, repeated),
            segments = mapOf(
                "https://media.example/a.ts" to "AAA",
                "https://media.example/b.ts" to "BBB",
            ),
        )
        val store = MemoryFileStore()
        val engine = engine(this, transfer, store)

        val job = engine.submit(request("live-cancel"))
        // Run the first batch, but stop before the no-new-fragment delay.
        testScheduler.advanceTimeBy(500)

        engine.cancel(job.id)
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.CANCELLED, finished.state, finished.error?.message)
        assertTrue(store.created.isNotEmpty())
        assertTrue(store.created.all { it.discarded })
        assertTrue(store.published.isEmpty())
    }
}
