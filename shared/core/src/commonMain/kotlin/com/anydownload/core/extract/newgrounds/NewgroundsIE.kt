/*
 * Newgrounds extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `newgrounds.py` from
 * `yt_dlp/extractor/newgrounds.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `newgrounds.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the listen/view media page (the `embedController` source URL, the
 * `/portal/video/` sources map, title/uploader/timestamp/duration/thumbnail/
 * description/age-limit/views), the collection/search playlist, and the user
 * movie/audio listing. The upstream netrc `_perform_login` flow is not
 * translated: a page the server answers with 401 is the typed login wall. The
 * `_check_formats` probe is not translated, so a dead format URL is not
 * pruned before download. No cookie, token, or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.newgrounds

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

private const val MAX_PAGES = 5
private const val USER_PAGE_SIZE = 30

/** Upstream `NewgroundsIE`: one listen/view media page. */
class NewgroundsIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val mediaId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // A 401 from the page is already the typed login wall in ExtractorHttp.
        val webpage = http.downloadWebpage(url)

        val mediaUrlString = ExtractorUtils.searchRegex(
            "embedController\\(\\[\\{\"url\"\\s*:\\s*(\"[^\"]+\"),",
            webpage,
        )
        val formats = mutableListOf<MediaFormat>()
        var uploader: String? = null
        if (mediaUrlString != null) {
            val source = (ExtractorUtils.parseJson(mediaUrlString) as? JsonPrimitive)?.content
                ?: throw ExtractionError.Malformed("The Newgrounds source URL was not a string.")
            formats += MediaFormat(url = source, formatId = "source", preference = 1)
        } else {
            val jsonVideo = http.downloadJson(
                "https://www.newgrounds.com/portal/video/$mediaId",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Referer" to url,
                    "X-Requested-With" to "XMLHttpRequest",
                ),
            ) as? JsonObject ?: throw ExtractionError.Malformed("The Newgrounds video API was not an object.")
            uploader = jsonVideo.str("author")
            for ((formatId, sources) in jsonVideo.obj("sources").orEmpty()) {
                val quality = formatId.dropLast(1).toIntOrNull()
                for (element in (sources as? JsonArray).orEmpty()) {
                    val sourceUrl = (element as? JsonObject)?.str("src")
                        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                        ?: continue
                    formats += MediaFormat(
                        formatId = formatId,
                        url = sourceUrl,
                        preference = quality,
                    )
                }
            }
        }

        if (uploader == null) {
            uploader = ExtractorUtils.searchRegex(
                "(?s)<h4[^>]*>(.+?)</h4>.*?<em>\\s*(?:Author|Artist)\\s*</em>",
                webpage,
            ) ?: ExtractorUtils.searchRegex(
                "(?:Author|Writer)\\s*<a[^>]+>([^<]+)",
                webpage,
            )
        }

        if (formats.size == 1) {
            val filesize = ExtractorUtils.searchRegex(
                "\"filesize\"\\s*:\\s*[\"']?([\\d]+)[\"']?,",
                webpage,
            )?.toLongOrNull()
            val mediaType = ExtractorUtils.searchRegex(
                "\"description\"\\s*:\\s*[\"']?([^\"']+)[\"']?,",
                webpage,
            )
            formats[0] = formats[0].copy(
                filesize = filesize,
                vcodec = if (mediaType == "Audio File") MediaFormat.CODEC_NONE else formats[0].vcodec,
            )
        }

        val ageMarker = ExtractorUtils.searchRegex(
            "<h2\\s+class=[\"']rated-([etma])[\"']",
            webpage,
        ) ?: "e"
        val description = cleanHtml(elementById(webpage, "author_comments"))
            ?: ExtractorUtils.htmlSearchMeta(webpage, "og:description")
        return InfoDict(
            id = mediaId,
            title = ExtractorUtils.searchRegex(
                "<title[^>]*>(.*?)</title>",
                webpage,
                setOf(RegexOption.DOT_MATCHES_ALL),
            )?.trim(),
            uploader = uploader,
            duration = ExtractorUtils.searchRegex(
                "\"duration\"\\s*:\\s*[\"']?(\\d+)",
                webpage,
            )?.let(ExtractorUtils::parseDuration),
            uploadDate = ExtractorUtils.searchRegex(
                "itemprop=\"(?:uploadDate|datePublished)\"\\s+content=\"([^\"]+)\"",
                webpage,
            )?.let(ExtractorUtils::unifiedStrdate),
            viewCount = parseCount(
                ExtractorUtils.searchRegex(
                    "(?s)<dt>\\s*(?:Views|Listens)\\s*</dt>\\s*<dd>([\\d.,]+)</dd>",
                    webpage,
                ),
            ),
            ageLimit = AGE_LIMIT[ageMarker] ?: 0,
            thumbnails = listOfNotNull(
                ExtractorUtils.htmlSearchMeta(webpage, "og:image")?.let { Thumbnail(url = it) },
            ),
            description = description,
            formats = formats,
            webpageUrl = url,
            extractor = "newgrounds",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Newgrounds"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?newgrounds\\.com/(?:audio/listen|portal/view)/(?<id>\\d+)(?:/format/flash)?",
        )

        private val AGE_LIMIT = mapOf("e" to 0, "t" to 13, "m" to 17, "a" to 18)
    }
}

