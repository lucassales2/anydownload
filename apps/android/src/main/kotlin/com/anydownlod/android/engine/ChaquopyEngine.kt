package com.anydownlod.android.engine

import com.anydownlod.core.ArtifactDeletionResult
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.domain.Artifact
import com.anydownlod.core.domain.ArtifactKind
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobAttempt
import com.anydownlod.core.domain.JobError
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobProgress
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.StartPolicy
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
 * Android adapter that runs non-direct URLs through the [ChaquopyPort]
 * (pinned yt-dlp under `apps/android` only). Job rows are owned here; the port
 * is the only Python-touched code. When the port is unavailable the job fails
 * with an honest [JobErrorCode.ENGINE_UNAVAILABLE] — Add never pretends a
 * site URL started.
 *
 * Raw values: no stdout, exception text, or media URLs enter [DownloadJob] or
 * this class's messages.
 */
class ChaquopyEngine(
    private val port: ChaquopyPort,
    private val downloadRoot: () -> String,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val idGenerator: () -> String = { "ad-${Random.nextLong().toULong().toString(16)}" },
    seedJobs: List<DownloadJob> = emptyList(),
) : DownloadEngine {

    private val lock = Any()
    private val _jobs = MutableStateFlow(seedJobs)
    override val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val idempotency = mutableMapOf<String, String>()
    private val running = mutableMapOf<String, Job>()
    private val cancelRequested = mutableMapOf<String, Boolean>()

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
                runCatching { java.nio.file.Files.deleteIfExists(rootPath().resolve(artifact.relativePath).normalize()) }
                    .getOrDefault(false)
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
            sourceHost = hostOf(request.sourceUrl),
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
        val root = downloadRoot()
        if (root.isBlank()) {
            fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "Choose a download folder in Settings first.")
            return
        }
        if (!port.available) {
            fail(
                jobId,
                JobErrorCode.ENGINE_UNAVAILABLE,
                "Chaquopy is not available in this build, so site URLs cannot download.",
                retryable = false,
            )
            return
        }
        update(jobId, persistNow = false) {
            it.copy(state = JobState.DOWNLOADING, progress = JobProgress(phase = "downloading"))
        }
        try {
            val request = findJob(jobId)?.request ?: return
            val result = withContext(ioDispatcher) { port.runDownload(request, root) }
            currentCoroutineContext().ensureActive()
            when (result) {
                is ChaquopyResult.Finished -> complete(jobId, result, request.options.mediaType)
                is ChaquopyResult.Failed -> fail(
                    jobId,
                    result.code,
                    result.message,
                    result.retryable,
                )
            }
        } catch (cancelled: CancellationException) {
            if (isCancelRequested(jobId)) confirmCancelled(jobId) else throw cancelled
        } catch (failure: Throwable) {
            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
            } else {
                fail(jobId, JobErrorCode.EXTRACTION_FAILURE, "The source could not be extracted.", retryable = true)
            }
        }
    }

    private fun complete(jobId: String, result: ChaquopyResult.Finished, mediaType: MediaType) {
        val fileName = result.relativePath.substringAfterLast('/')
        val artifact = Artifact(
            id = "artifact-${idGenerator()}",
            jobId = jobId,
            kind = artifactKind(mediaType, fileName),
            fileName = fileName,
            relativePath = result.relativePath,
            sizeBytes = result.sizeBytes,
        )
        update(jobId) {
            it.copy(
                state = JobState.COMPLETED,
                progress = JobProgress(phase = "completed", percent = 100.0, downloadedBytes = result.sizeBytes),
                error = null,
                finishedAtEpochMillis = now(),
                artifacts = it.artifacts + artifact,
            )
        }
    }

    private fun artifactKind(mediaType: MediaType, fileName: String): ArtifactKind {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") ||
                lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".opus") ||
                lower.endsWith(".ogg") -> ArtifactKind.AUDIO
            lower.endsWith(".srt") || lower.endsWith(".vtt") || lower.endsWith(".ttml") ||
                lower.endsWith(".sbv") -> ArtifactKind.CAPTIONS
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") -> ArtifactKind.THUMBNAIL
            else -> when (mediaType) {
                MediaType.VIDEO -> ArtifactKind.VIDEO
                MediaType.AUDIO -> ArtifactKind.AUDIO
                MediaType.CAPTIONS -> ArtifactKind.CAPTIONS
                MediaType.THUMBNAIL -> ArtifactKind.THUMBNAIL
            }
        }
    }

    private fun rootPath() = java.nio.file.Path.of(downloadRoot()).toAbsolutePath().normalize()

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

    private fun cancelledError(): JobError =
        JobError(JobErrorCode.CANCELLED, "The download was cancelled.", retryable = false)

    private fun isCancelRequested(jobId: String): Boolean =
        engineCriticalSection(lock) { cancelRequested[jobId] == true }

    private fun findJob(jobId: String): DownloadJob? = _jobs.value.firstOrNull { it.id == jobId }

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
        updated
    }
}

private fun hostOf(url: String): String? {
    val afterScheme = url.substringAfter("://", "")
    if (afterScheme.isEmpty()) return null
    return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').ifEmpty { null }
}

private fun <T> engineCriticalSection(lock: Any, block: () -> T): T =
    synchronized(lock) { block() }