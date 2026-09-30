/*
 * Facebook extractors — AnyDownload
 *
 * Kotlin translation of the classic server-JS subset of `FacebookIE` and the
 * `FacebookPluginsVideoIE`, `FacebookRedirectURLIE`, and `FacebookReelIE`
 * redirects from `yt_dlp/extractor/facebook.py` at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `facebook.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `handleServerJS` / `s.handle` `VideoConfig` instances carry the
 * classic `sd_src`/`hd_src`/`sd_src_no_ratelimit`/`hd_src_no_ratelimit`
 * formats and the `dash_manifest` XML; page `og:` metadata, `ownerName`,
 * `data-utime`, and view counts map onto the info dict, and every format gets
 * the `facebookexternalhit/1.1` user agent and the 250 MiB chunk hint.
 * Login walls and the interstitial message fail typed. The `data-sjs`
 * RelayPrefetchedStreamCache / GraphQL paths, the tahoe fallback, watch
 * parties, ads, and photos are not translated. A login wall or DRM wall is
 * Partial. No cookie or signed media URL is stored or committed; fixture
 * hosts are `*.example`.
 */
package com.anydownlod.core.extract.facebook

import com.anydownlod.core.download.Mpd
import com.anydownlod.core.download.MpdResult
import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.SubtitleFormat
import com.anydownlod.core.extract.SubtitleTrack
import com.anydownlod.core.extract.Thumbnail
import com.anydownlod.core.extract.DownloaderOptions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `FacebookIE`: one public video from the classic server-JS data. */
class FacebookIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Facebook"

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()

        val webpage = http.downloadWebpage(
            url,
            headers = mapOf("user-agent" to EXTERNAL_HIT_AGENT),
        )

        val videoData = extractVideoData(webpage)
        if (videoData.isEmpty()) {
            INTERSTITIAL.find(webpage)?.groupValues?.get(1)?.let {
                throw ExtractionError.Unavailable("Facebook says this video is not available.")
            }
            if (LOGIN_MARKERS.any { webpage.contains(it) }) {
                throw ExtractionError.LoginRequired()
            }
            throw ExtractionError.LoginRequired(
                "Facebook did not expose a playable video; the modern data paths are not translated and a sign-in may be required.",
            )
        }

        val formats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in videoData) {
            val items = when (element) {
                is JsonObject -> listOf(element)
                is JsonArray -> element.filterIsInstance<JsonObject>()
                else -> emptyList()
            }
            for (item in items) {
                val streamType = item.str("stream_type") ?: "progressive"
                for (quality in listOf("sd", "hd")) {
                    for (sourceType in listOf("src", "src_no_ratelimit")) {
                        val sourceUrl = item.str("${quality}_$sourceType") ?: continue
                        formats += MediaFormat(
                            formatId = "${streamType}_${quality}_$sourceType",
                            url = sourceUrl,
                            quality = (if (streamType == "progressive") -10 else -3)
                                .plus(if (quality == "hd") 1 else 0)
                                .toString(),
                            height = if (quality == "hd") 720 else null,
                        )
                    }
                }
                item.str("dash_manifest")?.let { manifest ->
                    val xml = percentDecode(manifest, plusAsSpace = true)
                    if ("<MPD" in xml) {
                        when (val result = Mpd.parse(item.str("dash_manifest_url") ?: url, xml)) {
                            is MpdResult.Formats -> formats += result.formats
                            is MpdResult.Failed -> Unit
                        }
                    }
                }
                item.str("subtitles_src")?.let { subtitleUrl ->
                    subtitles += SubtitleTrack(
                        language = "en",
                        formats = listOf(SubtitleFormat(ext = subtitleExt(subtitleUrl), url = subtitleUrl)),
                    )
                }
            }
        }
        if (formats.isEmpty()) throw ExtractionError.NoFormats("Facebook did not expose a downloadable format.")

        // Upstream `process_formats`: a non-browser agent and a bounded chunk.
        val prepared = formats.distinctBy { it.url }.map { format ->
            format.copy(
                httpHeaders = (format.httpHeaders ?: emptyMap()) + ("user-agent" to EXTERNAL_HIT_AGENT),
                downloaderOptions = format.downloaderOptions ?: DownloaderOptions(httpChunkSize = CHUNK_SIZE),
            )
        }

        val title = ExtractorUtils.htmlSearchMeta(webpage, "og:title", "twitter:title")
            ?.replace(Regex("\\s*\\|\\s*Facebook$"), "")
            ?.takeIf { it.isNotBlank() }
            ?: ExtractorUtils.searchRegex("<title[^>]*>(.+?)</title>", webpage)
                ?.let(::stripTags)
                ?.takeIf { it.isNotBlank() }
            ?: "Facebook video #$videoId"
        val description = ExtractorUtils.htmlSearchMeta(webpage, "description", "og:description", "twitter:description")
        val uploader = ExtractorUtils.searchRegex("\\bownerName\\s*:\\s*\"([^\"]+)\"", webpage)
        val thumbnail = ExtractorUtils.htmlSearchMeta(webpage, "og:image", "twitter:image")
            ?.takeIf { it.contains(Regex("\\.(?:jpg|png)")) }

        return InfoDict(
            id = videoId,
            title = title,
            description = description,
            uploader = uploader,
            uploadDate = ExtractorUtils.searchRegex("<abbr[^>]+data-utime=[\"'](\\d+)", webpage)
                ?.toLongOrNull()?.let(ExtractorUtils::epochSecondsToDate),
            viewCount = viewCountOf(webpage),
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = prepared,
            subtitles = subtitles.distinctBy { it.formats.firstOrNull()?.url },
            webpageUrl = url,
            extractor = "facebook",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Facebook"

        /** Upstream `_VALID_URL` without the onion host forms. */
        val VALID_URL: Regex = Regex(
            "(?:https?://(?:[\\w-]+\\.)?facebook\\.com/(?:[^#]*?\\#!/)?" +
                "(?:" +
                "(?:(?:permalink\\.php|video/video\\.php|photo\\.php|video\\.php|video/embed|story\\.php|" +
                "watch(?:/live)?/?)\\?(?:.*?)(?:v|video_id|story_fbid)=)" +
                "|[^/]+/videos/(?:[^/]+/)?" +
                "|[^/]+/posts/" +
                "|events/(?:[^/]+/)?" +
                "|groups/[^/]+/(?:permalink|posts)/(?:[\\da-f]+/)?" +
                "|watchparty/" +
                ")|facebook:)" +
                "(?<id>pfbid[A-Za-z0-9]+|\\d+)",
        )
    }
}

