/*
 * Wrestle Universe extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `wrestleuniverse.py` from
 * `yt_dlp/extractor/wrestleuniverse.py` at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `wrestleuniverse.py` is not
 * vendored; see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the VOD and PPV/live URL surfaces. The API needs the `token`
 * cookie (or a Firebase email/password login) and the encrypted stream API
 * needs RSA key exchange, so every URL form matches and fails typed. No API
 * key, device id, token, or media URL is stored here.
 */
package com.anydownload.core.extract.wrestleuniverse

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val URL_TMPL = "https?://(?:www\\.)?wrestle-universe\\.com/(?:(?<lang>\\w{2})/)?%s/(?<id>\\w+)"

private const val LOGIN_WALL =
    "The Wrestle Universe API needs the token cookie (or a Firebase login) and the encrypted " +
        "stream API needs RSA key exchange, which the port does not carry."

/** Upstream `WrestleUniverseVODIE`: a VOD video. */
class WrestleUniverseVODIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "WrestleUniverseVOD"

        val VALID_URL: Regex = Regex(URL_TMPL.replace("%s", "videos"))
    }
}

/** Upstream `WrestleUniversePPVIE`: a PPV/live video. */
class WrestleUniversePPVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "WrestleUniversePPV"

        val VALID_URL: Regex = Regex(URL_TMPL.replace("%s", "lives"))
    }
}
