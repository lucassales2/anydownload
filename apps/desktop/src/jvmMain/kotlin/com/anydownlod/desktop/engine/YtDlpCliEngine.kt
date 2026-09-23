package com.anydownlod.desktop.engine

import com.anydownlod.core.ArtifactDeletionResult
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.domain.Artifact
import com.anydownlod.core.domain.ArtifactKind
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobAttempt
import com.anydownlod.core.domain.JobError
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobProgress
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.StartPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * The desktop download engine: one yt-dlp process per job, argument lists
 * only, progress parsed from the stable templates in [YtDlpArguments].
 *
 * Playlists expand through a short `--flat-playlist` scan into child jobs so
 * each item gets its own row, cancel, and retry. Shared code never sees a
 * process.
 */
class YtDlpCliEngine(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val runner: CliProcessRunner = JavaCliProcessRunner,
    private val resolveExecutable: (String) -> String? = ExecutableOnPath::find,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { "local-${Random.nextLong().toULong().toString(16)}" },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val persist: (List<DownloadJob>) -> Unit = {},
    private val cookieFilePath: () -> String? = { null },
    seedJobs: List<DownloadJob> = emptyList(),
) : DownloadEngine {

    private val lock = Any()
    private val _jobs = MutableStateFlow(seedJobs)
    override val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val idempotency = ConcurrentHashMap<String, String>()
    private val processes = ConcurrentHashMap<String, CliProcess>()
    private val cancelRequested = ConcurrentHashMap<String, Boolean>()
    private val running = ConcurrentHashMap<String, Job>()
    private val semaphore = Semaphore(settingsRepository.settings.value.maxConcurrentDownloads.coerceAtLeast(1))

    init {
        seedJobs.forEach { job ->
            if (job.request.idempotencyKey.isNotEmpty()) {
                idempotency[job.request.idempotencyKey] = job.id
            }
        }
    }

    override fun submit(request: DownloadRequest): DownloadJob {
        val key = request.idempotencyKey
        if (key.isNotEmpty()) {
            idempotency[key]?.let { existingId -> findJob(existingId)?.let { return it } }
        }
        return when (request.options.startPolicy) {
            StartPolicy.AUTOMATIC -> beginWork(acceptJob(request, parentBatchId = null))
            StartPolicy.MANUAL -> update(acceptJob(request, parentBatchId = null).id) {
                it.copy(state = JobState.PENDING)
            } ?: error("The accepted job disappeared.")
        }
    }

    override fun start(jobId: String): DownloadJob? {
        val job = findJob(jobId) ?: return null
        if (job.state != JobState.PENDING && job.state != JobState.SCHEDULED) return job
        return beginWork(job)
    }

    override fun cancel(jobId: String): DownloadJob? {
        val job = findJob(jobId) ?: return null
        if (job.state.isTerminal) return job
        cancelRequested[jobId] = true
        processes.remove(jobId)?.destroyTree()
        return update(jobId) {
            it.copy(
                state = JobState.CANCELLED,
                error = cancelledError(),
                finishedAtEpochMillis = now(),
            )
        }
    }

    override fun retry(jobId: String): DownloadJob? {
        val job = findJob(jobId) ?: return null
        if (job.state != JobState.FAILED && job.state != JobState.CANCELLED) return job
        cancelRequested.remove(jobId)
        val at = now()
        val queued = update(jobId) {
            it.copy(
                state = JobState.QUEUED,
                progress = null,
                error = null,
                finishedAtEpochMillis = null,
                attempts = it.attempts + JobAttempt(
                    id = "attempt-${idGenerator()}",
                    jobId = jobId,
                    state = JobState.QUEUED,
                    startedAtEpochMillis = at,
                ),
            )
        } ?: return null
        launchJob(jobId)
        return queued
    }

    override fun removeHistory(jobId: String): Boolean {
        val job = findJob(jobId) ?: return false
        cancelRequested[jobId] = true
        processes.remove(jobId)?.destroyTree()
        synchronized(lock) {
            _jobs.value = _jobs.value.filterNot { it.id == job.id }
            idempotency.filterValues { it == job.id }.keys.toList().forEach { idempotency.remove(it) }
            persist(_jobs.value)
        }
        return true
    }

    override fun deleteArtifacts(jobId: String): ArtifactDeletionResult {
        val job = findJob(jobId) ?: return ArtifactDeletionResult(deletedCount = 0)
        val rootText = settingsRepository.settings.value.downloadRoot
        if (rootText.isBlank()) return ArtifactDeletionResult(deletedCount = 0)
        val root = Path.of(rootText).toAbsolutePath().normalize()
        var deleted = 0
        val failures = mutableListOf<String>()
        val updated = job.artifacts.map { artifact ->
            if (artifact.removed) return@map artifact
            val path = runCatching { DownloadPaths.artifactPath(root, artifact.relativePath) }.getOrNull()
            if (path == null) {
                failures += artifact.fileName
                artifact
            } else {
                if (runCatching { Files.deleteIfExists(path) }.getOrDefault(false)) deleted++
                artifact.copy(removed = true)
            }
        }
        update(jobId) { it.copy(artifacts = updated) }
        return ArtifactDeletionResult(deletedCount = deleted, failures = failures)
    }

    /** Destroys every live process. The next launch marks active jobs failed. */
    fun shutdown() {
        processes.keys.toList().forEach { id -> processes.remove(id)?.destroyTree() }
        running.values.forEach { it.cancel() }
        running.clear()
    }

    private fun acceptJob(request: DownloadRequest, parentBatchId: String?): DownloadJob {
        val at = now()
        val id = "job-${idGenerator()}"
        val accepted = DownloadJob(
            id = id,
            request = request,
            state = JobState.RESOLVING,
            revision = 1,
            createdAtEpochMillis = at,
            updatedAtEpochMillis = at,
            startedAtEpochMillis = at,
            sourceHost = hostOf(request.sourceUrl),
            parentBatchId = parentBatchId,
            attempts = listOf(
                JobAttempt(
                    id = "attempt-${idGenerator()}",
                    jobId = id,
                    state = JobState.RESOLVING,
                    startedAtEpochMillis = at,
                )
            ),
        )
        synchronized(lock) {
            _jobs.value = _jobs.value + accepted
            if (request.idempotencyKey.isNotEmpty()) idempotency[request.idempotencyKey] = id
            persist(_jobs.value)
        }
        return accepted
    }

    private fun beginWork(job: DownloadJob): DownloadJob {
        if (resolveExecutable("yt-dlp") == null) {
            return fail(
                jobId = job.id,
                code = JobErrorCode.ENGINE_UNAVAILABLE,
                message = "yt-dlp was not found on PATH. Install it, then retry.",
            ) ?: job
        }
        val queued = update(job.id) {
            it.copy(state = JobState.QUEUED, error = null, finishedAtEpochMillis = null)
        } ?: return job
        launchJob(job.id)
        return queued
    }

    private fun launchJob(jobId: String) {
        if (running.containsKey(jobId)) return
        val coroutine = scope.launch {
            try {
                runJob(jobId)
            } finally {
                running.remove(jobId)
            }
        }
        running[jobId] = coroutine
    }

    private suspend fun runJob(jobId: String) {
        val executable = resolveExecutable("yt-dlp")
        if (executable == null) {
            fail(
                jobId = jobId,
                code = JobErrorCode.ENGINE_UNAVAILABLE,
                message = "yt-dlp was not found on PATH. Install it, then retry.",
            )
            return
        }

        semaphore.withPermit {
            if (cancelRequested[jobId] == true || findJob(jobId)?.state == JobState.CANCELLED) {
                return@withPermit
            }
            val job = findJob(jobId) ?: return@withPermit
            val settings = settingsRepository.settings.value
            if (settings.downloadRoot.isBlank()) {
                fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "Choose a download folder in Settings first.")
                return@withPermit
            }
            val baseRoot = Path.of(settings.downloadRoot).toAbsolutePath().normalize()
            runCatching { Files.createDirectories(baseRoot) }.onFailure {
                fail(jobId, JobErrorCode.DISK_EXHAUSTED, "The download folder could not be created.")
                return@withPermit
            }

            val selectedPresets = settings.presets.filter { it.id in job.request.options.presetIds }
            val effectiveOptions = PresetLayering.apply(job.request.options, selectedPresets)
            val effectiveRequest = job.request.copy(options = effectiveOptions)

            val destination = try {
                DownloadPaths.resolveDestination(baseRoot, effectiveOptions.destinationFolder)
            } catch (failure: IllegalArgumentException) {
                fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, failure.message ?: "Invalid destination folder.")
                return@withPermit
            }
            val outputPath = try {
                DownloadPaths.outputTemplatePath(
                    destination,
                    DownloadPaths.applyFilenamePrefix(
                        settings.outputTemplate,
                        effectiveOptions.filenamePrefix,
                    ),
                )
            } catch (failure: IllegalArgumentException) {
                fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output template escapes the download folder.")
                return@withPermit
            }
            val chapterPath = if (effectiveOptions.splitByChapters) {
                try {
                    DownloadPaths.outputTemplatePath(
                        destination,
                        DownloadPaths.applyFilenamePrefix(
                            settings.chapterTemplate,
                            effectiveOptions.filenamePrefix,
                        ),
                    )
                } catch (failure: IllegalArgumentException) {
                    fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The chapter template escapes the download folder.")
                    return@withPermit
                }
            } else {
                null
            }

            // Playlists expand into child jobs; child jobs skip the scan.
            if (job.parentBatchId == null) {
                val entries = expandPlaylist(jobId, executable, job.request.sourceUrl, baseRoot)
                if (cancelRequested[jobId] == true) {
                    confirmCancelled(jobId)
                    return@withPermit
                }
                if (entries.size > 1) {
                    expandIntoChildren(job, entries)
                    return@withPermit
                }
            }

            val command = YtDlpArguments.build(
                executable = executable,
                request = effectiveRequest,
                outputTemplatePath = outputPath,
                chapterTemplatePath = chapterPath,
                cookieFilePath = cookieFilePath(),
            )
            val processStartedAt = System.currentTimeMillis()
            val process = try {
                withContext(ioDispatcher) { runner.start(command, destination) }
            } catch (failure: Exception) {
                fail(jobId, JobErrorCode.ENGINE_UNAVAILABLE, "yt-dlp could not be started.")
                return@withPermit
            }
            processes[jobId] = process
            update(jobId, persistNow = false) {
                it.copy(state = JobState.DOWNLOADING, progress = JobProgress(phase = "downloading"))
            }

            val stderr = StringBuilder()
            val stderrDrain = scope.launch(ioDispatcher) {
                runCatching {
                    process.stderr.useLines { lines ->
                        lines.forEach { line ->
                            if (stderr.length < 8_000) stderr.appendLine(line)
                            // yt-dlp prints postprocess progress to stderr.
                            if (line.startsWith(YtDlpArguments.POSTPROCESS_PREFIX)) {
                                val event = YtDlpProgressParser.parse(line)
                                if (event is YtDlpEvent.Progress) applyProgress(jobId, event)
                            }
                        }
                    }
                }
            }
            val finalPaths = mutableListOf<String>()
            try {
                withContext(ioDispatcher) {
                    process.stdout.useLines { lines ->
                        lines.forEach { line ->
                            when (val event = YtDlpProgressParser.parse(line)) {
                                is YtDlpEvent.Title -> update(jobId, persistNow = false) { current ->
                                    current.copy(title = event.title.ifBlank { current.title })
                                }

                                is YtDlpEvent.Progress -> applyProgress(jobId, event)
                                is YtDlpEvent.FinalFile -> finalPaths += event.path
                                is YtDlpEvent.Entry -> Unit
                                null -> Unit
                            }
                        }
                    }
                }
            } finally {
                runCatching { stderrDrain.join() }
                processes.remove(jobId)
                stderrDrain.cancel()
            }

            if (cancelRequested[jobId] == true) {
                confirmCancelled(jobId)
                return@withPermit
            }

            val exit = withContext(ioDispatcher) { process.waitFor(0) ?: process.waitFor(5_000) }
            when {
                exit == null -> fail(
                    jobId,
                    JobErrorCode.POSTPROCESSING_FAILURE,
                    "yt-dlp did not report an exit code. Try again.",
                )

                exit != 0 -> fail(jobId, mapExitError(stderr.toString()))
                else -> {
                    val reported = finalPaths.mapNotNull { reportedPath ->
                        runCatching { DownloadPaths.artifactPath(baseRoot, reportedPath) }.getOrNull()
                    }
                    // Captions and thumbnail-only jobs do not print a file path,
                    // so discover the files the process just wrote under the
                    // destination folder.
                    val files = if (reported.isNotEmpty()) {
                        reported
                    } else {
                        discoverNewFiles(destination, processStartedAt - 2_000)
                    }
                    completeFromFiles(jobId, baseRoot, files, effectiveOptions)
                }
            }
        }
    }

    /**
     * A short flat scan with the playlist item cap. Returns the entry URLs, or
     * an empty list when the scan failed so the caller can download directly.
     */
    private suspend fun expandPlaylist(
        jobId: String,
        executable: String,
        url: String,
        workingDirectory: Path,
    ): List<String> {
        val limit = findJob(jobId)?.request?.options?.playlistItemLimit ?: 0
        val command = buildList {
            add(executable)
            add("--flat-playlist")
            add("--skip-download")
            add("--no-warnings")
            add("--print")
            add(YtDlpArguments.ENTRY_PRINT_TEMPLATE)
            if (limit > 0) {
                add("--playlist-end")
                add(limit.toString())
            }
            add("--")
            add(url)
        }
        val process = try {
            withContext(ioDispatcher) { runner.start(command, workingDirectory) }
        } catch (failure: Exception) {
            return emptyList()
        }
        processes[jobId] = process
        val stderrDrain = scope.launch(ioDispatcher) {
            runCatching { process.stderr.useLines { lines -> lines.forEach { _ -> } } }
        }
        return try {
            val entries = mutableListOf<String>()
            withContext(ioDispatcher) {
                process.stdout.useLines { lines ->
                    lines.forEach { line ->
                        val event = YtDlpProgressParser.parse(line)
                        if (event is YtDlpEvent.Entry) entries += event.url
                    }
                }
            }
            process.waitFor(5_000)
            entries
        } finally {
            processes.remove(jobId)
            stderrDrain.cancel()
        }
    }

    private fun expandIntoChildren(parent: DownloadJob, entries: List<String>) {
        entries.forEachIndexed { index, entryUrl ->
            if (cancelRequested[parent.id] == true) return
            val childRequest = DownloadRequest(
                sourceUrl = entryUrl,
                options = parent.request.options.copy(
                    startPolicy = StartPolicy.AUTOMATIC,
                    playlistItemLimit = 0,
                ),
                idempotencyKey = "${parent.request.idempotencyKey.ifBlank { parent.id }}:child:$index",
            )
            submitChild(childRequest, parent.id)
        }
        update(parent.id) { current ->
            current.copy(
                state = JobState.COMPLETED,
                title = "Playlist (${entries.size} items)",
                progress = JobProgress(phase = "completed", percent = 100.0),
                error = null,
                finishedAtEpochMillis = now(),
            )
        }
    }

    private fun submitChild(request: DownloadRequest, parentBatchId: String): DownloadJob {
        val accepted = acceptJob(request, parentBatchId = parentBatchId)
        return beginWork(accepted)
    }

    private fun applyProgress(jobId: String, event: YtDlpEvent.Progress) {
        update(jobId, persistNow = false) { job ->
            if (event.postprocessing) {
                job.copy(
                    state = JobState.POSTPROCESSING,
                    progress = JobProgress(
                        phase = "postprocessing",
                        percent = job.progress?.percent,
                        downloadedBytes = job.progress?.downloadedBytes,
                        totalBytes = job.progress?.totalBytes,
                    ),
                )
            } else {
                val downloaded = event.downloadedBytes ?: job.progress?.downloadedBytes
                val total = event.totalBytes ?: job.progress?.totalBytes
                val percent = if (downloaded != null && total != null && total > 0) {
                    downloaded.toDouble() / total * 100.0
                } else {
                    null
                }
                job.copy(
                    state = JobState.DOWNLOADING,
                    progress = JobProgress(
                        phase = "downloading",
                        percent = percent,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speedBytesPerSecond = event.speedBytesPerSecond,
                        etaSeconds = event.etaSeconds,
                    ),
                )
            }
        }
    }

    private fun completeFromFiles(
        jobId: String,
        root: Path,
        files: List<Path>,
        options: DownloadOptions,
    ) {
        val safeFiles = files.mapNotNull { file ->
            val absolute = file.toAbsolutePath().normalize()
            absolute.takeIf { it.startsWith(root) && Files.isRegularFile(it) }
        }.distinct()
        if (safeFiles.isEmpty()) {
            fail(jobId, JobErrorCode.POSTPROCESSING_FAILURE, "yt-dlp finished but the output file was not found.")
            return
        }
        val artifacts = safeFiles.map { file ->
            Artifact(
                id = "artifact-${idGenerator()}",
                jobId = jobId,
                kind = artifactKind(file, options),
                fileName = file.fileName.toString(),
                relativePath = root.relativize(file).toString().replace('\\', '/'),
                sizeBytes = runCatching { Files.size(file) }.getOrNull(),
            )
        }
        val totalSize = artifacts.sumOf { it.sizeBytes ?: 0L }.takeIf { it > 0 }
        update(jobId) { current ->
            current.copy(
                state = JobState.COMPLETED,
                progress = JobProgress(
                    phase = "completed",
                    percent = 100.0,
                    downloadedBytes = totalSize,
                    totalBytes = totalSize,
                ),
                artifacts = (current.artifacts + artifacts).distinctBy { it.relativePath },
                error = null,
                finishedAtEpochMillis = now(),
            )
        }
    }

    private fun discoverNewFiles(directory: Path, sinceMillis: Long): List<Path> {
        if (!Files.isDirectory(directory)) return emptyList()
        val result = mutableListOf<Path>()
        runCatching {
            Files.walkFileTree(
                directory,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val name = file.fileName.toString()
                        val excluded = name.endsWith(".part") || name.endsWith(".ytdl") || name.endsWith(".tmp")
                        if (!excluded && attrs.isRegularFile && attrs.lastModifiedTime().toMillis() >= sinceMillis) {
                            result.add(file)
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        }
        return result
    }

    private fun artifactKind(file: Path, options: DownloadOptions): ArtifactKind {
        val name = file.fileName.toString().lowercase()
        return when {
            options.splitByChapters && name.isVideoLike() -> ArtifactKind.CHAPTER
            name.endsWith(".srt") || name.endsWith(".vtt") ||
                name.endsWith(".ttml") || name.endsWith(".txt") -> ArtifactKind.CAPTIONS

            name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".webp") -> ArtifactKind.THUMBNAIL

            name.endsWith(".json") -> ArtifactKind.METADATA
            name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".opus") ||
                name.endsWith(".wav") || name.endsWith(".flac") -> ArtifactKind.AUDIO

            else -> when (options.mediaType) {
                MediaType.AUDIO -> ArtifactKind.AUDIO
                MediaType.CAPTIONS -> ArtifactKind.CAPTIONS
                MediaType.THUMBNAIL -> ArtifactKind.THUMBNAIL
                MediaType.VIDEO -> ArtifactKind.VIDEO
            }
        }
    }

    private fun confirmCancelled(jobId: String) {
        update(jobId) { current ->
            if (current.state == JobState.CANCELLED) {
                current
            } else {
                current.copy(
                    state = JobState.CANCELLED,
                    error = cancelledError(),
                    finishedAtEpochMillis = now(),
                )
            }
        }
    }

    private fun fail(jobId: String, code: JobErrorCode, message: String): DownloadJob? =
        fail(jobId, JobError(code, message, retryable = code != JobErrorCode.INVALID_URL_OPTIONS))

    private fun fail(jobId: String, error: JobError): DownloadJob? =
        update(jobId) { current ->
            current.copy(
                state = JobState.FAILED,
                error = error,
                finishedAtEpochMillis = now(),
            )
        }

    private fun mapExitError(stderr: String): JobError {
        val lower = stderr.lowercase()
        val code = when {
            "requested format is not available" in lower -> JobErrorCode.UNSUPPORTED_FORMAT
            "sign in" in lower || "login" in lower -> JobErrorCode.LOGIN_REQUIRED
            "429" in lower || "too many requests" in lower -> JobErrorCode.RATE_LIMITED
            "private" in lower || "unavailable" in lower -> JobErrorCode.UNAVAILABLE_OR_PRIVATE
            "unsupported url" in lower -> JobErrorCode.UNSUPPORTED_SOURCE
            "postprocessing" in lower || "post-processing" in lower -> JobErrorCode.POSTPROCESSING_FAILURE
            "connection" in lower || "timed out" in lower -> JobErrorCode.NETWORK_FAILURE
            else -> JobErrorCode.EXTRACTION_FAILURE
        }
        val message = when (code) {
            JobErrorCode.UNSUPPORTED_FORMAT ->
                "No format matches those preferences. Try Auto or a lower height."

            JobErrorCode.LOGIN_REQUIRED -> "This source needs a sign-in. Add cookies or pick another source."
            JobErrorCode.RATE_LIMITED -> "The source rate-limited this download. Try again later."
            JobErrorCode.UNAVAILABLE_OR_PRIVATE -> "This media is unavailable or private."
            JobErrorCode.UNSUPPORTED_SOURCE -> "This URL is not supported by the installed yt-dlp."
            JobErrorCode.POSTPROCESSING_FAILURE -> "Post-processing failed. Check that ffmpeg is installed."
            JobErrorCode.NETWORK_FAILURE -> "The network connection failed. Check it and retry."
            else -> "yt-dlp could not download this URL."
        }
        return JobError(code, message, retryable = true)
    }

    private fun cancelledError(): JobError =
        JobError(JobErrorCode.CANCELLED, "The download was cancelled.", retryable = false)

    private fun findJob(jobId: String): DownloadJob? = _jobs.value.firstOrNull { it.id == jobId }

    private fun update(
        jobId: String,
        persistNow: Boolean = true,
        transform: (DownloadJob) -> DownloadJob,
    ): DownloadJob? = synchronized(lock) {
        val current = _jobs.value.firstOrNull { it.id == jobId } ?: return null
        val updated = transform(current).copy(
            revision = current.revision + 1,
            updatedAtEpochMillis = now(),
        )
        _jobs.value = _jobs.value.map { if (it.id == jobId) updated else it }
        if (persistNow) persist(_jobs.value)
        updated
    }
}

private fun hostOf(url: String): String? {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    if (afterScheme.isEmpty()) return null
    return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').ifEmpty { null }
}

private fun String.isVideoLike(): Boolean =
    endsWith(".mp4") || endsWith(".mkv") || endsWith(".webm") || endsWith(".mov") || endsWith(".avi")
