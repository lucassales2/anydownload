package com.anydownlod.core

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.MediaFormat

/**
 * What an extracted [InfoDict] can satisfy as one D4 download: the single-file
 * (progressive) video heights that exist, the audio containers that exist
 * natively, and how many formats were dropped for lack of a JavaScript
 * runtime. The Edit panel only enables what is present.
 */
data class FormatChoices(
    /** Single-file video heights, best first. Empty when none exist. */
    val videoHeights: List<Int> = emptyList(),
    /** Audio containers the extracted formats already carry. */
    val audioContainers: Set<AudioContainer> = emptySet(),
    /** Formats dropped because they need the JavaScript runtime. */
    val formatsNeedingJs: Int = 0,
) {
    val hasSingleFileVideo: Boolean get() = videoHeights.isNotEmpty()
    val bestVideoHeight: Int? get() = videoHeights.firstOrNull()

    /** The audio container to preselect: the best present native one. */
    val preferredAudioContainer: AudioContainer?
        get() = AudioContainer.entries.firstOrNull { it in audioContainers }

    companion object {
        fun from(info: InfoDict): FormatChoices {
            val heights = info.formats.asSequence()
                .filter { format ->
                    format.vcodec != MediaFormat.CODEC_NONE && format.acodec != MediaFormat.CODEC_NONE
                }
                .mapNotNull { format -> format.height?.toInt()?.takeIf { it > 0 } }
                .distinct()
                .sortedDescending()
                .toList()
            val containers = info.formats.mapNotNull(::containerOf).toSet()
            return FormatChoices(
                videoHeights = heights,
                audioContainers = containers,
                formatsNeedingJs = info.formatsNeedingJs,
            )
        }

        /** Native container of an audio-only stream, or null when unrecognized. */
        private fun containerOf(format: MediaFormat): AudioContainer? {
            val audioOnly = format.vcodec == MediaFormat.CODEC_NONE &&
                format.acodec != null && format.acodec != MediaFormat.CODEC_NONE
            if (!audioOnly) return null
            val codec = format.acodec?.lowercase().orEmpty()
            val ext = format.ext?.lowercase().orEmpty()
            return when {
                ext == "mp3" -> AudioContainer.MP3
                ext == "wav" -> AudioContainer.WAV
                ext == "flac" -> AudioContainer.FLAC
                codec.startsWith("opus") || ext == "opus" || ext == "ogg" || ext == "webm" -> AudioContainer.OPUS
                codec.startsWith("mp4a") || codec.startsWith("aac") ||
                    ext == "m4a" || ext == "mp4" || ext == "aac" -> AudioContainer.M4A

                else -> null
            }
        }
    }
}
