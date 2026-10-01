/*
 * MX Player extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `mxplayer.py` from
 * `yt_dlp/extractor/mxplayer.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `mxplayer.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the page `window.__mxs__` data (third-party and mxplay HLS/DASH
 * streams, metadata, thumbnails), the season paged API entries (five pages
 * eagerly), the show season entries, and the SEO redirect resolver. DRM-
 * protected streams fail typed; cast/creator/genre/tag label lists the port
 * does not carry are dropped. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.mxplayer

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

/** Upstream `MxplayerIE`: a movie, episode, or short. */
class MxplayerIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val mxs = extractMxs(http, url, videoId)
        val config = mxs.obj("config")
        val cdnBase = config?.str("videoCdnBaseUrl")?.trimEnd('/')?.plus("/") ?: ""
        val imgBase = config?.str("imageBaseUrl")?.trimEnd('/')?.plus("/") ?: ""
        val entities = mxs.obj("entities")?.obj(videoId)
            ?: throw ExtractionError.Malformed("The page had no entity data.")
        val stream = entities.obj("stream")
        if (stream?.bool("drmProtect") == true) {
            throw ExtractionError.Unavailable("This MX Player stream is DRM protected.")
        }
        val manifestUrls = linkedSetOf<String>()
        val thirdParty = stream?.get("thirdParty")
        when (thirdParty) {
            is JsonArray -> thirdParty.forEach { (it as? JsonPrimitive)?.content?.let(manifestUrls::add) }
            is JsonObject -> thirdParty.values.forEach { (it as? JsonPrimitive)?.content?.let(manifestUrls::add) }
            is JsonPrimitive -> manifestUrls += thirdParty.content
            else -> Unit
        }
        for (key in listOf("hls", "dash")) {
            stream?.obj("mxplay")?.obj(key)?.str("high")?.let { manifestUrls += cdnBase + it }
        }
        val formats = mutableListOf<MediaFormat>()
        for (manifestUrl in manifestUrls) {
            when (ExtractorUtils.determineExt(manifestUrl)) {
                "m3u8" -> formats += MediaFormat(
                    formatId = "hls",
                    url = manifestUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                "mpd" -> formats += MediaFormat(
                    formatId = "dash",
                    url = manifestUrl,
                    ext = "mp4",
                    protocol = "mpd",
                )

                else -> Unit
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The page returned no playable stream.")
        }
        val thumbnails = mutableListOf<Thumbnail>()
        for (key in listOf("imageInfo", "titleContentImageInfo")) {
            for (element in entities.obj(key)?.values.orEmpty()) {
                val image = element as? JsonObject ?: continue
                val imageUrl = image.str("url") ?: continue
                thumbnails += Thumbnail(
                    id = image.str("type"),
                    url = imgBase + imageUrl,
                    width = image.number("width")?.toLong(),
                    height = image.number("height")?.toLong(),
                )
            }
        }
        return InfoDict(
            id = videoId,
            title = cleanHtml(entities.str("title")),
            description = cleanHtml(entities.str("description")),
            duration = entities.number("duration"),
            ageLimit = entities.number("rating")?.toInt(),
            uploadDate = dateFromIso(entities.str("publishTime") ?: entities.str("releaseDate")),
            viewCount = entities.number("viewCount")?.toLong(),
            thumbnails = thumbnails,
            formats = formats,
            webpageUrl = url,
            extractor = "mxplayer",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Mxplayer"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?mxplayer\\.in/" +
                "(?:movie|shorts|show/[\\w-]+/(?!seasons/)[\\w-]+)/" +
                "(?<displayId>[\\w-]+)-(?<id>[0-9a-f]{32})(?:[/?#]|$)",
        )
    }
}

