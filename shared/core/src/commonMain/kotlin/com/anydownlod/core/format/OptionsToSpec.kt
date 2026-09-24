/*
 * Typed options to format specification — AnyDownload
 *
 * The app's `DownloadOptions` compile here into a `FormatSpec`; the domain
 * never stores a spec string, and in D4 the compiler never emits a merge.
 * The mapping follows the ADR-008 decision (best single file; the media
 * toolkit is not built) rather than a single upstream function.
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

    /** Captions and thumbnails belong to a later phase. */
    data object NotInPhase : CompiledSpec
}

object OptionsToSpec {

    fun compile(options: DownloadOptions): CompiledSpec = when (options.mediaType) {
        MediaType.VIDEO -> compileVideo(options)
        MediaType.AUDIO -> compileAudio(options)
        MediaType.CAPTIONS,
        MediaType.THUMBNAIL,
        -> CompiledSpec.NotInPhase
    }

    private fun compileVideo(options: DownloadOptions): CompiledSpec {
        val quality = when (val preference = options.quality) {
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
        val specText = if (profileFilters.isEmpty()) quality else "$quality$profileFilters/$quality"
        val sort = when (options.videoCodec) {
            VideoCodec.AUTO -> emptyList()
            VideoCodec.H264 -> listOf("vcodec:avc")
            VideoCodec.HEVC -> listOf("vcodec:h265")
            VideoCodec.AV1 -> listOf("vcodec:av01")
            VideoCodec.VP9 -> listOf("vcodec:vp9")
        }
        return CompiledSpec.SingleFile(FormatSpec.parse(specText), specText, sort)
    }

    private fun compileAudio(options: DownloadOptions): CompiledSpec = when (val container = options.audioContainer) {
        null -> CompiledSpec.SingleFile(FormatSpec.parse("ba"), "ba")
        AudioContainer.M4A -> CompiledSpec.SingleFile(FormatSpec.parse("ba[ext=m4a]"), "ba[ext=m4a]")
        AudioContainer.OPUS -> CompiledSpec.SingleFile(FormatSpec.parse("ba[acodec=opus]"), "ba[acodec=opus]")
        AudioContainer.MP3 -> CompiledSpec.NeedsToolkit(
            container = container.wireName,
            message = "MP3 needs the media toolkit, which is not built yet.",
        )

        AudioContainer.WAV -> CompiledSpec.NeedsToolkit(
            container = container.wireName,
            message = "WAV needs the media toolkit, which is not built yet.",
        )

        AudioContainer.FLAC -> CompiledSpec.NeedsToolkit(
            container = container.wireName,
            message = "FLAC needs the media toolkit, which is not built yet.",
        )
    }
}
