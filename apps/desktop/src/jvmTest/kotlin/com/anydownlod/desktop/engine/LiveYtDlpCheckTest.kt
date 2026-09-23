package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.ArtifactKind
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.desktop.DesktopApp
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.desktop.store.DesktopStore
import com.anydownlod.desktop.subscriptions.DesktopSubscriptionRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Opt-in network checks against the installed yt-dlp. They never run unless
 * the paths are supplied:
 *   ./gradlew :apps:desktop:test -Danydownlod.live.url=... \
 *       -Danydownlod.live.cancelUrl=... -Danydownlod.live.failureUrl=... \
 *       -Danydownlod.live.root=/tmp/anydownlod-live
 *
 * No media URL is stored in the repository.
 */
class LiveYtDlpCheckTest {

    private fun liveValue(property: String, env: String): String? =
        System.getProperty(property)?.takeIf { it.isNotBlank() }
            ?: System.getenv(env)?.takeIf { it.isNotBlank() }

    private fun liveEngine(root: String): Pair<YtDlpCliEngine, CoroutineScope> {
        val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return YtDlpCliEngine(settingsRepository = settings, scope = scope) to scope
    }

    @Test
    fun realDownloadCompletesAndRegistersAnArtifact() = runBlocking {
        val url = liveValue("anydownlod.live.url", "ANYDOWNLOAD_LIVE_URL")
        val root = liveValue("anydownlod.live.root", "ANYDOWNLOAD_LIVE_ROOT")
        assumeTrue("Set the live URL and root to run this check", url != null && root != null)

        val (engine, scope) = liveEngine(root!!)
        try {
            val job = engine.submit(DownloadRequest(sourceUrl = url!!))
            val finished = withTimeout(180_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state.isTerminal } }
            }.first { it.id == job.id }

            println(
                "live download: state=${finished.state} title=${finished.title} " +
                    "percent=${finished.progress?.percent} artifacts=${finished.artifacts.map { it.fileName }}"
            )
            assertEquals(JobState.COMPLETED, finished.state, "error=${finished.error}")
            val artifact = assertNotNull(finished.artifacts.firstOrNull())
            val file = Path.of(root, artifact.relativePath)
            assertTrue(Files.exists(file))
            assertTrue(Files.size(file) > 0)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun realCancelStopsTheLiveProcessTree() = runBlocking {
        val url = liveValue("anydownlod.live.cancelUrl", "ANYDOWNLOAD_LIVE_CANCEL_URL")
        val root = liveValue("anydownlod.live.root", "ANYDOWNLOAD_LIVE_ROOT")
        assumeTrue("Set the cancel URL and root to run this check", url != null && root != null)

        val (engine, scope) = liveEngine(root!!)
        try {
            val job = engine.submit(DownloadRequest(sourceUrl = url!!))
            withTimeout(120_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.DOWNLOADING } }
            }

            engine.cancel(job.id)

