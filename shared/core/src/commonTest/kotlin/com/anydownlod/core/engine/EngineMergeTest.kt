package com.anydownlod.core.engine

import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.ArtifactKind
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.MediaTags
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.FileHandle
import com.anydownlod.core.platform.FileStore
import com.anydownlod.core.platform.HttpFailureReason
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.postprocess.MediaFilePath
import com.anydownlod.core.postprocess.MediaToolkit
import com.anydownlod.core.postprocess.ToolkitCapabilities
import com.anydownlod.core.postprocess.ToolkitError
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * T-077: the registry route executes one merge. Two sides download to temps
 * through the same direct-file path, the fake toolkit concatenates them, and
 * one artifact is published. Fixtures only; no network and no real binary.
 */
class EngineMergeTest {

    private val videoUrl = "https://cdn.fixtures.example.net/video-only.mp4"
    private val audioUrl = "https://cdn.fixtures.example.net/audio-only.m4a"
    private val artworkUrl = "https://cdn.fixtures.example.net/cover.jpg"
    private val videoBytes = ByteArray(2048) { 7 }
    private val audioBytes = ByteArray(1024) { 9 }
    private val artworkBytes = ByteArray(64) { 3 }

    private fun request(key: String) = DownloadRequest(
        sourceUrl = "https://youtube.example/watch?v=fixture",
        options = DownloadOptions(),
        idempotencyKey = key,
    )

    private fun engine(
        scope: TestScope,
        info: InfoDict,
        transfer: HttpTransfer,
        store: FakeFileStore,
        toolkit: MediaToolkit,
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = store,
        settings = InMemorySettingsRepository(AppSettings(downloadRoot = "/tmp/anydownlod-merge-test")),
        scope = scope,
        idGenerator = { "id-${++idCounter}" },
        ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
        registry = ExtractorRegistry(listOf(FixedExtractor(info))),
        toolkit = toolkit,
    )

    private var idCounter = 0

    @Test
    fun mergeCapableEngineDownloadsBothSidesAndPublishesOneArtifact() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(store)
        val transfer = TwoUrlTransfer()
        val engine = engine(this, splitInfo(), transfer, store, toolkit)

        val job = engine.submit(request("merge"))
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        val artifact = finished.artifacts.single()
        assertEquals("Fixture Clip.mp4", artifact.relativePath)
        assertEquals((videoBytes.size + audioBytes.size).toLong(), artifact.sizeBytes)
        assertEquals(1, toolkit.mergeCalls)
        assertEquals(listOf(videoUrl, audioUrl), transfer.requested)

