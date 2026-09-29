package com.anydownlod.ui.queue

import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.ui.export.JobSourceUrls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueuePresenterTest {

    private fun engine() = InMemoryDownloadEngine(seedJobs = InMemoryDownloadEngine.sampleJobs())

    private fun List<DownloadJob>.byId(id: String): DownloadJob = first { it.id == id }

    @Test
    fun rowsKeepOnlyQueueStates() {
        val rows = QueuePresenter.rows(InMemoryDownloadEngine.sampleJobs())

        assertEquals(
            setOf(
                "seed-pending",
                "seed-scheduled",
                "seed-downloading",
                "seed-downloading-unknown-total",
                "seed-postprocessing",
            ),
            rows.map { it.id }.toSet(),
        )
        assertTrue(rows.none { it.state.isTerminal })
    }

    @Test
    fun nullPercentStaysIndeterminateAndOmitsSpeedAndEta() {
        val row = QueuePresenter.rows(InMemoryDownloadEngine.sampleJobs())
            .first { it.id == "seed-downloading-unknown-total" }

        assertNull(row.percent)
        assertTrue(row.indeterminate)
        assertNull(row.speedBytesPerSecond)
        assertNull(row.etaSeconds)
    }

    @Test
    fun knownPercentKeepsSpeedAndEta() {
        val row = QueuePresenter.rows(InMemoryDownloadEngine.sampleJobs())
            .first { it.id == "seed-downloading" }

        assertEquals(42.5, row.percent)
        assertFalse(row.indeterminate)
        assertEquals(1_200_000.0, row.speedBytesPerSecond)
        assertEquals(5L, row.etaSeconds)
    }

    @Test
    fun unknownStateIsNotAQueueRow() {
        val unknown = InMemoryDownloadEngine.sampleJobs().first()
            .copy(id = "future", state = JobState.UNKNOWN)

        assertTrue(QueuePresenter.rows(listOf(unknown)).isEmpty())
    }

    @Test
    fun startSelectedSkipsRowsAlreadyWorking() {
        val engine = engine()
        val presenter = QueuePresenter(engine)

        presenter.startSelected(setOf("seed-pending", "seed-downloading"))

        assertEquals(JobState.QUEUED, engine.jobs.value.byId("seed-pending").state)
        assertEquals(JobState.DOWNLOADING, engine.jobs.value.byId("seed-downloading").state)
    }

    @Test
    fun cancelSelectedOnlyCancelsLiveRows() {
        val engine = engine()
        val presenter = QueuePresenter(engine)

        presenter.cancelSelected(setOf("seed-downloading", "seed-completed"))

        assertEquals(JobState.CANCELLED, engine.jobs.value.byId("seed-downloading").state)
        assertEquals(JobState.COMPLETED, engine.jobs.value.byId("seed-completed").state)
    }

    @Test
    fun bulkActionsOperateOnKnownIdsOnlyAndDoNotRollBack() {
        val engine = engine()
        val presenter = QueuePresenter(engine)

        presenter.startSelected(setOf("seed-pending", "missing-id"))

        assertEquals(JobState.QUEUED, engine.jobs.value.byId("seed-pending").state)
        assertEquals(JobState.SCHEDULED, engine.jobs.value.byId("seed-scheduled").state)

        presenter.cancelSelected(setOf("seed-downloading", "missing-id", "seed-completed"))

        assertEquals(JobState.CANCELLED, engine.jobs.value.byId("seed-downloading").state)
        assertEquals(JobState.COMPLETED, engine.jobs.value.byId("seed-completed").state)
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
        val presenter = QueuePresenter(batchEngine())

        assertEquals(
            listOf("https://example.com/watch?v=batch-1", "https://example.com/watch?v=batch-3"),
            presenter.selectedUrls(setOf("batch-1", "batch-3", "missing")),
        )
        assertTrue(presenter.selectedUrls(emptySet()).isEmpty())
        assertEquals("", JobSourceUrls.text(presenter.selectedUrls(emptySet())))
    }

    @Test
    fun batchUrlsIncludeEveryChildAndExcludeJobsOutsideTheParent() {
        val presenter = QueuePresenter(batchEngine())

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
        val text = JobSourceUrls.text(presenter.batchUrls(setOf("batch-1")))
        assertTrue(text.none { it == '\r' || it == '\u0000' })
        for (forbidden in listOf("cookie", "signature", "googlevideo", "token=")) {
            assertTrue(forbidden !in text, "copied text must not carry '$forbidden'")
        }
    }
}
