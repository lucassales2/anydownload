package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.music.AudioCandidate
import com.anydownlod.core.music.AudioMatcher
import com.anydownlod.core.music.AudioProvider
import com.anydownlod.core.music.AudioSource
import com.anydownlod.core.music.SongListEntry
import com.anydownlod.core.music.SongListResult
import com.anydownlod.core.music.SongRecord
import com.anydownlod.core.music.SpotifyBackend
import com.anydownlod.core.music.SpotifyDownloadService
import com.anydownlod.core.music.SpotifyListOptions
import com.anydownlod.core.music.SpotifyMetadataClient
import com.anydownlod.core.music.SpotifyQuery
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * T-086 desktop gate: a public track fixture becomes one tagged MP3 inside the
 * download root. The Spotify metadata and the match are fakes over public
 * fixture URLs; the download, MP3 extraction, and tag embedding run through
 * the real `HttpDownloadEngine` and `DesktopFfmpegToolkit`. No live call and
 * no cookie.
 */
class DesktopSpotifyDownloadGateTest {

    private val audioUrl = "https://cdn.fixtures.example.net/song.m4a"
    private val artworkUrl = "https://cdn.fixtures.example.net/cover.jpg"

    private val record = SongRecord(
        songId = "4uLU6hMCjMI75M1A2tKUQC",
        title = "Fixture Song",
        artists = listOf("Fixture Artist"),
        album = "Fixture Album",
        albumArtist = "Fixture Artist",
        durationMs = 214_000,
        isrc = "ISRCFIXTURE01",
        artworkUrl = artworkUrl,
        trackNumber = 1,
        year = 2020,
    )

    private class FixedMetadata(private val result: SongListResult) : SpotifyMetadataClient {
        override val backend: SpotifyBackend = SpotifyBackend.UNAUTHENTICATED
        override suspend fun resolve(query: SpotifyQuery): SongListResult = result
    }

    private class FixedProvider(private val candidate: AudioCandidate) : AudioProvider {
        override val source: AudioSource = AudioSource.YOUTUBE_MUSIC
        override val supportsIsrc: Boolean = false
        override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> =
            listOf(candidate)
    }

