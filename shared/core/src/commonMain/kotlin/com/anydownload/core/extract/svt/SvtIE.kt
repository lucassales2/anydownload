/*
 * SVT extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `svt.py` from
 * `yt_dlp/extractor/svt.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `svt.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public videoplayer API (m3u8/mpd/plain references, subtitles
 * with the forced-track split, metadata, geo failure), the series GraphQL
 * listables, and the page urqlState scan. f4m references are skipped and
 * the series/episode number fields the port does not carry are dropped. No
 * cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.svt

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

/** Upstream `SVTPlayIE`: a video page. */
class SVTPlayIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value
        val svtId = match.groups["svtId"]?.value ?: match.groups["modalId"]?.value
        val webpage = if (videoId != null) {
            try {
                http.downloadWebpage(url)
            } catch (error: ExtractionError) {
                if (svtId == null) throw error else ""
            }
        } else {
            ""
        }
        val resolvedId = svtId ?: findSvtId(webpage)
            ?: throw ExtractionError.Malformed("Unable to extract the SVT ID.")
        val info = extractByVideoId(http, resolvedId).let { dict ->
            if (dict.title == null) {
                dict.copy(
                    title = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
                        ?.replace(Regex("\\s*\\|\\s*.+?$"), ""),
                )
            } else {
                dict
            }
        }.let { dict ->
            if (dict.description == null) {
                dict.copy(description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"))
            } else {
                dict
            }
        }
        return info.copy(
            thumbnails = listOfNotNull(
                ExtractorUtils.htmlSearchMeta(webpage, "og:image")?.let { Thumbnail(url = it) },
            ),
            webpageUrl = url,
        )
    }

    companion object {
        const val IE_KEY: String = "SVTPlay"

        val VALID_URL: Regex = Regex(
            "(?:(?:svt:|https?://(?:www\\.)?svt\\.se/barnkanalen/barnplay/[^/]+/)(?<svtId>[^/?#&]+)|" +
                "https?://(?:www\\.)?(?:svtplay|oppetarkiv)\\.se/(?:video|klipp|kanaler)/(?<id>[^/?#&]+)" +
                "(?:.*?(?:modalId|id)=(?<modalId>[\\da-zA-Z-]+))?)",
        )
    }
}

