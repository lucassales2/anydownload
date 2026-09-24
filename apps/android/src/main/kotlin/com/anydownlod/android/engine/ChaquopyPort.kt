package com.anydownlod.android.engine

import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode

/**
 * The only seam where Android touches Python.
 *
 * The real implementation (added behind this port when Chaquopy is applied to
 * this module) runs the **pinned** yt-dlp with argument lists, streams output
 * only as structured tokens, and never lets Python exceptions, stdout, or
 * media URLs reach job rows, logs, or the UI. Tests substitute a fake port, so
 * the routing and job lifecycle are verified without a Python runtime.
 */
interface ChaquopyPort {
    /**
     * False when the embedded runtime is missing (for example a build without
     * Chaquopy). The engine then fails site URLs with an honest
     * "engine unavailable" error and never pretends a download started.
     */
    val available: Boolean

    /**
     * Runs one pinned yt-dlp job and returns the redacted outcome. Must be
     * cooperative with cancellation ([kotlinx.coroutines.currentCoroutineContext
     * .ensureActive] at chunk boundaries).
     */
    suspend fun runDownload(request: DownloadRequest, downloadRoot: String): ChaquopyResult

    companion object {
        /**
         * The yt-dlp version pinned at build time (PyPI `2026.8.19`, verified
         * 2026-09-23). There is deliberately no in-app `yt-dlp -U`.
         */
        const val pinnedVersion = "2026.8.19"

        /** The honest outcome when the Chaquopy runtime is absent. */
        fun resultUnavailable(): ChaquopyResult = ChaquopyResult.Failed(
            code = JobErrorCode.ENGINE_UNAVAILABLE,
            message = "Chaquopy is not available in this build, so site URLs cannot download.",
            retryable = false,
        )
    }
}

/** Redacted outcome of one Python-backed download. */
sealed interface ChaquopyResult {
    /** A file was written inside [downloadRoot]. Path is relative to the root. */
    data class Finished(
        val relativePath: String,
        val sizeBytes: Long?,
    ) : ChaquopyResult

    /** Safe, mapped error. Raw Python output never crosses this boundary. */
    data class Failed(
        val code: JobErrorCode,
        val message: String,
        val retryable: Boolean = true,
    ) : ChaquopyResult
}