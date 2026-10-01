/*
 * YouPorn extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `youporn.py` from
 * `yt_dlp/extractor/youporn.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `youporn.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the watch/embed player vars (mp4 and master m3u8), the page
 * metadata, and the category/channel/collection/tag/pornstar/browse
 * listings. The JSON-LD merge, the display-id field, the comment count, and
 * the category/tag label lists are not carried; the listings walk at most
 * five pages eagerly. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.youporn

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `YouPornIE`: a single video. */
class YouPornIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage("https://www.youporn.com/watch/$videoId")
        val watchable = Regex(
            "<div\\s[^>]*\\bid\\s*=\\s*(?:\"watch-container\"|'watch-container'|watch-container(?!-)\\b)[^>]*>",
        ).containsMatchIn(webpage)
        if (!watchable) {
            throw ExtractionError.Unavailable("The video is unavailable.")
        }
        val playerVars = jsonAfterKey(webpage, "playervars")
            ?: throw ExtractionError.Malformed("The page had no player vars.")
        val definitions = playerVars.array("mediaDefinitions").orEmpty()
        val formats = mutableListOf<MediaFormat>()
        for (hls in getFormatData(http, definitions, "hls")) {
            val defaultQuality = hls["defaultQuality"]
            if (defaultQuality is JsonPrimitive && (defaultQuality.content == "true" || defaultQuality.content == "false")) {
                continue
            }
            for (inner in nestedVideoUrls(hls["videoUrl"])) {
                formats += MediaFormat(
                    formatId = "hls",
                    url = inner,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
            }
        }
        for (definition in getFormatData(http, definitions, "mp4")) {
            val videoUrl = (definition["videoUrl"] as? JsonPrimitive)?.content ?: continue
            val match = MP4_PATH.find(videoUrl)
            val bitrate = match?.groupValues?.get(2)?.toLongOrNull()
            val height = definition.number("quality")?.toLong()
                ?: match?.groupValues?.get(1)?.toLongOrNull()
            formats += MediaFormat(
                formatId = height?.let { h -> bitrate?.let { b -> "${h}p-${b}k" } },
                url = videoUrl,
                ext = "mp4",
                height = height,
                tbr = bitrate?.toDouble(),
                filesize = definition.number("videoSize")?.toLong(),
            )
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The player vars carried no playable format.")
        }
        val title = ExtractorUtils.searchRegex(
            "(?s)<div[^>]+class=[\"']watchVideoTitle[^>]+>(.+?)</div>",
            webpage,
            default = null,
        ) ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title")
            ?: ExtractorUtils.htmlSearchMeta(webpage, "title")
        val description = ExtractorUtils.searchRegex(
            "(?s)<div[^>]+\\bid=[\"']description[\"'][^>]*>(.+?)</div>",
            webpage,
            default = null,
        ) ?: ExtractorUtils.htmlSearchMeta(webpage, "og:description")
        val thumbnail = ExtractorUtils.searchRegex(
            "(?:imageurl\\s*=|poster\\s*:)\\s*([\"'])(?<thumbnail>.+?)\\1",
            webpage,
            group = 2,
            default = null,
        )
        val duration = playerVars.number("duration")?.toLong()
            ?: ExtractorUtils.htmlSearchMeta(webpage, "video:duration")?.toDoubleOrNull()?.toLong()
        val uploader = ExtractorUtils.searchRegex(
            "(?s)<div[^>]+class=[\"']submitByLink[\"'][^>]*>(.+?)</div>",
            webpage,
            default = null,
        )
        val uploadDate = ExtractorUtils.unifiedStrdate(
            listOf(
                "UPLOADED:\\s*<span>([^<]+)",
                "Date\\s+[Aa]dded:\\s*<span>([^<]+)",
                "(?s)<div[^>]+class=[\"']videoInfo(?:Date|Time)\\b[^>]*>(.+?)</div>",
                "(?s)<label\\b[^>]*>Uploaded[^<]*</label>\\s*<span\\b[^>]*>(.+?)</span>",
            ).firstNotNullOfOrNull { ExtractorUtils.searchRegex(it, webpage, default = null) },
        )
        val viewCount = ExtractorUtils.searchRegex(
            "(<div [^>]*\\bdata-value\\s*=[^>]+>)\\s*<label>Views:</label>",
            webpage,
            default = null,
        )?.let { tag -> attributeValue(tag, "data-value") }?.let(::parseCount)
        val ageLimit = if (Regex("RTA-5042-1996-1400-1577-RTA").containsMatchIn(webpage)) 18 else null
        return InfoDict(
            id = videoId,
            title = title?.let(::cleanHtml),
            description = description?.let(::cleanHtml),
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            duration = duration?.toDouble(),
            uploader = uploader?.let(::cleanHtml),
            uploadDate = uploadDate,
            viewCount = viewCount,
            ageLimit = ageLimit,
            formats = formats,
            webpageUrl = url,
            extractor = "youporn",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "YouPorn"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?youporn\\.com/(?:watch|embed)/(?<id>\\d+)(?:/(?<displayId>[^/?#&]+))?/?(?:[#?]|$)",
        )

        private val MP4_PATH = Regex("(\\d{3,4})[pP]_(\\d+)[kK]_\\d+")
    }
}

// ------------------------------------------------------------ list listings

/** Upstream `YouPornListBaseIE` and its six listing subclasses. */
class YouPornCategoryIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractList(http, url, "category", IE_KEY, titleFromSlug = { it.replace('-', ' ').replace('_', ' ') })

    companion object {
        const val IE_KEY: String = "YouPornCategory"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?youporn\\.com/(?<type>category)/(?<id>[^/?#&]+)" +
                "(?:/(?<sort>popular|views|rating|time|duration))?/?(?:[#?]|$)",
        )
    }
}

/** Upstream `YouPornChannelIE`. */
class YouPornChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractList(http, url, "channel", IE_KEY, titleFromSlug = { it.replace('_', ' ').titleCase() })

    companion object {
        const val IE_KEY: String = "YouPornChannel"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?youporn\\.com/(?<type>channel)/(?<id>[^/?#&]+)" +
                "(?:/(?<sort>rating|views|duration))?/?(?:[#?]|$)",
        )
    }
}

/** Upstream `YouPornCollectionIE`. */
class YouPornCollectionIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val plId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val info = extractList(http, url, "collection", IE_KEY, titleFromSlug = { it }, webpage = webpage)
        val infos = cleanHtml(
            ExtractorUtils.searchRegex(
                "(?s)<[^>]+class=[\"'][^\"']*collection-infos[^\"']*[\"'][^>]*>(.*?)</",
                webpage,
                default = null,
            ),
        )
        val match = Regex(
            "^\\s*Collection: (?<title>.+?) \\d+ VIDEOS \\d+ VIEWS \\d+ days LAST UPDATED From: (?<uploader>[\\w_-]+)",
        ).find(infos.orEmpty())
        if (match != null) {
            val title = match.groupValues[1]
            val uploader = match.groupValues[2]
            return info.copy(
                title = info.title?.replace(plId, title),
                uploader = uploader,
            )
        }
        return info
    }

    companion object {
        const val IE_KEY: String = "YouPornCollection"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?youporn\\.com/(?<type>collection)s/videos/(?<id>\\d+)" +
                "(?:/(?<sort>rating|views|time|duration))?/?(?:[#?]|$)",
        )
    }
}

