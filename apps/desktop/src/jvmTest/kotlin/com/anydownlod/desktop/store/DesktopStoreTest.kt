package com.anydownlod.desktop.store

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.ClipboardAccess
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.CaptionPreference
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobError
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.Preset
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.domain.Subscription
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile
import com.anydownlod.core.fake.InMemoryDownloadEngine
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopStoreTest {

    private fun tempDir(): Path = Files.createTempDirectory("anydownlod-store-")

    private fun sampleJobs(): List<DownloadJob> = InMemoryDownloadEngine.sampleJobs()

    @Test
    fun everyJobStateAndOptionRoundTripsThroughTheDto() {
        val base = sampleJobs()
        val extras = listOf(
            base.first { it.id == "seed-pending" }.copy(id = "seed-queued", state = JobState.QUEUED),
            base.first { it.id == "seed-pending" }.copy(id = "seed-resolving", state = JobState.RESOLVING),
            base.first { it.id == "seed-failed" }.copy(id = "seed-cancelled", state = JobState.CANCELLED),
            base.first { it.id == "seed-failed" }.copy(id = "seed-unknown", state = JobState.UNKNOWN),
        )
        (base + extras).forEach { job ->
            assertEquals(job, job.toDto().toDomain(), "state ${job.state} did not round-trip")
        }

        val richOptions = DownloadOptions(
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
            clipStart = "10",
            clipEnd = "1:30",
            splitByChapters = true,
            sponsorBlockRemove = true,
            presetIds = listOf("p1", "p2"),
            useCookies = true,
        )
        val rich = base.first().copy(
            request = DownloadRequest("https://example.com/watch?v=fixture", richOptions, "key-1"),
        )
        val restored = rich.toDto().toDomain()
        assertEquals(rich, restored)
        assertEquals("", restored.request.options.customYtDlpJson)
    }

    @Test
    fun activeJobsBecomeRetryableFailuresAndPendingSurvives() {
        val dir = tempDir()
        DesktopStore(dir, now = { 1_000_000L }).saveJobs(sampleJobs())

        val loaded = DesktopStore(dir, now = { 1_000_000L }).load()
        val byId = loaded.jobs.associateBy { it.id }

        listOf("seed-downloading", "seed-downloading-unknown-total", "seed-postprocessing").forEach { id ->
            val job = byId.getValue(id)
            val error = job.error
            assertEquals(JobState.FAILED, job.state, id)
            assertNotNull(error, id)
            assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, error.code, id)
            assertTrue(error.retryable, id)
            assertTrue(error.message.contains("closed"), id)
            assertEquals(JobState.FAILED, job.latestAttempt?.state, id)
        }

        assertEquals(JobState.PENDING, byId.getValue("seed-pending").state)
        assertEquals(JobState.SCHEDULED, byId.getValue("seed-scheduled").state)
        assertEquals(JobState.COMPLETED, byId.getValue("seed-completed").state)
        assertEquals(JobState.FAILED, byId.getValue("seed-failed").state)
    }

    @Test
    fun atomicWriteKeepsThePreviousFileWhenTheWriterThrows() {
        val dir = tempDir()
        val job = sampleJobs().first { it.id == "seed-pending" }.copy(revision = 5, title = "new")
        val good = DesktopStore(dir, now = { 1L })
        good.saveJobs(listOf(job))
        val before = Files.readString(good.jobsFile)

        val failing = DesktopStore(dir, now = { 2L }, beforeRename = { throw IOException("boom") })
        val result = runCatching {
            failing.saveJobs(listOf(job.copy(revision = 6, title = "should not land")))
        }

        assertTrue(result.isFailure)
        assertEquals(before, Files.readString(good.jobsFile))
        assertFalse(Files.list(dir).anyMatch { it.fileName.toString().endsWith(".tmp") })
    }

    @Test
    fun corruptFileIsMovedAsideAndLoadWarns() {
        val dir = tempDir()
        Files.writeString(dir.resolve("jobs.json"), "{ not json")

        val store = DesktopStore(dir, now = { 42L })
        val loaded = store.load()

        assertTrue(loaded.jobs.isEmpty())
        assertNotNull(store.loadWarning)
        assertTrue(store.loadWarning!!.contains("jobs.json"))
        assertTrue(Files.list(dir).anyMatch { it.fileName.toString() == "jobs.json.corrupt-42" })
    }

    @Test
    fun emptyFileLoadsAsEmptyStateWithoutAWarning() {
        val dir = tempDir()
        Files.writeString(dir.resolve("jobs.json"), "")

        val store = DesktopStore(dir, now = { 1L })
        val loaded = store.load()

        assertTrue(loaded.jobs.isEmpty())
        assertNull(store.loadWarning)
    }

    @Test
    fun staleRevisionDoesNotRegressTheFile() {
        val store = DesktopStore(tempDir(), now = { 1L })
        val job = sampleJobs().first { it.id == "seed-pending" }.copy(revision = 5, title = "new")
        store.saveJobs(listOf(job))

        assertEquals(0, store.saveJobs(listOf(job.copy(revision = 6, title = "newer"))).skippedStale)

        val staleResult = store.saveJobs(listOf(job.copy(revision = 4, title = "stale")))

        assertEquals(1, staleResult.skippedStale)
        val reloaded = DesktopStore(store.stateDirectory, now = { 2L }).load()
        assertEquals("newer", reloaded.jobs.single().title)
    }

    @Test
    fun cookieJsonHasNoValueOrContentsFieldAndCustomJsonIsNotPersisted() {
        val dir = tempDir()
        val store = DesktopStore(dir, now = { 1L })
        store.saveSettings(AppSettings(cookiesConfigured = true))
        store.saveJobs(sampleJobs())

        val settingsJson = Files.readString(store.settingsFile)
        assertFalse(settingsJson.contains("\"value\""))
        assertFalse(settingsJson.contains("\"contents\""))
        assertTrue(settingsJson.contains("cookiesConfigured"))

        val jobsJson = Files.readString(store.jobsFile)
        assertFalse(jobsJson.contains("customYtDlp"))
    }

    @Test
    fun clearCompletedDropsOldTerminalRowsOnSaveAndLoad() {
        val now = 1_000_000L
        val old = sampleJobs().first { it.id == "seed-completed" }.copy(
            finishedAtEpochMillis = now - 120_000,
            updatedAtEpochMillis = now - 120_000,
        )
        val recent = sampleJobs().first { it.id == "seed-failed" }.copy(
            finishedAtEpochMillis = now - 10_000,
            updatedAtEpochMillis = now - 10_000,
        )
        val dir = tempDir()
        val store = DesktopStore(dir, now = { now })
        store.saveSettings(AppSettings(clearCompletedAfterSeconds = 60))

        val result = store.saveJobs(listOf(old, recent))

        assertEquals(1, result.clearedExpired)
        val loaded = DesktopStore(dir, now = { now }).load()
        assertEquals(listOf("seed-failed"), loaded.jobs.map { it.id })
    }

    @Test
    fun unsafeStoredDestinationFolderIsCleared() {
        val dir = tempDir()
        val job = sampleJobs().first().copy(
            request = DownloadRequest(
                sourceUrl = "https://example.com/watch?v=fixture",
                options = DownloadOptions(destinationFolder = "../escape"),
            ),
        )
        DesktopStore(dir, now = { 1L }).saveJobs(listOf(job))

        val loaded = DesktopStore(dir, now = { 2L }).load()

        assertNull(loaded.jobs.single().request.options.destinationFolder)
        assertNotNull(loaded.jobs.single().request.options)
    }

    @Test
    fun settingsSubscriptionsAndPresetsRoundTrip() {
        val dir = tempDir()
        val store = DesktopStore(dir, now = { 1L })
        val settings = AppSettings(
            downloadRoot = "/tmp/downloads",
            theme = ThemePreference.DARK,
            clipboardAccess = ClipboardAccess.ALLOWED,
            handledClipboardUrl = "https://example.com/watch?v=1",
            maxConcurrentDownloads = 4,
            clearCompletedAfterSeconds = 300,
            cookiesConfigured = true,
            presets = listOf(Preset("p1", "Small", mapOf("writeMetadata" to "true"))),
        )
        val subscription = Subscription(
            id = "sub-1",
            sourceUrl = "https://example.com/channel/fixture",
            displayName = "Fixture",
            paused = true,
            checkIntervalMinutes = 30,
            titleFilterRegex = "episode",
            skipMembersOnly = true,
            downloadOptions = DownloadOptions(
                mediaType = MediaType.AUDIO,
                audioContainer = AudioContainer.MP3,
            ),
            lastCheckedAtEpochMillis = 123L,
            nextCheckAtEpochMillis = null,
            lastError = JobError(JobErrorCode.RATE_LIMITED, "Slow down.", retryable = true),
            seenIds = listOf("a", "b"),
        )
        store.saveSettings(settings)
        store.saveSubscriptions(listOf(subscription))

        val loaded = DesktopStore(dir, now = { 2L }).load()

        assertEquals(settings, loaded.settings)
        assertEquals(listOf(subscription), loaded.subscriptions)
    }

    @Test
    fun blankDownloadRootGetsTheDefault() {
        val loaded = DesktopStore(
            stateDirectory = tempDir(),
            now = { 1L },
            defaultDownloadRoot = { "/default/downloads" },
        ).load()

        assertEquals("/default/downloads", loaded.settings.downloadRoot)
        assertTrue(loaded.jobs.isEmpty())
    }

    @Test
    fun persistingEngineWritesAfterEveryMutation() {
        val dir = tempDir()
        val store = DesktopStore(dir, now = { 1L })
        val engine = PersistingDownloadEngine(InMemoryDownloadEngine(), store)

        val job = engine.submit(DownloadRequest(sourceUrl = "https://example.com/watch?v=fixture"))
        assertEquals(1, DesktopStore(dir, now = { 2L }).load().jobs.size)

        engine.cancel(job.id)
        assertEquals(JobState.CANCELLED, DesktopStore(dir, now = { 3L }).load().jobs.single().state)

        engine.removeHistory(job.id)
        assertTrue(DesktopStore(dir, now = { 4L }).load().jobs.isEmpty())
    }
}
