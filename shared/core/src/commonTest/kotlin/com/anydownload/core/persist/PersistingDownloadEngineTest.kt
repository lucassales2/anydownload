package com.anydownload.core.persist

import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobErrorCode
import com.anydownload.core.domain.JobState
import com.anydownload.core.fake.InMemoryDownloadEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-103: the shared wrapper hosts put around their engine. */
class PersistingDownloadEngineTest {

    private class MemoryStorage : JobDocumentStorage {
        var document: String? = null
        var nextError: JobDocumentStorageError? = null
        var writes = 0

        override fun read(): String? = document

        override fun write(document: String): JobDocumentStorageError? {
            writes++
            if (nextError != null) return nextError
            this.document = document
            return null
        }
    }

    @Test
    fun wrapperWritesAfterEachMutationAndAReplacementStoreSeesTheRow() {
        val storage = MemoryStorage()
        val engine = PersistingDownloadEngine(InMemoryDownloadEngine(), JobDocumentStore(), storage::write)

        val job = engine.submit(
            DownloadRequest(
                sourceUrl = "https://example.com/watch?v=fixture",
                idempotencyKey = "k1",
            ),
        )

        assertEquals(1, storage.writes)
        val replacement = JobDocumentStore(now = { 2_000L }).restore(storage.read(), clearAfterSeconds = 0)
        assertEquals(listOf(job.id), replacement.jobs.map { it.id })
        // The saved row was active; a replacement load marks it retryable.
        assertEquals(JobState.FAILED, replacement.jobs.single().state)
        assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, replacement.jobs.single().error?.code)
        assertTrue(replacement.jobs.single().error?.retryable == true)
    }

    @Test
    fun storageFullKeepsTheInMemoryListAndSurfacesTheTypedError() {
        val storage = MemoryStorage().apply { nextError = JobDocumentStorageError.FULL }
        val engine = PersistingDownloadEngine(InMemoryDownloadEngine(), JobDocumentStore(), storage::write)

        engine.submit(DownloadRequest(sourceUrl = "https://example.com/watch?v=fixture"))
        engine.submit(DownloadRequest(sourceUrl = "https://example.com/watch?v=two"))

        assertEquals(JobDocumentStorageError.FULL, engine.storageError.value)
        assertEquals(2, engine.jobs.value.size)
        assertNull(storage.document)

        storage.nextError = null
        engine.persistNow()
        assertNull(engine.storageError.value)
        assertNotNull(storage.document)
    }

    @Test
    fun completedArtifactStaysRegisteredAndAnActiveRowReloadsRetryable() {
        val storage = MemoryStorage()
        val completed = InMemoryDownloadEngine.sampleJobs().first { it.id == "seed-completed" }
        val downloading = InMemoryDownloadEngine.sampleJobs().first { it.id == "seed-downloading" }
        val writer = JobDocumentStore(now = { 1_000L })
        val saved = writer.save(listOf(completed, downloading), clearAfterSeconds = 0)
        assertNull(storage.write(saved.document))

        val restored = JobDocumentStore(now = { 2_000L }).restore(storage.read(), clearAfterSeconds = 0)
        val byId = restored.jobs.associateBy { it.id }

        assertEquals(completed.artifacts, byId.getValue("seed-completed").artifacts)
        assertEquals(JobState.COMPLETED, byId.getValue("seed-completed").state)
        assertEquals(JobState.FAILED, byId.getValue("seed-downloading").state)
        assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, byId.getValue("seed-downloading").error?.code)
        assertTrue(byId.getValue("seed-downloading").error?.retryable == true)
    }
}
