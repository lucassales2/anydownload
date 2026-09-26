/*
 * Typed options to format specification — AnyDownload
 *
 * The app's `DownloadOptions` compile here into a `FormatSpec`; the domain
 * never stores a spec string. When the host can merge, video options compile
 * to one `+` merge with the D4 single-file spec as the `/` fallback; when it
 * cannot, they keep the D4 single-file spec. The compiler never emits more
 * than one `+`, and never a merge for audio-only options. The mapping follows
 * the ADR-009 decision rather than a single upstream function.
 */
package com.anydownlod.core.format

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile

/** The compiler result; [NeedsToolkit] and [NotInPhase] are honest dead ends. */
sealed interface CompiledSpec {
    /** A single-file selection plus any codec sort preference. */
    data class SingleFile(
        val spec: FormatSpec,
        val specText: String,
        val sort: List<String> = emptyList(),
    ) : CompiledSpec

    /** The selection needs the media toolkit (transcode/remux); not built in D4. */
    data class NeedsToolkit(
        val container: String,
        val message: String,
    ) : CompiledSpec

    /**
     * The host toolkit extracts the best audio-only stream and writes it as
     * [container]. The engine downloads [spec] to a temp and calls
     * `MediaToolkit.extractAudio`.
     */
    data class ExtractAudio(
        val spec: FormatSpec,
        val specText: String,
        val container: AudioContainer,
    ) : CompiledSpec

    /** Captions and thumbnails belong to a later phase. */
    data object NotInPhase : CompiledSpec
}

object OptionsToSpec {

    /**
     * Compiles [options] to a spec. [canMerge] is the host toolkit's
     * `canMerge`: true compiles one `bv*+ba/b`-style merge, false keeps the
     * D4 single-file spec. [audioContainers] is the host toolkit's audio set:
     * MP3, WAV, and FLAC compile to an extraction only when the host lists
     * them.
     */
    fun compile(
        options: DownloadOptions,
        canMerge: Boolean = false,
        audioContainers: Set<AudioContainer> = emptySet(),
    ): CompiledSpec = when (options.mediaType) {
        MediaType.VIDEO -> compileVideo(options, canMerge)
        MediaType.AUDIO -> compileAudio(options, audioContainers)
        MediaType.CAPTIONS,
        MediaType.THUMBNAIL,
        -> CompiledSpec.NotInPhase
    }

    private fun compileVideo(options: DownloadOptions, canMerge: Boolean): CompiledSpec {
        val preference = options.quality
        val quality = when (preference) {
            QualityPreference.Best -> "b"
            QualityPreference.Worst -> "w"
            is QualityPreference.Resolution -> preference.token.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let { "b[height<=$it]" }
                ?: "b"
        }
        val profileFilters = when (options.videoProfile) {
            VideoContainerProfile.MP4,
            VideoContainerProfile.IOS_COMPATIBLE,
            -> "[ext=mp4][vcodec^=avc1]"

            VideoContainerProfile.AUTO,
            null,
            -> ""
        }
        val singleFileText = if (profileFilters.isEmpty()) quality else "$quality$profileFilters/$quality"
        val specText = if (canMerge) mergeSpecText(preference, profileFilters, singleFileText) else singleFileText
        val sort = when (options.videoCodec) {
            VideoCodec.AUTO -> emptyList()
            VideoCodec.H264 -> listOf("vcodec:avc")
            VideoCodec.HEVC -> listOf("vcodec:h265")
            VideoCodec.AV1 -> listOf("vcodec:av01")
            VideoCodec.VP9 -> listOf("vcodec:vp9")
        }
        return CompiledSpec.SingleFile(FormatSpec.parse(specText), specText, sort)
    }

    /**
     * One `+` merge with [singleFileText] as the `/` fallback. The profile and
     * height filters stay on the video side; the audio side is the best (or
     * worst) audio-only stream.
     */
    private fun mergeSpecText(
        preference: QualityPreference,
        profileFilters: String,
        singleFileText: String,
    ): String {
        val heightFilter = (preference as? QualityPreference.Resolution)
            ?.token?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.let { "[height<=$it]" }
            .orEmpty()
        return when (preference) {
            QualityPreference.Worst -> "wv*$heightFilter$profileFilters+wa/$singleFileText"
            else -> "bv*$heightFilter$profileFilters+ba/$singleFileText"
        }
    }

    private fun compileAudio(
        options: DownloadOptions,
        audioContainers: Set<AudioContainer>,
    ): CompiledSpec = when (val container = options.audioContainer) {
        null -> CompiledSpec.SingleFile(FormatSpec.parse("ba"), "ba")
        AudioContainer.M4A -> CompiledSpec.SingleFile(FormatSpec.parse("ba[ext=m4a]"), "ba[ext=m4a]")
        AudioContainer.OPUS -> CompiledSpec.SingleFile(FormatSpec.parse("ba[acodec=opus]"), "ba[acodec=opus]")
        AudioContainer.MP3, AudioContainer.WAV, AudioContainer.FLAC ->
            if (container in audioContainers) {
                CompiledSpec.ExtractAudio(
                    spec = FormatSpec.parse("ba"),
                    specText = "ba",
                    container = container,
                )
            } else {
                CompiledSpec.NeedsToolkit(
                    container = container.wireName,
                    message = "This host cannot write ${container.wireName.uppercase()} audio.",
                )
            }
    }
}
