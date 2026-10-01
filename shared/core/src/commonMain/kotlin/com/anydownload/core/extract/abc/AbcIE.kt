/*
 * ABC (Australia) extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `abc.py` from
 * `yt_dlp/extractor/abc.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `abc.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the abc.net.au news/btn/listen page scans (direct audio links,
 * YouTube embeds, and the sources/files/renditions or inline data JSON) and
 * the iview show-series page scan. `ABCIViewIE` matches and fails typed:
 * the iview HLS URL needs an HMAC-SHA256 `hdnea` token and the port does
 * not add an HMAC helper. No cookie, token, or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.abc

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `ABCIE`: the abc.net.au news/btn/listen pages. */
class ABCIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val directAudio = Regex(
            "<a\\s+href=\"([^\"]+)\"\\s+data-duration=\"\\d+\"\\s+title=\"Download audio directly\">",
        ).find(webpage)
        if (directAudio != null) {
            val audioUrl = directAudio.groupValues[1]
            return InfoDict(
                id = videoId,
                title = ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
                description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
                thumbnails = listOfNotNull(
                    ExtractorUtils.htmlSearchMeta(webpage, "og:image")?.let { Thumbnail(url = it) },
                ),
                formats = listOf(
                    MediaFormat(
                        url = audioUrl,
                        ext = ExtractorUtils.determineExt(audioUrl),
                        vcodec = MediaFormat.CODEC_NONE,
                    ),
                ),
                webpageUrl = url,
                extractor = "abc.net.au",
                extractorKey = IE_KEY,
            )
        }
        val youtube = Regex(
            "<a href=\"(http://www\\.youtube\\.com/watch\\?v=[^\"]+)\"><span><strong>External Link:</strong>",
        ).find(webpage)?.groupValues?.get(1)
            ?: Regex("<iframe width=\"100%\" src=\"(//www\\.youtube-nocookie\\.com/embed/[^?\"#]+)")
                .find(webpage)?.groupValues?.get(1)
        if (youtube != null) {
            val youtubeUrl = if (youtube.startsWith("//")) "https:$youtube" else youtube
            return InfoDict(
                id = videoId,
                entries = listOf(InfoEntry(url = youtubeUrl)),
                webpageUrl = url,
                extractor = "abc.net.au",
                extractorKey = IE_KEY,
            )
        }
        val json = Regex("(?:\"sources\"|\"files\"|\"renditions\")\\s*:\\s*(\\[[^\\]]+\\])")
            .find(webpage)?.groupValues?.get(1)
            ?: Regex("inline(?:Video|Audio|YouTube)Data\\.push\\(([^)]+)\\);")
                .find(webpage)?.groupValues?.get(1)?.let { ExtractorUtils.jsToJson(it) }
            ?: throw ExtractionError.Unavailable("Unable to extract the ABC video URLs.")
        val parsed = ExtractorUtils.parseJson(json) as? JsonArray
            ?: throw ExtractionError.Malformed("The ABC video data was not a list.")
        val formats = mutableListOf<MediaFormat>()
        for (element in parsed) {
            val info = element as? JsonObject ?: continue
            val mediaUrl = info.str("url") ?: continue
            var height = info.number("height")?.toLong()
            var bitrate = info.number("bitrate")?.toLong()
            var width = info.number("width")?.toLong()
            var formatId: String? = null
            val match = Regex("_(?:(\\d+)|(\\d+)k)\\.mp4$").find(mediaUrl)
            if (match != null) {
                val heightFromUrl = match.groupValues[1].ifEmpty { null }
                if (heightFromUrl != null) {
                    height = height ?: heightFromUrl.toLongOrNull()
                    width = width ?: info.primitiveText("label")?.toLongOrNull()
                } else {
                    bitrate = bitrate ?: match.groupValues[2].toLongOrNull()
                    formatId = info.primitiveText("label")
                }
            }
            formats += MediaFormat(
                formatId = formatId,
                url = mediaUrl,
                vcodec = info.str("codec"),
                width = width,
                height = height,
                tbr = bitrate?.toDouble(),
                filesize = info.number("filesize")?.toLong(),
            )
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The ABC page carried no playable format.")
        }
        return InfoDict(
            id = videoId,
            title = ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
            thumbnails = listOfNotNull(
                ExtractorUtils.htmlSearchMeta(webpage, "og:image")?.let { Thumbnail(url = it) },
            ),
            formats = formats,
            webpageUrl = url,
            extractor = "abc.net.au",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ABC"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?abc\\.net\\.au/(?:news|btn|listen)/(?:[^/?#]+/){1,4}(?<id>\\d{5,})",
        )
    }
}

