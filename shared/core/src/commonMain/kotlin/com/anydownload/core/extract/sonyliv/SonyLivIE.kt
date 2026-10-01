/*
 * SonyLIV extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `sonyliv.py` from
 * `yt_dlp/extractor/sonyliv.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `sonyliv.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the anonymous `GETTOKEN` call, the VOD playback JSON (one DASH row
 * and one HLS row from the same `videoURL`), the detail metadata, and the
 * season/episode pagination. Limitations: the platform's request-header
 * allowlist refuses the `security_token` header upstream sends on every call
 * (the same rule as `streaks` and `x-addr`), so a real API may refuse the
 * call; the OTP/login flow is not translated and a subscription wall fails
 * typed; an `isEncrypted` asset fails typed DRM; the `timestamp`,
 * `season_number`, `series`, `episode_number`, and `release_year` fields the
 * info dict does not model are dropped; and the `sort_order` extractor arg is
 * not carried (ascending only). No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.sonyliv

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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val API_BASE = "https://apiv2.sonyliv.com/AGL"

/** Upstream `SonyLIVIE` and `SonyLIVSeriesIE`: the shared API call. */
abstract class SonyLivBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_call_api`: the `resultObj` of one AGL response. */
    protected suspend fun callApi(
        version: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        segment: String = "A/ENG/WEB",
    ): JsonElement {
        val response = http.downloadJson("$API_BASE/$version/$segment/$path", headers = headers)
        val obj = response as? JsonObject
            ?: throw ExtractionError.Malformed("The SonyLIV API was not an object.")
        return obj["resultObj"]
            ?: throw ExtractionError.Malformed("The SonyLIV API returned no result.")
    }

    /** The anonymous `security_token` upstream keeps in `_HEADERS`. */
    protected suspend fun securityToken(headers: Map<String, String> = emptyMap()): String? =
        (callApi("1.4", "ALL/GETTOKEN", headers) as? JsonPrimitive)?.content
}

/** Upstream `SonyLIVIE`: one episode, movie, clip, trailer, or music video. */
class SonyLivIE(
    http: ExtractorHttp,
) : SonyLivBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_initialize_pre_login`; the header is refused by the allowlist.
        val token = securityToken()
        val apiHeaders = token?.let { mapOf("security_token" to it) } ?: emptyMap()

        val content = callApi(
            "1.5",
            "IN/CONTENT/VIDEOURL/VOD/$videoId",
            apiHeaders,
        ) as? JsonObject ?: throw ExtractionError.Malformed("The SonyLIV content JSON was not an object.")
        if (content.boolean("isEncrypted") == true) {
            throw ExtractionError.Unavailable("The video is DRM protected.")
        }
        val dashUrl = content.str("videoURL")
            ?: throw ExtractionError.Malformed("The SonyLIV content JSON carried no video URL.")

        val formats = mutableListOf(
            MediaFormat(formatId = "dash", url = dashUrl, ext = "mp4", protocol = "mpd"),
            MediaFormat(
                formatId = "hls",
                url = dashUrl.replace(".mpd", ".m3u8").replace("/DASH/", "/HLS/"),
                ext = "mp4",
                protocol = "m3u8_native",
            ),
        )

        val metadata = ((callApi("1.6", "IN/DETAIL/$videoId", apiHeaders) as? JsonObject)
            ?.firstContainer()?.obj("metadata"))
            ?: throw ExtractionError.Malformed("The SonyLIV detail JSON carried no metadata.")

        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in (content["subtitle"] as? JsonArray).orEmpty()) {
            val sub = element as? JsonObject ?: continue
            val subUrl = sub.str("subtitleUrl") ?: continue
            val language = sub.str("subtitleLanguageName") ?: "ENG"
            val existing = subtitles.indexOfFirst { it.language == language }
            val track = SubtitleTrack(
                language = language,
                formats = listOf(
                    SubtitleFormat(ext = ExtractorUtils.determineExt(subUrl, "vtt"), url = subUrl),
                ),
            )
            if (existing >= 0) {
                subtitles[existing] = subtitles[existing].copy(
                    formats = subtitles[existing].formats + track.formats,
                )
            } else {
                subtitles += track
            }
        }

        return InfoDict(
            id = videoId,
            title = metadata.str("episodeTitle"),
            formats = formats,
            thumbnails = content.str("posterURL")?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            description = metadata.str("longDescription") ?: metadata.str("shortDescription"),
            duration = metadata.number("duration"),
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "sonyliv",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "SonyLIV"

        val VALID_URL: Regex = Regex(
            "(?:(?:sonyliv:)|(?:https?://(?:www\\.)?sonyliv\\.com/" +
                "(?:s(?:how|port)s/[^/]+|movies|clip|trailer|music-videos)/[^/?#&]+-))(?<id>\\d+)",
        )
    }
}

/** Upstream `SonyLIVSeriesIE`: the paginated episode list of a show. */
class SonyLivSeriesIE(
    http: ExtractorHttp,
) : SonyLivBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val showId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val headers = mapOf("accept" to "application/json, text/plain, */*", "referer" to "https://www.sonyliv.com")
        // The header is refused by the allowlist; the token fetch stays for fidelity.
        val token = securityToken(headers)
        val apiHeaders = headers + (token?.let { mapOf("security_token" to it) } ?: emptyMap())

        val seasonsResponse = callApi(
            "1.9",
            "IN/DL/DETAIL/$showId?kids_safe=false&from=0&to=49",
            apiHeaders,
            segment = "R/ENG/WEB",
        ) as? JsonObject
            ?: throw ExtractionError.Malformed("The SonyLIV series JSON was not an object.")
        val seasons = seasonsResponse.firstContainer()?.array("containers").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.takeIf { it.number("id") != null }
        }

        val entries = mutableListOf<InfoEntry>()
        for (season in seasons) {
            val seasonId = season.str("id") ?: continue
            var cursor = 0
            var pages = 0
            while (pages < MAX_PAGES) {
                pages++
                val page = callApi(
                    "1.4",
                    "IN/CONTENT/DETAIL/BUNDLE/$seasonId" +
                        "?from=$cursor&to=${cursor + 99}&orderBy=episodeNumber&sortOrder=asc",
                    apiHeaders,
                    segment = "R/ENG/WEB",
                ) as? JsonObject
                    ?: throw ExtractionError.Malformed("The SonyLIV bundle JSON was not an object.")
                val episodes = page.firstContainer()?.array("containers").orEmpty().mapNotNull { element ->
                    (element as? JsonObject)?.takeIf { it.number("id") != null }
                }
                if (episodes.isEmpty()) break
                for (episode in episodes) {
                    val videoId = episode.str("id") ?: continue
                    entries += InfoEntry(id = videoId, url = "sonyliv:$videoId")
                }
                cursor += 100
            }
        }

        return InfoDict(
            id = showId,
            entries = entries,
            webpageUrl = url,
            extractor = "sonyliv:series",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "SonyLIVSeries"

        private const val MAX_PAGES = 100

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?sonyliv\\.com/shows/[^/?#&]+-(?<id>\\d{10})/?(?:${'$'}|[?#])",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `traverse_obj(..., ('containers', 0, 'containers'))` support. */
private fun JsonObject.firstContainer(): JsonObject? =
    (this["containers"] as? JsonArray)?.firstOrNull() as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
