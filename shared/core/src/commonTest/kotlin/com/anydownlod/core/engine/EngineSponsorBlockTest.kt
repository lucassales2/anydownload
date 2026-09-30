package com.anydownlod.core.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.SponsorBlockOutcome
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.FileHandle
import com.anydownlod.core.platform.FileStore
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.postprocess.MediaFilePath
import com.anydownlod.core.postprocess.MediaToolkit
import com.anydownlod.core.postprocess.SponsorSegment
import com.anydownlod.core.postprocess.ToolkitCapabilities
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T-016 SponsorBlock engine path: opt-in, typed outcomes, and a typed refusal. */
class EngineSponsorBlockTest {

    private val sourceUrl = "https://fixture.example/watch"
    private val mediaUrl = "https://cdn.example/media.mp4"

    private class MemoryHandle : FileHandle {
        override fun write(bytes: ByteArray, length: Int) = Unit
        override fun close() = Unit
        override fun discard() = Unit
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

    private class SponsorToolkit(private val canRemove: Boolean) : MediaToolkit {
        val removed = mutableListOf<List<SponsorSegment>>()
        override fun capabilities(): ToolkitCapabilities = ToolkitCapabilities(canRemoveSegments = canRemove)

        override suspend fun merge(video: MediaFilePath, audio: MediaFilePath, destination: MediaFilePath) =
            error("unused")

        override suspend fun extractAudio(source: MediaFilePath, container: com.anydownlod.core.domain.AudioContainer, destination: MediaFilePath) =
            error("unused")

        override suspend fun embedTags(file: MediaFilePath, tags: com.anydownlod.core.domain.MediaTags, artwork: ByteArray?) =
            error("unused")

        override suspend fun removeSegments(source: MediaFilePath, segments: List<SponsorSegment>, destination: MediaFilePath) {
            removed += segments
        }
    }

    private class FixedExtractor(private val mediaUrl: String) : InfoExtractor(
        ieKey = ExtractorRegistry.GENERIC_KEY,
        http = ExtractorHttp(NoopTransfer),
        validUrl = Regex("""https://fixture\.example/.+"""),
    ) {
        override suspend fun extract(url: String): InfoDict = InfoDict(
            id = "abcdefghijk",
            title = "Fixture Clip",
            formats = listOf(
                MediaFormat(formatId = "18", url = mediaUrl, ext = "mp4", vcodec = "avc1", acodec = "mp4a"),
            ),
        )
    }

    private object NoopTransfer : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("unused")
    }

    private class SponsorTransfer(
        private val mediaUrl: String,
        private val sponsorBody: String?,
    ) : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = when {
            request.url == mediaUrl -> HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                totalBytes = 4,
                body = ByteArrayHttpBody(byteArrayOf(1, 2, 3, 4)),
            )

            request.url.startsWith("https://sponsor.ajay.app/") -> {
                val body = sponsorBody ?: return HttpResponse.Final(statusCode = 404)
                HttpResponse.Final(
                    statusCode = 200,
                    contentType = "application/json",
                    totalBytes = body.length.toLong(),
                    body = ByteArrayHttpBody(body.encodeToByteArray()),
                )
            }

            else -> HttpResponse.Final(statusCode = 404)
        }
    }

    private fun TestScope.engine(toolkit: MediaToolkit, sponsorBody: String?): HttpDownloadEngine =
        HttpDownloadEngine(
            transfer = SponsorTransfer(mediaUrl, sponsorBody),
            fileStore = MemoryStore(),
            settings = InMemorySettingsRepository(AppSettings(downloadRoot = "/tmp/anydownlod-sponsor")),
            scope = this,
            idGenerator = { "sb-${++idCounter}" },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            registry = ExtractorRegistry(listOf(FixedExtractor(mediaUrl))),
            toolkit = toolkit,
        )

    private var idCounter = 0

    private fun request(options: DownloadOptions, key: String) = DownloadRequest(
        sourceUrl = sourceUrl,
        options = options,
        idempotencyKey = key,
    )

    private val oneSegment = """
        [{"category": "sponsor", "actionType": "skip", "segment": [1.0, 2.0]}]
    """.trimIndent()

    @Test
    fun segmentsAreRemovedThroughTheToolkit() = runTest {
        val toolkit = SponsorToolkit(canRemove = true)
        val engine = engine(toolkit, oneSegment)

        val job = engine.submit(request(DownloadOptions(sponsorBlockRemove = true), key = "sb-remove"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(SponsorBlockOutcome.REMOVED, finished.sponsorBlock)
        assertEquals(listOf(SponsorSegment("sponsor", 1_000, 2_000)), toolkit.removed.single())
    }

    @Test
    fun noSegmentsKeepsTheMediaAndRecordsTheOutcome() = runTest {
        val toolkit = SponsorToolkit(canRemove = true)
        val engine = engine(toolkit, "[]")

        val job = engine.submit(request(DownloadOptions(sponsorBlockRemove = true), key = "sb-none"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(SponsorBlockOutcome.NO_SEGMENTS, finished.sponsorBlock)
        assertTrue(toolkit.removed.isEmpty())
    }

    @Test
    fun anUnreachableServiceKeepsTheMediaAndRecordsUnavailable() = runTest {
        val toolkit = SponsorToolkit(canRemove = true)
        val engine = engine(toolkit, sponsorBody = null)

        val job = engine.submit(request(DownloadOptions(sponsorBlockRemove = true), key = "sb-unavailable"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(SponsorBlockOutcome.UNAVAILABLE, finished.sponsorBlock)
    }

    @Test
    fun aHostWithoutSegmentRemovalFailsTyped() = runTest {
        val toolkit = SponsorToolkit(canRemove = false)
        val engine = engine(toolkit, oneSegment)

        val job = engine.submit(request(DownloadOptions(sponsorBlockRemove = true), key = "sb-unsupported"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.UNSUPPORTED_FORMAT, finished.error?.code)
    }

    @Test
    fun aClipAndSponsorBlockRemovalCannotBeCombined() = runTest {
        val toolkit = SponsorToolkit(canRemove = true)
        val engine = engine(toolkit, oneSegment)

        val job = engine.submit(
            request(
                DownloadOptions(sponsorBlockRemove = true, clipStart = "0", clipEnd = "10"),
                key = "sb-clip-combined",
            ),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        assertTrue(toolkit.removed.isEmpty())
    }
}
