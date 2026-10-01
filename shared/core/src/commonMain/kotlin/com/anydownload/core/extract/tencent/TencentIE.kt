/*
 * Tencent Video / WeTV / Iflix extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `tencent.py` from
 * `yt_dlp/extractor/tencent.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tencent.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the series pages (VQQ `data-vid` scan, WeTV/Iflix Next.js
 * `videoList` and `play-video__link` scans) become child entries. The three
 * episode classes match and fail typed: the `getvinfo` API needs a
 * guid-based `ckey` signature and enables DRM, which the port excludes. No
 * key, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.tencent

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val WALL =
    "The getvinfo API needs a guid-based ckey signature and enables DRM; the port excludes both."

/** Upstream `VQQVideoIE`: a v.qq.com video. */
class VQQVideoIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "VQQVideo"

        val VALID_URL: Regex = Regex(
            "https?://v\\.qq\\.com/x/(?:page|cover/(?<seriesId>\\w+))/(?<id>\\w+)",
        )
    }
}

/** Upstream `VQQSeriesIE`: a v.qq.com cover page. */
class VQQSeriesIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val seriesId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val entries = Regex(
            "<div[^>]+data-vid=\"(?<videoId>[^\"]+)\"[^>]+class=\"[^\"]+episode-item-rect--number",
        ).findAll(webpage).map { match ->
            InfoEntry(url = urlJoin(url, "/x/cover/$seriesId/${match.groupValues[1]}.html"))
        }.toList()
        return InfoDict(
            id = seriesId,
            title = cleanTitle(ExtractorUtils.htmlSearchMeta(webpage, "og:title")),
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
            entries = entries,
            webpageUrl = url,
            extractor = "vqq:series",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "VQQSeries"

        val VALID_URL: Regex = Regex("https?://v\\.qq\\.com/x/cover/(?<id>\\w+)\\.html/?(?:[?#]|$)")
    }
}

/** Upstream `WeTvEpisodeIE`: a WeTV episode (signed API wall). */
class WeTvEpisodeIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "WeTvEpisode"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?wetv\\.vip/(?:[^?#]+/)?play/(?<seriesId>\\w+)(?:-[^?#]+)?/(?<id>\\w+)(?:-[^?#]+)?",
        )
    }
}

/** Upstream `WeTvSeriesIE`: a WeTV series page. */
class WeTvSeriesIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractSeries(http, url, "wetv", "play", IE_KEY)

    companion object {
        const val IE_KEY: String = "WeTvSeries"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?wetv\\.vip/(?:[^?#]+/)?play/(?<id>\\w+)(?:-[^/?#]+)?/?(?:[?#]|$)",
        )
    }
}

/** Upstream `IflixEpisodeIE`: an Iflix episode (signed API wall). */
class IflixEpisodeIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "IflixEpisode"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?iflix\\.com/(?:[^?#]+/)?play/(?<seriesId>\\w+)(?:-[^?#]+)?/(?<id>\\w+)(?:-[^?#]+)?",
        )
    }
}

/** Upstream `IflixSeriesIE`: an Iflix series page. */
class IflixSeriesIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractSeries(http, url, "iflix", "play", IE_KEY)

    companion object {
        const val IE_KEY: String = "IflixSeries"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?iflix\\.com/(?:[^?#]+/)?play/(?<id>\\w+)(?:-[^/?#]+)?/?(?:[?#]|$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun extractSeries(
    http: ExtractorHttp,
    url: String,
    site: String,
    path: String,
    ieKey: String,
): InfoDict {
    val seriesId = seriesId(url) ?: throw ExtractionError.UnsupportedUrl()
    val webpage = http.downloadWebpage(url)
    val metadata = nextJsPageData(webpage)
    val entries = mutableListOf<InfoEntry>()
    for (element in metadata?.array("videoList").orEmpty()) {
        val vid = (element as? JsonObject)?.str("vid") ?: continue
        entries += InfoEntry(url = urlJoin(url, "/$path/$seriesId/$vid"))
    }
    if (entries.isEmpty()) {
        for (match in Regex("<a[^>]+class=\"play-video__link\"[^>]+href=\"([^\"]+)\"").findAll(webpage)) {
            entries += InfoEntry(url = urlJoin(url, match.groupValues[1]))
        }
    }
    val cover = metadata?.obj("coverInfo")
    return InfoDict(
        id = seriesId,
        title = cleanTitle(cover?.str("title") ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title")),
        description = cover?.str("description") ?: ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
        entries = entries,
        webpageUrl = url,
        extractor = "$site:series",
        extractorKey = ieKey,
    )
}

private fun seriesId(url: String): String? {
    val path = url.substringAfter("://", "").substringBefore('?').substringBefore('#')
    val segments = path.split('/').filter { it.isNotEmpty() }
    val playIndex = segments.indexOf("play")
    if (playIndex < 0) return null
    val candidate = segments.getOrNull(playIndex + 1) ?: return null
    return candidate.substringBefore('-')
}

private val NEXT_DATA = Regex(
    "<script[^>]+id=\"__NEXT_DATA__\"[^>]*>(.+?)</script>",
    RegexOption.DOT_MATCHES_ALL,
)

private fun nextJsPageData(html: String): JsonObject? {
    val raw = NEXT_DATA.find(html)?.groupValues?.get(1) ?: return null
    val root = ExtractorUtils.parseJson(raw) as? JsonObject ?: return null
    return root.obj("props")?.obj("pageProps")?.obj("data")
}

private fun cleanTitle(value: String?): String? {
    val text = value?.trim() ?: return null
    val cleaned = text.replace(Regex("<[^>]*>"), "").trim()
    return if (cleaned.isEmpty()) null else cleaned
}

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    val origin = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: return href
    return if (href.startsWith("/")) origin + href else "$origin/$href"
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
