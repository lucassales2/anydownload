/*
 * Radiko extractors — AnyDownload
 *
 * Kotlin translation of the URL shapes of `radiko.py` from
 * `yt_dlp/extractor/radiko.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `radiko.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: both URL families match and fail typed. Every stream starts at the
 * `v2/api/auth1` handshake, which needs the `X-Radiko-*` request headers and
 * reads the auth token/key length/key offset from the response headers; the
 * platform allowlist refuses those request headers and strips those response
 * headers by design, and the partial key is cut from the player's full key
 * (upstream also embeds one as a fallback), which the port does not carry
 * (the naver/zingmp3/abc rule). The program XML walk and the m3u8
 * `preference`/`ffmpeg_args` shaping are not translated. No cookie, token,
 * or signed media URL is stored here.
 */
package com.anydownload.core.extract.radiko

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val AUTH_WALL =
    "Radiko streams need the X-Radiko auth handshake (partial key and token " +
        "headers) that this port does not carry."

/** Upstream `RadikoBaseIE`: the shared auth wall. */
abstract class RadikoBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected fun authWall(): Nothing = throw ExtractionError.Unavailable(AUTH_WALL)
}

/** Upstream `RadikoIE`: a time-free (timeshift) URL. */
class RadikoIE(
    http: ExtractorHttp,
) : RadikoBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        if (!VALID_URL.containsMatchIn(url)) throw ExtractionError.UnsupportedUrl()
        // Upstream `_auth_client` -> `_negotiate_token`: the X-Radiko handshake.
        authWall()
    }

    companion object {
        const val IE_KEY: String = "Radiko"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?radiko\\.jp/#!/ts/(?<station>[A-Z0-9-]+)/(?<timestring>\\d+)",
        )
    }
}

/** Upstream `RadikoRadioIE`: a live station URL. */
class RadikoRadioIE(
    http: ExtractorHttp,
) : RadikoBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_auth_client` -> `_negotiate_token`: the X-Radiko handshake.
        authWall()
    }

    companion object {
        const val IE_KEY: String = "RadikoRadio"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?radiko\\.jp/#!/live/(?<id>[A-Z0-9-]+)",
        )
    }
}
