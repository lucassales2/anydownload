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
 * T-064 opt-in live check: the real Kotlin extractor + shared download engine
 * save the native M4A stream of the public Big Buck Bunny video, and no
 * yt-dlp process runs. Enabled only with `-PliveExtractorTests=true`.
 */
class DesktopLiveKotlinDownloadTest {

    @Test
    fun liveYoutubeM4aDownloadsThroughKotlinWithoutACliProcess() = runBlocking {
        assumeTrue(
            "Set -PliveExtractorTests=true to run the live Kotlin download check",
            System.getProperty("liveExtractorTests") == "true",
        )

        val root = Files.createTempDirectory("anydownlod-live-kotlin")
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
            )
            val cli = YtDlpCliEngine(
                settingsRepository = settings,
                scope = scope,
                runner = CliProcessRunner { _, _ -> processStarted.set(true); FakeCliProcess(emptyList()) },
                resolveExecutable = { "/fake/yt-dlp" },
                ioDispatcher = Dispatchers.Default,
            )
            val engine = DesktopRoutingEngine(
                http = http,
                cli = cli,
                classify = DesktopRouteClassifier(registry = registry)::route,
                scope = scope,
            )
            val url = "https://www.youtube.com/watch?v=YE7VzlLtp-4"
            assertEquals(DesktopRoute.KOTLIN, DesktopRouteClassifier(registry = registry).route(url))

            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = url,
                    options = DownloadOptions(mediaType = MediaType.AUDIO, audioContainer = AudioContainer.M4A),
                    idempotencyKey = "live-desktop-audio",
                ),
            )
            val finished = withTimeout(180_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state.isTerminal } }
                    .first { it.id == job.id }
            }
            println(
                "live desktop kotlin: state=${finished.state} formats=${finished.formatsNeedingJs} " +
                    "titleLength=${finished.title?.length ?: 0} artifacts=${finished.artifacts.size}",
            )
            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            val artifact = finished.artifacts.single()
            assertTrue(artifact.relativePath.endsWith(".m4a"), artifact.relativePath)
            assertTrue(Files.size(root.resolve(artifact.relativePath)) > 0)
            assertTrue(!processStarted.get(), "no yt-dlp process may run for the live Kotlin download")
        } finally {
            scope.cancel()
            root.toFile().deleteRecursively()
        }
    }
}