/** Upstream `YouPornTagIE`. */
class YouPornTagIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractList(http, url, "tag", IE_KEY, titleFromSlug = { it.replace('-', ' ').replace('_', ' ') })

    companion object {
        const val IE_KEY: String = "YouPornTag"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?youporn\\.com/porn(?<type>tag)s/(?<id>[^/?#&]+)" +
                "(?:/(?<sort>views|rating|time|duration))?/?(?:[#?]|$)",
        )
    }
}

/** Upstream `YouPornStarIE`. */
class YouPornStarIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val plId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val info = extractList(http, url, "pornstar", IE_KEY, titleFromSlug = { it.replace('_', ' ').titleCase() }, webpage = webpage)
        val infos = cleanHtml(
            ExtractorUtils.searchRegex(
                "(?s)<div [^>]*\\bclass\\s*=\\s*(?:\"[^\"]*pornstar-info-wrapper[^\"]*\"|'[^']*pornstar-info-wrapper[^']*')[^>]*>(.+?)(?:</div>\\s*){6,}",
                webpage,
                default = null,
            ),
        )
        return info.copy(description = infos?.trim()?.takeIf { it.isNotEmpty() })
    }

    companion object {
        const val IE_KEY: String = "YouPornStar"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?youporn\\.com/(?<type>pornstar)/(?<id>[^/?#&]+)" +
                "(?:/(?<sort>rating|views|duration))?/?(?:[#?]|$)",
        )
    }
}

