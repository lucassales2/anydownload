package com.anydownlod.core.platform

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlPolicy
import com.anydownlod.core.fake.InMemorySettingsRepository
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory

/**
 * Native (iosSimulatorArm64) tests for the iOS adapter stack: the shared
 * engine with the real sandbox [IosFileStore]. The offline tests use an
 * in-memory transfer (default CI stays fixture-only); the live test uses the
 * real [IosHttpTransfer] against a LOCAL fixture server and is skipped unless
 * `IOS_LIVE_FIXTURE_PORT` names one.
 */
class IosEngineTest {

    private fun tempRoot(label: String): String {
        val base = NSTemporaryDirectory().trimEnd('/')
        return "$base/anydownlod-ios-$label-${Random.nextLong().toULong().toString(16)}"
    }

    private fun engine(
        transfer: HttpTransfer,
        fileStore: FileStore,
        root: String,
        urlCheck: (String) -> UrlCheck = UrlPolicy::check,
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = fileStore,
        settings = InMemorySettingsRepository(AppSettings(downloadRoot = root)),
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
        urlCheck = urlCheck,
    )

    @Test
    fun directFileStreamsThroughTheRealIosFileStoreAndCompletes() = runBlocking {
        val root = tempRoot("direct")
        val payload = ByteArray(8 * 1024) { (it % 251).toByte() }
        val transfer = FakeTransfer {
            HttpResponse.Final(200, "application/octet-stream", payload.size.toLong(), FakeBody(listOf(payload)))
        }
        val fileStore = IosFileStore(root)
        val http = engine(transfer, fileStore, root)

        val job = http.submit(DownloadRequest(
            sourceUrl = "https://fixtures.example.com/files/tiny.bin",
            options = DownloadOptions(),
            idempotencyKey = "ios-direct",
        ))
        val finished = waitFor(http, job.id, JobState.COMPLETED)

        assertEquals(JobState.COMPLETED, finished.state)
        assertEquals(payload.size.toLong(), finished.progress?.downloadedBytes)
        val artifact = finished.artifacts.single()
        assertEquals("tiny.bin", artifact.relativePath)
        assertEquals(payload.size.toLong(), fileStore.size("tiny.bin"))
    }

