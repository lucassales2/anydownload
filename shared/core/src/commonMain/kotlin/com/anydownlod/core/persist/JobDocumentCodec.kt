package com.anydownlod.core.persist

import com.anydownlod.core.domain.Artifact
import com.anydownlod.core.domain.ArtifactKind
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.CaptionPreference
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
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.domain.SponsorBlockOutcome
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON codec for the on-device jobs document (T-102).
 *
 * The document is metadata only: it carries job rows, attempts, progress and
 * error summaries, and registered artifacts. Media bytes never enter it, and
 * there is no cookie or free-form yt-dlp JSON field. Unknown fields are
 * ignored so a newer file still loads; an unknown state wire name becomes
 * [JobState.UNKNOWN] instead of dropping the row.
 *
 * The shape stays compatible with the `jobs.json` the desktop host wrote
 * before this codec existed. [Json] settings match that file: pretty printed,
 * defaults encoded, unknown keys ignored.
 *
 * The DTOs here are private to this package on purpose. `OptionsDto` and
 * `ErrorDto` also appear in the desktop subscriptions document; duplicating
 * the shape keeps the jobs document stable even if that one changes.
 */
object JobDocumentCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Encodes the rows as the JSON document a host stores as bytes. */
    fun encode(jobs: List<DownloadJob>): String =
        json.encodeToString(JobsDocument(jobs.map { it.toDto() }))

    /**
     * Decodes a stored document. Throws the serializer's exception for
     * malformed JSON; the host decides whether to move the file aside.
     */
    fun decode(text: String): List<DownloadJob> =
        json.decodeFromString<JobsDocument>(text).jobs.map { it.toDomain() }
}

@Serializable
internal data class JobsDocument(val jobs: List<JobDto> = emptyList())

@Serializable
internal data class JobDto(
    val id: String,
    val request: RequestDto,
    val state: String,
    val revision: Long = 0,
    val createdAtEpochMillis: Long = 0,
    val updatedAtEpochMillis: Long = 0,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null,
    val title: String? = null,
    val sourceHost: String? = null,
    val thumbnailUrl: String? = null,
    val formatsNeedingJs: Int = 0,
    val tagsEmbedded: Boolean? = null,
    val lyricsEmbedded: Boolean? = null,
    val sponsorBlock: String? = null,
    val parentBatchId: String? = null,
    val subscriptionId: String? = null,
    val scheduledAtEpochMillis: Long? = null,
    val progress: ProgressDto? = null,
    val error: ErrorDto? = null,
    val artifacts: List<ArtifactDto> = emptyList(),
    val attempts: List<AttemptDto> = emptyList(),
)

@Serializable
internal data class RequestDto(
    val sourceUrl: String,
    val idempotencyKey: String = "",
    val options: OptionsDto = OptionsDto(),
    val metadata: MediaTagsDto? = null,
    val artworkUrl: String? = null,
    val parentBatchId: String? = null,
    val selectedMediaIds: List<String> = emptyList(),
    val relativePath: String? = null,
    val lrcContent: String? = null,
)

@Serializable
internal data class MediaTagsDto(
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val isrc: String? = null,
    val lyrics: String? = null,
)

/**
 * The custom yt-dlp JSON placeholder is intentionally not persisted: it is
 * always empty and nothing may read it (Q-09).
 */
@Serializable
internal data class OptionsDto(
    val mediaType: String = MediaType.VIDEO.wireName,
    val startPolicy: String = StartPolicy.AUTOMATIC.wireName,
    val videoProfile: String? = null,
    val videoCodec: String = VideoCodec.AUTO.wireName,
    val quality: String = QualityPreference.Best.token,
    val audioContainer: String? = null,
    val audioBitrate: String? = null,
    val captionLanguage: String? = null,
    val captionPreference: String? = null,
    val captionFormat: String? = null,
    val embedSubtitles: Boolean = false,
    val writeMetadata: Boolean = false,
    val writeThumbnail: Boolean = false,
    val filenamePrefix: String? = null,
    val destinationFolder: String? = null,
    val playlistItemLimit: Int = 0,
    val playlistItems: String = "",
    val overwrite: String = OverwriteMode.SKIP.wireName,
    val clipStart: String? = null,
    val clipEnd: String? = null,
    val splitByChapters: Boolean = false,
    val sponsorBlockRemove: Boolean = false,
    val presetIds: List<String> = emptyList(),
    val useCookies: Boolean = false,
)

