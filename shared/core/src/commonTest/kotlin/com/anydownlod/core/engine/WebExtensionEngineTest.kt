package com.anydownlod.core.engine

import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebExtensionEngineTest {

    private fun request(url: String = "https://example.com/files/tiny.bin", key: String = "") =
        DownloadRequest(sourceUrl = url, idempotencyKey = key)

    private fun engine(
        bridge: WebExtensionBridge,
        scope: TestScope,
    ) = WebExtensionEngine(
        bridge = bridge,
        scope = scope,
        idGenerator = { "id-${++idCounter}" },
        ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
    )

    private var idCounter = 0

    private class FakeBridge(
        override val available: Boolean,
        var probe: WebProbe = WebProbe.Final(200, "application/octet-stream", 1_024, "https://example.com/files/tiny.bin"),
        var download: WebDownload = WebDownload.Completed("tiny.bin", 1_024),
        val probeCalls: MutableList<String> = mutableListOf(),
        val downloadCalls: MutableList<Pair<String, String>> = mutableListOf(),
        val cancels: MutableList<String> = mutableListOf(),
        var downloadGate: Channel<Unit>? = null,
    ) : WebExtensionBridge {
        override suspend fun probe(url: String): WebProbe {
            probeCalls += url
            return probe
        }

        override suspend fun download(url: String, jobId: String, onProgress: (Long, Long?) -> Unit): WebDownload {
            downloadCalls += url to jobId
            downloadGate?.receive()
            currentCoroutineContext().ensureActive()
            if (download is WebDownload.Completed) {
                onProgress(512L, 1_024L)
            }
            return download
        }

        override suspend fun cancelDownload(jobId: String) {
            cancels += jobId
        }
    }

    @Test
    fun directFileCompletesWithAnArtifact() = runTest {
        val bridge = FakeBridge(available = true)
        val engine = engine(bridge, this)

        val job = engine.submit(request(key = "k1"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state)
        assertEquals(listOf("https://example.com/files/tiny.bin"), bridge.probeCalls)
        assertEquals("tiny.bin", finished.artifacts.single().relativePath)
        assertEquals(1_024L, finished.progress?.downloadedBytes)
        assertEquals(100.0, finished.progress?.percent)
        assertTrue(bridge.downloadCalls.single().first.endsWith("tiny.bin"))
    }

    @Test
    fun missingExtensionFailsHonestlyWithoutAnyBridgeCall() = runTest {
        val bridge = FakeBridge(available = false)
        val engine = engine(bridge, this)

        val job = engine.submit(request(key = "k2"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, finished.error?.code)
        assertFalse(finished.error?.retryable ?: true)
        assertEquals(0, bridge.probeCalls.size, "the page must not even probe when the extension is missing")
        assertEquals(0, bridge.downloadCalls.size)
        assertTrue(finished.artifacts.isEmpty())
    }

    @Test
    fun htmlProbeFailsWithTypedExtractorError() = runTest {
        val bridge = FakeBridge(
            available = true,
            probe = WebProbe.Final(200, "text/html; charset=utf-8", null, "https://example.com/watch?v=x"),
        )
        val engine = engine(bridge, this)

        val job = engine.submit(request(url = "https://example.com/watch?v=x", key = "k3"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.EXTRACTION_FAILURE, finished.error?.code)
        assertTrue(finished.error?.retryable == false)
        assertEquals(0, bridge.downloadCalls.size)
    }

    @Test
    fun blocklistedFinalUrlIsRefusedBeforeDownload() = runTest {
        val bridge = FakeBridge(
            available = true,
            probe = WebProbe.Final(200, "application/octet-stream", null, "http://127.0.0.1/evil.bin"),
        )
        val engine = engine(bridge, this)

        val job = engine.submit(request(url = "https://example.com/start", key = "k4"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        assertEquals(0, bridge.downloadCalls.size)
    }

    @Test
    fun cancellWhileDownloadingAbortsViaBridgeAndBecomesCancelled() = runTest {
        val gate = Channel<Unit>(capacity = Channel.UNLIMITED)
        val bridge = FakeBridge(available = true, downloadGate = gate)
        val engine = engine(bridge, this)

        val job = engine.submit(request(key = "k5"))
        testScheduler.advanceUntilIdle() // engine waits on the download gate

        assertEquals(JobState.DOWNLOADING, engine.jobs.value.first { it.id == job.id }.state)

        val cancelled = engine.cancel(job.id)
        assertNotNull(cancelled)
        assertEquals(JobState.CANCELLED, cancelled.state)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(job.id), bridge.cancels)
        assertEquals(JobState.CANCELLED, engine.jobs.value.first { it.id == job.id }.state)
        assertTrue(engine.jobs.value.first { it.id == job.id }.artifacts.isEmpty())
    }

    @Test
    fun httpErrorStatusFromProbeFailsMapped() = runTest {
        val bridge = FakeBridge(
            available = true,
            probe = WebProbe.Final(404, "text/plain", null, "https://example.com/gone.bin"),
        )
        val engine = engine(bridge, this)

        val job = engine.submit(request(key = "k6"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.UNAVAILABLE_OR_PRIVATE, finished.error?.code)
        assertEquals(0, bridge.downloadCalls.size)
    }

    @Test
    fun extensionPermissionFailureMapsToEngineUnavailable() = runTest {
        val bridge = FakeBridge(
            available = true,
            probe = WebProbe.Failed(WebFailureCode.PERMISSION, "permission"),
        )
        val engine = engine(bridge, this)

        val job = engine.submit(request(key = "k7"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, finished.error?.code)
        assertFalse(finished.error?.retryable ?: true)
    }

    @Test
    fun idempotentDoubleSubmitReturnsOneJobAndOneDownload() = runTest {
        val bridge = FakeBridge(available = true)
        val engine = engine(bridge, this)

        engine.submit(request(key = "dup"))
        testScheduler.advanceUntilIdle()
        engine.submit(request(key = "dup"))
        testScheduler.advanceUntilIdle()

        assertEquals(1, engine.jobs.value.size)
        assertEquals(1, bridge.probeCalls.size)
        assertEquals(1, bridge.downloadCalls.size)
    }

    @Test
    fun nullProgressTotalStaysUnknown() = runTest {
        val bridge = FakeBridge(
            available = true,
            probe = WebProbe.Final(200, "application/octet-stream", null, "https://example.com/files/live.bin"),
            download = WebDownload.Completed("live.bin", null),
        )
        val engine = engine(bridge, this)

        val job = engine.submit(request(key = "k8"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state)
        assertNull(finished.artifacts.single().sizeBytes)
    }
}