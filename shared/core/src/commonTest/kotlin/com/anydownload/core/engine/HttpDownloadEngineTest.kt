package com.anydownload.core.engine

import com.anydownload.core.SettingsRepository
import com.anydownload.core.domain.AppSettings
import com.anydownload.core.domain.ArtifactKind
import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobErrorCode
import com.anydownload.core.domain.JobState
import com.anydownload.core.domain.StartPolicy
import com.anydownload.core.fake.InMemorySettingsRepository
import com.anydownload.core.platform.FileHandle
import com.anydownload.core.platform.FileStore
import com.anydownload.core.platform.HttpBody
import com.anydownload.core.platform.HttpFailureReason
import com.anydownload.core.platform.HttpRequest
import com.anydownload.core.platform.HttpResponse
import com.anydownload.core.platform.HttpTransfer
import kotlinx.coroutines.CompletableDeferred
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
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requested.add(request.url)
            return responder(request.url)
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

    /** Suspends on the gate before the first read only. */
    private class OneShotGatedBody(
        private val chunks: List<ByteArray>,
        private val gate: CompletableDeferred<Unit>,
    ) : HttpBody {
        private var chunkIndex = 0
        private var position = 0
        private var released = false

        override suspend fun readNext(buffer: ByteArray): Int {
            if (!released) {
                gate.await()
                released = true
            }
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

    private open class FakeFileStore : FileStore {
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
            check(!relativePath.startsWith("/") && relativePath.split('/').none { it == ".." }) {
                "The artifact path escapes the download root."
            }
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

    private open class FakeFile : FileHandle {
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

    private class FailingWriteFile : FakeFile() {
        override fun write(data: ByteArray, length: Int) {
            throw IllegalStateException("disk full")
        }
    }

    /** A store whose handle cannot write, so the engine must type the failure. */
    private class FailingWriteFileStore : FileStore {
        val created = mutableListOf<FakeFile>()
        val publishTargets = mutableListOf<String>()

        override fun createTempFile(): FileHandle = FailingWriteFile().also { created += it }

        override fun publish(temp: FileHandle, relativePath: String): String {
            publishTargets += relativePath
            return relativePath
        }

        override fun delete(relativePath: String): Boolean = false

        override fun size(relativePath: String): Long? = null
    }

    /** Runs [onPublish] inside publish, so a test can race cancel with commit. */
    private class CallbackFileStore(private val onPublish: () -> Unit) : FakeFileStore() {
        override fun publish(temp: FileHandle, relativePath: String): String {
            val published = super.publish(temp, relativePath)
            onPublish()
            return published
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
    fun transferFailureFromThePortFailsTypedWithoutTouchingFiles() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Failed(HttpFailureReason.BLOCKED_DESTINATION, "This address was refused.")
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(url = "https://fixtures.example.com/files/a.bin", key = "failed-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        assertFalse(finished.error?.retryable ?: true)
        assertTrue(store.created.isEmpty(), "a refused request must not create a temp file")
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

    @Test
    fun concurrencyLimitOneQueuesTheSecondJobUntilTheFirstFinishes() = runTest {
        val payload = ByteArray(64) { (it % 251).toByte() }
        val gate = CompletableDeferred<Unit>()
        val transfer = FakeTransfer { url ->
            val body: HttpBody = if (url.endsWith("first.bin")) {
                OneShotGatedBody(listOf(payload), gate)
            } else {
                FakeBody(listOf(payload))
            }
            HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                totalBytes = payload.size.toLong(),
                body = body,
            )
        }
        val settings = settings().apply { update { it.copy(maxConcurrentDownloads = 1) } }
        val engine = engine(transfer, settings = settings, scope = this)

        val first = engine.submit(request(url = "https://fixtures.example.com/files/first.bin", key = "limit-1"))
        val second = engine.submit(request(url = "https://fixtures.example.com/files/second.bin", key = "limit-2"))
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.DOWNLOADING, engine.jobs.value.first { it.id == first.id }.state)
        assertEquals(JobState.QUEUED, engine.jobs.value.first { it.id == second.id }.state)

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == first.id }.state)
        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == second.id }.state)
    }

    @Test
    fun concurrencyLimitIsReadWhenAWorkerStarts() = runTest {
        val payload = ByteArray(32) { (it % 251).toByte() }
        val gate = CompletableDeferred<Unit>()
        val transfer = FakeTransfer { url ->
            val body: HttpBody = if (url.endsWith("first.bin")) {
                OneShotGatedBody(listOf(payload), gate)
            } else {
                FakeBody(listOf(payload))
            }
            HttpResponse.Final(statusCode = 200, contentType = "application/octet-stream", body = body)
        }
        val settings = settings().apply { update { it.copy(maxConcurrentDownloads = 1) } }
        val engine = engine(transfer, settings = settings, scope = this)

        val first = engine.submit(request(url = "https://fixtures.example.com/files/first.bin", key = "raise-1"))
        testScheduler.advanceUntilIdle()
        assertEquals(JobState.DOWNLOADING, engine.jobs.value.first { it.id == first.id }.state)

        // The limit is raised before the second job starts; the new worker
        // reads it at start instead of using a value captured at build time.
        settings.update { it.copy(maxConcurrentDownloads = 2) }
        val second = engine.submit(request(url = "https://fixtures.example.com/files/second.bin", key = "raise-2"))
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == second.id }.state)
        assertEquals(JobState.DOWNLOADING, engine.jobs.value.first { it.id == first.id }.state)
        gate.complete(Unit)
    }

    @Test
    fun retryOfCompletedJobDoesNotDownloadAgain() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                body = FakeBody(listOf(ByteArray(24))),
            )
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(key = "retry-completed-key"))
        testScheduler.advanceUntilIdle()
        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
        assertEquals(1, transfer.requested.size)

        val retried = engine.retry(job.id)
        assertNotNull(retried)
        assertEquals(JobState.COMPLETED, retried.state)
        testScheduler.advanceUntilIdle()

        val after = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, after.state)
        assertEquals(1, after.attempts.size)
        assertEquals(1, transfer.requested.size)
        assertEquals(1, store.publishTargets.size)
    }

    @Test
    fun lateCancelAfterCompletionKeepsTheCompletedFile() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                body = FakeBody(listOf(ByteArray(24))),
            )
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(key = "late-cancel-key"))
        testScheduler.advanceUntilIdle()
        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)

        val cancelled = engine.cancel(job.id)
        assertNotNull(cancelled)
        assertEquals(JobState.COMPLETED, cancelled.state)
        assertEquals(1, engine.jobs.value.first { it.id == job.id }.artifacts.size)
        assertTrue(store.live.containsKey("tiny.bin"))
    }

    @Test
    fun aCollisionGetsANumericSuffixAndDoesNotOverwrite() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                body = FakeBody(listOf(ByteArray(16))),
            )
        }
        val store = FakeFileStore()
        store.live["tiny.bin"] = FakeFile()
        store.live["tiny (2).bin"] = FakeFile()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(key = "collision-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state)
        assertEquals("tiny (3).bin", finished.artifacts.single().relativePath)
        assertEquals(listOf("tiny (3).bin"), store.publishTargets)
        assertTrue(store.live.containsKey("tiny.bin"))
        assertTrue(store.live.containsKey("tiny (2).bin"))
    }

    @Test
    fun aTraversalRequestedPathFailsTypedWithoutWriting() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                body = FakeBody(listOf(ByteArray(8))),
            )
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(
            DownloadRequest(
                sourceUrl = "https://fixtures.example.com/files/tiny.bin",
                relativePath = "../escape.mp4",
            ),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        assertTrue(finished.artifacts.isEmpty())
        assertTrue(store.publishTargets.isEmpty())
    }

    @Test
    fun aHostWriteFailureIsDiskExhaustedAndDiscardsTheTemp() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                body = FakeBody(listOf(ByteArray(64))),
            )
        }
        val store = FailingWriteFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(key = "disk-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.DISK_EXHAUSTED, finished.error?.code)
        assertFalse(finished.error?.message.orEmpty().contains("/"))
        assertTrue(store.created.single().discarded)
        assertTrue(store.publishTargets.isEmpty())
    }

    @Test
    fun removeHistoryDropsTheRowButKeepsTheFile() = runTest {
        val transfer = FakeTransfer {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                body = FakeBody(listOf(ByteArray(24))),
            )
        }
        val store = FakeFileStore()
        val engine = engine(transfer, store, scope = this)

        val job = engine.submit(request(key = "remove-keeps-file"))
        testScheduler.advanceUntilIdle()
        val path = engine.jobs.value.first { it.id == job.id }.artifacts.single().relativePath
        assertTrue(store.live.containsKey(path))

        assertTrue(engine.removeHistory(job.id))

        assertTrue(store.live.containsKey(path), "removing history must not delete the file")
        assertTrue(engine.jobs.value.isEmpty())
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
