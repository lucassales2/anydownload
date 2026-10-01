package com.anydownload.core.persist

import com.anydownload.core.domain.AudioContainer
import com.anydownload.core.domain.CaptionFormat
import com.anydownload.core.domain.CaptionPreference
import com.anydownload.core.domain.DownloadJob
import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobError
import com.anydownload.core.domain.JobErrorCode
import com.anydownload.core.domain.JobState
import com.anydownload.core.domain.MediaType
import com.anydownload.core.domain.OverwriteMode
import com.anydownload.core.domain.QualityPreference
import com.anydownload.core.domain.StartPolicy
import com.anydownload.core.domain.VideoCodec
import com.anydownload.core.domain.VideoContainerProfile
import com.anydownload.core.fake.InMemoryDownloadEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T-102: the shared job document owns interrupt-on-load, stale revisions,
 * destination sanitizing, and clear-completed. These tests run on every
 * target the shared module builds for.
 */
class JobDocumentStoreTest {

    private val at = 1_000_000L

    private fun store(now: Long = at) = JobDocumentStore(now = { now })

    private fun sampleJobs(): List<DownloadJob> = InMemoryDownloadEngine.sampleJobs()

    @Test
    fun activeStatesLoadAsRetryableEngineUnavailableAndOtherStatesStay() {
        val base = sampleJobs()
        val extras = listOf(
            base.first { it.id == "seed-pending" }.copy(id = "seed-resolving", state = JobState.RESOLVING),
            base.first { it.id == "seed-pending" }.copy(id = "seed-queued", state = JobState.QUEUED),
            base.first { it.id == "seed-failed" }.copy(id = "seed-cancelled", state = JobState.CANCELLED),
        )

        val restored = store().restore(
            text = JobDocumentCodec.encode(base + extras),
            clearAfterSeconds = 0,
        )
        assertEquals(5, restored.interruptedActive)
        val byId = restored.jobs.associateBy { it.id }

        listOf(
            "seed-resolving",
            "seed-queued",
            "seed-downloading",
            "seed-downloading-unknown-total",
            "seed-postprocessing",
        ).forEach { id ->
            val job = byId.getValue(id)
            assertEquals(JobState.FAILED, job.state, id)
            val error = job.error
            assertNotNull(error, id)
            assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, error.code, id)
            assertTrue(error.retryable, id)
            assertEquals(JobState.FAILED, job.latestAttempt?.state, id)
        }

