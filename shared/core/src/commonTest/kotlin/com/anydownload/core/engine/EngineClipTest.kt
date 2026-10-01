package com.anydownload.core.engine

import com.anydownload.core.domain.AppSettings
import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobErrorCode
import com.anydownload.core.domain.JobState
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorRegistry
import com.anydownload.core.extract.Chapter
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.fake.InMemorySettingsRepository
import com.anydownload.core.platform.ByteArrayHttpBody
import com.anydownload.core.platform.FileHandle
import com.anydownload.core.platform.FileStore
import com.anydownload.core.platform.HttpRequest
import com.anydownload.core.platform.HttpResponse
import com.anydownload.core.platform.HttpTransfer
import com.anydownload.core.postprocess.MediaFilePath
import com.anydownload.core.postprocess.MediaToolkit
import com.anydownload.core.postprocess.ToolkitCapabilities
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T-016 engine clip: range validation, toolkit cut, and URL timestamp fallback. */
class EngineClipTest {

    private val sourceUrl = "https://fixture.example/watch"
    private val mediaUrl = "https://cdn.example/media.mp4"

    private class MemoryHandle : FileHandle {
        private val chunks = mutableListOf<ByteArray>()
        var discarded = false
        override fun write(bytes: ByteArray, length: Int) {
            chunks += bytes.copyOf(length)
        }

        override fun close() = Unit
        override fun discard() {
            discarded = true
        }
    }

    private class MemoryStore : FileStore {
        val published = mutableMapOf<String, ByteArray>()
        override fun createTempFile(): FileHandle = MemoryHandle()
        override fun createTempFile(extension: String): FileHandle = MemoryHandle()
        override fun mediaFilePath(temp: FileHandle): MediaFilePath = MediaFilePath("temp")
        override fun mediaFilePath(relativePath: String): MediaFilePath = MediaFilePath(relativePath)
        override fun publish(temp: FileHandle, relativePath: String): String {
            published[relativePath] = ByteArray(0)
            return relativePath
        }

        override fun delete(relativePath: String): Boolean = published.remove(relativePath) != null
        override fun size(relativePath: String): Long? = published[relativePath]?.size?.toLong()
    }

    private class ClipToolkit(
        private val canClip: Boolean,
        private val gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null,
    ) : MediaToolkit {
        val clips = mutableListOf<Triple<Long, Long?, String>>()
        override fun capabilities(): ToolkitCapabilities = ToolkitCapabilities(canClip = canClip)

        override suspend fun merge(video: MediaFilePath, audio: MediaFilePath, destination: MediaFilePath) =
            error("unused")

        override suspend fun extractAudio(source: MediaFilePath, container: com.anydownload.core.domain.AudioContainer, destination: MediaFilePath) =
            error("unused")

        override suspend fun embedTags(file: MediaFilePath, tags: com.anydownload.core.domain.MediaTags, artwork: ByteArray?) =
            error("unused")

        override suspend fun clip(source: MediaFilePath, startMillis: Long, endMillis: Long?, destination: MediaFilePath) {
            gate?.await()
            clips += Triple(startMillis, endMillis, destination.token)
        }
    }

    private class FixedExtractor(
        private val sourceUrl: String,
        private val mediaUrl: String,
        private val chapters: List<Chapter> = emptyList(),
    ) : InfoExtractor(
        ieKey = ExtractorRegistry.GENERIC_KEY,
        http = ExtractorHttp(NoopTransfer),
        validUrl = Regex("""https://fixture\.example/.+"""),
    ) {
        override suspend fun extract(url: String): InfoDict = InfoDict(
            id = "fixture",
            title = "Fixture Clip",
            formats = listOf(
                MediaFormat(formatId = "18", url = mediaUrl, ext = "mp4", vcodec = "avc1", acodec = "mp4a"),
            ),
            chapters = chapters,
        )
    }

