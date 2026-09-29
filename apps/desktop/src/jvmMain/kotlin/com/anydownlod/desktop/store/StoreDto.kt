package com.anydownlod.desktop.store

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.AppSettingsDefaults
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.ClipboardAccess
import com.anydownlod.core.domain.CaptionPreference
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.JobError
import com.anydownlod.core.domain.JobErrorCode
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
internal data class ErrorDto(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
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