/** Upstream `YouPornVideosIE`: the browse/root listings. */
class YouPornVideosIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractList(http, url, null, IE_KEY, titleFromSlug = { if (it == "browse") "YouPorn" else it })

    companion object {
        const val IE_KEY: String = "YouPornVideos"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?youporn\\.com/" +
                "(?:(?<id>browse)/(?<browseSort>duration|rating|time|views)|" +
                "(?<sort>most_(?:favou?rit|view)ed|recommended|top_rated)?)/?(?:[#?]|$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private const val MAX_LIST_PAGES = 5

private suspend fun extractList(
    http: ExtractorHttp,
    url: String,
    expectedType: String?,
    ieKey: String,
    titleFromSlug: (String) -> String,
    webpage: String? = null,
): InfoDict {
    val type = expectedType ?: matchType(url)
    val rawId = matchId(url)
    val sort = matchSort(url)
    val query = parseQueryLast(url).filterKeys { it != "page" || true }
    val baseId = rawId ?: "YouPorn"
    var title = titleFromSlug(baseId)
    if (type != null) title = "${type.replaceFirstChar { it.uppercase() }} $title"
    val idParts = mutableListOf(baseId.lowercase())
    if (sort == null) {
        title += " videos"
    } else {
        title = "$title videos by ${sort.replace('_', ' ').replace('-', ' ')}"
        idParts += sort
    }
    if (query.isNotEmpty()) {
        val filters = query.entries.sortedBy { it.key }.map { "${it.key}=${it.value}" }
        title += " (${filters.joinToString(",")})"
        idParts += filters
    }
    val playlistId = idParts.joinToString("/")
    val pageNum = query["page"]?.toIntOrNull()
    val entries = mutableListOf<InfoEntry>()
    var currentUrl = url
    var currentHtml = webpage
    var page = pageNum ?: 1
    var walked = 0
    while (walked < MAX_LIST_PAGES) {
        val html = currentHtml ?: http.downloadWebpage(currentUrl)
        currentHtml = null
        for (href in videoTitleHrefs(html)) {
            entries += InfoEntry(url = urlJoin(currentUrl, href))
        }
        walked++
        if (pageNum != null) break
        val nextUrl = nextPageUrl(currentUrl, html) ?: break
        if (nextUrl == currentUrl) break
        currentUrl = nextUrl
        page++
    }
    return InfoDict(
        id = playlistId,
        title = title,
        entries = entries,
        webpageUrl = url,
        extractor = "youporn",
        extractorKey = ieKey,
    )
}

private val URL_GROUPS = mapOf(
    "type" to Regex("/(?<value>category|channel|collection|pornstar|tag)/"),
    "id" to Regex("/(?<value>[^/?#&]+)/?(?:[#?]|$)"),
    "sort" to Regex("/(?<value>popular|views|rating|time|duration|most_[\\w]+|recommended|top_rated)/?(?:[#?]|$)"),
)

private fun matchType(url: String): String? = URL_GROUPS["type"]!!.find(url)?.groupValues?.get(1)

private fun matchId(url: String): String? {
    val path = url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
    val segments = path.split('/').filter { it.isNotEmpty() }
    return when {
        segments.isEmpty() -> null
        segments[0] == "browse" -> "browse"
        segments[0] == "collections" -> segments.getOrNull(2)
        segments[0] == "porntags" -> segments.getOrNull(1)
        segments.size >= 2 && segments[0] in listOf("category", "channel", "pornstar") -> segments[1]
        else -> null
    }
}

private fun matchSort(url: String): String? {
    val path = url.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
    val segments = path.split('/').filter { it.isNotEmpty() }
    val sorts = setOf(
        "popular", "views", "rating", "time", "duration",
        "most_favorited", "most_favourited", "most_viewed", "recommended", "top_rated",
    )
    return segments.firstOrNull { it in sorts }
}

private fun parseQueryLast(url: String): Map<String, String> {
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return emptyMap()
    return query.split('&').mapNotNull { pair ->
        val key = pair.substringBefore('=')
        val value = pair.substringAfter('=', "")
        if (key.isEmpty()) null else key to value
    }.toMap()
}

private fun videoTitleHrefs(html: String): List<String> {
    val out = mutableListOf<String>()
    for (tag in Regex("<a\\b[^>]*>").findAll(html)) {
        val attributes = tagAttributes(tag.value)
        val classes = attributes["class"] ?: continue
        if (!classes.split(' ').contains("video-title")) continue
        attributes["href"]?.let { out += it }
    }
    return out
}

private fun nextPageUrl(base: String, html: String): String? {
    val marker = Regex("<[^>]*\\bid\\s*=\\s*[\"']?next[\"']?[^>]*>").find(html) ?: return null
    val tail = html.substring(marker.range.last + 1).take(4000)
    val href = Regex("<a\\b[^>]*\\bhref\\s*=\\s*[\"']([^\"']+)[\"']").find(tail)?.groupValues?.get(1)
        ?: return null
    return urlJoin(base, href)
}

private fun jsonAfterKey(html: String, key: String): JsonObject? {
    val marker = Regex("\\b$key\\s*:").find(html) ?: return null
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

private suspend fun getFormatData(
    http: ExtractorHttp,
    definitions: List<JsonElement>,
    streamType: String,
): List<JsonObject> {
    val infoUrl = definitions.mapNotNull { it as? JsonObject }
        .firstOrNull { it.str("format") == streamType }
        ?.str("videoUrl")
        ?: return emptyList()
    val body = try {
        http.downloadJson(infoUrl)
    } catch (error: ExtractionError) {
        return emptyList()
    }
    val list = body as? JsonArray ?: return emptyList()
    return list.mapNotNull { it as? JsonObject }
        .filter { it.str("format") == streamType && it.str("videoUrl") != null }
}

private fun nestedVideoUrls(element: JsonElement?): List<String> {
    return when (element) {
        is JsonArray -> element.flatMap { nestedVideoUrls(it) }
        is JsonObject -> listOfNotNull((element["videoUrl"] as? JsonPrimitive)?.content)
        is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
        else -> emptyList()
    }
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    val withoutTags = text.replace(Regex("<[^>]*>"), " ")
    return ExtractorUtils.unescapeHtml(withoutTags)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun parseCount(value: String?): Long? {
    val text = value?.trim() ?: return null
    val match = Regex("([\\d.,]+)\\s*([KkMm])?").find(text) ?: return null
    val base = match.groupValues[1].replace(",", "").replace(".", "").toDoubleOrNull() ?: return null
    return when (match.groupValues[2].lowercase()) {
        "k" -> (base * 1_000).toLong()
        "m" -> (base * 1_000_000).toLong()
        else -> base.toLong()
    }
}

private val ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

private fun tagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        out[match.groupValues[1].lowercase()] = match.groupValues[2].ifEmpty { match.groupValues[3] }
    }
    return out
}

private fun attributeValue(tag: String, name: String): String? = tagAttributes(tag)[name]

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    val origin = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: return href
    return if (href.startsWith("/")) origin + href else "$origin/$href"
}

private fun String.titleCase(): String =
    split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
