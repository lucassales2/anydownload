package com.anydownlod.core.engine

import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.platform.HttpRequest
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
        registry: com.anydownlod.core.extract.ExtractorRegistry? = null,
    ) = WebExtensionEngine(
        bridge = bridge,
        scope = scope,
        idGenerator = { "id-${++idCounter}" },
        ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        registry = registry,
    )

    private var idCounter = 0

    private class FakeBridge(
        override val available: Boolean,
        var probe: WebProbe = WebProbe.Final(200, "application/octet-stream", 1_024, "https://example.com/files/tiny.bin"),
        var download: WebDownload = WebDownload.Completed("tiny.bin", 1_024),
        var page: WebPage = WebPage.Final("https://example.com/watch?v=x", "no media here"),
        val probeCalls: MutableList<String> = mutableListOf(),
        val pageCalls: MutableList<String> = mutableListOf(),
        val fetchCalls: MutableList<HttpRequest> = mutableListOf(),
        val downloadCalls: MutableList<Pair<String, String>> = mutableListOf(),
        val downloadHeaders: MutableList<Map<String, String>> = mutableListOf(),
        val downloadSaveViaBlob: MutableList<Boolean> = mutableListOf(),
        val cancels: MutableList<String> = mutableListOf(),
        var downloadGate: Channel<Unit>? = null,
        var fetchReply: WebFetch = WebFetch.Failed(WebFailureCode.OTHER, "not used"),
    ) : WebExtensionBridge {
        override suspend fun probe(url: String): WebProbe {
            probeCalls += url
            return probe
        }

        override suspend fun fetchPage(url: String): WebPage {
            pageCalls += url
            return page
        }

        override suspend fun fetch(request: HttpRequest): WebFetch {
            fetchCalls += request
            return fetchReply
        }

        override suspend fun download(
            url: String,
            jobId: String,
            headers: Map<String, String>,
            saveViaBlob: Boolean,
            onProgress: (Long, Long?) -> Unit,
        ): WebDownload {
            downloadCalls += url to jobId
            downloadHeaders += headers
            downloadSaveViaBlob += saveViaBlob
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
        assertEquals(listOf("https://example.com/watch?v=x"), bridge.pageCalls)
        assertEquals(0, bridge.downloadCalls.size)
        assertTrue(finished.artifacts.isEmpty())
    }

    @Test
    fun matchingHtmlPageSavesTheMediaUrl() = runTest {
        val mediaUrl = "https://cdn.fixtures.example.net/media/clip.bin"
        val bridge = FakeBridge(
            available = true,
            probe = WebProbe.Final(200, "text/html; charset=utf-8", null, "https://example.com/watch?v=x"),
            page = WebPage.Final(
                finalUrl = "https://example.com/watch?v=x",
                html = "<html><body><video src=\"$mediaUrl\"></video></body></html>",
            ),
            download = WebDownload.Completed("clip.bin", 4_096),
        )
        val engine = engine(bridge, this)

        val job = engine.submit(request(url = "https://example.com/watch?v=x", key = "k4"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state)
        assertNull(finished.error)
        // The extension fetched the page; the media URL went to the save step.
        assertEquals(listOf("https://example.com/watch?v=x"), bridge.pageCalls)
        assertTrue(bridge.downloadCalls.single().first == mediaUrl)
        assertEquals("clip.bin", finished.artifacts.single().relativePath)
    }

    @Test
    fun twoMatchHtmlPageFailsTypedAndSavesNothing() = runTest {
        val bridge = FakeBridge(
            available = true,
            probe = WebProbe.Final(200, "text/html; charset=utf-8", null, "https://example.com/watch?v=x"),
            page = WebPage.Final(
                finalUrl = "https://example.com/watch?v=x",
                html = "<video src='one.mp4'></video><audio src='two.ogg'></audio>",
            ),
        )
        val engine = engine(bridge, this)

        val job = engine.submit(request(url = "https://example.com/watch?v=x", key = "k5"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.EXTRACTION_FAILURE, finished.error?.code)
        assertEquals(0, bridge.downloadCalls.size)
        assertTrue(finished.artifacts.isEmpty())
    }

    @Test
    fun pageFetchFailureMapsTyped() = runTest {
        val bridge = FakeBridge(
            available = true,
            probe = WebProbe.Final(200, "text/html; charset=utf-8", null, "https://example.com/watch?v=x"),
            page = WebPage.Failed(WebFailureCode.NETWORK, "The page could not be reached."),
        )
        val engine = engine(bridge, this)

        val job = engine.submit(request(url = "https://example.com/watch?v=x", key = "k6"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.NETWORK_FAILURE, finished.error?.code)
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

    @Test
    fun aRegistryMatchedUrlExtractsThroughTheBridgeAndSavesTheFormat() = runTest {
        val bridge = FakeBridge(available = true)
        val extractor = object : com.anydownlod.core.extract.InfoExtractor(
            ieKey = com.anydownlod.core.extract.ExtractorRegistry.GENERIC_KEY,
            http = com.anydownlod.core.extract.ExtractorHttp(UnusedTransfer),
            validUrl = Regex("""https?://youtube\.example/watch.*"""),
        ) {
            override suspend fun extract(url: String): com.anydownlod.core.extract.InfoDict =
                com.anydownlod.core.extract.InfoDict(
                    id = "fixture",
                    title = "Fixture Clip",
                    thumbnails = listOf(
                        com.anydownlod.core.extract.Thumbnail(url = "https://i.example/large.jpg", width = 1280, height = 720),
                    ),
                    formats = listOf(
                        com.anydownlod.core.extract.MediaFormat(
                            formatId = "18",
                            url = "https://cdn.fixtures.example.net/clip.mp4",
                            ext = "mp4",
                            vcodec = "avc1",
                            acodec = "mp4a",
                            httpHeaders = mapOf("referer" to "https://youtube.example/", "cookie" to "secret"),
                        ),
                    ),
                )
        }
        val registry = com.anydownlod.core.extract.ExtractorRegistry(listOf(extractor))
        val engine = engine(bridge, this, registry)

        val job = engine.submit(request(url = "https://youtube.example/watch?v=fixture", key = "web-kotlin"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals("Fixture Clip", finished.title)
        assertEquals("https://i.example/large.jpg", finished.thumbnailUrl)
        assertTrue(bridge.probeCalls.isEmpty(), "a matched URL must not probe")
        assertEquals("https://cdn.fixtures.example.net/clip.mp4", bridge.downloadCalls.single().first)
        // Only allowlisted headers ride along; the refused cookie is dropped.
        assertEquals(mapOf("referer" to "https://youtube.example/"), bridge.downloadHeaders.single())
        assertEquals(listOf(true), bridge.downloadSaveViaBlob, "matched formats use the offscreen saver")
    }

    @Test
    fun aMatchedUrlThatFailsExtractionFailsTypedWithoutProbing() = runTest {
        val bridge = FakeBridge(available = true)
        val extractor = object : com.anydownlod.core.extract.InfoExtractor(
            ieKey = com.anydownlod.core.extract.ExtractorRegistry.GENERIC_KEY,
            http = com.anydownlod.core.extract.ExtractorHttp(UnusedTransfer),
            validUrl = Regex("""https?://youtube\.example/watch.*"""),
        ) {
            override suspend fun extract(url: String): com.anydownlod.core.extract.InfoDict =
                throw com.anydownlod.core.extract.ExtractionError.LoginRequired()
        }
        val registry = com.anydownlod.core.extract.ExtractorRegistry(listOf(extractor))
        val engine = engine(bridge, this, registry)

        val job = engine.submit(request(url = "https://youtube.example/watch?v=fixture", key = "web-login"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.LOGIN_REQUIRED, finished.error?.code)
        assertTrue(bridge.probeCalls.isEmpty())
        assertTrue(bridge.downloadCalls.isEmpty())
    }

    private object UnusedTransfer : com.anydownlod.core.platform.HttpTransfer {
        override suspend fun execute(request: com.anydownlod.core.platform.HttpRequest): com.anydownlod.core.platform.HttpResponse =
            error("unused")
    }
}