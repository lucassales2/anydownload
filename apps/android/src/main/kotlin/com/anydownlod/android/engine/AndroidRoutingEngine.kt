package com.anydownlod.android.engine

import com.anydownlod.core.ArtifactDeletionResult
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.engine.HttpDownloadEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Android router between the shared [HttpDownloadEngine] (direct files) and
 * the [ChaquopyEngine] (other URLs). Every job is created by exactly one
 * engine, so the merged [jobs] flow has no duplicates and each action routes
 * to the owning engine.
 */
class AndroidRoutingEngine(
    private val http: HttpDownloadEngine,
    private val chaquopy: ChaquopyEngine,
    private val classify: (String) -> AndroidRoute,
    scope: CoroutineScope,
) : DownloadEngine {

    override val jobs: StateFlow<List<DownloadJob>> =
        combine(chaquopy.jobs, http.jobs) { chaquopyJobs, httpJobs -> chaquopyJobs + httpJobs }
            .stateIn(scope, SharingStarted.Eagerly, chaquopy.jobs.value + http.jobs.value)

    override fun submit(request: DownloadRequest): DownloadJob =
        when (classify(request.sourceUrl)) {
            AndroidRoute.DIRECT_FILE -> http.submit(request)
            AndroidRoute.KOTLIN -> http.submit(request)
            AndroidRoute.CHAQUOPY -> chaquopy.submit(request)
        }

    override fun start(jobId: String): DownloadJob? = owner(jobId)?.start(jobId)

    override fun cancel(jobId: String): DownloadJob? = owner(jobId)?.cancel(jobId)

    override fun retry(jobId: String): DownloadJob? = owner(jobId)?.retry(jobId)

    override fun removeHistory(jobId: String): Boolean = owner(jobId)?.removeHistory(jobId) ?: false

    override fun deleteArtifacts(jobId: String): ArtifactDeletionResult =
        owner(jobId)?.deleteArtifacts(jobId) ?: ArtifactDeletionResult(deletedCount = 0)

    private fun owner(jobId: String): DownloadEngine? = when {
        http.jobs.value.any { it.id == jobId } -> http
        chaquopy.jobs.value.any { it.id == jobId } -> chaquopy
        else -> null
    }
}