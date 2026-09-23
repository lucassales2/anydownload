package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.ArtifactKind
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.fake.InMemorySettingsRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YtDlpCliEngineTest {

    private fun engine(
        root: Path,
        runner: CliProcessRunner,
        resolve: (String) -> String? = { "/fake/yt-dlp" },
        persist: (List<DownloadJob>) -> Unit = {},
    ): Pair<YtDlpCliEngine, CoroutineScope> {
        val settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString()))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = YtDlpCliEngine(
            settingsRepository = settings,
            scope = scope,
            runner = runner,
            resolveExecutable = resolve,
            ioDispatcher = Dispatchers.Default,
            persist = persist,
        )
        return engine to scope
    }

    private fun request(options: DownloadOptions = DownloadOptions()) =
        DownloadRequest(sourceUrl = "https://example.com/watch?v=fixture", options = options)

    @Test
    fun missingYtDlpFailsImmediatelyWithoutStartingAnything() {
        var started = false
        val (engine, scope) = engine(
            root = Files.createTempDirectory("anydownlod-engine-"),
            runner = CliProcessRunner { _, _ -> started = true; FakeCliProcess(emptyList()) },
            resolve = { null },
        )
        try {
            val job = engine.submit(request())

            assertEquals(JobState.FAILED, job.state)
            assertEquals(JobErrorCode.ENGINE_UNAVAILABLE, job.error?.code)
            assertTrue(job.error!!.message.contains("yt-dlp"))
            assertFalse(started)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun successfulDownloadRegistersAnArtifact() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-engine-")
        val file = root.resolve("One public video.mp4")
        Files.writeString(file, "fake media")
        val lines = listOf(
            "TITLE|One public video",
            "DL|downloading|10485760|NA|10485760|1048576.0|5",
            "PP|started",
            "FILE|$file",
        )
        val (engine, scope) = engine(root, CliProcessRunner { _, _ -> FakeCliProcess(lines, exitCode = 0) })
        try {
            val job = engine.submit(request())
            val finished = withTimeout(5_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.COMPLETED } }
            }.first { it.id == job.id }

            assertEquals("One public video", finished.title)
            assertEquals(1, finished.artifacts.size)
            assertEquals("One public video.mp4", finished.artifacts.single().fileName)
            assertEquals(ArtifactKind.VIDEO, finished.artifacts.single().kind)
            assertEquals(10L, finished.artifacts.single().sizeBytes)
            assertEquals(100.0, finished.progress?.percent)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancelDestroysTheProcessAndRecordsCancelled() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-engine-")
        val process = BlockingCliProcess()
        val runner = CliProcessRunner { command, _ ->
            if (command.contains("--flat-playlist")) FakeCliProcess(emptyList()) else process
        }
        val (engine, scope) = engine(root, runner)
        try {
            val job = engine.submit(request())
            withTimeout(5_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.DOWNLOADING } }
            }

            engine.cancel(job.id)

            assertEquals(JobState.CANCELLED, engine.jobs.value.single().state)
            assertEquals(JobErrorCode.CANCELLED, engine.jobs.value.single().error?.code)
            assertTrue(process.destroyed)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun nonZeroExitFailsWithARedactedMessage() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-engine-")
        val runner = CliProcessRunner { command, _ ->
            if (command.contains("--flat-playlist")) {
                FakeCliProcess(emptyList())
            } else {
                FakeCliProcess(emptyList(), exitCode = 1, stderrLines = listOf("ERROR: Private video"))
            }
        }
        val (engine, scope) = engine(root, runner)
        try {
            val job = engine.submit(request())
            val failed = withTimeout(5_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.FAILED } }
            }.first { it.id == job.id }

            val error = failed.error
            assertNotNull(error)
            assertEquals(JobErrorCode.UNAVAILABLE_OR_PRIVATE, error.code)
            assertFalse(error.message.contains("Private video"))
            assertFalse(error.message.contains("example.com"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun playlistScanExpandsIntoChildJobsAndHonorsTheItemLimit() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-engine-")
        val commands = CopyOnWriteArrayList<List<String>>()
        val runner = CliProcessRunner { command, _ ->
            commands += command
            if (command.contains("--flat-playlist")) {
                FakeCliProcess(
                    listOf(
                        "ENTRY|https://example.com/watch?v=one",
                        "ENTRY|https://example.com/watch?v=two",
                    )
                )
            } else {
                val url = command.last()
                val index = if (url.endsWith("one")) 1 else 2
                val file = root.resolve("item$index.mp4")
                Files.writeString(file, "media-$index")
                FakeCliProcess(listOf("TITLE|Item $index", "FILE|$file"))
            }
        }
        val (engine, scope) = engine(root, runner)
        try {
            val parent = engine.submit(request(DownloadOptions(playlistItemLimit = 2)))
            val all = withTimeout(10_000) {
                engine.jobs.first { jobs ->
                    jobs.count { it.state == JobState.COMPLETED && it.parentBatchId != null } == 2
                }
            }

            assertEquals(JobState.COMPLETED, all.first { it.id == parent.id }.state)
            val children = all.filter { it.parentBatchId == parent.id }
            assertEquals(2, children.size)
            assertTrue(children.all { it.state == JobState.COMPLETED })
            assertTrue(children.all { it.artifacts.size == 1 })
            assertTrue(children.none { it.request.sourceUrl == parent.request.sourceUrl })

            val scan = commands.first { it.contains("--flat-playlist") }
            assertEquals("2", scan[scan.indexOf("--playlist-end") + 1])
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancelDuringExpansionStopsTheScanAndLeavesNoChildren() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-engine-")
        val scanProcess = BlockingCliProcess()
        val runner = CliProcessRunner { command, _ ->
            if (command.contains("--flat-playlist")) scanProcess else FakeCliProcess(emptyList())
        }
        val (engine, scope) = engine(root, runner)
        try {
            val parent = engine.submit(request())
            withTimeout(5_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == parent.id && it.state == JobState.QUEUED } }
            }
            delay(200)

            engine.cancel(parent.id)

            assertEquals(JobState.CANCELLED, engine.jobs.value.first { it.id == parent.id }.state)
            assertTrue(scanProcess.destroyed)
            assertTrue(engine.jobs.value.none { it.parentBatchId == parent.id })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun splitChaptersRegisterEveryFileAsAChapterArtifact() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-engine-")
        val first = root.resolve("video - 01 - intro.mp4")
        val second = root.resolve("video - 02 - main.mp4")
        Files.writeString(first, "1")
        Files.writeString(second, "22")
        val commands = CopyOnWriteArrayList<List<String>>()
        val runner = CliProcessRunner { command, _ ->
            commands += command
            if (command.contains("--flat-playlist")) {
                FakeCliProcess(emptyList())
            } else {
                FakeCliProcess(listOf("TITLE|video", "FILE|$first", "FILE|$second"))
            }
        }
        val (engine, scope) = engine(root, runner)
        try {
            val job = engine.submit(request(DownloadOptions(splitByChapters = true)))
            val finished = withTimeout(5_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.COMPLETED } }
            }.first { it.id == job.id }

            assertEquals(2, finished.artifacts.size)
            assertTrue(finished.artifacts.all { it.kind == ArtifactKind.CHAPTER })
            val download = commands.first { !it.contains("--flat-playlist") }
            assertTrue(download.contains("--split-chapters"))
            assertTrue(download.any { it.startsWith("chapter:") })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun prefixAndDestinationFolderReachTheOutputTemplate() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-engine-")
        val commands = CopyOnWriteArrayList<List<String>>()
        val runner = CliProcessRunner { command, _ ->
            commands += command
            FakeCliProcess(emptyList())
        }
        val (engine, scope) = engine(root, runner)
        try {
            val job = engine.submit(
                request(DownloadOptions(filenamePrefix = "pre-", destinationFolder = "audio/2026")),
            )
            withTimeout(5_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.FAILED } }
            }

            val download = commands.first { !it.contains("--flat-playlist") }
            val output = download[download.indexOf("-o") + 1]
            assertEquals(
                root.resolve("audio/2026/pre-%(title)s.%(ext)s").toString(),
                output,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun manualStartDoesNotSpawnUntilStart() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-engine-")
        var downloadStarted = false
        val runner = CliProcessRunner { command, _ ->
            if (command.contains("--flat-playlist")) {
                FakeCliProcess(emptyList())
            } else {
                downloadStarted = true
                FakeCliProcess(emptyList())
            }
        }
        val (engine, scope) = engine(root, runner)
        try {
            val job = engine.submit(request(DownloadOptions(startPolicy = StartPolicy.MANUAL)))

            assertEquals(JobState.PENDING, job.state)
            delay(200)
            assertFalse(downloadStarted)

            engine.start(job.id)

            withTimeout(5_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state.isTerminal } }
            }
            assertTrue(downloadStarted)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun retryRunsExtractionAgainWithTheStoredRequest() = runBlocking {
        val root = Files.createTempDirectory("anydownlod-engine-")
        var downloadCalls = 0
        val runner = CliProcessRunner { command, _ ->
            if (command.contains("--flat-playlist")) {
                FakeCliProcess(emptyList())
            } else {
                downloadCalls += 1
                if (downloadCalls == 1) {
                    FakeCliProcess(emptyList(), exitCode = 1, stderrLines = listOf("ERROR: network"))
                } else {
                    val file = root.resolve("retry.mp4")
                    Files.writeString(file, "ok")
                    FakeCliProcess(listOf("FILE|$file"))
                }
            }
        }
        val (engine, scope) = engine(root, runner)
        try {
            val job = engine.submit(request())
            val failed = withTimeout(5_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.FAILED } }
            }.first { it.id == job.id }

            engine.retry(failed.id)

            val completed = withTimeout(5_000) {
                engine.jobs.first { jobs -> jobs.any { it.id == job.id && it.state == JobState.COMPLETED } }
            }.first { it.id == job.id }
            assertEquals(2, downloadCalls)
            assertEquals("https://example.com/watch?v=fixture", completed.request.sourceUrl)
        } finally {
            scope.cancel()
        }
    }
}
