package com.anydownlod.core

import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobState
import kotlinx.coroutines.flow.StateFlow

/**
 * The seam between the shared screens and whatever performs a download.
 *
 * D1 ships an in-memory fake for every host and, on desktop only, a yt-dlp
 * process adapter. Callers observe [jobs] and call the actions; they never
 * build engine arguments themselves.
 *
 * Implementations must keep these promises:
 * - Two [submit] calls with the same non-blank idempotency key return the
 *   original job.
 * - [submit] with [com.anydownlod.core.domain.StartPolicy.MANUAL] does not
 *   start work.
 * - Raw process output, signed URLs, and cookie material never enter a job.
 */
interface DownloadEngine {
    /** Every accepted job, newest revision per job. */
    val jobs: StateFlow<List<DownloadJob>>

    /** Accepts a request, returning the existing job for a repeated key. */
    fun submit(request: DownloadRequest): DownloadJob

    /** Queues a [JobState.PENDING] or [JobState.SCHEDULED] job. */
    fun start(jobId: String): DownloadJob?

    /** Cancels non-terminal work. Does not delete finished files. */
    fun cancel(jobId: String): DownloadJob?

    /** Appends a new attempt to a failed or cancelled job and queues it. */
    fun retry(jobId: String): DownloadJob?

    /**
     * Drops a job row from the in-app history. It never deletes a file.
     * Returns false for an unknown id.
     */
    fun removeHistory(jobId: String): Boolean

    /**
     * Deletes or marks the job's files under the download root. The history
     * row stays. Reports how many artifacts were removed and any file names
     * that failed; this never throws for a partial failure.
     */
    fun deleteArtifacts(jobId: String): ArtifactDeletionResult
}

/** Outcome of one delete-file request. File names are safe to show. */
data class ArtifactDeletionResult(
    val deletedCount: Int,
    val failures: List<String> = emptyList(),
) {
    val allDeleted: Boolean get() = failures.isEmpty()
}
