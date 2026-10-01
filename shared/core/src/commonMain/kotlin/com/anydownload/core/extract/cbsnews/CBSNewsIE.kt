/*
 * CBS News extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `cbsnews.py` from
 * `yt_dlp/extractor/cbsnews.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `cbsnews.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `CBSNEWS.defaultPayload` item walk (mp4 and m3u8; an Anvato id
 * becomes an `anvato:` redirect that fails typed), the embed-iframe playlist
 * scans, the public rundown live APIs, and the live-video story API.
 * `CBSNewsEmbedIE` matches and fails typed: its payload is zlib-compressed
 * and the port has no inflate helper. No cookie, token, or signed media URL
 * is stored here.
 */
package com.anydownload.core.extract.cbsnews

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

private const val LOCALE_RE =
    "atlanta|baltimore|boston|chicago|colorado|detroit|losangeles|miami|minnesota|newyork|" +
        "philadelphia|pittsburgh|sacramento|sanfrancisco|texas"

/** Upstream `CBSNewsIE`: cbsnews.com news and video pages. */
class CBSNewsIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        extractPlaylist(webpage, displayId, url)?.let { return it }
        val item = getItem(webpage)
        val videoId = item?.primitiveText("mpxRefId") ?: displayId
        val videoUrl = item?.let(::getVideoUrl)
            ?: throw ExtractionError.NoFormats("No video content was found.")
        return extractVideo(http, item, videoUrl, videoId, url)
    }

    companion object {
        const val IE_KEY: String = "CBSNews"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?cbsnews\\.com/(?:news|video)/(?<id>[\\w-]+)")
    }
}

/** Upstream `CBSNewsEmbedIE`: the zlib-compressed embed payload. */
class CBSNewsEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(
        "The CBS News embed payload is zlib-compressed and the port has no inflate helper.",
    )

    companion object {
        const val IE_KEY: String = "CBSNewsEmbed"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?cbsnews\\.com/embed/video[^#]*#(?<id>.+)")
    }
}

/** Upstream `CBSLocalIE`: the local station video pages. */
class CBSLocalIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val item = getItem(webpage)
        val videoId = item?.primitiveText("mpxRefId") ?: displayId
        val videoUrl = item?.let(::getVideoUrl)
        if (videoUrl == null) {
            extractPlaylist(webpage, displayId, url)?.let { return it }
            throw ExtractionError.NoFormats("No video content was found.")
        }
        return extractVideo(http, item, videoUrl, videoId, url)
    }

    companion object {
        const val IE_KEY: String = "CBSLocal"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?cbsnews\\.com/(?:$LOCALE_RE)/(?:live/)?video/(?<id>[\\w-]+)",
        )
    }
}

/** Upstream `CBSLocalArticleIE`: the local station news articles. */
class CBSLocalArticleIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        extractPlaylist(webpage, displayId, url)?.let { return it }
        val item = getItem(webpage)
        val videoId = item?.primitiveText("mpxRefId") ?: displayId
        val videoUrl = item?.let(::getVideoUrl)
            ?: throw ExtractionError.NoFormats("No video content was found.")
        return extractVideo(http, item, videoUrl, videoId, url)
    }

    companion object {
        const val IE_KEY: String = "CBSLocalArticle"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?cbsnews\\.com/(?:$LOCALE_RE)/news/(?<id>[\\w-]+)",
        )
    }
}

/** Upstream `CBSLocalLiveIE`: a local live stream. */
class CBSLocalLiveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val code = LOCALES[matchId(url)] ?: throw ExtractionError.Unavailable(
            "The livestream is not available for this locale.",
        )
        return extractLive(http, "CBSN-$code", url)
    }

    companion object {
        const val IE_KEY: String = "CBSLocalLive"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?cbsnews\\.com/(?<id>$LOCALE_RE)/live/?(?:[?#]|$)",
        )
    }
}

/** Upstream `CBSNewsLiveIE`: the national live stream. */
class CBSNewsLiveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = extractLive(http, "CBSN-US", url)

    companion object {
        const val IE_KEY: String = "CBSNewsLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?cbsnews\\.com/live/?(?:[?#]|$)")
    }
}

/** Upstream `CBSNewsLiveVideoIE`: the live-video story pages. */
class CBSNewsLiveVideoIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = http.downloadJson(
            "http://feeds.cbsn.cbsnews.com/rundown/story?device=desktop&dvr_slug=$displayId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The rundown story API was not an object.")
        val videoUrl = data.str("url")
            ?: throw ExtractionError.NoFormats("The rundown story had no video URL.")
        return InfoDict(
            id = displayId,
            title = data.str("headline"),
            duration = ExtractorUtils.parseDuration(data.str("segmentDur"))?.toDouble(),
            thumbnails = listOfNotNull(data.str("thumbnail_url_hd")?.let { Thumbnail(url = it) }),
            formats = listOf(
                MediaFormat(formatId = "hls", url = videoUrl, ext = "mp4", protocol = "m3u8_native"),
            ),
            webpageUrl = url,
            extractor = "cbsnews:livevideo",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "CBSNewsLiveVideo"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?cbsnews\\.com/live/video/(?<id>[^/?#]+)")
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun getItem(webpage: String): JsonObject? {
    val marker = Regex("CBSNEWS\\.defaultPayload\\s*=").find(webpage) ?: return null
    val start = webpage.indexOf('{', marker.range.last + 1)
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var i = start
    while (i < webpage.length) {
        val c = webpage[i]
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
                        val root = ExtractorUtils.parseJson(webpage.substring(start, i + 1)) as? JsonObject
                        return root?.array("items")?.firstOrNull() as? JsonObject
                    }
                }
            }
        }
        i++
    }
    return null
}

