package com.anydownload.ui.persist

import com.anydownload.core.domain.AppSettings
import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobErrorCode
import com.anydownload.core.domain.JobState
import com.anydownload.core.engine.HttpDownloadEngine
import com.anydownload.core.fake.InMemorySettingsRepository
import com.anydownload.core.persist.JobDocumentStore
import com.anydownload.core.persist.PersistingDownloadEngine
import com.anydownload.core.platform.ByteArrayHttpBody
import com.anydownload.core.platform.HttpRequest
import com.anydownload.core.platform.HttpResponse
import com.anydownload.core.platform.HttpTransfer
import com.anydownload.core.platform.IosFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T-106 M1 iOS gate, simulator-only and offline: the shared HTTP engine over
 * the sandbox [IosFileStore] completes a fixture, types a failure, and a new
 * engine/store built from the same Application-Support-style state path
 * restores both rows.
 */
class IosM1GateTest {

    private val fixtureUrl = "https://fixtures.example.com/files/clip.mp4"
    private val failureUrl = "https://fixtures.example.com/private?token=SECRET"

    private class IosProcess(
        statePath: String,
        downloadRoot: String,
        transfer: HttpTransfer,
        scope: CoroutineScope,
    ) {
        private var idCounter = 0
        private val documentStore = JobDocumentStore()
        private val storage = IosJobDocumentStorage(statePath)
        val engine: PersistingDownloadEngine

        init {
            val restored = documentStore.restore(storage, clearAfterSeconds = 0).jobs
            var persisting: PersistingDownloadEngine? = null
            val delegate = HttpDownloadEngine(
                transfer = transfer,
                fileStore = IosFileStore(downloadRoot),
                settings = InMemorySettingsRepository(AppSettings(downloadRoot = downloadRoot)),
                scope = scope,
                idGenerator = { "ios-gate-${++idCounter}" },
                persist = { persisting?.persistNow() },
                seedJobs = restored,
            )
            val wrapper = PersistingDownloadEngine(
                delegate = delegate,
                documentStore = documentStore,
                writeDocument = storage::write,
                clearAfterSeconds = { 0L },
            )
            persisting = wrapper
            engine = wrapper
        }
    }

    private class FixtureTransfer(
        private val fixtureUrl: String,
        private val payload: ByteArray,
    ) : HttpTransfer {
        val requested = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requested += request.url
            return if (request.url == fixtureUrl) {
                HttpResponse.Final(
                    statusCode = 200,
                    contentType = "application/octet-stream",
                    totalBytes = payload.size.toLong(),
                    body = ByteArrayHttpBody(payload),
                )
            } else {
                HttpResponse.Final(statusCode = 503, contentType = "text/plain")
            }
        }
    }

    private fun tempRoot(): String =
        NSTemporaryDirectory().trimEnd('/') + "/anydownlod-ios-gate-" + NSUUID().UUIDString

    @Test
    fun fixtureCompletesFailureIsTypedAndTheSandboxStateReloadsBothRows() = runBlocking {
        val root = tempRoot()
        val statePath = "$root/state/jobs.json"
        val downloadRoot = "$root/downloads"
        val payload = ByteArray(32 * 1024) { (it % 251).toByte() }

        val firstTransfer = FixtureTransfer(fixtureUrl, payload)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val first = IosProcess(statePath, downloadRoot, firstTransfer, firstScope)

        val completedId = first.engine.submit(DownloadRequest(sourceUrl = fixtureUrl, idempotencyKey = "ios-fixture")).id
        val failedId = first.engine.submit(DownloadRequest(sourceUrl = failureUrl, idempotencyKey = "ios-failure")).id

        val terminal = withTimeout(30_000) {
            first.engine.jobs.first { jobs -> jobs.filter { it.state.isTerminal }.size == 2 }
        }
        val completed = terminal.first { it.id == completedId }
        val failed = terminal.first { it.id == failedId }

        assertEquals(JobState.COMPLETED, completed.state, completed.error?.message)
        assertEquals("clip.mp4", completed.artifacts.single().relativePath)
        assertTrue(
            NSFileManager.defaultManager.fileExistsAtPath("$downloadRoot/clip.mp4"),
            "the fixture file must exist in the sandbox download root",
        )
        assertEquals(payload.size.toLong(), IosFileStore(downloadRoot).size("clip.mp4"))

        assertEquals(JobState.FAILED, failed.state)
        assertEquals(JobErrorCode.NETWORK_FAILURE, failed.error?.code)
        assertFalse(failed.error?.message.orEmpty().contains("SECRET"))
        assertFalse(failed.error?.message.orEmpty().contains(failureUrl))
        assertTrue(failed.artifacts.isEmpty())

        firstScope.cancel()

        val secondTransfer = FixtureTransfer(fixtureUrl, payload)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val second = IosProcess(statePath, downloadRoot, secondTransfer, secondScope)

        val reloaded = second.engine.jobs.value.associateBy { it.id }
        assertEquals(setOf(completedId, failedId), reloaded.keys)
        assertEquals(JobState.COMPLETED, reloaded.getValue(completedId).state)
        assertEquals("clip.mp4", reloaded.getValue(completedId).artifacts.single().relativePath)
        assertEquals(JobState.FAILED, reloaded.getValue(failedId).state)
        assertTrue(secondTransfer.requested.isEmpty(), "restoring a row must not start work")

        assertEquals(JobState.COMPLETED, second.engine.retry(completedId)?.state)
        assertTrue(secondTransfer.requested.isEmpty(), "retry of a completed job must not download")
        assertEquals(payload.size.toLong(), IosFileStore(downloadRoot).size("clip.mp4"))

        secondScope.cancel()
    }
}
