package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.platform.JavaNetHttpTransfer
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
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
import org.junit.Assume.assumeTrue

/**
 * T-079 opt-in gate: the real Kotlin extractor and shared engine download the
 * public Big Buck Bunny video's best split pair, merge it with the desktop
 * toolkit into one playable file, and no yt-dlp process runs. Enabled only
 * with `-PliveExtractorTests=true` and `ffmpeg`/`ffprobe` on PATH.
 */
class DesktopLiveKotlinMergeTest {

    @Test
    fun liveYoutubeSplitStreamsMergeIntoOneFileWithoutACliProcess() = runBlocking {
        assumeTrue(
            "Set -PliveExtractorTests=true to run the live Kotlin merge check",
            System.getProperty("liveExtractorTests") == "true",
        )
        val (_, ffprobe) = FfmpegFixtures.assumeTools()

        val root = Files.createTempDirectory("anydownlod-live-merge")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val processStarted = AtomicBoolean(false)
        try {
            val transfer = JavaNetHttpTransfer()
            val registry = ExtractorRegistry(listOf(YoutubeIE(ExtractorHttp(transfer))))
            val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString()))
            val http = HttpDownloadEngine(
                transfer = transfer,
                fileStore = DesktopFileStore { settings.settings.value.downloadRoot },
                settings = settings,
                scope = scope,
                ioDispatcher = Dispatchers.Default,
                registry = registry,
                toolkit = DesktopFfmpegToolkit(),
            )
            val cli = YtDlpCliEngine(
                settingsRepository = settings,
                scope = scope,
                runner = CliProcessRunner { _, _ -> processStarted.set(true); FakeCliProcess(emptyList()) },
                resolveExecutable = { "/fake/yt-dlp" },
                ioDispatcher = Dispatchers.Default,
            )
            val classifier = DesktopRouteClassifier(registry = registry)
            val engine = DesktopRoutingEngine(
                http = http,
                cli = cli,
                classify = classifier::route,
                scope = scope,
            )
            val url = "https://www.youtube.com/watch?v=YE7VzlLtp-4"
            assertEquals(DesktopRoute.KOTLIN, classifier.route(url))

            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = url,
                    options = DownloadOptions(),
                    idempotencyKey = "live-desktop-merge",
                ),
            )
            val phases = mutableListOf<String?>()
            val finished = withTimeout(600_000) {
                engine.jobs.first { jobs ->
                    val current = jobs.first { it.id == job.id }
                    phases += current.progress?.phase
                    current.state.isTerminal
                }.first { it.id == job.id }
            }
            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertTrue(phases.contains("merging"), "the live run must pass through the merging phase: $phases")
            val artifact = finished.artifacts.single()
            assertTrue(artifact.relativePath.endsWith(".mp4"), artifact.relativePath)
            val streams = FfmpegFixtures.probeStreams(ffprobe, root.resolve(artifact.relativePath))
            println(
                "live desktop merge: state=${finished.state} artifacts=${finished.artifacts.size} " +
                    "video=${streams.count { it.first == "video" }} audio=${streams.count { it.first == "audio" }}",
            )
            assertEquals(1, streams.count { it.first == "video" }, streams.toString())
            assertEquals(1, streams.count { it.first == "audio" }, streams.toString())
            assertTrue(!processStarted.get(), "no yt-dlp process may run for the live Kotlin merge")
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }
}
