package com.anydownlod.ui.history

import com.anydownlod.core.domain.JobState
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.ui.i18n.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryPresenterTest {

    private fun engine() = InMemoryDownloadEngine(seedJobs = InMemoryDownloadEngine.sampleJobs())

    @Test
    fun rowsKeepTerminalAndUnknownStates() {
        val unknown = InMemoryDownloadEngine.sampleJobs().first()
            .copy(id = "future", state = JobState.UNKNOWN)
        val rows = HistoryPresenter.rows(InMemoryDownloadEngine.sampleJobs() + unknown)

        assertEquals(setOf("seed-completed", "seed-failed", "future"), rows.map { it.id }.toSet())
        assertEquals(UiText.raw("unknown"), rows.first { it.id == "future" }.stateLabel)
        assertTrue(rows.first { it.id == "seed-failed" }.canRetry)
        assertFalse(rows.first { it.id == "seed-completed" }.canRetry)
    }

    @Test
    fun retrySelectedOnlyRetriesFailedAndCancelled() {
        val engine = engine()
        val presenter = HistoryPresenter(engine)

        presenter.retrySelected(setOf("seed-failed", "seed-completed"))

        assertEquals(JobState.QUEUED, engine.jobs.value.first { it.id == "seed-failed" }.state)
        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == "seed-completed" }.state)
    }

    @Test
    fun removeSelectedDropsEveryRowAndKeepsArtifacts() {
        val engine = engine()
        val presenter = HistoryPresenter(engine)

        presenter.removeSelected(setOf("seed-failed", "seed-completed"))

        assertTrue(engine.jobs.value.none { it.id == "seed-failed" || it.id == "seed-completed" })
    }

    @Test
    fun removeHistoryAndDeleteArtifactsAreDifferentOperations() {
        val engine = engine()
        val presenter = HistoryPresenter(engine)

        assertTrue(presenter.remove("seed-failed"))
        assertTrue(engine.jobs.value.none { it.id == "seed-failed" })

        val result = presenter.deleteArtifacts("seed-completed")

        assertEquals(1, result.deletedCount)
        assertTrue(result.allDeleted)
        val completed = engine.jobs.value.first { it.id == "seed-completed" }
        assertEquals(JobState.COMPLETED, completed.state)
        assertTrue(completed.artifacts.single().removed)
    }
}
