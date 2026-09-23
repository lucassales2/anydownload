package com.anydownlod.ui.history

import com.anydownlod.core.domain.Artifact
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.JobState
import com.anydownlod.ui.i18n.UiText
import com.anydownlod.ui.shell.displayLabel

/** One artifact line on a history row, with the original for host callbacks. */
data class HistoryArtifact(
    val id: String,
    val fileName: String,
    val sizeBytes: Long?,
    val removed: Boolean,
    val source: Artifact,
)

/** One row of the Completed list, already shaped for rendering. */
data class HistoryRow(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val state: JobState,
    val stateLabel: UiText,
    val errorMessage: String?,
    val canRetry: Boolean,
    val artifacts: List<HistoryArtifact>,
) {
    val hasArtifact: Boolean get() = artifacts.any { !it.removed }
}

fun DownloadJob.toHistoryRow(): HistoryRow = HistoryRow(
    id = id,
    title = title ?: request.sourceUrl,
    sourceUrl = request.sourceUrl,
    state = state,
    stateLabel = state.displayLabel(),
    errorMessage = error?.message,
    canRetry = state == JobState.FAILED || state == JobState.CANCELLED,
    artifacts = artifacts.map { it.toHistoryArtifact() },
)

private fun Artifact.toHistoryArtifact(): HistoryArtifact = HistoryArtifact(
    id = id,
    fileName = fileName,
    sizeBytes = sizeBytes,
    removed = removed,
    source = this,
)
