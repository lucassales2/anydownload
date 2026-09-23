package com.anydownlod.core.fake

import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobProgress
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.StartPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryDownloadEngineTest {

    private var clock = 1_000L

    private fun engine(seedJobs: List<DownloadJob> = emptyList()) =
        InMemoryDownloadEngine(
            seedJobs = seedJobs,
            now = { clock++ },
            idGenerator = defaultIdGenerator(),
        )

    private fun request(
        policy: StartPolicy = StartPolicy.AUTOMATIC,
        key: String = "",
    ) = DownloadRequest(
        sourceUrl = "https://example.com/watch?v=fixture",
        options = DownloadOptions(startPolicy = policy),
        idempotencyKey = key,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun automaticSubmitResolvesThenQueues() = runTest {
        val engine = engine()
        val observed = mutableListOf<JobState>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.jobs.collect { jobs -> jobs.lastOrNull()?.let { observed += it.state } }
        }

        val job = engine.submit(request(StartPolicy.AUTOMATIC, key = "auto-key"))

        assertEquals(JobState.QUEUED, job.state)
        assertEquals(listOf(JobState.RESOLVING, JobState.QUEUED), observed)
        assertEquals("example.com", job.sourceHost)
        collector.cancel()
    }

    @Test
    fun manualSubmitStaysPendingUntilStart() {
        val engine = engine()
        val job = engine.submit(request(StartPolicy.MANUAL))

        assertEquals(JobState.PENDING, job.state)
        assertEquals(JobState.PENDING, job.latestAttempt?.state)

        val started = engine.start(job.id)

        assertNotNull(started)
        assertEquals(JobState.QUEUED, started.state)
        assertEquals(JobState.QUEUED, started.latestAttempt?.state)
        assertTrue(started.revision > job.revision)
    }

    @Test
    fun cancelNonTerminalJobBecomesCancelled() {
        val engine = engine()
        val job = engine.submit(request(StartPolicy.AUTOMATIC))

        val cancelled = engine.cancel(job.id)

        assertNotNull(cancelled)
        assertEquals(JobState.CANCELLED, cancelled.state)
        assertEquals(JobErrorCode.CANCELLED, cancelled.error?.code)
        assertFalse(cancelled.error!!.retryable)
        assertEquals(JobState.CANCELLED, cancelled.latestAttempt?.state)
    }

    @Test
    fun cancelLeavesATerminalJobAlone() {
        val completed = InMemoryDownloadEngine.sampleJobs().first { it.state == JobState.COMPLETED }
        val engine = engine(seedJobs = listOf(completed))

        val after = engine.cancel(completed.id)

        assertNotNull(after)
        assertEquals(JobState.COMPLETED, after.state)
        assertEquals(completed.revision, after.revision)
    }

    @Test
    fun retryCreatesANewAttemptAndKeepsTheFailedOne() {
        val failed = InMemoryDownloadEngine.sampleJobs().first { it.state == JobState.FAILED }
        val engine = engine(seedJobs = listOf(failed))

        val retried = engine.retry(failed.id)

        assertNotNull(retried)
        assertEquals(JobState.QUEUED, retried.state)
        assertEquals(2, retried.attempts.size)
        assertEquals(JobState.FAILED, retried.attempts[0].state)
        assertNotNull(retried.attempts[0].error)
        assertEquals(JobState.QUEUED, retried.attempts[1].state)
        assertNull(retried.progress)
        assertNull(retried.error)
        assertTrue(retried.revision > failed.revision)
    }

    @Test
    fun retryDoesNothingForAnActiveJob() {
        val pending = InMemoryDownloadEngine.sampleJobs().first { it.state == JobState.PENDING }
        val engine = engine(seedJobs = listOf(pending))

        val after = engine.retry(pending.id)

        assertNotNull(after)
        assertEquals(JobState.PENDING, after.state)
        assertEquals(pending.attempts.size, after.attempts.size)
    }

    @Test
    fun idempotentSubmitReturnsTheOriginalJob() {
        val engine = engine()

        val first = engine.submit(request(StartPolicy.AUTOMATIC, key = "same-key"))
        val second = engine.submit(request(StartPolicy.AUTOMATIC, key = "same-key"))

        assertEquals(first.id, second.id)
        assertEquals(1, engine.jobs.value.size)
    }

    @Test
    fun blankIdempotencyKeysAreNotDeduplicated() {
        val engine = engine()

        val first = engine.submit(request(StartPolicy.AUTOMATIC))
        val second = engine.submit(request(StartPolicy.AUTOMATIC))

        assertNotEquals(first.id, second.id)
        assertEquals(2, engine.jobs.value.size)
    }

    @Test
    fun progressWithNullPercentStaysNull() {
        val engine = engine()
        val job = engine.submit(request(StartPolicy.AUTOMATIC, key = "progress-key"))

        val updated = engine.updateProgress(
            job.id,
            JobProgress(phase = "downloading", percent = null, downloadedBytes = 1024),
        )

        assertNotNull(updated)
        assertNull(updated.progress?.percent)
        assertEquals(1024L, updated.progress?.downloadedBytes)
        assertNull(updated.latestAttempt?.progress?.percent)
    }

    @Test
    fun unknownJobIdsReturnNull() {
        val engine = engine()

        assertNull(engine.start("missing"))
        assertNull(engine.cancel("missing"))
        assertNull(engine.retry("missing"))
        assertNull(engine.updateProgress("missing", JobProgress()))
    }

    @Test
    fun removeHistoryDropsTheRowWithoutTouchingArtifacts() {
        val completed = InMemoryDownloadEngine.sampleJobs().first { it.state == JobState.COMPLETED }
        val engine = engine(seedJobs = listOf(completed))

        assertTrue(engine.removeHistory(completed.id))

        assertTrue(engine.jobs.value.isEmpty())
        assertFalse(engine.removeHistory(completed.id))
    }

    @Test
    fun deleteArtifactsMarksEveryFileRemovedAndKeepsTheRow() {
        val completed = InMemoryDownloadEngine.sampleJobs().first { it.state == JobState.COMPLETED }
        val engine = engine(seedJobs = listOf(completed))

        val result = engine.deleteArtifacts(completed.id)

        assertEquals(1, result.deletedCount)
        assertTrue(result.allDeleted)
        val stored = engine.jobs.value.single()
        assertEquals(JobState.COMPLETED, stored.state)
        assertTrue(stored.artifacts.single().removed)
    }

    @Test
    fun deleteArtifactsOnAMissingJobIsSafe() {
        val result = engine().deleteArtifacts("missing")

        assertEquals(0, result.deletedCount)
        assertTrue(result.allDeleted)
    }

    @Test
    fun sampleJobsCoverEveryD1State() {
        val jobs = InMemoryDownloadEngine.sampleJobs()

        assertEquals(
            setOf(
                JobState.PENDING,
                JobState.SCHEDULED,
                JobState.DOWNLOADING,
                JobState.POSTPROCESSING,
                JobState.COMPLETED,
                JobState.FAILED,
            ),
            jobs.map { it.state }.toSet(),
        )
        assertTrue(jobs.any { it.state == JobState.DOWNLOADING && it.progress?.percent == null })
        assertTrue(jobs.any { it.state == JobState.DOWNLOADING && it.progress?.percent != null })
        assertTrue(jobs.any { it.state == JobState.COMPLETED && it.artifacts.isNotEmpty() })
        assertTrue(jobs.any { it.state == JobState.FAILED && it.error != null })
        assertTrue(jobs.all { it.sourceHost == "example.com" })
        assertTrue(jobs.any { it.scheduledAtEpochMillis != null })
    }
}
