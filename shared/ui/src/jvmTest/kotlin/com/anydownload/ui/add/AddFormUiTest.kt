package com.anydownload.ui.add

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import com.anydownload.core.AppGraph
import com.anydownload.core.domain.AudioContainer
import com.anydownload.core.domain.MediaType
import com.anydownload.core.domain.QualityPreference
import com.anydownload.core.domain.VideoContainerProfile
import com.anydownload.core.postprocess.ToolkitCapabilities
import com.anydownload.core.fake.InMemoryAppGraph
import com.anydownload.ui.App
import com.anydownload.ui.ShellUiHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end clicks on the home library. The idle screen keeps the link field
 * and shows one download list at a time, with no clipboard dialog, no
 * clipboard banner, and no add-form option controls.
 */
@OptIn(ExperimentalTestApi::class)
class AddFormUiTest {

    @Test
    fun idleScreenShowsTheLinkFieldAndTheDownloadLists() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithTag("add-url-field").assertExists()
        onNodeWithTag("add-download-button").assertIsNotEnabled()
        onNodeWithTag("add-subscribe-button").assertExists()

        onNodeWithText("All").assertExists()
        onNodeWithText("No downloads yet").assertExists()
        onNodeWithText("Complete").performClick()
        onNodeWithText("No finished downloads").assertExists()
        onNodeWithText("Failed").performClick()
        onNodeWithText("No failed downloads").assertExists()
        onNodeWithText("Subscriptions").performClick()
        onNodeWithText("No subscriptions").assertExists()

