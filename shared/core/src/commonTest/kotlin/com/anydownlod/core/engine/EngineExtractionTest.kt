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
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
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
    ) = DownloadRequest(sourceUrl = url, options = options, idempotencyKey = key)

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
    fun mp3FailsTypedBecauseTheMediaToolkitIsNotBuilt() = runTest {
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
        assertTrue(finished.error?.message?.contains("media toolkit") == true, finished.error?.message)
        assertTrue(finished.error?.message?.contains("M4A") == true, finished.error?.message)
    }

    @Test
    fun mergeAndEmptySelectionsAreTypedAndHonest() {
        val video = MediaFormat(formatId = "v", url = formatUrl, vcodec = "avc1", acodec = "none")
        val audio = MediaFormat(formatId = "a", url = formatUrl, vcodec = "none", acodec = "opus")

        val merged = assertIs<FormatResolution.Unsupported>(
            resolveSelection(Selection.Merge(video, audio), formatsNeedingJs = 0),
        )
        assertTrue(merged.message.contains("merging"), merged.message)
        assertTrue(merged.message.contains("media toolkit"), merged.message)

        val none = assertIs<FormatResolution.Unsupported>(resolveSelection(Selection.None, formatsNeedingJs = 3))
        assertTrue(none.message.contains("3 more formats need the JavaScript runtime"), none.message)

        val noneWithoutJs = assertIs<FormatResolution.Unsupported>(resolveSelection(Selection.None, formatsNeedingJs = 0))
        assertFalse(noneWithoutJs.message.contains("JavaScript"), noneWithoutJs.message)
    }

    @Test
    fun noCompiledSpecEverRequestsAMerge() {
        for (profile in com.anydownlod.core.domain.VideoContainerProfile.entries) {
            for (codec in com.anydownlod.core.domain.VideoCodec.entries) {
                for (quality in listOf(
                    com.anydownlod.core.domain.QualityPreference.Best,
                    com.anydownlod.core.domain.QualityPreference.Worst,
                    com.anydownlod.core.domain.QualityPreference.Resolution("720"),
                )) {
                    val compiled = OptionsToSpec.compile(
                        DownloadOptions(videoProfile = profile, videoCodec = codec, quality = quality),
                    )
                    val single = assertIs<CompiledSpec.SingleFile>(compiled)
                    assertFalse(single.specText.contains("+"), "compiled spec must never merge: ${single.specText}")
                    assertFalse(single.sort.any { it.contains("+") })
                }
            }
        }
        val selections = OptionsToSpec.compile(
            DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.M4A),
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

    private class FixedExtractor(private val info: InfoDict) : InfoExtractor(
        ieKey = ExtractorRegistry.GENERIC_KEY,
        http = ExtractorHttp(NoopTransfer),
        validUrl = Regex("""https?://youtube\.example/.+"""),
    ) {
        override suspend fun extract(url: String): InfoDict = info
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
