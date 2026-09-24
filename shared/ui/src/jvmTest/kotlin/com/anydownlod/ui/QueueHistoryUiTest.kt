package com.anydownlod.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.AppGraph
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.fake.InMemoryAppGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Headless click-through of the Downloading and Completed lists. This stands
 * in for the T-029 desktop click-through where the host cannot send OS input.
 */
@OptIn(ExperimentalTestApi::class)
class QueueHistoryUiTest {

    private fun InMemoryAppGraph.job(id: String): DownloadJob = engine.jobs.value.first { it.id == id }

    private fun InMemoryAppGraph.jobOrNull(id: String): DownloadJob? = engine.jobs.value.firstOrNull { it.id == id }

    @Test
    fun startCancelRetryRemoveAndDeleteThroughTheUi() = runComposeUiTest {
        val graph = InMemoryAppGraph.seeded()
        setContent { ShellUiHarness(graph) }

        // Downloading: Start only exists on the pending row.
        onNodeWithTag("queue-list").performScrollToNode(hasTestTag("queue-start-seed-pending"))
        onNodeWithTag("queue-start-seed-pending").performClick()
        assertEquals(JobState.QUEUED, graph.job("seed-pending").state)

        // Cancelling live work asks first and never claims to delete a file.
        onNodeWithTag("queue-list").performScrollToNode(hasTestTag("queue-cancel-seed-downloading"))
        onNodeWithTag("queue-cancel-seed-downloading").performClick()
        onNodeWithText("This stops the download. It does not delete a finished file.").assertExists()
        onNodeWithTag("queue-confirm-cancel").performClick()
        assertEquals(JobState.CANCELLED, graph.job("seed-downloading").state)

        // Completed: retry the failed row.
        onNodeWithText("Completed").performClick()
        onNodeWithTag("history-list").performScrollToNode(hasTestTag("history-retry-seed-failed"))
        onNodeWithTag("history-retry-seed-failed").performClick()
        assertEquals(JobState.QUEUED, graph.job("seed-failed").state)

        // Delete the completed file (row stays), then remove the row.
        onNodeWithTag("history-list").performScrollToNode(hasTestTag("history-delete-seed-completed"))
        onNodeWithTag("history-delete-seed-completed").performClick()
        onNodeWithText("Delete this file from the download folder?").assertExists()
        onNodeWithTag("history-confirm-delete").performClick()
        assertTrue(graph.job("seed-completed").artifacts.single().removed)

        onNodeWithTag("history-list").performScrollToNode(hasTestTag("history-remove-seed-completed"))
        onNodeWithTag("history-remove-seed-completed").performClick()
        onNodeWithText("Remove this entry from history?").assertExists()
        onNodeWithTag("history-confirm-remove").performClick()
        assertNull(graph.jobOrNull("seed-completed"))
    }

    @Test
    fun bulkStartAndBulkRemoveOperateOnTheSelection() = runComposeUiTest {
        val graph = InMemoryAppGraph.seeded()
        setContent { ShellUiHarness(graph) }

        // Select two startable rows, then use the bulk action.
        onNodeWithTag("queue-list").performScrollToNode(hasTestTag("queue-select-seed-pending"))
        onNodeWithTag("queue-select-seed-pending").performClick()
        onNodeWithTag("queue-list").performScrollToNode(hasTestTag("queue-select-seed-scheduled"))
        onNodeWithTag("queue-select-seed-scheduled").performClick()
        onNodeWithTag("queue-start-selected").performClick()
        assertEquals(JobState.QUEUED, graph.job("seed-pending").state)
        assertEquals(JobState.QUEUED, graph.job("seed-scheduled").state)

        // Select-all from a partial selection checks every row.
        onNodeWithTag("queue-select-all").performClick()
        onNodeWithTag("queue-list").performScrollToNode(hasTestTag("queue-select-seed-downloading"))
        onNodeWithTag("queue-select-seed-downloading").assertIsOn()

        // Bulk remove two history rows after one confirm.
        onNodeWithText("Completed").performClick()
        onNodeWithTag("history-list").performScrollToNode(hasTestTag("history-select-seed-completed"))
        onNodeWithTag("history-select-seed-completed").performClick()
        onNodeWithTag("history-list").performScrollToNode(hasTestTag("history-select-seed-failed"))
        onNodeWithTag("history-select-seed-failed").performClick()
        onNodeWithTag("history-remove-selected").performClick()
        onNodeWithTag("history-confirm-remove").performClick()
        assertNull(graph.jobOrNull("seed-completed"))
        assertNull(graph.jobOrNull("seed-failed"))
    }

    @Test
    fun openSourceCallsTheHostCallback() = runComposeUiTest {
        val base = InMemoryAppGraph.seeded()
        var opened: String? = null
        val graph = object : AppGraph by base {
            override val openUrl: (String) -> Unit = { opened = it }
        }
        setContent { ShellUiHarness(graph) }

        onNodeWithTag("queue-list").performScrollToNode(hasTestTag("queue-open-seed-downloading"))
        onNodeWithTag("queue-open-seed-downloading").performClick()

        assertEquals("https://example.com/watch?v=seed-downloading", opened)
    }
}
