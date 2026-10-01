/*
 * Dropout extractors — AnyDownload
 *
 * Kotlin translation of `dropout.py` from `yt_dlp/extractor/dropout.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `dropout.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the watch page's `embed_url`, title, description, thumbnail (crop
 * query removed), series, and release date (folded into `uploadDate`), the
 * `watch-unauthorized` typed login wall, and the season page's
 * `browse-item-link` pagination as child entries. Limitations: the netrc
 * login is not translated, so a page without the `_session` cookie fails
 * typed LoginRequired; the upstream `url_transparent` dispatch to
 * `VHXEmbedIE` becomes one child entry at the embed URL, and `VHXEmbedIE`
 * itself is not part of this card (it stays not-started); the
 * `episode_number`/`season_number`/`display_id` fields are not modeled and
 * are dropped; a 400 page end is treated as an empty page. No cookie, token,
 * or signed media URL is stored here.
 */
package com.anydownload.core.extract.dropout

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.Thumbnail

/** Upstream `DropoutIE`: one watch page. */
class DropoutIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        if ("<div id=\"watch-unauthorized\"" in webpage) {
            // Upstream tries the netrc login first; the port does not log in.
            throw ExtractionError.LoginRequired("This Dropout video needs a subscription sign-in.")
        }

        val embedUrl = ExtractorUtils.searchRegex("embed_url:\\s*[\"'](.+?)[\"']", webpage)
            ?: throw ExtractionError.Malformed("The Dropout page carried no embed URL.")
        val embedId = ExtractorUtils.searchRegex("embed\\.vhx\\.tv/videos/(.+?)\\?", embedUrl)
        val watchInfo = elementById(webpage, "watch-info") ?: ""

        val title = cleanHtml(elementByClass(watchInfo, "video-title"))
        val seasonEpisode = elementByClass(
            elementByClass(watchInfo, "text") ?: "",
            "site-font-secondary-color",
        )
        val releaseDate = ExtractorUtils.searchRegex(
            "data-meta-field-name=[\"']release_dates[\"'] data-meta-field-value=[\"'](.+?)[\"']",
            watchInfo,
        )
        val thumbnail = ExtractorUtils.htmlSearchMeta(webpage, "og:image")?.substringBefore('?')

        return InfoDict(
            id = embedId,
            title = title,
            description = ExtractorUtils.htmlSearchMeta(webpage, "description"),
            thumbnails = thumbnail?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            uploadDate = releaseDate?.let(ExtractorUtils::unifiedStrdate),
            entries = listOf(InfoEntry(id = embedId, title = title, url = embedUrl)),
            webpageUrl = url,
            extractor = "dropout",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "Dropout"

        val VALID_URL: Regex = Regex(
            "https?://(?:watch\\.)?dropout\\.tv/(?:[^/?#]+/)*videos/(?<id>[^/?#]+)/?(?:[?#]|$)",
        )
    }
}

/** Upstream `DropoutSeasonIE`: a series/season listing. */
class DropoutSeasonIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val seasonId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seasonNum = match.groups["season"]?.value ?: "1"
        val seasonTitle = titleCase(seasonId.replace('-', ' '))

        val entries = mutableListOf<InfoEntry>()
        var page = 0
        while (page < MAX_PAGES) {
            val webpage = try {
                http.downloadWebpage("$url?page=${page + 1}")
            } catch (error: ExtractionError) {
                // Upstream `expected_status={400}`: a page end yields no items.
                break
            }
            val hrefs = elementsByClass(webpage, "browse-item-link").mapNotNull { tag ->
                extractAttributes(tag)["href"]
            }
            if (hrefs.isEmpty()) break
            entries += hrefs.map { InfoEntry(url = it) }
            page++
        }

        return InfoDict(
            id = "$seasonId-season-$seasonNum",
            title = "$seasonTitle - Season $seasonNum",
            entries = entries,
            webpageUrl = url,
            extractor = "dropout:season",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "DropoutSeason"

        private const val MAX_PAGES = 100

        val VALID_URL: Regex = Regex(
            "https?://(?:watch\\.)?dropout\\.tv/(?<id>[^/$\u0026?#]+)(?:/?$|/season:(?<season>[0-9]+)/?$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `get_element_by_id` with tag-balanced slicing. */
private fun elementById(webpage: String, id: String): String? =
    balancedElement(webpage, Regex("(?s)<(\\w+)\\b[^>]+id\\s*=\\s*[\"']${Regex.escape(id)}[\"'][^>]*>"))

/** Upstream `get_element_by_class` with tag-balanced slicing. */
private fun elementByClass(webpage: String, className: String): String? =
    balancedElement(
        webpage,
        Regex(
            "(?s)<(\\w+)\\b[^>]+class\\s*=\\s*[\"'](?:[\\w-]+\\s+)*?" +
                Regex.escape(className) + "(?:\\s+[\\w-]+)*[\"'][^>]*>",
        ),
    )

/** Upstream `get_elements_html_by_class`: the opening tags with [className]. */
private fun elementsByClass(webpage: String, className: String): List<String> =
    Regex(
        "(?s)<(\\w+)\\b[^>]+class\\s*=\\s*[\"'](?:[\\w-]+\\s+)*?" +
            Regex.escape(className) + "(?:\\s+[\\w-]+)*[\"'][^>]*>",
    ).findAll(webpage).map { it.value }.toList()

private fun balancedElement(webpage: String, openTag: Regex): String? {
    val open = openTag.find(webpage) ?: return null
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

/** Upstream `extract_attributes` for one tag. */
private fun extractAttributes(tag: String): Map<String, String> {
    val attributes = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        val value = match.groupValues[2]
            .ifEmpty { match.groupValues[3] }
            .ifEmpty { match.groupValues[4] }
        attributes[match.groupValues[1].lowercase()] = value
    }
    return attributes
}

private val ATTRIBUTE = Regex(
    """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""",
)

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), " "))
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

/** Upstream `str.title()` for the hyphen-separated slug. */
private fun titleCase(value: String): String =
    value.split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercaseChar() }
    }
