package com.anydownlod.ui.add

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.ui.App
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end clicks on the real add form. This is the headless version of the
 * T-028 desktop click-through.
 */
@OptIn(ExperimentalTestApi::class)
class AddFormUiTest {

    @Test
    fun typingAUrlAndClickingDownloadCreatesOneJob() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithTag("add-url-field").performTextInput("https://example.com/watch?v=fixture")
        onNodeWithTag("add-download-button").assertIsEnabled().performScrollTo().performClick()

        assertEquals(1, graph.engine.jobs.value.size)
        assertEquals(
            "https://example.com/watch?v=fixture",
            graph.engine.jobs.value.single().request.sourceUrl,
        )
    }

    @Test
    fun manualStartAndBatchWithOneBadLineThroughTheUi() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        // One URL, auto-start off: the job stays pending.
        onNodeWithTag("add-url-field").performTextInput("https://example.com/watch?v=manual")
        onNodeWithTag("add-autostart-switch").performClick()
        onNodeWithTag("add-download-button").assertIsEnabled().performScrollTo().performClick()
        assertEquals(1, graph.engine.jobs.value.size)
        assertEquals(JobState.PENDING, graph.engine.jobs.value.single().state)

        // Batch with one bad line: two good jobs, and the bad line stays visible.
        onNodeWithTag("add-url-field").performTextInput(
            "https://example.com/watch?v=one\nnot-a-url\nhttps://example.com/watch?v=two"
        )
        onNodeWithTag("add-download-button").assertIsEnabled().performScrollTo().performClick()
        assertEquals(3, graph.engine.jobs.value.size)
        onNodeWithText("not-a-url: Only http:// and https:// sources are supported.", substring = true)
            .assertExists()
    }

    @Test
    fun bothActionsAreDisabledWhileTheFieldIsEmpty() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithTag("add-download-button").performScrollTo().assertIsNotEnabled()
        onNodeWithTag("add-subscribe-button").assertIsNotEnabled()
    }

    @Test
    fun switchingMediaTypeShowsOnlyTheRelevantFields() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithText("Container profile").assertExists()
        onNodeWithText("Audio").performClick()
        onNodeWithText("Container").assertExists()
        onNodeWithText("Container profile").assertDoesNotExist()

        onNodeWithText("Captions").performClick()
        onNodeWithText("Caption language").assertExists()
        onNodeWithText("Container").assertDoesNotExist()
    }

    @Test
    fun cookieCheckboxAppearsOnlyWhenConfiguredAndSetsTheRequest() = runComposeUiTest {
        val settingsRepository = InMemorySettingsRepository(AppSettings(cookiesConfigured = true))
        val graph = InMemoryAppGraph(settings = settingsRepository)
        setContent { App(graph) }

        onNodeWithText("Use the configured cookie file").assertExists()
        onNodeWithText("Use the configured cookie file").performScrollTo().performClick()
        onNodeWithTag("add-url-field").performTextInput("https://example.com/watch?v=fixture")
        onNodeWithTag("add-download-button").assertIsEnabled().performScrollTo().performClick()

        assertTrue(graph.engine.jobs.value.single().request.options.useCookies)
    }

    @Test
    fun cookieCheckboxIsHiddenWhenNotConfigured() = runComposeUiTest {
        setContent { App(InMemoryAppGraph()) }

        onNodeWithText("Use the configured cookie file").assertDoesNotExist()
    }

    @Test
    fun subscribeCreatesOneSubscriptionAndIsDisabledForABatch() = runComposeUiTest {
        val graph = InMemoryAppGraph()
        setContent { App(graph) }

        onNodeWithTag("add-url-field").performTextInput("https://example.com/channel/fixture")
        onNodeWithTag("add-subscribe-button").assertIsEnabled().performScrollTo().performClick()
        assertEquals(1, graph.subscriptions.subscriptions.value.size)

        onNodeWithTag("add-url-field").performTextInput("\nhttps://example.com/channel/second")
        onNodeWithTag("add-subscribe-button").assertIsNotEnabled()
        assertEquals(1, graph.subscriptions.subscriptions.value.size)
    }
}
