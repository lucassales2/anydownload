/*
 * Amazon MiniTV extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `amazonminitv.py` from
 * `yt_dlp/extractor/amazonminitv.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `amazonminitv.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `/prs` playback assets (one HLS row and one DASH row), the three
 * GraphQL queries (content, getEpisodes, getSeasons), and the metadata the
 * info dict models. Limitation: upstream reads the guest `session-id` cookie
 * from the minitv page in `_real_initialize`; the platform strips `Set-Cookie`
 * at its boundary by design and the port does not read user cookie values, so
 * `sessionIdToken` is sent empty and a real deployment may refuse the GraphQL
 * calls (a typed Unavailable then names the API message). The `language`,
 * `release_timestamp`, and series/season/episode fields the port does not
 * model are dropped. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.amazonminitv

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Chapter
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Upstream `AmazonMiniTVBaseIE`: the API calls shared by the three classes. */
abstract class AmazonMiniTvBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {

    protected suspend fun callApi(asin: String, data: JsonObject? = null): JsonElement {
        val response = if (data == null) {
            http.downloadJson(
                "https://www.amazon.in/minitv/api/web/prs" +
                    "?deviceType=A1WMMUXPCUJL4N&contentId=$asin&clientId=ATVIN&deviceLocale=en_GB",
            )
        } else {
            http.downloadJson(
                "https://www.amazon.in/minitv/api/web/graphql",
                method = "POST",
                headers = mapOf(
                    "content-type" to "application/json",
                    "currentpageurl" to "/",
                    "currentplatform" to "dWeb",
                ),
                body = data.toString().encodeToByteArray(),
            )
        }
        val obj = response as? JsonObject
            ?: throw ExtractionError.Malformed("The MiniTV API was not an object.")
        val errors = obj["errors"] as? JsonArray
        if (errors != null && errors.isNotEmpty()) {
            val message = ((errors[0] as? JsonObject)?.str("message")) ?: "unknown error"
            throw ExtractionError.Unavailable("MiniTV said: $message")
        }
        if (data == null) return obj
        val operation = data.str("operationName")
            ?: throw ExtractionError.Malformed("The MiniTV call named no operation.")
        val payload = obj["data"] as? JsonObject
            ?: throw ExtractionError.Malformed("The MiniTV API returned no data.")
        return payload[operation]
            ?: throw ExtractionError.Malformed("The MiniTV API returned no $operation data.")
    }

    /** Upstream `_call_api` variables for the GraphQL calls. */
    protected fun graphQlVariables(operationName: String, variables: Map<String, String>): JsonObject =
        buildJsonObject {
            put("operationName", operationName)
            put(
                "variables",
                buildJsonObject {
                    for ((name, value) in variables) put(name, value)
                    put("contentType", "VOD")
                    // Upstream reads the guest `session-id` cookie; see the file header.
                    put("sessionIdToken", "")
                    put("clientId", "ATVIN")
                    put("deviceLocale", "en_GB")
                },
            )
            put("query", when (operationName) {
                "content" -> GRAPHQL_QUERY_CONTENT
                "getEpisodes" -> GRAPHQL_QUERY_EPISODES
                else -> GRAPHQL_QUERY_SEASONS
            })
        }

    companion object {
        /** Upstream `_GRAPHQL_QUERY_CONTENT`, verbatim. */
        const val GRAPHQL_QUERY_CONTENT: String = """
query content(${'$'}sessionIdToken: String!, ${'$'}deviceLocale: String, ${'$'}contentId: ID!, ${'$'}contentType: ContentType!, ${'$'}clientId: String) {
  content(
    applicationContextInput: {deviceLocale: ${'$'}deviceLocale, sessionIdToken: ${'$'}sessionIdToken, clientId: ${'$'}clientId}
    contentId: ${'$'}contentId
    contentType: ${'$'}contentType
  ) {
    contentId
    name
    ... on Episode {
      contentId
      vodType
      name
      images
      description {
        synopsis
        contentLengthInSeconds
      }
      publicReleaseDateUTC
      audioTracks
      seasonId
      seriesId
      seriesName
      seasonNumber
      episodeNumber
      timecode {
        endCreditsTime
      }
    }
    ... on MovieContent {
      contentId
      vodType
      name
      description {
        synopsis
        contentLengthInSeconds
      }
      images
      publicReleaseDateUTC
      audioTracks
    }
  }
}"""

        /** Upstream `_GRAPHQL_QUERY` of the season class, verbatim. */
        const val GRAPHQL_QUERY_EPISODES: String = """
query getEpisodes(${'$'}sessionIdToken: String!, ${'$'}clientId: String, ${'$'}episodeOrSeasonId: ID!, ${'$'}deviceLocale: String) {
  getEpisodes(
    applicationContextInput: {sessionIdToken: ${'$'}sessionIdToken, deviceLocale: ${'$'}deviceLocale, clientId: ${'$'}clientId}
    episodeOrSeasonId: ${'$'}episodeOrSeasonId
  ) {
    episodes {
      ... on Episode {
        contentId
        name
        images
        seriesName
        seasonId
        seriesId
        seasonNumber
        episodeNumber
        description {
          synopsis
          contentLengthInSeconds
        }
        publicReleaseDateUTC
      }
    }
  }
}
"""

        /** Upstream `_GRAPHQL_QUERY` of the series class, verbatim. */
        const val GRAPHQL_QUERY_SEASONS: String = """
query getSeasons(${'$'}sessionIdToken: String!, ${'$'}deviceLocale: String, ${'$'}episodeOrSeasonOrSeriesId: ID!, ${'$'}clientId: String) {
  getSeasons(
    applicationContextInput: {deviceLocale: ${'$'}deviceLocale, sessionIdToken: ${'$'}sessionIdToken, clientId: ${'$'}clientId}
    episodeOrSeasonOrSeriesId: ${'$'}episodeOrSeasonOrSeriesId
  ) {
    seasons {
      seasonId
    }
  }
}
"""
    }
}

