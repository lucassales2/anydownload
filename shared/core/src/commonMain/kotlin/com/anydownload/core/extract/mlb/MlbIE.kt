/*
 * MLB extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `mlb.py` from
 * `yt_dlp/extractor/mlb.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `mlb.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `content.mlb.com` details JSON, the Fastball GraphQL
 * media-playback query, the article `window.initState` scan, m3u8/plain
 * playbacks, thumbnail cuts, and closed-caption URLs. `MLBTVIE` matches and
 * fails typed: its GraphQL session/playback flow needs device and playback
 * tokens. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.mlb

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `MLBIE`: the legacy video id pages. */
class MLBIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val video = http.downloadJson(
            "http://content.mlb.com/mlb/item/id/v1/$displayId/details/web-v1.json",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The MLB details API was not an object.")
        return buildInfo(video, video, video.str("date"), video.str("language"), url)
    }

    companion object {
        const val IE_KEY: String = "MLB"

        val VALID_URL: Regex = Regex(
            "https?://(?:[\\da-z_-]+\\.)*mlb\\.com/" +
                "(?:(?:(?:[^/]+/)*video/[^/]+/c-|" +
                "(?:shared/video/embed/(?:embed|m-internal-embed)\\.html|" +
                "(?:[^/]+/)+(?:play|index)\\.jsp)\\?.*?\\bcontent_id=)(?<id>\\d+))",
        )
    }
}

/** Upstream `MLBVideoIE`: the Fastball GraphQL media playback. */
class MLBVideoIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !MLBIE.VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val query = """
            {
              mediaPlayback(ids: "$displayId") {
                description
                feeds(types: CMS) {
                  closedCaptions
                  duration
                  image { cuts { width height src } }
                  playbacks { name url }
                }
                id
                timestamp
                title
              }
            }
        """.trimIndent()
        val data = http.downloadJson(
            "https://fastball-gateway.mlb.com/graphql?query=${encodeQuery(query)}",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The MLB GraphQL API was not an object.")
        val video = data.obj("data")?.array("mediaPlayback")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The MLB GraphQL API returned no media playback.")
        val feed = video.array("feeds")?.firstOrNull() as? JsonObject ?: video
        return buildInfo(video, feed, video.str("timestamp"), "EN", url)
    }

    companion object {
        const val IE_KEY: String = "MLBVideo"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?mlb\\.com/(?:[^/]+/)*video/(?<id>[^/?&#]+)",
        )
    }
}

/** Upstream `MLBTVIE`: the MLB.TV games (session-token wall). */
class MLBTVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(
        "The MLB.TV GraphQL playback needs initSession/initPlaybackSession device and playback " +
            "tokens; the port does not translate that flow.",
    )

    companion object {
        const val IE_KEY: String = "MLBTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?mlb\\.com/tv/g(?<id>\\d{6})")
    }
}

/** Upstream `MLBArticleIE`: the news articles with video parts. */
class MLBArticleIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val initStateRaw = jsonAfterKey(webpage, "window.initState")
            ?: throw ExtractionError.Malformed("The article page had no window.initState data.")
        val apolloCache = initStateRaw.obj("apolloCache")
            ?: throw ExtractionError.Malformed("The article page had no apollo cache.")
        val article = apolloCache.obj("ROOT_QUERY")?.entries
            ?.firstOrNull { it.key.startsWith("getArticle") }
            ?.value as? JsonObject
            ?: throw ExtractionError.Malformed("The article page had no article data.")
        val entries = article.array("parts").orEmpty().mapNotNull { element ->
            val part = element as? JsonObject ?: return@mapNotNull null
            val isVideo = part.str("__typename") == "Video" || part.str("type") == "video"
            if (!isVideo) return@mapNotNull null
            val slug = part.str("slug") ?: return@mapNotNull null
            InfoEntry(url = "https://www.mlb.com/video/$slug")
        }
        return InfoDict(
            id = article.str("translationId") ?: displayId,
            title = ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
            description = article.str("summary"),
            entries = entries,
            webpageUrl = url,
            extractor = "mlb:article",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MLBArticle"

        val VALID_URL: Regex = Regex("https?://www\\.mlb\\.com/news/(?<id>[\\w-]+)")
    }
}

// ------------------------------------------------------------------ helpers

