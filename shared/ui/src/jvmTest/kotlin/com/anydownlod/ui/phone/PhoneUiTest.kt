package com.anydownlod.ui.phone

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.ui.App
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PhoneUiTest {

    @Test
    fun welcomeOpensHomeAndTheDownloadList() = runComposeUiTest {
        setContent { App(InMemoryAppGraph(), phone = true) }

        onNodeWithText("Video Downloader").assertExists()
        onNodeWithText("Paste your link here or auto-detect").assertExists()
        onNodeWithTag("add-url-field").assertExists()
        onNodeWithText("All Videos Download").assertDoesNotExist()
        onNodeWithText("TikTok").assertDoesNotExist()
        onNodeWithText("Premium Features").assertDoesNotExist()
        onNodeWithText("View all").performClick()

        onNodeWithText("Downloads").assertExists()
        onNodeWithText("All").assertExists()
        onNodeWithText("No downloads yet").assertExists()
        onNodeWithText("Complete").performClick()
        onNodeWithText("No finished downloads").assertExists()
        onNodeWithText("Failed").performClick()
        onNodeWithText("No failed downloads").assertExists()
        onNodeWithTag("phone-back").performClick()
        onNodeWithText("Video Downloader").assertExists()
    }

    @Test
    fun typingAUrlAndDownloadingOpensThePreviewThenQueuesTheJob() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph, phone = true) }

        onNodeWithTag("add-url-field").performTextInput("https://example.com/watch?v=fixture")
        onNodeWithTag("phone-field-download").performClick()

        onNodeWithTag("preview-title").assertExists()
        assertEquals(0, graph.engine.jobs.value.size)

        onNodeWithTag("add-download-button").assertIsEnabled()
        onNodeWithTag("phone-field-download").performClick()
        assertEquals(1, graph.engine.jobs.value.size)
        assertEquals(
            "https://example.com/watch?v=fixture",
            graph.engine.jobs.value.single().request.sourceUrl,
        )
        onNodeWithTag("phone-downloads").assertExists()
    }

    @Test
    fun invalidInputStaysOnTheHomeField() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph, phone = true) }

        onNodeWithTag("add-url-field").performTextInput("https://user:pass@example.com/watch")
        onNodeWithTag("phone-field-download").performClick()

        onNodeWithText("This URL embeds credentials and was refused.").assertExists()
        assertTrue(graph.engine.jobs.value.isEmpty())
        onNodeWithTag("phone-home").assertExists()
    }
}
