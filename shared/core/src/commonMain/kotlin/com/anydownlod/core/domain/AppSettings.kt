package com.anydownlod.core.domain

/** Light/dark preference. [SYSTEM] follows the operating system. */
enum class ThemePreference(val wireName: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;
}

/**
 * A named bundle of allowlisted engine options the add form can select.
 *
 * [options] is an insertion-ordered map of stable app tokens, never a command
 * line. Layering and allowlist enforcement live in the desktop argument mapper.
 */
data class Preset(
    val id: String,
    val name: String,
    val options: Map<String, String> = emptyMap(),
)

/** Reviewed MeTube defaults for a fresh local install. */
object AppSettingsDefaults {
    const val OUTPUT_TEMPLATE = "%(title)s.%(ext)s"
    const val PLAYLIST_TEMPLATE = "%(playlist_title)s/%(title)s.%(ext)s"
    const val CHANNEL_TEMPLATE = "%(channel)s/%(title)s.%(ext)s"
    const val CHAPTER_TEMPLATE = "%(title)s - %(section_number)02d - %(section_title)s.%(ext)s"
    const val MAX_CONCURRENT_DOWNLOADS = 3
    const val CLEAR_COMPLETED_AFTER_SECONDS = 0L
    const val SUBSCRIPTION_INTERVAL_MINUTES = 60
}

/** The preset option keys the app understands. Anything else is dropped. */
object PresetOptionKeys {
    const val EMBED_SUBTITLES = "embedSubtitles"
    const val WRITE_METADATA = "writeMetadata"
    const val WRITE_THUMBNAIL = "writeThumbnail"
    const val SPLIT_BY_CHAPTERS = "splitByChapters"
    const val SPONSORBLOCK_REMOVE = "sponsorBlockRemove"

    val all: List<String> = listOf(
        EMBED_SUBTITLES,
        WRITE_METADATA,
        WRITE_THUMBNAIL,
        SPLIT_BY_CHAPTERS,
        SPONSORBLOCK_REMOVE,
    )

    fun label(key: String): String = when (key) {
        EMBED_SUBTITLES -> "Embed subtitles"
        WRITE_METADATA -> "Write metadata"
        WRITE_THUMBNAIL -> "Write thumbnail sidecar"
        SPLIT_BY_CHAPTERS -> "Split by chapters"
        SPONSORBLOCK_REMOVE -> "Remove SponsorBlock segments"
        else -> key
    }
}

/**
 * Local app preferences, owned by the on-device store.
 *
 * The cookie section is only a status flag: there is intentionally no field
 * that can hold cookie contents or a source path. The desktop store keeps the
 * frozen file path outside this document.
 */
data class AppSettings(
    /** Absolute folder on desktop; blank until the user picks one. */
    val downloadRoot: String = "",
    val outputTemplate: String = AppSettingsDefaults.OUTPUT_TEMPLATE,
    val playlistTemplate: String = AppSettingsDefaults.PLAYLIST_TEMPLATE,
    val channelTemplate: String = AppSettingsDefaults.CHANNEL_TEMPLATE,
    val chapterTemplate: String = AppSettingsDefaults.CHAPTER_TEMPLATE,
    val maxConcurrentDownloads: Int = AppSettingsDefaults.MAX_CONCURRENT_DOWNLOADS,
    /** 0 disables clearing. */
    val clearCompletedAfterSeconds: Long = AppSettingsDefaults.CLEAR_COMPLETED_AFTER_SECONDS,
    val subscriptionIntervalMinutes: Int = AppSettingsDefaults.SUBSCRIPTION_INTERVAL_MINUTES,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val cookiesConfigured: Boolean = false,
    val presets: List<Preset> = emptyList(),
)

/** Result of looking for one external tool on the local machine. */
data class ToolAvailability(
    val available: Boolean = false,
    val version: String? = null,
)

/**
 * Presence of the external tools the desktop engine needs. The in-memory fake
 * reports both missing; the desktop host replaces it with a PATH probe.
 */
data class ToolStatus(
    val ytDlp: ToolAvailability = ToolAvailability(),
    val ffmpeg: ToolAvailability = ToolAvailability(),
)
