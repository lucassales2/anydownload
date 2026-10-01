/*
 * Radio France extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `radiofrance.py` from
 * `yt_dlp/extractor/radiofrance.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `radiofrance.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the radiovisions audio players, the France Culture JSON-LD audio,
 * the public live API, the podcast/profile playlist APIs (five pages
 * eagerly), and the programme grid. The playlist entries keep their URLs and
 * titles; per-entry duration fields the port does not carry are dropped. No
 * cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.radiofrance

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

private const val STATIONS_RE =
    "franceculture|franceinfo|franceinter|francemusique|fip|mouv"

/** Upstream `RadioFranceIE`: the maison radiovisions pages. */
class RadioFranceIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val formatsStr = ExtractorUtils.searchRegex(
            "class=\"jp-jplayer[^\"]*\" data-source=\"([^\"]+)\">",
            webpage,
            default = null,
        ) ?: throw ExtractionError.NoFormats("The radiovision page had no audio sources.")
        val formats = Regex("([a-z0-9]+)\\s*:\\s*'([^']+)'").findAll(formatsStr)
            .mapIndexed { index, match ->
                MediaFormat(
                    formatId = match.groupValues[1],
                    url = match.groupValues[2],
                    vcodec = MediaFormat.CODEC_NONE,
                    preference = index,
                )
            }.toList()
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The radiovision page had no playable source.")
        }
        return InfoDict(
            id = videoId,
            title = ExtractorUtils.searchRegex("<h1>(.*?)</h1>", webpage, default = null),
            description = ExtractorUtils.searchRegex(
                "<div class=\"bloc_page_wrapper\"><div class=\"text\">(.*?)</div>",
                webpage,
                default = null,
            ),
            uploader = ExtractorUtils.searchRegex(
                "<div class=\"credit\">&nbsp;&nbsp;&copy;&nbsp;(.*?)</div>",
                webpage,
                default = null,
            ),
            formats = formats,
            webpageUrl = url,
            extractor = "radiofrance",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RadioFrance"

        val VALID_URL: Regex = Regex("https?://maison\\.radiofrance\\.fr/radiovisions/(?<id>[^?#]+)")
    }
}

/** Upstream `FranceCultureIE`: the podcast episode pages. */
class FranceCultureIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["displayId"]?.value ?: videoId
        val webpage = http.downloadWebpage(url)
        val audioJson = findAudioObject(webpage)
            ?: throw ExtractionError.Malformed("The episode page had no AudioObject data.")
        val contentUrl = audioJson.str("contentUrl")
            ?: throw ExtractionError.NoFormats("The AudioObject had no content URL.")
        val isMp3 = audioJson.str("encodingFormat") == "mp3"
        return InfoDict(
            id = videoId,
            title = ExtractorUtils.searchRegex(
                "(?s)<h1[^>]*itemprop=\"[^\"]*name[^\"]*\"[^>]*>(.+?)</h1>",
                webpage,
                default = null,
            )?.let(::cleanHtml) ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title"),
            description = ExtractorUtils.searchRegex(
                "(?s)<meta name=\"description\"\\s*content=\"([^\"]+)",
                webpage,
                default = null,
            ) ?: ExtractorUtils.htmlSearchMeta(webpage, "description"),
            duration = ExtractorUtils.parseDuration(audioJson.str("duration"))?.toDouble(),
            uploadDate = ExtractorUtils.unifiedStrdate(
                ExtractorUtils.searchRegex("\"datePublished\"\\s*:\\s*\"([^\"]+)", webpage, default = null),
            ),
            uploader = ExtractorUtils.searchRegex(
                "(?s)<span class=\"author\">(.*?)</span>",
                webpage,
                default = null,
            )?.let(::cleanHtml),
            thumbnails = listOfNotNull(
                ExtractorUtils.htmlSearchMeta(webpage, "og:image")?.let { Thumbnail(url = it) },
            ),
            formats = listOf(
                MediaFormat(
                    url = contentUrl,
                    ext = ExtractorUtils.determineExt(contentUrl),
                    vcodec = if (isMp3) MediaFormat.CODEC_NONE else null,
                ),
            ),
            webpageUrl = url,
            extractor = "franceculture",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "FranceCulture"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?radiofrance\\.fr/(?:$STATIONS_RE)/podcasts/(?:[^?#]+/)?" +
                "(?<displayId>[^?#]+)-(?<id>\\d{6,})(?:$|[?#])",
        )
    }
}

/** Upstream `RadioFranceLiveIE`: the station live streams. */
class RadioFranceLiveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val stationId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val substationId = match.groups["substationId"]?.value
        val apiResponse = if (substationId != null) {
            extractDataFromWebpage(http.downloadWebpage(url), "webRadioData")
                ?: throw ExtractionError.Malformed("The station page had no web radio data.")
        } else {
            http.downloadJson("https://www.radiofrance.fr/$stationId/api/live") as? JsonObject
                ?: throw ExtractionError.Malformed("The live API was not an object.")
        }
        val media = apiResponse.obj("now")?.obj("media") ?: apiResponse.obj("media")
        val formats = mutableListOf<MediaFormat>()
        for (element in media?.array("sources").orEmpty()) {
            val source = element as? JsonObject ?: continue
            val sourceUrl = source.str("url") ?: continue
            formats += if (source.str("format") == "hls") {
                MediaFormat(formatId = "hls", url = sourceUrl, ext = "mp4", protocol = "m3u8_native")
            } else {
                MediaFormat(url = sourceUrl, abr = source.number("bitrate"))
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The live API returned no stream.")
        }
        val title = apiResponse.obj("visual")?.str("legend")
            ?: listOfNotNull(
                apiResponse.obj("now")?.obj("firstLine")?.str("title"),
                apiResponse.obj("now")?.obj("secondLine")?.str("title"),
            ).joinToString(" - ").takeIf { it.isNotEmpty() }
        return InfoDict(
            id = listOfNotNull(stationId, substationId).joinToString("-"),
            title = title,
            isLive = true,
            formats = formats,
            webpageUrl = url,
            extractor = "radiofrance:live",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RadioFranceLive"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?radiofrance\\.fr/(?<id>$STATIONS_RE)/?(?<substationId>radio-[\\w-]+)?(?:[#?]|$)",
        )
    }
}