        // No clipboard dialog, no clipboard banner, no add-form option controls.
        onNodeWithText("Check the clipboard for links?").assertDoesNotExist()
        onNodeWithTag("clipboard-suggestion").assertDoesNotExist()
        onNodeWithTag("add-autostart-switch").assertDoesNotExist()
        onNodeWithTag("add-advanced-toggle").assertDoesNotExist()
        onNodeWithText("Media type").assertDoesNotExist()
        onNodeWithText("Delivery").assertDoesNotExist()
    }

    @Test
    fun clickingTheLatestDownloadOpensItsPreview() = runComposeUiTest {
        val graph = InMemoryAppGraph.seeded()
        val before = graph.engine.jobs.value.size
        setContent { App(graph) }

        onNodeWithTag("latest-download").performClick()

        onNodeWithTag("preview-title").assertTextEquals("Preview title")
        onNodeWithTag("preview-back").assertExists()
        onNodeWithTag("add-url-field").assertTextEquals("https://example.com/watch?v=seed-completed")
        assertEquals(before, graph.engine.jobs.value.size)
    }

    @Test
    fun typingAUrlAndClickingDownloadOpensThePreview() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithTag("add-url-field").performTextInput("https://example.com/watch?v=fixture")
        onNodeWithTag("add-download-button").assertIsEnabled().performClick()

        // T-053: the preview opens and nothing downloads yet.
        onNodeWithText("Preview title").assertExists()
        assertEquals(0, graph.engine.jobs.value.size)
        onNodeWithTag("preview-download").performClick()

        assertEquals(1, graph.engine.jobs.value.size)
        val job = graph.engine.jobs.value.single()
        assertEquals(
            "https://example.com/watch?v=fixture",
            job.request.sourceUrl,
        )
        // The panel was never opened: Download used the allowlist defaults.
        assertEquals(MediaType.VIDEO, job.request.options.mediaType)
        assertEquals(QualityPreference.Best, job.request.options.quality)
        assertEquals(VideoContainerProfile.AUTO, job.request.options.videoProfile)
    }

    @Test
    fun editPanelCollapsesAndExpandsOnThePreview() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithTag("add-url-field").performTextInput("https://example.com/watch?v=edit")
        onNodeWithTag("add-download-button").performClick()
        onNodeWithText("Preview title").assertExists()

        // Collapsed on open: only Download and the Edit download toggle.
        onNodeWithTag("preview-edit-panel").assertDoesNotExist()
        onNodeWithText("Edit download").assertExists()

        // Expand reveals media type (video or audio), quality, and format.
        onNodeWithTag("preview-edit-toggle").performClick()
        onNodeWithTag("preview-edit-panel").assertExists()
        onNodeWithText("Media type").assertExists()
        onNodeWithTag("preview-edit-media-video").assertIsSelected()
        onNodeWithText("Quality preference").assertExists()
        onNodeWithText("Format").assertExists()

        // Switching to audio keeps the same three controls.
        onNodeWithTag("preview-edit-media-audio").performClick()
        onNodeWithText("M4A").assertExists()
        onNodeWithText("MP3").assertExists()
        onNodeWithText("Quality preference").assertExists()

        // Out of scope: no captions, cookies, clips, or custom yt-dlp JSON.
        onNodeWithText("Caption language").assertDoesNotExist()
        onNodeWithText("Use the configured cookie file").assertDoesNotExist()
        onNodeWithText("Custom options").assertDoesNotExist()

        // Collapsing hides the choices again.
        onNodeWithText("Hide edit").assertExists()
        onNodeWithTag("preview-edit-toggle").performClick()
        onNodeWithTag("preview-edit-panel").assertDoesNotExist()
        onNodeWithText("Media type").assertDoesNotExist()
    }

    @Test
    fun downloadUsesTheEditedAudioChoices() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithTag("add-url-field").performTextInput("https://example.com/watch?v=edit")
        onNodeWithTag("add-download-button").performClick()
        onNodeWithTag("preview-edit-toggle").performClick()
        onNodeWithTag("preview-edit-media-audio").performScrollTo().performClick()
        onNodeWithTag("preview-edit-quality-192").performScrollTo().performClick()
        onNodeWithTag("preview-download").performScrollTo().performClick()

        val job = graph.engine.jobs.value.single()
        assertEquals(MediaType.AUDIO, job.request.options.mediaType)
        assertEquals(AudioContainer.M4A, job.request.options.audioContainer)
        assertEquals("192", job.request.options.audioBitrate)
    }

    @Test
    fun invalidInputStaysOnTheFieldWithoutJobsOrPreview() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        // A userinfo URL embeds credentials and stays on the field.
        onNodeWithTag("add-url-field").performTextInput("https://user:pass@example.com/watch")
        onNodeWithTag("add-download-button").assertIsEnabled().performClick()
        onNodeWithText("This URL embeds credentials and was refused.").assertExists()
        assertTrue(graph.engine.jobs.value.isEmpty())

        // Plain text is a Spotify search now; the idle field opens the preview
        // instead of reporting a scheme error.
        onNodeWithTag("add-url-field").performTextReplacement("not-a-url")
        onNodeWithTag("add-download-button").assertIsEnabled().performClick()
        onNodeWithText("Preview title").assertExists()
        assertTrue(graph.engine.jobs.value.isEmpty())
    }

    @Test
    fun moreThanOneLineStaysOnTheFieldWithoutJobs() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        // The D3 home submits one URL; a multi-line paste is refused, not batched.
        onNodeWithTag("add-url-field").performTextInput(
            "https://example.com/watch?v=one\nhttps://example.com/watch?v=two"
        )
        onNodeWithTag("add-download-button").assertIsEnabled().performClick()
        onNodeWithText("Enter one source URL.").assertExists()
        onNodeWithText("Preview title").assertDoesNotExist()
        assertTrue(graph.engine.jobs.value.isEmpty())
    }

    @Test
    fun pasteButtonFillsTheFieldAndDownloadCreatesAJob() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent {
            CompositionLocalProvider(
                LocalClipboardManager provides FakeClipboard("https://example.com/watch?v=pasted"),
            ) {
                App(graph)
            }
        }

        onNodeWithTag("add-download-button").assertIsNotEnabled()
        onNodeWithTag("add-paste-button").performClick()
        onNodeWithTag("add-url-field").assertTextEquals("https://example.com/watch?v=pasted")
        onNodeWithTag("add-download-button").assertIsEnabled().performClick()
        onNodeWithTag("preview-download").performClick()

        assertEquals(1, graph.engine.jobs.value.size)
        assertEquals(
            "https://example.com/watch?v=pasted",
            graph.engine.jobs.value.single().request.sourceUrl,
        )
    }

    @Test
    fun pasteButtonReportsAnEmptyClipboard() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalClipboardManager provides FakeClipboard(null)) {
                App(InMemoryAppGraph())
            }
        }

        onNodeWithTag("add-paste-button").performClick()
        onNodeWithText("Clipboard is empty.").assertExists()
        onNodeWithTag("add-download-button").assertIsNotEnabled()
    }

    @Test
    fun advancedAudioContainersFollowDesktopCapabilities() = runComposeUiTest {
        val base = InMemoryAppGraph()
        val desktop = object : AppGraph by base {
            override val toolkitCapabilities: ToolkitCapabilities = ToolkitCapabilities(
                canMerge = true,
                audioContainers = setOf(
                    AudioContainer.M4A,
                    AudioContainer.OPUS,
                    AudioContainer.MP3,
                    AudioContainer.WAV,
                    AudioContainer.FLAC,
                ),
            )
        }
        setContent { ShellUiHarness(desktop) }

        onNodeWithText("Audio").performScrollTo().performClick()
        onNodeWithTag("add-audio-mp3").assertIsEnabled()
        onNodeWithTag("add-audio-wav").assertIsEnabled()
        onNodeWithTag("add-audio-flac").assertIsEnabled()
    }

    @Test
    fun advancedAudioContainersFollowMobileCapabilities() = runComposeUiTest {
        val base = InMemoryAppGraph()
        val mobile = object : AppGraph by base {
            override val toolkitCapabilities: ToolkitCapabilities = ToolkitCapabilities(
                canMerge = true,
                audioContainers = setOf(AudioContainer.M4A, AudioContainer.OPUS),
            )
        }
        setContent { ShellUiHarness(mobile) }

        onNodeWithText("Audio").performScrollTo().performClick()
        onNodeWithTag("add-audio-m4a").assertIsEnabled()
        onNodeWithTag("add-audio-opus").assertIsEnabled()
        onNodeWithTag("add-audio-mp3").assertIsNotEnabled()
        onNodeWithTag("add-audio-wav").assertIsNotEnabled()
        onNodeWithTag("add-audio-flac").assertIsNotEnabled()
        onNodeWithText("This host cannot write MP3", substring = true).assertExists()
    }

    @Test
    fun advancedAudioContainersAreAllDisabledOnWeb() = runComposeUiTest {
        setContent { ShellUiHarness(InMemoryAppGraph()) }

        onNodeWithText("Audio").performScrollTo().performClick()
        AudioContainer.entries.forEach { container ->
            onNodeWithTag("add-audio-${container.wireName}").assertIsNotEnabled()
        }
        onNodeWithText("This host cannot write FLAC", substring = true).assertExists()
    }
}

private class FakeClipboard(initial: String?) : ClipboardManager {
    private var text: String? = initial

    override fun getText(): AnnotatedString? = text?.let(::AnnotatedString)

    override fun setText(annotatedString: AnnotatedString) {
        text = annotatedString.text
    }
}
