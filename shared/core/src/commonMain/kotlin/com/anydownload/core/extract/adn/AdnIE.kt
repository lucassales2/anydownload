/*
 * Animation Digital Network extractors — AnyDownload
 *
 * Kotlin translation of the public listing subset of `adn.py` from
 * `yt_dlp/extractor/adn.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `adn.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public show/episode listing API for `ADNSeasonIE`. `ADNIE`
 * matches and fails typed, because playback needs a subscription login and
 * the RSA-encrypted player token flow, which the port does not carry. No
 * RSA key, token, or media URL is stored here.
 */
package com.anydownload.core.extract.adn

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val API_BASE_URL = "https://gw.api.animationdigitalnetwork.fr/"

/** Upstream `ADNIE`: one episode. */
class ADNIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "This video requires a subscription, and the player token flow needs RSA encryption, " +
                "which the port does not carry.",
        )
    }

    companion object {
        const val IE_KEY: String = "ADN"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?animationdigitalnetwork\\.com/(?:(?<lang>de)/)?" +
                "video/[^/?#]+/(?<id>\\d+)",
        )
    }
}

/** Upstream `ADNSeasonIE`: a show listing. */
class ADNSeasonIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val lang = match.groups["lang"]?.value
        val showSlug = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val headers = mapOf("X-Target-Distribution" to (lang ?: "fr"))
        val show = http.downloadJson("${API_BASE_URL}show/$showSlug/", headers = headers) as? JsonObject
        val showObject = show?.obj("show")
            ?: throw ExtractionError.Malformed("The ADN show API returned no show.")
        val showId = showObject.primitive("id")
            ?: throw ExtractionError.Malformed("The ADN show had no id.")
        val episodes = http.downloadJson(
            "${API_BASE_URL}video/show/$showId?order=asc&limit=-1",
            headers = headers,
        ) as? JsonObject ?: throw ExtractionError.Malformed("The ADN episode API returned no object.")
        val entries = mutableListOf<InfoEntry>()
        for (element in episodes.array("videos").orEmpty()) {
            val episode = element as? JsonObject ?: continue
            val episodeId = episode.primitive("id") ?: continue
            val prefix = if (lang != null) "/$lang" else ""
            entries += InfoEntry(
                id = episodeId,
                title = episode.str("name"),
                url = "https://animationdigitalnetwork.com$prefix/video/$showSlug/$episodeId",
            )
        }
        return InfoDict(
            id = showId,
            title = showObject.str("title"),
            entries = entries,
            webpageUrl = url,
            extractor = "adn:season",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ADNSeason"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?animationdigitalnetwork\\.com/(?:(?<lang>de)/)?" +
                "video/(?<id>\\d+)[^/?#]*/?(?:$|[#?])",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