    @Test
    fun cancelMidStreamLeavesNoCompletedFile() = runBlocking {
        val root = tempRoot("cancel")
        val fileStore = IosFileStore(root)
        val gate = channelCapacity()
        val transfer = FakeTransfer {
            HttpResponse.Final(200, "application/octet-stream", null, GatedBody(listOf(ByteArray(1024)), gate))
        }
        val http = engine(transfer, fileStore, root)

        val job = http.submit(DownloadRequest(
            sourceUrl = "https://fixtures.example.com/files/stall.bin",
            options = DownloadOptions(),
            idempotencyKey = "ios-cancel",
        ))
        withTimeout(15_000) {
            http.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.DOWNLOADING } }
        }

        val cancelled = http.cancel(job.id)
        assertNotNull(cancelled)
        withTimeout(15_000) {
            http.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.CANCELLED } }
        }

        assertEquals(JobState.CANCELLED, cancelled.state)
        assertEquals(null, fileStore.size("stall.bin"), "a cancelled download must leave no published file")
        assertTrue(http.jobs.value.first { it.id == job.id }.artifacts.isEmpty())
    }

    @Test
    fun htmlContentFailsWithTypedExtractorError() = runBlocking {
        val root = tempRoot("html")
        val transfer = FakeTransfer {
            HttpResponse.Final(200, "text/html; charset=utf-8", 1_024, FakeBody(listOf(ByteArray(16))))
        }
        val http = engine(transfer, IosFileStore(root), root)

        val job = http.submit(DownloadRequest(
            sourceUrl = "https://example.com/watch?v=fixture",
            idempotencyKey = "ios-html",
        ))
        val finished = waitFor(http, job.id, JobState.FAILED)

        assertEquals(JobErrorCode.EXTRACTION_FAILURE, finished.error?.code)
        assertTrue(finished.artifacts.isEmpty())
    }

    @Test
    fun fileStoreWritesPublishesAndRejectsTraversal() {
        val root = tempRoot("store")
        val store = IosFileStore(root)
        val handle = store.createTempFile()
        handle.write(ByteArray(4096) { 5 }, 4096)
        handle.close()

        val relative = store.publish(handle, "folder/file.bin")
        assertEquals("folder/file.bin", relative)
        assertEquals(4096L, store.size(relative))
        assertTrue(store.delete(relative))
        assertEquals(null, store.size(relative))

        assertFailsWith<IllegalArgumentException> {
            val bad = store.createTempFile()
            try {
                store.publish(bad, "../escape.bin")
            } finally {
                bad.discard()
            }
        }
        assertFailsWith<IllegalArgumentException> { store.publish(store.createTempFile(), "/absolute.bin") }
    }

    @Test
    fun realNSURLSessionDownloadsALocalFixture() = runBlocking {
        // The simulator strips custom environment variables, so the port also
        // comes from a host file the simulator can read (written by the test
        // runner; see shared/core/build.gradle.kts and the T-042 evidence).
        val endpoint = NSProcessInfo.processInfo.environment["IOS_LIVE_FIXTURE"]
            ?.toString()
            ?: readHostText("/tmp/anydownlod-ios-live.txt")?.trim()
        if (endpoint.isNullOrBlank()) {
            println("Skipped: set IOS_LIVE_FIXTURE or write /tmp/anydownlod-ios-live.txt (host:port of a local fixture server) to run the live transfer check.")
            return@runBlocking
        }
        val root = tempRoot("live")
        val fileStore = IosFileStore(root)
        val base = "http://$endpoint"
        val transfer = IosHttpTransfer(timeoutSeconds = 15.0)
        val fixtureCheck: (String) -> UrlCheck = { url ->
            if (url.startsWith(base)) UrlCheck.Allowed(url) else UrlPolicy.check(url)
        }
        val http = engine(transfer, fileStore, root, urlCheck = fixtureCheck)

        val job = http.submit(DownloadRequest(
            sourceUrl = "$base/files/tiny.bin",
            idempotencyKey = "ios-live",
        ))
        val finished = withTimeout(45_000) {
            http.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.COMPLETED } }
                .first { it.id == job.id }
        }
        assertEquals(JobState.COMPLETED, finished.state)
        val artifact = finished.artifacts.single()
        assertEquals("tiny.bin", artifact.relativePath)
        assertNotNull(fileStore.size(artifact.relativePath))
    }

    @Test
    fun realNSURLSessionDownloadsAMatchingHtmlPageAndFailsUnresolvedTyped() = runBlocking {
        // Same opt-in as the direct-file live fixture: IOS_LIVE_FIXTURE env or
        // /tmp/anydownlod-ios-live.txt names the host:port of a local server
        // that serves /watch (one <video src="/media/clip.bin">), the media
        // file, and /page-without-media (no media element).
        val endpoint = NSProcessInfo.processInfo.environment["IOS_LIVE_FIXTURE"]
            ?.toString()
            ?: readHostText("/tmp/anydownlod-ios-live.txt")?.trim()
        if (endpoint.isNullOrBlank()) {
            println("Skipped: set IOS_LIVE_FIXTURE or write /tmp/anydownlod-ios-live.txt (host:port of a local fixture server) to run the live HTML route check.")
            return@runBlocking
        }
        val root = tempRoot("live-html")
        val fileStore = IosFileStore(root)
        val base = "http://$endpoint"
        val transfer = IosHttpTransfer(timeoutSeconds = 15.0)
        val fixtureCheck: (String) -> UrlCheck = { url ->
            if (url.startsWith(base)) UrlCheck.Allowed(url) else UrlPolicy.check(url)
        }
        val http = engine(transfer, fileStore, root, urlCheck = fixtureCheck)

        // 1) A matching HTML page downloads the fixture media into the sandbox.
        val job = http.submit(DownloadRequest(
            sourceUrl = "$base/watch",
            options = DownloadOptions(),
            idempotencyKey = "ios-live-html",
        ))
        val finished = withTimeout(45_000) {
            http.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.COMPLETED } }
                .first { it.id == job.id }
        }
        assertEquals(JobState.COMPLETED, finished.state)
        val artifact = finished.artifacts.single()
        assertEquals("clip.bin", artifact.relativePath)
        assertEquals(4_096L, fileStore.size(artifact.relativePath))

        // 2) Unresolved HTML fails typed, with no Python and no CLI.
        val unresolved = http.submit(DownloadRequest(
            sourceUrl = "$base/page-without-media",
            options = DownloadOptions(),
            idempotencyKey = "ios-live-unresolved",
        ))
        val failed = withTimeout(45_000) {
            http.jobs.first { jobs -> jobs.any { it.id == unresolved.id && it.state == JobState.FAILED } }
                .first { it.id == unresolved.id }
        }
        assertEquals(JobErrorCode.EXTRACTION_FAILURE, failed.error?.code)
        assertEquals(false, failed.error?.retryable)
        assertTrue(failed.artifacts.isEmpty())
    }

    private suspend fun waitFor(engine: HttpDownloadEngine, jobId: String, state: JobState): DownloadJob =
        withTimeout(20_000) {
            engine.jobs.first { jobs -> jobs.any { it.id == jobId && it.state == state } }
                .first { it.id == jobId }
        }
}

private fun channelCapacity() = Channel<Unit>(capacity = Channel.UNLIMITED)

/** Sandbox-free read of a small host file (simulator processes see /tmp). */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun readHostText(path: String): String? {
    val stream = platform.posix.fopen(path, "r") ?: return null
    try {
        val buffer = ByteArray(64)
        val output = StringBuilder()
        while (true) {
            val read = buffer.usePinned { pinned ->
                platform.posix.fread(pinned.addressOf(0), 1uL, buffer.size.toULong(), stream)
            }
            if (read == 0uL) break
            for (i in 0 until read.toInt()) output.append(buffer[i].toInt().toChar())
        }
        return output.toString()
    } finally {
        platform.posix.fclose(stream)
    }
}

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

private class FakeBody(private val chunks: List<ByteArray>) : HttpBody {
    private var index = 0
    override suspend fun readNext(buffer: ByteArray): Int {
        currentCoroutineContext().ensureActive()
        if (index >= chunks.size) return -1
        val chunk = chunks[index++]
        val count = minOf(chunk.size, buffer.size)
        chunk.copyInto(buffer, 0, 0, count)
        return count
    }

    override suspend fun close() = Unit
}

private class GatedBody(
    private val chunks: List<ByteArray>,
    private val gate: Channel<Unit>,
) : HttpBody {
    private var index = 0
    override suspend fun readNext(buffer: ByteArray): Int {
        gate.receive()
        currentCoroutineContext().ensureActive()
        if (index >= chunks.size) return -1
        val chunk = chunks[index++]
        val count = minOf(chunk.size, buffer.size)
        chunk.copyInto(buffer, 0, 0, count)
        return count
    }

    override suspend fun close() = Unit
}