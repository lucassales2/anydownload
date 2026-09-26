package com.anydownlod.desktop.store

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.AppSettingsDefaults
import com.anydownlod.core.domain.Artifact
import com.anydownlod.core.domain.ArtifactKind
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.ClipboardAccess
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
import com.anydownlod.core.domain.Preset
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.domain.Subscription
import com.anydownlod.core.domain.ThemePreference
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile
import kotlinx.serialization.Serializable

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
    val tagsEmbedded: Boolean? = null,
    val lyricsEmbedded: Boolean? = null,
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

@Serializable
internal data class SubscriptionsDocument(val subscriptions: List<SubscriptionDto> = emptyList())

@Serializable
internal data class SubscriptionDto(
    val id: String,
    val sourceUrl: String,
    val displayName: String,
    val paused: Boolean = false,
    val checkIntervalMinutes: Int = AppSettingsDefaults.SUBSCRIPTION_INTERVAL_MINUTES,
    val titleFilterRegex: String = "",
    val skipMembersOnly: Boolean = false,
    val downloadOptions: OptionsDto = OptionsDto(),
    val lastCheckedAtEpochMillis: Long? = null,
    val nextCheckAtEpochMillis: Long? = null,
    val lastError: ErrorDto? = null,
    val seenIds: List<String> = emptyList(),
)

@Serializable
internal data class SettingsDocument(
    val downloadRoot: String = "",
    val outputTemplate: String = AppSettingsDefaults.OUTPUT_TEMPLATE,
    val playlistTemplate: String = AppSettingsDefaults.PLAYLIST_TEMPLATE,
    val channelTemplate: String = AppSettingsDefaults.CHANNEL_TEMPLATE,
    val chapterTemplate: String = AppSettingsDefaults.CHAPTER_TEMPLATE,
    val maxConcurrentDownloads: Int = AppSettingsDefaults.MAX_CONCURRENT_DOWNLOADS,
    val clearCompletedAfterSeconds: Long = AppSettingsDefaults.CLEAR_COMPLETED_AFTER_SECONDS,
    val subscriptionIntervalMinutes: Int = AppSettingsDefaults.SUBSCRIPTION_INTERVAL_MINUTES,
    val theme: String = ThemePreference.SYSTEM.wireName,
    val clipboardAccess: String = ClipboardAccess.UNKNOWN.wireName,
    val handledClipboardUrl: String = "",
    val cookiesConfigured: Boolean = false,
    val cookieFilePath: String? = null,
    val presets: List<PresetDto> = emptyList(),
    val spotifyFallbackProviders: List<String> = emptyList(),
)

@Serializable
internal data class PresetDto(
    val id: String,
    val name: String,
    val options: Map<String, String> = emptyMap(),
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
    tagsEmbedded = tagsEmbedded,
    lyricsEmbedded = lyricsEmbedded,
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
    tagsEmbedded = tagsEmbedded,
    lyricsEmbedded = lyricsEmbedded,
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

internal fun Subscription.toDto(): SubscriptionDto = SubscriptionDto(
    id = id,
    sourceUrl = sourceUrl,
    displayName = displayName,
    paused = paused,
    checkIntervalMinutes = checkIntervalMinutes,
    titleFilterRegex = titleFilterRegex,
    skipMembersOnly = skipMembersOnly,
    downloadOptions = downloadOptions.toDto(),
    lastCheckedAtEpochMillis = lastCheckedAtEpochMillis,
    nextCheckAtEpochMillis = nextCheckAtEpochMillis,
    lastError = lastError?.toDto(),
    seenIds = seenIds,
)

internal fun SubscriptionDto.toDomain(): Subscription = Subscription(
    id = id,
    sourceUrl = sourceUrl,
    displayName = displayName,
    paused = paused,
    checkIntervalMinutes = checkIntervalMinutes,
    titleFilterRegex = titleFilterRegex,
    skipMembersOnly = skipMembersOnly,
    downloadOptions = downloadOptions.toDomain(),
    lastCheckedAtEpochMillis = lastCheckedAtEpochMillis,
    nextCheckAtEpochMillis = nextCheckAtEpochMillis,
    lastError = lastError?.toDomain(),
    seenIds = seenIds,
)

internal fun Preset.toDto(): PresetDto = PresetDto(id = id, name = name, options = options)

internal fun PresetDto.toDomain(): Preset = Preset(id = id, name = name, options = options)

internal fun AppSettings.toDocument(cookieFilePath: String?): SettingsDocument = SettingsDocument(
    downloadRoot = downloadRoot,
    outputTemplate = outputTemplate,
    playlistTemplate = playlistTemplate,
    channelTemplate = channelTemplate,
    chapterTemplate = chapterTemplate,
    maxConcurrentDownloads = maxConcurrentDownloads,
    clearCompletedAfterSeconds = clearCompletedAfterSeconds,
    subscriptionIntervalMinutes = subscriptionIntervalMinutes,
    theme = theme.wireName,
    clipboardAccess = clipboardAccess.wireName,
    handledClipboardUrl = handledClipboardUrl,
    cookiesConfigured = cookiesConfigured,
    cookieFilePath = cookieFilePath,
    presets = presets.map { it.toDto() },
    spotifyFallbackProviders = spotifyFallbackProviders,
)

internal fun SettingsDocument.toDomain(): AppSettings = AppSettings(
    downloadRoot = downloadRoot,
    outputTemplate = outputTemplate,
    playlistTemplate = playlistTemplate,
    channelTemplate = channelTemplate,
    chapterTemplate = chapterTemplate,
    maxConcurrentDownloads = maxConcurrentDownloads,
    clearCompletedAfterSeconds = clearCompletedAfterSeconds,
    subscriptionIntervalMinutes = subscriptionIntervalMinutes,
    theme = ThemePreference.entries.firstOrNull { it.wireName == theme } ?: ThemePreference.SYSTEM,
    clipboardAccess = ClipboardAccess.entries.firstOrNull { it.wireName == clipboardAccess }
        ?: ClipboardAccess.UNKNOWN,
    handledClipboardUrl = handledClipboardUrl,
    cookiesConfigured = cookiesConfigured,
    presets = presets.map { it.toDomain() },
    spotifyFallbackProviders = spotifyFallbackProviders,
)
