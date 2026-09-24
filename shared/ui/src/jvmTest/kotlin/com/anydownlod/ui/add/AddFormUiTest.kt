package com.anydownlod.ui.add

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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.VideoContainerProfile
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.ui.App
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end clicks on the T-052 link-only home. This is the headless version
 * of the desktop click-through: the idle screen is the link field alone, with
 * no clipboard dialog, no clipboard banner, and no add-form option controls.
 */
@OptIn(ExperimentalTestApi::class)
class AddFormUiTest {

    @Test
    fun idleScreenShowsOnlyTheLinkField() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithTag("add-url-field").assertExists()
        onNodeWithTag("add-download-button").assertIsNotEnabled()

        // No clipboard dialog, no clipboard banner, no add-form option controls.
        onNodeWithText("Check the clipboard for links?").assertDoesNotExist()
        onNodeWithTag("clipboard-suggestion").assertDoesNotExist()
        onNodeWithTag("add-autostart-switch").assertDoesNotExist()
        onNodeWithTag("add-advanced-toggle").assertDoesNotExist()
        onNodeWithText("Media type").assertDoesNotExist()
        onNodeWithText("Delivery").assertDoesNotExist()
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
        onNodeWithTag("preview-edit-media-audio").performClick()
        onNodeWithTag("preview-edit-quality-192").performClick()
        onNodeWithTag("preview-download").performClick()

        val job = graph.engine.jobs.value.single()
        assertEquals(MediaType.AUDIO, job.request.options.mediaType)
        assertEquals(AudioContainer.M4A, job.request.options.audioContainer)
        assertEquals("192", job.request.options.audioBitrate)
    }

    @Test
    fun invalidInputStaysOnTheFieldWithoutJobsOrPreview() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithTag("add-url-field").performTextInput("not-a-url")
        onNodeWithTag("add-download-button").assertIsEnabled().performClick()
        onNodeWithText("Only http:// and https:// sources are supported.").assertExists()
        onNodeWithText("Preview title").assertDoesNotExist()
        assertTrue(graph.engine.jobs.value.isEmpty())

        // A userinfo URL embeds credentials and stays on the field too.
        onNodeWithTag("add-url-field").performTextReplacement("https://user:pass@example.com/watch")
        onNodeWithTag("add-download-button").assertIsEnabled().performClick()
        onNodeWithText("This URL embeds credentials and was refused.").assertExists()
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
}

private class FakeClipboard(initial: String?) : ClipboardManager {
    private var text: String? = initial

    override fun getText(): AnnotatedString? = text?.let(::AnnotatedString)

    override fun setText(annotatedString: AnnotatedString) {
        text = annotatedString.text
    }
}