private fun getVideoUrl(item: JsonObject): String? = item.str("video") ?: item.str("video2")

private val LOCALES = mapOf(
    "baltimore" to "BAL",
    "boston" to "BOS",
    "chicago" to "CHI",
    "colorado" to "DEN",
    "detroit" to "DET",
    "losangeles" to "LA",
    "miami" to "MIA",
    "minnesota" to "MIN",
    "newyork" to "NY",
    "philadelphia" to "PHI",
    "pittsburgh" to "PIT",
    "sacramento" to "SAC",
    "sanfrancisco" to "SF",
    "texas" to "DAL",
)

private fun extractPlaylist(webpage: String, playlistId: String, url: String): InfoDict? {
    val entries = Regex(
        "<iframe[^>]+data-src=\"(https?://(?:www\\.)?cbsnews\\.com/embed/video/[^#]*#[^\"]+)\"",
    ).findAll(webpage).map { InfoEntry(url = it.groupValues[1]) }.toList()
    if (entries.isEmpty()) return null
    return InfoDict(
        id = playlistId,
        title = ExtractorUtils.htmlSearchMeta(webpage, "og:title", "twitter:title"),
        description = ExtractorUtils.htmlSearchMeta(
            webpage,
            "og:description",
            "twitter:description",
            "description",
        ),
        entries = entries,
        webpageUrl = url,
        extractor = "cbsnews",
        extractorKey = "CBSNews",
    )
}

private suspend fun extractVideo(
    http: ExtractorHttp,
    item: JsonObject,
    videoUrl: String,
    videoId: String,
    url: String,
): InfoDict {
    val formats = mutableListOf<MediaFormat>()
    var redirectUrl: String? = null
    val format = item.str("format")
    if (format?.contains("mp4") == true || ExtractorUtils.determineExt(videoUrl) == "mp4") {
        formats += MediaFormat(url = videoUrl, ext = "mp4")
    } else {
        val manifest = http.downloadWebpage(videoUrl)
        val anvatoId = ExtractorUtils.searchRegex("anvato-(\\d+)", manifest, default = null)
        if (anvatoId != null) {
            redirectUrl = "anvato:5VD6Eyd6djewbCmNwBFnsJj17YAvGRwl:$anvatoId"
        } else {
            formats += MediaFormat(
                formatId = "hls",
                url = videoUrl,
                ext = "mp4",
                protocol = "m3u8_native",
            )
        }
    }
    val subtitles = mutableListOf<SubtitleTrack>()
    val captionsUrl = item.str("captions")
    if (captionsUrl != null) {
        subtitles += SubtitleTrack(
            language = "en",
            formats = listOf(SubtitleFormat(ext = "dfxp", url = captionsUrl)),
        )
    }
    val images = item.obj("images")
    return InfoDict(
        id = videoId,
        title = item.str("fulltitle") ?: item.str("title"),
        description = item.str("dek"),
        duration = item.number("duration"),
        uploadDate = item.number("timestamp")?.let {
            ExtractorUtils.epochSecondsToDate((it / 1000).toLong())
        },
        isLive = item.str("type") == "live",
        thumbnails = listOfNotNull(
            (images?.str("hd") ?: images?.str("sd"))?.let { Thumbnail(url = it) },
        ),
        formats = formats,
        subtitles = subtitles,
        redirectUrl = redirectUrl,
        webpageUrl = url,
        extractor = "cbsnews",
        extractorKey = "CBSNews",
    )
}

private suspend fun extractLive(http: ExtractorHttp, videoId: String, url: String): InfoDict {
    val response = http.downloadJson(
        "https://feeds-cbsn.cbsnews.com/2.0/rundown/?partner=cbsnsite&edition=$videoId&type=live",
    ) as? JsonObject ?: throw ExtractionError.Malformed("The rundown API was not an object.")
    val data = response.obj("navigation")?.array("data")?.firstOrNull() as? JsonObject
        ?: throw ExtractionError.Unavailable("The livestream is not available.")
    val videoUrl = data.str("videoUrlDAI") ?: data.str("videoUrl") ?: data.obj("base")?.str("url")
        ?: throw ExtractionError.Unavailable("The livestream is not available.")
    return InfoDict(
        id = videoId,
        title = data.str("headline"),
        description = data.str("rundown_slug"),
        isLive = true,
        thumbnails = listOfNotNull(
            data.obj("images")?.str("thumbnail_url_hd")?.let { Thumbnail(url = it) },
        ),
        formats = listOf(
            MediaFormat(formatId = "hls", url = videoUrl, ext = "mp4", protocol = "m3u8_native"),
        ),
        webpageUrl = url,
        extractor = "cbsnews:live",
        extractorKey = "CBSNewsLive",
    )
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
