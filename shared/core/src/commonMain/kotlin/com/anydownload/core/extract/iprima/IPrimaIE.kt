/*
 * iPrima extractors — AnyDownload
 *
 * Kotlin translation of `iprima.py` from `yt_dlp/extractor/iprima.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `iprima.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `IPrimaIE` matches every non-CNN iPrima URL form and fails typed
 * LoginRequired — upstream `_real_initialize` requires an email/password
 * session token before any content call, and the `X-OTT-Access-Token` header
 * is refused by the platform allowlist in any case (the playsuisse rule).
 * `IPrimaCNNIE` translates the public CNN flow: the page title, the player id
 * search, the `prehravac/init` player options, the HLS tracks (DASH tracks
 * are skipped exactly as the upstream dead `return` does), the `src:` regex
 * fallback, and the geo marker. Limitations: the `ott_adult_confirmed`
 * cookie upstream sets cannot be set by an extractor here; an m3u8 URL
 * becomes one HLS row. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.iprima

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

private const val LOGIN_WALL = "Login is required to access any iPrima content."

/** Upstream `IPrimaIE`: every non-CNN iPrima URL form, a typed login wall. */
class IPrimaIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_real_initialize` needs `_perform_login`'s email/password
        // session token before any content call; the play API also sends it in
        // the `X-OTT-Access-Token` header the allowlist refuses.
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "IPrima"

        val VALID_URL: Regex = Regex(
            "https?://(?!cnn)(?:[^/]+)\\.iprima\\.cz/(?:[^/]+/)*(?<id>[^/?#&]+)",
        )
    }
}

/** Upstream `IPrimaCNNIE`: the public cnn.iprima.cz player flow. */
class IPrimaCNNIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_set_cookie('play.iprima.cz', 'ott_adult_confirmed', '1')`
        // cannot be set by an extractor here; the page and player are public.

        val webpage = http.downloadWebpage(url)
        val title = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
            ?: ExtractorUtils.searchRegex("<h1>([^<]+)", webpage)

        val realId = searchRealId(webpage)
            ?: throw ExtractionError.Malformed("Unable to extract the iPrima video id from the webpage.")

        val playerPage = http.downloadWebpage(
            "http://play.iprima.cz/prehravac/init" +
                "?_infuse=1&_ts=${Clock.System.now().toEpochMilliseconds() / 1000}&productId=$realId",
            headers = mapOf("referer" to url),
        )

        val formats = mutableListOf<MediaFormat>()

        fun addFormats(formatUrl: String, formatKey: String?, lang: String?) {
            val ext = ExtractorUtils.determineExt(formatUrl)
            when {
                formatKey == "hls" || ext == "m3u8" -> formats += MediaFormat(
                    formatId = "hls",
                    url = formatUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                    language = lang,
                )
                // Upstream returns before adding DASH tracks; kept as a skip.
                formatKey == "dash" || ext == "mpd" -> Unit
            }
        }

        val optionsText = ExtractorUtils.searchRegex(
            "(?s)(?:TDIPlayerOptions|playerOptions)\\s*=\\s*(\\{.+?\\});\\s*\\]\\]",
            playerPage,
            setOf(RegexOption.DOT_MATCHES_ALL),
            default = "{}",
        )
        val options = ExtractorUtils.parseJson(ExtractorUtils.jsToJson(optionsText ?: "{}"))
        val tracks = (options as? JsonObject)?.get("tracks") as? JsonObject
        if (tracks != null) {
            for ((key, value) in tracks) {
                val trackList = value as? JsonArray ?: continue
                for (element in trackList) {
                    val track = element as? JsonObject ?: continue
                    val src = track.str("src") ?: continue
                    addFormats(src, key.lowercase(), track.str("lang"))
                }
            }
        }

        if (formats.isEmpty()) {
            for (match in Regex("src[\"']\\s*:\\s*([\"'])(.+?)\\1").findAll(playerPage)) {
                addFormats(match.groupValues[2], null, null)
            }
        }

        if (formats.isEmpty() && ">GEO_IP_NOT_ALLOWED<" in playerPage) {
            throw ExtractionError.GeoRestricted(listOf("CZ"))
        }

        return InfoDict(
            id = realId,
            title = title,
            formats = formats,
            thumbnails = ExtractorUtils.htmlSearchMeta(webpage, "thumbnail", "og:image", "twitter:image")
                ?.let { listOf(Thumbnail(url = it)) }
                .orEmpty(),
            description = ExtractorUtils.htmlSearchMeta(webpage, "description", "og:description", "twitter:description"),
            webpageUrl = url,
            extractor = "iprima:cnn",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_search_regex` id alternatives, in order. */
    private fun searchRealId(webpage: String): String? {
        val patterns = listOf(
            "<iframe[^>]+\\bsrc=[\"'](?:https?:)?//" +
                "(?:api\\.play-backend\\.iprima\\.cz/prehravac/embedded|prima\\.iprima\\.cz/[^/]+/[^/]+)" +
                "\\?.*?\\bid=(p\\d+)",
            "data-product=\"([^\"]+)\">",
            "id=[\"']player-(p\\d+)\"",
            "playerId\\s*:\\s*[\"']player-(p\\d+)",
            "\\bvideos\\s*=\\s*[\"'](p\\d+)",
        )
        for (pattern in patterns) {
            ExtractorUtils.searchRegex(pattern, webpage)?.let { return it }
        }
        return null
    }

    companion object {
        const val IE_KEY: String = "IPrimaCNN"

        val VALID_URL: Regex = Regex(
            "https?://cnn\\.iprima\\.cz/(?:[^/]+/)*(?<id>[^/?#&]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