/** Upstream `FacebookPluginsVideoIE`: decode `href=` and re-enter the registry. */
class FacebookPluginsVideoIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Facebook plugins video"

    override suspend fun extract(url: String): InfoDict {
        val encoded = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val target = percentDecode(encoded, plusAsSpace = false)
        if (!target.startsWith("http")) throw ExtractionError.Malformed("The Facebook plugin href was invalid.")
        return InfoDict(
            id = target,
            webpageUrl = url,
            extractor = "facebook",
            extractorKey = IE_KEY,
            redirectUrl = target,
        )
    }

    companion object {
        const val IE_KEY: String = "FacebookPluginsVideo"

        val VALID_URL: Regex = Regex(
            "https?://(?:[\\w-]+\\.)?facebook\\.com/plugins/video\\.php\\?.*?\\bhref=(?<id>https.+)",
        )
    }
}

/** Upstream `FacebookRedirectURLIE`: the `u=` safety redirect target. */
class FacebookRedirectURLIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Facebook redirect"

    override suspend fun extract(url: String): InfoDict {
        val target = queryParam(url, "u") ?: throw ExtractionError.Malformed("The Facebook redirect had no target.")
        return InfoDict(
            id = target,
            webpageUrl = url,
            extractor = "facebook",
            extractorKey = IE_KEY,
            redirectUrl = target,
        )
    }

    companion object {
        const val IE_KEY: String = "FacebookRedirectURL"

        val VALID_URL: Regex = Regex("https?://(?:[\\w-]+\\.)?facebook\\.com/flx/warn[/?]")
    }
}