/** Upstream `RadioFrancePodcastIE`: the podcast playlist pages. */
class RadioFrancePodcastIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractPlaylist(http, url, "expressions", "concepts", "pageCursor", IE_KEY)

    companion object {
        const val IE_KEY: String = "RadioFrancePodcast"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?radiofrance\\.fr/(?:$STATIONS_RE)/podcasts/(?<id>[\\w-]+)/?(?:[?#]|$)",
        )
    }
}

/** Upstream `RadioFranceProfileIE`: the people pages. */
class RadioFranceProfileIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractPlaylist(http, url, "documents", "taxonomy", "cursor", IE_KEY, relation = "personality")

    companion object {
        const val IE_KEY: String = "RadioFranceProfile"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?radiofrance\\.fr/personnes/(?<id>[\\w-]+)")
    }
}

/** Upstream `RadioFranceProgramScheduleIE`: the programme grids. */
class RadioFranceProgramScheduleIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val station = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val grid = extractDataFromWebpage(webpage, "grid")
            ?: throw ExtractionError.Malformed("The programme grid page had no grid data.")
        val entries = mutableListOf<InfoEntry>()
        for (element in grid.array("steps").orEmpty()) {
            val step = element as? JsonObject ?: continue
            val expression = step.obj("expression") ?: continue
            val path = expression.str("path") ?: continue
            entries += InfoEntry(
                title = expression.str("title"),
                url = "https://www.radiofrance.fr/$path",
            )
        }
        val uploadDate = grid.number("date")?.toLong()?.let(ExtractorUtils::epochSecondsToDate)
        return InfoDict(
            id = listOfNotNull(station, "program", uploadDate).joinToString("-"),
            uploadDate = uploadDate,
            entries = entries,
            webpageUrl = url,
            extractor = "radiofrance:program",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RadioFranceProgramSchedule"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?radiofrance\\.fr/(?<id>$STATIONS_RE)/grille-programmes",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun extractPlaylist(
    http: ExtractorHttp,
    url: String,
    metadataKey: String,
    endpoint: String,
    cursorParam: String,
    ieKey: String,
    relation: String? = null,
): InfoDict {
    val displayId = url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
    val pathValue = "/" + url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
    val metadataResponse = http.downloadJson(
        "https://www.radiofrance.fr/api/v2.1/path?value=$pathValue",
    ) as? JsonObject ?: throw ExtractionError.Malformed("The path API was not an object.")
    val metadata = metadataResponse.obj("content")
        ?: throw ExtractionError.Malformed("The path API had no content.")
    val contentId = metadata.primitiveText("id") ?: displayId
    val entries = mutableListOf<InfoEntry>()
    var contentResponse = metadata.obj(metadataKey)
    var pages = 0
    while (contentResponse != null && pages < MAX_PAGES) {
        pages++
        for (element in contentResponse.array("items").orEmpty()) {
            val item = element as? JsonObject ?: continue
            val path = item.str("path") ?: continue
            entries += InfoEntry(
                title = item.str("title"),
                url = "https://www.radiofrance.fr/$path",
            )
        }
        val next = contentResponse.obj("pagination")?.primitiveText("next")
            ?: contentResponse.primitiveText("next") ?: break
        val query = if (relation != null) {
            "relation=$relation&$cursorParam=$next"
        } else {
            "$cursorParam=$next"
        }
        contentResponse = try {
            http.downloadJson("https://www.radiofrance.fr/api/v2.1/$endpoint/$contentId/$metadataKey?$query")
                as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
    }
    return InfoDict(
        id = contentId,
        title = metadata.str("title") ?: metadata.str("name"),
        description = metadata.str("standFirst") ?: metadata.str("role"),
        thumbnails = listOfNotNull(
            metadata.obj("visual")?.str("src")?.let { Thumbnail(url = it) },
        ),
        entries = entries,
        webpageUrl = url,
        extractor = "radiofrance:playlist",
        extractorKey = ieKey,
    )
}

private const val MAX_PAGES = 5

/** Upstream `RadioFranceBaseIE._extract_data_from_webpage`. */
private fun extractDataFromWebpage(webpage: String, key: String): JsonObject? {
    val marker = Regex("\\bconst\\s+data\\s*=").find(webpage) ?: return null
    val start = webpage.indexOf('[', marker.range.last + 1)
    if (start < 0) return null
    val array = extractBalanced(webpage, start, '[', ']') ?: return null
    val parsed = ExtractorUtils.parseJson(array) as? JsonArray ?: return null
    for (element in parsed) {
        val data = (element as? JsonObject)?.obj("data")?.obj(key)
        if (data != null) return data
    }
    return null
}

private fun findAudioObject(webpage: String): JsonObject? {
    val marker = Regex("\\{\\s*\"@type\"\\s*:\\s*\"AudioObject\"").find(webpage) ?: return null
    val start = marker.range.first
    val json = extractBalanced(webpage, start, '{', '}') ?: return null
    return ExtractorUtils.parseJson(json) as? JsonObject
}

private fun extractBalanced(text: String, start: Int, open: Char, close: Char): String? {
    var depth = 0
    var inString = false
    var escaped = false
    var i = start
    while (i < text.length) {
        val c = text[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
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

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
