package com.anydownlod.core.engine

import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.extract.DownloaderOptions
import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.InfoMedia
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.Thumbnail
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.format.CompiledSpec
import com.anydownlod.core.format.FormatSelector
import com.anydownlod.core.format.OptionsToSpec
import com.anydownlod.core.format.Selection
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.FileHandle
import com.anydownlod.core.platform.FileStore
import com.anydownlod.core.platform.HttpBody
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The registry route through [HttpDownloadEngine]: extract, select one format,
 * download it. Fixtures only; the fake transfer never touches a network.
 */
class EngineExtractionTest {

    private val formatUrl = "https://cdn.fixtures.example.net/clip.mp4"
    private val payload = ByteArray(4096) { (it % 251).toByte() }

    private fun engine(
        scope: TestScope,
        extractor: InfoExtractor,
        transfer: HttpTransfer,
        store: FileStore = FakeFileStore(),
        settings: SettingsRepository = InMemorySettingsRepository(
            AppSettings(downloadRoot = "/tmp/anydownlod-engine-extraction"),
        ),
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = store,
        settings = settings,
        scope = scope,
        idGenerator = { "id-${++idCounter}" },
        ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        registry = ExtractorRegistry(listOf(extractor)),
    )

    private var idCounter = 0

    private fun request(
        url: String = "https://youtube.example/watch?v=fixture",
        options: DownloadOptions = DownloadOptions(startPolicy = StartPolicy.AUTOMATIC),
        key: String = "",
        selectedMediaIds: List<String> = emptyList(),
    ) = DownloadRequest(
        sourceUrl = url,
        options = options,
        idempotencyKey = key,
        selectedMediaIds = selectedMediaIds,
    )

