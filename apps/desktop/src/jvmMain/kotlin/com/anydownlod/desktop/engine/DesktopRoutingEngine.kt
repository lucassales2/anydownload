package com.anydownlod.desktop.engine

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
 * Desktop router between the shared [HttpDownloadEngine] (direct files) and
 * the D1 [YtDlpCliEngine] (everything else, platform sites and missing-tool
 * behavior included).
 *
 * Every job is created by exactly one engine, so the merged [jobs] flow has
 * no duplicates and every action routes to the engine that owns the id.
 * Direct-file jobs never touch `ProcessBuilder`; the router decides before
 * either engine starts work.
 */
class DesktopRoutingEngine(
    private val http: HttpDownloadEngine,
    private val cli: YtDlpCliEngine,
    private val classify: (String) -> DesktopRoute,
    scope: CoroutineScope,
) : DownloadEngine {

    override val jobs: StateFlow<List<DownloadJob>> =
        combine(cli.jobs, http.jobs) { cliJobs, httpJobs -> cliJobs + httpJobs }
            .stateIn(scope, SharingStarted.Eagerly, cli.jobs.value + http.jobs.value)

    override fun submit(request: DownloadRequest): DownloadJob =
        when (classify(request.sourceUrl)) {
            DesktopRoute.DIRECT_FILE -> http.submit(request)
            DesktopRoute.KOTLIN -> http.submit(request)
            DesktopRoute.YTDLP_CLI -> cli.submit(request)
        }

    override fun start(jobId: String): DownloadJob? = owner(jobId)?.start(jobId)

    override fun cancel(jobId: String): DownloadJob? = owner(jobId)?.cancel(jobId)

    override fun retry(jobId: String): DownloadJob? = owner(jobId)?.retry(jobId)

    override fun removeHistory(jobId: String): Boolean = owner(jobId)?.removeHistory(jobId) ?: false

    override fun deleteArtifacts(jobId: String): ArtifactDeletionResult =
        owner(jobId)?.deleteArtifacts(jobId) ?: ArtifactDeletionResult(deletedCount = 0)

    private fun owner(jobId: String): DownloadEngine? = when {
        http.jobs.value.any { it.id == jobId } -> http
        cli.jobs.value.any { it.id == jobId } -> cli
        else -> null
    }
}