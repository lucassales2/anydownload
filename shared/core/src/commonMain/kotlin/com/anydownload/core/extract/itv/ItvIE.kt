/*
 * ITV extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `itv.py` from
 * `yt_dlp/extractor/itv.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `itv.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the ITV Hub `data-video-*` params, the featureset selection
 * (aes/hls, last platform tag first), the playlist POST with the public
 * `hmac` header, the MediaFiles into HLS/direct rows, the outband-webvtt
 * subtitle call, the posterframe/og thumbnails, and the BTCC/news Next.js
 * Brightcove entries. The JSON-LD merge and the Brightcove smuggle payload
 * (geo IP blocks/referrer) are not translated; the geo headers bypass is not
 * applied. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.itv

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

/** Upstream `ITVIE`: an itv.com/hub episode. */
class ITVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val paramsTag = Regex("(?s)<[^>]+id=\"video\"[^>]*>").find(webpage)
            ?.value ?: throw ExtractionError.Malformed("The ITV page had no video params.")
        val params = tagAttributes(paramsTag)
        val variants = params["data-video-variants"]?.let {
            ExtractorUtils.parseJson(it) as? JsonObject
        } ?: JsonObject(emptyMap())

        val videoChoice = chooseFeatureset(variants) { featureset ->
            featureset.take(2).mapNotNull { (it as? JsonPrimitive)?.content }.toSet() == setOf("aes", "hls")
        } ?: throw ExtractionError.Unavailable("No downloads available.")
        val playlistUrl = params["data-video-playlist"] ?: params["data-video-id"]
            ?: throw ExtractionError.Malformed("The ITV page had no playlist URL.")
        val hmac = params["data-video-hmac"]
            ?: throw ExtractionError.Malformed("The ITV page had no hmac.")
        val headers = mapOf(
            "Accept" to "application/vnd.itv.vod.playlist.v2+json",
            "Content-Type" to "application/json",
            "hmac" to hmac.uppercase(),
        )

        val playlist = callApi(videoId, playlistUrl, headers, videoChoice.first, videoChoice.second)
        val videoData = playlist.obj("Playlist")?.obj("Video") ?: JsonObject(emptyMap())
        val base = videoData.str("Base")
        val formats = mutableListOf<MediaFormat>()
        for (element in videoData.array("MediaFiles").orEmpty()) {
            val file = element as? JsonObject ?: continue
            var href = file.str("Href") ?: continue
            if (base != null) href = base + href
            if (ExtractorUtils.determineExt(href) == "m3u8") {
                formats += MediaFormat(
                    formatId = "hls",
                    url = href,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
            } else {
                formats += MediaFormat(url = href, ext = ExtractorUtils.determineExt(href))
            }
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        chooseFeatureset(variants) { featureset ->
            (featureset.getOrNull(2) as? JsonPrimitive)?.content == "outband-webvtt"
        }?.let { (platformTag, featureset) ->
            val subsPlaylist = try {
                callApi(videoId, playlistUrl, headers, platformTag, featureset)
            } catch (error: ExtractionError) {
                null
            }
            val hrefs = subsPlaylist?.obj("Playlist")?.obj("Video")?.array("Subtitles").orEmpty()
                .mapNotNull { (it as? JsonObject)?.str("Href") }
                .filter { it.startsWith("http://") || it.startsWith("https://") }
            if (hrefs.isNotEmpty()) {
                subtitles += SubtitleTrack(
                    language = "en",
                    formats = hrefs.map { SubtitleFormat(ext = "vtt", url = it) },
                )
            }
        }

        val thumbnails = mutableListOf<Thumbnail>()
        params["data-video-posterframe"]?.let { posterframe ->
            thumbnails += Thumbnail(
                url = posterframe
                    .replace("{width}", "1920")
                    .replace("{height}", "1080")
                    .replace("{quality}", "100")
                    .replace("{blur}", "0")
                    .replace("{bg}", "false"),
                width = 1920,
                height = 1080,
            )
            thumbnails += Thumbnail(
                url = baseUrl(posterframe) + urlBasename(posterframe),
                preference = -2,
            )
        }
        ExtractorUtils.htmlSearchMeta(webpage, "og:image")?.let { thumbnails += Thumbnail(url = it) }
        return InfoDict(
            id = videoId,
            title = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "twitter:title"),
            description = cleanHtml(elementByClass(webpage, "episode-info__synopsis")),
            duration = ExtractorUtils.parseDuration(videoData.str("Duration")),
            thumbnails = thumbnails.distinctBy { it.url },
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "itv",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_call_api`: the playlist POST with the public hmac header. */
    private suspend fun callApi(
        videoId: String,
        playlistUrl: String,
        headers: Map<String, String>,
        platformTag: String,
        featureset: JsonArray,
    ): JsonObject {
        val body = "{\"user\":{\"itvUserId\":\"\",\"entitlements\":[],\"token\":\"\"}," +
            "\"device\":{\"manufacturer\":\"Safari\",\"model\":\"5\",\"os\":{\"name\":\"Windows NT\"," +
            "\"version\":\"6.1\",\"type\":\"desktop\"}}," +
            "\"client\":{\"version\":\"4.1\",\"id\":\"browser\"}," +
            "\"variantAvailability\":{\"featureset\":{\"min\":$featureset,\"max\":$featureset}," +
            "\"platformTag\":\"$platformTag\"}}"
        return http.downloadJson(
            playlistUrl,
            method = "POST",
            headers = headers,
            body = body.encodeToByteArray(),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The ITV playlist API was not an object.")
    }

    /** Upstream `next((platform_tag, featureset) for reversed variants ...)`. */
    private fun chooseFeatureset(
        variants: JsonObject,
        predicate: (JsonArray) -> Boolean,
    ): Pair<String, JsonArray>? {
        val entries = variants.entries.toList().asReversed()
        for ((platformTag, featuresets) in entries) {
            for (element in (featuresets as? JsonArray).orEmpty()) {
                val featureset = element as? JsonArray ?: continue
                if (predicate(featureset)) return platformTag to featureset
            }
        }
        return null
    }

    companion object {
        const val IE_KEY: String = "ITV"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?itv\\.com/hub/[^/]+/(?<id>[0-9a-zA-Z]+)",
        )
    }
}

/** Upstream `ITVBTCCIE`: an itv.com news/btcc article with Brightcove embeds. */
class ITVBTCCIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val content = nextJsData(webpage)
            ?.obj("props")?.obj("pageProps")?.obj("article")?.obj("body")?.array("content")
            .orEmpty()
        val entries = mutableListOf<InfoEntry>()
        for (element in content) {
            val video = element as? JsonObject ?: continue
            val data = video.obj("data") ?: continue
            val isBrightcove = data.str("name") == "Brightcove" || data.str("type") == "Brightcove"
            if (!isBrightcove) continue
            val videoId = data.primitive("id") ?: continue
            val accountId = data.str("accountId") ?: continue
            val playerId = data.str("playerId") ?: continue
            entries += InfoEntry(
                id = videoId,
                url = "http://players.brightcove.net/$accountId/${playerId}_default/index.html" +
                    "?videoId=$videoId",
            )
        }
        return InfoDict(
            id = playlistId,
            title = ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
            entries = entries,
            webpageUrl = url,
            extractor = "itv:btcc",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ITVBTCC"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?itv\\.com/(?:news|btcc)/(?:[^/]+/)*(?<id>[^/?#&]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun nextJsData(webpage: String): JsonObject? {
    val body = Regex("(?s)<script[^>]+id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>")
        .find(webpage)?.groupValues?.get(1) ?: return null
    return ExtractorUtils.parseJson(body) as? JsonObject
}

/** Upstream `get_element_by_class`, tag-balanced for nested elements. */
private fun elementByClass(webpage: String, className: String): String? {
    val open = Regex(
        "(?s)<(\\w+)\\b[^>]+class\\s*=\\s*[\"'](?:[\\w-]+\\s+)*?" +
            Regex.escape(className) + "(?:\\s+[\\w-]+)*[\"'][^>]*>",
    ).find(webpage) ?: return null
    val tag = open.groupValues[1]
    val start = open.range.last + 1
    var depth = 1
    var index = start
    val tagRegex = Regex("</?$tag\\b[^>]*>", RegexOption.IGNORE_CASE)
    while (index < webpage.length) {
        val match = tagRegex.find(webpage, index) ?: break
        if (match.value.startsWith("</")) {
            depth--
            if (depth == 0) return webpage.substring(start, match.range.first)
        } else {
            depth++
        }
        index = match.range.last + 1
    }
    return null
}

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), " "))
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

/** Upstream `base_url` for the posterframe fallback. */
private fun baseUrl(url: String): String = url.substringBeforeLast('/', "") + "/"

/** Upstream `url_basename`. */
private fun urlBasename(url: String): String = url.substringAfterLast('/')

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

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
