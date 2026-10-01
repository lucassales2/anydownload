/*
 * Teachable extractors — AnyDownload
 *
 * Kotlin translation of `teachable.py` from `yt_dlp/extractor/teachable.py`
 * at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `teachable.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the lecture page's Wistia embed discovery (a local helper for the
 * upstream `WistiaIE._extract_embed_urls` subset: the meta/iframe/script src
 * regex and the `data-wistia-id` / `Wistia.embed` / `id="wistia_` regex), the
 * `teachable:` prefix handling, the locked-lecture typed wall, the
 * chapter/section walk, and the course page's `section-item` entries. The
 * netrc login (`_login`) is not translated, so a locked lecture fails typed
 * LoginRequired; `chapter`/`chapter_number` and `video_title` extras are not
 * modeled and are dropped. No cookie, token, or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.teachable

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor

private const val URL_PREFIX = "teachable:"

/** Upstream `_SITES`, notable entries only. */
private const val SITES = "v1\\.upskillcourses\\.com|gns3\\.teachable\\.com|academyhacker\\.com|" +
    "stackskills\\.com|market\\.saleshacker\\.com|learnability\\.org|edurila\\.com|" +
    "courses\\.workitdaily\\.com"

/** Upstream `TeachableBaseIE`: the shared URL shape and prefix helpers. */
abstract class TeachableBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_login` uses netrc credentials; the port does not log in. */
    protected fun login(site: String) {
        // No netrc login: the lecture's locked-content check fails typed instead.
    }

    protected fun siteOf(match: MatchResult): String? =
        match.groups["site"]?.value ?: match.groups["sitet"]?.value

    protected fun stripPrefix(url: String): String =
        if (url.startsWith(URL_PREFIX)) url.removePrefix(URL_PREFIX) else url
}

/** Upstream `TeachableIE`: one lecture page. */
class TeachableIE(
    http: ExtractorHttp,
) : TeachableBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val site = siteOf(match) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        login(site)
        val actualUrl = stripPrefix(url)

        val webpage = http.downloadWebpage(actualUrl)
        val wistiaUrls = wistiaEmbedUrls(webpage)
        if (wistiaUrls.isEmpty()) {
            if (LOCKED_PATTERNS.any { it.containsMatchIn(webpage) }) {
                throw ExtractionError.LoginRequired("Lecture contents locked")
            }
            throw ExtractionError.Unavailable("Unable to find video URL")
        }

        val title = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
        return InfoDict(
            id = videoId,
            title = title,
            entries = wistiaUrls.map { InfoEntry(title = title, url = it) },
            webpageUrl = url,
            extractor = "teachable",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "Teachable"

        private val LOCKED_PATTERNS = listOf(
            Regex("class=[\"']lecture-contents-locked"),
            Regex(">\\s*Lecture contents locked"),
            Regex("id=[\"']lecture-locked"),
            Regex("class=[\"'](?:inner-)?lesson-locked"),
            Regex(">LESSON LOCKED<"),
        )

        val VALID_URL: Regex = Regex(
            "(?:(?:teachable:)https?://(?<sitet>[a-zA-Z0-9.-]+)|" +
                "https?://(?:www\\.)?(?<site>$SITES))/courses/[^/]+/lectures/(?<id>\\d+)",
        )
    }
}

/** Upstream `TeachableCourseIE`: a course page. */
class TeachableCourseIE(
    http: ExtractorHttp,
) : TeachableBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        !TeachableIE.VALID_URL.containsMatchIn(url) && super.suitable(url)

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val site = siteOf(match) ?: throw ExtractionError.UnsupportedUrl()
        val courseId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        login(site)
        val prefixed = url.startsWith(URL_PREFIX)
        val actualUrl = stripPrefix(url)

        val webpage = http.downloadWebpage(actualUrl)
        val urlBase = "https://$site/"

        val entries = mutableListOf<InfoEntry>()
        for (matchLi in SECTION_ITEM.findAll(webpage)) {
            val li = matchLi.groups["li"]?.value ?: continue
            if ("fa-youtube-play" !in li && !Regex("\\d{1,2}:\\d{2}").containsMatchIn(li)) continue
            val lectureUrl = LECTURE_URL.find(li)?.groups?.get("url")?.value ?: continue
            val lectureId = ExtractorUtils.searchRegex("/lectures/(\\d+)", lectureUrl)
            val title = ExtractorUtils.searchRegex(
                "<span[^>]+class=[\"']lecture-name[^>]+>([^<]+)",
                li,
            )
            val entryUrl = if (lectureUrl.startsWith("http")) {
                lectureUrl
            } else {
                urlBase.trimEnd('/') + "/" + lectureUrl.trimStart('/')
            }
            entries += InfoEntry(
                id = lectureId,
                title = cleanHtml(title),
                url = if (prefixed) URL_PREFIX + entryUrl else entryUrl,
            )
        }

        val courseTitle = ExtractorUtils.searchRegex(
            "(?s)<img[^>]+class=[\"']course-image[^>]+>\\s*<h\\d>(.+?)</h",
            webpage,
            setOf(RegexOption.DOT_MATCHES_ALL),
        ) ?: ExtractorUtils.searchRegex(
            "(?s)<h\\d[^>]+class=[\"']course-title[^>]+>(.+?)</h",
            webpage,
            setOf(RegexOption.DOT_MATCHES_ALL),
        )

        return InfoDict(
            id = courseId,
            title = courseTitle,
            entries = entries,
            webpageUrl = url,
            extractor = "teachable:course",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "TeachableCourse"

        private val SECTION_ITEM = Regex(
            "(?s)(?<li><li[^>]+class=([\"'])(?:(?!\\2).)*?section-item[^>]+>.+?</li>)",
        )
        private val LECTURE_URL = Regex("<a[^>]+href=([\"'])(?<url>(?:(?!\\1).)+)\\1")

        val VALID_URL: Regex = Regex(
            "(?:(?:teachable:)https?://(?<sitet>[a-zA-Z0-9.-]+)|" +
                "https?://(?:www\\.)?(?<site>$SITES))/(?:courses|p)/(?:enrolled/)?(?<id>[^/?#&]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private val WISTIA_EMBED = Regex(
    "<(?:meta[^>]+?content|(?:iframe|script)[^>]+?src)=[\"']" +
        "((?:https?:)?//(?:fast\\.)?wistia\\.(?:net|com)/embed/(?:iframe|medias)/[a-z0-9]{10})",
    RegexOption.IGNORE_CASE,
)

private val WISTIA_ID = Regex(
    "(?:data-wistia-?id=[\"']|Wistia\\.embed\\([\"']|id=[\"']wistia_)([a-z0-9]{10})",
)

/** Upstream `WistiaIE._extract_embed_urls` subset used by the lecture page. */
private fun wistiaEmbedUrls(webpage: String): List<String> {
    val urls = mutableListOf<String>()
    for (match in WISTIA_EMBED.findAll(webpage)) {
        val url = match.groupValues[1]
        urls += if (url.startsWith("//")) "https:$url" else url
    }
    for (match in WISTIA_ID.findAll(webpage)) {
        urls += "wistia:${match.groupValues[1]}"
    }
    return urls.distinct()
}

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}
