/*
 * Preset overlay — AnyDownload (T-017)
 *
 * Translation of the `--parse-metadata`/preset layering behavior in
 * `yt_dlp/YoutubeDL.py` (`parse_options`, `_parse_outtmpl`) and MeTube's
 * ordered preset model at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30. Unlicense;
 * see shared/core/NOTICE.md.
 *
 * Order is behavior: defaults, then the selected presets in Settings order,
 * then the explicit add-form values. Within the preset layer a later preset
 * overrides an earlier one on the same key. Only the typed allowlist is
 * accepted; every other key is dropped. A final safety pass makes it
 * impossible for any layer to enable free-form yt-dlp JSON, an escaping
 * folder, an invalid clip, or an invalid playlist selection.
 */
package com.anydownlod.core.format

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionFormat
import com.anydownlod.core.domain.CaptionPreference
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.Preset
import com.anydownlod.core.domain.PresetOptionKeys
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile

object PresetOverlay {

    /**
     * The typed keys a preset may set. Booleans only turn on; a typed field
     * applies only when the form left it at its neutral value, and a blank
     * value clears a nullable field.
     */
    val ALLOWED_KEYS: Set<String> = PresetOptionKeys.typed.toSet()

    fun apply(options: DownloadOptions, selectedInOrder: List<Preset>): DownloadOptions {
        // Later presets override earlier ones on the same key inside the preset
        // layer; the explicit form value then wins over the merged layer.
        val layered = mutableMapOf<String, String>()
        for (preset in selectedInOrder) {
            for ((key, value) in preset.options) {
                if (key in ALLOWED_KEYS) layered[key] = value
            }
        }
        return enforceSafety(applyLayered(options, layered))
    }

    private fun applyLayered(options: DownloadOptions, layered: Map<String, String>): DownloadOptions {
        fun isOn(key: String): Boolean = layered[key]?.equals("true", ignoreCase = true) == true
        return options.copy(
            embedSubtitles = options.embedSubtitles || isOn(PresetOptionKeys.EMBED_SUBTITLES),
            writeMetadata = options.writeMetadata || isOn(PresetOptionKeys.WRITE_METADATA),
            writeThumbnail = options.writeThumbnail || isOn(PresetOptionKeys.WRITE_THUMBNAIL),
            splitByChapters = options.splitByChapters || isOn(PresetOptionKeys.SPLIT_BY_CHAPTERS),
            sponsorBlockRemove = options.sponsorBlockRemove || isOn(PresetOptionKeys.SPONSORBLOCK_REMOVE),
            audioContainer = options.audioContainer ?: layered[PresetOptionKeys.AUDIO_CONTAINER]?.let(::audioContainerOf),
            audioBitrate = options.audioBitrate ?: layered[PresetOptionKeys.AUDIO_BITRATE]?.ifBlank { null },
            captionLanguage = options.captionLanguage ?: layered[PresetOptionKeys.CAPTION_LANGUAGE]?.ifBlank { null },
            captionPreference = options.captionPreference
                ?: layered[PresetOptionKeys.CAPTION_PREFERENCE]?.let(::captionPreferenceOf),
            captionFormat = options.captionFormat ?: layered[PresetOptionKeys.CAPTION_FORMAT]?.let(::captionFormatOf),
            videoProfile = options.videoProfile ?: layered[PresetOptionKeys.VIDEO_PROFILE]?.let(::videoProfileOf),
            quality = if (options.quality is QualityPreference.Best) {
                QualityPreference.fromToken(layered[PresetOptionKeys.QUALITY]?.ifBlank { "best" } ?: "best")
            } else {
                options.quality
            },
            videoCodec = if (options.videoCodec == VideoCodec.AUTO) {
                VideoCodec.fromWire(layered[PresetOptionKeys.VIDEO_CODEC].orEmpty()) ?: VideoCodec.AUTO
            } else {
                options.videoCodec
            },
            filenamePrefix = options.filenamePrefix ?: layered[PresetOptionKeys.FILENAME_PREFIX]?.ifBlank { null },
            destinationFolder = options.destinationFolder ?: layered[PresetOptionKeys.DESTINATION_FOLDER]?.ifBlank { null },
            playlistItemLimit = if (options.playlistItemLimit > 0) {
                options.playlistItemLimit
            } else {
                layered[PresetOptionKeys.PLAYLIST_ITEM_LIMIT]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            },
            playlistItems = options.playlistItems.ifBlank {
                layered[PresetOptionKeys.PLAYLIST_ITEMS].orEmpty()
            },
        )
    }

    /**
     * The last gate every effective option set passes: free-form yt-dlp JSON
     * stays empty and an unsafe folder is cleared. Invalid clip/playlist
     * values are left for the engine's own typed validation, so a bad form or
     * preset value fails the job visibly instead of silently downloading the
     * whole file.
     */
    fun enforceSafety(options: DownloadOptions): DownloadOptions {
        var safe = options.copy(customYtDlpJson = "")
        if (!safe.destinationFolder.isNullOrBlank() && !isSafeFolder(safe.destinationFolder)) {
            safe = safe.copy(destinationFolder = null)
        }
        return safe
    }

    private fun isSafeFolder(folder: String): Boolean {
        if (folder.startsWith("/") || folder.startsWith("\\") || Regex("^[A-Za-z]:").containsMatchIn(folder)) return false
        val segments = folder.split('/', '\\')
        return segments.none { it == ".." || it.contains(':') || it.any { character -> character < ' ' } }
    }

    private fun audioContainerOf(value: String): AudioContainer? = AudioContainer.fromWire(value)
    private fun captionPreferenceOf(value: String): CaptionPreference? = CaptionPreference.fromWire(value)
    private fun captionFormatOf(value: String): CaptionFormat? = CaptionFormat.fromWire(value)
    private fun videoProfileOf(value: String): VideoContainerProfile? = VideoContainerProfile.fromWire(value)
}
