package com.anydownlod.core.engine

import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.ArtifactKind
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.platform.FileHandle
import com.anydownlod.core.platform.FileStore
import com.anydownlod.core.platform.HttpBody
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpDownloadEngineTest {

    private fun request(
        url: String = "https://fixtures.example.com/files/tiny.bin",
        policy: StartPolicy = StartPolicy.AUTOMATIC,
        key: String = "",
        options: DownloadOptions = DownloadOptions(),
    ) = DownloadRequest(sourceUrl = url, options = options.copy(startPolicy = policy), idempotencyKey = key)

    private fun settings(root: String = "/tmp/anydownlod-test-root") =
        InMemorySettingsRepository(AppSettings(downloadRoot = root))

    private fun engine(
        transfer: HttpTransfer,
        fileStore: FileStore = FakeFileStore(),
        settings: SettingsRepository = settings(),
        scope: TestScope,
    ) = HttpDownloadEngine(
        transfer = transfer,
        fileStore = fileStore,
        settings = settings,
        scope = scope,
        idGenerator = { "id-${++idCounter}" },
        ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
    )

    private var idCounter = 0

    // ------------------------------------------------------------------ fakes

    private class FakeTransfer(
        val requested: MutableList<String> = mutableListOf(),
        private val responder: (url: String) -> HttpResponse,
    ) : HttpTransfer {
        override suspend fun execute(url: String): HttpResponse {
            requested.add(url)
            return responder(url)
        }
    }

    /** Slices a chunk across reads, exactly like a real HTTP body. */
    private class FakeBody(private val chunks: List<ByteArray>) : HttpBody {
        private var chunkIndex = 0
        private var position = 0

        override suspend fun readNext(buffer: ByteArray): Int {
            currentCoroutineContext().ensureActive()
            while (chunkIndex < chunks.size && position >= chunks[chunkIndex].size) {
                chunkIndex++
                position = 0
            }
            if (chunkIndex >= chunks.size) return -1
            val chunk = chunks[chunkIndex]
            val count = minOf(chunk.size - position, buffer.size)
            chunk.copyInto(buffer, 0, position, position + count)
            position += count
            return count
        }

        override suspend fun close() = Unit
    }

    /** Suspends on the gate before every read, so tests can cancel mid-stream. */
    private class GatedBody(
        private val chunks: List<ByteArray>,
        private val gate: Channel<Unit>,
    ) : HttpBody {
        private var chunkIndex = 0
        private var position = 0

        override suspend fun readNext(buffer: ByteArray): Int {
            gate.receive()
            currentCoroutineContext().ensureActive()
            while (chunkIndex < chunks.size && position >= chunks[chunkIndex].size) {
                chunkIndex++
                position = 0
            }
            if (chunkIndex >= chunks.size) return -1
            val chunk = chunks[chunkIndex]
            val count = minOf(chunk.size - position, buffer.size)
            chunk.copyInto(buffer, 0, position, position + count)
            position += count
            return count
        }

        override suspend fun close() = Unit
    }

    private class FakeFileStore : FileStore {
        val created = mutableListOf<FakeFile>()
        val publishTargets = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val live = mutableMapOf<String, FakeFile>()

        override fun createTempFile(): FileHandle {
            val file = FakeFile()
            created += file
            return file
        }

        override fun publish(temp: FileHandle, relativePath: String): String {
            val file = temp as FakeFile
            check(!file.published) { "A temp file may only be published once." }
            file.published = true
            publishTargets += relativePath
            live[relativePath] = file
            return relativePath
        }

        override fun delete(relativePath: String): Boolean {
            deleted += relativePath
            return live.remove(relativePath) != null
        }

        override fun size(relativePath: String): Long? = live[relativePath]?.bytes?.size?.toLong()
    }

    private class FakeFile : FileHandle {
        val bytes = GrowingBytes()
        var closed = false
        var published = false
        var discarded = false

        override fun write(data: ByteArray, length: Int) {
            bytes.append(data, length)
        }

        override fun close() {
            closed = true
        }

        override fun discard() {
            discarded = true
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun directFileStreamsToTempThenPublishesAndCompletes() = runTest {
        val payload = ByteArray(300_000) { (it % 251).toByte() }
        val url = "https://fixtures.example.com/files/tiny.bin"
        val transfer = FakeTransfer {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                totalBytes = payload.size.toLong(),
                body = FakeBody(listOf(payload)),
            )
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(url = url, key = "success-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state)
        assertNull(finished.error)
        assertEquals(payload.size.toLong(), finished.progress?.downloadedBytes)
        assertEquals(payload.size.toLong(), finished.progress?.totalBytes)
        assertEquals(100.0, finished.progress?.percent)

        val artifact = finished.artifacts.single()
        assertEquals("tiny.bin", artifact.relativePath)
        assertEquals("tiny.bin", artifact.fileName)
        assertEquals(ArtifactKind.VIDEO, artifact.kind)
        assertEquals(payload.size.toLong(), artifact.sizeBytes)

        assertEquals(listOf(url), transfer.requested)
        assertEquals(listOf("tiny.bin"), store.publishTargets)
        val written = store.live["tiny.bin"]
        assertNotNull(written)
        assertTrue(written.bytes.toByteArray().contentEquals(payload))
        assertTrue(written.published)
        assertFalse(written.discarded)
    }

    @Test
    fun cancelMidStreamDiscardsTempAndCancelsTheJob() = runTest {
        val gate = Channel<Unit>(capacity = Channel.UNLIMITED)
        val transfer = FakeTransfer {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                totalBytes = null,
                body = GatedBody(listOf(ByteArray(64), ByteArray(64)), gate),
            )
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(key = "cancel-key"))
        testScheduler.advanceUntilIdle() // engine waits on the gate inside readNext

        assertEquals(JobState.DOWNLOADING, engine.jobs.value.first { it.id == job.id }.state)

        val cancelled = engine.cancel(job.id)
        assertNotNull(cancelled)
        assertEquals(JobState.CANCELLED, cancelled.state)

        testScheduler.advanceUntilIdle()

        val now = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.CANCELLED, now.state)
        assertEquals(JobErrorCode.CANCELLED, now.error?.code)
        assertNull(now.artifacts.firstOrNull())
        assertTrue(store.created.isNotEmpty())
        assertTrue(store.created.all { it.discarded })
        assertTrue(store.publishTargets.isEmpty())
    }

    @Test
    fun httpErrorFailsWithMappedErrorCode() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(statusCode = 404, contentType = "text/plain")
        }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(key = "err-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.UNAVAILABLE_OR_PRIVATE, finished.error?.code)
        assertTrue(finished.artifacts.isEmpty())
    }

    @Test
    fun rateLimitedStatusMapsToRateLimited() = runTest {
        val transfer = FakeTransfer { HttpResponse.Final(statusCode = 429) }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(key = "rate-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.RATE_LIMITED, finished.error?.code)
        assertTrue(finished.error?.retryable == true)
    }

    @Test
    fun redirectToBlockedAddressFailsWithoutFetchingIt() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Redirect("http://127.0.0.1/admin")
        }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(key = "redir-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        // The blocked destination was validated before a second request went out.
        assertEquals(1, transfer.requested.size)
    }

    @Test
    fun redirectToBlockedAddressIsRejectedByPolicyStandalone() = runTest {
        val check = UrlPolicy.check("http://127.0.0.1/admin")
        assertTrue(check is UrlCheck.Rejected)
        assertEquals(UrlRejectReason.BlockedDestination, (check as UrlCheck.Rejected).reason)
    }

    @Test
    fun htmlContentTypeIsNeedsExtractorAndFailsTyped() = runTest {
        var bodyClosed = false
        val body = object : HttpBody {
            override suspend fun readNext(buffer: ByteArray): Int = -1
            override suspend fun close() {
                bodyClosed = true
            }
        }
        val transfer = FakeTransfer {
            HttpResponse.Final(statusCode = 200, contentType = "text/html; charset=utf-8", body = body)
        }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(key = "html-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.EXTRACTION_FAILURE, finished.error?.code)
        assertTrue(finished.error?.retryable == false)
        assertTrue(bodyClosed)
        assertTrue(finished.artifacts.isEmpty())
    }

    @Test
    fun unavailableTransferFailsAsEngineUnavailable() = runTest {
        // The web host without the extension (and the T-039 iOS placeholder)
        // return HttpResponse.Unavailable: no request is sent and the job
        // fails with a typed, non-retryable engine error.
        val transfer = FakeTransfer { HttpResponse.Unavailable("browser extension required") }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(key = "unavail-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, finished.error?.code)
        assertTrue(finished.error?.retryable == false)
        assertTrue(finished.artifacts.isEmpty())
    }

    @Test
    fun idempotentDoubleSubmitReturnsTheOriginalJobOnce() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(statusCode = 200, contentType = "application/octet-stream", body = FakeBody(listOf(ByteArray(16))))
        }
        val engine = engine(transfer, scope = this)

        val first = engine.submit(request(key = "same-key"))
        testScheduler.advanceUntilIdle()
        val second = engine.submit(request(key = "same-key"))

        assertEquals(first.id, second.id)
        assertEquals(1, engine.jobs.value.size)
        assertEquals(1, transfer.requested.size)
    }

    @Test
    fun manualStartStaysPendingUntilStart() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(statusCode = 200, contentType = "application/octet-stream", body = FakeBody(listOf(ByteArray(16))))
        }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(policy = StartPolicy.MANUAL, key = "manual-key"))
        assertEquals(JobState.PENDING, job.state)
        assertEquals(0, transfer.requested.size)

        testScheduler.advanceUntilIdle()
        assertEquals(JobState.PENDING, engine.jobs.value.first { it.id == job.id }.state)

        val started = engine.start(job.id)
        assertNotNull(started)
        testScheduler.advanceUntilIdle()
        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
    }

    @Test
    fun invalidInitialUrlFailsWithoutCallingTheNetwork() = runTest {
        val transfer = FakeTransfer { HttpResponse.Final(statusCode = 200) }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(url = "https://user:pass@fixtures.example.com/x", key = "leak-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        assertEquals(0, transfer.requested.size)
    }

    @Test
    fun loopbackDestinationIsRejected() = runTest {
        val transfer = FakeTransfer { HttpResponse.Final(statusCode = 200) }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(url = "http://127.0.0.1/evil", key = "loop-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        assertEquals(0, transfer.requested.size)
    }

    @Test
    fun followsAllowedRedirectsAndDownloadsTheFinalHost() = runTest {
        val finalBody = ByteArray(128) { 7 }
        val transfer = FakeTransfer { url ->
            if (url == "https://fixtures.example.com/redirector") {
                HttpResponse.Redirect("https://cdn.fixtures.example.net/asset.bin")
            } else {
                HttpResponse.Final(200, "application/octet-stream", finalBody.size.toLong(), FakeBody(listOf(finalBody)))
            }
        }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(url = "https://fixtures.example.com/redirector", key = "follow-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state)
        assertEquals(listOf("https://fixtures.example.com/redirector", "https://cdn.fixtures.example.net/asset.bin"), transfer.requested)
        assertEquals("asset.bin", finished.artifacts.single().relativePath)
    }

    @Test
    fun redirectLoopExceedingBudgetFailsAsNetworkFailure() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Redirect("https://fixtures.example.com/loop")
        }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(url = "https://fixtures.example.com/start", key = "loop-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.NETWORK_FAILURE, finished.error?.code)
        assertEquals(UrlPolicy.MAX_REDIRECTS + 1, transfer.requested.size)
    }

    @Test
    fun retryAppendsAttemptAndCanSucceed() = runTest {
        var calls = 0
        val transfer = FakeTransfer { _ ->
            calls++
            if (calls == 1) {
                HttpResponse.Final(statusCode = 503)
            } else {
                HttpResponse.Final(200, "application/octet-stream", body = FakeBody(listOf(ByteArray(48))))
            }
        }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(key = "retry-key"))
        testScheduler.advanceUntilIdle()
        assertEquals(JobState.FAILED, engine.jobs.value.first { it.id == job.id }.state)
        assertEquals(1, engine.jobs.value.first { it.id == job.id }.attempts.size)

        val retried = engine.retry(job.id)
        assertNotNull(retried)
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state)
        assertEquals(2, finished.attempts.size)
        assertEquals(JobState.FAILED, finished.attempts.first().state)
        assertEquals(JobState.COMPLETED, finished.attempts.last().state)
    }

    @Test
    fun removeHistoryDropsTheRowAndLetsTheKeyBeReused() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(statusCode = 200, contentType = "application/octet-stream", body = FakeBody(listOf(ByteArray(8))))
        }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(key = "history-key"))
        testScheduler.advanceUntilIdle()
        assertEquals(1, engine.jobs.value.size)
        assertTrue(engine.removeHistory(job.id))
        assertEquals(0, engine.jobs.value.size)
        assertFalse(engine.removeHistory(job.id))

        // The idempotency key was freed, so a new submit makes a fresh job.
        val again = engine.submit(request(key = "history-key"))
        assertNotEquals(job.id, again.id)
        assertEquals(1, engine.jobs.value.size)
    }

    @Test
    fun deleteArtifactsRemovesThePublishedFile() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(statusCode = 200, contentType = "application/octet-stream", body = FakeBody(listOf(ByteArray(32))))
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(key = "del-key"))
        testScheduler.advanceUntilIdle()
        assertEquals(1, store.live.size)

        val result = engine.deleteArtifacts(job.id)

        assertEquals(1, result.deletedCount)
        assertEquals(listOf("tiny.bin"), store.deleted)
        val finished = engine.jobs.value.first { it.id == job.id }
        assertTrue(finished.artifacts.single().removed)
        assertEquals(0, store.live.size)
    }

    @Test
    fun progressPercentIsNullWhenContentLengthIsUnknown() = runTest {
        val gate = Channel<Unit>(capacity = Channel.UNLIMITED)
        val chunk = ByteArray(1_000) { 5 }
        val transfer = FakeTransfer {
            HttpResponse.Final(200, "application/octet-stream", totalBytes = null, body = GatedBody(listOf(chunk), gate))
        }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(key = "unknown-total-key"))
        testScheduler.advanceUntilIdle() // parked on the gate
        gate.send(Unit) // first chunk lands
        testScheduler.advanceUntilIdle()

        val running = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.DOWNLOADING, running.state)
        assertEquals(chunk.size.toLong(), running.progress?.downloadedBytes)
        assertNull(running.progress?.percent)
        assertNull(running.progress?.speedBytesPerSecond)
        assertNull(running.progress?.etaSeconds)

        gate.send(Unit) // EOF after chunks are consumed
        testScheduler.advanceUntilIdle()
        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
    }

    @Test
    fun progressPercentFollowsContentLength() = runTest {
        val gate = Channel<Unit>(capacity = Channel.UNLIMITED)
        val chunk = ByteArray(500) { 9 }
        val transfer = FakeTransfer {
            HttpResponse.Final(200, "application/octet-stream", totalBytes = 1_000, body = GatedBody(listOf(chunk, chunk), gate))
        }
        val engine = engine(transfer, scope = this)

        val job = engine.submit(request(key = "known-total-key"))
        testScheduler.advanceUntilIdle()
        gate.send(Unit)
        testScheduler.advanceUntilIdle()

        val running = engine.jobs.value.first { it.id == job.id }
        assertEquals(500L, running.progress?.downloadedBytes)
        assertEquals(50.0, running.progress?.percent)

        gate.send(Unit) // second chunk
        testScheduler.advanceUntilIdle()
        gate.send(Unit) // EOF
        testScheduler.advanceUntilIdle()
        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
    }

    @Test
    fun matchingHtmlFixtureDownloadsTheMediaUrl() = runTest {
        val pageUrl = "https://fixtures.example.com/watch"
        val mediaUrl = "https://cdn.fixtures.example.net/media/clip.bin"
        val payload = ByteArray(4_096) { 3 }
        val pageHtml = """
            <html><head><title>Fixture</title></head><body>
            <video controls><source src="$mediaUrl" type="video/mp4"></video>
            </body></html>
        """.trimIndent()
        val transfer = FakeTransfer { url ->
            if (url == pageUrl) {
                HttpResponse.Final(
                    statusCode = 200,
                    contentType = "text/html; charset=utf-8",
                    totalBytes = pageHtml.encodeToByteArray().size.toLong(),
                    body = FakeBody(listOf(pageHtml.encodeToByteArray())),
                )
            } else {
                HttpResponse.Final(
                    statusCode = 200,
                    contentType = "application/octet-stream",
                    totalBytes = payload.size.toLong(),
                    body = FakeBody(listOf(payload)),
                )
            }
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(url = pageUrl, key = "html-media-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state)
        assertNull(finished.error)
        assertEquals(listOf(pageUrl, mediaUrl), transfer.requested)
        assertEquals("clip.bin", finished.artifacts.single().relativePath)
        val written = store.live["clip.bin"]
        assertNotNull(written)
        assertTrue(written.bytes.toByteArray().contentEquals(payload))
        assertTrue(written.published)
    }

    @Test
    fun zeroMatchHtmlFailsTypedWithoutSaving() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "text/html; charset=utf-8",
                body = FakeBody(listOf("<html><body>nothing here</body></html>".encodeToByteArray())),
            )
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(key = "zero-media-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.EXTRACTION_FAILURE, finished.error?.code)
        assertEquals(false, finished.error?.retryable)
        assertTrue(finished.artifacts.isEmpty())
        assertTrue(store.created.isEmpty())
        assertTrue(store.publishTargets.isEmpty())
    }

    @Test
    fun twoMatchHtmlFailsTypedWithoutSaving() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "text/html; charset=utf-8",
                body = FakeBody(
                    listOf("""<video src="one.mp4"></video><video src="two.mp4"></video>""".encodeToByteArray()),
                ),
            )
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(key = "two-media-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.EXTRACTION_FAILURE, finished.error?.code)
        assertTrue(finished.artifacts.isEmpty())
        assertTrue(store.publishTargets.isEmpty())
    }

    @Test
    fun mediaUrlRedirectingToBlockedAddressFailsWithoutSaving() = runTest {
        val pageUrl = "https://fixtures.example.com/watch"
        val transfer = FakeTransfer { url ->
            if (url == pageUrl) {
                HttpResponse.Final(
                    statusCode = 200,
                    contentType = "text/html",
                    body = FakeBody(listOf("""<video src="https://fixtures.example.com/go"></video>""".encodeToByteArray())),
                )
            } else {
                HttpResponse.Redirect("http://127.0.0.1/admin")
            }
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(url = pageUrl, key = "blocked-media-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        assertEquals(listOf(pageUrl, "https://fixtures.example.com/go"), transfer.requested)
        assertTrue(finished.artifacts.isEmpty())
        assertTrue(store.publishTargets.isEmpty())
    }

    @Test
    fun mediaHopReturningHtmlAgainFailsTypedWithoutRecursing() = runTest {
        val pageUrl = "https://fixtures.example.com/watch"
        val transfer = FakeTransfer { url ->
            if (url == pageUrl) {
                HttpResponse.Final(
                    statusCode = 200,
                    contentType = "text/html",
                    body = FakeBody(listOf("""<video src="https://fixtures.example.com/again"></video>""".encodeToByteArray())),
                )
            } else {
                HttpResponse.Final(
                    statusCode = 200,
                    contentType = "text/html; charset=utf-8",
                    body = FakeBody(listOf("<html>still a page</html>".encodeToByteArray())),
                )
            }
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(url = pageUrl, key = "again-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.EXTRACTION_FAILURE, finished.error?.code)
        assertEquals(false, finished.error?.retryable)
        // Only the page and the one media hop were fetched: no recursion.
        assertEquals(2, transfer.requested.size)
        assertTrue(finished.artifacts.isEmpty())
        assertTrue(store.publishTargets.isEmpty())
    }

    @Test
    fun cancelDuringMediaStreamAfterExtractionLeavesNoFile() = runTest {
        val gate = Channel<Unit>(capacity = Channel.UNLIMITED)
        val pageUrl = "https://fixtures.example.com/watch"
        val transfer = FakeTransfer { url ->
            if (url == pageUrl) {
                HttpResponse.Final(
                    statusCode = 200,
                    contentType = "text/html",
                    body = FakeBody(listOf("""<video src="https://fixtures.example.com/clip.bin"></video>""".encodeToByteArray())),
                )
            } else {
                HttpResponse.Final(
                    statusCode = 200,
                    contentType = "application/octet-stream",
                    totalBytes = null,
                    body = GatedBody(listOf(ByteArray(64), ByteArray(64)), gate),
                )
            }
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(url = pageUrl, key = "html-cancel-key"))
        testScheduler.advanceUntilIdle() // parked on the media gate inside readNext
        assertEquals(JobState.DOWNLOADING, engine.jobs.value.first { it.id == job.id }.state)

        engine.cancel(job.id)
        testScheduler.advanceUntilIdle()

        val now = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.CANCELLED, now.state)
        assertEquals(JobErrorCode.CANCELLED, now.error?.code)
        assertNull(now.artifacts.firstOrNull())
        assertTrue(store.created.isNotEmpty())
        assertTrue(store.created.all { it.discarded })
        assertTrue(store.publishTargets.isEmpty())
    }

    @Test
    fun oversizedPageIsBoundedAndFailsCleanly() = runTest {
        val pageUrl = "https://fixtures.example.com/big"
        val big = ByteArray(HttpDownloadEngine.MAX_HTML_BYTES * 2)
        // The only media element sits beyond the read cap.
        val tail = """<video src="late.mp4"></video>""".encodeToByteArray()
        tail.copyInto(big, big.size - tail.size)
        val transfer = FakeTransfer {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "text/html; charset=utf-8",
                body = FakeBody(listOf(big)),
            )
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(url = pageUrl, key = "big-page-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.EXTRACTION_FAILURE, finished.error?.code)
        // The capped read never saw the late media element, so nothing was fetched.
        assertEquals(1, transfer.requested.size)
        assertTrue(store.publishTargets.isEmpty())
    }
}

/** Common-safe growable byte buffer (ByteArrayOutputStream is JVM-only). */
private class GrowingBytes {
    private var data = ByteArray(8)
    var size: Int = 0
        private set

    fun append(bytes: ByteArray, length: Int) {
        if (size + length > data.size) {
            var newSize = data.size
            while (newSize < size + length) newSize *= 2
            data = data.copyOf(newSize)
        }
        bytes.copyInto(data, size, 0, length)
        size += length
    }

    fun toByteArray(): ByteArray = data.copyOf(size)
}