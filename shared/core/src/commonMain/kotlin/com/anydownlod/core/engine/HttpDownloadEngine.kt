package com.anydownlod.core.engine

import com.anydownlod.core.ArtifactDeletionResult
import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.domain.Artifact
import com.anydownlod.core.domain.ArtifactKind
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobAttempt
import com.anydownlod.core.domain.JobError
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobProgress
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaTags
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.OverwriteMode
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.download.FragmentDownloader
import com.anydownlod.core.download.FragmentOutcome
import com.anydownlod.core.download.M3u8
import com.anydownlod.core.download.ManifestResult
import com.anydownlod.core.download.Mpd
import com.anydownlod.core.download.MpdResult
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.MediaFragment
import com.anydownlod.core.extract.GenericExtraction
import com.anydownlod.core.extract.GenericExtractionFailure
import com.anydownlod.core.extract.GenericExtractor
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoMedia
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.format.CompiledSpec
import com.anydownlod.core.format.FormatSelector
import com.anydownlod.core.format.OptionsToSpec
import com.anydownlod.core.format.Selection
import com.anydownlod.core.platform.ContentRange
import com.anydownlod.core.platform.FileHandle
import com.anydownlod.core.platform.FileStore
import com.anydownlod.core.platform.HttpBody
import com.anydownlod.core.platform.HttpFailureReason
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.platform.engineCriticalSection
import com.anydownlod.core.postprocess.MediaFilePath
import com.anydownlod.core.postprocess.MediaToolkit
import com.anydownlod.core.postprocess.ToolkitError
import com.anydownlod.core.postprocess.UnavailableToolkit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Shared engine for one direct HTTP(S) file download on every host.
 *
 * This class owns job state and policy; [HttpTransfer] and [FileStore] own
 * transport and bytes (T-039 wires per-target actuals). It never spawns a
 * process, never calls Python, and never reads the disabled free-form
 * yt-dlp JSON field ([DownloadRequest.options] carries only allowlisted
 * values).
 *
 * URLs that would need an extractor run this engine's HTML route: a bounded
 * read of the page, the shared generic extractor subset, then the chosen
 * media URL through the same direct-file path (policy, redirects, stream).
 * HTML that the extractor cannot resolve to exactly one media URL fails with
 * a typed [JobErrorCode.EXTRACTION_FAILURE] so the UI can map it, instead of
 * pretending a download started. The media hop never recurses: if it returns
 * HTML again, the job fails typed. Progress reports bytes exactly as
 * received; percent only follows Content-Length. Speed and ETA stay null:
 * unknown stays unknown.
 */