private fun buildInfo(
    video: JsonObject,
    feed: JsonObject,
    timestampValue: String?,
    languageValue: String?,
    url: String,
): InfoDict {
    val videoId = video.primitiveText("id") ?: throw ExtractionError.Malformed("The MLB video had no id.")
    val formats = mutableListOf<MediaFormat>()
    for (element in feed.array("playbacks").orEmpty()) {
        val playback = element as? JsonObject ?: continue
        val playbackUrl = playback.str("url") ?: continue
        val name = playback.str("name")
        if (ExtractorUtils.determineExt(playbackUrl) == "m3u8") {
            formats += MediaFormat(
                formatId = name ?: "hls",
                url = playbackUrl,
                ext = "mp4",
                protocol = "m3u8_native",
            )
        } else {
            var height: Long? = null
            var width: Long? = null
            var tbr: Double? = null
            var fps: Double? = null
            val nameMatch = NAME_BITRATE.find(name.orEmpty())
            if (nameMatch != null) {
                height = nameMatch.groupValues[3].toLongOrNull()
                tbr = nameMatch.groupValues[1].toDoubleOrNull()
                width = nameMatch.groupValues[2].toLongOrNull()
            }
            val urlMatch = URL_BITRATE.find(playbackUrl)
            if (urlMatch != null) {
                fps = urlMatch.groupValues[3].toDoubleOrNull()
                height = urlMatch.groupValues[2].toLongOrNull()
                tbr = urlMatch.groupValues[4].toDoubleOrNull()
                width = urlMatch.groupValues[1].toLongOrNull()
            }
            formats += MediaFormat(
                formatId = name,
                url = playbackUrl,
                ext = ExtractorUtils.determineExt(playbackUrl),
                height = height,
                width = width,
                tbr = tbr,
                fps = fps,
            )
        }
    }
    val thumbnails = mutableListOf<Thumbnail>()
    for (element in feed.obj("image")?.array("cuts").orEmpty()) {
        val cut = element as? JsonObject ?: continue
        val src = cut.str("src") ?: continue
        thumbnails += Thumbnail(
            url = src,
            height = cut.number("height")?.toLong(),
            width = cut.number("width")?.toLong(),
        )
    }
    val language = (languageValue ?: "EN").lowercase()
    val subtitles = mutableListOf<SubtitleTrack>()
    val ccUrls = mutableListOf<SubtitleFormat>()
    for (element in feed.array("keywordsAll").orEmpty()) {
        val keyword = element as? JsonObject ?: continue
        if (keyword.str("type")?.startsWith("closed_captions_location_") != true) continue
        keyword.str("value")?.let { ccUrls += SubtitleFormat(ext = "vtt", url = it) }
    }
    for (element in feed.array("closedCaptions").orEmpty()) {
        val ccUrl = (element as? JsonPrimitive)?.content ?: continue
        ccUrls += SubtitleFormat(ext = "vtt", url = ccUrl)
    }
    if (ccUrls.isNotEmpty()) {
        subtitles += SubtitleTrack(language = language, formats = ccUrls)
    }
    return InfoDict(
        id = videoId,
        title = video.str("title"),
        description = video.str("description"),
        duration = ExtractorUtils.parseDuration(feed.str("duration"))?.toDouble(),
        uploadDate = dateFromIso(timestampValue),
        thumbnails = thumbnails,
        formats = formats,
        subtitles = subtitles,
        webpageUrl = url,
        extractor = "mlb",
        extractorKey = "MLB",
    )
}

private val NAME_BITRATE = Regex("_(\\d+)K_(\\d+)X(\\d+)")
private val URL_BITRATE = Regex("_(\\d+)x(\\d+)_(\\d+)_(\\d+)K\\.mp4")

private fun jsonAfterKey(html: String, key: String): JsonObject? {
    val marker = Regex("window\\.initState\\s*=").find(html) ?: return null
    val start = html.indexOf('{', marker.range.last + 1)
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var i = start
    while (i < html.length) {
        val c = html[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return ExtractorUtils.parseJson(html.substring(start, i + 1)) as? JsonObject
                    }
                }
            }
        }
        i++
    }
    return null
}

private fun encodeQuery(value: String): String =
    value.replace("%", "%25").replace(" ", "%20").replace("\n", "%0A")
        .replace("\"", "%22").replace("{", "%7B").replace("}", "%7D")
        .replace(":", "%3A").replace(",", "%2C").replace("(", "%28").replace(")", "%29")

private fun dateFromIso(value: String?): String? {
    val match = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(value ?: return null) ?: return null
    return match.groupValues[1] + match.groupValues[2] + match.groupValues[3]
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
