/*
 * Extractor core — AnyDownload
 *
 * Translation of the model named by yt-dlp's `yt_dlp/extractor/common.py` at
 * the upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-24.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. Only the fields D4's extractors fill
 * are listed; `common.py` itself is not vendored. See shared/core/NOTICE.md
 * and port/manifest.json.
 *
 * Upstream contract translated: `_real_extract` returns an info dict with a
 * `formats` list; format fields keep their upstream names in Kotlin case.
 * Fields the port does not fill stay null, never invented.
 */
package com.anydownlod.core.extract

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** One downloadable stream, mirroring the upstream format dict. */
@Serializable
data class MediaFormat(
    val formatId: String? = null,
    val url: String? = null,
    val ext: String? = null,
    val protocol: String? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val width: Long? = null,
    val height: Long? = null,
    val fps: Double? = null,
    val tbr: Double? = null,
    val abr: Double? = null,
    val vbr: Double? = null,
    val asr: Long? = null,
    val filesize: Long? = null,
    val filesizeApprox: Long? = null,
    /** Upstream `audio_channels`; used by the format sorter. */
    val audioChannels: Long? = null,
    /** Upstream `dynamic_range` (`sdr`, `hdr10`, `dv`, ...); used by the sorter. */
    val dynamicRange: String? = null,
    /** Upstream `language_preference`; used by the sorter. */
    val languagePreference: Double? = null,
    val container: String? = null,
    val language: String? = null,
    val quality: String? = null,
    /** Upstream `preference`: the extractor's own preference for this format. */
    val preference: Int? = null,
    val sourcePreference: Int? = null,
    val formatNote: String? = null,
    /** Headers the format's URL needs; values come from the extractor only. */
    val httpHeaders: Map<String, String>? = null,
    val downloaderOptions: DownloaderOptions? = null,
    val hasDrm: Boolean? = null,
    val manifestUrl: String? = null,
    val fragmentBaseUrl: String? = null,
    /** Parsed manifest fragments (T-073); null for plain HTTP formats. */
    val fragments: List<MediaFragment>? = null,
) {
    /** Upstream `is_audio_only`: no video stream. */
    val isAudioOnly: Boolean
        get() = vcodec == CODEC_NONE && !acodec.isNullOrBlank() && acodec != CODEC_NONE

    /** Upstream `is_video_only`: no audio stream. */
    val isVideoOnly: Boolean
        get() = acodec == CODEC_NONE && !vcodec.isNullOrBlank() && vcodec != CODEC_NONE

    companion object {
        const val CODEC_NONE = "none"
    }
}

/** One fragment of an HLS/DASH manifest (upstream `fragments` entry). */
@Serializable
data class MediaFragment(
    val url: String,
    val rangeStart: Long? = null,
    val rangeEnd: Long? = null,
    /** HLS media sequence number, used to derive the AES-128 IV. */
    val sequence: Long? = null,
)

/** The `downloader_options` sub-dict fields the port understands. */
@Serializable
data class DownloaderOptions(
    val httpChunkSize: Long? = null,
)

/** Upstream thumbnail dict. */
@Serializable
data class Thumbnail(
    val url: String,
    val id: String? = null,
    val width: Long? = null,
    val height: Long? = null,
    val preference: Int? = null,
)

/**
 * Port-only (not an upstream field): one selectable media item of a source
 * that carries several videos (an X status). Each item has a stable
 * [mediaId], its own metadata, and its own formats. `formats` on the
 * [InfoDict] stays empty for such a source; the engine resolves each selected
 * item instead. Empty for every other extractor.
 */
@Serializable
data class InfoMedia(
    val mediaId: String,
    val title: String? = null,
    val duration: Double? = null,
    val thumbnails: List<Thumbnail> = emptyList(),
    val formats: List<MediaFormat> = emptyList(),
)

