package com.anydownlod.ui.history

import com.anydownlod.core.domain.JobState
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.ui.export.JobSourceUrls
import com.anydownlod.ui.i18n.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryViewModelTest {

    private fun engine() = InMemoryDownloadEngine(seedJobs = InMemoryDownloadEngine.sampleJobs())

    @Test
    fun rowsKeepTerminalAndUnknownStates() {
        val unknown = InMemoryDownloadEngine.sampleJobs().first()
            .copy(id = "future", state = JobState.UNKNOWN)
        val rows = HistoryViewModel.rows(InMemoryDownloadEngine.sampleJobs() + unknown)

        assertEquals(setOf("seed-completed", "seed-failed", "future"), rows.map { it.id }.toSet())
        assertEquals(UiText.raw("unknown"), rows.first { it.id == "future" }.stateLabel)
        assertTrue(rows.first { it.id == "seed-failed" }.canRetry)
        assertFalse(rows.first { it.id == "seed-completed" }.canRetry)
    }

    @Test
    fun retrySelectedOnlyRetriesFailedAndCancelled() {
        val engine = engine()
        val presenter = HistoryViewModel(engine)

        presenter.retrySelected(setOf("seed-failed", "seed-completed"))

        assertEquals(JobState.QUEUED, engine.jobs.value.first { it.id == "seed-failed" }.state)
        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == "seed-completed" }.state)
    }

    @Test
    fun removeSelectedDropsEveryRowAndKeepsArtifacts() {
        val engine = engine()
        val presenter = HistoryViewModel(engine)

        presenter.removeSelected(setOf("seed-failed", "seed-completed"))

        assertTrue(engine.jobs.value.none { it.id == "seed-failed" || it.id == "seed-completed" })
    }

    @Test
    fun removeHistoryAndDeleteArtifactsAreDifferentOperations() {
        val engine = engine()
        val presenter = HistoryViewModel(engine)

        assertTrue(presenter.remove("seed-failed"))
        assertTrue(engine.jobs.value.none { it.id == "seed-failed" })

        val result = presenter.deleteArtifacts("seed-completed")

        assertEquals(1, result.deletedCount)
        assertTrue(result.allDeleted)
        val completed = engine.jobs.value.first { it.id == "seed-completed" }
        assertEquals(JobState.COMPLETED, completed.state)
        assertTrue(completed.artifacts.single().removed)
    }

    private fun batchEngine(): InMemoryDownloadEngine {
        val base = InMemoryDownloadEngine.sampleJobs().first { it.id == "seed-completed" }
        fun child(id: String, state: JobState = JobState.COMPLETED, batch: String? = "batch-a") = base.copy(
            id = id,
            state = state,
            parentBatchId = batch,
            request = base.request.copy(sourceUrl = "https://example.com/watch?v=$id"),
        )
        return InMemoryDownloadEngine(
            seedJobs = listOf(
                child("batch-1"),
                child("batch-2"),
                child("batch-3", state = JobState.FAILED),
                child("other-1", batch = null),
            ),
        )
    }

    @Test
    fun selectedUrlsReturnOnePerSelectedJobAndIncludeFailures() {
        val presenter = HistoryViewModel(batchEngine())

        assertEquals(
            listOf("https://example.com/watch?v=batch-1", "https://example.com/watch?v=batch-3"),
            presenter.selectedUrls(setOf("batch-1", "batch-3", "missing")),
        )
        assertTrue(presenter.selectedUrls(emptySet()).isEmpty())
        assertEquals("", JobSourceUrls.text(presenter.selectedUrls(emptySet())))
    }

    @Test
    fun batchUrlsIncludeEveryChildAndExcludeJobsOutsideTheParent() {
        val presenter = HistoryViewModel(batchEngine())

        assertEquals(
            listOf(
                "https://example.com/watch?v=batch-1",
                "https://example.com/watch?v=batch-2",
                "https://example.com/watch?v=batch-3",
            ),
            presenter.batchUrls(setOf("batch-2")),
        )
        assertTrue(presenter.batchUrls(setOf("other-1")).isEmpty())
        assertTrue("other-1" !in JobSourceUrls.text(presenter.batchUrls(setOf("batch-1"))))
    }
}
