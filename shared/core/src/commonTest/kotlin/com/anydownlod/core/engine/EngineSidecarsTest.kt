package com.anydownlod.core.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.ArtifactKind
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.CaptionPreference
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaTags
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.SubtitleFormat
import com.anydownlod.core.extract.SubtitleTrack
import com.anydownlod.core.extract.Thumbnail
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.FileHandle
import com.anydownlod.core.platform.FileStore
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.postprocess.MediaFilePath
import com.anydownlod.core.postprocess.MediaToolkit
import com.anydownlod.core.postprocess.ToolkitCapabilities
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-015 engine sidecars: caption conversion, thumbnail sidecars, and audio tag
 * embedding after a completed download. Fixtures only.
 */
class EngineSidecarsTest {

    private val sourceUrl = "https://fixture.example/watch"
    private val mediaUrl = "https://cdn.example/media.mp4"
    private val audioUrl = "https://cdn.example/audio.m4a"
    private val captionUrl = "https://cdn.example/captions.vtt"
    private val thumbnailUrl = "https://cdn.example/thumb.jpg"

    private val vtt = """
        WEBVTT

        00:00:01.000 --> 00:00:03.000
        Olá, mundo
    """.trimIndent()

    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01)

    private class MemoryHandle : FileHandle {
        private val chunks = mutableListOf<ByteArray>()
        var discarded = false
        val bytes: ByteArray get() = chunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
        override fun write(bytes: ByteArray, length: Int) {
            chunks += bytes.copyOf(length)
        }

        override fun close() = Unit
        override fun discard() {
            discarded = true
        }
    }

    private class MemoryStore : FileStore {
        val created = mutableListOf<MemoryHandle>()
        val published = mutableMapOf<String, ByteArray>()
        override fun createTempFile(): FileHandle = MemoryHandle().also { created += it }
        override fun createTempFile(extension: String): FileHandle = MemoryHandle().also { created += it }
        override fun mediaFilePath(temp: FileHandle): MediaFilePath = MediaFilePath("temp")
        override fun mediaFilePath(relativePath: String): MediaFilePath = MediaFilePath(relativePath)
        override fun publish(temp: FileHandle, relativePath: String): String {
            published[relativePath] = (temp as MemoryHandle).bytes
            return relativePath
        }

        override fun delete(relativePath: String): Boolean = published.remove(relativePath) != null
        override fun size(relativePath: String): Long? = published[relativePath]?.size?.toLong()
    }

    private class FakeToolkit(private val canEmbedSubtitles: Boolean = false) : MediaToolkit {
        val embedded = mutableListOf<Pair<MediaTags, ByteArray?>>()
        val embeddedSubtitles = mutableListOf<Triple<String, MediaFilePath, String?>>()
        override fun capabilities(): ToolkitCapabilities = ToolkitCapabilities(
            canMerge = false,
            audioContainers = setOf(AudioContainer.M4A),
            canEmbedTags = true,
            canEmbedArtwork = true,
            canEmbedSubtitles = canEmbedSubtitles,
        )

        override suspend fun merge(video: MediaFilePath, audio: MediaFilePath, destination: MediaFilePath) =
            error("unused")

        override suspend fun extractAudio(source: MediaFilePath, container: AudioContainer, destination: MediaFilePath) {
            // The engine publishes the destination temp; an empty file is enough.
        }

        override suspend fun embedTags(file: MediaFilePath, tags: MediaTags, artwork: ByteArray?) {
            embedded += tags to artwork
        }

        override suspend fun embedSubtitles(file: MediaFilePath, subtitles: MediaFilePath, language: String?) {
            embeddedSubtitles += Triple(file.token, subtitles, language)
        }
    }

    private class FixtureTransfer(
        private val mediaUrl: String,
        private val audioUrl: String,
        private val captionUrl: String,
        private val thumbnailUrl: String,
        private val vtt: String,
        private val jpeg: ByteArray,
    ) : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = when (request.url) {
            mediaUrl, audioUrl -> HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                totalBytes = 4,
                body = ByteArrayHttpBody(byteArrayOf(1, 2, 3, 4)),
            )

            captionUrl -> HttpResponse.Final(
                statusCode = 200,
                contentType = "text/vtt",
                totalBytes = vtt.length.toLong(),
                body = ByteArrayHttpBody(vtt.encodeToByteArray()),
            )

            thumbnailUrl -> HttpResponse.Final(
                statusCode = 200,
                contentType = "image/jpeg",
                totalBytes = jpeg.size.toLong(),
                body = ByteArrayHttpBody(jpeg),
            )

            else -> HttpResponse.Final(statusCode = 404)
        }
    }

    private class FixtureExtractor(
        private val info: InfoDict,
        private val sourceUrl: String,
    ) : InfoExtractor(
        ieKey = ExtractorRegistry.GENERIC_KEY,
        http = ExtractorHttp(NoopTransfer),
        validUrl = Regex("""https://fixture\.example/.+"""),
    ) {
        override suspend fun extract(url: String): InfoDict =
            if (url == sourceUrl) info else throw UnsupportedOperationException()
    }

    private object NoopTransfer : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("unused")
    }

    /** Expands two children; each child carries the same captions/thumbnail. */
    private inner class PlaylistSidecarExtractor(private val playlistUrl: String) : InfoExtractor(
        ieKey = ExtractorRegistry.GENERIC_KEY,
        http = ExtractorHttp(NoopTransfer),
        validUrl = Regex("""https://fixture\.example/.+"""),
    ) {
        override suspend fun extract(url: String): InfoDict {
            if (url == playlistUrl) {
                return InfoDict(
                    id = "playlist",
                    title = "Fixture Playlist",
                    entries = listOf(
                        com.anydownlod.core.extract.InfoEntry(id = "a", title = "One", url = "https://fixture.example/watch?a"),
                        com.anydownlod.core.extract.InfoEntry(id = "b", title = "Two", url = "https://fixture.example/watch?b"),
                    ),
                )
            }
            return videoInfo()
        }
    }

    private fun videoInfo(withCaptions: Boolean = true) = InfoDict(
        id = "fixture",
        title = "Fixture Clip",
        uploader = "Fixture Channel",
        uploadDate = "20240102",
        formats = listOf(
            MediaFormat(formatId = "18", url = mediaUrl, ext = "mp4", vcodec = "avc1", acodec = "mp4a"),
        ),
        subtitles = if (withCaptions) {
            listOf(
                SubtitleTrack(
                    language = "en",
                    name = "English",
                    formats = listOf(SubtitleFormat(ext = "vtt", url = captionUrl)),
                ),
            )
        } else {
            emptyList()
        },
        automaticCaptions = listOf(
            SubtitleTrack(
                language = "pt",
                automatic = true,
                formats = listOf(SubtitleFormat(ext = "vtt", url = captionUrl)),
            ),
        ),
        thumbnails = listOf(Thumbnail(url = thumbnailUrl, width = 1280, height = 720)),
    )

    private fun audioInfo() = InfoDict(
        id = "fixture",
        title = "Fixture Song",
        uploader = "Fixture Channel",
        uploadDate = "20240102",
        formats = listOf(
            MediaFormat(formatId = "140", url = audioUrl, ext = "m4a", vcodec = "none", acodec = "mp4a"),
        ),
        thumbnails = listOf(Thumbnail(url = thumbnailUrl, width = 640, height = 480)),
    )

    private fun TestScope.engine(
        info: InfoDict,
        transfer: HttpTransfer,
        store: FileStore = MemoryStore(),
        toolkit: MediaToolkit = FakeToolkit(),
        settings: com.anydownlod.core.SettingsRepository = InMemorySettingsRepository(
            AppSettings(downloadRoot = "/tmp/anydownlod-sidecars"),
        ),
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = transfer,
        fileStore = store,
        settings = settings,
        scope = this,
        idGenerator = { "side-${++idCounter}" },
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        registry = ExtractorRegistry(listOf(FixtureExtractor(info, sourceUrl))),
        toolkit = toolkit,
    )

    private var idCounter = 0

    private fun request(options: DownloadOptions, key: String) = DownloadRequest(
        sourceUrl = sourceUrl,
        options = options,
        idempotencyKey = key,
    )

    @Test
    fun aPresetEnablesTheThumbnailSidecarForTheAttempt() = runTest {
        val store = MemoryStore()
        val settings = InMemorySettingsRepository(AppSettings(downloadRoot = "/tmp/anydownlod-sidecars"))
        settings.addPreset("Thumbs", mapOf("writeThumbnail" to "true"))
        val presetId = settings.settings.value.presets.single().id
        val engine = engine(
            info = videoInfo(),
            transfer = FixtureTransfer(mediaUrl, audioUrl, captionUrl, thumbnailUrl, vtt, jpegBytes),
            store = store,
            settings = settings,
        )

        val job = engine.submit(
            request(DownloadOptions(presetIds = listOf(presetId)), key = "preset-sidecar"),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertTrue(ArtifactKind.THUMBNAIL in finished.artifacts.map { it.kind })
    }

    @Test
    fun captionAndThumbnailSidecarsArePublishedAfterTheMedia() = runTest {
        val store = MemoryStore()
        val engine = engine(
            info = videoInfo(),
            transfer = FixtureTransfer(mediaUrl, audioUrl, captionUrl, thumbnailUrl, vtt, jpegBytes),
            store = store,
        )

        val job = engine.submit(
            request(
                DownloadOptions(
                    captionLanguage = "en",
                    captionPreference = CaptionPreference.MANUAL,
                    captionFormat = CaptionFormat.SRT,
                    writeThumbnail = true,
                ),
                key = "sidecar-video",
            ),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        val kinds = finished.artifacts.map { it.kind }
        assertTrue(ArtifactKind.VIDEO in kinds)
        assertTrue(ArtifactKind.CAPTIONS in kinds)
        assertTrue(ArtifactKind.THUMBNAIL in kinds)

        val captions = finished.artifacts.first { it.kind == ArtifactKind.CAPTIONS }
        assertEquals("Fixture Clip.en.srt", captions.fileName)
        assertEquals(
            "1\n00:00:01,000 --> 00:00:03,000\nOlá, mundo\n\n",
            store.published.getValue("Fixture Clip.en.srt").decodeToString(),
        )

        val thumbnail = finished.artifacts.first { it.kind == ArtifactKind.THUMBNAIL }
        assertEquals("Fixture Clip.jpg", thumbnail.fileName)
        assertContentEquals(jpegBytes, store.published.getValue("Fixture Clip.jpg"))
    }

    @Test
    fun aMissingTrackSkipsTheCaptionSidecarWithoutFailingTheJob() = runTest {
        val store = MemoryStore()
        val engine = engine(
            info = videoInfo(withCaptions = false),
            transfer = FixtureTransfer(mediaUrl, audioUrl, captionUrl, thumbnailUrl, vtt, jpegBytes),
            store = store,
        )

        val job = engine.submit(
            request(
                DownloadOptions(
                    captionLanguage = "de",
                    captionFormat = CaptionFormat.SRT,
                ),
                key = "sidecar-missing",
            ),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(listOf(ArtifactKind.VIDEO), finished.artifacts.map { it.kind })
    }

    @Test
    fun embedSubtitlesEmbedsInsteadOfWritingASidecar() = runTest {
        val toolkit = FakeToolkit(canEmbedSubtitles = true)
        val store = MemoryStore()
        val engine = engine(
            info = videoInfo(),
            transfer = FixtureTransfer(mediaUrl, audioUrl, captionUrl, thumbnailUrl, vtt, jpegBytes),
            store = store,
            toolkit = toolkit,
        )

        val job = engine.submit(
            request(
                DownloadOptions(
                    captionLanguage = "en",
                    captionPreference = CaptionPreference.MANUAL,
                    embedSubtitles = true,
                ),
                key = "sidecar-embed",
            ),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(listOf(ArtifactKind.VIDEO), finished.artifacts.map { it.kind })
        val (_, subtitles, language) = toolkit.embeddedSubtitles.single()
        assertEquals("en", language)
        assertTrue(store.published.keys.none { it.endsWith(".srt") })
        // The SRT temp is written and then discarded, not published.
        assertTrue(subtitles.token.isNotBlank())
    }

    @Test
    fun aHostWithoutSubtitleEmbeddingWritesTheSidecarInstead() = runTest {
        val toolkit = FakeToolkit(canEmbedSubtitles = false)
        val store = MemoryStore()
        val engine = engine(
            info = videoInfo(),
            transfer = FixtureTransfer(mediaUrl, audioUrl, captionUrl, thumbnailUrl, vtt, jpegBytes),
            store = store,
            toolkit = toolkit,
        )

        val job = engine.submit(
            request(
                DownloadOptions(captionLanguage = "en", embedSubtitles = true),
                key = "sidecar-embed-fallback",
            ),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertTrue(ArtifactKind.CAPTIONS in finished.artifacts.map { it.kind })
        assertTrue(toolkit.embeddedSubtitles.isEmpty())
    }

    @Test
    fun playlistChildrenGetTheirOwnSidecars() = runTest {
        val store = MemoryStore()
        val engine = HttpDownloadEngine(
            transfer = FixtureTransfer(mediaUrl, audioUrl, captionUrl, thumbnailUrl, vtt, jpegBytes),
            fileStore = store,
            settings = InMemorySettingsRepository(AppSettings(downloadRoot = "/tmp/anydownlod-sidecars")),
            scope = this,
            idGenerator = { "side-${++idCounter}" },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            registry = ExtractorRegistry(listOf(PlaylistSidecarExtractor(sourceUrl))),
        )

        val parent = engine.submit(
            request(
                DownloadOptions(captionLanguage = "en", captionFormat = CaptionFormat.SRT, writeThumbnail = true),
                key = "sidecar-playlist",
            ),
        )
        testScheduler.advanceUntilIdle()

        val children = engine.jobs.value.filter { it.parentBatchId == parent.id }
        assertEquals(2, children.size)
        assertTrue(children.all { it.state == JobState.COMPLETED }, children.joinToString { it.error?.message.orEmpty() })
        assertTrue(children.all { child -> child.artifacts.count { it.kind == ArtifactKind.CAPTIONS } == 1 })
        assertTrue(children.all { child -> child.artifacts.count { it.kind == ArtifactKind.THUMBNAIL } == 1 })
    }

    @Test
    fun writeMetadataEmbedsInfoTagsOnAudio() = runTest {
        val toolkit = FakeToolkit()
        val store = MemoryStore()
        val engine = engine(
            info = audioInfo(),
            transfer = FixtureTransfer(mediaUrl, audioUrl, captionUrl, thumbnailUrl, vtt, jpegBytes),
            store = store,
            toolkit = toolkit,
        )

        val job = engine.submit(
            request(
                DownloadOptions(
                    mediaType = MediaType.AUDIO,
                    audioContainer = AudioContainer.M4A,
                    writeMetadata = true,
                ),
                key = "sidecar-audio",
            ),
        )
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
        assertEquals(true, finished.tagsEmbedded)
        val (tags, artwork) = toolkit.embedded.single()
        assertEquals("Fixture Song", tags.title)
        assertEquals(listOf("Fixture Channel"), tags.artists)
        assertEquals(2024, tags.year)
        assertContentEquals(jpegBytes, artwork!!)

        val infoArtifact = finished.artifacts.first { it.kind == ArtifactKind.METADATA }
        assertEquals("Fixture Song.info.json", infoArtifact.fileName)
        assertTrue(store.published.getValue("Fixture Song.info.json").decodeToString().contains("Fixture Song"))
    }
}