@Serializable
internal data class ProgressDto(
    val phase: String? = null,
    val percent: Double? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Double? = null,
    val etaSeconds: Long? = null,
)

@Serializable
internal data class ErrorDto(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)

@Serializable
internal data class ArtifactDto(
    val id: String,
    val jobId: String,
    val kind: String,
    val fileName: String,
    val relativePath: String,
    val sizeBytes: Long? = null,
    val removed: Boolean = false,
)

@Serializable
internal data class AttemptDto(
    val id: String,
    val jobId: String,
    val state: String,
    val progress: ProgressDto? = null,
    val error: ErrorDto? = null,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null,
)

internal fun DownloadJob.toDto(): JobDto = JobDto(
    id = id,
    request = request.toDto(),
    state = state.wireName,
    revision = revision,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    startedAtEpochMillis = startedAtEpochMillis,
    finishedAtEpochMillis = finishedAtEpochMillis,
    title = title,
    sourceHost = sourceHost,
    thumbnailUrl = thumbnailUrl,
    formatsNeedingJs = formatsNeedingJs,
    tagsEmbedded = tagsEmbedded,
    lyricsEmbedded = lyricsEmbedded,
    sponsorBlock = sponsorBlock?.wireName,
    parentBatchId = parentBatchId,
    subscriptionId = subscriptionId,
    scheduledAtEpochMillis = scheduledAtEpochMillis,
    progress = progress?.toDto(),
    error = error?.toDto(),
    artifacts = artifacts.map { it.toDto() },
    attempts = attempts.map { it.toDto() },
)

internal fun JobDto.toDomain(): DownloadJob = DownloadJob(
    id = id,
    request = request.toDomain(),
    state = JobState.fromWire(state),
    revision = revision,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    startedAtEpochMillis = startedAtEpochMillis,
    finishedAtEpochMillis = finishedAtEpochMillis,
    title = title,
    sourceHost = sourceHost,
    thumbnailUrl = thumbnailUrl,
    formatsNeedingJs = formatsNeedingJs,
    tagsEmbedded = tagsEmbedded,
    lyricsEmbedded = lyricsEmbedded,
    sponsorBlock = SponsorBlockOutcome.fromWire(sponsorBlock),
    parentBatchId = parentBatchId,
    subscriptionId = subscriptionId,
    scheduledAtEpochMillis = scheduledAtEpochMillis,
    progress = progress?.toDomain(),
    error = error?.toDomain(),
    artifacts = artifacts.map { it.toDomain() },
    attempts = attempts.map { it.toDomain() },
)

internal fun DownloadRequest.toDto(): RequestDto = RequestDto(
    sourceUrl = sourceUrl,
    idempotencyKey = idempotencyKey,
    options = options.toDto(),
    metadata = metadata?.toDto(),
    artworkUrl = artworkUrl,
    parentBatchId = parentBatchId,
    selectedMediaIds = selectedMediaIds,
    relativePath = relativePath,
    lrcContent = lrcContent,
)

internal fun RequestDto.toDomain(): DownloadRequest = DownloadRequest(
    sourceUrl = sourceUrl,
    idempotencyKey = idempotencyKey,
    options = options.toDomain(),
    metadata = metadata?.toDomain(),
    artworkUrl = artworkUrl,
    parentBatchId = parentBatchId,
    selectedMediaIds = selectedMediaIds,
    relativePath = relativePath,
    lrcContent = lrcContent,
)

internal fun MediaTags.toDto(): MediaTagsDto = MediaTagsDto(
    title = title,
    artists = artists,
    album = album,
    albumArtist = albumArtist,
    year = year,
    trackNumber = trackNumber,
    discNumber = discNumber,
    isrc = isrc,
    lyrics = lyrics,
)

internal fun MediaTagsDto.toDomain(): MediaTags = MediaTags(
    title = title,
    artists = artists,
    album = album,
    albumArtist = albumArtist,
    year = year,
    trackNumber = trackNumber,
    discNumber = discNumber,
    isrc = isrc,
    lyrics = lyrics,
)