        assertEquals(JobState.PENDING, byId.getValue("seed-pending").state)
        assertEquals(JobState.SCHEDULED, byId.getValue("seed-scheduled").state)
        assertEquals(JobState.COMPLETED, byId.getValue("seed-completed").state)
        assertEquals(JobState.FAILED, byId.getValue("seed-failed").state)
        assertEquals(JobState.CANCELLED, byId.getValue("seed-cancelled").state)
    }

    @Test
    fun staleRevisionNeverReplacesANewerRow() {
        val document = store()
        val job = sampleJobs().first { it.id == "seed-pending" }.copy(revision = 5, title = "new")
        document.save(listOf(job), clearAfterSeconds = 0)

        assertEquals(0, document.save(listOf(job.copy(revision = 6, title = "newer")), 0).skippedStale)

        val stale = document.save(listOf(job.copy(revision = 4, title = "stale")), 0)
        assertEquals(1, stale.skippedStale)
        assertEquals("newer", JobDocumentCodec.decode(stale.document).single().title)
    }

    @Test
    fun unsafeDestinationIsClearedWhileASafeOneIsKept() {
        val base = sampleJobs().first { it.id == "seed-completed" }
        val unsafe = base.copy(
            id = "unsafe",
            request = DownloadRequest(
                sourceUrl = "https://example.com/watch?v=fixture",
                options = DownloadOptions(destinationFolder = "../escape"),
            ),
        )
        val safe = base.copy(
            id = "safe",
            request = DownloadRequest(
                sourceUrl = "https://example.com/watch?v=fixture",
                options = DownloadOptions(destinationFolder = "audio/2026"),
            ),
        )

        val restored = store().restore(JobDocumentCodec.encode(listOf(unsafe, safe)), clearAfterSeconds = 0)

        assertEquals(1, restored.sanitizedDestinations)
        val byId = restored.jobs.associateBy { it.id }
        assertNull(byId.getValue("unsafe").request.options.destinationFolder)
        assertEquals("audio/2026", byId.getValue("safe").request.options.destinationFolder)
    }

    @Test
    fun clearCompletedDropsOnlyRowsTheSettingNamesOnRestoreAndSave() {
        val oldCompleted = sampleJobs().first { it.id == "seed-completed" }.copy(
            finishedAtEpochMillis = at - 120_000,
            updatedAtEpochMillis = at - 120_000,
        )
        val recentFailed = sampleJobs().first { it.id == "seed-failed" }.copy(
            finishedAtEpochMillis = at - 10_000,
            updatedAtEpochMillis = at - 10_000,
        )
        val oldCancelled = sampleJobs().first { it.id == "seed-failed" }.copy(
            id = "old-cancelled",
            state = JobState.CANCELLED,
            finishedAtEpochMillis = at - 200_000,
            updatedAtEpochMillis = at - 200_000,
        )
        val jobs = listOf(oldCompleted, recentFailed, oldCancelled)

        val restored = store().restore(JobDocumentCodec.encode(jobs), clearAfterSeconds = 60)
        assertEquals(2, restored.clearedExpired)
        assertEquals(listOf("seed-failed"), restored.jobs.map { it.id })

        val saved = store().save(jobs, clearAfterSeconds = 60)
        assertEquals(2, saved.clearedExpired)
        assertEquals(listOf("seed-failed"), JobDocumentCodec.decode(saved.document).map { it.id })
    }

    @Test
    fun completedJobArtifactListSurvivesALoad() {
        val completed = sampleJobs().first { it.id == "seed-completed" }
        assertTrue(completed.artifacts.isNotEmpty())

        val restored = store().restore(JobDocumentCodec.encode(listOf(completed)), clearAfterSeconds = 0)

        assertEquals(JobState.COMPLETED, restored.jobs.single().state)
        assertEquals(completed.artifacts, restored.jobs.single().artifacts)
        assertEquals(0, restored.interruptedActive)
    }

    @Test
    fun documentHasNoCookieMaterialOrCustomYtDlpJson() {
        val job = sampleJobs().first().copy(
            id = "flagged",
            request = DownloadRequest(
                sourceUrl = "https://example.com/watch?v=fixture",
                options = DownloadOptions(useCookies = true),
            ),
        )

        val text = JobDocumentCodec.encode(listOf(job))

        // The cookie state is a boolean flag only; no value or contents exist.
        assertTrue(text.contains("\"useCookies\": true"))
        assertFalse(text.contains("customYtDlp"))
        assertFalse(text.contains("cookieValue"))
        assertFalse(text.contains("cookieContents"))
        assertFalse(text.contains("Netscape"))
        assertFalse(text.contains("mediaBytes"))
    }

    @Test
    fun unknownStateWireNameStaysVisibleAndUnknownFieldsAreIgnored() {
        val text = """
            {
              "jobs": [
                {
                  "id": "future",
                  "request": { "sourceUrl": "https://example.com/watch?v=fixture" },
                  "state": "paused",
                  "revision": 3,
                  "somethingNew": true
                }
              ],
              "newTopLevel": 42
            }
        """.trimIndent()

        val restored = store().restore(text, clearAfterSeconds = 0).jobs.single()

        assertEquals("future", restored.id)
        assertEquals(JobState.UNKNOWN, restored.state)
        assertEquals(3, restored.revision)
    }

    @Test
    fun documentsWrittenBeforeTheSharedCodecStillLoad() {
        val text = """
            {
              "jobs": [
                {
                  "id": "legacy",
                  "request": {
                    "sourceUrl": "https://example.com/watch?v=fixture",
                    "options": {}
                  },
                  "state": "completed",
                  "revision": 2,
                  "artifacts": [
                    {
                      "id": "a",
                      "jobId": "legacy",
                      "kind": "video",
                      "fileName": "Legacy.mp4",
                      "relativePath": "Legacy.mp4"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val job = JobDocumentCodec.decode(text).single()

        assertEquals(JobState.COMPLETED, job.state)
        assertEquals(0, job.formatsNeedingJs)
        assertTrue(job.request.selectedMediaIds.isEmpty())
        assertEquals(1, job.artifacts.size)
        assertEquals("Legacy.mp4", job.artifacts.single().relativePath)
    }

    @Test
    fun richOptionsRoundTripThroughTheCodec() {
        val rich = sampleJobs().first().copy(
            id = "rich",
            request = DownloadRequest(
                sourceUrl = "https://example.com/watch?v=fixture",
                idempotencyKey = "key-1",
                options = DownloadOptions(
                    mediaType = MediaType.AUDIO,
                    startPolicy = StartPolicy.MANUAL,
                    videoProfile = VideoContainerProfile.MP4,
                    videoCodec = VideoCodec.HEVC,
                    quality = QualityPreference.Resolution("1440"),
                    audioContainer = AudioContainer.FLAC,
                    audioBitrate = "320",
                    captionLanguage = "en",
                    captionPreference = CaptionPreference.MANUAL,
                    captionFormat = CaptionFormat.VTT,
                    embedSubtitles = true,
                    writeMetadata = true,
                    writeThumbnail = true,
                    filenamePrefix = "pre-",
                    destinationFolder = "audio/2026",
                    playlistItemLimit = 5,
                    overwrite = OverwriteMode.FORCE,
                    clipStart = "10",
                    clipEnd = "1:30",
                    splitByChapters = true,
                    sponsorBlockRemove = true,
                    presetIds = listOf("p1", "p2"),
                    useCookies = true,
                ),
            ),
            error = JobError(JobErrorCode.RATE_LIMITED, "Slow down.", retryable = true),
        )

        val restored = store().restore(JobDocumentCodec.encode(listOf(rich)), clearAfterSeconds = 0).jobs.single()

        assertEquals(rich, restored)
        assertEquals("", restored.request.options.customYtDlpJson)
    }
}
