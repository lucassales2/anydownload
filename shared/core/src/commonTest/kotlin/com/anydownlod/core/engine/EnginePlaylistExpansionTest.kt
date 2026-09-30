package com.anydownlod.core.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoEntry
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.fake.InMemorySettingsRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T-107 playlist expansion: an [InfoDict.entries] result becomes bounded child
 * jobs on the shared engine. Fixtures only; no network.
 */
class EnginePlaylistExpansionTest {

    private val playlistUrl = "https://youtube.example/playlist?list=fixture"
    private val payload = ByteArray(64) { (it % 251).toByte() }

    private var idCounter = 0

    private fun engine(
        scope: TestScope,
        extractor: InfoExtractor,
        transfer: HttpTransfer = ScriptedTransfer(payload),
        persist: (List<DownloadJob>) -> Unit = {},
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = FakeFileStore(),
        settings = InMemorySettingsRepository(AppSettings(downloadRoot = "/tmp/anydownlod-playlist-test")),
        scope = scope,
        idGenerator = { "id-${++idCounter}" },
        ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        registry = ExtractorRegistry(listOf(extractor)),
        persist = persist,
    )

    private fun entry(id: String) = InfoEntry(
        id = id,
        title = "Fixture $id",
        url = "https://youtube.example/watch?v=$id",
    )

    private class PlaylistExtractor(
        private val playlistUrl: String,
        private val entries: List<InfoEntry>,
    ) : InfoExtractor(
        ieKey = ExtractorRegistry.GENERIC_KEY,
        http = ExtractorHttp(NoopTransfer),
        validUrl = Regex("""https?://youtube\.example/.+"""),
    ) {
        override suspend fun extract(url: String): InfoDict {
            if (url == playlistUrl && entries.isNotEmpty()) {
                return InfoDict(id = "playlist", title = "Fixture Playlist", entries = entries)
            }
            return InfoDict(
                id = "video",
                title = "Fixture Video",
                formats = listOf(
                    MediaFormat(
                        formatId = "18",
                        url = url,
                        ext = "mp4",
                        vcodec = "avc1",
                        acodec = "mp4a",
                    ),
                ),
            )
        }
    }

