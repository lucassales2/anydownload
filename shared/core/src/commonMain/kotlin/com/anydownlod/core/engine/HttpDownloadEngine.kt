package com.anydownlod.core.engine

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
        semaphore.withPermit {
            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
                return@withPermit
            }
            val url = findJob(jobId)?.request?.sourceUrl ?: return@withPermit
            val options = findJob(jobId)?.request?.options ?: DownloadOptions()
            try {
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
        when (val resolution = resolveFormat(info, options)) {
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
        }
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
    ) {
        val initialRange = chunkSize?.takeIf { it > 0 }?.let { 0L..(it - 1) }
        var current = HttpRequest(url = url, headers = headers, range = initialRange)
        var hops = 0
        var body: HttpBody? = null
        try {
            while (true) {
                when (val policy = urlCheck(current.url)) {
                    is UrlCheck.Rejected -> {
                        fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, redactedUrlReason(policy.reason))
                        return
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
                        return
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
                        return
                    }

                    is HttpResponse.Redirect -> {
                        if (response.location.isBlank()) {
                            fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source redirected without a destination.")
                            return
                        }
                        hops++
                        if (hops > maxRedirects) {
                            fail(jobId, JobErrorCode.NETWORK_FAILURE, "Too many redirects.")
                            return
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
                            return
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
                                    return
                                }
                                val pageBody = body
                                if (pageBody == null) {
                                    fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source returned an empty response body.")
                                    return
                                }
                                body = null // ownership passes to the extraction step
                                extractMediaFromPage(jobId, pageBody, current.url, options)
                                return
                            }

                            UrlClassifier.Classification.MANIFEST -> {
                                val manifestBody = body
                                if (manifestBody == null) {
                                    fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source returned an empty response body.")
                                    return
                                }
                                body = null // ownership passes to the manifest step
                                streamManifestToFile(jobId, manifestBody, response.contentType, current.url, options)
                                return
                            }

                            UrlClassifier.Classification.DIRECT_FILE -> {
                                val fileBody = body
                                if (fileBody == null) {
                                    fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source returned an empty response body.")
                                    return
                                }
                                body = null // ownership passes to the streaming step
                                val chunked = chunkSize?.takeIf { it > 0 && response.statusCode == 206 }
                                if (chunked != null) {
                                    streamChunkedToFile(
                                        jobId = jobId,
                                        firstBody = fileBody,
                                        firstRangeTotal = response.totalBytes ?: declaredSize,
                                        declaredSize = declaredSize,
                                        sourceUrl = current.url,
                                        headers = headers,
                                        chunkSize = chunked,
                                        options = options,
                                        artifactTitle = artifactTitle,
                                        artifactExt = artifactExt,
                                    )
                                } else {
                                    streamToFile(
                                        jobId = jobId,
                                        body = fileBody,
                                        totalBytes = response.totalBytes ?: declaredSize,
                                        options = options,
                                        sourceUrl = current.url,
                                        artifactTitle = artifactTitle,
                                        artifactExt = artifactExt,
                                    )
                                }
                                return
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
    private suspend fun streamManifestToFile(
        jobId: String,
        body: HttpBody,
        contentType: String?,
        sourceUrl: String,
        options: DownloadOptions,
    ) {
        val manifestText = runCatching { readManifestText(body) }.getOrNull()
        if (manifestText == null) {
            fail(jobId, JobErrorCode.EXTRACTION_FAILURE, "The manifest could not be read.", retryable = false)
            return
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
                    return
                }

                is MpdResult.Formats -> {
                    val format = selectBestManifestFormat(result.formats) ?: run {
                        fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, "The MPD declared no downloadable representation.", retryable = false)
                        return
                    }
                    fragments = format.fragments ?: emptyList()
                    artifactExt = "mp4"
                }
            }
        } else {
            when (val first = M3u8.parse(sourceUrl, manifestText)) {
                is ManifestResult.Failed -> {
                    fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, first.reason, retryable = false)
                    return
                }

                is ManifestResult.Master -> {
                    val variant = selectBestManifestFormat(first.formats) ?: run {
                        fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, "The master playlist declared no variant.", retryable = false)
                        return
                    }
                    val variantUrl = variant.url ?: run {
                        fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, "The master playlist variant had no URI.", retryable = false)
                        return
                    }
                    val variantText = fetchManifestText(variantUrl) ?: run {
                        fail(jobId, JobErrorCode.NETWORK_FAILURE, "The media playlist could not be fetched.")
                        return
                    }
                    when (val second = M3u8.parse(variantUrl, variantText)) {
                        is ManifestResult.Media -> {
                            fragments = second.fragments
                            initSegment = second.initSegment
                            key = second.key
                        }

                        is ManifestResult.Master -> {
                            fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, "The playlist nested another master playlist.", retryable = false)
                            return
                        }

                        is ManifestResult.Failed -> {
                            fail(jobId, JobErrorCode.UNSUPPORTED_FORMAT, second.reason, retryable = false)
                            return
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
            return
        }
        val handle = runCatching { fileStore.createTempFile() }.getOrNull()
        if (handle == null) {
            fail(jobId, JobErrorCode.DISK_EXHAUSTED, "The download folder could not be created.")
            return
        }
        var published = false
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
                    return
                }

                is FragmentOutcome.Failed -> {
                    fail(jobId, JobErrorCode.NETWORK_FAILURE, outcome.reason)
                    return
                }

                is FragmentOutcome.Completed -> Unit
            }
            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
                return
            }
            val manifestTitle = sourceUrl.substringAfterLast('/').substringBefore('?').substringBefore('#')
                .substringBeforeLast('.', missingDelimiterValue = "")
                .ifBlank { "media" }
            val relativePath = artifactPath(options, sourceUrl, manifestTitle, artifactExt) ?: run {
                fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
                return
            }
            published = publishAndComplete(jobId, handle, written, null, options, relativePath)
        } finally {
            if (!published) {
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

    private suspend fun streamToFile(
        jobId: String,
        body: HttpBody,
        totalBytes: Long?,
        options: DownloadOptions,
        sourceUrl: String,
        artifactTitle: String? = null,
        artifactExt: String? = null,
    ) {
        val handle = runCatching { fileStore.createTempFile() }.getOrNull()
        if (handle == null) {
            runCatching { body.close() }
            fail(jobId, JobErrorCode.DISK_EXHAUSTED, "The download folder could not be created.")
            return
        }

        var downloaded = 0L
        var published = false
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
                return
            }

            val relativePath = artifactPath(options, sourceUrl, artifactTitle, artifactExt) ?: run {
                fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
                return
            }
            published = publishAndComplete(jobId, handle, downloaded, totalBytes, options, relativePath)
        } finally {
            runCatching { body.close() }
            if (!published) {
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
    private suspend fun streamChunkedToFile(
        jobId: String,
        firstBody: HttpBody,
        firstRangeTotal: Long?,
        declaredSize: Long?,
        sourceUrl: String,
        headers: Map<String, String>,
        chunkSize: Long,
        options: DownloadOptions,
        artifactTitle: String?,
        artifactExt: String?,
    ) {
        val handle = runCatching { fileStore.createTempFile() }.getOrNull()
        if (handle == null) {
            runCatching { firstBody.close() }
            fail(jobId, JobErrorCode.DISK_EXHAUSTED, "The download folder could not be created.")
            return
        }

        var downloaded = 0L
        var published = false
        var body: HttpBody? = firstBody
        var total = firstRangeTotal ?: declaredSize
        try {
            while (true) {
                if (isCancelRequested(jobId)) {
                    confirmCancelled(jobId)
                    return
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
                    return
                }
                if (total != null && downloaded >= total) break
                if (chunkRead <= 0L) {
                    fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source stopped before the file was complete.")
                    return
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
                            return
                        }
                        if (total == null) {
                            total = response.contentRange?.let { ContentRange.totalBytes(it) } ?: declaredSize
                        }
                        val nextBody = response.body
                        if (nextBody == null) {
                            fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source returned an empty chunk.")
                            return
                        }
                        body = nextBody
                    }

                    is HttpResponse.Redirect -> {
                        fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source redirected between chunks.")
                        return
                    }

                    is HttpResponse.Failed -> {
                        fail(jobId, JobErrorCode.NETWORK_FAILURE, response.message)
                        return
                    }

                    is HttpResponse.Unavailable -> {
                        fail(
                            jobId,
                            JobErrorCode.ENGINE_UNAVAILABLE,
                            "A required download tool is missing on this device.",
                            retryable = false,
                        )
                        return
                    }
                }
            }

            currentCoroutineContext().ensureActive()
            if (isCancelRequested(jobId)) {
                confirmCancelled(jobId)
                return
            }
            if (total != null && downloaded < total) {
                fail(jobId, JobErrorCode.NETWORK_FAILURE, "The source stopped before the file was complete.")
                return
            }

            val relativePath = artifactPath(options, sourceUrl, artifactTitle, artifactExt) ?: run {
                fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
                return
            }
            published = publishAndComplete(jobId, handle, downloaded, total, options, relativePath)
        } finally {
            runCatching { body?.close() }
            if (!published) {
                runCatching { handle.discard() }
            }
        }
    }

    private fun artifactPath(
        options: DownloadOptions,
        sourceUrl: String,
        artifactTitle: String?,
        artifactExt: String?,
    ): String? = try {
        if (artifactTitle != null) {
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
    ): Boolean {
        handle.close()
        val published = try {
            fileStore.publish(handle, relativePath)
        } catch (failure: Throwable) {
            fail(jobId, JobErrorCode.INVALID_URL_OPTIONS, "The output path is unsafe.")
            return false
        }
        complete(jobId, downloaded, totalBytes, options.mediaType, published, relativePath)
        return true
    }

    private fun complete(
        jobId: String,
        downloaded: Long,
        totalBytes: Long?,
        mediaType: MediaType,
        relativePath: String,
        publishedPath: String,
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
                artifacts = it.artifacts + artifact,
            )
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
    data class Unsupported(val message: String) : FormatResolution
}

internal fun resolveFormat(info: InfoDict, options: DownloadOptions): FormatResolution =
    when (val compiled = OptionsToSpec.compile(options)) {
        is CompiledSpec.NeedsToolkit -> FormatResolution.Unsupported(
            "${compiled.message} Choose M4A or Opus audio, or a single-file video, instead.",
        )

        CompiledSpec.NotInPhase -> FormatResolution.Unsupported(
            "Captions and thumbnails arrive in a later phase.",
        )

        is CompiledSpec.SingleFile -> resolveSelection(
            FormatSelector.select(info, compiled.spec, compiled.sort),
            info.formatsNeedingJs,
        )
    }

internal fun resolveSelection(selection: Selection, formatsNeedingJs: Int): FormatResolution = when (selection) {
    is Selection.Single -> FormatResolution.Ready(selection.format)
    is Selection.Merge -> FormatResolution.Unsupported(
        "This choice needs merging, and the media toolkit is not built yet. " +
            "Choose M4A or Opus audio, or a single-file video.",
    )

    Selection.None -> FormatResolution.Unsupported(
        buildString {
            append("No single-file format matches this choice.")
            if (formatsNeedingJs > 0) {
                append(" $formatsNeedingJs more formats need the JavaScript runtime.")
            }
        },
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