    private object NoopTransfer : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("unused")
    }

    private class MediaTransfer(private val mediaUrl: String) : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = if (request.url == mediaUrl) {
            HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                totalBytes = 4,
                body = ByteArrayHttpBody(byteArrayOf(1, 2, 3, 4)),
            )
        } else {
            HttpResponse.Final(statusCode = 404)
        }
    }

    private fun TestScope.engine(
        toolkit: MediaToolkit,
        url: String = sourceUrl,
        chapters: List<Chapter> = emptyList(),
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = MediaTransfer(mediaUrl),
        fileStore = MemoryStore(),
        settings = InMemorySettingsRepository(AppSettings(downloadRoot = "/tmp/anydownlod-clip")),
        scope = this,
        idGenerator = { "clip-${++idCounter}" },
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        registry = ExtractorRegistry(listOf(FixedExtractor(url, mediaUrl, chapters))),
        toolkit = toolkit,
    )

    private var idCounter = 0

    private fun request(url: String, options: DownloadOptions, key: String) = DownloadRequest(
        sourceUrl = url,
        options = options,
        idempotencyKey = key,
    )

    @Test
    fun clipRangeCutsThroughTheToolkit() = runTest {
        val toolkit = ClipToolkit(canClip = true)
        val engine = engine(toolkit)

        val job = engine.submit(
            request(sourceUrl, DownloadOptions(clipStart = "10", clipEnd = "20"), key = "clip-range"),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        val (start, end, _) = toolkit.clips.single()
        assertEquals(10_000L, start)
        assertEquals(20_000L, end)
        assertTrue(finished.artifacts.single().relativePath.endsWith(".mp4"))
    }

    @Test
    fun aUrlTimestampIsTheFallbackClipStart() = runTest {
        val toolkit = ClipToolkit(canClip = true)
        val url = "$sourceUrl?t=30"
        val engine = engine(toolkit, url = url)

        val job = engine.submit(request(url, DownloadOptions(), key = "clip-url"))
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
        val (start, end, _) = toolkit.clips.single()
        assertEquals(30_000L, start)
        assertEquals(null, end)
    }

    @Test
    fun cancellingDuringAClipDiscardsTheTemp() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val toolkit = ClipToolkit(canClip = true, gate = gate)
        val engine = engine(toolkit)

        val job = engine.submit(
            request(sourceUrl, DownloadOptions(clipStart = "10", clipEnd = "20"), key = "clip-cancel"),
        )
        testScheduler.advanceUntilIdle()

        engine.cancel(job.id)
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.CANCELLED, finished.state, finished.error?.message)
        assertTrue(finished.artifacts.isEmpty())
    }

    @Test
    fun anInvalidRangeFailsTyped() = runTest {
        val toolkit = ClipToolkit(canClip = true)
        val engine = engine(toolkit)

        val job = engine.submit(
            request(sourceUrl, DownloadOptions(clipStart = "20", clipEnd = "10"), key = "clip-invalid"),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        assertTrue(toolkit.clips.isEmpty())
    }

    @Test
    fun chapterSplitPublishesOneConfinedArtifactPerChapter() = runTest {
        val toolkit = ClipToolkit(canClip = true)
        val chapters = listOf(
            Chapter(startTime = 0.0, endTime = 30.0, title = "Intro"),
            Chapter(startTime = 30.0, endTime = 60.0, title = "Outro"),
        )
        val engine = engine(toolkit, chapters = chapters)

        val job = engine.submit(
            request(sourceUrl, DownloadOptions(splitByChapters = true), key = "split-chapters"),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(listOf("Fixture Clip - 01 - Intro.mp4", "Fixture Clip - 02 - Outro.mp4"), finished.artifacts.map { it.fileName })
        assertEquals(2, finished.artifacts.count { it.kind == com.anydownload.core.domain.ArtifactKind.CHAPTER })
        assertEquals(2, toolkit.clips.size)
        assertEquals(listOf(0L to 30_000L, 30_000L to 60_000L), toolkit.clips.map { it.first to it.second })
    }

    @Test
    fun aHostWithoutClipFailsChapterSplitTyped() = runTest {
        val toolkit = ClipToolkit(canClip = false)
        val engine = engine(toolkit, chapters = listOf(Chapter(startTime = 0.0, endTime = 30.0, title = "Intro")))

        val job = engine.submit(
            request(sourceUrl, DownloadOptions(splitByChapters = true), key = "split-unavailable"),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.UNSUPPORTED_FORMAT, finished.error?.code)
    }

    @Test
    fun aHostWithoutClipFailsTypedInsteadOfPublishingTheWholeFile() = runTest {
        val toolkit = ClipToolkit(canClip = false)
        val engine = engine(toolkit)

        val job = engine.submit(
            request(sourceUrl, DownloadOptions(clipStart = "10", clipEnd = "20"), key = "clip-unavailable"),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.UNSUPPORTED_FORMAT, finished.error?.code)
        assertTrue(finished.artifacts.isEmpty())
    }
}