/** Upstream `FacebookReelIE`: canonicalize to the watch URL. */
class FacebookReelIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Facebook reel"

    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return InfoDict(
            id = videoId,
            webpageUrl = url,
            extractor = "facebook",
            extractorKey = IE_KEY,
            redirectUrl = "https://m.facebook.com/watch/?v=$videoId&_rdr",
        )
    }

    companion object {
        const val IE_KEY: String = "FacebookReel"

        val VALID_URL: Regex = Regex("https?://(?:[\\w-]+\\.)?facebook\\.com/reel/(?<id>\\d+)")
    }
}

private const val EXTERNAL_HIT_AGENT = "facebookexternalhit/1.1"
private const val CHUNK_SIZE = 250L shl 20

private val LOGIN_MARKERS = listOf(
    ">You must log in to continue",
    "id=\"login_form\"",
    "id=\"loginbutton\"",
)

private val INTERSTITIAL = Regex("class=\"[^\"]*uiInterstitialContent[^\"]*\"><div>(.*?)</div>")

/** Upstream `extract_video_data`: `VideoConfig` entries of the server JS data. */
private fun extractVideoData(webpage: String): List<JsonElement> {
    val serverJsData = SERVER_JS.find(webpage)?.groupValues?.get(1)
        ?.let { ExtractorUtils.parseJson(it) as? JsonObject }
    var instances = serverJsData?.array("instances")
    if (instances == null) {
        instances = HANDLE_S_JS.find(webpage)?.groupValues?.get(1)
            ?.let { ExtractorUtils.parseJson(it) as? JsonObject }
            ?.obj("jsmods")?.array("instances")
    }
    val videoData = mutableListOf<JsonElement>()
    for (element in instances.orEmpty()) {
        val item = element as? JsonArray ?: continue
        val kind = (item.getOrNull(1) as? JsonArray)
            ?.firstOrNull()
            ?.let { (it as? JsonPrimitive)?.content }
        if (kind != "VideoConfig") continue
        val videoItem = (item.getOrNull(2) as? JsonArray)?.firstOrNull() as? JsonObject ?: continue
        if (videoItem.str("video_id") == null) continue
        videoItem["videoData"]?.let { videoData += it }
    }
    return videoData
}

private val SERVER_JS = Regex(
    "handleServerJS\\((\\{.+\\})(?:\\);|\",)",
    RegexOption.DOT_MATCHES_ALL,
)

private val HANDLE_S_JS = Regex("\\bs\\.handle\\((\\{.+?\\})\\);", RegexOption.DOT_MATCHES_ALL)

private fun subtitleExt(url: String): String = when {
    url.substringBefore('?').endsWith(".srt", ignoreCase = true) -> "srt"
    else -> "vtt"
}

/** Upstream `parse_count` for the page's view-count fields. */
private fun viewCountOf(webpage: String): Long? {
    Regex("\\bviewCount\\s*:\\s*[\"']([\\d,.]+)")
        .find(webpage)?.groupValues?.get(1)
        ?.filter { it.isDigit() }?.toLongOrNull()
        ?.let { return it }
    return Regex("video_view_count[\"']?\\s*:\\s*(\\d+)")
        .find(webpage)?.groupValues?.get(1)?.toLongOrNull()
}

private fun stripTags(value: String): String {
    val withoutTags = Regex("<[^>]*>").replace(value, " ")
    return (ExtractorUtils.unescapeHtml(withoutTags) ?: withoutTags)
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** A small `urllib.parse.unquote[_plus]` subset. */
private fun percentDecode(value: String, plusAsSpace: Boolean): String {
    val out = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%' && index + 2 < value.length) {
            val code = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (code != null) {
                out.append(code.toChar())
                index += 3
                continue
            }
        }
        out.append(if (plusAsSpace && character == '+') ' ' else character)
        index++
    }
    return out.toString()
}

private fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    for (pair in query.split('&')) {
        if (pair.substringBefore('=') == name) return percentDecode(pair.substringAfter('=', ""), plusAsSpace = false)
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