/** Upstream `ABCIViewIE`: the iview video pages (HMAC-token wall). */
class ABCIViewIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(
        "The iview HLS URL needs an HMAC-SHA256 hdnea token; the port does not add an HMAC helper.",
    )

    companion object {
        const val IE_KEY: String = "ABCIView"

        val VALID_URL: Regex = Regex("https?://iview\\.abc\\.net\\.au/(?:[^/]+/)*video/(?<id>[^/?#]+)")
    }
}

/** Upstream `ABCIViewShowSeriesIE`: the iview show/series pages. */
class ABCIViewShowSeriesIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val showId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val raw = Regex(
            "window\\.__INITIAL_STATE__\\s*=\\s*['\"](.+?)['\"]\\s*;",
            RegexOption.DOT_MATCHES_ALL,
        ).find(webpage)?.groupValues?.get(1)
            ?: throw ExtractionError.Malformed("The iview page had no initial state.")
        val state = ExtractorUtils.parseJson(unescapeJsString(raw)) as? JsonObject
            ?: throw ExtractionError.Malformed("The iview initial state was not an object.")
        val embedded = state.obj("route")?.obj("pageData")?.obj("_embedded")
            ?: throw ExtractionError.Malformed("The iview page had no embedded data.")
        val highlight = embedded.obj("highlightVideo")?.str("shareUrl")
        if (highlight != null) {
            return InfoDict(
                id = showId,
                redirectUrl = highlight,
                webpageUrl = url,
                extractor = "abc.net.au:iview:showseries",
                extractorKey = IE_KEY,
            )
        }
        val series = embedded.obj("selectedSeries")
            ?: throw ExtractionError.Malformed("The iview page had no selected series.")
        val episodes = mutableListOf<InfoEntry>()
        val videoEpisodes = series.obj("_embedded")?.get("videoEpisodes")
        val items = when (videoEpisodes) {
            is JsonArray -> videoEpisodes
            is JsonObject -> videoEpisodes.array("items")
            else -> null
        }
        for (element in items.orEmpty()) {
            val shareUrl = (element as? JsonObject)?.str("shareUrl") ?: continue
            episodes += InfoEntry(url = shareUrl)
        }
        val thumbnail = series.str("thumbnail")
            ?: series.array("images").orEmpty()
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.str("name") == "seriesThumbnail" }
                ?.str("url")
        return InfoDict(
            id = series.primitiveText("id") ?: showId,
            title = series.str("title") ?: series.str("displaySubtitle"),
            description = series.str("description"),
            uploader = series.str("showTitle") ?: series.str("displayTitle"),
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            entries = episodes,
            webpageUrl = url,
            extractor = "abc.net.au:iview:showseries",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ABCIViewShowSeries"

        val VALID_URL: Regex = Regex("https?://iview\\.abc\\.net\\.au/show/(?<id>[^/]+)(?:/series/\\d+)?$")
    }
}

// ------------------------------------------------------------------ helpers

private fun unescapeJsString(value: String): String {
    val out = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '\\' && i + 1 < value.length) {
            when (val next = value[i + 1]) {
                'u' -> {
                    val hex = value.substring(i + 2, minOf(i + 6, value.length))
                    val code = hex.toIntOrNull(16)
                    if (code != null && hex.length == 4) {
                        out.append(code.toChar())
                        i += 6
                        continue
                    }
                }

                'n' -> {
                    out.append('\n')
                    i += 2
                    continue
                }

                '"' -> {
                    out.append('"')
                    i += 2
                    continue
                }

                '\\' -> {
                    out.append('\\')
                    i += 2
                    continue
                }
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