    private object NoopTransfer : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("unused")
    }

    private class ScriptedTransfer(private val body: ByteArray) : HttpTransfer {
        val requests = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request.url
            return HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                totalBytes = body.size.toLong(),
                body = ByteArrayHttpBody(body),
            )
        }
    }

    private class FakeHandle : FileHandle {
        var written = 0
        override fun write(data: ByteArray, length: Int) {
            written += length
        }

        override fun close() = Unit
        override fun discard() = Unit
    }

    private class FakeFileStore : FileStore {
        val live = mutableMapOf<String, Int>()
        override fun createTempFile(): FileHandle = FakeHandle()
        override fun publish(temp: FileHandle, relativePath: String): String {
            live[relativePath] = (temp as FakeHandle).written
            return relativePath
        }

        override fun delete(relativePath: String): Boolean = live.remove(relativePath) != null
        override fun size(relativePath: String): Long? = live[relativePath]?.toLong()
    }

    private fun List<DownloadJob>.childrenOf(parentId: String): List<DownloadJob> =
        filter { it.parentBatchId == parentId }

    @Test
    fun threeEntriesWithLimitTwoCreateTwoChildrenAndFetchTwoMedia() = runTest {
        val extractor = PlaylistExtractor(playlistUrl, listOf(entry("v1"), entry("v2"), entry("v3")))
        val transfer = ScriptedTransfer(payload)
        val engine = engine(this, extractor, transfer)

        val parent = engine.submit(
            DownloadRequest(
                sourceUrl = playlistUrl,
                options = DownloadOptions(playlistItemLimit = 2),
                idempotencyKey = "limit-two",
            ),
        )
        testScheduler.advanceUntilIdle()

        val parentJob = engine.jobs.value.first { it.id == parent.id }
        assertEquals(JobState.COMPLETED, parentJob.state, parentJob.error?.message)
        assertTrue(parentJob.artifacts.isEmpty(), "the parent must not download media")

        val children = engine.jobs.value.childrenOf(parent.id)
        assertEquals(2, children.size)
        assertEquals(
            setOf("https://youtube.example/watch?v=v1", "https://youtube.example/watch?v=v2"),
            children.map { it.request.sourceUrl }.toSet(),
        )
        assertEquals(2, transfer.requests.size, "the third entry must not be fetched")
        assertTrue(children.all { it.state == JobState.COMPLETED })
        assertTrue(children.all { it.artifacts.size == 1 })
    }

    @Test
    fun anExplicitLimitAboveFiftyRaisesTheCap() = runTest {
        val extractor = PlaylistExtractor(playlistUrl, (1..60).map { entry("v$it") })
        val engine = engine(this, extractor)

        val parent = engine.submit(
            DownloadRequest(
                sourceUrl = playlistUrl,
                options = DownloadOptions(startPolicy = StartPolicy.MANUAL, playlistItemLimit = 60),
                idempotencyKey = "raise-cap",
            ),
        )
        assertEquals(JobState.PENDING, parent.state)
        engine.start(parent.id)
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == parent.id }.state)
        assertEquals(60, engine.jobs.value.childrenOf(parent.id).size)
    }

    @Test
    fun playlistItemsSelectionCreatesOnlyTheChosenChildren() = runTest {
        val extractor = PlaylistExtractor(playlistUrl, listOf(entry("v1"), entry("v2"), entry("v3")))
        val engine = engine(this, extractor)

        val parent = engine.submit(
            DownloadRequest(
                sourceUrl = playlistUrl,
                options = DownloadOptions(playlistItems = "3,1"),
                idempotencyKey = "items-spec",
            ),
        )
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == parent.id }.state)
        val children = engine.jobs.value.childrenOf(parent.id)
        assertEquals(
            listOf("https://youtube.example/watch?v=v3", "https://youtube.example/watch?v=v1"),
            children.map { it.request.sourceUrl },
        )
    }

    @Test
    fun anInvalidPlaylistItemsSpecFailsTheParentTyped() = runTest {
        val extractor = PlaylistExtractor(playlistUrl, listOf(entry("v1")))
        val engine = engine(this, extractor)

        val parent = engine.submit(
            DownloadRequest(
                sourceUrl = playlistUrl,
                options = DownloadOptions(playlistItems = "1,,2"),
                idempotencyKey = "items-bad",
            ),
        )
        testScheduler.advanceUntilIdle()

        val parentJob = engine.jobs.value.first { it.id == parent.id }
        assertEquals(JobState.FAILED, parentJob.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, parentJob.error?.code)
        assertTrue(engine.jobs.value.childrenOf(parent.id).isEmpty())
    }

    @Test
    fun limitZeroCapsExpansionAtFifty() = runTest {
        val extractor = PlaylistExtractor(playlistUrl, (1..60).map { entry("v$it") })
        val engine = engine(this, extractor)

        val parent = engine.submit(
            DownloadRequest(
                sourceUrl = playlistUrl,
                options = DownloadOptions(startPolicy = StartPolicy.MANUAL, playlistItemLimit = 0),
                idempotencyKey = "cap-fifty",
            ),
        )
        assertEquals(JobState.PENDING, parent.state)
        engine.start(parent.id)
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == parent.id }.state)
        val children = engine.jobs.value.childrenOf(parent.id)
        assertEquals(HttpDownloadEngine.PLAYLIST_ITEM_CAP, children.size)
        assertEquals(50, children.size)
        // Children copied the parent's manual start policy, so nothing downloaded.
        assertTrue(children.all { it.state == JobState.PENDING })
        assertTrue(children.none { it.artifacts.isNotEmpty() })
    }

    @Test
    fun unavailableEntryBecomesAFailedChildAndTheGoodEntryStillDownloads() = runTest {
        val extractor = PlaylistExtractor(
            playlistUrl,
            listOf(
                entry("good"),
                InfoEntry(
                    id = "bad",
                    title = "Fixture bad",
                    url = "https://youtube.example/watch?v=bad",
                    failure = ExtractionError.Unavailable("This entry is private."),
                ),
            ),
        )
        val engine = engine(this, extractor)

        val parent = engine.submit(DownloadRequest(sourceUrl = playlistUrl, idempotencyKey = "mixed"))
        testScheduler.advanceUntilIdle()

        val children = engine.jobs.value.childrenOf(parent.id).associateBy { it.request.sourceUrl }
        assertEquals(2, children.size)

        val failed = children.getValue("https://youtube.example/watch?v=bad")
        assertEquals(JobState.FAILED, failed.state)
        assertEquals(JobErrorCode.UNAVAILABLE_OR_PRIVATE, failed.error?.code)
        assertTrue(failed.artifacts.isEmpty())

        val good = children.getValue("https://youtube.example/watch?v=good")
        assertEquals(JobState.COMPLETED, good.state)
        assertEquals(1, good.artifacts.size)
    }

    @Test
    fun duplicateEntryIdsProduceOneChild() = runTest {
        val extractor = PlaylistExtractor(playlistUrl, listOf(entry("dup"), entry("dup")))
        val engine = engine(this, extractor)

        val parent = engine.submit(DownloadRequest(sourceUrl = playlistUrl, idempotencyKey = "duplicate"))
        testScheduler.advanceUntilIdle()

        assertEquals(1, engine.jobs.value.childrenOf(parent.id).size)
    }

    @Test
    fun cancelDuringExpansionKeepsTheFirstChildAndCreatesNoMore() = runTest {
        val extractor = PlaylistExtractor(playlistUrl, listOf(entry("a"), entry("b"), entry("c")))
        var engineRef: HttpDownloadEngine? = null
        var parentId: String? = null
        var cancelled = false
        val persist: (List<DownloadJob>) -> Unit = { jobs ->
            if (!cancelled && jobs.any { it.parentBatchId != null }) {
                cancelled = true
                parentId?.let { id -> engineRef?.cancel(id) }
            }
        }
        val engine = engine(this, extractor, persist = persist)
        engineRef = engine

        val parent = engine.submit(DownloadRequest(sourceUrl = playlistUrl, idempotencyKey = "cancel-expansion"))
        parentId = parent.id
        testScheduler.advanceUntilIdle()

        val jobs = engine.jobs.value
        assertEquals(JobState.CANCELLED, jobs.first { it.id == parent.id }.state)
        val children = jobs.childrenOf(parent.id)
        assertEquals(1, children.size)
        assertEquals("https://youtube.example/watch?v=a", children.single().request.sourceUrl)
    }

    @Test
    fun singleVideoInfoStillDownloadsOneFileAndCreatesNoChild() = runTest {
        val extractor = PlaylistExtractor(playlistUrl, entries = emptyList())
        val engine = engine(this, extractor)

        val job = engine.submit(DownloadRequest(sourceUrl = playlistUrl, idempotencyKey = "single-video"))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.single { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(1, finished.artifacts.size)
        assertNull(finished.parentBatchId)
        assertEquals(1, engine.jobs.value.size)
    }
}
