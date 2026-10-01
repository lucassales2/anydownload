/*
 * TikTok extractors — AnyDownload
 *
 * Kotlin translation of the webpage subset of `TikTokIE` and the redirect of
 * `TikTokVMIE` from `yt_dlp/extractor/tiktok.py` at upstream tag
 * `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tiktok.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the web page's `__UNIVERSAL_DATA_FOR_REHYDRATION__` JSON, the
 * `webapp.video-detail` item, `_extract_web_formats` (bitrateInfo UrlKey
 * parsing, play/download addresses, the music track), captions, thumbnails,
 * author/stats metadata, and the private-post status codes. `vm.tiktok.com`,
 * `vt.tiktok.com`, and `www.tiktok.com/t/` resolve as one redirect hop. Not
 * translated: the app API (device registration, mobile endpoints), the
 * challenge-cookie solving and impersonation path, user/sound/tag/collection
 * listings, Douyin, and live.
 *
 * No cookie, bearer token, msToken, or signed media URL is stored or
 * committed; fixture hosts are `*.example`.
 */
package com.anydownload.core.extract.tiktok

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Upstream `TikTokIE`: one public TikTok post from the web page. */
class TikTokIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "TikTok"

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val userId = match.groups["user"]?.value
        val pageUrl = "https://www.tiktok.com/@" + (userId ?: "_") + "/video/" + videoId

        val webpage = http.downloadWebpage(pageUrl)
        val scope = universalData(webpage)
            ?: throw ExtractionError.LoginRequired(
                "TikTok did not expose the post; the page may need a sign-in or challenge cookie.",
            )
        val detail = scope.obj("webapp.video-detail")
        val status = detail?.number("statusCode")?.toLong() ?: 0L
        val item = detail?.obj("itemInfo")?.obj("itemStruct")
        if (item == null) {
            when (status) {
                10216L, 10222L -> throw ExtractionError.LoginRequired(
                    "This TikTok post is private; an account with access is needed.",
                )

                10204L -> throw ExtractionError.Unavailable("This TikTok post is blocked for this network.")
                else -> throw ExtractionError.Unavailable("The TikTok post is not available (status $status).")
            }
        }

        val video = item.obj("video")
        if (video == null && item["isContentClassified"]?.let { (it as? JsonPrimitive)?.booleanOrNull } == true) {
            throw ExtractionError.LoginRequired("This TikTok post needs a sign-in for age-restricted content.")
        }

        val description = item.str("desc")
        val author = item.obj("authorInfo") ?: item.obj("author")
        val stats = item.obj("stats")
        val title = description?.let {
            if (it.length > TITLE_LENGTH) it.take(TITLE_LENGTH) + "..." else it
        }

        return InfoDict(
            id = videoId,
            title = title,
            description = description,
            duration = video?.number("duration"),
            uploader = author?.str("uniqueId") ?: author?.str("author"),
            channel = author?.str("nickname"),
            channelId = author?.str("authorSecId") ?: author?.str("secUid"),
            uploadDate = item.number("createTime")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            viewCount = stats?.number("playCount")?.toLong(),
            formats = extractWebFormats(item, pageUrl),
            subtitles = extractSubtitles(item),
            thumbnails = thumbnails(video),
            webpageUrl = pageUrl,
            extractor = "tiktok",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_extract_web_formats`: bitrateInfo, play, download, music. */
    private fun extractWebFormats(item: JsonObject, webpageUrl: String): List<MediaFormat> {
        val video = item.obj("video") ?: return emptyList()
        val headers = mapOf("referer" to webpageUrl)
        val playWidth = video.number("width")?.toLong()
        val playHeight = video.number("height")?.toLong()
        val ratio = if (playWidth != null && playHeight != null && playHeight != 0L) {
            playWidth.toDouble() / playHeight
        } else {
            0.5625
        }
        val formats = mutableListOf<MediaFormat>()

        for (element in video.array("bitrateInfo").orEmpty()) {
            val bitrateInfo = element as? JsonObject ?: continue
            val playAddr = bitrateInfo.obj("PlayAddr") ?: continue
            val urlList = playAddr.array("UrlList") ?: continue
            val parsed = parseUrlKey(playAddr.str("UrlKey") ?: "")
            val isBytevc2 = parsed.vcodec == "bytevc2"
            var width = playWidth
            var height = playHeight
            val dimension = parsed.resolution?.removeSuffix("p")?.toLongOrNull()
            if (dimension != null && dimension > 0) {
                val side = if (dimension == 540L) 576L else dimension
                if (ratio < 1) {
                    val scaled = (side / ratio).toLong()
                    width = side
                    height = scaled - (scaled % 2)
                } else {
                    val scaled = (side * ratio).toLong()
                    width = scaled + (scaled % 2)
                    height = side
                }
            }
            for (urlElement in urlList) {
                val videoUrl = ExtractorUtils.urlOrNone((urlElement as? JsonPrimitive)?.content) ?: continue
                formats += MediaFormat(
                    formatId = parsed.formatId,
                    url = protoRelative(videoUrl),
                    ext = "mp4",
                    vcodec = parsed.vcodec ?: "h264",
                    acodec = if (videoUrl.substringBefore('?').endsWith("/media-video-hvc1/")) {
                        MediaFormat.CODEC_NONE
                    } else {
                        "aac"
                    },
                    tbr = parsed.tbr,
                    filesize = playAddr.number("DataSize")?.toLong(),
                    formatNote = if (isBytevc2) "UNPLAYABLE" else null,
                    preference = if (isBytevc2) -100 else -1,
                    quality = parsed.resolution,
                    width = width,
                    height = height,
                    httpHeaders = headers,
                )
            }
        }

        val playQuality = formats.firstOrNull { it.width == playWidth }?.quality
        urlFrom(video["playAddr"])?.let { playUrl ->
            formats += MediaFormat(
                formatId = "play",
                url = protoRelative(playUrl),
                ext = "mp4",
                vcodec = "h264",
                acodec = "aac",
                width = playWidth,
                height = playHeight,
                quality = playQuality,
                httpHeaders = headers,
            )
        }
        urlFrom(video["downloadAddr"])?.let { downloadUrl ->
            formats += MediaFormat(
                formatId = "download",
                url = protoRelative(downloadUrl),
                ext = "mp4",
                vcodec = "h264",
                acodec = "aac",
                formatNote = "watermarked",
                preference = -2,
                httpHeaders = headers,
            )
        }

        item.obj("music")?.let { music ->
            ExtractorUtils.urlOrNone(music.str("playUrl"))?.let { audioUrl ->
                val ext = if (audioUrl.substringBefore('?').endsWith(".mp3")) "mp3" else "m4a"
                formats += MediaFormat(
                    formatId = "audio",
                    url = protoRelative(audioUrl),
                    ext = ext,
                    acodec = if (ext == "m4a") "aac" else ext,
                    vcodec = MediaFormat.CODEC_NONE,
                    httpHeaders = headers,
                )
            }
        }

        return formats
            .filter { hostOf(it.url)?.equals("www.tiktok.com", ignoreCase = true) != true }
            .distinctBy { it.url }
    }

    /** Upstream `_parse_url_key`: `v1200_h264_720p_1500000` style keys. */
    private fun parseUrlKey(urlKey: String): ParsedUrlKey {
        val match = URL_KEY.find(urlKey) ?: return ParsedUrlKey()
        val codec = match.groups["codec"]?.value
        return ParsedUrlKey(
            formatId = match.groups["id"]?.value,
            vcodec = codec?.let { if (it == "bytevc1") "h265" else it },
            resolution = match.groups["res"]?.value,
            tbr = match.groups["bitrate"]?.value?.toDoubleOrNull()?.div(1000),
        )
    }

    /** Upstream `_get_subtitles`, web page data only (no app endpoints). */
    private fun extractSubtitles(item: JsonObject): List<SubtitleTrack> {
        val video = item.obj("video") ?: return emptyList()
        val tracks = mutableListOf<SubtitleTrack>()
        for (element in video.obj("cla_info")?.array("caption_infos").orEmpty()) {
            val caption = element as? JsonObject ?: continue
            val captionUrl = caption.str("url") ?: continue
            tracks += SubtitleTrack(
                language = caption.str("lang") ?: "en",
                formats = listOf(SubtitleFormat(extForFormat(caption.str("Format")), protoRelative(captionUrl))),
            )
        }
        if (tracks.isNotEmpty()) return tracks
        for (element in video.array("subtitleInfos").orEmpty()) {
            val caption = element as? JsonObject ?: continue
            val captionUrl = caption.str("Url") ?: continue
            tracks += SubtitleTrack(
                language = caption.str("LanguageCodeName") ?: "en",
                formats = listOf(SubtitleFormat(extForFormat(caption.str("Format")), protoRelative(captionUrl))),
            )
        }
        return tracks
    }

    private fun thumbnails(video: JsonObject?): List<Thumbnail> {
        if (video == null) return emptyList()
        val thumbnails = mutableListOf<Thumbnail>()
        for (id in listOf("thumbnail", "cover", "dynamicCover", "originCover")) {
            val url = ExtractorUtils.urlOrNone(video.str(id)) ?: continue
            thumbnails += Thumbnail(url = protoRelative(url), id = id)
        }
        return thumbnails
    }

    companion object {
        const val IE_KEY: String = "TikTok"

        /** Upstream `_VALID_URL`. */
        val VALID_URL: Regex = Regex(
            "https?://www\\.tiktokv?\\.com/(?:embed|(?:share|@(?<user>[-\\w.]+)?)/video)/(?<id>\\d+)",
        )

        private const val TITLE_LENGTH = 72
        private val URL_KEY = Regex("v[^_]+_(?<id>(?<codec>[^_]+)_(?<res>\\d+p)_(?<bitrate>\\d+))")
    }
}

/**
 * Upstream `TikTokVMIE`: `vm.tiktok.com`, `vt.tiktok.com`, and
 * `www.tiktok.com/t/` resolve one redirect hop and re-enter the registry.
 */
class TikTokVMIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "vm.tiktok"

    override suspend fun extract(url: String): InfoDict {
        val id = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val finalUrl = http.followRedirects(url, headers = mapOf("user-agent" to "facebookexternalhit/1.1"))
        return InfoDict(
            id = id,
            webpageUrl = url,
            extractor = "tiktok",
            extractorKey = IE_KEY,
            redirectUrl = finalUrl,
        )
    }

    companion object {
        const val IE_KEY: String = "TikTokVM"

        /** Upstream `_VALID_URL`. */
        val VALID_URL: Regex = Regex(
            "https?://(?:(?:vm|vt)\\.tiktok\\.com|(?:www\\.)?tiktok\\.com/t)/(?<id>\\w+)",
        )
    }
}

private data class ParsedUrlKey(
    val formatId: String? = null,
    val vcodec: String? = null,
    val resolution: String? = null,
    val tbr: Double? = null,
)

/** Upstream `_get_universal_data`: the rehydration script's `__DEFAULT_SCOPE__`. */
private fun universalData(html: String): JsonObject? {
    val match = UNIVERSAL_DATA.find(html) ?: return null
    return (ExtractorUtils.parseJson(match.groupValues[1]) as? JsonObject)?.obj("__DEFAULT_SCOPE__")
}

private val UNIVERSAL_DATA = Regex(
    "<script[^>]+id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.+?)</script>",
    RegexOption.DOT_MATCHES_ALL,
)

/** Upstream `extract_addr`/`url_or_none` for a string or `{src}` address. */
private fun urlFrom(element: JsonElement?): String? = when (element) {
    is JsonPrimitive -> ExtractorUtils.urlOrNone(element.content)
    is JsonObject -> ExtractorUtils.urlOrNone(element.str("src"))
    else -> null
}

private fun protoRelative(value: String): String = if (value.startsWith("//")) "https:$value" else value

private fun hostOf(url: String?): String? =
    url?.substringAfter("://", "")?.takeIf { it.isNotEmpty() }?.substringBefore('/')?.substringBefore('?')

private fun extForFormat(format: String?): String = when (format) {
    "creator_caption" -> "json"
    "srt" -> "srt"
    "webvtt" -> "vtt"
    else -> "vtt"
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
