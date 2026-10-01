package com.anydownload.core.engine

import com.anydownload.core.domain.AppSettings
import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobState
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorRegistry
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-017 download archive: record on success, skip on a repeat. */
class EngineArchiveTest {

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
        override fun publish(temp: FileHandle, relativePath: String): String {
            published[relativePath] = ByteArray(0)
            return relativePath
        }

        override fun delete(relativePath: String): Boolean = published.remove(relativePath) != null
        override fun size(relativePath: String): Long? = published[relativePath]?.size?.toLong()
    }

    private class MemoryArchive : DownloadArchive {
        val entries = mutableSetOf<ArchiveEntry>()
        override fun contains(entry: ArchiveEntry): Boolean = entry in entries
        override fun add(entry: ArchiveEntry) {
            entries += entry
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

    private class MediaTransfer(private val mediaUrl: String) : HttpTransfer {
        var requests = 0
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests++
            return if (request.url == mediaUrl) {
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
    }

    private fun TestScope.engine(transfer: HttpTransfer, archive: DownloadArchive): HttpDownloadEngine =
        HttpDownloadEngine(
            transfer = transfer,
            fileStore = MemoryStore(),
            settings = InMemorySettingsRepository(AppSettings(downloadRoot = "/tmp/anydownlod-archive")),
            scope = this,
            idGenerator = { "ar-${++idCounter}" },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            registry = ExtractorRegistry(listOf(FixedExtractor(mediaUrl))),
            archive = archive,
        )

    private var idCounter = 0

    @Test
    fun aCompletedDownloadIsRecordedAndARepeatIsArchivedWithoutARequest() = runTest {
        val transfer = MediaTransfer(mediaUrl)
        val archive = MemoryArchive()
        val engine = engine(transfer, archive)

        val first = engine.submit(
            DownloadRequest(sourceUrl = sourceUrl, options = DownloadOptions(), idempotencyKey = "archive-first"),
        )
        testScheduler.advanceUntilIdle()
        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == first.id }.state)
        assertTrue(archive.contains(ArchiveEntry(ExtractorRegistry.GENERIC_KEY, "abcdefghijk")))
        val requestsAfterFirst = transfer.requests

        val second = engine.submit(
            DownloadRequest(sourceUrl = sourceUrl, options = DownloadOptions(), idempotencyKey = "archive-second"),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == second.id }
        assertEquals(JobState.COMPLETED, finished.state)
        assertEquals("archived", finished.progress?.phase)
        assertTrue(finished.artifacts.isEmpty())
        assertEquals(requestsAfterFirst, transfer.requests, "the archived source makes no request")
    }

    @Test
    fun archiveEntriesCarryOnlyTheKeyAndId() {
        assertEquals("youtube abcdefghijk", ArchiveEntry("youtube", "abcdefghijk").line())
        assertNull(ArchiveEntry.of(null, "abcdefghijk"))
        assertNull(ArchiveEntry.of("youtube", null))
        assertNull(ArchiveEntry.of("you tube", "abcdefghijk"))
        assertNull(ArchiveEntry.of("youtube", "ab cd"))
    }
}
