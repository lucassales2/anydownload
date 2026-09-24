package com.anydownlod.core.engine

import com.anydownlod.core.ArtifactDeletionResult
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.domain.Artifact
import com.anydownlod.core.domain.ArtifactKind
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobAttempt
import com.anydownlod.core.domain.JobError
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobProgress
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.extract.GenericExtraction
import com.anydownlod.core.extract.GenericExtractionFailure
import com.anydownlod.core.extract.GenericExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Web download engine: [WebExtensionBridge] fetches and saves; this class
 * owns the job rows and applies the same destination policy and content-type
 * classification as the other hosts.
 *
 * Without the extension ([WebExtensionBridge.available] false) a submit fails
 * with an honest [JobErrorCode.ENGINE_UNAVAILABLE] — Add never pretends a
 * download started and the page performs no cross-origin fetch. Non-direct
 * (HTML) URLs fail with the same typed [JobErrorCode.EXTRACTION_FAILURE] the
 * iOS host reports.
 */
class WebExtensionEngine(
    private val bridge: WebExtensionBridge,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val idGenerator: () -> String = { "web-${Random.nextLong().toULong().toString(16)}" },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    seedJobs: List<DownloadJob> = emptyList(),
) : DownloadEngine {

    private val _jobs = MutableStateFlow(seedJobs)
    override val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val idempotency = mutableMapOf<String, String>()
    private val cancelRequested = mutableMapOf<String, Boolean>()
    private val running = mutableMapOf<String, Job>()

    init {
        seedJobs.forEach { job ->
            if (job.request.idempotencyKey.isNotEmpty()) {
                idempotency[job.request.idempotencyKey] = job.id
            }
        }
    }

    override fun submit(request: DownloadRequest): DownloadJob {
        val key = request.idempotencyKey
        if (key.isNotEmpty()) {
            idempotency[key]?.let { existingId -> findJob(existingId)?.let { return it } }
        }
        return when (request.options.startPolicy) {
            StartPolicy.AUTOMATIC -> beginWork(acceptJob(request))
            StartPolicy.MANUAL -> update(acceptJob(request).id) {
                it.copy(state = JobState.PENDING)
            } ?: error("The accepted job disappeared.")
        }
    }

    override fun start(jobId: String): DownloadJob? {
        val job = findJob(jobId) ?: return null
        if (job.state != JobState.PENDING && job.state != JobState.SCHEDULED) return job
        return beginWork(job)
    }

    override fun cancel(jobId: String): DownloadJob? {
        val job = findJob(jobId) ?: return null
        if (job.state.isTerminal) return job
        cancelRequested[jobId] = true
        running.remove(jobId)?.cancel()
        scope.launch { bridge.cancelDownload(jobId) }
        return update(jobId) {
            it.copy(state = JobState.CANCELLED, error = cancelledError(), finishedAtEpochMillis = now())
        }
    }

    override fun retry(jobId: String): DownloadJob? {
        val job = findJob(jobId) ?: return null
        if (job.state != JobState.FAILED && job.state != JobState.CANCELLED) return job
        cancelRequested.remove(jobId)
        val at = now()
        val queued = update(jobId) {
            it.copy(
                state = JobState.QUEUED,
                progress = null,
                error = null,
                finishedAtEpochMillis = null,
                attempts = it.attempts + JobAttempt(
                    id = "attempt-${idGenerator()}",
                    jobId = jobId,
                    state = JobState.QUEUED,
                    startedAtEpochMillis = at,
                ),
            )
        } ?: return null
        launchJob(jobId)
        return queued
    }

    override fun removeHistory(jobId: String): Boolean {
        val job = findJob(jobId) ?: return false
        cancelRequested[jobId] = true
        running.remove(jobId)?.cancel()
        scope.launch { bridge.cancelDownload(jobId) }
        _jobs.value = _jobs.value.filterNot { it.id == job.id }
        idempotency.filterValues { it == job.id }.keys.toList().forEach { idempotency.remove(it) }
        return true
    }

    override fun deleteArtifacts(jobId: String): ArtifactDeletionResult {
        val job = findJob(jobId) ?: return ArtifactDeletionResult(deletedCount = 0)
        // Identifiers only; nothing is removed from the browser. The history
        // row stays, with the artifact marked removed.
        update(jobId) {
            it.copy(artifacts = it.artifacts.map { artifact -> artifact.copy(removed = true) })
        }
        return ArtifactDeletionResult(deletedCount = job.artifacts.count { !it.removed })
    }

    private fun acceptJob(request: DownloadRequest): DownloadJob {
        val at = now()
        val id = "job-${idGenerator()}"
        val accepted = DownloadJob(
            id = id,
            request = request,
            state = JobState.RESOLVING,
            revision = 1,
            createdAtEpochMillis = at,
            updatedAtEpochMillis = at,
            startedAtEpochMillis = at,
            sourceHost = UrlPolicy.hostOf(request.sourceUrl),
            attempts = listOf(
                JobAttempt(
                    id = "attempt-${idGenerator()}",
                    jobId = id,
                    state = JobState.RESOLVING,
                    startedAtEpochMillis = at,
                )
            ),
        )
        _jobs.value = _jobs.value + accepted
        if (request.idempotencyKey.isNotEmpty()) idempotency[request.idempotencyKey] = id
        return accepted
    }

    private fun beginWork(job: DownloadJob): DownloadJob {
        val queued = update(job.id) {
            it.copy(state = JobState.QUEUED, error = null, finishedAtEpochMillis = null)
        } ?: return job
        launchJob(job.id)
        return queued
    }

    private fun launchJob(jobId: String) {
        if (running.containsKey(jobId)) return
        val coroutine = scope.launch {
            try {
                runJob(jobId)
            } finally {
                running.remove(jobId)
            }
        }
        running[jobId] = coroutine
    }

    private suspend fun runJob(jobId: String) {
        if (cancelRequested[jobId] == true) {
            confirmCancelled(jobId)
            return
        }
        val url = findJob(jobId)?.request?.sourceUrl ?: return
        val mediaType = findJob(jobId)?.mediaType ?: MediaType.VIDEO
        if (!bridge.available) {
            fail(
                jobId,
                JobErrorCode.ENGINE_UNAVAILABLE,
                "The AnyDownload browser extension is required to download files on the web.",
                retryable = false,
            )
            return
        }
        when (val policy = UrlPolicy.check(url)) {
            is UrlCheck.Rejected -> {
                fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, redactedUrlReason(policy.reason))
                return
            }

            is UrlCheck.Allowed -> Unit
        }
        update(jobId, persistNow = false) {
            it.copy(state = JobState.DOWNLOADING, progress = JobProgress(phase = "downloading"))
        }
        try {
            when (val probe = withContext(ioDispatcher) { bridge.probe(url) }) {
                is WebProbe.Failed -> fail(jobId, mapFailure(probe.code, probe.message))

                is WebProbe.Final -> {
                    val finalUrl = probe.finalUrl
                    when (val policy = UrlPolicy.check(finalUrl)) {
                        is UrlCheck.Rejected -> {
                            fail(
                                jobId,
                                JobErrorCode.INVALID_URL_OPTIONS,
                                "The final address after redirects is local or reserved and was refused.",
                            )
                            return
                        }

                        is UrlCheck.Allowed -> Unit
                    }
                    if (probe.statusCode !in 200..299) {
                        val error = mapHttpStatus(probe.statusCode)
                        fail(jobId, error.code, error.message, error.retryable)
                        return
                    }
                    if (UrlClassifier.classify(probe.contentType) ==
                        UrlClassifier.Classification.NEEDS_EXTRACTOR
                    ) {
                        // T-050: the extension fetches the page, the shared
                        // Kotlin extractor (never a second JavaScript
                        // extractor) picks the one media URL, and the
                        // extension saves that file with the D2 path.
                        when (val page = withContext(ioDispatcher) { bridge.fetchPage(finalUrl) }) {
                            is WebPage.Failed -> fail(jobId, mapFailure(page.code, page.message))

                            is WebPage.Final -> {
                                val pageUrl = page.finalUrl
                                when (val policy = UrlPolicy.check(pageUrl)) {
                                    is UrlCheck.Rejected -> {
                                        fail(
                                            jobId,
                                            JobErrorCode.INVALID_URL_OPTIONS,
                                            "The final page address is local or reserved and was refused.",
                                        )
                                        return
                                    }

                                    is UrlCheck.Allowed -> Unit
                                }
                                when (
                                    val extraction = GenericExtractor.extract(
                                        pageUrl = pageUrl,
                                        html = page.html,
                                    )
                                ) {
                                    is GenericExtraction.Direct -> {
                                        downloadViaBridge(jobId, extraction.url, mediaType, totalBytes = null)
                                    }

                                    is GenericExtraction.Failed -> {
                                        fail(
                                            jobId,
                                            JobErrorCode.EXTRACTION_FAILURE,
                                            redactedExtractionMessage(extraction.reason),
                                            retryable = false,
                                        )
                                    }
                                }
                            }
                        }
                        return
                    }
                    downloadViaBridge(jobId, finalUrl, mediaType, probe.totalBytes)
                }
            }
        } catch (cancelled: CancellationException) {
            if (cancelRequested[jobId] == true) confirmCancelled(jobId) else throw cancelled
        } catch (failure: Throwable) {
            if (cancelRequested[jobId] == true) {
                confirmCancelled(jobId)
            } else {
                fail(jobId, JobErrorCode.NETWORK_FAILURE, "The download failed. Check the network and retry.")
            }
        }
    }

    /** The D2 save step: one validated URL handed to the extension downloader. */
    private suspend fun downloadViaBridge(jobId: String, url: String, mediaType: MediaType, totalBytes: Long?) {
        when (val policy = UrlPolicy.check(url)) {
            is UrlCheck.Rejected -> {
                fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, redactedUrlReason(policy.reason))
                return
            }

            is UrlCheck.Allowed -> Unit
        }
        val outcome = withContext(ioDispatcher) {
            bridge.download(url, jobId) { downloaded, total ->
                update(jobId, persistNow = false) {
                    it.copy(
                        progress = JobProgress(
                            phase = "downloading",
                            percent = total?.takeIf { total != 0L }
                                ?.let { (downloaded.toDouble() / it) * 100.0 },
                            downloadedBytes = downloaded,
                            totalBytes = total,
                        ),
                    )
                }
            }
        }
        currentCoroutineContext().ensureActive()
        when (outcome) {
            is WebDownload.Completed -> complete(jobId, outcome, mediaType, totalBytes)
            is WebDownload.Failed -> fail(jobId, mapFailure(outcome.code, outcome.message, outcome.retryable))
            is WebDownload.Aborted -> if (cancelRequested[jobId] == true) {
                confirmCancelled(jobId)
            } else {
                fail(jobId, JobErrorCode.NETWORK_FAILURE, "The download stopped before finishing.")
            }
        }
    }

    private fun redactedExtractionMessage(reason: GenericExtractionFailure): String = when (reason) {
        GenericExtractionFailure.UnsupportedPageUrl -> "The page URL is not an HTTP(S) address this app can read."
        GenericExtractionFailure.NoMedia -> "This page has no media this app can download."
        GenericExtractionFailure.MultipleMedia -> "This page has more than one media element, so the app cannot choose one."
    }

    private fun complete(jobId: String, outcome: WebDownload.Completed, mediaType: MediaType, probeTotal: Long?) {
        val artifact = Artifact(
            id = "artifact-${idGenerator()}",
            jobId = jobId,
            kind = artifactKind(mediaType, outcome.fileName),
            fileName = outcome.fileName,
            relativePath = outcome.fileName,
            sizeBytes = outcome.sizeBytes,
        )
        update(jobId) {
            it.copy(
                state = JobState.COMPLETED,
                progress = JobProgress(
                    phase = "completed",
                    percent = 100.0,
                    downloadedBytes = outcome.sizeBytes,
                    totalBytes = probeTotal,
                ),
                error = null,
                finishedAtEpochMillis = now(),
                artifacts = it.artifacts + artifact,
            )
        }
    }

    private fun mapFailure(code: WebFailureCode, message: String, retryable: Boolean = true): JobError =
        when (code) {
            WebFailureCode.PERMISSION -> JobError(
                JobErrorCode.ENGINE_UNAVAILABLE,
                "The extension does not have permission to fetch this site.",
                retryable = false,
            )

            WebFailureCode.BLOCKED_DESTINATION -> JobError(
                JobErrorCode.INVALID_URL_OPTIONS,
                "This URL points to a local or reserved address and was refused.",
                retryable = false,
            )

            WebFailureCode.TIMEOUT,
            WebFailureCode.NETWORK,
            WebFailureCode.OTHER,
            -> JobError(JobErrorCode.NETWORK_FAILURE, message, retryable)
        }

    private fun mapHttpStatus(status: Int): JobError = when (status) {
        401 -> JobError(
            JobErrorCode.LOGIN_REQUIRED,
            "This source needs a sign-in. Add cookies or pick another source.",
            retryable = false,
        )

        403 -> JobError(JobErrorCode.UNAVAILABLE_OR_PRIVATE, "This source refused the download.", retryable = false)
        404, 410, 451 -> JobError(
            JobErrorCode.UNAVAILABLE_OR_PRIVATE,
            "This file is unavailable or was removed.",
            retryable = false,
        )

        429 -> JobError(JobErrorCode.RATE_LIMITED, "The source rate-limited this download. Try again later.", retryable = true)
        in 500..599 -> JobError(JobErrorCode.NETWORK_FAILURE, "The source failed temporarily. Try again.", retryable = true)
        else -> JobError(JobErrorCode.NETWORK_FAILURE, "The source returned an unexpected response.", retryable = true)
    }

    private fun redactedUrlReason(reason: UrlRejectReason): String = when (reason) {
        UrlRejectReason.UnsupportedScheme -> "Only http and https source URLs are supported."
        UrlRejectReason.MissingHost -> "This URL has no host."
        UrlRejectReason.UserInfo -> "This URL embeds credentials and was refused."
        UrlRejectReason.BlockedDestination -> "This URL points to a local or reserved address and was refused."
        UrlRejectReason.Malformed -> "This URL is malformed."
    }

    private fun cancelledError(): JobError =
        JobError(JobErrorCode.CANCELLED, "The download was cancelled.", retryable = false)

    private fun artifactKind(mediaType: MediaType, fileName: String): ArtifactKind {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") ||
                lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".opus") ||
                lower.endsWith(".ogg") -> ArtifactKind.AUDIO
            lower.endsWith(".srt") || lower.endsWith(".vtt") || lower.endsWith(".ttml") ||
                lower.endsWith(".sbv") -> ArtifactKind.CAPTIONS
            lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
                lower.endsWith(".mov") || lower.endsWith(".avi") -> ArtifactKind.VIDEO
            else -> when (mediaType) {
                MediaType.VIDEO -> ArtifactKind.VIDEO
                MediaType.AUDIO -> ArtifactKind.AUDIO
                MediaType.CAPTIONS -> ArtifactKind.CAPTIONS
                MediaType.THUMBNAIL -> ArtifactKind.THUMBNAIL
            }
        }
    }

    private fun findJob(jobId: String): DownloadJob? = _jobs.value.firstOrNull { it.id == jobId }

    private fun update(
        jobId: String,
        persistNow: Boolean = true,
        transform: (DownloadJob) -> DownloadJob,
    ): DownloadJob? {
        val current = _jobs.value.firstOrNull { it.id == jobId } ?: return null
        val transformed = transform(current).copy(
            revision = current.revision + 1,
            updatedAtEpochMillis = now(),
        )
        val attempts = if (transformed.attempts.isEmpty()) {
            listOf(
                JobAttempt(
                    id = "attempt-${idGenerator()}",
                    jobId = jobId,
                    state = transformed.state,
                    progress = transformed.progress,
                    error = transformed.error,
                    startedAtEpochMillis = transformed.startedAtEpochMillis,
                    finishedAtEpochMillis = transformed.finishedAtEpochMillis,
                )
            )
        } else {
            transformed.attempts.dropLast(1) + transformed.attempts.last().copy(
                state = transformed.state,
                progress = transformed.progress,
                error = transformed.error,
                finishedAtEpochMillis = transformed.finishedAtEpochMillis,
            )
        }
        val updated = transformed.copy(attempts = attempts)
        _jobs.value = _jobs.value.map { if (it.id == jobId) updated else it }
        return updated
    }

    private fun fail(jobId: String, code: JobErrorCode, message: String, retryable: Boolean = true): DownloadJob? =
        update(jobId) {
            it.copy(
                state = JobState.FAILED,
                error = JobError(code, message, retryable),
                finishedAtEpochMillis = now(),
            )
        }

    private fun fail(jobId: String, error: JobError): DownloadJob? =
        update(jobId) {
            it.copy(
                state = JobState.FAILED,
                error = error,
                finishedAtEpochMillis = now(),
            )
        }

    private fun confirmCancelled(jobId: String): DownloadJob? =
        update(jobId) {
            it.copy(
                state = JobState.CANCELLED,
                error = cancelledError(),
                finishedAtEpochMillis = now(),
            )
        }
}