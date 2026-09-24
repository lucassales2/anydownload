package com.anydownlod.core.engine

import com.anydownlod.core.ArtifactDeletionResult
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.SettingsRepository
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
import com.anydownlod.core.platform.FileHandle
import com.anydownlod.core.platform.FileStore
import com.anydownlod.core.platform.HttpBody
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.platform.engineCriticalSection
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Shared engine for one direct HTTP(S) file download on every host.
 *
 * This class owns job state and policy; [HttpTransfer] and [FileStore] own
 * transport and bytes (T-039 wires per-target actuals). It never spawns a
 * process, never calls Python, and never reads the disabled free-form
 * yt-dlp JSON field ([DownloadRequest.options] carries only allowlisted
 * values).
 *
 * URLs that would need an extractor (an HTML response) fail with a typed
 * [JobErrorCode.EXTRACTION_FAILURE] so the UI can map it, instead of
 * pretending a download started. Progress reports bytes exactly as received;
 * percent only follows Content-Length. Speed and ETA stay null: unknown stays
 * unknown.
 */
class HttpDownloadEngine(
    private val transfer: HttpTransfer,
    private val fileStore: FileStore,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val idGenerator: () -> String = { "local-${Random.nextLong().toULong().toString(16)}" },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val persist: (List<DownloadJob>) -> Unit = {},
    private val maxRedirects: Int = UrlPolicy.MAX_REDIRECTS,
    private val chunkSize: Int = 64 * 1024,
    /**
     * Destination policy for the submitted URL and every redirect hop.
     * Defaults to [UrlPolicy]. Tests keep a fixture exception strictly local
     * (for example exactly one loopback mock-server origin); hosts must never
     * weaken the default.
     */
    private val urlCheck: (String) -> UrlCheck = UrlPolicy::check,
    seedJobs: List<DownloadJob> = emptyList(),
) : DownloadEngine {

    private val lock = Any()
    private val _jobs = MutableStateFlow(seedJobs)
    override val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val idempotency = mutableMapOf<String, String>()
    private val running = mutableMapOf<String, Job>()
    private val cancelRequested = mutableMapOf<String, Boolean>()
    private val semaphore = Semaphore(settings.settings.value.maxConcurrentDownloads.coerceAtLeast(1))

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
            val existing = engineCriticalSection(lock) {
                idempotency[key]?.let { existingId -> findJob(existingId) }
            }
            existing?.let { return it }
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
        engineCriticalSection(lock) {
            cancelRequested[jobId] = true
            running.remove(jobId)?.cancel()
        }
        return update(jobId) {
            it.copy(state = JobState.CANCELLED, error = cancelledError(), finishedAtEpochMillis = now())
        }
    }

    override fun retry(jobId: String): DownloadJob? {
        val job = findJob(jobId) ?: return null
        if (job.state != JobState.FAILED && job.state != JobState.CANCELLED) return job
        engineCriticalSection(lock) { cancelRequested.remove(jobId) }
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
        engineCriticalSection(lock) {
            cancelRequested[jobId] = true
            running.remove(jobId)?.cancel()
            _jobs.value = _jobs.value.filterNot { it.id == job.id }
            idempotency.filterValues { it == job.id }.keys.toList().forEach { idempotency.remove(it) }
            persist(_jobs.value)
        }
        return true
    }

    override fun deleteArtifacts(jobId: String): ArtifactDeletionResult {
        val job = findJob(jobId) ?: return ArtifactDeletionResult(deletedCount = 0)
        var deleted = 0
        val failures = mutableListOf<String>()
        val updated = job.artifacts.map { artifact ->
            if (artifact.removed) return@map artifact
            val ok = artifact.relativePath.isNotBlank() &&
                runCatching { fileStore.delete(artifact.relativePath) }.getOrDefault(false)
            if (ok) deleted++ else failures += artifact.fileName
            artifact.copy(removed = true)
        }
        update(jobId) { it.copy(artifacts = updated) }
        return ArtifactDeletionResult(deletedCount = deleted, failures = failures)
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
        engineCriticalSection(lock) {
            _jobs.value = _jobs.value + accepted
            if (request.idempotencyKey.isNotEmpty()) idempotency[request.idempotencyKey] = id
            persist(_jobs.value)
        }
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
        engineCriticalSection(lock) {
            if (running.containsKey(jobId)) return@engineCriticalSection
            val coroutine = scope.launch {
                try {
                    runJob(jobId)
                } finally {
                    engineCriticalSection(lock) { running.remove(jobId) }
                }
            }
            running[jobId] = coroutine
        }
    }

    private suspend fun runJob(jobId: String) {
        if (isCancelRequested(jobId)) {
            confirmCancelled(jobId)
            return
        }
        if (settings.settings.value.downloadRoot.isBlank()) {
            fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "Choose a download folder in Settings first.")
            return
        }
        semaphore.withPermit {
            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
                return@withPermit
            }
            val url = findJob(jobId)?.request?.sourceUrl ?: return@withPermit
            val options = findJob(jobId)?.request?.options ?: DownloadOptions()
            update(jobId, persistNow = false) {
                it.copy(state = JobState.DOWNLOADING, progress = JobProgress(phase = "downloading"))
            }
            try {
                downloadDirectFile(jobId, url, options)
            } catch (cancelled: CancellationException) {
                if (isCancelRequested(jobId)) confirmCancelled(jobId) else throw cancelled
            } catch (failure: Throwable) {
                if (isCancelRequested(jobId)) {
                    confirmCancelled(jobId)
                } else {
                    fail(jobId, JobErrorCode.NETWORK_FAILURE, "The download failed. Check the network and retry.")
                }
            }
        }
    }

    private suspend fun downloadDirectFile(jobId: String, url: String, options: DownloadOptions) {
        var current = url
        var hops = 0
        var body: HttpBody? = null
        try {
            while (true) {
                when (val policy = urlCheck(current)) {
                    is UrlCheck.Rejected -> {
                        fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, redactedUrlReason(policy.reason))
                        return
                    }

                    is UrlCheck.Allowed -> Unit
                }

                val response = withContext(ioDispatcher) { transfer.execute(current) }
                when (response) {
                    is HttpResponse.Unavailable -> {
                        fail(
                            jobId,
                            JobErrorCode.ENGINE_UNAVAILABLE,
                            "A required download tool is missing on this device.",
                            retryable = false,
                        )
                        return
                    }

                    is HttpResponse.Redirect -> {
                        if (response.location.isBlank()) {
                            fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source redirected without a destination.")
                            return
                        }
                        hops++
                        if (hops > maxRedirects) {
                            fail(jobId, JobErrorCode.NETWORK_FAILURE, "Too many redirects.")
                            return
                        }
                        current = response.location
                        currentCoroutineContext().ensureActive()
                    }

                    is HttpResponse.Final -> {
                        body = response.body
                        if (response.statusCode !in 200..299) {
                            body?.close()
                            body = null
                            val error = mapHttpStatus(response.statusCode)
                            fail(jobId, error.code, error.message, error.retryable)
                            return
                        }
                        when (UrlClassifier.classify(response.contentType)) {
                            UrlClassifier.Classification.NEEDS_EXTRACTOR -> {
                                body?.close()
                                body = null
                                fail(
                                    jobId,
                                    JobErrorCode.EXTRACTION_FAILURE,
                                    "This URL needs an extractor, which this build does not include.",
                                    retryable = false,
                                )
                                return
                            }

                            UrlClassifier.Classification.DIRECT_FILE -> {
                                val fileBody = body
                                if (fileBody == null) {
                                    fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source returned an empty response body.")
                                    return
                                }
                                body = null // ownership passes to the streaming step
                                streamToFile(jobId, fileBody, response.totalBytes, options, current)
                                return
                            }
                        }
                    }
                }
            }
        } finally {
            runCatching { body?.close() }
        }
    }

    private suspend fun streamToFile(
        jobId: String,
        body: HttpBody,
        totalBytes: Long?,
        options: DownloadOptions,
        sourceUrl: String,
    ) {
        val handle = runCatching { fileStore.createTempFile() }.getOrNull()
        if (handle == null) {
            runCatching { body.close() }
            fail(jobId, JobErrorCode.DISK_EXHAUSTED, "The download folder could not be created.")
            return
        }

        var downloaded = 0L
        var published: String? = null
        try {
            val buffer = ByteArray(chunkSize)
            while (!isCancelRequested(jobId)) {
                val count = withContext(ioDispatcher) { body.readNext(buffer) }
                if (count == -1) break
                if (count == 0) continue
                handle.write(buffer, count)
                downloaded += count
                reportProgress(jobId, downloaded, totalBytes)
                currentCoroutineContext().ensureActive()
            }
            currentCoroutineContext().ensureActive()

            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
                return
            }

            handle.close()
            val relativePath = try {
                ArtifactName.build(sourceUrl, options)
            } catch (failure: IllegalArgumentException) {
                fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
                return
            }

            published = try {
                fileStore.publish(handle, relativePath)
            } catch (failure: Throwable) {
                fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
                return
            }
            complete(jobId, downloaded, totalBytes, options.mediaType, published, relativePath)
        } finally {
            runCatching { body.close() }
            if (published == null) {
                runCatching { handle.discard() }
            }
        }
    }

    private fun complete(
        jobId: String,
        downloaded: Long,
        totalBytes: Long?,
        mediaType: MediaType,
        relativePath: String,
        publishedPath: String,
    ) {
        val sizeBytes = runCatching { fileStore.size(publishedPath) }.getOrNull() ?: downloaded
        val artifact = Artifact(
            id = "artifact-${idGenerator()}",
            jobId = jobId,
            kind = artifactKind(mediaType, relativePath),
            fileName = relativePath.substringAfterLast('/'),
            relativePath = relativePath,
            sizeBytes = sizeBytes,
        )
        update(jobId) {
            it.copy(
                state = JobState.COMPLETED,
                progress = JobProgress(
                    phase = "completed",
                    percent = 100.0,
                    downloadedBytes = downloaded,
                    totalBytes = totalBytes,
                ),
                error = null,
                finishedAtEpochMillis = now(),
                artifacts = it.artifacts + artifact,
            )
        }
    }

    private fun reportProgress(jobId: String, downloaded: Long, totalBytes: Long?) {
        update(jobId, persistNow = false) {
            it.copy(
                progress = JobProgress(
                    phase = "downloading",
                    percent = totalBytes?.takeIf { it > 0 }?.let { (downloaded.toDouble() / it) * 100.0 },
                    downloadedBytes = downloaded,
                    totalBytes = totalBytes,
                ),
            )
        }
    }

    private fun fail(jobId: String, code: JobErrorCode, message: String, retryable: Boolean = true): DownloadJob? =
        update(jobId) {
            it.copy(
                state = JobState.FAILED,
                error = JobError(code, message, retryable),
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

    private fun isCancelRequested(jobId: String): Boolean =
        engineCriticalSection(lock) { cancelRequested[jobId] == true }

    private fun update(
        jobId: String,
        persistNow: Boolean = true,
        transform: (DownloadJob) -> DownloadJob,
    ): DownloadJob? = engineCriticalSection(lock) {
        val current = _jobs.value.firstOrNull { it.id == jobId } ?: return@engineCriticalSection null
        val transformed = transform(current).copy(
            revision = current.revision + 1,
            updatedAtEpochMillis = now(),
        )
        // The latest attempt mirrors the job row so the screens read one source
        // of truth; older attempts keep their own history.
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
        if (persistNow) persist(_jobs.value)
        updated
    }

    private fun findJob(jobId: String): DownloadJob? = _jobs.value.firstOrNull { it.id == jobId }

    private fun redactedUrlReason(reason: UrlRejectReason): String = when (reason) {
        UrlRejectReason.UnsupportedScheme -> "Only http and https source URLs are supported."
        UrlRejectReason.MissingHost -> "This URL has no host."
        UrlRejectReason.UserInfo -> "This URL embeds credentials and was refused."
        UrlRejectReason.BlockedDestination -> "This URL points to a local or reserved address and was refused."
        UrlRejectReason.Malformed -> "This URL is malformed."
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
        in 300..399 -> JobError(
            JobErrorCode.NETWORK_FAILURE,
            "The source redirected without a usable destination.",
            retryable = true,
        )

        else -> JobError(JobErrorCode.NETWORK_FAILURE, "The source returned an unexpected response.", retryable = true)
    }

    private fun cancelledError(): JobError =
        JobError(JobErrorCode.CANCELLED, "The download was cancelled.", retryable = false)

    private fun artifactKind(mediaType: MediaType, relativePath: String): ArtifactKind {
        val lower = relativePath.lowercase()
        return when {
            lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
                lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".m4v") -> ArtifactKind.VIDEO
            lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") ||
                lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".opus") ||
                lower.endsWith(".ogg") -> ArtifactKind.AUDIO
            lower.endsWith(".srt") || lower.endsWith(".vtt") || lower.endsWith(".ttml") ||
                lower.endsWith(".sbv") -> ArtifactKind.CAPTIONS
            else -> when (mediaType) {
                MediaType.VIDEO -> ArtifactKind.VIDEO
                MediaType.AUDIO -> ArtifactKind.AUDIO
                MediaType.CAPTIONS -> ArtifactKind.CAPTIONS
                MediaType.THUMBNAIL -> ArtifactKind.THUMBNAIL
            }
        }
    }
}