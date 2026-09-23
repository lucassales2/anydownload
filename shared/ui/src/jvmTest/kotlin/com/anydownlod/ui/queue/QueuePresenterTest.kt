package com.anydownlod.ui.queue

import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.fake.InMemoryDownloadEngine
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
}
