package com.anydownlod.ui.queue

import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.JobState
import com.anydownlod.ui.shell.displayLabel

/**
 * One row of the Downloading list, already shaped for rendering.
 */
data class QueueRow(
    val id: String,
    val title: String,
    val sourceUrl: String,
    val sourceHost: String?,
    val state: JobState,
    val stateLabel: String,
    val phase: String?,
    val percent: Double?,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val speedBytesPerSecond: Double?,
    val etaSeconds: Long?,
    val scheduledAtEpochMillis: Long?,
    val canStart: Boolean,
    val cancelNeedsConfirm: Boolean,
) {
    /** Null percent means an indeterminate bar, never an invented 0%. */
    val indeterminate: Boolean get() = percent == null
}

fun DownloadJob.toQueueRow(): QueueRow = QueueRow(
    id = id,
    title = title ?: request.sourceUrl,
    sourceUrl = request.sourceUrl,
    sourceHost = sourceHost,
    state = state,
    stateLabel = state.displayLabel(),
    phase = progress?.phase,
    percent = progress?.percent,
    downloadedBytes = progress?.downloadedBytes,
    totalBytes = progress?.totalBytes,
    speedBytesPerSecond = progress?.speedBytesPerSecond,
    etaSeconds = progress?.etaSeconds,
    scheduledAtEpochMillis = scheduledAtEpochMillis,
    canStart = state == JobState.PENDING || state == JobState.SCHEDULED,
    cancelNeedsConfirm = state == JobState.DOWNLOADING || state == JobState.POSTPROCESSING,
)