    @Test
    fun matchedUrlExtractsSelectsAndDownloadsOneFormat() = runTest {
        val info = InfoDict(
            id = "fixture",
            title = "Fixture Clip",
            thumbnails = listOf(
                Thumbnail(url = "https://i.example/small.jpg", width = 120, height = 90),
                Thumbnail(url = "https://i.example/large.jpg", width = 1280, height = 720),
            ),
            formats = listOf(
                MediaFormat(
                    formatId = "18",
                    url = formatUrl,
                    ext = "mp4",
                    vcodec = "avc1",
                    acodec = "mp4a",
                    height = 360,
                    filesize = payload.size.toLong(),
                ),
            ),
            formatsNeedingJs = 2,
        )
        val transfer = ScriptedTransfer(payload)
        val store = FakeFileStore()
        val engine = engine(this, FixedExtractor(info), transfer, store)

        val job = engine.submit(request(key = "extract-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals("Fixture Clip", finished.title)
        assertEquals("https://i.example/large.jpg", finished.thumbnailUrl)
        assertEquals("youtube.example", finished.sourceHost)
        assertEquals(2, finished.formatsNeedingJs)

        // The artifact name comes from the extracted title and the selected
        // format's container, not the URL tail.
        val artifact = finished.artifacts.single()
        assertEquals("Fixture Clip.mp4", artifact.relativePath)
        assertEquals(payload.size.toLong(), artifact.sizeBytes)
        assertTrue(store.live.getValue("Fixture Clip.mp4").bytes.toByteArray().contentEquals(payload))
        assertEquals(listOf(formatUrl), transfer.requests.map { it.url })
    }

    @Test
    fun extractionErrorsMapToTypedJobErrors() = runTest {
        val cases = listOf(
            ExtractionError.LoginRequired() to JobErrorCode.LOGIN_REQUIRED,
            ExtractionError.AgeRestricted() to JobErrorCode.UNAVAILABLE_OR_PRIVATE,
            ExtractionError.GeoRestricted(listOf("BR")) to JobErrorCode.UNAVAILABLE_OR_PRIVATE,
            ExtractionError.Unavailable("This video is private.") to JobErrorCode.UNAVAILABLE_OR_PRIVATE,
            ExtractionError.NoFormats() to JobErrorCode.UNSUPPORTED_FORMAT,
            ExtractionError.Malformed("bad json") to JobErrorCode.EXTRACTION_FAILURE,
            ExtractionError.UnsupportedUrl() to JobErrorCode.UNSUPPORTED_SOURCE,
        )
        for ((error, expected) in cases) {
            val engine = engine(this, ThrowingExtractor(error), ScriptedTransfer(payload))
            val job = engine.submit(request(key = "error-${expected.wireName}-${error::class.simpleName}"))
            testScheduler.advanceUntilIdle()
            val finished = engine.jobs.value.first { it.id == job.id }
            assertEquals(JobState.FAILED, finished.state)
            assertEquals(expected, finished.error?.code, "for ${error::class.simpleName}")
            assertFalse(finished.error?.retryable ?: true)
            assertTrue(finished.artifacts.isEmpty())
        }
    }

    @Test
    fun mp3FailsTypedWhenTheHostCannotWriteIt() = runTest {
        val engine = engine(this, FixedExtractor(singleFormatInfo()), ScriptedTransfer(payload))
        val job = engine.submit(
            request(
                options = DownloadOptions(
                    startPolicy = StartPolicy.AUTOMATIC,
                    mediaType = MediaType.AUDIO,
                    audioContainer = AudioContainer.MP3,
                ),
                key = "mp3-key",
            ),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.UNSUPPORTED_FORMAT, finished.error?.code)
        assertTrue(finished.error?.message?.contains("cannot write MP3") == true, finished.error?.message)
        assertTrue(finished.error?.message?.contains("M4A") == true, finished.error?.message)
    }

    @Test
    fun mergeAndEmptySelectionsAreTypedAndHonest() {
        val video = MediaFormat(formatId = "v", url = formatUrl, vcodec = "avc1", acodec = "none")
        val audio = MediaFormat(formatId = "a", url = formatUrl, vcodec = "none", acodec = "opus")

        val merged = assertIs<FormatResolution.Unsupported>(
            resolveSelection(Selection.Merge(video, audio), formatsNeedingJs = 0),
        )
        assertTrue(merged.message.contains("cannot merge"), merged.message)
        assertTrue(merged.message.contains("M4A"), merged.message)

        val mergeReady = assertIs<FormatResolution.Merge>(
            resolveSelection(Selection.Merge(video, audio), formatsNeedingJs = 0, canMerge = true),
        )
        assertEquals("v", mergeReady.video.formatId)
        assertEquals("a", mergeReady.audio.formatId)

        val none = assertIs<FormatResolution.Unsupported>(resolveSelection(Selection.None, formatsNeedingJs = 3))
        assertTrue(none.message.contains("3 more formats need the JavaScript runtime"), none.message)

        val noneWithoutJs = assertIs<FormatResolution.Unsupported>(resolveSelection(Selection.None, formatsNeedingJs = 0))
        assertFalse(noneWithoutJs.message.contains("JavaScript"), noneWithoutJs.message)
    }

    @Test
    fun compiledSpecMergesOnlyWhenTheHostCan() {
        for (profile in com.anydownlod.core.domain.VideoContainerProfile.entries) {
            for (codec in com.anydownlod.core.domain.VideoCodec.entries) {
                for (quality in listOf(
                    com.anydownlod.core.domain.QualityPreference.Best,
                    com.anydownlod.core.domain.QualityPreference.Worst,
                    com.anydownlod.core.domain.QualityPreference.Resolution("720"),
                )) {
                    val options = DownloadOptions(videoProfile = profile, videoCodec = codec, quality = quality)
                    val single = assertIs<CompiledSpec.SingleFile>(OptionsToSpec.compile(options))
                    assertFalse(single.specText.contains("+"), "compiled spec must never merge: ${single.specText}")
                    assertFalse(single.sort.any { it.contains("+") })

                    val mergeCapable = assertIs<CompiledSpec.SingleFile>(OptionsToSpec.compile(options, canMerge = true))
                    assertEquals(
                        1,
                        mergeCapable.specText.count { it == '+' },
                        "a merge-capable host must compile exactly one merge: ${mergeCapable.specText}",
                    )
                    assertTrue(mergeCapable.specText.contains("/"), mergeCapable.specText)
                }
            }
        }
        val selections = OptionsToSpec.compile(
            DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.M4A),
            canMerge = true,
        )
        assertFalse(assertIs<CompiledSpec.SingleFile>(selections).specText.contains("+"))
    }

    @Test
    fun aFormatWithoutAUrlFailsTypedInsteadOfDownloading() = runTest {
        val engine = engine(
            this,
            FixedExtractor(
                singleFormatInfo().copy(
                    formats = listOf(MediaFormat(formatId = "x", url = null, vcodec = "avc1", acodec = "mp4a")),
                ),
            ),
            ScriptedTransfer(payload),
        )
        val job = engine.submit(request(key = "no-url"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.UNSUPPORTED_FORMAT, finished.error?.code)
    }

    @Test
    fun unmatchedUrlsKeepTheProbePathAndNeverExtract() = runTest {
        val transfer = ScriptedTransfer(payload)
        val store = FakeFileStore()
        val engine = engine(this, UnmatchedExtractor(), transfer, store)

        val job = engine.submit(request(url = "https://files.example.net/plain.bin", key = "probe-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state)
        // The probe path used the URL tail (no extracted metadata was involved).
        assertEquals("plain.bin", finished.artifacts.single().relativePath)
        assertEquals(null, finished.title)
        assertEquals(listOf("https://files.example.net/plain.bin"), transfer.requests.map { it.url })
    }

    @Test
    fun formatResolutionForACompilerSpecSelectsASingleFile() {
        val resolved = resolveFormat(singleFormatInfo(), DownloadOptions())
        val ready = assertIs<FormatResolution.Ready>(resolved)
        assertEquals("18", ready.format.formatId)
        val selection = FormatSelector.select(singleFormatInfo(), OptionsToSpec.compile(DownloadOptions()).let {
            assertIs<CompiledSpec.SingleFile>(it).spec
        })
        assertIs<Selection.Single>(selection)
        assertNotNull(ready.format.url)
    }

    // ------------------------------------------------------- media selection

    private val statusUrl = "https://x.example/fixture/status/9999999999999999999"

    private fun media(id: String, url: String, title: String) = InfoMedia(
        mediaId = id,
        title = title,
        duration = 5.0,
        formats = listOf(
            MediaFormat(formatId = "http-256", url = url, ext = "mp4", height = 360),
        ),
    )

    @Test
    fun twoSelectedOfThreeVideosDownloadTwoFilesFromAFreshExtraction() = runTest {
        val urlA = "https://cdn.fixtures.example.net/a.mp4"
        val urlB = "https://cdn.fixtures.example.net/b.mp4"
        val urlC = "https://cdn.fixtures.example.net/c.mp4"
        val extractor = FixedExtractor(
            InfoDict(
                id = "9999999999999999999",
                title = "Fixture status",
                media = listOf(
                    media("a", urlA, "First video"),
                    media("b", urlB, "Second video"),
                    media("c", urlC, "Third video"),
                ),
            ),
            validUrl = Regex("""https?://x\.example/.+"""),
        )
        val transfer = ScriptedTransfer(payload)
        val store = FakeFileStore()
        val engine = engine(this, extractor, transfer, store)

        val job = engine.submit(
            request(url = statusUrl, key = "media-key", selectedMediaIds = listOf("c", "a")),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(statusUrl, finished.request.sourceUrl)
        assertEquals(1, extractor.calls, "the engine re-extracts at download time")

        // One file per selected video, in extraction order; the unselected
        // video's URL is never fetched.
        assertEquals(listOf(urlA, urlC), transfer.requests.map { it.url })
        assertEquals(listOf("First video.mp4", "Third video.mp4"), finished.artifacts.map { it.relativePath })
        assertTrue(finished.artifacts.all { it.jobId == job.id })
        assertTrue(store.live.getValue("First video.mp4").bytes.toByteArray().contentEquals(payload))
        assertTrue(store.live.getValue("Third video.mp4").bytes.toByteArray().contentEquals(payload))
    }

    @Test
    fun anEmptySelectionFailsTypedAndNeverFetchesMedia() = runTest {
        val transfer = ScriptedTransfer(payload)
        val engine = engine(
            this,
            FixedExtractor(
                InfoDict(media = listOf(media("a", "https://cdn.fixtures.example.net/a.mp4", "First"))),
                validUrl = Regex("""https?://x\.example/.+"""),
            ),
            transfer,
        )
        val job = engine.submit(request(url = statusUrl, key = "empty-media-key"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        assertTrue(finished.error?.message?.contains("Select at least one video") == true, finished.error?.message)
        assertFalse(finished.error?.retryable ?: true)
        assertTrue(finished.artifacts.isEmpty())
        assertTrue(transfer.requests.isEmpty(), "an empty selection must not fetch media")
    }

    @Test
    fun aSelectedIdGoneFromTheFreshExtractionFailsBeforeAnyMediaGet() = runTest {
        val transfer = ScriptedTransfer(payload)
        val engine = engine(
            this,
            FixedExtractor(
                InfoDict(media = listOf(media("a", "https://cdn.fixtures.example.net/a.mp4", "First"))),
                validUrl = Regex("""https?://x\.example/.+"""),
            ),
            transfer,
        )
        val job = engine.submit(
            request(url = statusUrl, key = "stale-media-key", selectedMediaIds = listOf("a", "gone")),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.UNAVAILABLE_OR_PRIVATE, finished.error?.code)
        assertTrue(finished.artifacts.isEmpty())
        assertTrue(transfer.requests.isEmpty(), "a stale selection must not half-download")
    }

    @Test
    fun cancellingBetweenSelectedVideosDiscardsTheCurrentTemp() = runTest {
        val info = InfoDict(
            media = listOf(
                media("a", "https://cdn.fixtures.example.net/a.mp4", "First"),
                media("b", "https://cdn.fixtures.example.net/b.mp4", "Second"),
            ),
        )
        val gate = CompletableDeferred<Unit>()
        val transfer = GatedSecondBodyTransfer(payload, gate)
        val store = FakeFileStore()
        val engine = engine(this, FixedExtractor(info, Regex("""https?://x\.example/.+""")), transfer, store)

        val job = engine.submit(
            request(url = statusUrl, key = "cancel-media-key", selectedMediaIds = listOf("a", "b")),
        )
        testScheduler.advanceUntilIdle()
        engine.cancel(job.id)
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.CANCELLED, finished.state)
        assertEquals(1, finished.artifacts.size, "the published file stays; the current temp is discarded")
        assertTrue(store.created.last().discarded, "the cancelled temp must be discarded")
        assertTrue(store.live.values.none { it.discarded })
    }

    @Test
    fun aMidWayFailureKeepsPublishedFilesAndFailsTyped() = runTest {
        val info = InfoDict(
            media = listOf(
                media("a", "https://cdn.fixtures.example.net/a.mp4", "First"),
                media("b", "https://cdn.fixtures.example.net/b.mp4", "Second"),
                media("c", "https://cdn.fixtures.example.net/c.mp4", "Third"),
            ),
        )
        val store = FakeFileStore()
        val transfer = FailsOnSecondTransfer(payload)
        val engine = engine(this, FixedExtractor(info, Regex("""https?://x\.example/.+""")), transfer, store)

        val job = engine.submit(
            request(url = statusUrl, key = "midway-media-key", selectedMediaIds = listOf("a", "b")),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.NETWORK_FAILURE, finished.error?.code)
        assertEquals(1, finished.artifacts.size, "the file already published stays")
        assertEquals("First.mp4", finished.artifacts.single().relativePath)
        // A non-2xx response fails before the second temp is created, so the
        // only created temp is the published one.
        assertEquals(1, store.created.size)
        assertTrue(store.created.single().published)
        assertEquals(
            listOf("https://cdn.fixtures.example.net/a.mp4", "https://cdn.fixtures.example.net/b.mp4"),
            transfer.requests.map { it.url },
        )
    }

    // ------------------------------------------------------------------ fakes

    private fun singleFormatInfo() = InfoDict(
        id = "fixture",
        title = "Fixture Clip",
        formats = listOf(
            MediaFormat(
                formatId = "18",
                url = formatUrl,
                ext = "mp4",
                vcodec = "avc1",
                acodec = "mp4a",
                downloaderOptions = DownloaderOptions(httpChunkSize = 1024),
            ),
        ),
    )

    private class FixedExtractor(
        private val info: InfoDict,
        validUrl: Regex = Regex("""https?://youtube\.example/.+"""),
    ) : InfoExtractor(
        ieKey = ExtractorRegistry.GENERIC_KEY,
        http = ExtractorHttp(NoopTransfer),
        validUrl = validUrl,
    ) {
        var calls: Int = 0
            private set

        override suspend fun extract(url: String): InfoDict {
            calls++
            return info
        }
    }

    private class ThrowingExtractor(private val error: ExtractionError) : InfoExtractor(
        ieKey = ExtractorRegistry.GENERIC_KEY,
        http = ExtractorHttp(NoopTransfer),
        validUrl = Regex("""https?://youtube\.example/.+"""),
    ) {
        override suspend fun extract(url: String): InfoDict = throw error
    }

    /** Matches nothing the tests submit, so the probe path is chosen. */
    private class UnmatchedExtractor : InfoExtractor(
        ieKey = ExtractorRegistry.GENERIC_KEY,
        http = ExtractorHttp(NoopTransfer),
        validUrl = Regex("""https?://youtube\.example/.+"""),
    ) {
        override suspend fun extract(url: String): InfoDict = error("must not be called")
    }

    private object NoopTransfer : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("unused")
    }

    private class ScriptedTransfer(
        private val body: ByteArray,
        private val contentType: String = "application/octet-stream",
        private val statusCode: Int = 200,
    ) : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return HttpResponse.Final(
                statusCode = statusCode,
                contentType = contentType,
                totalBytes = body.size.toLong(),
                body = ByteArrayHttpBody(body),
            )
        }
    }

    /** Suspends inside the second body's first read so a test can cancel. */
    private class GatedSecondBodyTransfer(
        private val body: ByteArray,
        private val gate: CompletableDeferred<Unit>,
    ) : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val responseBody: HttpBody = if (requests.size == 1) {
                ByteArrayHttpBody(body)
            } else {
                GatedBody(body, gate)
            }
            return HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                totalBytes = body.size.toLong(),
                body = responseBody,
            )
        }
    }

