package com.anydownlod.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Headless click-through of the Subscriptions table. It stands in for the
 * T-030 desktop click-through where the host cannot send OS input.
 */
@OptIn(ExperimentalTestApi::class)
class SubscriptionsUiTest {

    @Test
    fun checkEditPauseAndDeleteThroughTheUi() = runComposeUiTest {
        val repository = InMemorySubscriptionRepository()
        val subscription = repository.add(
            sourceUrl = "https://example.com/channel/fixture",
            displayName = "Fixture channel",
            downloadOptions = DownloadOptions(),
        )
        val graph = InMemoryAppGraph(subscriptions = repository)
        setContent { ShellUiHarness(graph) }

        onNodeWithText("Subscriptions").performClick()
        onNodeWithText("Fixture channel").assertExists()

        // Check now records a timestamp and never enqueues a job.
        onNodeWithTag("subscriptions-check-${subscription.id}").performClick()
        assertNotNull(repository.subscriptions.value.single().lastCheckedAtEpochMillis)
        assertTrue(graph.engine.jobs.value.isEmpty())

        // Pause, then resume from the same button.
        onNodeWithTag("subscriptions-pause-${subscription.id}").performClick()
        assertTrue(repository.subscriptions.value.single().paused)
        onNodeWithTag("subscriptions-pause-${subscription.id}").performClick()
        assertFalse(repository.subscriptions.value.single().paused)

        // An invalid regex stays in the dialog; a valid one saves.
        onNodeWithTag("subscriptions-edit-${subscription.id}").performClick()
        onNodeWithTag("subscriptions-edit-filter").performTextReplacement("[")
        onNodeWithTag("subscriptions-edit-save").performClick()
        onNodeWithText("The title filter is not a valid regular expression.").assertExists()
        assertEquals("", repository.subscriptions.value.single().titleFilterRegex)

        onNodeWithTag("subscriptions-edit-filter").performTextReplacement("fixture")
        onNodeWithTag("subscriptions-edit-save").performClick()
        assertEquals("fixture", repository.subscriptions.value.single().titleFilterRegex)
        assertEquals(DownloadOptions(), repository.subscriptions.value.single().downloadOptions)

        // Delete confirms the scope and leaves the row's jobs alone.
        onNodeWithTag("subscriptions-delete-${subscription.id}").performClick()
        onNodeWithText("Downloads already in the queue or history stay. Future checks stop.").assertExists()
        onNodeWithTag("subscriptions-confirm-delete").performClick()
        assertTrue(repository.subscriptions.value.isEmpty())
    }

    @Test
    fun checkSelectedRecordsOnlyTheSelectedRows() = runComposeUiTest {
        val repository = InMemorySubscriptionRepository()
        val first = repository.add("https://example.com/channel/one", "One", DownloadOptions())
        val second = repository.add("https://example.com/channel/two", "Two", DownloadOptions())
        setContent { ShellUiHarness(InMemoryAppGraph(subscriptions = repository)) }

        onNodeWithText("Subscriptions").performClick()
        onNodeWithTag("subscriptions-list").performScrollToNode(hasTestTag("subscriptions-select-${first.id}"))
        onNodeWithTag("subscriptions-select-${first.id}").performClick()
        onNodeWithTag("subscriptions-check-selected").performClick()

        val stored = repository.subscriptions.value
        assertNotNull(stored.first { it.id == first.id }.lastCheckedAtEpochMillis)
        assertNull(stored.first { it.id == second.id }.lastCheckedAtEpochMillis)
    }

    @Test
    fun emptyStateExplainsChecksRunWhileOpen() = runComposeUiTest {
        setContent { ShellUiHarness(InMemoryAppGraph()) }

        onNodeWithText("Subscriptions").performClick()
        onNodeWithText("Checks run while the app is open.", substring = true).assertExists()
    }
}
