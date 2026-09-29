package com.anydownlod.ui.persist

import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.core.persist.JobDocumentStore
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosJobDocumentStorageTest {

    private fun statePath(): String {
        val directory = NSTemporaryDirectory().trimEnd('/') + "/anydownlod-jobs-" + NSUUID().UUIDString
        return "$directory/state/jobs.json"
    }

    @Test
    fun completedArtifactAndInterruptedRowRoundTripOutsideTheDownloadRoot() {
        val downloadRoot = NSTemporaryDirectory().trimEnd('/') + "/anydownlod-downloads-" + NSUUID().UUIDString
        val storage = IosJobDocumentStorage(statePath())
        assertTrue(
            !storage.filePath.startsWith(downloadRoot),
            "the state file must not live inside the download root",
        )

        val completed = InMemoryDownloadEngine.sampleJobs().first { it.id == "seed-completed" }
        val downloading = InMemoryDownloadEngine.sampleJobs().first { it.id == "seed-downloading" }
        val saved = JobDocumentStore(now = { 1_000L }).save(listOf(completed, downloading), clearAfterSeconds = 0)
        assertNull(storage.write(saved.document))

        val restored = JobDocumentStore(now = { 2_000L }).restore(storage.read(), clearAfterSeconds = 0)
        val byId = restored.jobs.associateBy { it.id }

        assertEquals(completed.artifacts, byId.getValue("seed-completed").artifacts)
        assertEquals(JobState.COMPLETED, byId.getValue("seed-completed").state)
        assertEquals(JobState.FAILED, byId.getValue("seed-downloading").state)
        assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, byId.getValue("seed-downloading").error?.code)
        assertTrue(byId.getValue("seed-downloading").error?.retryable == true)
    }

    @Test
    fun missingFileReadsAsNull() {
        assertNull(IosJobDocumentStorage(statePath()).read())
    }
}