class HttpDownloadEngine(
    private val transfer: HttpTransfer,
    private val fileStore: FileStore,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val idGenerator: () -> String = { "local-${Random.nextLong().toULong().toString(16)}" },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val persist: (List<DownloadJob>) -> Unit = {},
    /**
     * The extractor registry. When null (the D2/D3 wiring) every URL keeps
     * the direct-file and generic-page probe path; hosts add the registry in
     * the D4 host tasks.
     */
    private val registry: ExtractorRegistry? = null,
    /**
     * The host media toolkit. The web host and tests keep the default
     * [UnavailableToolkit]: the compiler then never emits a merge.
     */
    private val toolkit: MediaToolkit = UnavailableToolkit,
    private val maxRedirects: Int = UrlPolicy.MAX_REDIRECTS,
    private val chunkSize: Int = 64 * 1024,
    /**
     * Destination policy for the submitted URL and every redirect hop.
     * Defaults to [UrlPolicy]. Tests keep a fixture exception strictly local
     * (for example exactly one loopback mock-server origin); hosts must never
     * weaken the default.
     */
    private val urlCheck: (String) -> UrlCheck = UrlPolicy::check,
    seedJobs: List<DownloadJob> = emptyList(),
) : DownloadEngine {

    companion object {
        /** Bounded page read for the HTML route; a huge page is never held whole. */
        const val MAX_HTML_BYTES = 512 * 1024

        /** Largest HLS/DASH manifest the engine will read (T-073). */
        const val MANIFEST_MAX_BYTES = 2 * 1024 * 1024

        /** Largest artwork image the engine will fetch for tag embedding. */
        const val ARTWORK_MAX_BYTES = 5 * 1024 * 1024

        /**
         * The container a D5 merge writes. A codec pair this container cannot
         * hold fails typed instead of being silently saved as one stream.
         */
        const val MERGE_CONTAINER_EXT = "mp4"
    }

    private val lock = Any()
    private val _jobs = MutableStateFlow(seedJobs)
    override val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val idempotency = mutableMapOf<String, String>()
    private val running = mutableMapOf<String, Job>()
    private val cancelRequested = mutableMapOf<String, Boolean>()
    private val semaphore = Semaphore(settings.settings.value.maxConcurrentDownloads.coerceAtLeast(1))

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
            val existing = engineCriticalSection(lock) {
                idempotency[key]?.let { existingId -> findJob(existingId) }
            }
            existing?.let { return it }
        }
        return when (request.options.startPolicy) {
            StartPolicy.AUTOMATIC -> beginWork(acceptJob(request))
            StartPolicy.MANUAL -> update(acceptJob(request).id) {
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
        engineCriticalSection(lock) {
            cancelRequested[jobId] = true
            running.remove(jobId)?.cancel()
        }
        return update(jobId) {
            it.copy(state = JobState.CANCELLED, error = cancelledError(), finishedAtEpochMillis = now())
        }
    }

    override fun retry(jobId: String): DownloadJob? {
        val job = findJob(jobId) ?: return null
        if (job.state != JobState.FAILED && job.state != JobState.CANCELLED) return job
        engineCriticalSection(lock) { cancelRequested.remove(jobId) }
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
        engineCriticalSection(lock) {
            cancelRequested[jobId] = true
            running.remove(jobId)?.cancel()
            _jobs.value = _jobs.value.filterNot { it.id == job.id }
            idempotency.filterValues { it == job.id }.keys.toList().forEach { idempotency.remove(it) }
            persist(_jobs.value)
        }
        return true
    }

    override fun deleteArtifacts(jobId: String): ArtifactDeletionResult {
        val job = findJob(jobId) ?: return ArtifactDeletionResult(deletedCount = 0)
        var deleted = 0
        val failures = mutableListOf<String>()
        val updated = job.artifacts.map { artifact ->
            if (artifact.removed) return@map artifact
            val ok = artifact.relativePath.isNotBlank() &&
                runCatching { fileStore.delete(artifact.relativePath) }.getOrDefault(false)
            if (ok) deleted++ else failures += artifact.fileName
            artifact.copy(removed = true)
        }
        update(jobId) { it.copy(artifacts = updated) }
        return ArtifactDeletionResult(deletedCount = deleted, failures = failures)
    }

    private fun acceptJob(request: DownloadRequest): DownloadJob {
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
            sourceHost = UrlPolicy.hostOf(request.sourceUrl),
            parentBatchId = request.parentBatchId,
            attempts = listOf(
                JobAttempt(
                    id = "attempt-${idGenerator()}",
                    jobId = id,
                    state = JobState.RESOLVING,
                    startedAtEpochMillis = at,
                )
            ),
        )
        engineCriticalSection(lock) {
            _jobs.value = _jobs.value + accepted
            if (request.idempotencyKey.isNotEmpty()) idempotency[request.idempotencyKey] = id
            persist(_jobs.value)
        }
        return accepted
    }

    private fun beginWork(job: DownloadJob): DownloadJob {
        val queued = update(job.id) {
            it.copy(state = JobState.QUEUED, error = null, finishedAtEpochMillis = null)
        } ?: return job
        launchJob(job.id)
        return queued
    }

    private fun launchJob(jobId: String) {
        engineCriticalSection(lock) {
            if (running.containsKey(jobId)) return@engineCriticalSection
            val coroutine = scope.launch {
                try {
                    runJob(jobId)
                } finally {
                    engineCriticalSection(lock) { running.remove(jobId) }
                }
            }
            running[jobId] = coroutine
        }
    }

    private suspend fun runJob(jobId: String) {
        if (isCancelRequested(jobId)) {
            confirmCancelled(jobId)
            return
        }
        if (settings.settings.value.downloadRoot.isBlank()) {
            fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "Choose a download folder in Settings first.")
            return
        }
        val request = findJob(jobId)?.request ?: return
        semaphore.withPermit {
            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
                return@withPermit
            }
            val url = findJob(jobId)?.request?.sourceUrl ?: return@withPermit
            val options = findJob(jobId)?.request?.options ?: DownloadOptions()
            try {
                if (handleExistingArtifact(jobId, request)) return@withPermit
                val extractor = registry?.suitableFor(url)
                if (extractor != null) {
                    extractAndDownload(jobId, url, options, extractor)
                } else {
                    update(jobId, persistNow = false) {
                        it.copy(state = JobState.DOWNLOADING, progress = JobProgress(phase = "downloading"))
                    }
                    downloadDirectFile(jobId, url, options, extractHtml = true)
                }
            } catch (cancelled: CancellationException) {
                if (isCancelRequested(jobId)) confirmCancelled(jobId) else throw cancelled
            } catch (failure: Throwable) {
                if (isCancelRequested(jobId)) {
                    confirmCancelled(jobId)
                } else {
                    fail(jobId, JobErrorCode.NETWORK_FAILURE, "The download failed. Check the network and retry.")
                }
            }
        }
    }

    /**
     * The D4 registry route: extract one [InfoDict], compile the typed options
     * to a format spec, select exactly one format, and download it through the
     * direct-file path. Merging, the media toolkit, and captions/thumbnails
     * fail typed with an honest message.
     */
    private suspend fun extractAndDownload(
        jobId: String,
        url: String,
        options: DownloadOptions,
        extractor: com.anydownlod.core.extract.InfoExtractor,
    ) {
        update(jobId, persistNow = false) {
            it.copy(state = JobState.RESOLVING, progress = JobProgress(phase = "extracting"))
        }
        val info = try {
            withContext(ioDispatcher) { extractor.extract(url) }
        } catch (error: ExtractionError) {
            val mapped = extractionJobError(error)
            fail(jobId, mapped.code, mapped.message, mapped.retryable)
            return
        }
        update(jobId) {
            it.copy(
                title = info.title ?: it.title,
                thumbnailUrl = info.thumbnails.lastOrNull()?.url ?: it.thumbnailUrl,
                sourceHost = UrlPolicy.hostOf(url),
                formatsNeedingJs = info.formatsNeedingJs,
            )
        }
        if (info.media.isNotEmpty()) {
            downloadSelectedMedia(jobId, url, info, options)
            return
        }
        val capabilities = toolkit.capabilities()
        when (val resolution = resolveFormat(info, options, capabilities.canMerge, capabilities.audioContainers)) {
            is FormatResolution.Unsupported -> {
                fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, resolution.message, retryable = false)
            }

            is FormatResolution.Ready -> {
                val format = resolution.format
                val formatUrl = format.url
                if (formatUrl.isNullOrBlank()) {
                    fail(
                        jobId,
                        JobErrorCode.UNSUPPORTED_FORMAT,
                        "The selected format has no downloadable URL.",
                        retryable = false,
                    )
                    return
                }
                update(jobId, persistNow = false) {
                    it.copy(state = JobState.DOWNLOADING, progress = JobProgress(phase = "downloading"))
                }
                downloadDirectFile(
                    jobId = jobId,
                    url = formatUrl,
                    options = options,
                    extractHtml = false,
                    headers = format.httpHeaders.orEmpty(),
                    chunkSize = format.downloaderOptions?.httpChunkSize,
                    declaredSize = format.filesize ?: format.filesizeApprox,
                    artifactTitle = info.title,
                    artifactExt = format.ext,
                )
            }

            is FormatResolution.Merge -> downloadAndMerge(
                jobId = jobId,
                sourceUrl = url,
                info = info,
                options = options,
                video = resolution.video,
                audio = resolution.audio,
            )

            is FormatResolution.ExtractAudio -> downloadAndExtractAudio(
                jobId = jobId,
                sourceUrl = url,
                info = info,
                options = options,
                format = resolution.format,
                container = resolution.container,
            )
        }
    }

    /**
     * The D7 media route: a source with several videos (an X status) writes
     * one file per selected stable media id. Every id is resolved against the
     * fresh extraction before any media request, so a stale selection never
     * half-downloads. Files are published one by one on the same job, whose
     * source URL stays the status URL. A failure mid-way keeps the files
     * already published and fails the job typed; the failed temp is discarded.
     */
    private suspend fun downloadSelectedMedia(
        jobId: String,
        sourceUrl: String,
        info: InfoDict,
        options: DownloadOptions,
    ) {
        val selectedIds = findJob(jobId)?.request?.selectedMediaIds.orEmpty()
        if (selectedIds.isEmpty()) {
            fail(
                jobId,
                JobErrorCode.INVALID_URL_OPTIONS,
                "Select at least one video before downloading this post.",
                retryable = false,
            )
            return
        }
        val selected = info.media.filter { it.mediaId in selectedIds }
        if (selected.size != selectedIds.size) {
            fail(
                jobId,
                JobErrorCode.UNAVAILABLE_OR_PRIVATE,
                "A selected video is no longer part of this post.",
                retryable = false,
            )
            return
        }
        val capabilities = toolkit.capabilities()
        val resolved = mutableListOf<MediaFormat>()
        for (media in selected) {
            // Each media is its own single-format selection; the preview's
            // media URLs are never reused.
            val mediaInfo = info.copy(formats = media.formats, media = emptyList())
            when (
                val resolution = resolveFormat(
                    info = mediaInfo,
                    options = options,
                    canMerge = capabilities.canMerge,
                    audioContainers = capabilities.audioContainers,
                )
            ) {
                is FormatResolution.Ready -> resolved += resolution.format
                is FormatResolution.Unsupported -> {
                    fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, resolution.message, retryable = false)
                    return
                }

                is FormatResolution.Merge, is FormatResolution.ExtractAudio -> {
                    fail(
                        jobId,
                        JobErrorCode.UNSUPPORTED_FORMAT,
                        "This selection needs the media toolkit and is not available for a post video.",
                        retryable = false,
                    )
                    return
                }
            }
        }

        var downloaded = 0L
        val multi = selected.size > 1
        selected.forEachIndexed { index, media ->
            val format = resolved[index]
            val formatUrl = format.url
            if (formatUrl.isNullOrBlank()) {
                fail(
                    jobId,
                    JobErrorCode.UNSUPPORTED_FORMAT,
                    "The selected format has no downloadable URL.",
                    retryable = false,
                )
                return
            }
            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
                return
            }
            update(jobId, persistNow = false) {
                it.copy(state = JobState.DOWNLOADING, progress = JobProgress(phase = "downloading"))
            }
            val title = mediaTitle(info, media, multi, index)
            val temp = downloadDirectFile(
                jobId = jobId,
                url = formatUrl,
                options = options,
                extractHtml = false,
                headers = format.httpHeaders.orEmpty(),
                chunkSize = format.downloaderOptions?.httpChunkSize,
                declaredSize = format.filesize ?: format.filesizeApprox,
                artifactTitle = title,
                artifactExt = format.ext,
                publishResult = false,
            ) ?: return
            if (isCancelRequested(jobId)) {
                runCatching { temp.handle.discard() }
                confirmCancelled(jobId)
                return
            }
            val published = publishMediaArtifact(
                jobId = jobId,
                temp = temp,
                options = options,
                sourceUrl = sourceUrl,
                artifactTitle = title,
                artifactExt = format.ext,
            )
            if (!published) return
            downloaded += temp.bytes
        }
        update(jobId) {
            it.copy(
                state = JobState.COMPLETED,
                progress = JobProgress(phase = "completed", percent = 100.0, downloadedBytes = downloaded),
                error = null,
                finishedAtEpochMillis = now(),
            )
        }
    }

    /** A media title, numbered when several videos are selected at once. */
    private fun mediaTitle(
        info: InfoDict,
        media: InfoMedia,
        multi: Boolean,
        index: Int,
    ): String? = media.title
        ?: info.title?.let { title -> if (multi) "$title #${index + 1}" else title }

    /**
     * Publishes one finished media temp under the artifact name and appends
     * the artifact without completing the job, so several files can share the
     * status job. The existing name sanitization and collision policy stand.
     */
    private suspend fun publishMediaArtifact(
        jobId: String,
        temp: TempDownload,
        options: DownloadOptions,
        sourceUrl: String,
        artifactTitle: String?,
        artifactExt: String?,
    ): Boolean {
        val relativePath = artifactPath(
            options = options,
            sourceUrl = sourceUrl,
            artifactTitle = artifactTitle ?: temp.suggestedTitle,
            artifactExt = artifactExt ?: temp.suggestedExt,
            requestedPath = requestedPath(jobId),
        ) ?: run {
            fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
            runCatching { temp.handle.discard() }
            return false
        }
        temp.handle.close()
        val published = try {
            fileStore.publish(temp.handle, relativePath)
            true
        } catch (failure: Throwable) {
            fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
            false
        }
        if (!published) {
            runCatching { temp.handle.discard() }
            return false
        }
        val sizeBytes = runCatching { fileStore.size(relativePath) }.getOrNull() ?: temp.bytes
        val artifact = Artifact(
            id = "artifact-${idGenerator()}",
            jobId = jobId,
            kind = artifactKind(options.mediaType, relativePath),
            fileName = relativePath.substringAfterLast('/'),
            relativePath = relativePath,
            sizeBytes = sizeBytes,
        )
        update(jobId) { it.copy(artifacts = it.artifacts + artifact) }
        return true
    }

    /**
     * The D5 merge route: download the selected video and audio streams to
     * temps through the same direct-file path, merge them with the host
     * toolkit into one container, and publish one artifact. Temps are deleted
     * on success, failure, and cancel. An incompatible container or a missing
     * toolkit fails typed; it never silently saves one stream.
     */
    private suspend fun downloadAndMerge(
        jobId: String,
        sourceUrl: String,
        info: InfoDict,
        options: DownloadOptions,
        video: MediaFormat,
        audio: MediaFormat,
    ) {
        val videoTemp = downloadFormatToTemp(jobId, video, options, info.title) ?: return
        try {
            val audioTemp = downloadFormatToTemp(jobId, audio, options, info.title) ?: return
            try {
                if (isCancelRequested(jobId)) {
                    confirmCancelled(jobId)
                    return
                }
                update(jobId) {
                    it.copy(state = JobState.POSTPROCESSING, progress = JobProgress(phase = "merging"))
                }
                val destination = runCatching { fileStore.createTempFile(MERGE_CONTAINER_EXT) }.getOrNull()
                if (destination == null) {
                    fail(jobId, JobErrorCode.DISK_EXHAUSTED, "The download folder could not be created.")
                    return
                }
                var published = false
                try {
                    // The toolkit writes the file itself; release the empty handle first.
                    destination.close()
                    try {
                        toolkit.merge(
                            fileStore.mediaFilePath(videoTemp.handle),
                            fileStore.mediaFilePath(audioTemp.handle),
                            fileStore.mediaFilePath(destination),
                        )
                    } catch (error: ToolkitError) {
                        val mapped = toolkitJobError(error)
                        fail(jobId, mapped.code, mapped.message, mapped.retryable)
                        return
                    }
                    currentCoroutineContext().ensureActive()
                    if (isCancelRequested(jobId)) {
                        confirmCancelled(jobId)
                        return
                    }
                    val relativePath = artifactPath(
                        options = options,
                        sourceUrl = sourceUrl,
                        artifactTitle = info.title,
                        artifactExt = MERGE_CONTAINER_EXT,
                        requestedPath = requestedPath(jobId),
                    ) ?: run {
                        fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
                        return
                    }
                    published = publishAndComplete(
                        jobId = jobId,
                        handle = destination,
                        downloaded = videoTemp.bytes + audioTemp.bytes,
                        totalBytes = null,
                        options = options,
                        relativePath = relativePath,
                    )
                } finally {
                    if (!published) {
                        runCatching { destination.discard() }
                    }
                }
            } finally {
                runCatching { audioTemp.handle.discard() }
            }
        } finally {
            runCatching { videoTemp.handle.discard() }
        }
    }

    /**
     * The T-082 audio extraction route: download the best audio-only stream to
     * a temp and let the host toolkit copy or transcode it into the requested
     * container. The destination extension is the container the toolkit wrote;
     * a rejected codec fails typed and leaves no artifact.
     */
    private suspend fun downloadAndExtractAudio(
        jobId: String,
        sourceUrl: String,
        info: InfoDict,
        options: DownloadOptions,
        format: MediaFormat,
        container: AudioContainer,
    ) {
        val sourceTemp = downloadFormatToTemp(jobId, format, options, info.title) ?: return
        try {
            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
                return
            }
            update(jobId) {
                it.copy(state = JobState.POSTPROCESSING, progress = JobProgress(phase = "extracting audio"))
            }
            val destination = runCatching { fileStore.createTempFile(container.wireName) }.getOrNull()
            if (destination == null) {
                fail(jobId, JobErrorCode.DISK_EXHAUSTED, "The download folder could not be created.")
                return
            }
            var published = false
            try {
                // The toolkit writes the file itself; release the empty handle first.
                destination.close()
                try {
                    toolkit.extractAudio(
                        fileStore.mediaFilePath(sourceTemp.handle),
                        container,
                        fileStore.mediaFilePath(destination),
                    )
                } catch (error: ToolkitError) {
                    val mapped = toolkitJobError(error)
                    fail(jobId, mapped.code, mapped.message, mapped.retryable)
                    return
                }
                currentCoroutineContext().ensureActive()
                if (isCancelRequested(jobId)) {
                    confirmCancelled(jobId)
                    return
                }
                val tags = embedRequestedTags(jobId, destination, container)
                if (tags is TagResult.Failed) return
                val relativePath = artifactPath(
                    options = options,
                    sourceUrl = sourceUrl,
                    artifactTitle = info.title,
                    artifactExt = container.wireName,
                    requestedPath = requestedPath(jobId),
                ) ?: run {
                    fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
                    return
                }
                published = publishAndComplete(
                    jobId = jobId,
                    handle = destination,
                    downloaded = sourceTemp.bytes,
                    totalBytes = null,
                    options = options,
                    relativePath = relativePath,
                    tagsEmbedded = tagsEmbeddedOf(tags),
                    lyricsEmbedded = lyricsEmbeddedOf(tags),
                )
            } finally {
                if (!published) {
                    runCatching { destination.discard() }
                }
            }
        } finally {
            runCatching { sourceTemp.handle.discard() }
        }
    }

    /** Downloads one selected format into a temp through the direct-file path. */
    private suspend fun downloadFormatToTemp(
        jobId: String,
        format: MediaFormat,
        options: DownloadOptions,
        artifactTitle: String?,
    ): TempDownload? {
        val formatUrl = format.url
        if (formatUrl.isNullOrBlank()) {
            fail(
                jobId,
                JobErrorCode.UNSUPPORTED_FORMAT,
                "The selected format has no downloadable URL.",
                retryable = false,
            )
            return null
        }
        update(jobId, persistNow = false) {
            it.copy(state = JobState.DOWNLOADING, progress = JobProgress(phase = "downloading"))
        }
        return downloadDirectFile(
            jobId = jobId,
            url = formatUrl,
            options = options,
            extractHtml = false,
            headers = format.httpHeaders.orEmpty(),
            chunkSize = format.downloaderOptions?.httpChunkSize,
            declaredSize = format.filesize ?: format.filesizeApprox,
            artifactTitle = artifactTitle,
            artifactExt = format.ext,
            publishResult = false,
        )
    }

    /**
     * A finished download held in a temp file, closed and ready to publish or
     * merge. [suggestedTitle] and [suggestedExt] carry the manifest-derived
     * name when the source had one.
     */
    private class TempDownload(
        val handle: FileHandle,
        val bytes: Long,
        val totalBytes: Long?,
        val suggestedTitle: String? = null,
        val suggestedExt: String? = null,
    )

    /**
     * Publishes a completed temp under the artifact name, or hands it back for
     * a merge. A refused name discards the temp.
     */
    private suspend fun finishTemp(
        jobId: String,
        temp: TempDownload,
        publishResult: Boolean,
        options: DownloadOptions,
        sourceUrl: String,
        artifactTitle: String?,
        artifactExt: String?,
    ): TempDownload? {
        if (!publishResult) return temp
        val container = AudioContainer.fromWire(artifactExt ?: temp.suggestedExt.orEmpty())
        val tags = embedRequestedTags(jobId, temp.handle, container)
        if (tags is TagResult.Failed) {
            runCatching { temp.handle.discard() }
            return null
        }
        val relativePath = artifactPath(
            options,
            sourceUrl,
            artifactTitle ?: temp.suggestedTitle,
            artifactExt ?: temp.suggestedExt,
            requestedPath(jobId),
        ) ?: run {
            fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
            runCatching { temp.handle.discard() }
            return null
        }
        val published = publishAndComplete(
            jobId = jobId,
            handle = temp.handle,
            downloaded = temp.bytes,
            totalBytes = temp.totalBytes,
            options = options,
            relativePath = relativePath,
            tagsEmbedded = tagsEmbeddedOf(tags),
            lyricsEmbedded = lyricsEmbeddedOf(tags),
        )
        if (!published) {
            runCatching { temp.handle.discard() }
        }
        return null
    }

    /** Whether the request's Spotify tags could be written on this host. */
    private sealed interface TagResult {
        data object NotRequested : TagResult
        data class Skipped(val lyricsEmbedded: Boolean?) : TagResult
        data class Embedded(val lyricsEmbedded: Boolean?) : TagResult
        data object Failed : TagResult
    }

    private fun tagsEmbeddedOf(result: TagResult): Boolean? = when (result) {
        TagResult.NotRequested -> null
        is TagResult.Skipped -> false
        is TagResult.Embedded -> true
        TagResult.Failed -> null
    }

    private fun lyricsEmbeddedOf(result: TagResult): Boolean? = when (result) {
        is TagResult.Skipped -> result.lyricsEmbedded
        is TagResult.Embedded -> result.lyricsEmbedded
        else -> null
    }

    /**
     * Embeds the request's tags into a finished temp when it carries any. The
     * media path is resolved only when tags were requested, so a host without
     * a toolkit is never asked for one. [container] gates lyrics: a container
     * the host does not list in `lyricsContainers` never receives them.
     */
    private suspend fun embedRequestedTags(
        jobId: String,
        handle: FileHandle,
        container: AudioContainer?,
    ): TagResult {
        val request = findJob(jobId)?.request ?: return TagResult.NotRequested
        if (request.metadata == null) return TagResult.NotRequested
        return embedTagsInto(jobId, request, fileStore.mediaFilePath(handle), container)
    }

    /**
     * Embeds the request's tags into a finished file. A host without the
     * capability keeps the file and records a skip; a failed rewrite fails
     * the job and the caller discards the file. Lyrics ride along only when
     * [container] is in the host's `lyricsContainers`; the job records that
     * the lyrics were skipped otherwise.
     */
    private suspend fun embedTagsInto(
        jobId: String,
        request: DownloadRequest,
        file: MediaFilePath,
        container: AudioContainer?,
    ): TagResult {
        val tags = request.metadata ?: return TagResult.NotRequested
        val capabilities = toolkit.capabilities()
        val lyricsSupported = tags.lyrics != null &&
            container != null &&
            container in capabilities.lyricsContainers
        val lyricsEmbedded = when {
            tags.lyrics == null -> null
            lyricsSupported -> true
            else -> false
        }
        if (!capabilities.canEmbedTags) return TagResult.Skipped(lyricsEmbedded)
        update(jobId) {
            it.copy(state = JobState.POSTPROCESSING, progress = JobProgress(phase = "tagging"))
        }
        val artwork = if (capabilities.canEmbedArtwork) fetchArtwork(request.artworkUrl) else null
        val effectiveTags = if (tags.lyrics != null && !lyricsSupported) tags.copy(lyrics = null) else tags
        try {
            toolkit.embedTags(file, effectiveTags, artwork)
        } catch (error: ToolkitError) {
            val mapped = toolkitJobError(error)
            fail(jobId, mapped.code, mapped.message, mapped.retryable)
            return TagResult.Failed
        }
        return TagResult.Embedded(lyricsEmbedded)
    }

    /**
     * Handles an existing destination before any download. `skip` completes
     * the job with the existing file; `metadata` retags it when the host can
     * and the request carries tags; `force` returns false so the download
     * replaces it. Returns true when the job is already finished.
     */
    private suspend fun handleExistingArtifact(jobId: String, request: DownloadRequest): Boolean {
        val relativePath = request.relativePath?.takeIf { it.isNotBlank() } ?: return false
        val existingSize = runCatching { fileStore.size(relativePath) }.getOrNull() ?: return false
        when (request.options.overwrite) {
            OverwriteMode.FORCE -> return false
            OverwriteMode.SKIP -> completeSkipped(jobId, request, relativePath, existingSize, tagsEmbedded = null)
            OverwriteMode.METADATA -> {
                val tags = request.metadata
                if (tags != null && toolkit.capabilities().canEmbedTags) {
                    val result = retagExisting(jobId, request, relativePath)
                    if (result is TagResult.Failed) return true
                    completeSkipped(
                        jobId = jobId,
                        request = request,
                        relativePath = relativePath,
                        sizeBytes = fileStore.size(relativePath) ?: existingSize,
                        tagsEmbedded = true,
                        lyricsEmbedded = lyricsEmbeddedOf(result),
                    )
                } else {
                    completeSkipped(
                        jobId = jobId,
                        request = request,
                        relativePath = relativePath,
                        sizeBytes = existingSize,
                        tagsEmbedded = if (tags != null) false else null,
                        lyricsEmbedded = if (tags?.lyrics != null) false else null,
                    )
                }
            }
        }
        return true
    }

    /** Rewrites an existing file's tags for the `metadata` overwrite mode. */
    private suspend fun retagExisting(
        jobId: String,
        request: DownloadRequest,
        relativePath: String,
    ): TagResult {
        val container = AudioContainer.fromWire(relativePath.substringAfterLast('.', ""))
        return embedTagsInto(jobId, request, fileStore.mediaFilePath(relativePath), container)
    }

    /** Completes a job without downloading because the destination exists. */
    private fun completeSkipped(
        jobId: String,
        request: DownloadRequest,
        relativePath: String,
        sizeBytes: Long?,
        tagsEmbedded: Boolean?,
        lyricsEmbedded: Boolean? = if (request.metadata?.lyrics != null) false else null,
    ) {
        val artifact = Artifact(
            id = "artifact-${idGenerator()}",
            jobId = jobId,
            kind = artifactKind(request.options.mediaType, relativePath),
            fileName = relativePath.substringAfterLast('/'),
            relativePath = relativePath,
            sizeBytes = sizeBytes,
        )
        update(jobId) {
            it.copy(
                state = JobState.COMPLETED,
                progress = JobProgress(phase = "skipped"),
                error = null,
                finishedAtEpochMillis = now(),
                tagsEmbedded = tagsEmbedded,
                lyricsEmbedded = lyricsEmbedded,
                artifacts = it.artifacts + artifact,
            )
        }
    }

    private fun requestedPath(jobId: String): String? = findJob(jobId)?.request?.relativePath

    /** One bounded artwork fetch; any failure or redirect means no artwork. */
    private suspend fun fetchArtwork(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        if (urlCheck(url) !is UrlCheck.Allowed) return null
        return try {
            when (val response = transfer.execute(HttpRequest(url = url))) {
                is HttpResponse.Final -> {
                    val body = response.body
                    if (response.statusCode !in 200..299 || body == null) {
                        runCatching { body?.close() }
                        null
                    } else {
                        readBoundedBytes(body, ARTWORK_MAX_BYTES)
                    }
                }

                else -> null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    /** Reads at most [cap] bytes of a body; the caller closes nothing. */
    private suspend fun readBoundedBytes(body: HttpBody, cap: Int): ByteArray? {
        val out = ByteArray(cap)
        val chunk = ByteArray(minOf(chunkSize, cap))
        var total = 0
        try {
            while (total < cap) {
                val count = withContext(ioDispatcher) { body.readNext(chunk) }
                if (count == -1) break
                if (count == 0) continue
                currentCoroutineContext().ensureActive()
                val keep = minOf(count, cap - total)
                chunk.copyInto(out, total, 0, keep)
                total += keep
            }
        } finally {
            runCatching { body.close() }
        }
        return out.copyOf(total)
    }


    private suspend fun downloadDirectFile(
        jobId: String,
        url: String,
        options: DownloadOptions,
        extractHtml: Boolean,
        headers: Map<String, String> = emptyMap(),
        chunkSize: Long? = null,
        declaredSize: Long? = null,
        artifactTitle: String? = null,
        artifactExt: String? = null,
        publishResult: Boolean = true,
    ): TempDownload? {
        val initialRange = chunkSize?.takeIf { it > 0 }?.let { 0L..(it - 1) }
        var current = HttpRequest(url = url, headers = headers, range = initialRange)
        var hops = 0
        var body: HttpBody? = null
        try {
            while (true) {
                when (val policy = urlCheck(current.url)) {
                    is UrlCheck.Rejected -> {
                        fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, redactedUrlReason(policy.reason))
                        return null
                    }

                    is UrlCheck.Allowed -> Unit
                }

                val response = withContext(ioDispatcher) { transfer.execute(current) }
                when (response) {
                    is HttpResponse.Unavailable -> {
                        fail(
                            jobId,
                            JobErrorCode.ENGINE_UNAVAILABLE,
                            "A required download tool is missing on this device.",
                            retryable = false,
                        )
                        return null
                    }

                    is HttpResponse.Failed -> {
                        val error = when (response.reason) {
                            HttpFailureReason.PERMISSION -> JobError(
                                JobErrorCode.ENGINE_UNAVAILABLE,
                                response.message,
                                retryable = false,
                            )

                            HttpFailureReason.BLOCKED_DESTINATION -> JobError(
                                JobErrorCode.INVALID_URL_OPTIONS,
                                response.message,
                                retryable = false,
                            )

                            else -> JobError(JobErrorCode.NETWORK_FAILURE, response.message, retryable = true)
                        }
                        fail(jobId, error.code, error.message, error.retryable)
                        return null
                    }

                    is HttpResponse.Redirect -> {
                        if (response.location.isBlank()) {
                            fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source redirected without a destination.")
                            return null
                        }
                        hops++
                        if (hops > maxRedirects) {
                            fail(jobId, JobErrorCode.NETWORK_FAILURE, "Too many redirects.")
                            return null
                        }
                        current = current.copy(url = response.location)
                        currentCoroutineContext().ensureActive()
                    }

                    is HttpResponse.Final -> {
                        body = response.body
                        if (response.statusCode !in 200..299) {
                            body?.close()
                            body = null
                            val error = mapHttpStatus(response.statusCode)
                            fail(jobId, error.code, error.message, error.retryable)
                            return null
                        }
                        when (UrlClassifier.classify(response.contentType, current.url)) {
                            UrlClassifier.Classification.NEEDS_EXTRACTOR -> {
                                if (!extractHtml) {
                                    body?.close()
                                    body = null
                                    fail(
                                        jobId,
                                        JobErrorCode.EXTRACTION_FAILURE,
                                        "The media URL returned another page instead of a file.",
                                        retryable = false,
                                    )
                                    return null
                                }
                                val pageBody = body
                                if (pageBody == null) {
                                    fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source returned an empty response body.")
                                    return null
                                }
                                body = null // ownership passes to the extraction step
                                extractMediaFromPage(jobId, pageBody, current.url, options)
                                return null
                            }

                            UrlClassifier.Classification.MANIFEST -> {
                                val manifestBody = body
                                if (manifestBody == null) {
                                    fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source returned an empty response body.")
                                    return null
                                }
                                body = null // ownership passes to the manifest step
                                val temp = streamManifestToTemp(jobId, manifestBody, response.contentType, current.url)
                                    ?: return null
                                return finishTemp(
                                    jobId = jobId,
                                    temp = temp,
                                    publishResult = publishResult,
                                    options = options,
                                    sourceUrl = current.url,
                                    artifactTitle = artifactTitle,
                                    artifactExt = artifactExt,
                                )
                            }

                            UrlClassifier.Classification.DIRECT_FILE -> {
                                val fileBody = body
                                if (fileBody == null) {
                                    fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source returned an empty response body.")
                                    return null
                                }
                                body = null // ownership passes to the streaming step
                                val chunked = chunkSize?.takeIf { it > 0 && response.statusCode == 206 }
                                val temp = (if (chunked != null) {
                                    streamChunkedToTemp(
                                        jobId = jobId,
                                        firstBody = fileBody,
                                        firstRangeTotal = response.totalBytes ?: declaredSize,
                                        declaredSize = declaredSize,
                                        sourceUrl = current.url,
                                        headers = headers,
                                        chunkSize = chunked,
                                    )
                                } else {
                                    streamToTemp(
                                        jobId = jobId,
                                        body = fileBody,
                                        totalBytes = response.totalBytes ?: declaredSize,
                                    )
                                }) ?: return null
                                return finishTemp(
                                    jobId = jobId,
                                    temp = temp,
                                    publishResult = publishResult,
                                    options = options,
                                    sourceUrl = current.url,
                                    artifactTitle = artifactTitle,
                                    artifactExt = artifactExt,
                                )
                            }
                        }
                    }
                }
            }
        } finally {
            runCatching { body?.close() }
        }
    }

    /**
     * The T-045 HTML route: reads a bounded page body, asks the generic
     * extractor subset for exactly one media URL, and downloads that URL with
     * the same direct-file path. The media hop may not extract again; a page
     * that resolves to another page fails typed.
     */
    private suspend fun extractMediaFromPage(
        jobId: String,
        pageBody: HttpBody,
        pageUrl: String,
        options: DownloadOptions,
    ) {
        try {
            val html = withContext(ioDispatcher) { readBoundedHtml(pageBody) }
            when (
                val extraction = GenericExtractor.extract(
                    pageUrl = pageUrl,
                    html = html,
                    // Same policy seam as the download loop: UrlPolicy in
                    // production, a strictly local fixture exception in tests.
                    candidateCheck = urlCheck,
                )
            ) {
                is GenericExtraction.Direct -> {
                    downloadDirectFile(jobId, extraction.url, options, extractHtml = false)
                }

                is GenericExtraction.Failed -> {
                    fail(
                        jobId,
                        JobErrorCode.EXTRACTION_FAILURE,
                        redactedExtractionMessage(extraction.reason),
                        retryable = false,
                    )
                }
            }
        } finally {
            runCatching { pageBody.close() }
        }
    }

    /**
     * Reads at most [MAX_HTML_BYTES] of a page body so a hostile or huge page
     * is never held in memory whole. Invalid UTF-8 tails decode with the
     * replacement character, which the HTML scanner ignores.
     */
    private suspend fun readBoundedHtml(body: HttpBody, cap: Int = MAX_HTML_BYTES): String {
        val out = StringBuilder()
        val chunk = ByteArray(chunkSize)
        var total = 0
        while (total < cap) {
            val count = withContext(ioDispatcher) { body.readNext(chunk) }
            if (count == -1) break
            if (count == 0) continue
            currentCoroutineContext().ensureActive()
            val keep = minOf(count, cap - total)
            if (keep > 0) {
                out.append(chunk.decodeToString(0, keep))
                total += keep
            }
        }
        return out.toString()
    }

    private fun redactedExtractionMessage(reason: GenericExtractionFailure): String = when (reason) {
        GenericExtractionFailure.UnsupportedPageUrl -> "The page URL is not an HTTP(S) address this app can read."
        GenericExtractionFailure.NoMedia -> "This page has no media this app can download."
        GenericExtractionFailure.MultipleMedia -> "This page has more than one media element, so the app cannot choose one."
    }

    /**
     * HLS/DASH manifest path (T-073): parse the playlist or MPD, pick the best
     * variant by bandwidth, and concatenate its fragments into one temp file
     * through [FragmentDownloader]. No FFmpeg, no merge step; live playlists,
     * unsupported key methods, and DRM fail typed. `percent` is the fragment
     * ratio and the phase marks it as an estimate.
     */
    private suspend fun streamManifestToTemp(
        jobId: String,
        body: HttpBody,
        contentType: String?,
        sourceUrl: String,
    ): TempDownload? {
        val manifestText = runCatching { readManifestText(body) }.getOrNull()
        if (manifestText == null) {
            fail(jobId, JobErrorCode.EXTRACTION_FAILURE, "The manifest could not be read.", retryable = false)
            return null
        }
        val isDash = contentType?.startsWith("application/dash+xml", ignoreCase = true) == true ||
            sourceUrl.substringBefore('?').substringBefore('#').endsWith(".mpd", ignoreCase = true) ||
            manifestText.contains("<MPD")
        var fragments: List<MediaFragment> = emptyList()
        var initSegment: MediaFragment? = null
        var key: com.anydownlod.core.download.Aes128KeyInfo? = null
        var artifactExt = "ts"
        if (isDash) {
            when (val result = Mpd.parse(sourceUrl, manifestText)) {
                is MpdResult.Failed -> {
                    fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, result.reason, retryable = false)
                    return null
                }

                is MpdResult.Formats -> {
                    val format = selectBestManifestFormat(result.formats) ?: run {
                        fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, "The MPD declared no downloadable representation.", retryable = false)
                        return null
                    }
                    fragments = format.fragments ?: emptyList()
                    artifactExt = "mp4"
                }
            }
        } else {
            when (val first = M3u8.parse(sourceUrl, manifestText)) {
                is ManifestResult.Failed -> {
                    fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, first.reason, retryable = false)
                    return null
                }

                is ManifestResult.Master -> {
                    val variant = selectBestManifestFormat(first.formats) ?: run {
                        fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, "The master playlist declared no variant.", retryable = false)
                        return null
                    }
                    val variantUrl = variant.url ?: run {
                        fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, "The master playlist variant had no URI.", retryable = false)
                        return null
                    }
                    val variantText = fetchManifestText(variantUrl) ?: run {
                        fail(jobId, JobErrorCode.NETWORK_FAILURE, "The media playlist could not be fetched.")
                        return null
                    }
                    when (val second = M3u8.parse(variantUrl, variantText)) {
                        is ManifestResult.Media -> {
                            fragments = second.fragments
                            initSegment = second.initSegment
                            key = second.key
                        }

                        is ManifestResult.Master -> {
                            fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, "The playlist nested another master playlist.", retryable = false)
                            return null
                        }

                        is ManifestResult.Failed -> {
                            fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, second.reason, retryable = false)
                            return null
                        }
                    }
                }

                is ManifestResult.Media -> {
                    fragments = first.fragments
                    initSegment = first.initSegment
                    key = first.key
                }
            }
        }
        if (fragments.isEmpty()) {
            fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, "The manifest declared no fragments.", retryable = false)
            return null
        }
        val handle = runCatching { fileStore.createTempFile() }.getOrNull()
        if (handle == null) {
            fail(jobId, JobErrorCode.DISK_EXHAUSTED, "The download folder could not be created.")
            return null
        }
        var finished = false
        var written = 0L
        try {
            val outcome = FragmentDownloader(transfer).download(
                fragments = fragments,
                initSegment = initSegment,
                key = key,
                onChunk = { chunk ->
                    handle.write(chunk, chunk.size)
                    written += chunk.size
                },
                onProgress = { completed, total, bytes ->
                    update(jobId, persistNow = false) {
                        it.copy(
                            progress = JobProgress(
                                phase = "fragments $completed/$total (estimated)",
                                percent = (completed.toDouble() / total) * 100.0,
                                downloadedBytes = bytes,
                                totalBytes = null,
                            ),
                        )
                    }
                },
                isCancelled = { isCancelRequested(jobId) },
            )
            currentCoroutineContext().ensureActive()
            when (outcome) {
                is FragmentOutcome.Cancelled -> {
                    confirmCancelled(jobId)
                    return null
                }

                is FragmentOutcome.Failed -> {
                    fail(jobId, JobErrorCode.NETWORK_FAILURE, outcome.reason)
                    return null
                }

                is FragmentOutcome.Completed -> Unit
            }
            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
                return null
            }
            val manifestTitle = sourceUrl.substringAfterLast('/').substringBefore('?').substringBefore('#')
                .substringBeforeLast('.', missingDelimiterValue = "")
                .ifBlank { "media" }
            handle.close()
            finished = true
            return TempDownload(
                handle = handle,
                bytes = written,
                totalBytes = null,
                suggestedTitle = manifestTitle,
                suggestedExt = artifactExt,
            )
        } finally {
            if (!finished) {
                runCatching { handle.discard() }
            }
        }
    }

    private fun selectBestManifestFormat(formats: List<MediaFormat>): MediaFormat? =
        formats.filter { it.fragments?.isNotEmpty() == true || it.protocol != "http_dash_segments" }
            .maxByOrNull { it.tbr ?: -1.0 }

    /** Reads at most 2 MiB of manifest text; larger playlists fail typed. */
    private suspend fun readManifestText(body: HttpBody): String {
        val chunks = mutableListOf<ByteArray>()
        var total = 0
        val buffer = ByteArray(64 * 1024)
        try {
            while (true) {
                val read = withContext(ioDispatcher) { body.readNext(buffer) }
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > MANIFEST_MAX_BYTES) throw IllegalArgumentException("manifest too large")
                chunks += buffer.copyOf(read)
            }
        } finally {
            runCatching { body.close() }
        }
        val bytes = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(bytes, offset)
            offset += chunk.size
        }
        return bytes.decodeToString()
    }

    private suspend fun fetchManifestText(url: String): String? {
        var current = url
        for (hop in 0..maxRedirects) {
            when (val response = withContext(ioDispatcher) { transfer.execute(HttpRequest(current)) }) {
                is HttpResponse.Redirect -> current = response.location
                is HttpResponse.Final -> {
                    if (response.statusCode !in 200..299) return null
                    val body = response.body ?: return null
                    return runCatching { readManifestText(body) }.getOrNull()
                }

                is HttpResponse.Failed, is HttpResponse.Unavailable -> return null
            }
        }
        return null
    }

    /**
     * Streams [body] into a temp file and returns it closed on success. On
     * failure or cancel the temp is removed. The caller publishes the temp or
     * merges it.
     */
    private suspend fun streamToTemp(
        jobId: String,
        body: HttpBody,
        totalBytes: Long?,
    ): TempDownload? {
        val handle = runCatching { fileStore.createTempFile() }.getOrNull()
        if (handle == null) {
            runCatching { body.close() }
            fail(jobId, JobErrorCode.DISK_EXHAUSTED, "The download folder could not be created.")
            return null
        }

        var downloaded = 0L
        var finished = false
        try {
            val buffer = ByteArray(chunkSize)
            while (!isCancelRequested(jobId)) {
                val count = withContext(ioDispatcher) { body.readNext(buffer) }
                if (count == -1) break
                if (count == 0) continue
                handle.write(buffer, count)
                downloaded += count
                reportProgress(jobId, downloaded, totalBytes)
                currentCoroutineContext().ensureActive()
            }
            currentCoroutineContext().ensureActive()

            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
                return null
            }

            handle.close()
            finished = true
            return TempDownload(handle, downloaded, totalBytes)
        } finally {
            runCatching { body.close() }
            if (!finished) {
                runCatching { handle.discard() }
            }
        }
    }

    /**
     * Ranged chunk reader for formats that declare `http_chunk_size`. Each
     * chunk is one `Range` request appended to the same temp file; the total
     * comes from the first chunk's `Content-Range` (or the declared format
     * size). A non-2xx chunk status maps through the shared HTTP table, so a
     * mid-stream 403 is `UNAVAILABLE_OR_PRIVATE`, not `NETWORK_FAILURE`.
     */
    private suspend fun streamChunkedToTemp(
        jobId: String,
        firstBody: HttpBody,
        firstRangeTotal: Long?,
        declaredSize: Long?,
        sourceUrl: String,
        headers: Map<String, String>,
        chunkSize: Long,
    ): TempDownload? {
        val handle = runCatching { fileStore.createTempFile() }.getOrNull()
        if (handle == null) {
            runCatching { firstBody.close() }
            fail(jobId, JobErrorCode.DISK_EXHAUSTED, "The download folder could not be created.")
            return null
        }

        var downloaded = 0L
        var finished = false
        var body: HttpBody? = firstBody
        var total = firstRangeTotal ?: declaredSize
        try {
            while (true) {
                if (isCancelRequested(jobId)) {
                    confirmCancelled(jobId)
                    return null
                }
                val currentBody = body ?: break
                body = null
                val buffer = ByteArray(chunkSize.toInt().coerceIn(4096, 64 * 1024))
                var chunkRead = 0L
                while (true) {
                    val count = withContext(ioDispatcher) { currentBody.readNext(buffer) }
                    if (count == -1) break
                    if (count == 0) continue
                    handle.write(buffer, count)
                    chunkRead += count
                    downloaded += count
                    reportProgress(jobId, downloaded, total)
                    currentCoroutineContext().ensureActive()
                }
                runCatching { currentBody.close() }

                if (isCancelRequested(jobId)) {
                    confirmCancelled(jobId)
                    return null
                }
                if (total != null && downloaded >= total) break
                if (chunkRead <= 0L) {
                    fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source stopped before the file was complete.")
                    return null
                }

                val start = downloaded
                val end = if (total != null) {
                    minOf(start + chunkSize - 1, total - 1)
                } else {
                    start + chunkSize - 1
                }
                val request = HttpRequest(url = sourceUrl, headers = headers, range = start..end)
                when (val response = withContext(ioDispatcher) { transfer.execute(request) }) {
                    is HttpResponse.Final -> {
                        if (response.statusCode == 416) break // nothing left to fetch
                        if (response.statusCode !in 200..299) {
                            runCatching { response.body?.close() }
                            val error = mapHttpStatus(response.statusCode)
                            fail(jobId, error.code, error.message, error.retryable)
                            return null
                        }
                        if (total == null) {
                            total = response.contentRange?.let { ContentRange.totalBytes(it) } ?: declaredSize
                        }
                        val nextBody = response.body
                        if (nextBody == null) {
                            fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source returned an empty chunk.")
                            return null
                        }
                        body = nextBody
                    }

                    is HttpResponse.Redirect -> {
                        fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source redirected between chunks.")
                        return null
                    }

                    is HttpResponse.Failed -> {
                        fail(jobId, JobErrorCode.NETWORK_FAILURE, response.message)
                        return null
                    }

                    is HttpResponse.Unavailable -> {
                        fail(
                            jobId,
                            JobErrorCode.ENGINE_UNAVAILABLE,
                            "A required download tool is missing on this device.",
                            retryable = false,
                        )
                        return null
                    }
                }
            }

            currentCoroutineContext().ensureActive()
            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
                return null
            }
            if (total != null && downloaded < total) {
                fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source stopped before the file was complete.")
                return null
            }

            handle.close()
            finished = true
            return TempDownload(handle, downloaded, total)
        } finally {
            runCatching { body?.close() }
            if (!finished) {
                runCatching { handle.discard() }
            }
        }
    }

    private fun artifactPath(
        options: DownloadOptions,
        sourceUrl: String,
        artifactTitle: String?,
        artifactExt: String?,
        requestedPath: String? = null,
    ): String? = try {
        if (!requestedPath.isNullOrBlank()) {
            requestedPath
        } else if (artifactTitle != null) {
            ArtifactName.build(artifactTitle, artifactExt, options)
        } else {
            ArtifactName.build(sourceUrl, options)
        }
    } catch (failure: IllegalArgumentException) {
        null
    }

    /** Closes, publishes, and completes; false when the path was refused. */
    private suspend fun publishAndComplete(
        jobId: String,
        handle: FileHandle,
        downloaded: Long,
        totalBytes: Long?,
        options: DownloadOptions,
        relativePath: String,
        tagsEmbedded: Boolean? = null,
        lyricsEmbedded: Boolean? = null,
    ): Boolean {
        handle.close()
        val published = try {
            fileStore.publish(handle, relativePath)
        } catch (failure: Throwable) {
            fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
            return false
        }
        complete(jobId, downloaded, totalBytes, options.mediaType, published, relativePath, tagsEmbedded, lyricsEmbedded)
        return true
    }

    private fun complete(
        jobId: String,
        downloaded: Long,
        totalBytes: Long?,
        mediaType: MediaType,
        relativePath: String,
        publishedPath: String,
        tagsEmbedded: Boolean? = null,
        lyricsEmbedded: Boolean? = null,
    ) {
        val sizeBytes = runCatching { fileStore.size(publishedPath) }.getOrNull() ?: downloaded
        val artifact = Artifact(
            id = "artifact-${idGenerator()}",
            jobId = jobId,
            kind = artifactKind(mediaType, relativePath),
            fileName = relativePath.substringAfterLast('/'),
            relativePath = relativePath,
            sizeBytes = sizeBytes,
        )
        val lrcArtifact = writeLrcSibling(jobId, publishedPath)
        update(jobId) {
            it.copy(
                state = JobState.COMPLETED,
                progress = JobProgress(
                    phase = "completed",
                    percent = 100.0,
                    downloadedBytes = downloaded,
                    totalBytes = totalBytes,
                ),
                error = null,
                finishedAtEpochMillis = now(),
                tagsEmbedded = tagsEmbedded,
                lyricsEmbedded = lyricsEmbedded,
                artifacts = it.artifacts + artifact + listOfNotNull(lrcArtifact),
            )
        }
    }

    /**
     * Writes the request's timed LRC next to the published audio when the
     * Spotify path asked for one. A failed sidecar write is not fatal: the
     * audio is already complete and no half-file is published.
     */
    private fun writeLrcSibling(jobId: String, audioPath: String): Artifact? {
        val content = findJob(jobId)?.request?.lrcContent?.takeIf { it.isNotBlank() } ?: return null
        val dot = audioPath.lastIndexOf('.')
        val lrcPath = if (dot > 0) audioPath.substring(0, dot) + ".lrc" else "$audioPath.lrc"
        return try {
            val handle = fileStore.createTempFile("lrc")
            val published = try {
                val bytes = content.encodeToByteArray()
                handle.write(bytes, bytes.size)
                handle.close()
                fileStore.publish(handle, lrcPath)
            } catch (failure: Throwable) {
                runCatching { handle.discard() }
                throw failure
            }
            Artifact(
                id = "artifact-${idGenerator()}",
                jobId = jobId,
                kind = ArtifactKind.METADATA,
                fileName = published.substringAfterLast('/'),
                relativePath = published,
                sizeBytes = runCatching { fileStore.size(published) }.getOrNull(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun reportProgress(jobId: String, downloaded: Long, totalBytes: Long?) {
        update(jobId, persistNow = false) {
            it.copy(
                progress = JobProgress(
                    phase = "downloading",
                    percent = totalBytes?.takeIf { it > 0 }?.let { (downloaded.toDouble() / it) * 100.0 },
                    downloadedBytes = downloaded,
                    totalBytes = totalBytes,
                ),
            )
        }
    }

    private fun fail(jobId: String, code: JobErrorCode, message: String, retryable: Boolean = true): DownloadJob? =
        update(jobId) {
            it.copy(
                state = JobState.FAILED,
                error = JobError(code, message, retryable),
                finishedAtEpochMillis = now(),
            )
        }

    private fun confirmCancelled(jobId: String): DownloadJob? =
        update(jobId) {
            it.copy(
                state = JobState.CANCELLED,
                error = cancelledError(),
                finishedAtEpochMillis = now(),
            )
        }

    private fun isCancelRequested(jobId: String): Boolean =
        engineCriticalSection(lock) { cancelRequested[jobId] == true }

    private fun update(
        jobId: String,
        persistNow: Boolean = true,
        transform: (DownloadJob) -> DownloadJob,
    ): DownloadJob? = engineCriticalSection(lock) {
        val current = _jobs.value.firstOrNull { it.id == jobId } ?: return@engineCriticalSection null
        val transformed = transform(current).copy(
            revision = current.revision + 1,
            updatedAtEpochMillis = now(),
        )
        // The latest attempt mirrors the job row so the screens read one source
        // of truth; older attempts keep their own history.
        val attempts = if (transformed.attempts.isEmpty()) {
            listOf(
                JobAttempt(
                    id = "attempt-${idGenerator()}",
                    jobId = jobId,
                    state = transformed.state,
                    progress = transformed.progress,
                    error = transformed.error,
                    startedAtEpochMillis = transformed.startedAtEpochMillis,
                    finishedAtEpochMillis = transformed.finishedAtEpochMillis,
                )
            )
        } else {
            transformed.attempts.dropLast(1) + transformed.attempts.last().copy(
                state = transformed.state,
                progress = transformed.progress,
                error = transformed.error,
                finishedAtEpochMillis = transformed.finishedAtEpochMillis,
            )
        }
        val updated = transformed.copy(attempts = attempts)
        _jobs.value = _jobs.value.map { if (it.id == jobId) updated else it }
        if (persistNow) persist(_jobs.value)
        updated
    }

    private fun findJob(jobId: String): DownloadJob? = _jobs.value.firstOrNull { it.id == jobId }

    private fun redactedUrlReason(reason: UrlRejectReason): String = when (reason) {
        UrlRejectReason.UnsupportedScheme -> "Only http and https source URLs are supported."
        UrlRejectReason.MissingHost -> "This URL has no host."
        UrlRejectReason.UserInfo -> "This URL embeds credentials and was refused."
        UrlRejectReason.BlockedDestination -> "This URL points to a local or reserved address and was refused."
        UrlRejectReason.Malformed -> "This URL is malformed."
    }

    private fun mapHttpStatus(status: Int): JobError = when (status) {
        401 -> JobError(
            JobErrorCode.LOGIN_REQUIRED,
            "This source needs a sign-in. Add cookies or pick another source.",
            retryable = false,
        )

        403 -> JobError(JobErrorCode.UNAVAILABLE_OR_PRIVATE, "This source refused the download.", retryable = false)
        404, 410, 451 -> JobError(
            JobErrorCode.UNAVAILABLE_OR_PRIVATE,
            "This file is unavailable or was removed.",
            retryable = false,
        )

        429 -> JobError(JobErrorCode.RATE_LIMITED, "The source rate-limited this download. Try again later.", retryable = true)
        in 500..599 -> JobError(JobErrorCode.NETWORK_FAILURE, "The source failed temporarily. Try again.", retryable = true)
        in 300..399 -> JobError(
            JobErrorCode.NETWORK_FAILURE,
            "The source redirected without a usable destination.",
            retryable = true,
        )

        else -> JobError(JobErrorCode.NETWORK_FAILURE, "The source returned an unexpected response.", retryable = true)
    }

    private fun cancelledError(): JobError =
        JobError(JobErrorCode.CANCELLED, "The download was cancelled.", retryable = false)

    private fun artifactKind(mediaType: MediaType, relativePath: String): ArtifactKind {
        val lower = relativePath.lowercase()
        return when {
            lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
                lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".m4v") -> ArtifactKind.VIDEO
            lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") ||
                lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".opus") ||
                lower.endsWith(".ogg") -> ArtifactKind.AUDIO
            lower.endsWith(".srt") || lower.endsWith(".vtt") || lower.endsWith(".ttml") ||
                lower.endsWith(".sbv") -> ArtifactKind.CAPTIONS
            else -> when (mediaType) {
                MediaType.VIDEO -> ArtifactKind.VIDEO
                MediaType.AUDIO -> ArtifactKind.AUDIO
                MediaType.CAPTIONS -> ArtifactKind.CAPTIONS
                MediaType.THUMBNAIL -> ArtifactKind.THUMBNAIL
            }
        }
    }
}

/** Selection outcome for the registry route, testable without a network. */
internal sealed interface FormatResolution {
    data class Ready(val format: MediaFormat) : FormatResolution
    data class Merge(val video: MediaFormat, val audio: MediaFormat) : FormatResolution
    data class ExtractAudio(val format: MediaFormat, val container: AudioContainer) : FormatResolution
    data class Unsupported(val message: String) : FormatResolution
}

internal fun resolveFormat(
    info: InfoDict,
    options: DownloadOptions,
    canMerge: Boolean = false,
    audioContainers: Set<AudioContainer> = emptySet(),
): FormatResolution =
    when (val compiled = OptionsToSpec.compile(options, canMerge, audioContainers)) {
        is CompiledSpec.NeedsToolkit -> FormatResolution.Unsupported(
            "${compiled.message} Choose M4A or Opus audio, or a single-file video, instead.",
        )

        CompiledSpec.NotInPhase -> FormatResolution.Unsupported(
            "Captions and thumbnails arrive in a later phase.",
        )

        is CompiledSpec.SingleFile -> resolveSelection(
            FormatSelector.select(info, compiled.spec, compiled.sort),
            info.formatsNeedingJs,
            canMerge,
        )

        is CompiledSpec.ExtractAudio -> when (val selection = FormatSelector.select(info, compiled.spec)) {
            is Selection.Single -> FormatResolution.ExtractAudio(selection.format, compiled.container)
            else -> FormatResolution.Unsupported(
                buildString {
                    append("No audio-only format matches this choice.")
                    if (info.formatsNeedingJs > 0) {
                        append(" ${info.formatsNeedingJs} more formats need the JavaScript runtime.")
                    }
                },
            )
        }
    }

internal fun resolveSelection(
    selection: Selection,
    formatsNeedingJs: Int,
    canMerge: Boolean = false,
): FormatResolution = when (selection) {
    is Selection.Single -> FormatResolution.Ready(selection.format)
    is Selection.Merge -> if (canMerge) {
        FormatResolution.Merge(selection.video, selection.audio)
    } else {
        FormatResolution.Unsupported(
            "This host cannot merge video and audio, so this choice is unavailable. " +
                "Choose M4A or Opus audio, or a single-file video.",
        )
    }

    Selection.None -> FormatResolution.Unsupported(
        buildString {
            append("No single-file format matches this choice.")
            if (formatsNeedingJs > 0) {
                append(" $formatsNeedingJs more formats need the JavaScript runtime.")
            }
        },
    )
}

/** Maps a host toolkit failure to the typed job error the UI reads. */
internal fun toolkitJobError(error: ToolkitError): JobError = when (error) {
    is ToolkitError.ToolUnavailable -> JobError(
        JobErrorCode.UNSUPPORTED_FORMAT,
        error.message ?: "The media toolkit is not available on this host.",
        retryable = false,
    )

    is ToolkitError.IncompatibleStreams -> JobError(
        JobErrorCode.UNSUPPORTED_FORMAT,
        error.message ?: "This host cannot merge the selected streams into one file.",
        retryable = false,
    )

    is ToolkitError.Io -> JobError(
        JobErrorCode.POSTPROCESSING_FAILURE,
        error.message ?: "The media toolkit could not write the merged file.",
        retryable = error.retryable,
    )
}

internal fun extractionJobError(error: ExtractionError): JobError = when (error) {
        is ExtractionError.LoginRequired -> JobError(
            JobErrorCode.LOGIN_REQUIRED,
            "This source needs a sign-in. Add cookies or pick another source.",
            retryable = false,
        )

        is ExtractionError.AgeRestricted -> JobError(
            JobErrorCode.UNAVAILABLE_OR_PRIVATE,
            "This video is age-restricted.",
            retryable = false,
        )

        is ExtractionError.GeoRestricted -> JobError(
            JobErrorCode.UNAVAILABLE_OR_PRIVATE,
            "This source is not available in your region.",
            retryable = false,
        )

        is ExtractionError.Unavailable -> JobError(
            JobErrorCode.UNAVAILABLE_OR_PRIVATE,
            error.message ?: "This source is unavailable.",
            retryable = false,
        )

        is ExtractionError.NoFormats -> JobError(
            JobErrorCode.UNSUPPORTED_FORMAT,
            error.message ?: "No downloadable format was found.",
            retryable = false,
        )

        is ExtractionError.Malformed -> JobError(
            JobErrorCode.EXTRACTION_FAILURE,
            "The source could not be read.",
            retryable = false,
        )

        is ExtractionError.UnsupportedUrl -> JobError(
            JobErrorCode.UNSUPPORTED_SOURCE,
            "This URL has no extractor.",
            retryable = false,
        )
    }