            assertEquals(JobState.CANCELLED, engine.jobs.value.single().state)
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline && liveYtDlpProcesses().isNotEmpty()) {
                delay(200)
            }
            val survivors = liveYtDlpProcesses()
            survivors.forEach { handle ->
                println("surviving process: ${handle.info().command().orElse("?")} :: ${handle.info().commandLine().orElse("?")}")
            }
            assertTrue(survivors.isEmpty(), "a yt-dlp process survived cancel")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun realUnsupportedUrlFailsWithARedactedError() = runBlocking {
        val url = liveValue("anydownlod.live.failureUrl", "ANYDOWNLOAD_LIVE_FAILURE_URL")
        val root = liveValue("anydownlod.live.root", "ANYDOWNLOAD_LIVE_ROOT")
        assumeTrue("Set the failure URL and root to run this check", url != null && root != null)

        val (engine, scope) = liveEngine(root!!)
        try {
            val job = engine.submit(DownloadRequest(sourceUrl = url!!))
            val finished = withTimeout(120_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state.isTerminal } }
            }.first { it.id == job.id }

            println("live failure: state=${finished.state} code=${finished.error?.code} message=${finished.error?.message}")
            assertEquals(JobState.FAILED, finished.state)
            val error = assertNotNull(finished.error)
            assertFalse(error.message.contains("example.com"))
            assertFalse(error.message.contains("cookie", ignoreCase = true))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun realAudioExtractProducesAnAudioArtifact() = runBlocking {
        val url = liveValue("anydownlod.live.audioUrl", "ANYDOWNLOAD_LIVE_AUDIO_URL")
        val root = liveValue("anydownlod.live.root", "ANYDOWNLOAD_LIVE_ROOT")
        assumeTrue("Set the audio URL and root to run this check", url != null && root != null)

        val (engine, scope) = liveEngine(root!!)
        try {
            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = url!!,
                    options = DownloadOptions(
                        mediaType = MediaType.AUDIO,
                        audioContainer = AudioContainer.MP3,
                        audioBitrate = "192",
                    ),
                ),
            )
            val finished = awaitTerminal(engine, job.id)
            println("live audio: state=${finished.state} artifacts=${finished.artifacts.map { it.fileName }}")
            assertEquals(JobState.COMPLETED, finished.state, "error=${finished.error}")
            assertTrue(finished.artifacts.any { it.kind == ArtifactKind.AUDIO })
            assertTrue(finished.artifacts.any { it.fileName.endsWith(".mp3") })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun realPlaylistExpandsIntoChildJobsWithTheItemLimit() = runBlocking {
        val url = liveValue("anydownlod.live.playlistUrl", "ANYDOWNLOAD_LIVE_PLAYLIST_URL")
        val root = liveValue("anydownlod.live.root", "ANYDOWNLOAD_LIVE_ROOT")
        assumeTrue("Set the playlist URL and root to run this check", url != null && root != null)

        val (engine, scope) = liveEngine(root!!)
        try {
            val parent = engine.submit(
                DownloadRequest(sourceUrl = url!!, options = DownloadOptions(playlistItemLimit = 2)),
            )
            val expanded = withTimeout(180_000) {
                engine.jobs.first { jobs -> jobs.count { it.parentBatchId == parent.id } == 2 }
            }
            val children = expanded.filter { it.parentBatchId == parent.id }
            println("live playlist: parent=${parent.title} title=${expanded.first { it.id == parent.id }.title} children=${children.size}")
            assertEquals(2, children.size, "the item limit must cap the expansion")
            assertTrue(children.none { it.request.sourceUrl == url })

            // Stop the live downloads; expansion and cancellability are the point.
            children.forEach { engine.cancel(it.id) }
            assertTrue(children.all { engine.jobs.value.first { job -> job.id == it.id }.state.isTerminal })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun realBatchOfTwoUrlsRunsAsSeparateJobs() = runBlocking {
        val url = liveValue("anydownlod.live.batchUrl", "ANYDOWNLOAD_LIVE_BATCH_URL")
        val root = liveValue("anydownlod.live.root", "ANYDOWNLOAD_LIVE_ROOT")
        assumeTrue("Set the batch URL and root to run this check", url != null && root != null)

        val (engine, scope) = liveEngine(root!!)
        try {
            val first = engine.submit(DownloadRequest(sourceUrl = url!!, idempotencyKey = "batch-1"))
            val second = engine.submit(DownloadRequest(sourceUrl = url!!, idempotencyKey = "batch-2"))
            assertNotEquals(first.id, second.id)

            val finishedFirst = awaitTerminal(engine, first.id)
            val finishedSecond = awaitTerminal(engine, second.id)
            println("live batch: first=${finishedFirst.state} second=${finishedSecond.state}")
            assertEquals(JobState.COMPLETED, finishedFirst.state, "error=${finishedFirst.error}")
            assertEquals(JobState.COMPLETED, finishedSecond.state, "error=${finishedSecond.error}")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun realThumbnailOnlyProducesAnImageArtifact() = runBlocking {
        val url = liveValue("anydownlod.live.thumbnailUrl", "ANYDOWNLOAD_LIVE_THUMBNAIL_URL")
        val root = liveValue("anydownlod.live.root", "ANYDOWNLOAD_LIVE_ROOT")
        assumeTrue("Set the thumbnail URL and root to run this check", url != null && root != null)

        val (engine, scope) = liveEngine(root!!)
        try {
            val job = engine.submit(
                DownloadRequest(sourceUrl = url!!, options = DownloadOptions(mediaType = MediaType.THUMBNAIL)),
            )
            val finished = awaitTerminal(engine, job.id)
            println("live thumbnail: state=${finished.state} artifacts=${finished.artifacts.map { it.fileName }}")
            assertEquals(JobState.COMPLETED, finished.state, "error=${finished.error}")
            assertTrue(finished.artifacts.any { it.kind == ArtifactKind.THUMBNAIL })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun realSubscriptionFirstCheckMarksSeenAndEnqueuesNothing() = runBlocking {
        val url = liveValue("anydownlod.live.subscriptionUrl", "ANYDOWNLOAD_LIVE_SUBSCRIPTION_URL")
        val root = liveValue("anydownlod.live.root", "ANYDOWNLOAD_LIVE_ROOT")
        assumeTrue("Set the subscription URL and root to run this check", url != null && root != null)

        val directory = Files.createTempDirectory("anydownlod-live-subs-")
        val store = DesktopStore(directory, now = { 1L })
        val settingsRepository = InMemorySettingsRepository(AppSettings(downloadRoot = root!!))
        val engine = InMemoryDownloadEngine()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val delegate = InMemorySubscriptionRepository()
        val subscription = delegate.add(
            sourceUrl = url!!,
            displayName = "Live channel",
            downloadOptions = DownloadOptions(),
        )
        val repository = DesktopSubscriptionRepository(
            delegate = delegate,
            store = store,
            engine = engine,
            settings = settingsRepository,
            scope = scope,
            scanLimit = 2,
        )
        try {
            repository.runCheck(subscription.id)
            val afterFirst = delegate.subscriptions.value.single()
            println(
                "live subscription: seen=${afterFirst.seenIds.size} jobs=${engine.jobs.value.size} " +
                    "error=${afterFirst.lastError?.message}"
            )
            assertNotNull(afterFirst.lastCheckedAtEpochMillis)
            assertTrue(afterFirst.seenIds.isNotEmpty(), "the first check must mark current items seen")
            assertTrue(engine.jobs.value.isEmpty(), "the first check must not enqueue the back catalog")

            repository.runCheck(subscription.id)
            assertTrue(engine.jobs.value.isEmpty(), "nothing new should be enqueued on the second check")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun realColdStartShowsEmptyStateAndToolVersions() = runBlocking {
        val root = liveValue("anydownlod.live.root", "ANYDOWNLOAD_LIVE_ROOT")
        assumeTrue("Set the live root to run this check", root != null)

        val stateDirectory = Files.createTempDirectory("anydownlod-live-cold-")
        val desktop = DesktopApp.open(
            stateDirectory = stateDirectory,
            defaultDownloadRoot = { root!! },
        )
        try {
            assertTrue(desktop.graph.engine.jobs.value.isEmpty())
            assertTrue(desktop.graph.subscriptions.subscriptions.value.isEmpty())
            val tools = desktop.graph.toolProbe.probe()
            println("live tools: yt-dlp=${tools.ytDlp.version} ffmpeg=${tools.ffmpeg.version}")
            assertTrue(tools.ytDlp.available)
            assertTrue(tools.ffmpeg.available)
        } finally {
            desktop.close()
        }
    }

    private suspend fun awaitTerminal(engine: YtDlpCliEngine, jobId: String): com.anydownlod.core.domain.DownloadJob =
        withTimeout(180_000) {
            engine.jobs.first { jobs -> jobs.any { it.id == jobId && it.state.isTerminal } }
        }.first { it.id == jobId }

    private fun liveYtDlpProcesses() = ProcessHandle.allProcesses()
        .filter { handle ->
            val info = handle.info()
            val command = info.command().orElse("")
            val commandLine = info.commandLine().orElse("")
            val commandName = command.substringAfterLast('/').lowercase()
            val isShellOrJvm = commandName in setOf("java", "bash", "zsh", "sh", "dash", "gradle", "gradlew")
            !isShellOrJvm && commandLine.contains("bin/yt-dlp")
        }
        .toList()
}
