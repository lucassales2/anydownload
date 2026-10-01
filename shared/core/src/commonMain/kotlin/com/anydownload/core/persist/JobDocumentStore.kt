package com.anydownload.core.persist

import com.anydownload.core.domain.DownloadJob
import com.anydownload.core.domain.JobError
import com.anydownload.core.domain.JobErrorCode
import com.anydownload.core.domain.JobState
import com.anydownload.core.platform.engineCriticalSection
import com.anydownload.core.validation.RelativePathValidation
import com.anydownload.core.validation.RelativePathValidator
import kotlin.time.Clock

/** Rows and counters from one document restore. */
data class JobDocumentRestore(
    val jobs: List<DownloadJob>,
    val sanitizedDestinations: Int = 0,
    val interruptedActive: Int = 0,
    val clearedExpired: Int = 0,
)

/** The encoded document and counters from one save. */
data class JobDocumentSave(
    val document: String,
    val written: Int,
    val skippedStale: Int,
    val clearedExpired: Int,
)

/**
 * The shared persistence rules for the jobs document (T-102).
 *
 * A host only stores bytes. This class owns what a restart means:
 * - An active state on disk (`RESOLVING`, `QUEUED`, `DOWNLOADING`,
 *   `POSTPROCESSING`) becomes `FAILED` with `ENGINE_UNAVAILABLE`, retryable.
 *   The attempt is closed and the job is never auto-resumed.
 * - `PENDING` and `SCHEDULED` survive unchanged; terminal rows survive too.
 * - A destination folder that escapes the download root is cleared.
 * - Rows past the clear-completed age are dropped on restore and on save.
 * - A stale revision never replaces a newer row written by this instance.
 *
 * The instance keeps the stale-revision baseline, so a host must use one
 * instance per document and call [restore] before [save]. The class is
 * synchronized through [engineCriticalSection]: a real lock on the JVM, a
 * direct block on the single-threaded web and iOS hosts.
 */
class JobDocumentStore(
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val lock = Any()
    private val lastWrittenJobs = mutableMapOf<String, DownloadJob>()

    /**
     * Decodes a stored document and applies the restore rules. A null or blank
     * [text] is an empty state, not an error; malformed JSON throws so the
     * host can move the file aside. [clearAfterSeconds] comes from the host
     * settings (0 disables clearing).
     */
    fun restore(text: String?, clearAfterSeconds: Long): JobDocumentRestore =
        engineCriticalSection(lock) {
            lastWrittenJobs.clear()
            val loaded = if (text.isNullOrBlank()) emptyList() else JobDocumentCodec.decode(text)
            val at = now()
            var sanitized = 0
            var interrupted = 0
            val normalized = loaded.map { job ->
                val cleaned = sanitizeDestination(job)
                if (cleaned !== job) sanitized++
                if (cleaned.state.isInterruptedOnStartup()) {
                    interrupted++
                    interrupt(cleaned, at)
                } else {
                    cleaned
                }
            }
            val (jobs, cleared) = dropExpired(normalized, clearAfterSeconds, at)
            jobs.forEach { lastWrittenJobs[it.id] = it }
            JobDocumentRestore(
                jobs = jobs,
                sanitizedDestinations = sanitized,
                interruptedActive = interrupted,
                clearedExpired = cleared,
            )
        }

    /**
     * Convenience for hosts: reads the bytes from [storage] and applies the
     * same restore rules. Decoding a malformed document still throws; the
     * host decides what to do with a corrupt store.
     */
    fun restore(storage: JobDocumentStorage, clearAfterSeconds: Long): JobDocumentRestore =
        restore(storage.read(), clearAfterSeconds)

    /**
     * Drops expired rows, refuses a stale revision in favor of the last row
     * this instance saw, and encodes the accepted list. The host writes
     * [JobDocumentSave.document] where it keeps the file.
     */
    fun save(jobs: List<DownloadJob>, clearAfterSeconds: Long): JobDocumentSave =
        engineCriticalSection(lock) {
            val (kept, cleared) = dropExpired(jobs, clearAfterSeconds, now())
            val accepted = mutableListOf<DownloadJob>()
            var skipped = 0
            kept.forEach { job ->
                val last = lastWrittenJobs[job.id]
                if (last != null && job.revision < last.revision) {
                    skipped++
                    accepted += last
                } else {
                    accepted += job
                    lastWrittenJobs[job.id] = job
                }
            }
            lastWrittenJobs.keys.retainAll { id -> accepted.any { it.id == id } }
            JobDocumentSave(
                document = JobDocumentCodec.encode(accepted),
                written = accepted.size,
                skippedStale = skipped,
                clearedExpired = cleared,
            )
        }

    /** Drops the stale-revision baseline; the next [save] accepts any revision. */
    fun forget() = engineCriticalSection(lock) { lastWrittenJobs.clear() }
}

private fun JobState.isInterruptedOnStartup(): Boolean =
    this == JobState.RESOLVING || this == JobState.QUEUED ||
        this == JobState.DOWNLOADING || this == JobState.POSTPROCESSING

private fun sanitizeDestination(job: DownloadJob): DownloadJob {
    val folder = job.request.options.destinationFolder ?: return job
    return when (val result = RelativePathValidator.validate(folder)) {
        is RelativePathValidation.Valid -> {
            val normalized = result.path.ifEmpty { null }
            if (normalized == folder) {
                job
            } else {
                job.copy(
                    request = job.request.copy(
                        options = job.request.options.copy(destinationFolder = normalized),
                    ),
                )
            }
        }

        is RelativePathValidation.Invalid -> job.copy(
            request = job.request.copy(
                options = job.request.options.copy(destinationFolder = null),
            ),
        )
    }
}

private fun interrupt(job: DownloadJob, at: Long): DownloadJob {
    val error = JobError(
        code = JobErrorCode.ENGINE_UNAVAILABLE,
        message = "The app closed before the download finished. Retry to start it again.",
        retryable = true,
    )
    val attempts = if (job.attempts.isEmpty()) {
        job.attempts
    } else {
        job.attempts.dropLast(1) + job.attempts.last().copy(
            state = JobState.FAILED,
            error = error,
            finishedAtEpochMillis = at,
        )
    }
    return job.copy(
        state = JobState.FAILED,
        error = error,
        attempts = attempts,
        finishedAtEpochMillis = at,
        updatedAtEpochMillis = at,
        revision = job.revision + 1,
    )
}

private fun dropExpired(
    jobs: List<DownloadJob>,
    clearAfterSeconds: Long,
    at: Long,
): Pair<List<DownloadJob>, Int> {
    if (clearAfterSeconds <= 0) return jobs to 0
    val cutoff = at - clearAfterSeconds * 1000
    val retained = jobs.filterNot { job ->
        job.state.isTerminal && (job.finishedAtEpochMillis ?: job.updatedAtEpochMillis) < cutoff
    }
    return retained to (jobs.size - retained.size)
}