/** Upstream `SVTSeriesIE`: a series page. */
class SVTSeriesIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !SVTPlayIE.VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val seriesSlug = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seasonId = match.groups["seasonSlug"]?.value
        val query = """
            {
              listablesBySlug(slugs: ["$seriesSlug"]) {
                associatedContent(include: [productionPeriod, season]) {
                  items { item { ... on Episode { videoSvtId } } }
                  id
                  name
                }
                id
                longDescription
                name
                shortDescription
              }
            }
        """.trimIndent()
        val response = http.downloadJson(
            "https://api.svt.se/contento/graphql?query=${encodeQuery(query)}",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The series API was not an object.")
        val series = response.obj("data")?.array("listablesBySlug")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The series API returned no series.")
        var seasonName: String? = null
        val entries = mutableListOf<InfoEntry>()
        for (element in series.array("associatedContent").orEmpty()) {
            val season = element as? JsonObject ?: continue
            if (seasonId != null) {
                if (season.str("id") != seasonId) continue
                seasonName = season.str("name")
            }
            for (itemElement in season.array("items").orEmpty()) {
                val item = (itemElement as? JsonObject)?.obj("item") ?: continue
                val contentId = item.str("videoSvtId") ?: continue
                entries += InfoEntry(id = contentId, url = "svt:$contentId")
            }
        }
        val title = series.str("name")
        val seasonLabel = seasonName ?: seasonId
        val combinedTitle = when {
            title != null && seasonLabel != null -> "$title - $seasonLabel"
            seasonId != null -> seasonId
            else -> title
        }
        return InfoDict(
            id = seasonId ?: series.str("id"),
            title = combinedTitle,
            description = series.str("longDescription") ?: series.str("shortDescription"),
            entries = entries,
            webpageUrl = url,
            extractor = "svt:series",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "SVTSeries"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?svtplay\\.se/(?<id>[^/?&#]+)(?:.+?\\btab=(?<seasonSlug>[^&#]+))?",
        )
    }
}

/** Upstream `SVTPageIE`: the svt.se article pages. */
class SVTPageIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !SVTPlayIE.VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val title = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
        val urqlState = balancedAfter(webpage, Regex("urqlState\\s*[=:]"))
            ?: throw ExtractionError.Malformed("The page had no urqlState.")
        val data = ExtractorUtils.parseJson(urqlState) as? JsonObject
            ?: throw ExtractionError.Malformed("The urqlState was not an object.")
        val videoIds = linkedSetOf<String>()
        val page = data.values.mapNotNull { it as? JsonObject }.firstNotNullOfOrNull { entry ->
            val parsed = when (val raw = entry["data"]) {
                is JsonObject -> raw
                is JsonPrimitive -> ExtractorUtils.parseJson(raw.content) as? JsonObject
                else -> null
            }
            parsed?.obj("page")
        }
        page?.obj("topMedia")?.str("svtId")?.let { videoIds += it }
        page?.str("svtId")?.let { videoIds += it }
        for (element in page?.array("body").orEmpty()) {
            (element as? JsonObject)?.obj("video")?.str("svtId")?.let { videoIds += it }
        }
        val media = mutableListOf<com.anydownload.core.extract.InfoMedia>()
        for (videoId in videoIds) {
            val info = extractVideo(
                http.downloadJson("https://api.svt.se/video/$videoId") as? JsonObject
                    ?: throw ExtractionError.Malformed("The video API was not an object."),
                videoId,
            )
            media += com.anydownload.core.extract.InfoMedia(
                mediaId = videoId,
                title = title,
                formats = info.formats,
            )
        }
        return InfoDict(
            id = displayId,
            title = title,
            media = media,
            webpageUrl = url,
            extractor = "svt:page",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "SVTPage"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?svt\\.se/(?:[^/?#]+/)*(?<id>[^/?&#]+)")
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun extractByVideoId(http: ExtractorHttp, videoId: String): InfoDict {
    val data = http.downloadJson("https://api.svt.se/videoplayer-api/video/$videoId") as? JsonObject
        ?: throw ExtractionError.Malformed("The videoplayer API was not an object.")
    val info = extractVideo(data, videoId)
    return if (info.title == null) {
        info.copy(title = info.channel ?: info.description)
    } else {
        info
    }
}

private fun extractVideo(videoInfo: JsonObject, videoId: String): InfoDict {
    val isLive = videoInfo.primitiveText("live")?.lowercase() == "true" ||
        videoInfo.primitiveText("simulcast")?.lowercase() == "true"
    val formats = mutableListOf<MediaFormat>()
    val subtitles = mutableListOf<SubtitleTrack>()
    for (element in videoInfo.array("videoReferences").orEmpty()) {
        val reference = element as? JsonObject ?: continue
        val playerType = reference.str("playerType") ?: reference.str("format")
        val videoUrl = reference.str("url") ?: continue
        when (ExtractorUtils.determineExt(videoUrl)) {
            "m3u8" -> formats += MediaFormat(
                formatId = playerType,
                url = videoUrl,
                ext = "mp4",
                protocol = if (isLive) "m3u8" else "m3u8_native",
            )

            "mpd" -> formats += MediaFormat(
                formatId = playerType,
                url = videoUrl,
                ext = "mp4",
                protocol = "mpd",
            )

            "f4m" -> Unit // f4m is skipped: the port has no f4m helper.
            else -> formats += MediaFormat(formatId = playerType, url = videoUrl)
        }
    }
    val rights = videoInfo.obj("rights")
    if (formats.isEmpty() && rights?.primitiveText("geoBlockedSweden")?.lowercase() == "true") {
        throw ExtractionError.GeoRestricted()
    }
    if (formats.isEmpty()) {
        throw ExtractionError.NoFormats("The video API returned no playable reference.")
    }
    val subtitleReferences = videoInfo.array("subtitles") ?: videoInfo.array("subtitleReferences")
    for (element in subtitleReferences.orEmpty()) {
        val subtitle = element as? JsonObject ?: continue
        val subtitleUrl = subtitle.str("url") ?: continue
        val language = subtitle.str("language") ?: "sv"
        subtitles += SubtitleTrack(
            language = if (subtitleUrl.contains("text-open")) "$language-forced" else language,
            formats = listOf(SubtitleFormat(ext = "vtt", url = subtitleUrl)),
        )
    }
    val adult = videoInfo.primitiveText("inappropriateForChildren")
        ?: videoInfo.primitiveText("blockedForChildren")
    return InfoDict(
        id = videoId,
        title = videoInfo.str("title"),
        description = videoInfo.str("programTitle"),
        duration = videoInfo.number("materialLength")?.toLong()?.toDouble()
            ?: videoInfo.number("contentDuration")?.toLong()?.toDouble(),
        uploadDate = ExtractorUtils.unifiedStrdate(rights?.str("validFrom")),
        ageLimit = adult?.let { if (it == "true") 18 else 0 },
        isLive = isLive,
        formats = formats,
        subtitles = subtitles,
        extractor = "svt:play",
        extractorKey = "SVTPlay",
    )
}

private fun findSvtId(webpage: String): String? {
    val raw = balancedAfter(webpage, Regex("URQL_DATA\\s*=")) ?: return null
    val data = ExtractorUtils.parseJson(ExtractorUtils.jsToJson(raw)) as? JsonObject ?: return null
    for (element in data.values) {
        val value = (element as? JsonObject)?.obj("data") ?: continue
        val details = value.obj("detailsPageByPath") ?: continue
        details.obj("smartStart")?.str("videoSvtId")?.let { return it }
    }
    return null
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

private fun encodeQuery(value: String): String =
    value.replace("%", "%25").replace(" ", "%20").replace("\n", "%0A")
        .replace("\"", "%22").replace("{", "%7B").replace("}", "%7D")
        .replace(":", "%3A").replace(",", "%2C").replace("(", "%28").replace(")", "%29")
        .replace("[", "%5B").replace("]", "%5D")

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
