package com.anydownlod.web

import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.core.persist.JobDocumentStorageError
import com.anydownlod.core.persist.JobDocumentStore
import com.anydownlod.core.persist.PersistingDownloadEngine
import kotlinx.browser.window
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebJobDocumentStorageTest {

    /** DOM-free stand-in for `localStorage`, so the logic tests are isolated. */
    private class MemoryStorage : WebKeyValueStorage {
        val values = mutableMapOf<String, String>()
        override fun getItem(key: String): String? = values[key]
        override fun setItem(key: String, value: String) {
            values[key] = value
        }
    }

    private val localStorageKeys = mutableListOf<String>()

    @AfterTest
    fun cleanUp() {
        localStorageKeys.forEach { key -> runCatching { window.localStorage.removeItem(key) } }
    }

    private fun key(): String = "${WebJobDocumentStorage.KEY}.${Random.nextInt(1_000_000)}"

    @Test
    fun completedArtifactAndInterruptedRowRoundTripThroughLocalStorage() {
        val backing = MemoryStorage()
        val store = WebJobDocumentStorage(storage = backing, key = key())
        val completed = InMemoryDownloadEngine.sampleJobs().first { it.id == "seed-completed" }
        val downloading = InMemoryDownloadEngine.sampleJobs().first { it.id == "seed-downloading" }
        val saved = JobDocumentStore(now = { 1_000L }).save(listOf(completed, downloading), clearAfterSeconds = 0)

        assertNull(store.write(saved.document))
        val stored = backing.getItem(store.storageKey)
        assertTrue(stored != null && stored.contains("\"jobs\""))

        val restored = JobDocumentStore(now = { 2_000L }).restore(store.read(), clearAfterSeconds = 0)
        val byId = restored.jobs.associateBy { it.id }

        assertEquals(completed.artifacts, byId.getValue("seed-completed").artifacts)
        assertEquals(JobState.COMPLETED, byId.getValue("seed-completed").state)
        assertEquals(JobState.FAILED, byId.getValue("seed-downloading").state)
        assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, byId.getValue("seed-downloading").error?.code)
        assertTrue(byId.getValue("seed-downloading").error?.retryable == true)
    }

    @Test
    fun overCapWriteKeepsThePreviousDocumentAndReturnsFull() {
        val backing = MemoryStorage()
        val storageKey = key()
        val generous = WebJobDocumentStorage(storage = backing, maxDocumentBytes = 4096, key = storageKey)
        assertNull(generous.write("""{"jobs":[]}"""))
        val previous = backing.getItem(storageKey)

        val tiny = WebJobDocumentStorage(storage = backing, maxDocumentBytes = 8, key = storageKey)
        assertEquals(JobDocumentStorageError.FULL, tiny.write("""{"jobs":[{"id":"a"}]}"""))

        // The older document was not clobbered by the refused write.
        assertEquals(previous, backing.getItem(storageKey))
    }

    @Test
    fun overCapDoesNotLoseTheOnScreenRows() {
        val backing = MemoryStorage()
        val store = WebJobDocumentStorage(storage = backing, maxDocumentBytes = 8, key = key())
        val engine = PersistingDownloadEngine(
            delegate = InMemoryDownloadEngine(),
            documentStore = JobDocumentStore(),
            writeDocument = store::write,
        )

        engine.submit(DownloadRequest(sourceUrl = "https://example.com/watch?v=fixture"))

        assertEquals(JobDocumentStorageError.FULL, engine.storageError.value)
        assertEquals(1, engine.jobs.value.size)
        assertNull(backing.getItem(store.storageKey))
    }

    @Test
    fun inlineMediaMarkersAreRefusedAndNothingIsStored() {
        val backing = MemoryStorage()
        val store = WebJobDocumentStorage(storage = backing, key = key())

        assertEquals(
            JobDocumentStorageError.UNAVAILABLE,
            store.write("""{"jobs":[{"request":{"sourceUrl":"data:video/mp4;base64,AAAA"}}]}"""),
        )

        assertNull(backing.getItem(store.storageKey))
    }

    @Test
    fun theDefaultStoreUsesTheBrowsersOwnLocalStorage() {
        val storageKey = key()
        localStorageKeys += storageKey
        val store = WebJobDocumentStorage(key = storageKey)
        val completed = InMemoryDownloadEngine.sampleJobs().first { it.id == "seed-completed" }
        val saved = JobDocumentStore(now = { 1_000L }).save(listOf(completed), clearAfterSeconds = 0)

        assertNull(store.write(saved.document))
        assertTrue(window.localStorage.getItem(storageKey) != null)

        val restored = JobDocumentStore(now = { 2_000L }).restore(store.read(), clearAfterSeconds = 0)
        assertEquals(completed.artifacts, restored.jobs.single().artifacts)
    }
}