/** Upstream `NewgroundsPlaylistIE`: a collection or search listing. */
class NewgroundsPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        var webpage = http.downloadWebpage(url)
        val title = ExtractorUtils.searchRegex(
            "<title[^>]*>(.*?)</title>",
            webpage,
            setOf(RegexOption.DOT_MATCHES_ALL),
        )?.trim()
        // Cut the left menu like upstream.
        ExtractorUtils.searchRegex("(?s)<div[^>]+\\bclass=[\"']column wide(.+)", webpage)
            ?.let { webpage = it }
        val entries = mutableListOf<InfoEntry>()
        for (match in Regex(
            "(<a[^>]+\\bhref=[\"'][^\"']+((?:portal/view|audio/listen)/(\\d+))[^>]+>)",
        ).findAll(webpage)) {
            val tag = match.groupValues[1]
            val className = tagAttributes(tag)["class"]
            if (className != "item-portalsubmission" && className != "item-audiosubmission") continue
            entries += InfoEntry(
                id = match.groupValues[3],
                url = "https://www.newgrounds.com/${match.groupValues[2]}",
            )
        }
        return InfoDict(
            id = playlistId,
            title = title,
            entries = entries,
            webpageUrl = url,
            extractor = "newgrounds:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NewgroundsPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?newgrounds\\.com/(?:collection|[^/]+/search/[^/]+)/(?<id>[^/?#&]+)",
        )
    }
}

/** Upstream `NewgroundsUserIE`: a user movie/audio listing. */
class NewgroundsUserIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val channelId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val entries = mutableListOf<InfoEntry>()
        // Upstream OnDemandPagedList is 0-based and asks for page + 1.
        var page = 0
        while (page < MAX_PAGES) {
            val postsInfo = http.downloadJson(
                "$url?page=${page + 1}",
                headers = mapOf(
                    "Accept" to "application/json, text/javascript, */*; q = 0.01",
                    "X-Requested-With" to "XMLHttpRequest",
                ),
            ) as? JsonObject ?: throw ExtractionError.Malformed("The Newgrounds user API was not an object.")
            val posts = postsInfo.array("items").orEmpty()
                .flatMap { row -> (row as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content } }
            if (posts.isEmpty()) break
            for (post in posts) {
                val match = Regex(
                    "<a[^>]+\\bhref=[\"'][^\"']+((?:portal/view|audio/listen)/(\\d+))[^>]+>",
                ).find(post) ?: continue
                entries += InfoEntry(
                    id = match.groupValues[2],
                    url = "https://www.newgrounds.com/${match.groupValues[1]}",
                )
            }
            if (posts.size < USER_PAGE_SIZE) break
            page++
        }
        return InfoDict(
            id = channelId,
            entries = entries,
            webpageUrl = url,
            extractor = "newgrounds:user",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NewgroundsUser"

        val VALID_URL: Regex = Regex(
            "https?://(?<id>[^.]+)\\.newgrounds\\.com/(?:movies|audio)/?(?:[#?]|$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `parse_count` for the comma-grouped Newgrounds counters. */
private fun parseCount(value: String?): Long? =
    value?.replace(",", "")?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), " "))
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

/** Upstream `get_element_by_id` for the simple `author_comments` section. */
private fun elementById(webpage: String, id: String): String? = Regex(
    "<(\\w+)[^>]*\\bid\\s*=\\s*[\"']" + Regex.escape(id) + "[\"'][^>]*>(.*?)</\\1>",
    RegexOption.DOT_MATCHES_ALL,
).find(webpage)?.groupValues?.get(2)

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
