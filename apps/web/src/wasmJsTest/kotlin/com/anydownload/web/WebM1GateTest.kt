package com.anydownload.web

import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobErrorCode
import com.anydownload.core.domain.JobState
import com.anydownload.core.engine.WebDownload
import com.anydownload.core.engine.WebExtensionBridge
import com.anydownload.core.engine.WebExtensionEngine
import com.anydownload.core.engine.WebFailureCode
import com.anydownload.core.engine.WebFetch
import com.anydownload.core.engine.WebPage
import com.anydownload.core.engine.WebProbe
import com.anydownload.core.persist.JobDocumentStore
import com.anydownload.core.persist.PersistingDownloadEngine
import com.anydownload.core.platform.HttpRequest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T-106 M1 web gate: the page completes a fixture through the extension
 * bridge, types a failure, stores the job document in its key-value storage,
 * and a reload of that document restores both rows without starting work.
 * There is no live browser click-through here (the D6/D7 recorded gap); the
 * media request path is the extension bridge, never a page-origin fetch.
 */
class WebM1GateTest {

    private val fixtureUrl = "https://fixtures.example.com/files/clip.mp4"
    private val failureUrl = "https://fixtures.example.com/private?token=SECRET"
    private val payloadSize = 32 * 1024L

    private class MemoryStorage : WebKeyValueStorage {
        val values = mutableMapOf<String, String>()
        override fun getItem(key: String): String? = values[key]
        override fun setItem(key: String, value: String) {
            values[key] = value
        }
    }

    private class FakeBridge : WebExtensionBridge {
        override val available: Boolean = true
        val probes = mutableListOf<String>()
        val downloads = mutableListOf<String>()

        override suspend fun probe(url: String): WebProbe {
            probes += url
            return if (url == "https://fixtures.example.com/files/clip.mp4") {
                WebProbe.Final(200, "application/octet-stream", 32 * 1024L, url)
            } else {
                WebProbe.Failed(WebFailureCode.NETWORK, "The source could not be reached.")
            }
        }

        override suspend fun fetchPage(url: String): WebPage =
            WebPage.Failed(WebFailureCode.OTHER, "not used")

        override suspend fun fetch(request: HttpRequest): WebFetch =
            WebFetch.Failed(WebFailureCode.OTHER, "not used")

        override suspend fun download(
            url: String,
            jobId: String,
            headers: Map<String, String>,
            saveViaBlob: Boolean,
            onProgress: (Long, Long?) -> Unit,
        ): WebDownload {
            downloads += url
            onProgress(32 * 1024L, 32 * 1024L)
            return WebDownload.Completed("clip.mp4", 32 * 1024L)
        }

        override suspend fun cancelDownload(jobId: String) = Unit
    }

    private var idCounter = 0

    private fun TestScope.process(
        storage: WebJobDocumentStorage,
        bridge: FakeBridge,
        documentStore: JobDocumentStore,
    ): PersistingDownloadEngine {
        val restored = documentStore.restore(storage, clearAfterSeconds = 0).jobs
        var persisting: PersistingDownloadEngine? = null
        val engine = WebExtensionEngine(
            bridge = bridge,
            scope = this,
            idGenerator = { "web-gate-${++idCounter}" },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            persist = { persisting?.persistNow() },
            seedJobs = restored,
        )
        val wrapper = PersistingDownloadEngine(
            delegate = engine,
            documentStore = documentStore,
            writeDocument = storage::write,
            clearAfterSeconds = { 0L },
        )
        persisting = wrapper
        return wrapper
    }

    @Test
    fun fixtureCompletesFailureIsTypedAndThePageReloadsBothRows() = runTest {
        val memory = MemoryStorage()
        val storageKey = WebJobDocumentStorage.KEY + ".m1-gate"
        val storage = WebJobDocumentStorage(storage = memory, key = storageKey)
        val bridge = FakeBridge()
        val first = process(storage, bridge, JobDocumentStore())

        val completedId = first.submit(DownloadRequest(sourceUrl = fixtureUrl, idempotencyKey = "web-fixture")).id
        val failedId = first.submit(DownloadRequest(sourceUrl = failureUrl, idempotencyKey = "web-failure")).id
        advanceUntilIdle()

        val completed = first.jobs.value.first { it.id == completedId }
        val failed = first.jobs.value.first { it.id == failedId }

        assertEquals(JobState.COMPLETED, completed.state, completed.error?.message)
        assertEquals("clip.mp4", completed.artifacts.single().relativePath)
        assertEquals(listOf(fixtureUrl), bridge.downloads, "the media GET must go through the extension bridge")

        assertEquals(JobState.FAILED, failed.state)
        assertEquals(JobErrorCode.NETWORK_FAILURE, failed.error?.code)
        assertFalse(failed.error?.message.orEmpty().contains("SECRET"))
        assertFalse(failed.error?.message.orEmpty().contains(failureUrl))
        assertTrue(failed.artifacts.isEmpty())

        val stored = memory.getItem(storageKey)
        assertTrue(stored != null && stored.contains("\"jobs\""), "the page must persist the job document")

        // Reload from the same bytes: both rows are back and nothing starts.
        val secondBridge = FakeBridge()
        val second = process(storage, secondBridge, JobDocumentStore())
        advanceUntilIdle()

        val reloaded = second.jobs.value.associateBy { it.id }
        assertEquals(setOf(completedId, failedId), reloaded.keys)
        assertEquals(JobState.COMPLETED, reloaded.getValue(completedId).state)
        assertEquals("clip.mp4", reloaded.getValue(completedId).artifacts.single().relativePath)
        assertEquals(JobState.FAILED, reloaded.getValue(failedId).state)
        assertTrue(secondBridge.probes.isEmpty(), "restoring a row must not start work")

        assertEquals(JobState.COMPLETED, second.retry(completedId)?.state)
        advanceUntilIdle()
        assertTrue(secondBridge.downloads.isEmpty(), "retry of a completed job must not download")
        assertEquals(1, reloaded.getValue(completedId).artifacts.size)
    }
}
