package com.anydownlod.core.domain

/** Video container profile requested from the local engine. */
enum class VideoContainerProfile(val wireName: String) {
    AUTO("auto"),
    MP4("mp4"),
    IOS_COMPATIBLE("ios"),
    ;

    companion object {
        fun fromWire(value: String): VideoContainerProfile? =
            entries.firstOrNull { it.wireName == value.lowercase() }
    }
}

/** Preferred video codec. A preference, not a guarantee. */
enum class VideoCodec(val wireName: String) {
    AUTO("auto"),
    H264("h264"),
    HEVC("hevc"),
    AV1("av1"),
    VP9("vp9"),
    ;

    companion object {
        fun fromWire(value: String): VideoCodec? =
            entries.firstOrNull { it.wireName == value.lowercase() }
    }
}

/**
 * Quality preference for a media download.
 *
 * [Best] and [Worst] map onto the engine's own ordering. [Resolution] stores
 * the token the add form chose (for example `1080`). The domain never parses
 * or builds a yt-dlp format string.
 */
sealed interface QualityPreference {
    val token: String

    data object Best : QualityPreference {
        override val token: String = "best"
    }

    data object Worst : QualityPreference {
        override val token: String = "worst"
    }

    data class Resolution(override val token: String) : QualityPreference

    companion object {
        fun fromToken(token: String): QualityPreference = when (token.lowercase()) {
            Best.token -> Best
            Worst.token -> Worst
            else -> Resolution(token)
        }
    }
}

/** Audio container for an audio-only download. */
enum class AudioContainer(val wireName: String) {
    M4A("m4a"),
    MP3("mp3"),
    OPUS("opus"),
    WAV("wav"),
    FLAC("flac"),
    ;

    companion object {
        fun fromWire(value: String): AudioContainer? =
            entries.firstOrNull { it.wireName == value.lowercase() }
    }
}

/** Subtitle format for a captions-only download or a sidecar. */
enum class CaptionFormat(val wireName: String) {
    SRT("srt"),
    TXT("txt"),
    VTT("vtt"),
    TTML("ttml"),
    ;

    companion object {
        fun fromWire(value: String): CaptionFormat? =
            entries.firstOrNull { it.wireName == value.lowercase() }
    }
}

/** Whether manual or automatic caption tracks are preferred. */
enum class CaptionPreference(val wireName: String) {
    MANUAL("manual"),
    AUTOMATIC("automatic"),
    EITHER("either"),
    ;

    companion object {
        fun fromWire(value: String): CaptionPreference? =
            entries.firstOrNull { it.wireName == value.lowercase() }
    }
}

/**
 * What a download does when the destination file already exists. [SKIP] is
 * the default: the existing file is kept and the job completes as skipped.
 * [METADATA] keeps the audio and rewrites its tags when the request carries
 * them. [FORCE] replaces the file.
 */
enum class OverwriteMode(val wireName: String) {
    SKIP("skip"),
    METADATA("metadata"),
    FORCE("force"),
    ;

    companion object {
        fun fromWire(value: String): OverwriteMode? =
            entries.firstOrNull { it.wireName == value.lowercase() }
    }
}

/**
 * The option set of one download, kept separate from the source URL so a
 * subscription can capture the options without a one-off media URL.
 *
 * Fields that do not apply to [mediaType] stay null (or at their neutral
 * default). The desktop argument mapper is the only code allowed to translate
 * these values into engine arguments.
 */
data class DownloadOptions(
    val mediaType: MediaType = MediaType.VIDEO,
    val startPolicy: StartPolicy = StartPolicy.AUTOMATIC,
    val videoProfile: VideoContainerProfile? = null,
    val videoCodec: VideoCodec = VideoCodec.AUTO,
    val quality: QualityPreference = QualityPreference.Best,
    val audioContainer: AudioContainer? = null,
    val audioBitrate: String? = null,
    val captionLanguage: String? = null,
    val captionPreference: CaptionPreference? = null,
    val captionFormat: CaptionFormat? = null,
    val embedSubtitles: Boolean = false,
    val writeMetadata: Boolean = false,
    val writeThumbnail: Boolean = false,
    val filenamePrefix: String? = null,
    /** Relative folder inside the download root; null means the root itself. */
    val destinationFolder: String? = null,
    /** 0 means no extra cap from the app. */
    val playlistItemLimit: Int = 0,
    /** What to do when the destination file already exists. */
    val overwrite: OverwriteMode = OverwriteMode.SKIP,
    val clipStart: String? = null,
    val clipEnd: String? = null,
    val splitByChapters: Boolean = false,
    val sponsorBlockRemove: Boolean = false,
    val presetIds: List<String> = emptyList(),
    val useCookies: Boolean = false,
    /**
     * Named placeholder for MeTube's free-form yt-dlp JSON field. Q-09 is
     * open, so this stays empty and nothing reads it. The desktop argument
     * mapper must never turn it into an argument.
     */
    val customYtDlpJson: String = "",
)

/**
 * One add-form submission: a validated source URL, the chosen options, and
 * the key that makes a repeated click land on the original job.
 *
 * [metadata] and [artworkUrl] are set only by the Spotify path: the engine
 * embeds the tags after the audio is written when the host toolkit can. They
 * are never logged. [parentBatchId] groups the child jobs of one Spotify
 * album, playlist, or artist expansion.
 */
data class DownloadRequest(
    val sourceUrl: String,
    val options: DownloadOptions = DownloadOptions(),
    val idempotencyKey: String = "",
    val metadata: MediaTags? = null,
    val artworkUrl: String? = null,
    val parentBatchId: String? = null,
    /**
     * Stable media ids the user selected in a multi-media preview (an X
     * status). Empty for every other source. Never logged. The engine
     * re-extracts the source at download time and resolves each id again;
     * preview media URLs are never reused.
     */
    val selectedMediaIds: List<String> = emptyList(),
    /**
     * The exact path the artifact must be written to, relative to the download
     * root. Set by the Spotify path from its output template; null keeps the
     * extractor/URL-derived name. The engine validates it against the root.
     */
    val relativePath: String? = null,
    /**
     * Timed LRC lines for a sibling `.lrc` next to the audio. Set only by the
     * Spotify path when `generate-lrc` is on and the synced provider returned
     * timed lines; null writes no LRC.
     */
    val lrcContent: String? = null,
)
