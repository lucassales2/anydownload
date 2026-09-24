package com.anydownlod.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.desktop.engine.CliProcessRunner
import com.anydownlod.desktop.engine.ExecutableOnPath
import com.anydownlod.desktop.store.defaultDownloadRootPath
import com.anydownlod.desktop.store.defaultStateDirectory
import com.anydownlod.desktop.subscriptions.DesktopSubscriptionRepository
import com.anydownlod.ui.App
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A per-user Windows install whose PATH has neither yt-dlp nor ffmpeg.
 * The window host is the same [DesktopApp] the Windows .exe launches.
 */
class FreshWindowsInstallWithoutYtDlpTest {

    private val missingTool = "yt-dlp was not found on PATH. Install it, then retry."

    @Test
    fun windowsStateLivesInAppDataAndDownloadsStayUnderTheUserProfile() {
        val home = Path.of("C:/Users/Ada")
        val roaming = Path.of("C:/Users/Ada/AppData/Roaming")

        assertEquals(
            roaming.resolve("AnyDownload"),
            defaultStateDirectory(osName = "Windows 11", home = home, appData = roaming.toString()),
        )
        assertEquals(
            home.resolve("AnyDownload"),
            defaultStateDirectory(osName = "Windows 11", home = home, appData = null),
        )
        assertEquals(
            home.resolve("Downloads").resolve("AnyDownload").toString(),
            defaultDownloadRootPath(home),
        )
    }

    @Test
    fun freshInstallOpensAndFailsDownloadAndSubscriptionWithoutStartingAProcess() = runBlocking {
        val home = Files.createTempDirectory("anydownlod-win-home")
        val roaming = Files.createTempDirectory("anydownlod-win-roaming")
        val path = Files.createTempDirectory("anydownlod-win-path")
        var started = false
        val runner = CliProcessRunner { _, _ ->
            started = true
            error("yt-dlp must not start when it is not installed")
        }
        val stateDirectory = defaultStateDirectory(
            osName = "Windows 11",
            home = home,
            appData = roaming.toString(),
        )

        val app = openFreshInstall(stateDirectory, home, path, runner)
        try {
            val tools = app.graph.toolProbe.probe()
            assertFalse(tools.ytDlp.available)
            assertFalse(tools.ffmpeg.available)
            assertEquals(null, tools.ytDlp.version)
            assertEquals(defaultDownloadRootPath(home), app.graph.settings.settings.value.downloadRoot)
            assertEquals(null, app.graph.startupWarning)

            val job = app.graph.engine.submit(
                DownloadRequest(sourceUrl = "https://example.com/watch?v=fresh-windows"),
            )
            assertEquals(JobState.FAILED, job.state)
            assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, job.error?.code)
            assertEquals(missingTool, job.error?.message)
            assertTrue(app.graph.engine.jobs.value.none { !it.state.isTerminal })

            val subscription = app.graph.subscriptions.add(
                sourceUrl = "https://example.com/channel/fresh",
                displayName = "Fresh channel",
                downloadOptions = DownloadOptions(),
            )
            val repository = app.graph.subscriptions as DesktopSubscriptionRepository
            repository.runCheck(subscription.id)
            val recorded = app.graph.subscriptions.subscriptions.value.single()
            assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, recorded.lastError?.code)
            assertEquals(missingTool, recorded.lastError?.message)
            assertFalse(started)
        } finally {
            app.close()
        }

        assertTrue(Files.isRegularFile(stateDirectory.resolve("settings.json")))
        assertTrue(Files.isRegularFile(stateDirectory.resolve("jobs.json")))
        val jobs = Files.readString(stateDirectory.resolve("jobs.json"))
        assertTrue(jobs.contains(missingTool))

        val reopened = openFreshInstall(stateDirectory, home, path, runner)
        try {
            val job = reopened.graph.engine.jobs.value.single()
            assertEquals(JobState.FAILED, job.state)
            assertEquals(missingTool, job.error?.message)
            assertFalse(reopened.graph.toolProbe.probe().ytDlp.available)
            assertFalse(started)
        } finally {
            reopened.close()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingsShowsNotFoundAndTheFailedDownloadLandsInHistory() = runComposeUiTest {
        val home = Files.createTempDirectory("anydownlod-win-ui-home")
        val roaming = Files.createTempDirectory("anydownlod-win-ui-roaming")
        val path = Files.createTempDirectory("anydownlod-win-ui-path")
        var started = false
        val app = openFreshInstall(
            stateDirectory = defaultStateDirectory(
                osName = "Windows 11",
                home = home,
                appData = roaming.toString(),
            ),
            home = home,
            path = path,
            runner = CliProcessRunner { _, _ ->
                started = true
                error("yt-dlp must not start when it is not installed")
            },
        )
        try {
            setContent { App(app.graph) }

            onNodeWithText("Settings").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Not found").fetchSemanticsNodes().size == 2
            }
            onNodeWithTag("settings-tool-ytdlp").assertExists()
            onNodeWithTag("settings-tool-ffmpeg").assertExists()
            onNodeWithText("Versions come from the local PATH probe. yt-dlp and ffmpeg are needed for site and video URLs; direct file downloads work without them.")
                .assertExists()
            onNodeWithTag("settings-close").performClick()

            onNodeWithTag("add-url-field").performTextInput("https://example.com/watch?v=fresh-windows")
            onNodeWithTag("add-download-button").performScrollTo().performClick()
            onNodeWithText("A preview could not be loaded on this device. You can still start the download.")
                .assertExists()
            onNodeWithTag("preview-download").performClick()

            assertEquals(JobState.FAILED, app.graph.engine.jobs.value.single().state)
            onNodeWithText("Added 1 job: 1 started, 0 waiting to start.").assertExists()
            onNodeWithText("Nothing is downloading").assertExists()

            onNodeWithText("Completed").performClick()
            onNodeWithText(missingTool).assertExists()
            onNodeWithText("Failed").assertExists()
            assertFalse(started)
        } finally {
            app.close()
        }
    }

    private fun openFreshInstall(
        stateDirectory: Path,
        home: Path,
        path: Path,
        runner: CliProcessRunner,
    ): DesktopApp = DesktopApp.open(
        stateDirectory = stateDirectory,
        defaultDownloadRoot = { defaultDownloadRootPath(home) },
        processRunner = runner,
        resolveExecutable = { name ->
            ExecutableOnPath.findOnPath(name, path.toString(), windows = true)
        },
    )
}