    private class MapTransfer(private val files: Map<String, ByteArray>) : HttpTransfer {
        val requested = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requested += request.url
            val bytes = files[request.url] ?: return HttpResponse.Final(statusCode = 404)
            return HttpResponse.Final(
                statusCode = 200,
                contentType = "application/octet-stream",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

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

    @Test
    fun aFixtureTrackDownloadsAsATaggedMp3InsideTheRoot() = runBlocking {
        val (ffmpeg, ffprobe) = FfmpegFixtures.assumeTools()
        val root = Files.createTempDirectory("anydownlod-spotify-gate")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val audioFile = root.resolve("source.m4a")
            val artworkFile = root.resolve("cover.jpg")
            FfmpegFixtures.generateAudioOnly(ffmpeg, audioFile)
            FfmpegFixtures.runFfmpeg(
                ffmpeg,
                "-f", "lavfi", "-i", "color=c=red:s=64x64",
                "-frames:v", "1",
                artworkFile.toString(),
            )
            val audioBytes = Files.readAllBytes(audioFile)
            val artworkBytes = Files.readAllBytes(artworkFile)
            Files.delete(audioFile)
            Files.delete(artworkFile)

            val transfer = MapTransfer(mapOf(audioUrl to audioBytes, artworkUrl to artworkBytes))
            val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString()))
            val toolkit = DesktopFfmpegToolkit()
            val engine = HttpDownloadEngine(
                transfer = transfer,
                fileStore = DesktopFileStore { settings.settings.value.downloadRoot },
                settings = settings,
                scope = scope,
                ioDispatcher = Dispatchers.Default,
                registry = ExtractorRegistry(
                    listOf(
                        FixedExtractor(
                            InfoDict(
                                id = "fixture",
                                title = "Fixture Song",
                                formats = listOf(
                                    MediaFormat(
                                        formatId = "a",
                                        url = audioUrl,
                                        ext = "m4a",
                                        vcodec = "none",
                                        acodec = "mp4a",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                toolkit = toolkit,
            )
            val metadata = FixedMetadata(
                SongListResult(name = "Fixture Song", entries = listOf(SongListEntry.Song(record))),
            )
            val matcher = AudioMatcher(
                providers = listOf(
                    FixedProvider(
                        AudioCandidate(
                            source = AudioSource.YOUTUBE_MUSIC,
                            url = "https://youtube.example/watch?v=fixture",
                            title = "Fixture Song",
                            artists = listOf("Fixture Artist"),
                            durationSeconds = 214.0,
                            album = "Fixture Album",
                            verified = true,
                        ),
                    ),
                ),
            )
            val service = SpotifyDownloadService(metadata, matcher, engine, idGenerator = { "batch-gate" })

            val preview = service.preview("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC")
            val report = service.queue(preview, DownloadOptions(), toolkit.capabilities())
            assertEquals(1, report.jobs.size)
            assertEquals(AudioContainer.MP3, report.jobs.single().request.options.audioContainer)

            val job = report.jobs.single()
            val finished = withTimeout(120_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state.isTerminal } }
                    .first { it.id == job.id }
            }

            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertEquals(true, finished.tagsEmbedded)
            assertEquals("batch-gate", finished.parentBatchId)
            val artifact = finished.artifacts.single()
            assertEquals("Fixture Artist - Fixture Song.mp3", artifact.relativePath)

            val published = root.resolve(artifact.relativePath)
            assertTrue(Files.isRegularFile(published), "the tagged MP3 must be inside the download root")

            // ffprobe shows the title, artist, and album; the artwork is an
            // attached picture stream.
            val tags = FfmpegFixtures.probeTags(ffprobe, published)
            assertEquals("Fixture Song", tags["title"])
            assertEquals("Fixture Artist", tags["artist"])
            assertEquals("Fixture Album", tags["album"])
            val streams = FfmpegFixtures.probeStreams(ffprobe, published)
            assertTrue(streams.contains("audio" to "mp3"), streams.toString())
            assertTrue(streams.any { it.first == "video" }, "the cover art stream must be present: $streams")

            // The artwork and the audio came from the fixture transfer; no
            // Spotify audio URL is ever requested.
            assertTrue(transfer.requested.contains(audioUrl))
            assertTrue(transfer.requested.contains(artworkUrl))
            assertTrue(transfer.requested.none { it.contains("spotify") })
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun aTwoSongListWritesAnM3uInOrderAndArchivesTheSongs() = runBlocking {
        val (ffmpeg, _) = FfmpegFixtures.assumeTools()
        val root = Files.createTempDirectory("anydownlod-spotify-m3u")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val audioFile = root.resolve("source.m4a")
            FfmpegFixtures.generateAudioOnly(ffmpeg, audioFile)
            val audioBytes = Files.readAllBytes(audioFile)
            Files.delete(audioFile)
            val second = record.copy(
                songId = "fixturetrack00000000002",
                title = "Second Song",
                trackNumber = 2,
            )

            val transfer = MapTransfer(mapOf(audioUrl to audioBytes))
            val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString()))
            val toolkit = DesktopFfmpegToolkit()
            val engine = HttpDownloadEngine(
                transfer = transfer,
                fileStore = DesktopFileStore { settings.settings.value.downloadRoot },
                settings = settings,
                scope = scope,
                ioDispatcher = Dispatchers.Default,
                registry = ExtractorRegistry(
                    listOf(
                        FixedExtractor(
                            InfoDict(
                                id = "fixture",
                                title = "Fixture Song",
                                formats = listOf(
                                    MediaFormat(
                                        formatId = "a",
                                        url = audioUrl,
                                        ext = "m4a",
                                        vcodec = "none",
                                        acodec = "mp4a",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                toolkit = toolkit,
            )
            val metadata = FixedMetadata(
                SongListResult(
                    name = "Fixture Hits",
                    entries = listOf(SongListEntry.Song(record), SongListEntry.Song(second)),
                ),
            )
            val provider = object : AudioProvider {
                override val source: AudioSource = AudioSource.YOUTUBE_MUSIC
                override val supportsIsrc: Boolean = false
                override suspend fun search(query: String, matchedByIsrc: Boolean): List<AudioCandidate> = listOf(
                    AudioCandidate(
                        source = source,
                        url = "https://youtube.example/watch?v=one",
                        title = "Fixture Song",
                        artists = listOf("Fixture Artist"),
                        durationSeconds = 214.0,
                        album = "Fixture Album",
                        verified = true,
                    ),
                    AudioCandidate(
                        source = source,
                        url = "https://youtube.example/watch?v=two",
                        title = "Second Song",
                        artists = listOf("Fixture Artist"),
                        durationSeconds = 214.0,
                        album = "Fixture Album",
                        verified = true,
                    ),
                )
            }
            val service = SpotifyDownloadService(
                metadata = metadata,
                matcher = AudioMatcher(listOf(provider)),
                engine = engine,
                listStore = DesktopSpotifyListStore { settings.settings.value.downloadRoot },
                idGenerator = { "batch-m3u" },
            )

            val preview = service.preview("https://open.spotify.com/playlist/fixture")
            val report = service.queue(
                preview,
                DownloadOptions(),
                toolkit.capabilities(),
                SpotifyListOptions(writeM3u = true, m3uName = "Fixture Hits.m3u8", archive = true),
            )
            assertEquals(2, report.jobs.size)

            val finished = withTimeout(120_000) {
                engine.jobs.first { jobs -> jobs.count { it.state.isTerminal } == 2 }
            }
            assertTrue(finished.all { it.state == JobState.COMPLETED }, finished.toString())

            val m3u = Files.readString(root.resolve("Fixture Hits.m3u8"))
            assertTrue(m3u.startsWith("#EXTM3U\n"), m3u)
            val firstLine = m3u.indexOf("Fixture Artist - Fixture Song.mp3")
            val secondLine = m3u.indexOf("Fixture Artist - Second Song.mp3")
            assertTrue(
                firstLine in 0 until secondLine,
                "the m3u must list the files in list order: $m3u",
            )
            assertTrue(Files.isRegularFile(root.resolve("Fixture Artist - Fixture Song.mp3")))
            assertTrue(Files.isRegularFile(root.resolve("Fixture Artist - Second Song.mp3")))

            // The archive now holds both Spotify ids; a later queue skips them.
            val archive = Files.readString(root.resolve(SpotifyListOptions.DEFAULT_ARCHIVE))
            assertTrue(archive.contains("4uLU6hMCjMI75M1A2tKUQC"), archive)
            assertTrue(archive.contains("fixturetrack00000000002"), archive)

            val secondService = SpotifyDownloadService(
                metadata = metadata,
                matcher = AudioMatcher(listOf(provider)),
                engine = engine,
                listStore = DesktopSpotifyListStore { settings.settings.value.downloadRoot },
                idGenerator = { "batch-m3u-2" },
            )
            val skipped = secondService.queue(
                preview,
                DownloadOptions(),
                toolkit.capabilities(),
                SpotifyListOptions(archive = true),
            )
            assertTrue(skipped.jobs.isEmpty(), "an archived song must not be downloaded again")
            assertEquals(2, skipped.skipped.size)
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun metaRetagsAnExistingFixtureFileWithoutDownloading() = runBlocking {
        val (ffmpeg, ffprobe) = FfmpegFixtures.assumeTools()
        val root = Files.createTempDirectory("anydownlod-spotify-meta")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val existing = root.resolve("Fixture Artist - Fixture Song.mp3")
            FfmpegFixtures.generateMp3(ffmpeg, existing)

            val transfer = MapTransfer(mapOf(audioUrl to Files.readAllBytes(existing)))
            val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString()))
            val toolkit = DesktopFfmpegToolkit()
            val engine = HttpDownloadEngine(
                transfer = transfer,
                fileStore = DesktopFileStore { settings.settings.value.downloadRoot },
                settings = settings,
                scope = scope,
                ioDispatcher = Dispatchers.Default,
                registry = ExtractorRegistry(
                    listOf(
                        FixedExtractor(
                            InfoDict(
                                id = "fixture",
                                title = "Fixture Song",
                                formats = listOf(
                                    MediaFormat(formatId = "a", url = audioUrl, ext = "m4a", vcodec = "none", acodec = "mp4a"),
                                ),
                            ),
                        ),
                    ),
                ),
                toolkit = toolkit,
            )
            val service = SpotifyDownloadService(
                metadata = FixedMetadata(
                    SongListResult(name = "Fixture Song", entries = listOf(SongListEntry.Song(record))),
                ),
                matcher = AudioMatcher(
                    providers = listOf(
                        FixedProvider(
                            AudioCandidate(
                                source = AudioSource.YOUTUBE_MUSIC,
                                url = "https://youtube.example/watch?v=fixture",
                                title = "Fixture Song",
                                artists = listOf("Fixture Artist"),
                                durationSeconds = 214.0,
                                album = "Fixture Album",
                                verified = true,
                            ),
                        ),
                    ),
                ),
                engine = engine,
                listStore = DesktopSpotifyListStore { settings.settings.value.downloadRoot },
                idGenerator = { "batch-meta" },
            )

            val preview = service.preview("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUqc")
            val report = service.meta(preview, DownloadOptions(), toolkit.capabilities())
            assertEquals(1, report.jobs.size)

            val job = report.jobs.single()
            val finished = withTimeout(120_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state.isTerminal } }
                    .first { it.id == job.id }
            }

            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertEquals(true, finished.tagsEmbedded)
            assertEquals("skipped", finished.progress?.phase)
            assertTrue(transfer.requested.none { it == audioUrl }, "meta must not fetch the media when redownload is off")

            val tags = FfmpegFixtures.probeTags(ffprobe, existing)
            assertEquals("Fixture Song", tags["title"])
            assertEquals("Fixture Artist", tags["artist"])
            assertEquals("Fixture Album", tags["album"])
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }
}