        // Video temp, audio temp, then the mp4 destination.
        assertEquals(3, store.created.size)
        assertTrue(store.created[0].discarded, "the video temp must be deleted after a merge")
        assertTrue(store.created[1].discarded, "the audio temp must be deleted after a merge")
        assertTrue(store.created[2].published, "the destination temp must be published")
        assertFalse(store.created[2].discarded)
        assertEquals("Fixture Clip.mp4", store.live.keys.single())
    }

    @Test
    fun hostThatCannotMergeDownloadsASingleFileAndNeverCallsTheToolkit() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(store, canMerge = false)
        val transfer = TwoUrlTransfer()
        val engine = engine(this, singleFileInfo(), transfer, store, toolkit)

        val job = engine.submit(request("single"))
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals("Fixture Clip.mp4", finished.artifacts.single().relativePath)
        assertEquals(0, toolkit.mergeCalls, "a host without merge capability must never call merge")
        assertEquals(listOf(videoUrl), transfer.requested)
    }

    @Test
    fun incompatibleStreamsFailTypedAndLeaveNoArtifact() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(store, failWith = ToolkitError.IncompatibleStreams())
        val engine = engine(this, splitInfo(), TwoUrlTransfer(), store, toolkit)

        val job = engine.submit(request("incompatible"))
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.UNSUPPORTED_FORMAT, finished.error?.code)
        assertFalse(finished.error?.retryable ?: true)
        assertTrue(finished.artifacts.isEmpty())
        assertEquals(3, store.created.count { it.discarded }, "every temp must be deleted")
        assertTrue(store.live.isEmpty())
    }

    @Test
    fun missingToolkitFailsTypedWithoutPublishing() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(store, failWith = ToolkitError.ToolUnavailable())
        val engine = engine(this, splitInfo(), TwoUrlTransfer(), store, toolkit)

        val job = engine.submit(request("missing-tool"))
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.UNSUPPORTED_FORMAT, finished.error?.code)
        assertFalse(finished.error?.retryable ?: true)
        assertTrue(finished.artifacts.isEmpty())
        assertTrue(store.live.isEmpty())
    }

    @Test
    fun toolkitIoFailureIsARetryablePostprocessingFailure() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(store, failWith = ToolkitError.Io())
        val engine = engine(this, splitInfo(), TwoUrlTransfer(), store, toolkit)

        val job = engine.submit(request("io"))
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.POSTPROCESSING_FAILURE, finished.error?.code)
        assertTrue(finished.error?.retryable ?: false)
        assertTrue(finished.artifacts.isEmpty())
        assertEquals(3, store.created.count { it.discarded })
    }

    @Test
    fun cancelDuringMergeDiscardsTempsAndLeavesNoArtifact() = runTest {
        val store = FakeFileStore()
        val gate = CompletableDeferred<Unit>()
        val toolkit = ConcatenatingToolkit(store, gate = gate)
        val engine = engine(this, splitInfo(), TwoUrlTransfer(), store, toolkit)

        val job = engine.submit(request("cancel-merge"))
        advanceUntilIdle()

        assertTrue(toolkit.started.isCompleted, "the merge must have started")
        assertEquals(JobState.POSTPROCESSING, engine.jobs.value.first { it.id == job.id }.state)

        engine.cancel(job.id)
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.CANCELLED, finished.state)
        assertTrue(finished.artifacts.isEmpty())
        assertEquals(3, store.created.count { it.discarded }, "cancel must delete both temps and the destination")
        assertTrue(store.live.isEmpty())
    }

    @Test
    fun audioExtractionDownloadsTheBestAudioAndPublishesTheContainer() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(store)
        val transfer = TwoUrlTransfer()
        val engine = engine(this, audioOnlyInfo(), transfer, store, toolkit)

        val job = engine.submit(
            DownloadRequest(
                sourceUrl = "https://youtube.example/watch?v=fixture",
                options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3),
                idempotencyKey = "extract-mp3",
            ),
        )
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals("Fixture Clip.mp3", finished.artifacts.single().relativePath)
        assertEquals(1, toolkit.extractCalls)
        assertEquals(listOf(audioUrl), transfer.requested)
        // The source temp is deleted; the mp3 destination is published.
        assertEquals(2, store.created.size)
        assertTrue(store.created[0].discarded, "the source temp must be deleted after extraction")
        assertTrue(store.created[1].published, "the destination temp must be published")
    }

    @Test
    fun extractionFailureFailsTypedAndLeavesNoArtifact() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(store, failWith = ToolkitError.IncompatibleStreams())
        val engine = engine(this, audioOnlyInfo(), TwoUrlTransfer(), store, toolkit)

        val job = engine.submit(
            DownloadRequest(
                sourceUrl = "https://youtube.example/watch?v=fixture",
                options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3),
                idempotencyKey = "extract-mp3-fail",
            ),
        )
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.UNSUPPORTED_FORMAT, finished.error?.code)
        assertTrue(finished.artifacts.isEmpty())
        assertEquals(2, store.created.count { it.discarded })
    }

    // ------------------------------------------------------------- tag embedding

    @Test
    fun spotifyTagsAndArtworkAreEmbeddedIntoTheExtractedAudio() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(store, canEmbedTags = true, canEmbedArtwork = true)
        val transfer = TwoUrlTransfer()
        val engine = engine(this, audioOnlyInfo(), transfer, store, toolkit)
        val tags = MediaTags(
            title = "Fixture Song",
            artists = listOf("Fixture Artist"),
            album = "Fixture Album",
            isrc = "ISRCFIXTURE01",
        )

        val job = engine.submit(
            DownloadRequest(
                sourceUrl = "https://youtube.example/watch?v=fixture",
                options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3),
                idempotencyKey = "tags",
                metadata = tags,
                artworkUrl = artworkUrl,
                parentBatchId = "batch-1",
            ),
        )
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(true, finished.tagsEmbedded)
        assertEquals("batch-1", finished.parentBatchId)
        assertEquals("Fixture Clip.mp3", finished.artifacts.single().relativePath)
        val write = toolkit.tagWrites.single()
        assertEquals(tags, write.tags)
        assertContentEquals(artworkBytes, write.artwork)
        assertTrue(transfer.requested.contains(artworkUrl))
    }

    @Test
    fun aHostWithoutTagCapabilityKeepsTheAudioAndRecordsTheSkip() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(store)
        val engine = engine(this, audioOnlyInfo(), TwoUrlTransfer(), store, toolkit)

        val job = engine.submit(
            DownloadRequest(
                sourceUrl = "https://youtube.example/watch?v=fixture",
                options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3),
                idempotencyKey = "tags-skip",
                metadata = MediaTags(title = "Fixture Song"),
                artworkUrl = artworkUrl,
            ),
        )
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(false, finished.tagsEmbedded)
        assertEquals(1, finished.artifacts.size)
        assertTrue(toolkit.tagWrites.isEmpty(), "a host without the capability must not be called")
    }

    @Test
    fun aFailedArtworkFetchStillEmbedsTheTags() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(store, canEmbedTags = true, canEmbedArtwork = true)
        val transfer = TwoUrlTransfer(failArtwork = true)
        val engine = engine(this, audioOnlyInfo(), transfer, store, toolkit)

        val job = engine.submit(
            DownloadRequest(
                sourceUrl = "https://youtube.example/watch?v=fixture",
                options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3),
                idempotencyKey = "tags-no-art",
                metadata = MediaTags(title = "Fixture Song"),
                artworkUrl = artworkUrl,
            ),
        )
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(true, finished.tagsEmbedded)
        assertEquals(null, toolkit.tagWrites.single().artwork)
    }

    @Test
    fun aFailedTagWriteFailsTypedAndLeavesNoArtifact() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(
            store,
            canEmbedTags = true,
            failTagsWith = ToolkitError.Io(),
        )
        val engine = engine(this, audioOnlyInfo(), TwoUrlTransfer(), store, toolkit)

        val job = engine.submit(
            DownloadRequest(
                sourceUrl = "https://youtube.example/watch?v=fixture",
                options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3),
                idempotencyKey = "tags-fail",
                metadata = MediaTags(title = "Fixture Song"),
            ),
        )
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.POSTPROCESSING_FAILURE, finished.error?.code)
        assertTrue(finished.artifacts.isEmpty())
        assertEquals(2, store.created.count { it.discarded })
    }

    // ------------------------------------------------------- overwrite modes

    private fun existingFile(store: FakeFileStore, relativePath: String = "Fixture Clip.mp3") {
        val existing = FakeFile("existing")
        existing.bytes += 1
        store.live[relativePath] = existing
    }

    private fun taggedRequest(
        key: String,
        overwrite: com.anydownlod.core.domain.OverwriteMode,
        relativePath: String = "Fixture Clip.mp3",
    ) = DownloadRequest(
        sourceUrl = "https://youtube.example/watch?v=fixture",
        options = DownloadOptions(
            mediaType = MediaType.AUDIO,
            audioContainer = AudioContainer.MP3,
            overwrite = overwrite,
        ),
        idempotencyKey = key,
        metadata = MediaTags(title = "Fixture Song", artists = listOf("Fixture Artist")),
        relativePath = relativePath,
    )

    @Test
    fun skipModeDoesNotDownloadASecondFile() = runTest {
        val store = FakeFileStore()
        existingFile(store)
        val transfer = TwoUrlTransfer()
        val engine = engine(this, audioOnlyInfo(), transfer, store, ConcatenatingToolkit(store))

        val job = engine.submit(taggedRequest("skip", com.anydownlod.core.domain.OverwriteMode.SKIP))
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals("skipped", finished.progress?.phase)
        assertEquals("Fixture Clip.mp3", finished.artifacts.single().relativePath)
        assertTrue(transfer.requested.isEmpty(), "skip must not download")
        assertEquals(1, store.live.size, "skip must not write a second file")
    }

    @Test
    fun forceModeReplacesTheExistingFile() = runTest {
        val store = FakeFileStore()
        existingFile(store)
        val transfer = TwoUrlTransfer()
        val engine = engine(this, audioOnlyInfo(), transfer, store, ConcatenatingToolkit(store))

        val job = engine.submit(taggedRequest("force", com.anydownlod.core.domain.OverwriteMode.FORCE))
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(listOf(audioUrl), transfer.requested)
        assertEquals(1, store.live.size, "force must replace, not duplicate")
        assertEquals("Fixture Clip.mp3", store.live.keys.single())
        assertTrue(store.live.values.single().published, "the replacement must be the new temp")
    }

    @Test
    fun metadataModeRetagsWithoutDownloading() = runTest {
        val store = FakeFileStore()
        existingFile(store)
        val toolkit = ConcatenatingToolkit(store, canEmbedTags = true)
        val transfer = TwoUrlTransfer()
        val engine = engine(this, audioOnlyInfo(), transfer, store, toolkit)

        val job = engine.submit(taggedRequest("metadata", com.anydownlod.core.domain.OverwriteMode.METADATA))
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(true, finished.tagsEmbedded)
        assertTrue(transfer.requested.isEmpty(), "metadata mode must not download")
        assertEquals(1, toolkit.tagWrites.size)
        assertEquals("Fixture Clip.mp3", toolkit.tagWrites.single().file)
        assertEquals("Fixture Song", toolkit.tagWrites.single().tags.title)
    }

    // ----------------------------------------------------------------- lyrics

    @Test
    fun lyricsAreEmbeddedOnlyForAContainerTheHostLists() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(
            store,
            canEmbedTags = true,
            lyricsContainers = setOf(AudioContainer.MP3),
        )
        val engine = engine(this, audioOnlyInfo(), TwoUrlTransfer(), store, toolkit)

        val job = engine.submit(
            DownloadRequest(
                sourceUrl = "https://youtube.example/watch?v=fixture",
                options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3),
                idempotencyKey = "lyrics-mp3",
                metadata = MediaTags(title = "Fixture Song", lyrics = "[00:01.00] line"),
            ),
        )
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(true, finished.lyricsEmbedded)
        assertEquals("[00:01.00] line", toolkit.tagWrites.single().tags.lyrics)
    }

    @Test
    fun lyricsAreDroppedWhenTheContainerIsNotListed() = runTest {
        val store = FakeFileStore()
        val toolkit = ConcatenatingToolkit(
            store,
            canEmbedTags = true,
            lyricsContainers = setOf(AudioContainer.MP3),
        )
        val engine = engine(this, audioOnlyInfo(), TwoUrlTransfer(), store, toolkit)

        val job = engine.submit(
            DownloadRequest(
                sourceUrl = "https://youtube.example/watch?v=fixture",
                options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.M4A),
                idempotencyKey = "lyrics-m4a",
                metadata = MediaTags(title = "Fixture Song", lyrics = "[00:01.00] line"),
            ),
        )
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(false, finished.lyricsEmbedded)
        assertEquals(null, toolkit.tagWrites.single().tags.lyrics)
        assertEquals("Fixture Song", toolkit.tagWrites.single().tags.title)
    }

    @Test
    fun aRequestedLrcIsPublishedNextToTheAudio() = runTest {
        val store = FakeFileStore()
        val engine = engine(this, audioOnlyInfo(), TwoUrlTransfer(), store, ConcatenatingToolkit(store))

        val job = engine.submit(
            DownloadRequest(
                sourceUrl = "https://youtube.example/watch?v=fixture",
                options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3),
                idempotencyKey = "lrc",
                relativePath = "Fixture Clip.mp3",
                lrcContent = "[00:01.00] line\n[00:02.00] second",
            ),
        )
        advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(2, finished.artifacts.size)
        val lrc = finished.artifacts.first { it.relativePath.endsWith(".lrc") }
        assertEquals("Fixture Clip.lrc", lrc.relativePath)
        assertEquals(ArtifactKind.METADATA, lrc.kind)
        val content = store.live["Fixture Clip.lrc"]?.bytes
            ?.map { it.toInt().toChar() }
            ?.joinToString("")
        assertEquals("[00:01.00] line\n[00:02.00] second", content)
    }

    // ------------------------------------------------------------------ fakes

    private fun splitInfo() = InfoDict(
        id = "fixture",
        title = "Fixture Clip",
        formats = listOf(
            MediaFormat(formatId = "v", url = videoUrl, ext = "mp4", vcodec = "avc1", acodec = "none", height = 720),
            MediaFormat(formatId = "a", url = audioUrl, ext = "m4a", vcodec = "none", acodec = "mp4a"),
        ),
    )

    private fun singleFileInfo() = InfoDict(
        id = "fixture",
        title = "Fixture Clip",
        formats = listOf(
            MediaFormat(formatId = "18", url = videoUrl, ext = "mp4", vcodec = "avc1", acodec = "mp4a", height = 360),
        ),
    )

    private fun audioOnlyInfo() = InfoDict(
        id = "fixture",
        title = "Fixture Clip",
        formats = listOf(
            MediaFormat(formatId = "a", url = audioUrl, ext = "m4a", vcodec = "none", acodec = "mp4a"),
        ),
    )

    private class FixedExtractor(private val info: InfoDict) : InfoExtractor(
        ieKey = ExtractorRegistry.GENERIC_KEY,
        http = ExtractorHttp(NoopTransfer),
        validUrl = Regex("""https?://youtube\.example/.+"""),
    ) {
        override suspend fun extract(url: String): InfoDict = info
    }

    private object NoopTransfer : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("unused")
    }

    private inner class TwoUrlTransfer(private val failArtwork: Boolean = false) : HttpTransfer {
        val requested = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requested += request.url
            if (request.url == artworkUrl) {
                return if (failArtwork) {
                    HttpResponse.Failed(HttpFailureReason.NETWORK, "fixture artwork failure")
                } else {
                    HttpResponse.Final(
                        statusCode = 200,
                        contentType = "image/jpeg",
                        totalBytes = artworkBytes.size.toLong(),
                        body = ByteArrayHttpBody(artworkBytes),
                    )
                }
            }
            val bytes = when (request.url) {
                videoUrl -> videoBytes
                audioUrl -> audioBytes
                else -> error("unexpected URL")
            }
            return HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

    private class FakeFile(val token: String) : FileHandle {
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
        private var counter = 0

        override fun createTempFile(): FileHandle = FakeFile("temp-${++counter}").also { created += it }

        override fun createTempFile(extension: String): FileHandle =
            FakeFile("temp-${++counter}.$extension").also { created += it }

        override fun mediaFilePath(temp: FileHandle): MediaFilePath = MediaFilePath((temp as FakeFile).token)

        override fun mediaFilePath(relativePath: String): MediaFilePath = MediaFilePath(relativePath)

        override fun publish(temp: FileHandle, relativePath: String): String {
            val file = temp as FakeFile
            check(!file.published) { "A temp file may only be published once." }
            file.published = true
            live[relativePath] = file
            return relativePath
        }

        override fun delete(relativePath: String): Boolean = live.remove(relativePath) != null

        override fun size(relativePath: String): Long? = live[relativePath]?.bytes?.size?.toLong()

        fun file(token: String): FakeFile = created.first { it.token == token }
    }

    private class ConcatenatingToolkit(
        private val store: FakeFileStore,
        private val canMerge: Boolean = true,
        private val canEmbedTags: Boolean = false,
        private val canEmbedArtwork: Boolean = false,
        private val lyricsContainers: Set<AudioContainer>? = null,
        private val failWith: ToolkitError? = null,
        private val failTagsWith: ToolkitError? = null,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : MediaToolkit {
        val started = CompletableDeferred<Unit>()
        var mergeCalls = 0
            private set
        var extractCalls = 0
            private set
        val tagWrites = mutableListOf<TagWrite>()

        data class TagWrite(val file: String, val tags: MediaTags, val artwork: ByteArray?)

        override fun capabilities(): ToolkitCapabilities = ToolkitCapabilities(
            canMerge = canMerge,
            audioContainers = if (canMerge) {
                setOf(AudioContainer.M4A, AudioContainer.OPUS, AudioContainer.MP3, AudioContainer.WAV, AudioContainer.FLAC)
            } else {
                emptySet()
            },
            canEmbedTags = canEmbedTags,
            canEmbedArtwork = canEmbedArtwork,
            lyricsContainers = lyricsContainers ?: if (canEmbedTags) {
                setOf(AudioContainer.M4A, AudioContainer.MP3, AudioContainer.OPUS, AudioContainer.FLAC)
            } else {
                emptySet()
            },
        )

        override suspend fun merge(video: MediaFilePath, audio: MediaFilePath, destination: MediaFilePath) {
            mergeCalls++
            started.complete(Unit)
            gate?.await()
            failWith?.let { throw it }
            val videoTemp = store.file(video.token)
            val audioTemp = store.file(audio.token)
            val out = store.file(destination.token)
            out.bytes += videoTemp.bytes
            out.bytes += audioTemp.bytes
        }

        override suspend fun extractAudio(source: MediaFilePath, container: AudioContainer, destination: MediaFilePath) {
            extractCalls++
            failWith?.let { throw it }
            val sourceTemp = store.file(source.token)
            val out = store.file(destination.token)
            out.bytes += sourceTemp.bytes
        }

        override suspend fun embedTags(file: MediaFilePath, tags: MediaTags, artwork: ByteArray?) {
            failTagsWith?.let { throw it }
            failWith?.let { throw it }
            tagWrites += TagWrite(file.token, tags, artwork)
        }
    }
}