/**
 * Port-only (not an upstream field): one entry of a playlist or tab page.
 * [failure] is set when the extractor already knows the entry is unavailable;
 * [url] then still identifies the entry when the extractor has one. Empty
 * [InfoDict.entries] means the result is not a playlist.
 */
@Serializable
data class InfoEntry(
    val id: String? = null,
    val title: String? = null,
    val url: String? = null,
    /** In-process only; a failure is not a wire field. */
    @Transient val failure: ExtractionError? = null,
)

/** One subtitle-track format URL on the info dict (T-124). Writing files is D13. */
@Serializable
data class SubtitleFormat(
    val ext: String,
    val url: String,
)

/**
 * One subtitle or caption track on the info dict. Upstream models the same
 * data as `subtitles[lang] = [{ext, url, ...}]`; the Kotlin shape keeps the
 * language once and lists the format URLs under it. Writing SRT/VTT/TTML/TXT
 * files is T-015 (D13); extraction only records the tracks here.
 */
@Serializable
data class SubtitleTrack(
    val language: String,
    val name: String? = null,
    /** True for an automatic/ASR track (upstream `kind == 'asr'`). */
    val automatic: Boolean = false,
    /** True when the track URL needs a PO token that is not configured (E-13). */
    val needsPoToken: Boolean = false,
    val formats: List<SubtitleFormat> = emptyList(),
)

/** One chapter marker on the info dict (T-124). Splitting files is T-016. */
@Serializable
data class Chapter(
    /** Seconds from the start. */
    val startTime: Double? = null,
    /** Seconds from the start; null means the next chapter or the end. */
    val endTime: Double? = null,
    val title: String? = null,
)

/**
 * Upstream info dict. Fields not filled stay null or empty; the extractor key
 * and display name come from the owning [InfoExtractor].
 */
@Serializable
data class InfoDict(
    val id: String? = null,
    val title: String? = null,
    val ext: String? = null,
    val url: String? = null,
    val formats: List<MediaFormat> = emptyList(),
    /**
     * Port-only: selectable media of one page, grouped by stable media id.
     * Non-empty only for a source that carries several videos; [formats]
     * then stays empty and a download names the media it wants.
     */
    val media: List<InfoMedia> = emptyList(),
    /**
     * Port-only: playlist/tab entries. Non-empty means the engine expands one
     * bounded child job per entry instead of downloading media for this job.
     */
    val entries: List<InfoEntry> = emptyList(),
    val thumbnails: List<Thumbnail> = emptyList(),
    val duration: Double? = null,
    val uploader: String? = null,
    val channel: String? = null,
    val channelId: String? = null,
    val uploadDate: String? = null,
    val viewCount: Long? = null,
    val description: String? = null,
    val webpageUrl: String? = null,
    val extractor: String? = null,
    val extractorKey: String? = null,
    val ageLimit: Int? = null,
    val isLive: Boolean? = null,
    val availability: String? = null,
    /** Manual caption tracks (upstream `subtitles`). */
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** Automatic/ASR caption tracks (upstream `automatic_captions`). */
    val automaticCaptions: List<SubtitleTrack> = emptyList(),
    /** Chapter markers when the player response carries them. */
    val chapters: List<Chapter> = emptyList(),
    /**
     * Port-only (not an upstream field): how many formats were dropped
     * because they need the JavaScript runtime (signature cipher or an `n`
     * transform). The engine reports this as an honest limitation.
     */
    val formatsNeedingJs: Int = 0,
    /**
     * Port-only: how many formats were dropped because the client's PO-token
     * policy requires a GVS token and no provider supplied one (E-13).
     */
    val formatsNeedingPoToken: Int = 0,
    /**
     * Port-only (not an upstream field): the `url_result` delegation target.
     * When set, [ExtractorRegistry] re-dispatches the URL once per hop
     * (bounded) so a thin extractor can hand off to the site extractor that
     * owns the resolved URL, exactly like upstream `self.url_result(...)`.
     */
    val redirectUrl: String? = null,
)
