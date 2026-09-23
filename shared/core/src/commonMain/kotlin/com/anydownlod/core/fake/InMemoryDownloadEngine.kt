package com.anydownlod.core.fake

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
import com.anydownlod.core.domain.StartPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * In-memory [DownloadEngine] for non-desktop hosts, screens, and tests.
 *
 * Submit with [StartPolicy.AUTOMATIC] moves through `RESOLVING` then `QUEUED`;
 * submit with [StartPolicy.MANUAL] stops at `PENDING` until [start]. A repeated
 * non-blank idempotency key returns the original job. Nothing here downloads.
 *
 * [seedJobs] lets the UI show every state without clicking; a test should not
 * rely on the clock, so [now] and [idGenerator] are injectable.
 */
class InMemoryDownloadEngine(
    seedJobs: List<DownloadJob> = emptyList(),
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val idGenerator: () -> String = defaultIdGenerator(),
) : DownloadEngine {

    private val _jobs = MutableStateFlow(seedJobs)
    override val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val jobIdByIdempotencyKey = mutableMapOf<String, String>()

    init {
        seedJobs.forEach { job ->
            val key = job.request.idempotencyKey
            if (key.isNotEmpty() && !jobIdByIdempotencyKey.containsKey(key)) {
                jobIdByIdempotencyKey[key] = job.id
            }
        }
    }

    override fun submit(request: DownloadRequest): DownloadJob {
        val key = request.idempotencyKey
        if (key.isNotEmpty()) {
            jobIdByIdempotencyKey[key]?.let { existingId ->
                findJob(existingId)?.let { return it }
            }
        }

        val createdAt = now()
        val jobId = "job-${idGenerator()}"
        val accepted = DownloadJob(
            id = jobId,
            request = request,
            state = JobState.RESOLVING,
            revision = 1,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = createdAt,
            startedAtEpochMillis = createdAt,
            sourceHost = hostOf(request.sourceUrl),
            attempts = listOf(
                JobAttempt(
                    id = "attempt-${idGenerator()}",
                    jobId = jobId,
                    state = JobState.RESOLVING,
                    startedAtEpochMillis = createdAt,
                )
            ),
        )
        _jobs.value = _jobs.value + accepted
        if (key.isNotEmpty()) {
            jobIdByIdempotencyKey[key] = jobId
        }

        return when (request.options.startPolicy) {
            StartPolicy.AUTOMATIC -> transition(accepted, JobState.QUEUED)
            StartPolicy.MANUAL -> transition(accepted, JobState.PENDING)
        }
    }

    override fun start(jobId: String): DownloadJob? {
        val job = findJob(jobId) ?: return null
        if (job.state != JobState.PENDING && job.state != JobState.SCHEDULED) return job
        return transition(job, JobState.QUEUED)
    }

    override fun cancel(jobId: String): DownloadJob? {
        val job = findJob(jobId) ?: return null
        if (job.state.isTerminal) return job
        return transition(
            job = job,
            state = JobState.CANCELLED,
            error = JobError(
                code = JobErrorCode.CANCELLED,
                message = "The download was cancelled.",
                retryable = false,
            ),
        )
    }

    override fun retry(jobId: String): DownloadJob? {
        val job = findJob(jobId) ?: return null
        if (job.state != JobState.FAILED && job.state != JobState.CANCELLED) return job

        val startedAt = now()
        val retried = job.copy(
            state = JobState.QUEUED,
            progress = null,
            error = null,
            finishedAtEpochMillis = null,
            attempts = job.attempts + JobAttempt(
                id = "attempt-${idGenerator()}",
                jobId = job.id,
                state = JobState.QUEUED,
                startedAtEpochMillis = startedAt,
            ),
        )
        return publish(retried)
    }

    override fun removeHistory(jobId: String): Boolean {
        val job = findJob(jobId) ?: return false
        _jobs.value = _jobs.value.filterNot { it.id == job.id }
        val keys = jobIdByIdempotencyKey.filterValues { it == job.id }.keys.toList()
        keys.forEach { jobIdByIdempotencyKey.remove(it) }
        return true
    }

    override fun deleteArtifacts(jobId: String): ArtifactDeletionResult {
        val job = findJob(jobId) ?: return ArtifactDeletionResult(deletedCount = 0)
        val pending = job.artifacts.filterNot { it.removed }
        if (pending.isEmpty()) return ArtifactDeletionResult(deletedCount = 0)
        publish(
            job.copy(
                artifacts = job.artifacts.map { if (it.removed) it else it.copy(removed = true) },
            )
        )
        return ArtifactDeletionResult(deletedCount = pending.size)
    }

    /** Preview helper: replaces the latest progress without touching the state. */
    fun updateProgress(jobId: String, progress: JobProgress): DownloadJob? {
        val job = findJob(jobId) ?: return null
        val attempts = if (job.attempts.isEmpty()) {
            job.attempts
        } else {
            job.attempts.dropLast(1) + job.attempts.last().copy(progress = progress)
        }
        return publish(job.copy(progress = progress, attempts = attempts))
    }

    private fun findJob(jobId: String): DownloadJob? = _jobs.value.firstOrNull { it.id == jobId }

    private fun transition(job: DownloadJob, state: JobState, error: JobError? = null): DownloadJob {
        val at = now()
        val attempts = if (job.attempts.isEmpty()) {
            listOf(
                JobAttempt(
                    id = "attempt-${idGenerator()}",
                    jobId = job.id,
                    state = state,
                    error = error,
                    startedAtEpochMillis = at,
                    finishedAtEpochMillis = at.takeIf { state.isTerminal },
                )
            )
        } else {
            job.attempts.dropLast(1) + job.attempts.last().copy(
                state = state,
                error = error,
                finishedAtEpochMillis = at.takeIf { state.isTerminal },
            )
        }
        return publish(
            job.copy(
                state = state,
                error = error,
                attempts = attempts,
                finishedAtEpochMillis = at.takeIf { state.isTerminal },
            )
        )
    }

    private fun publish(job: DownloadJob): DownloadJob {
        val current = findJob(job.id) ?: return job
        val updated = job.copy(
            revision = current.revision + 1,
            updatedAtEpochMillis = now(),
        )
        _jobs.value = _jobs.value.map { if (it.id == updated.id) updated else it }
        return updated
    }

    companion object {
        /** One job for each D1 state, for screenshots and click-throughs. */
        fun sampleJobs(): List<DownloadJob> {
            val base = 1_767_225_600_000L // 2026-01-01T00:00:00Z
            var attemptCounter = 0

            fun sampleAttempt(
                jobId: String,
                state: JobState,
                progress: JobProgress? = null,
                error: JobError? = null,
            ): JobAttempt {
                attemptCounter += 1
                return JobAttempt(
                    id = "sample-attempt-$attemptCounter",
                    jobId = jobId,
                    state = state,
                    progress = progress,
                    error = error,
                    startedAtEpochMillis = base,
                    finishedAtEpochMillis = (base + 60_000L).takeIf { state.isTerminal },
                )
            }

            fun sampleJob(
                id: String,
                state: JobState,
                options: DownloadOptions = DownloadOptions(),
                title: String? = null,
                progress: JobProgress? = null,
                error: JobError? = null,
                artifacts: List<Artifact> = emptyList(),
                scheduledAtEpochMillis: Long? = null,
            ): DownloadJob = DownloadJob(
                id = id,
                request = DownloadRequest(sourceUrl = "https://example.com/watch?v=$id", options = options),
                state = state,
                revision = 1,
                createdAtEpochMillis = base,
                updatedAtEpochMillis = base + 60_000L,
                startedAtEpochMillis = base.takeUnless { state == JobState.PENDING || state == JobState.SCHEDULED },
                finishedAtEpochMillis = (base + 60_000L).takeIf { state.isTerminal },
                title = title,
                sourceHost = "example.com",
                scheduledAtEpochMillis = scheduledAtEpochMillis,
                progress = progress,
                error = error,
                artifacts = artifacts,
                attempts = listOf(sampleAttempt(id, state, progress, error)),
            )

            return listOf(
                sampleJob(
                    id = "seed-pending",
                    state = JobState.PENDING,
                    options = DownloadOptions(startPolicy = StartPolicy.MANUAL),
                    title = "Fixture pending video",
                ),
                sampleJob(
                    id = "seed-scheduled",
                    state = JobState.SCHEDULED,
                    title = "Fixture premiere",
                    scheduledAtEpochMillis = base + 3_600_000L,
                ),
                sampleJob(
                    id = "seed-downloading",
                    state = JobState.DOWNLOADING,
                    title = "Fixture downloading video",
                    progress = JobProgress(
                        phase = "downloading",
                        percent = 42.5,
                        downloadedBytes = 4_250_000,
                        totalBytes = 10_000_000,
                        speedBytesPerSecond = 1_200_000.0,
                        etaSeconds = 5,
                    ),
                ),
                sampleJob(
                    id = "seed-downloading-unknown-total",
                    state = JobState.DOWNLOADING,
                    title = "Fixture live stream",
                    progress = JobProgress(
                        phase = "downloading",
                        percent = null,
                        downloadedBytes = 512_000,
                        totalBytes = null,
                    ),
                ),
                sampleJob(
                    id = "seed-postprocessing",
                    state = JobState.POSTPROCESSING,
                    title = "Fixture merging video",
                    progress = JobProgress(phase = "postprocessing", percent = 100.0),
                ),
                sampleJob(
                    id = "seed-completed",
                    state = JobState.COMPLETED,
                    title = "Fixture completed video",
                    progress = JobProgress(phase = "completed", percent = 100.0),
                    artifacts = listOf(
                        Artifact(
                            id = "seed-artifact-video",
                            jobId = "seed-completed",
                            kind = ArtifactKind.VIDEO,
                            fileName = "Fixture completed video.mp4",
                            relativePath = "Fixture completed video.mp4",
                            sizeBytes = 10_000_000,
                        ),
                    ),
                ),
                sampleJob(
                    id = "seed-failed",
                    state = JobState.FAILED,
                    title = "Fixture members-only video",
                    error = JobError(
                        code = JobErrorCode.LOGIN_REQUIRED,
                        message = "Sign in to this source, then retry.",
                        retryable = true,
                    ),
                ),
            )
        }
    }
}
