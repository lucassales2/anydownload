/*
 * 10play extractors — AnyDownload
 *
 * Kotlin translation of the public listing subset of `tenplay.py` from
 * `yt_dlp/extractor/tenplay.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `tenplay.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public season listing API for `TenPlaySeasonIE` (five pages
 * eagerly). `TenPlayIE` matches and fails typed, because the video API needs
 * a login and a refresh/access token pair, which the port does not carry.
 * The port does not carry display ids or series/season/episode fields, so
 * they are dropped. No cookie, token, or media URL is stored here.
 */
package com.anydownload.core.extract.tenplay

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MAX_PAGES = 5

/** Upstream `TenPlayIE`: one video. */
class TenPlayIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "Login required to access this video, and the port does not carry the token refresh flow.",
        )
    }

    companion object {
        const val IE_KEY: String = "TenPlay"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?10(?:play)?\\.com\\.au/(?:[^/?#]+/)+(?<id>tpv\\d{6}[a-z]{5})",
        )
    }
}

/** Upstream `TenPlaySeasonIE`: a season listing. */
class TenPlaySeasonIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val show = match.groups["show"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val season = match.groups["season"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seasonInfo = http.downloadJson("https://10.com.au/api/shows/$show/episodes/$season")
            as? JsonObject ?: throw ExtractionError.Malformed("The 10play season API returned no object.")
        val content = seasonInfo.array("content")?.firstOrNull() as? JsonObject
        var carousel: JsonObject? = null
        for (element in content?.array("components").orEmpty()) {
            val component = element as? JsonObject ?: continue
            if (component.str("title")?.lowercase() == "episodes") {
                carousel = component
                break
            }
        }
        val carouselObject = carousel
            ?: throw ExtractionError.Malformed("The 10play season had no episodes carousel.")
        val playlistId = carouselObject.primitive("tpId")
            ?: throw ExtractionError.Malformed("The 10play season had no episodes carousel.")
        val loadMoreUrl = carouselObject.str("loadMoreUrl")
            ?: throw ExtractionError.Malformed("The 10play season carousel had no load-more URL.")
        val entries = mutableListOf<InfoEntry>()
        val skipIds = mutableListOf<String>()
        var page = 1
        var hasMore = true
        while (hasMore && page <= MAX_PAGES) {
            val query = if (skipIds.isEmpty()) {
                ""
            } else {
                "?skipIds%5B%5D=" + skipIds.joinToString("&skipIds%5B%5D=")
            }
            val carouselPage = try {
                http.downloadJson(urlJoin(url, loadMoreUrl) + query) as? JsonObject
            } catch (error: Exception) {
                break
            } ?: break
            val items = carouselPage.array("items").orEmpty()
            if (items.isEmpty()) break
            for (element in items) {
                val episode = element as? JsonObject ?: continue
                episode.primitive("id")?.let { skipIds += it }
                val cardLink = episode.str("cardLink") ?: continue
                entries += InfoEntry(id = episode.primitive("id"), url = urlJoin(url, cardLink))
            }
            hasMore = carouselPage.boolean("hasMore") == true
            page++
        }
        return InfoDict(
            id = playlistId,
            title = content?.str("title"),
            entries = entries,
            webpageUrl = url,
            extractor = "10play:season",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TenPlaySeason"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?10(?:play)?\\.com\\.au/(?<show>[^/?#]+)/episodes/" +
                "(?<season>[^/?#]+)/?(?:$|[?#])",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    val origin = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: return href
    return if (href.startsWith("/")) origin + href else "$origin/$href"
}

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
