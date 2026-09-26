package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.fake.InMemorySettingsRepository
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
 * T-077: the shared engine executes a merge with the real desktop toolkit.
 *
 * The two sides are local lavfi fixtures served by a fake transfer; the
 * published file is probed with `ffprobe`. No network and no live site.
 */
class DesktopEngineMergeTest {

    @Test
    fun engineMergesALocalSplitPairThroughTheDesktopToolkit() = runBlocking {
        val (ffmpeg, ffprobe) = FfmpegFixtures.assumeTools()
        val root = Files.createTempDirectory("anydownlod-desktop-merge")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val videoFile = root.resolve("source-video.mp4")
            val audioFile = root.resolve("source-audio.m4a")
            FfmpegFixtures.generateVideoOnly(ffmpeg, videoFile)
            FfmpegFixtures.generateAudioOnly(ffmpeg, audioFile)
            val videoBytes = Files.readAllBytes(videoFile)
            val audioBytes = Files.readAllBytes(audioFile)
            Files.delete(videoFile)
            Files.delete(audioFile)

            val videoUrl = "https://cdn.fixtures.example.net/video-only.mp4"
            val audioUrl = "https://cdn.fixtures.example.net/audio-only.m4a"
            val transfer = PairTransfer(mapOf(videoUrl to videoBytes, audioUrl to audioBytes))
            val info = InfoDict(
                id = "fixture",
                title = "Fixture Clip",
                formats = listOf(
                    MediaFormat(
                        formatId = "v",
                        url = videoUrl,
                        ext = "mp4",
                        vcodec = "avc1",
                        acodec = "none",
                        height = 720,
                    ),
                    MediaFormat(formatId = "a", url = audioUrl, ext = "m4a", vcodec = "none", acodec = "mp4a"),
                ),
            )
            val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString()))
            val engine = HttpDownloadEngine(
                transfer = transfer,
                fileStore = DesktopFileStore { settings.settings.value.downloadRoot },
                settings = settings,
                scope = scope,
                ioDispatcher = Dispatchers.Default,
                registry = ExtractorRegistry(listOf(FixedExtractor(info))),
                toolkit = DesktopFfmpegToolkit(),
            )

            val job = engine.submit(DownloadRequest(sourceUrl = "https://youtube.example/watch?v=fixture"))
            val finished = withTimeout(120_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state.isTerminal } }
                    .first { it.id == job.id }
            }

            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertEquals(listOf(videoUrl, audioUrl), transfer.requested)
            val artifact = finished.artifacts.single()
            assertEquals("Fixture Clip.mp4", artifact.relativePath)

            val published = root.resolve(artifact.relativePath)
            assertTrue(Files.isRegularFile(published))
            val streams = FfmpegFixtures.probeStreams(ffprobe, published)
            assertEquals(2, streams.size, streams.toString())
            assertTrue(streams.contains("video" to "h264"), streams.toString())
            assertTrue(streams.contains("audio" to "aac"), streams.toString())

            // No temp survives a completed merge.
            val leftovers = Files.newDirectoryStream(root).use { entries ->
                entries.filter { it.fileName.toString().startsWith(".anydownload-") }
            }
            assertTrue(leftovers.isEmpty(), "leftover temps: $leftovers")
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun engineExtractsAudioToMp3ThroughTheDesktopToolkit() = runBlocking {
        val (ffmpeg, ffprobe) = FfmpegFixtures.assumeTools()
        val root = Files.createTempDirectory("anydownlod-desktop-audio")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val audioFile = root.resolve("source-audio.m4a")
            FfmpegFixtures.generateAudioOnly(ffmpeg, audioFile)
            val audioBytes = Files.readAllBytes(audioFile)
            Files.delete(audioFile)

            val audioUrl = "https://cdn.fixtures.example.net/audio-only.m4a"
            val transfer = PairTransfer(mapOf(audioUrl to audioBytes))
            val info = InfoDict(
                id = "fixture",
                title = "Fixture Clip",
                formats = listOf(
                    MediaFormat(formatId = "a", url = audioUrl, ext = "m4a", vcodec = "none", acodec = "mp4a"),
                ),
            )
            val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString()))
            val engine = HttpDownloadEngine(
                transfer = transfer,
                fileStore = DesktopFileStore { settings.settings.value.downloadRoot },
                settings = settings,
                scope = scope,
                ioDispatcher = Dispatchers.Default,
                registry = ExtractorRegistry(listOf(FixedExtractor(info))),
                toolkit = DesktopFfmpegToolkit(),
            )

            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = "https://youtube.example/watch?v=fixture",
                    options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.MP3),
                ),
            )
            val finished = withTimeout(120_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state.isTerminal } }
                    .first { it.id == job.id }
            }

            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            val artifact = finished.artifacts.single()
            assertEquals("Fixture Clip.mp3", artifact.relativePath)
            val published = root.resolve(artifact.relativePath)
            assertEquals(listOf("audio" to "mp3"), FfmpegFixtures.probeStreams(ffprobe, published))
            assertTrue(FfmpegFixtures.probeFormat(ffprobe, published).contains("mp3"))
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }

    private class PairTransfer(private val bodies: Map<String, ByteArray>) : HttpTransfer {
        val requested = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requested += request.url
            val bytes = bodies[request.url] ?: error("unexpected URL")
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
}
