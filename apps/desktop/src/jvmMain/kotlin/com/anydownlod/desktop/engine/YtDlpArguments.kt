package com.anydownlod.desktop.engine

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.domain.CaptionPreference
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.VideoCodec
import com.anydownlod.core.domain.VideoContainerProfile
import com.anydownlod.core.validation.RelativePathValidation
import com.anydownlod.core.validation.RelativePathValidator
import java.nio.file.Path

/**
 * Builds the argv for one job from the whole [DownloadRequest]. This is the
 * only place that maps options onto yt-dlp flags; the UI never builds argv and
 * no shell string is ever created.
 *
 * Format selectors used by the reviewed D1 mapping:
 * - Auto profile/codec/best quality: no `-f`, so yt-dlp's default best applies.
 * - MP4: `ext=mp4` plus `--merge-output-format mp4`.
 * - iOS compatible: `ext=mp4` and an H.264 video filter.
 * - Codec: `vcodec^=avc1` (H.264), `vcodec~='^(hev1|hvc1)'` (HEVC),
 *   `vcodec~='^av01'` (AV1), `vcodec~='^vp0?9'` (VP9).
 * - Quality: Best uses `bv*+ba/b`, Worst uses `wv*+wa/w`, and a resolution
 *   token adds an exact `height=N` filter so an unavailable height fails with
 *   `unsupported format` instead of silently picking another size.
 */
object YtDlpArguments {
    const val PROGRESS_PREFIX = "DL|"
    const val POSTPROCESS_PREFIX = "PP|"
    const val TITLE_PREFIX = "TITLE|"
    const val FILE_PREFIX = "FILE|"
    const val ENTRY_PREFIX = "ENTRY|"

    const val DOWNLOAD_PROGRESS_TEMPLATE =
        "download:$PROGRESS_PREFIX%(progress.status)s|%(progress.downloaded_bytes)s|" +
            "%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s"
    const val POSTPROCESS_PROGRESS_TEMPLATE = "postprocess:$POSTPROCESS_PREFIX%(progress.status)s"
    const val TITLE_PRINT_TEMPLATE = "$TITLE_PREFIX%(title)s"
    const val FILE_PRINT_TEMPLATE = "$FILE_PREFIX%(filepath)s"

    // `%(filepath)s` is only final after the move postprocessor.
    const val FILE_PRINT_ARGUMENT = "after_move:$FILE_PRINT_TEMPLATE"
    const val ENTRY_PRINT_TEMPLATE = "$ENTRY_PREFIX%(url)s"

    /** The categories the reviewed MeTube baseline removes by default. */
    const val SPONSORBLOCK_CATEGORIES =
        "sponsor,intro,outro,selfpromo,preview,filler,interaction,music_offtopic"

    fun build(
        executable: String,
        request: DownloadRequest,
        outputTemplatePath: Path,
        chapterTemplatePath: Path? = null,
        ignoreErrors: Boolean = false,
        cookieFilePath: String? = null,
    ): List<String> = buildList {
        add(executable)
        add("--newline")
        add("--no-mtime")
        add("--no-simulate")
        add("--no-overwrites")
        if (ignoreErrors) add("--ignore-errors")
        if (request.options.useCookies && !cookieFilePath.isNullOrBlank()) {
            add("--cookies")
            add(cookieFilePath)
        }
        // `--print` implies quiet, which would hide the progress templates.
        add("--progress")
        add("--progress-template")
        add(DOWNLOAD_PROGRESS_TEMPLATE)
        add("--progress-template")
        add(POSTPROCESS_PROGRESS_TEMPLATE)
        add("--print")
        add(TITLE_PRINT_TEMPLATE)
        add("--print")
        add(FILE_PRINT_ARGUMENT)

        addMediaOptions(request.options, chapterTemplatePath != null)

        add("-o")
        add(outputTemplatePath.toString())
        if (chapterTemplatePath != null) {
            add("-o")
            add("chapter:$chapterTemplatePath")
        }
        add("--")
        add(request.sourceUrl)
    }

    private fun MutableList<String>.addMediaOptions(
        options: DownloadOptions,
        splitChapters: Boolean,
    ) {
        when (options.mediaType) {
            MediaType.VIDEO -> {
                videoFormatSelector(options)?.let { selector ->
                    add("-f")
                    add(selector)
                }
                when (options.videoProfile) {
                    VideoContainerProfile.MP4, VideoContainerProfile.IOS_COMPATIBLE -> {
                        add("--merge-output-format")
                        add("mp4")
                    }

                    else -> Unit
                }
                addSidecars(options)
            }

            MediaType.AUDIO -> {
                add("-x")
                options.audioContainer?.let { container ->
                    add("--audio-format")
                    add(container.wireName)
                }
                options.audioBitrate?.let { bitrate ->
                    if (options.audioContainer.isLossy()) {
                        add("--audio-quality")
                        add("${bitrate}K")
                    }
                }
                addSidecars(options)
            }

            MediaType.CAPTIONS -> {
                add("--skip-download")
                addCaptionSelection(options)
                options.captionFormat?.let { format ->
                    add("--sub-format")
                    add(format.wireName)
                }
            }

            MediaType.THUMBNAIL -> {
                add("--skip-download")
                add("--write-thumbnail")
                add("--convert-thumbnails")
                add("jpg")
            }
        }

        addClipSection(options)
        if (splitChapters) add("--split-chapters")
        if (options.sponsorBlockRemove) {
            add("--sponsorblock-remove")
            add(SPONSORBLOCK_CATEGORIES)
        }
    }