    private class GatedBody(
        private val bytes: ByteArray,
        private val gate: CompletableDeferred<Unit>,
    ) : HttpBody {
        private var position = 0
        private var released = false

        override suspend fun readNext(buffer: ByteArray): Int {
            if (!released) {
                gate.await()
                released = true
            }
            if (position >= bytes.size) return -1
            val count = minOf(bytes.size - position, buffer.size)
            bytes.copyInto(buffer, 0, position, position + count)
            position += count
            return count
        }

        override suspend fun close() = Unit
    }

    /** Fails the second request so a mid-way media failure can be checked. */
    private class FailsOnSecondTransfer(
        private val body: ByteArray,
    ) : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            if (requests.size >= 2) {
                return HttpResponse.Final(statusCode = 500, contentType = "text/plain")
            }
            return HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                totalBytes = body.size.toLong(),
                body = ByteArrayHttpBody(body),
            )
        }
    }

    private class FakeFile : FileHandle {
        val bytes = mutableListOf<Byte>()
        var closed = false
        var discarded = false
        var published = false

        override fun write(bytes: ByteArray, length: Int) {
            for (index in 0 until length) this.bytes += bytes[index]
        }

        override fun close() {
            closed = true
        }

        override fun discard() {
            discarded = true
        }
    }

    private class FakeFileStore : FileStore {
        val live = mutableMapOf<String, FakeFile>()
        val created = mutableListOf<FakeFile>()

        override fun createTempFile(): FileHandle = FakeFile().also { created += it }

        override fun publish(temp: FileHandle, relativePath: String): String {
            val file = temp as FakeFile
            check(!file.published) { "A temp file may only be published once." }
            file.published = true
            live[relativePath] = file
            return relativePath
        }

        override fun delete(relativePath: String): Boolean = live.remove(relativePath) != null

        override fun size(relativePath: String): Long? = live[relativePath]?.bytes?.size?.toLong()
    }
}
