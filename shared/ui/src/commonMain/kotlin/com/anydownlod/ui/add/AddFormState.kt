package com.anydownlod.ui.add

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.CaptionPreference
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile
import com.anydownlod.core.validation.RelativePathValidation
import com.anydownlod.core.validation.RelativePathValidator

/** One rejected line of a pasted batch. */
data class AddLineError(val line: String, val reason: String)

/** Short status line shown under the form actions. */
data class AddStatus(val message: String, val isError: Boolean = false)

/** Lossy containers accept a bitrate choice; lossless ones do not. */
val AudioContainer.isLossy: Boolean
    get() = this == AudioContainer.M4A || this == AudioContainer.MP3 || this == AudioContainer.OPUS

/**
 * Every control of the MeTube add form as plain state. The composable renders
 * it; [AddFormPresenter] mutates it and turns it into a [DownloadOptions].
 */
data class AddFormState(
    val urlText: String = "",
    val mediaType: MediaType = MediaType.VIDEO,
    val startPolicy: StartPolicy = StartPolicy.AUTOMATIC,
    val filenamePrefix: String = "",
    val destinationFolder: String = "",
    val videoProfile: VideoContainerProfile = VideoContainerProfile.AUTO,
    val videoCodec: VideoCodec = VideoCodec.AUTO,
    val quality: QualityPreference = QualityPreference.Best,
    val audioContainer: AudioContainer = AudioContainer.M4A,
    val audioBitrate: String = "",
    val captionLanguage: String = "",
    val captionPreference: CaptionPreference = CaptionPreference.EITHER,
    val captionFormat: CaptionFormat = CaptionFormat.SRT,
    val advancedExpanded: Boolean = false,
    val playlistItemLimit: String = "0",
    val clipStart: String = "",
    val clipEnd: String = "",
    val splitByChapters: Boolean = false,
    val sponsorBlockRemove: Boolean = false,
    val embedSubtitles: Boolean = false,
    val writeMetadata: Boolean = false,
    val writeThumbnail: Boolean = false,
    val selectedPresetIds: List<String> = emptyList(),
    val useCookies: Boolean = false,
    val lineErrors: List<AddLineError> = emptyList(),
) {
    val hasInput: Boolean get() = urlText.isNotBlank()

    val nonBlankLineCount: Int get() = urlText.lineSequence().count { it.isNotBlank() }

    val canSubscribe: Boolean get() = hasInput && nonBlankLineCount <= 1

    /** Inline destination-folder error, or null when the value is acceptable. */
    val destinationError: String?
        get() = when (val result = RelativePathValidator.validate(destinationFolder)) {
            is RelativePathValidation.Valid -> null
            is RelativePathValidation.Invalid -> result.reason
        }

    /** Inline playlist-limit error, or null. Blank is treated as 0. */
    val playlistLimitError: String?
        get() {
            val text = playlistItemLimit.trim()
            if (text.isEmpty()) return null
            val value = text.toIntOrNull()
            return if (value == null || value < 0) "Playlist limit must be a whole number, 0 or more." else null
        }

    /** True when a clip range (or start/end-only range) is malformed. */
    val clipError: String?
        get() {
            val start = clipStart.trim()
            val end = clipEnd.trim()
            if (start.isEmpty() && end.isEmpty()) return null
            val startSeconds = if (start.isEmpty()) null else parseClipTimestamp(start)
            val endSeconds = if (end.isEmpty()) null else parseClipTimestamp(end)
            if (start.isNotEmpty() && startSeconds == null) return "Clip start must be seconds or HH:MM:SS."
            if (end.isNotEmpty() && endSeconds == null) return "Clip end must be seconds or HH:MM:SS."
            if (startSeconds != null && endSeconds != null && endSeconds <= startSeconds) {
                return "Clip end must be after clip start."
            }
            return null
        }
}

/**
 * Covers every control without leaking a value that does not apply to
 * [AddFormState.mediaType]: video choices are null/auto for audio, captions,
 * or thumbnail requests, and so on.
 */
fun AddFormState.toDownloadOptions(settings: AppSettings): DownloadOptions {
    val appliesVideo = mediaType == MediaType.VIDEO
    val appliesAudio = mediaType == MediaType.AUDIO
    val appliesCaptions = mediaType == MediaType.CAPTIONS
    val allowsSidecars = mediaType != MediaType.THUMBNAIL

    return DownloadOptions(
        mediaType = mediaType,
        startPolicy = startPolicy,
        videoProfile = if (appliesVideo) videoProfile else null,
        videoCodec = if (appliesVideo) videoCodec else VideoCodec.AUTO,
        quality = if (appliesVideo) quality else QualityPreference.Best,
        audioContainer = if (appliesAudio) audioContainer else null,
        audioBitrate = if (appliesAudio && audioContainer.isLossy) {
            audioBitrate.trim().ifBlank { null }
        } else {
            null
        },
        captionLanguage = if (appliesCaptions) captionLanguage.trim().ifBlank { null } else null,
        captionPreference = if (appliesCaptions) captionPreference else null,
        captionFormat = if (appliesCaptions) captionFormat else null,
        embedSubtitles = (appliesVideo || appliesAudio) && embedSubtitles,
        writeMetadata = allowsSidecars && writeMetadata,
        writeThumbnail = allowsSidecars && writeThumbnail,
        filenamePrefix = filenamePrefix.trim().ifBlank { null },
        destinationFolder = destinationFolder.trim().ifBlank { null },
        playlistItemLimit = playlistItemLimit.trim().ifBlank { "0" }.toIntOrNull() ?: 0,
        clipStart = clipStart.trim().ifBlank { null },
        clipEnd = clipEnd.trim().ifBlank { null },
        splitByChapters = splitByChapters,
        sponsorBlockRemove = sponsorBlockRemove,
        // The add form shows presets in the Settings order, not click order.
        presetIds = settings.presets.map { it.id }.filter { it in selectedPresetIds },
        useCookies = useCookies && settings.cookiesConfigured,
        // Q-09 is still open; the disabled form field never reaches the request.
        customYtDlpJson = "",
    )
}

/**
 * Parses `HH:MM:SS`, `MM:SS`, or plain seconds. Returns null for anything
 * else, including out-of-range minutes/seconds.
 */
internal fun parseClipTimestamp(raw: String): Long? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    if (text.all { it.isDigit() }) return text.toLongOrNull()

    val parts = text.split(':')
    if (parts.size !in 2..3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
    val numbers = parts.map { it.toLong() }
    if (numbers.drop(1).any { it !in 0..59 }) return null
    return when (numbers.size) {
        2 -> numbers[0] * 60 + numbers[1]
        else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
    }
}
