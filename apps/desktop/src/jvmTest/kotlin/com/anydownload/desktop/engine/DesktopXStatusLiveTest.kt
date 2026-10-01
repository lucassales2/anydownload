package com.anydownload.desktop.engine

import com.anydownload.core.ExtractorMediaPreviewSource
import com.anydownload.core.MediaPreviewResult
import com.anydownload.core.domain.AppSettings
import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobState
import com.anydownload.core.engine.HttpDownloadEngine
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorRegistry
import com.anydownload.core.extract.twitter.TwitterIE
import com.anydownload.core.fake.InMemorySettingsRepository
import com.anydownload.core.platform.JavaNetHttpTransfer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
 * Opt-in D7 live check: with `-PxStatusUrl=<public status>` the extractor
 * previews a live public status and the engine downloads one selected video
 * to a temp root. The URL is supplied at run time and never stored in the
 * repository; no media file is committed. Default CI skips this test.
 */
class DesktopXStatusLiveTest {

    @Test
    fun oneSelectedVideoDownloadsFromALivePublicStatus() = runBlocking {
        val statusUrl = System.getProperty("xStatusUrl")?.takeIf { it.isNotBlank() }
        assumeTrue("Set -PxStatusUrl=<public status> to run the live X check", statusUrl != null)

        val root = Files.createTempDirectory("anydownlod-desktop-x-live")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val registry = ExtractorRegistry(listOf(TwitterIE(ExtractorHttp(JavaNetHttpTransfer()))))
            val preview = ExtractorMediaPreviewSource(registry).load(statusUrl!!)
            val ready = assertIs<MediaPreviewResult.Ready>(preview)
            val selected = ready.preview.videos.firstOrNull()
            assumeTrue("The live status listed no video", selected != null)

            val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString()))
            val engine = HttpDownloadEngine(
                transfer = JavaNetHttpTransfer(),
                fileStore = DesktopFileStore { root.toString() },
                settings = settings,
                scope = scope,
                ioDispatcher = Dispatchers.Default,
                registry = registry,
            )
            val job = engine.submit(
                DownloadRequest(sourceUrl = statusUrl, selectedMediaIds = listOf(selected!!.mediaId)),
            )
            val finished = withTimeout(120_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state.isTerminal } }
                    .first { it.id == job.id }
            }
            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertTrue(finished.artifacts.isNotEmpty())
            val file = root.resolve(finished.artifacts.first().relativePath)
            assertTrue(Files.exists(file) && Files.size(file) > 0, "the selected video must be on disk")
            println(
                "live x status: videos=${ready.preview.videos.size} " +
                    "selected=${finished.artifacts.size}",
            )
        } finally {
            scope.cancel()
        }
    }
}
