package com.anydownload.core.engine

import com.anydownload.core.SettingsRepository
import com.anydownload.core.domain.AppSettings
import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobState
import com.anydownload.core.fake.InMemorySettingsRepository
import com.anydownload.core.platform.ByteArrayHttpBody
import com.anydownload.core.platform.FileHandle
import com.anydownload.core.platform.FileStore
import com.anydownload.core.platform.HttpRequest
import com.anydownload.core.platform.HttpResponse
import com.anydownload.core.platform.HttpTransfer
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** T-017 global sleep and rate-limit controls. */
class EngineThrottleTest {

    private class MemoryHandle : FileHandle {
        override fun write(bytes: ByteArray, length: Int) = Unit
        override fun close() = Unit
        override fun discard() = Unit
    }

    private class MemoryStore : FileStore {
        val published = mutableMapOf<String, ByteArray>()
        override fun createTempFile(): FileHandle = MemoryHandle()
        override fun createTempFile(extension: String): FileHandle = MemoryHandle()
        override fun publish(temp: FileHandle, relativePath: String): String {
            published[relativePath] = ByteArray(0)
            return relativePath
        }

        override fun delete(relativePath: String): Boolean = published.remove(relativePath) != null
        override fun size(relativePath: String): Long? = published[relativePath]?.size?.toLong()
    }

    private class FixedTransfer(private val bytes: Int) : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = HttpResponse.Final(
            statusCode = 200,
            contentType = "application/octet-stream",
            totalBytes = bytes.toLong(),
            body = ByteArrayHttpBody(ByteArray(bytes)),
        )
    }

    private fun TestScope.engine(settings: SettingsRepository, transfer: HttpTransfer = FixedTransfer(16)) =
        HttpDownloadEngine(
            transfer = transfer,
            fileStore = MemoryStore(),
            settings = settings,
            scope = this,
            idGenerator = { "thr-${++idCounter}" },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

    private var idCounter = 0

    private fun request(key: String) = DownloadRequest(
        sourceUrl = "https://fixtures.example.com/files/tiny.bin",
        options = DownloadOptions(),
        idempotencyKey = key,
    )

    @Test
    fun theSleepIntervalWaitsBeforeTheAttemptStarts() = runTest {
        val settings = InMemorySettingsRepository(
            AppSettings(downloadRoot = "/tmp/anydownlod-throttle", sleepIntervalSeconds = 2),
        )
        val engine = engine(settings)

        val job = engine.submit(request("sleep-interval"))
        testScheduler.advanceTimeBy(1_000)
        assertTrue(
            engine.jobs.value.first { it.id == job.id }.state != JobState.COMPLETED,
            "the attempt waits for the interval",
        )
        testScheduler.advanceTimeBy(1_000)
        testScheduler.advanceUntilIdle()
        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
    }

    @Test
    fun theRateLimitSlowsTheMediaRead() = runTest {
        val settings = InMemorySettingsRepository(
            AppSettings(downloadRoot = "/tmp/anydownlod-throttle", rateLimitKibPerSecond = 1),
        )
        val engine = engine(settings, FixedTransfer(2_048))

        val job = engine.submit(request("rate-limit"))
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
        assertTrue(testScheduler.currentTime >= 2_000, "elapsed=${testScheduler.currentTime}")
    }

    @Test
    fun theSleepRequestsControlWaitsBeforeTheMediaHop() = runTest {
        val settings = InMemorySettingsRepository(
            AppSettings(downloadRoot = "/tmp/anydownlod-throttle", sleepRequestsSeconds = 1),
        )
        val engine = engine(settings)

        val job = engine.submit(request("sleep-requests"))
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
        assertTrue(testScheduler.currentTime >= 1_000, "elapsed=${testScheduler.currentTime}")
    }
}