/** Upstream `MxplayerSeasonIE`: a season's episode list. */
class MxplayerSeasonIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val seasonId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val mxs = extractMxs(http, url, seasonId)
        val entities = mxs.obj("entities")?.obj(seasonId)
            ?: throw ExtractionError.Malformed("The page had no season entity.")
        val apiUrl = entities.array("tabs").orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstNotNullOfOrNull { tab ->
                tab.str("api")?.let { api ->
                    if (api.startsWith("http")) api else "https://api.mxplayer.in/v1/web/$api"
                }
            }
        val entries = mutableListOf<InfoEntry>()
        if (apiUrl != null) {
            var nextQuery: String? = null
            var page = 0
            while (page < MAX_PAGES) {
                page++
                val response = try {
                    http.downloadJson(apiUrl + (nextQuery?.let { "?$it" } ?: "")) as? JsonObject
                } catch (error: ExtractionError) {
                    null
                } ?: break
                val items = response.array("items").orEmpty()
                for (element in items) {
                    val shareUrl = (element as? JsonObject)?.str("shareUrl") ?: continue
                    entries += InfoEntry(
                        url = if (shareUrl.startsWith("http")) shareUrl else "https://www.mxplayer.in/" + shareUrl.trimStart('/'),
                    )
                }
                if (items.size < PAGE_SIZE) break
                nextQuery = response.obj("next")?.entries?.joinToString("&") { (k, v) ->
                    "$k=${(v as? JsonPrimitive)?.content ?: ""}"
                } ?: break
            }
        }
        return InfoDict(
            id = seasonId,
            title = listOfNotNull(
                cleanHtml(entities.obj("container")?.str("title")),
                cleanHtml(entities.str("title")),
            ).joinToString(" - ").takeIf { it.isNotEmpty() },
            entries = entries,
            webpageUrl = url,
            extractor = "mxplayer:season",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MxplayerSeason"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?mxplayer\\.in/show/[\\w-]+/seasons/[\\w-]+-(?<id>[0-9a-f]{32})(?:[/?#]|$)",
        )

        private const val PAGE_SIZE = 20
        private const val MAX_PAGES = 5
    }
}

/** Upstream `MxplayerShowIE`: a show's season list. */
class MxplayerShowIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val showId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val mxs = extractMxs(http, url, showId)
        val entities = mxs.obj("entities")?.obj(showId)
            ?: throw ExtractionError.Malformed("The page had no show entity.")
        val entries = mutableListOf<InfoEntry>()
        for (tab in entities.array("tabs").orEmpty()) {
            for (element in (tab as? JsonObject)?.array("containers").orEmpty()) {
                val container = element as? JsonObject ?: continue
                val seasonId = container.primitiveText("id") ?: continue
                entries += InfoEntry(url = "https://www.mxplayer.in/detail/season/$seasonId")
            }
        }
        return InfoDict(
            id = showId,
            title = cleanHtml(entities.str("title")),
            entries = entries,
            webpageUrl = url,
            extractor = "mxplayer:show",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MxplayerShow"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?mxplayer\\.in/show/[\\w-]+-(?<id>[0-9a-f]{32})(?:[/?#]|$)",
        )
    }
}

/** Upstream `MxplayerRedirectIE`: the /detail/<type>/<id> resolver. */
class MxplayerRedirectIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val redirectId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val path = "/" + url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
        val detail = http.downloadJson(
            "https://seo.mxplayer.in/v1/api/seo/get-url-details?url=$path",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The SEO API was not an object.")
        val redirectUrl = detail.obj("data")?.str("redirect")
            ?: throw ExtractionError.Malformed("Unable to resolve the redirect URL.")
        return InfoDict(
            id = redirectId,
            redirectUrl = if (redirectUrl.startsWith("http")) redirectUrl else "https://www.mxplayer.in/" + redirectUrl.trimStart('/'),
            webpageUrl = url,
            extractor = "mxplayer:redirect",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MxplayerRedirect"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?mxplayer\\.in/detail/(?<type>episode|movie|season|shorts|tvshow)/" +
                "(?<id>[0-9a-f]{32})(?:[/?#]|$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun extractMxs(http: ExtractorHttp, url: String, itemId: String): JsonObject {
    val webpage = http.downloadWebpage(url)
    if (Regex("class=\"[^\"]*sub-message").containsMatchIn(webpage)) {
        throw ExtractionError.GeoRestricted()
    }
    val raw = balancedAfter(webpage, Regex("window\\.__mxs__\\s*="))
        ?: throw ExtractionError.Malformed("The page had no __mxs__ data.")
    return ExtractorUtils.parseJson(raw) as? JsonObject
        ?: throw ExtractionError.Malformed("The __mxs__ data was not an object.")
}

private fun balancedAfter(html: String, marker: Regex): String? {
    val match = marker.find(html) ?: return null
    val start = html.indexOf('{', match.range.last + 1)
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
                    if (depth == 0) return html.substring(start, i + 1)
                }
            }
        }
        i++
    }
    return null
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

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

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.let {
        when (it.content) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
