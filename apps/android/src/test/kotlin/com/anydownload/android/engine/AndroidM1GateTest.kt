package com.anydownload.android.engine

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
import com.anydownload.core.platform.JavaNetFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T-106 M1 Android gate, JVM-equivalent because no emulator is required: the
 * shared HTTP engine plus the Android state file complete a fixture, type a
 * failure, and reload both rows in a new "process". The fixture URL never goes
 * to Chaquopy (the Chaquopy engine is not built here, and this test's URL is
 * handled by the shared engine path the Android graph uses for Kotlin/direct
 * routes).
 */
class AndroidM1GateTest {

    private val fixtureUrl = "https://fixtures.example.com/files/clip.mp4"
    private val failureUrl = "https://fixtures.example.com/private?token=SECRET"

    private class AndroidProcess(
        stateFile: File,
        downloadRoot: Path,
        transfer: HttpTransfer,
        scope: CoroutineScope,
    ) {
        private var idCounter = 0
        private val documentStore = JobDocumentStore()
        private val storage = AndroidJobDocumentStorage(stateFile)
        val engine: PersistingDownloadEngine

        init {
            val restored = documentStore.restore(storage, clearAfterSeconds = 0).jobs
            var persisting: PersistingDownloadEngine? = null
            val delegate = HttpDownloadEngine(
                transfer = transfer,
                fileStore = JavaNetFileStore(downloadRoot),
                settings = InMemorySettingsRepository(AppSettings(downloadRoot = downloadRoot.toString())),
                scope = scope,
                idGenerator = { "android-gate-${++idCounter}" },
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

    @Test
    fun fixtureCompletesFailureIsTypedAndTheStateFileReloadsBothRows() = runBlocking {
        val stateRoot = Files.createTempDirectory("anydownlod-android-gate-state-").toFile()
        val stateFile = File(stateRoot, "jobs.json")
        val downloadRoot = Files.createTempDirectory("anydownlod-android-gate-downloads-")
        val payload = ByteArray(32 * 1024) { (it % 251).toByte() }

        val firstTransfer = FixtureTransfer(fixtureUrl, payload)
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val first = AndroidProcess(stateFile, downloadRoot, firstTransfer, firstScope)

        val completedId = first.engine.submit(DownloadRequest(sourceUrl = fixtureUrl, idempotencyKey = "android-fixture")).id
        val failedId = first.engine.submit(DownloadRequest(sourceUrl = failureUrl, idempotencyKey = "android-failure")).id

        val terminal = withTimeout(30_000) {
            first.engine.jobs.first { jobs -> jobs.filter { it.state.isTerminal }.size == 2 }
        }
        val completed = terminal.first { it.id == completedId }
        val failed = terminal.first { it.id == failedId }

        assertEquals(JobState.COMPLETED, completed.state, completed.error?.message)
        assertEquals("clip.mp4", completed.artifacts.single().relativePath)
        val written = downloadRoot.resolve("clip.mp4")
        assertTrue(Files.isRegularFile(written))
        assertTrue(Files.readAllBytes(written).contentEquals(payload))

        assertEquals(JobState.FAILED, failed.state)
        assertEquals(JobErrorCode.NETWORK_FAILURE, failed.error?.code)
        assertFalse(failed.error?.message.orEmpty().contains("SECRET"))
        assertFalse(failed.error?.message.orEmpty().contains(failureUrl))
        assertTrue(failed.artifacts.isEmpty())

        firstScope.cancel()

        val secondTransfer = FixtureTransfer(fixtureUrl, payload)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val second = AndroidProcess(stateFile, downloadRoot, secondTransfer, secondScope)

        val reloaded = second.engine.jobs.value.associateBy { it.id }
        assertEquals(setOf(completedId, failedId), reloaded.keys)
        assertEquals(JobState.COMPLETED, reloaded.getValue(completedId).state)
        assertEquals("clip.mp4", reloaded.getValue(completedId).artifacts.single().relativePath)
        assertEquals(JobState.FAILED, reloaded.getValue(failedId).state)
        assertTrue(secondTransfer.requested.isEmpty(), "restoring a row must not start work")

        assertEquals(JobState.COMPLETED, second.engine.retry(completedId)?.state)
        assertTrue(secondTransfer.requested.isEmpty(), "retry of a completed job must not download")
        assertEquals(1, Files.list(downloadRoot).use { stream -> stream.filter { Files.isRegularFile(it) }.count() })

        secondScope.cancel()
    }
}