    private fun MutableList<String>.addCaptionSelection(options: DownloadOptions) {
        when (options.captionPreference) {
            CaptionPreference.AUTOMATIC -> add("--write-auto-subs")
            CaptionPreference.EITHER -> {
                add("--write-subs")
                add("--write-auto-subs")
            }

            else -> add("--write-subs")
        }
        add("--sub-langs")
        add(options.captionLanguage?.ifBlank { null } ?: "en")
    }

    private fun MutableList<String>.addSidecars(options: DownloadOptions) {
        if (options.embedSubtitles) {
            add("--write-subs")
            add("--sub-langs")
            add(options.captionLanguage?.ifBlank { null } ?: "en")
            add("--embed-subs")
        }
        if (options.writeMetadata) add("--embed-metadata")
        if (options.writeThumbnail) {
            add("--write-thumbnail")
            add("--convert-thumbnails")
            add("jpg")
        }
    }

    private fun MutableList<String>.addClipSection(options: DownloadOptions) {
        if (options.clipStart == null && options.clipEnd == null) return
        val from = sectionTimestamp(options.clipStart) ?: "0"
        val to = sectionTimestamp(options.clipEnd) ?: "inf"
        add("--download-sections")
        add("*$from-$to")
    }

    private fun videoFormatSelector(options: DownloadOptions): String? {
        val filters = mutableListOf<String>()
        when (options.videoProfile) {
            VideoContainerProfile.MP4 -> filters += "ext=mp4"
            VideoContainerProfile.IOS_COMPATIBLE -> {
                filters += "ext=mp4"
                filters += "vcodec^=avc1"
            }

            else -> Unit
        }
        when (options.videoCodec) {
            VideoCodec.H264 -> filters += "vcodec^=avc1"
            VideoCodec.HEVC -> filters += "vcodec~='^(hev1|hvc1)'"
            VideoCodec.AV1 -> filters += "vcodec~='^av01'"
            VideoCodec.VP9 -> filters += "vcodec~='^vp0?9'"
            else -> Unit
        }
        val worst = options.quality == QualityPreference.Worst
        (options.quality as? QualityPreference.Resolution)?.token?.let { height ->
            filters += "height=$height"
        }
        val distinct = filters.distinct()
        if (distinct.isEmpty() && !worst) return null
        val suffix = distinct.joinToString(separator = "") { "[$it]" }
        return if (worst) {
            "wv*$suffix+wa/w$suffix"
        } else {
            "bv*$suffix+ba/b$suffix"
        }
    }

    /** Parses seconds / `MM:SS` / `HH:MM:SS` into a yt-dlp section timestamp. */
    internal fun sectionTimestamp(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val seconds = if (text.all { it.isDigit() }) {
            text.toLongOrNull() ?: return null
        } else {
            val parts = text.split(':')
            if (parts.size !in 2..3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
            val numbers = parts.map { it.toLongOrNull() ?: return null }
            if (numbers.drop(1).any { it !in 0..59 }) return null
            when (numbers.size) {
                2 -> numbers[0] * 60 + numbers[1]
                else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
            }
        }
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remaining = seconds % 60
        return listOf(hours, minutes, remaining).joinToString(":") { it.toString().padStart(2, '0') }
    }
}

private fun AudioContainer?.isLossy(): Boolean =
    this == AudioContainer.M4A || this == AudioContainer.MP3 || this == AudioContainer.OPUS

/** Path rules that keep every final file inside the download root. */
object DownloadPaths {    fun outputTemplatePath(downloadRoot: Path, template: String): Path {
        val root = downloadRoot.toAbsolutePath().normalize()
        val resolved = root.resolve(template).normalize()
        require(resolved.startsWith(root)) { "The output template escapes the download root." }
        return resolved
    }

    fun artifactPath(downloadRoot: Path, reportedPath: String): Path {
        val root = downloadRoot.toAbsolutePath().normalize()
        val candidate = Path.of(reportedPath).let { if (it.isAbsolute) it else root.resolve(it) }
            .normalize()
            .toAbsolutePath()
        require(candidate.startsWith(root)) { "The reported file is outside the download root." }
        return candidate
    }

    /** Applies the per-download prefix to the file-name segment only. */
    fun applyFilenamePrefix(template: String, prefix: String?): String {
        val clean = prefix?.trim().orEmpty()
        if (clean.isEmpty()) return template
        val separator = template.lastIndexOfAny(charArrayOf('/', '\\'))
        return if (separator < 0) {
            clean + template
        } else {
            template.substring(0, separator + 1) + clean + template.substring(separator + 1)
        }
    }

    /** Resolves the optional relative destination folder under the root. */
    fun resolveDestination(downloadRoot: Path, folder: String?): Path {
        val root = downloadRoot.toAbsolutePath().normalize()
        if (folder.isNullOrBlank()) return root
        return when (val result = RelativePathValidator.validate(folder)) {
            is RelativePathValidation.Valid -> {
                val candidate = root.resolve(result.path).normalize().toAbsolutePath()
                require(candidate.startsWith(root)) { "The destination folder escapes the download root." }
                candidate
            }

            is RelativePathValidation.Invalid ->
                throw IllegalArgumentException(result.error.name)
        }
    }
}
