package com.anydownlod.ui.queue

import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.JobState
import com.anydownlod.ui.export.JobSourceUrls

/**
 * Actions for the Downloading list. Starting or cancelling is always the
 * engine's job; this class exists so the rules (only pending rows start, bulk
 * actions skip rows that are already working) are unit-testable.
 */
class QueuePresenter(private val engine: DownloadEngine) {

    fun start(jobId: String): Boolean = engine.start(jobId) != null

    fun cancel(jobId: String): Boolean = engine.cancel(jobId) != null

    fun startSelected(ids: Set<String>) {
        engine.jobs.value
            .filter { it.id in ids && (it.state == JobState.PENDING || it.state == JobState.SCHEDULED) }
            .forEach { engine.start(it.id) }
    }

    fun cancelSelected(ids: Set<String>) {
        engine.jobs.value
            .filter { it.id in ids && !it.state.isTerminal && it.state != JobState.UNKNOWN }
            .forEach { engine.cancel(it.id) }
    }

    /** Source URLs of the selected rows, in row order (T-109). */
    fun selectedUrls(ids: Set<String>): List<String> = JobSourceUrls.forSelected(engine.jobs.value, ids)

    /** Source URLs of every child of the batches the selection names (T-109). */
    fun batchUrls(ids: Set<String>): List<String> = JobSourceUrls.forBatches(engine.jobs.value, ids)

    companion object {
        /**
         * Pure mapping from the engine flow to rows. A future `UNKNOWN` state
         * goes to the Completed list so it stays visible rather than sticking
         * in the queue.
         */
        fun rows(jobs: List<DownloadJob>): List<QueueRow> =
            jobs.filter { !it.state.isTerminal && it.state != JobState.UNKNOWN }
                .map { it.toQueueRow() }
    }
}
