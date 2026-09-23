package com.anydownlod.ui.history

import com.anydownlod.core.ArtifactDeletionResult
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.JobState

/**
 * Actions for the Completed list. Removing history, deleting a file, and
 * retrying stay three different engine calls.
 */
class HistoryPresenter(private val engine: DownloadEngine) {

    fun retry(jobId: String): Boolean = engine.retry(jobId) != null

    fun retrySelected(ids: Set<String>) {
        engine.jobs.value
            .filter { it.id in ids && (it.state == JobState.FAILED || it.state == JobState.CANCELLED) }
            .forEach { engine.retry(it.id) }
    }

    /** Drops the row only; the file on disk is untouched. */
    fun remove(jobId: String): Boolean = engine.removeHistory(jobId)

    fun removeSelected(ids: Set<String>) {
        ids.forEach { engine.removeHistory(it) }
    }

    /** Deletes the artifacts under the download root; the row stays. */
    fun deleteArtifacts(jobId: String): ArtifactDeletionResult = engine.deleteArtifacts(jobId)

    companion object {
        /**
         * Terminal jobs plus `UNKNOWN`, so a state written by a newer engine
         * is shown with its raw label instead of disappearing.
         */
        fun rows(jobs: List<DownloadJob>): List<HistoryRow> =
            jobs.filter { it.state.isTerminal || it.state == JobState.UNKNOWN }
                .map { it.toHistoryRow() }
    }
}
