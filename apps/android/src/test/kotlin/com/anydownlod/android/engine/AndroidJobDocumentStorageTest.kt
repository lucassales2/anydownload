package com.anydownlod.android.engine

import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.core.persist.JobDocumentStorageError
import com.anydownlod.core.persist.JobDocumentStore
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidJobDocumentStorageTest {

    private fun stateFile(): File =
        File(Files.createTempDirectory("anydownlod-android-state-").toFile(), "state/jobs.json")

    @Test
    fun completedArtifactAndInterruptedRowRoundTripThroughAnAppStateFile() {
        val downloadRoot = Files.createTempDirectory("anydownlod-android-downloads-").toFile()
        val storage = AndroidJobDocumentStorage(stateFile())
        assertTrue(
            !storage.path.startsWith(downloadRoot.absolutePath + File.separator),
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
    fun missingFileReadsAsNullAndAnUnwritablePathIsTyped() {
        val missing = AndroidJobDocumentStorage(File(stateFile().parentFile, "absent.json"))
        assertNull(missing.read())

        val blockedParent = File(Files.createTempDirectory("anydownlod-blocked-").toFile(), "not-a-dir")
        blockedParent.writeText("x")
        val unwritable = AndroidJobDocumentStorage(File(blockedParent, "jobs.json"))
        assertEquals(JobDocumentStorageError.UNAVAILABLE, unwritable.write("{}"))
    }

    @Test
    fun resumeRouteSplitsPersistedJobsWithoutNetwork() {
        assertEquals(
            AndroidRoute.DIRECT_FILE,
            AndroidRouteClassifier.resumeRoute("https://example.com/media/clip.mp4"),
        )
        assertEquals(
            AndroidRoute.CHAQUOPY,
            AndroidRouteClassifier.resumeRoute("https://example.com/watch?v=fixture"),
        )
    }
}