internal fun DownloadOptions.toDto(): OptionsDto = OptionsDto(
    mediaType = mediaType.wireName,
    startPolicy = startPolicy.wireName,
    videoProfile = videoProfile?.wireName,
    videoCodec = videoCodec.wireName,
    quality = quality.token,
    audioContainer = audioContainer?.wireName,
    audioBitrate = audioBitrate,
    captionLanguage = captionLanguage,
    captionPreference = captionPreference?.wireName,
    captionFormat = captionFormat?.wireName,
    embedSubtitles = embedSubtitles,
    writeMetadata = writeMetadata,
    writeThumbnail = writeThumbnail,
    filenamePrefix = filenamePrefix,
    destinationFolder = destinationFolder,
    playlistItemLimit = playlistItemLimit,
    playlistItems = playlistItems,
    overwrite = overwrite.wireName,
    clipStart = clipStart,
    clipEnd = clipEnd,
    splitByChapters = splitByChapters,
    sponsorBlockRemove = sponsorBlockRemove,
    presetIds = presetIds,
    useCookies = useCookies,
)

internal fun OptionsDto.toDomain(): DownloadOptions = DownloadOptions(
    mediaType = MediaType.fromWire(mediaType) ?: MediaType.VIDEO,
    startPolicy = StartPolicy.entries.firstOrNull { it.wireName == startPolicy } ?: StartPolicy.AUTOMATIC,
    videoProfile = videoProfile?.let(VideoContainerProfile::fromWire),
    videoCodec = VideoCodec.fromWire(videoCodec) ?: VideoCodec.AUTO,
    quality = QualityPreference.fromToken(quality),
    audioContainer = audioContainer?.let(AudioContainer::fromWire),
    audioBitrate = audioBitrate,
    captionLanguage = captionLanguage,
    captionPreference = captionPreference?.let(CaptionPreference::fromWire),
    captionFormat = captionFormat?.let(CaptionFormat::fromWire),
    embedSubtitles = embedSubtitles,
    writeMetadata = writeMetadata,
    writeThumbnail = writeThumbnail,
    filenamePrefix = filenamePrefix,
    destinationFolder = destinationFolder,
    playlistItemLimit = playlistItemLimit,
    playlistItems = playlistItems,
    overwrite = OverwriteMode.fromWire(overwrite) ?: OverwriteMode.SKIP,
    clipStart = clipStart,
    clipEnd = clipEnd,
    splitByChapters = splitByChapters,
    sponsorBlockRemove = sponsorBlockRemove,
    presetIds = presetIds,
    useCookies = useCookies,
    customYtDlpJson = "",
)

internal fun JobProgress.toDto(): ProgressDto = ProgressDto(
    phase = phase,
    percent = percent,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    speedBytesPerSecond = speedBytesPerSecond,
    etaSeconds = etaSeconds,
)

internal fun ProgressDto.toDomain(): JobProgress = JobProgress(
    phase = phase,
    percent = percent,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    speedBytesPerSecond = speedBytesPerSecond,
    etaSeconds = etaSeconds,
)

internal fun JobError.toDto(): ErrorDto = ErrorDto(
    code = code.wireName,
    message = message,
    retryable = retryable,
)

internal fun ErrorDto.toDomain(): JobError = JobError(
    code = JobErrorCode.fromWire(code),
    message = message,
    retryable = retryable,
)

internal fun Artifact.toDto(): ArtifactDto = ArtifactDto(
    id = id,
    jobId = jobId,
    kind = kind.wireName,
    fileName = fileName,
    relativePath = relativePath,
    sizeBytes = sizeBytes,
    removed = removed,
)

internal fun ArtifactDto.toDomain(): Artifact = Artifact(
    id = id,
    jobId = jobId,
    kind = ArtifactKind.entries.firstOrNull { it.wireName == kind } ?: ArtifactKind.VIDEO,
    fileName = fileName,
    relativePath = relativePath,
    sizeBytes = sizeBytes,
    removed = removed,
)

internal fun JobAttempt.toDto(): AttemptDto = AttemptDto(
    id = id,
    jobId = jobId,
    state = state.wireName,
    progress = progress?.toDto(),
    error = error?.toDto(),
    startedAtEpochMillis = startedAtEpochMillis,
    finishedAtEpochMillis = finishedAtEpochMillis,
)

internal fun AttemptDto.toDomain(): JobAttempt = JobAttempt(
    id = id,
    jobId = jobId,
    state = JobState.fromWire(state),
    progress = progress?.toDomain(),
    error = error?.toDomain(),
    startedAtEpochMillis = startedAtEpochMillis,
    finishedAtEpochMillis = finishedAtEpochMillis,
)
