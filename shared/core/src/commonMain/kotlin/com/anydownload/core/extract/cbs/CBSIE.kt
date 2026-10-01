/*
 * CBS extractors — AnyDownload
 *
 * Kotlin translation of the public API/page subset of `cbs.py` from
 * `yt_dlp/extractor/cbs.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `cbs.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `CBSIE` uses the public `videoPlayerService.php` XML, the
 * ThePlatform metadata and SMIL calls (through `ThePlatformBaseIE` /
 * `SmilManifest`), the asset-type map, the SMIL caption params, and the
 * DRM/geo typed failures; `ParamountPressExpressIE` translates the YouTube
 * and Brightcove redirects. Upstream marks `CBSIE` `_WORKING = False`; the
 * Brightcove smuggle token is not carried (the port's Brightcove extractor
 * does not read it), series fills `channel`, and season/episode fields are
 * dropped. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.cbs

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.extract.theplatform.SmilManifest
import com.anydownload.core.extract.theplatform.ThePlatformBaseIE
import com.anydownload.core.extract.theplatform.ThePlatformMetadata

private const val CBS_API = "https://can.cbs.com/thunder/player/videoPlayerService.php"
private const val THEPLATFORM_PREFIX = "https://link.theplatform.com/s/"

/** Upstream `CBSBaseIE`: the ThePlatform video assembly. */
abstract class CBSBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : ThePlatformBaseIE(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_parse_smil_subtitles`: the three caption param names. */
    protected fun parseSmilSubtitles(xml: String, language: String = "en"): List<SubtitleTrack> {
        val formats = mutableListOf<SubtitleFormat>()
        for ((name, ext) in listOf(
            "sMPTE-TTCCURL" to "tt",
            "ClosedCaptionURL" to "ttml",
            "webVTTCaptionURL" to "vtt",
        )) {
            val url = smilParamValue(xml, name) ?: continue
            formats += SubtitleFormat(ext = ext, url = url)
        }
        return if (formats.isEmpty()) emptyList() else listOf(SubtitleTrack(language = language, formats = formats))
    }

    /** Upstream `_extract_common_video_info`: the SMIL walk per asset type. */
    protected suspend fun extractCommonVideoInfo(
        contentId: String,
        assetTypes: Map<String, Map<String, String>>,
        mpxAcc: String,
    ): Triple<ThePlatformMetadata, List<MediaFormat>, List<SubtitleTrack>> {
        val tpPath = "dJ5BDC/media/guid/$mpxAcc/$contentId"
        val tpReleaseUrl = THEPLATFORM_PREFIX + tpPath
        val metadata = parseTheplatformMetadata(downloadTheplatformMetadata(tpPath, contentId))
        val formats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()
        var lastError: ExtractionError? = null
        for ((assetType, query) in assetTypes) {
            var xml: String? = null
            try {
                xml = downloadSmil(tpReleaseUrl, query)
            } catch (error: ExtractionError) {
                lastError = error
                if (assetType != "fallback") continue
                val retryQuery = query.toMutableMap()
                retryQuery["formats"] = ""
                retryQuery.remove("assetTypes")
                try {
                    xml = downloadSmil(tpReleaseUrl, retryQuery)
                } catch (retryError: ExtractionError) {
                    lastError = retryError
                    continue
                }
            }
            xml ?: continue
            when (val exception = SmilManifest.exceptionValue(xml)) {
                "GeoLocationBlocked" -> throw ExtractionError.GeoRestricted(listOf("US"))
                null -> Unit
                else -> {
                    lastError = ExtractionError.Unavailable(
                        SmilManifest.refAbstract(xml) ?: "The ThePlatform manifest failed.",
                    )
                    continue
                }
            }
            for (video in SmilManifest.videos(xml)) {
                formats += MediaFormat(
                    formatId = "$assetType-${video.height ?: formats.size}",
                    url = video.src,
                    ext = ExtractorUtils.determineExt(video.src),
                    width = video.width,
                    height = video.height,
                )
            }
            subtitles += parseSmilSubtitles(xml)
        }
        if (lastError != null && formats.isEmpty()) {
            throw ExtractionError.NoFormats(lastError.message ?: "No CBS format was found.")
        }
        return Triple(metadata, formats, subtitles)
    }

    private fun smilParamValue(xml: String, name: String): String? {
        val quoted = Regex.escape(name)
        Regex("<param[^>]*\\bname\\s*=\\s*[\"']$quoted[\"'][^>]*\\bvalue\\s*=\\s*[\"']([^\"']+)")
            .find(xml)?.let { return it.groupValues[1] }
        return Regex("<param[^>]*\\bvalue\\s*=\\s*[\"']([^\"']+)[\"'][^>]*\\bname\\s*=\\s*[\"']$quoted[\"']")
            .find(xml)?.groupValues?.get(1)
    }
}

/** Upstream `CBSIE`: a cbs.com or colbertlateshow.com video. */
class CBSIE(
    http: ExtractorHttp,
) : CBSBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val contentId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return extractVideoInfo(url, contentId, site = "cbs", mpxAcc = "2198311517")
    }

    private suspend fun extractVideoInfo(
        url: String,
        contentId: String,
        site: String,
        mpxAcc: String,
    ): InfoDict {
        val itemsData = http.downloadWebpage(CBS_API + "?partner=$site&contentId=$contentId")
        val items = Regex("(?s)<item[^>]*>(.*?)</item>").findAll(itemsData)
            .map { it.groupValues[1] }
            .toList()
        val videoData = items.firstOrNull()
            ?: throw ExtractionError.Malformed("The CBS video player service returned no item.")
        val title = xmlText(videoData, "videoTitle") ?: xmlText(videoData, "videotitle")

        val assetTypes = linkedMapOf<String, Map<String, String>>()
        var hasDrm = false
        for (item in items) {
            val rawType = xmlText(item, "assetType")
            val query = linkedMapOf("mbr" to "true")
            val assetType: String
            if (rawType == null) {
                assetType = "fallback"
                query["formats"] = "M3U+none,MPEG4,M3U+appleHlsEncryption,MP3"
            } else {
                assetType = rawType
                query["assetTypes"] = rawType
            }
            if (assetTypes.containsKey(assetType)) continue
            if (listOf("HLS_FPS", "DASH_CENC", "OnceURL").any { assetType.contains(it) }) {
                if (assetType.contains("DASH_CENC")) hasDrm = true
                continue
            }
            if (assetType.startsWith("HLS") || assetType.contains("StreamPack")) {
                query["formats"] = "MPEG4,M3U"
            } else if (assetType == "RTMP" || assetType == "WIFI" || assetType == "3G") {
                query["formats"] = "MPEG4,FLV"
            }
            assetTypes[assetType] = query
        }
        if (assetTypes.isEmpty() && hasDrm) {
            throw ExtractionError.Unavailable("The video is DRM protected.")
        }

        val (metadata, formats, subtitles) = extractCommonVideoInfo(contentId, assetTypes, mpxAcc)
        val preview = xmlText(videoData, "previewImageURL")
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        return InfoDict(
            id = contentId,
            title = title ?: metadata.title,
            description = metadata.description,
            duration = xmlText(videoData, "videoLength")?.toDoubleOrNull()?.div(1000.0)
                ?: metadata.durationSeconds,
            uploadDate = metadata.uploadDate,
            uploader = metadata.uploader,
            channel = xmlText(videoData, "seriesTitle"),
            thumbnails = listOfNotNull(
                (preview ?: metadata.thumbnailUrl)?.let { Thumbnail(url = it) },
            ),
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "cbs",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "CBS"

        val VALID_URL: Regex = Regex(
            "(?:cbs:|https?://(?:www\\.)?(?:" +
                "cbs\\.com/(?:shows|movies)/(?:video|[^/]+/video|[^/]+)/|" +
                "colbertlateshow\\.com/(?:video|podcasts)/))(?<id>[\\w-]+)",
        )
    }
}

/** Upstream `ParamountPressExpressIE`: the press-express YouTube/Brightcove redirects. */
class ParamountPressExpressIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        if (match.groups["yt"]?.value != null) {
            return InfoDict(
                id = displayId,
                redirectUrl = "https://www.youtube.com/watch?v=$displayId",
                webpageUrl = url,
                extractor = "paramountpressexpress",
                extractorKey = IE_KEY,
            )
        }
        val webpage = http.downloadWebpage(url)
        val videoId = ExtractorUtils.searchRegex(
            "\\bvideo_id\\s*=\\s*[\"'](\\d+)[\"']\\s*,",
            webpage,
        ) ?: throw ExtractionError.Malformed("The press-express page had no Brightcove id.")
        val player = tagAttributes(elementTagById(webpage, "vcbrightcoveplayer").orEmpty())
        val accountId = player["data-account"] ?: "6055873637001"
        val playerId = player["data-player"] ?: "OtLKgXlO9F"
        val embed = player["data-embed"] ?: "default"
        // The upstream page token is smuggled to the Brightcove extractor;
        // the port's Brightcove extractor does not read a token, so it is not
        // carried and no token is stored here.
        return InfoDict(
            id = displayId,
            redirectUrl = "https://players.brightcove.net/$accountId/${playerId}_$embed/index.html" +
                "?videoId=$videoId",
            webpageUrl = url,
            extractor = "paramountpressexpress",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ParamountPressExpress"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?paramountpressexpress\\.com(?:/[\\w-]+)+/" +
                "(?<yt>yt-)?video/?\\?watch=(?<id>[\\w-]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun xmlText(fragment: String, tag: String): String? {
    val escaped = Regex.escape(tag)
    val match = Regex(
        "(?s)<(?:[\\w.-]+:)?$escaped\\b[^>]*>(.*?)</(?:[\\w.-]+:)?$escaped>",
    ).find(fragment) ?: return null
    return ExtractorUtils.unescapeHtml(match.groupValues[1])?.trim()?.takeIf { it.isNotEmpty() }
}

private fun elementTagById(webpage: String, id: String): String? = Regex(
    "<\\w+[^>]*\\bid\\s*=\\s*[\"']" + Regex.escape(id) + "[\"'][^>]*>",
).find(webpage)?.value

private val ATTRIBUTE = Regex(
    "([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))",
)

private fun tagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        val value = match.groupValues[2].ifEmpty { match.groupValues[3] }.ifEmpty { match.groupValues[4] }
        out[match.groupValues[1].lowercase()] = value
    }
    return out
}