/** Upstream `AmazonMiniTVIE`: one episode/movie page or `amazonminitv:` id. */
class AmazonMiniTvIE(
    http: ExtractorHttp,
) : AmazonMiniTvBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val asin = "amzn1.dv.gti.${matchId(url) ?: throw ExtractionError.UnsupportedUrl()}"
        val prs = callApi(asin) as? JsonObject
            ?: throw ExtractionError.Malformed("The MiniTV playback info was not an object.")

        val formats = mutableListOf<MediaFormat>()
        for ((type, asset) in prs.obj("playbackAssets").orEmpty()) {
            val manifestUrl = (asset as? JsonObject)?.str("manifestUrl") ?: continue
            when (type) {
                "hls" -> formats += MediaFormat(
                    formatId = type,
                    url = manifestUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
                "dash" -> formats += MediaFormat(
                    formatId = type,
                    url = manifestUrl,
                    ext = "mp4",
                    protocol = "mpd",
                )
                // An unknown asset type carries no usable manifest; skipped as upstream warns.
            }
        }

        val titleInfo = callApi(
            asin,
            data = graphQlVariables("content", mapOf("contentId" to asin)),
        ) as? JsonObject
            ?: throw ExtractionError.Malformed("The MiniTV title info was not an object.")

        val creditsTime = titleInfo.obj("timecode")?.number("endCreditsTime")?.let { it / 1000.0 }
        val chapters = if (creditsTime != null && creditsTime != 0.0) {
            listOf(Chapter(startTime = creditsTime, title = "End Credits"))
        } else {
            emptyList()
        }

        return InfoDict(
            id = asin,
            title = titleInfo.str("name"),
            formats = formats,
            thumbnails = titleInfo.obj("images").orEmpty().mapNotNull { (type, value) ->
                (value as? JsonPrimitive)?.content?.let { Thumbnail(url = it, id = type) }
            },
            description = titleInfo.obj("description")?.str("synopsis"),
            duration = titleInfo.obj("description")?.number("contentLengthInSeconds"),
            chapters = chapters,
            webpageUrl = url,
            extractor = "amazonminitv",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "AmazonMiniTV"

        val VALID_URL: Regex = Regex(
            "(?:https?://(?:www\\.)?amazon\\.in/minitv/tp/|amazonminitv:(?:amzn1\\.dv\\.gti\\.)?)" +
                "(?<id>[a-f0-9-]+)",
        )
    }
}

/** Upstream `AmazonMiniTVSeasonIE`: `amazonminitv:season:` ids. */
class AmazonMiniTvSeasonIE(
    http: ExtractorHttp,
) : AmazonMiniTvBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val asin = "amzn1.dv.gti.${matchId(url) ?: throw ExtractionError.UnsupportedUrl()}"
        val seasonInfo = callApi(
            asin,
            data = graphQlVariables("getEpisodes", mapOf("episodeOrSeasonId" to asin)),
        ) as? JsonObject
            ?: throw ExtractionError.Malformed("The MiniTV season info was not an object.")
        val entries = (seasonInfo["episodes"] as? JsonArray).orEmpty().mapNotNull { element ->
            val contentId = (element as? JsonObject)?.str("contentId") ?: return@mapNotNull null
            InfoEntry(id = contentId, url = "amazonminitv:$contentId")
        }
        return InfoDict(
            id = asin,
            entries = entries,
            webpageUrl = url,
            extractor = "amazonminitv:season",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "AmazonMiniTVSeason"

        val VALID_URL: Regex = Regex(
            "amazonminitv:season:(?:amzn1\\.dv\\.gti\\.)?(?<id>[a-f0-9-]+)",
        )
    }
}

/** Upstream `AmazonMiniTVSeriesIE`: `amazonminitv:series:` ids. */
class AmazonMiniTvSeriesIE(
    http: ExtractorHttp,
) : AmazonMiniTvBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val asin = "amzn1.dv.gti.${matchId(url) ?: throw ExtractionError.UnsupportedUrl()}"
        val seriesInfo = callApi(
            asin,
            data = graphQlVariables("getSeasons", mapOf("episodeOrSeasonOrSeriesId" to asin)),
        ) as? JsonObject
            ?: throw ExtractionError.Malformed("The MiniTV series info was not an object.")
        val entries = (seriesInfo["seasons"] as? JsonArray).orEmpty().mapNotNull { element ->
            val seasonId = (element as? JsonObject)?.str("seasonId") ?: return@mapNotNull null
            InfoEntry(id = seasonId, url = "amazonminitv:season:$seasonId")
        }
        return InfoDict(
            id = asin,
            entries = entries,
            webpageUrl = url,
            extractor = "amazonminitv:series",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "AmazonMiniTVSeries"

        val VALID_URL: Regex = Regex(
            "amazonminitv:series:(?:amzn1\\.dv\\.gti\\.)?(?<id>[a-f0-9-]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
