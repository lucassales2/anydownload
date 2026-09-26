package com.anydownlod.core

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.MediaFormat

/**
 * What an extracted [InfoDict] can satisfy as one download: the single-file
 * (progressive) video heights that exist, the video-only heights a merge
 * could pair with audio, whether any audio-only stream exists, the audio
 * containers that exist natively, and how many formats were dropped for lack
 * of a JavaScript runtime. The Edit panel only enables what is present and
 * what the host toolkit can do.
 */
data class FormatChoices(
    /** Single-file video heights, best first. Empty when none exist. */
    val videoHeights: List<Int> = emptyList(),
    /** Audio containers the extracted formats already carry. */
    val audioContainers: Set<AudioContainer> = emptySet(),
    /** Formats dropped because they need the JavaScript runtime. */
    val formatsNeedingJs: Int = 0,
    /** Video-only heights a merge could pair with an audio-only stream. */
    val mergeableVideoHeights: List<Int> = emptyList(),
    /** True when the source has at least one audio-only stream. */
    val hasAudioOnly: Boolean = false,
) {
    val hasSingleFileVideo: Boolean get() = videoHeights.isNotEmpty()
    val bestVideoHeight: Int? get() = videoHeights.firstOrNull()

    /** True when this source has both sides a merge needs. */
    val hasSplitStreams: Boolean get() = hasAudioOnly && mergeableVideoHeights.isNotEmpty()

    /** The audio container to preselect: the best present native one. */
    val preferredAudioContainer: AudioContainer?
        get() = AudioContainer.entries.firstOrNull { it in audioContainers }

    companion object {
        fun from(info: InfoDict): FormatChoices {
            // A multi-media source (an X status) keeps its formats on each
            // InfoMedia; the panel shows what the listed videos can satisfy.
            val formats = info.formats + info.media.flatMap { it.formats }
            val heights = formats.asSequence()
                .filter { format ->
                    format.vcodec != MediaFormat.CODEC_NONE && format.acodec != MediaFormat.CODEC_NONE
                }
                .mapNotNull { format -> format.height?.toInt()?.takeIf { it > 0 } }
                .distinct()
                .sortedDescending()
                .toList()
            val mergeableHeights = formats.asSequence()
                .filter { format ->
                    format.vcodec != MediaFormat.CODEC_NONE && format.acodec == MediaFormat.CODEC_NONE
                }
                .mapNotNull { format -> format.height?.toInt()?.takeIf { it > 0 } }
                .distinct()
                .sortedDescending()
                .toList()
            val hasAudioOnly = formats.any { format ->
                format.vcodec == MediaFormat.CODEC_NONE && format.acodec != null &&
                    format.acodec != MediaFormat.CODEC_NONE
            }
            val containers = formats.mapNotNull(::containerOf).toSet()
            return FormatChoices(
                videoHeights = heights,
                audioContainers = containers,
                formatsNeedingJs = info.formatsNeedingJs,
                mergeableVideoHeights = mergeableHeights,
                hasAudioOnly = hasAudioOnly,
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
