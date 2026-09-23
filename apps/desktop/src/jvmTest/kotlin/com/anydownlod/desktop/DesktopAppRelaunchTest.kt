package com.anydownlod.desktop

import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.desktop.engine.BlockingCliProcess
import com.anydownlod.desktop.engine.CliProcessRunner
import com.anydownlod.desktop.engine.FakeCliProcess
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The relaunch flow from the T-032/T-033 verification, minus the window:
 * create state, "close" or kill the host, then reopen from the same directory.
 */
class DesktopAppRelaunchTest {

    private fun tempDir(): Path = Files.createTempDirectory("anydownlod-relaunch-")

    @Test
    fun pendingJobFolderSubscriptionAndPresetSurviveRelaunch() {
        val directory = tempDir()
        val first = DesktopApp.open(stateDirectory = directory, defaultDownloadRoot = { "/default" })

        val job = first.graph.engine.submit(
            DownloadRequest(
                sourceUrl = "https://example.com/watch?v=fixture",
                options = DownloadOptions(startPolicy = StartPolicy.MANUAL),
            ),
        )
        assertEquals(JobState.PENDING, job.state)
        first.graph.settings.update { it.copy(downloadRoot = "/tmp/chosen-folder") }
        first.graph.subscriptions.add(
            sourceUrl = "https://example.com/channel/fixture",
            displayName = "Fixture",
            downloadOptions = DownloadOptions(),
        )
        first.graph.settings.addPreset("Small", mapOf("writeMetadata" to "true"))
        first.close()

        val second = DesktopApp.open(stateDirectory = directory, defaultDownloadRoot = { "/default" })

        val reloaded = second.graph.engine.jobs.value.single()
        assertEquals(JobState.PENDING, reloaded.state)
        assertEquals("https://example.com/watch?v=fixture", reloaded.request.sourceUrl)
        assertEquals("/tmp/chosen-folder", second.graph.settings.settings.value.downloadRoot)
        assertEquals(
            listOf("Fixture"),
            second.graph.subscriptions.subscriptions.value.map { it.displayName },
        )
        assertEquals(
            listOf("Small"),
            second.graph.settings.settings.value.presets.map { it.name },
        )
    }

    @Test
    fun completedDownloadSurvivesRelaunchWithItsArtifact() = runBlocking {
        val directory = tempDir()
        val downloadRoot = Files.createTempDirectory("anydownlod-relaunch-complete-")
        val file = downloadRoot.resolve("done.mp4")
        val runner = CliProcessRunner { command, _ ->
            if (command.contains("--flat-playlist")) {
                FakeCliProcess(emptyList())
            } else {
                Files.writeString(file, "media")
                FakeCliProcess(listOf("TITLE|Done", "FILE|$file"))
            }
        }
        val first = DesktopApp.open(
            stateDirectory = directory,
            defaultDownloadRoot = { downloadRoot.toString() },
            processRunner = runner,
            resolveExecutable = { "/fake/yt-dlp" },
        )
        val job = first.graph.engine.submit(DownloadRequest("https://example.com/watch?v=fixture"))
        withTimeout(5_000) {
            first.graph.engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.COMPLETED } }
        }
        first.close()

        val second = DesktopApp.open(
            stateDirectory = directory,
            defaultDownloadRoot = { downloadRoot.toString() },
            processRunner = CliProcessRunner { _, _ -> FakeCliProcess(emptyList()) },
            resolveExecutable = { "/fake/yt-dlp" },
        )

        val reloaded = second.graph.engine.jobs.value.single()
        assertEquals(JobState.COMPLETED, reloaded.state)
        assertEquals(1, reloaded.artifacts.size)
        assertTrue(Files.exists(downloadRoot.resolve(reloaded.artifacts.single().relativePath)))
    }

    @Test
    fun interruptedActiveJobBecomesRetryableOnRelaunch() = runBlocking {
        val directory = tempDir()
        val downloadRoot = Files.createTempDirectory("anydownlod-relaunch-downloads-")
        val blocking = BlockingCliProcess()
        val first = DesktopApp.open(
            stateDirectory = directory,
            defaultDownloadRoot = { downloadRoot.toString() },
            processRunner = CliProcessRunner { command, _ ->
                if (command.contains("--flat-playlist")) FakeCliProcess(emptyList()) else blocking
            },
            resolveExecutable = { "/fake/yt-dlp" },
        )
        val job = first.graph.engine.submit(
            DownloadRequest(
                sourceUrl = "https://example.com/watch?v=fixture",
                options = DownloadOptions(startPolicy = StartPolicy.AUTOMATIC),
            ),
        )
        withTimeout(5_000) {
            first.graph.engine.jobs.first { jobs ->
                jobs.any { it.id == job.id && it.state == JobState.DOWNLOADING }
            }
        }
        // No close(): simulate the app being killed while the job was active.

        val second = DesktopApp.open(
            stateDirectory = directory,
            defaultDownloadRoot = { downloadRoot.toString() },
            processRunner = CliProcessRunner { _, _ -> BlockingCliProcess() },
            resolveExecutable = { "/fake/yt-dlp" },
        )

        val reloaded = second.graph.engine.jobs.value.single()
        assertEquals(JobState.FAILED, reloaded.state)
        assertEquals(true, reloaded.error?.retryable)
        assertTrue(reloaded.error!!.message.contains("closed"))

        blocking.destroyTree()
    }
}
