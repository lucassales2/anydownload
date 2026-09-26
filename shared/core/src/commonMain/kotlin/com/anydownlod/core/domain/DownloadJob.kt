package com.anydownlod.core.domain

/**
 * Bounded progress snapshot for a job. Every field may be unknown.
 *
 * A missing total keeps [percent] null; the UI shows an indeterminate
 * indicator instead of inventing 0%. Speed and ETA stay null when the engine
 * has not reported them.
 */
data class JobProgress(
    val phase: String? = null,
    val percent: Double? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Double? = null,
    val etaSeconds: Long? = null,
)

/**
 * Error categories the local engine and store may record. [UNKNOWN] only
 * exists to read an older or newer persisted row; D1 code emits the others.
 */
enum class JobErrorCode(val wireName: String) {
    INVALID_URL_OPTIONS("invalid_url_options"),
    UNSUPPORTED_SOURCE("unsupported_source"),
    UNAVAILABLE_OR_PRIVATE("unavailable_or_private"),
    LOGIN_REQUIRED("login_required"),
    RATE_LIMITED("rate_limited"),
    NETWORK_FAILURE("network_failure"),
    EXTRACTION_FAILURE("extraction_failure"),
    UNSUPPORTED_FORMAT("unsupported_format"),
    POSTPROCESSING_FAILURE("postprocessing_failure"),
    DISK_EXHAUSTED("disk_exhausted"),
    CANCELLED("cancelled"),
    ENGINE_UNAVAILABLE("engine_unavailable"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromWire(value: String): JobErrorCode =
            entries.firstOrNull { it.wireName == value.trim().lowercase() } ?: UNKNOWN
    }
}

/**
 * Short, safe error information for a job row. Raw process output, signed
 * media URLs, and cookie material never reach this type.
 */
data class JobError(
    val code: JobErrorCode,
    val message: String,
    val retryable: Boolean = false,
)

/** What a finished download wrote under the download root. */
enum class ArtifactKind(val wireName: String) {
    VIDEO("video"),
    AUDIO("audio"),
    CAPTIONS("captions"),
    THUMBNAIL("thumbnail"),
    CHAPTER("chapter"),
    METADATA("metadata"),
    ;
}

/**
 * A file the engine registered for a job. [relativePath] is resolved against
 * the download root by the host; it is never an absolute path.
 */
data class Artifact(
    val id: String,
    val jobId: String,
    val kind: ArtifactKind,
    val fileName: String,
    val relativePath: String,
    val sizeBytes: Long? = null,
    /** True once the host removed the file; the history row may stay. */
    val removed: Boolean = false,
)

/**
 * One run of a job. A retry appends a new attempt; an older attempt keeps its
 * own state, progress, and error, so a failed run is never rewritten as if it
 * had not happened.
 */
data class JobAttempt(
    val id: String,
    val jobId: String,
    val state: JobState,
    val progress: JobProgress? = null,
    val error: JobError? = null,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null,
)

/**
 * A download job owned by the local app. The on-device store is authoritative;
 * [revision] increments on every change so a stale write can be detected.
 *
 * [progress] and [error] mirror the latest attempt for row rendering, while
 * [attempts] keeps the run history.
 */
data class DownloadJob(
    val id: String,
    val request: DownloadRequest,
    val state: JobState,
    val revision: Long = 0,
    val createdAtEpochMillis: Long = 0,
    val updatedAtEpochMillis: Long = 0,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null,
    val title: String? = null,
    val sourceHost: String? = null,
    val thumbnailUrl: String? = null,
    /** Formats the extractor dropped because they need a JavaScript runtime. */
    val formatsNeedingJs: Int = 0,
    /**
     * Whether the request's Spotify tags were embedded: null when none were
     * requested, true when the host toolkit wrote them, false when the host
     * cannot embed (the audio file is still kept).
     */
    val tagsEmbedded: Boolean? = null,
    /**
     * Whether lyrics were embedded: null when none were requested, true when
     * the container supports lyrics and the toolkit wrote them, false when the
     * container cannot carry them (the audio file is still kept).
     */
    val lyricsEmbedded: Boolean? = null,
    val parentBatchId: String? = null,
    val subscriptionId: String? = null,
    /** Due time for [JobState.SCHEDULED] rows; null when unknown. */
    val scheduledAtEpochMillis: Long? = null,
    val progress: JobProgress? = null,
    val error: JobError? = null,
    val artifacts: List<Artifact> = emptyList(),
    val attempts: List<JobAttempt> = emptyList(),
) {
    val latestAttempt: JobAttempt? get() = attempts.lastOrNull()

    val mediaType: MediaType get() = request.options.mediaType
}

/** Engine/API capabilities advertised by the withdrawn server. */
data class Capabilities(
    val apiVersion: String,
    val engine: EngineInfo? = null,
    val mediaTypes: Set<MediaType> = emptySet(),
    val features: Set<String> = emptySet(),
) {
    fun supports(mediaType: MediaType): Boolean = mediaType in mediaTypes
}

data class EngineInfo(
    val name: String,
    val version: String? = null,
)
