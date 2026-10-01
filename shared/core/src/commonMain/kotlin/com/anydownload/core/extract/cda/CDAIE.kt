/*
 * CDA extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `cda.py` from
 * `yt_dlp/extractor/cda.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `cda.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: both URL forms match and fail typed. The CDA app API needs an
 * OAuth bearer token minted with hardcoded Basic client credentials and an
 * HMAC password hash, and the web player `file` fields are ROT13/encrypted
 * before a signed request; the port does not translate the token/crypto
 * flow. No key, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.cda

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "The CDA app API needs an OAuth bearer token minted with hardcoded Basic client credentials " +
        "and the web player file fields are encrypted before a signed request; the port does not " +
        "translate the token/crypto flow."

/** Upstream `CDAIE`: a video page. */
class CDAIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "CDA"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:(?:www|m)\\.)?cda\\.pl/video|ebd\\.cda\\.pl/[0-9]+x[0-9]+)/(?<id>[0-9a-z]+)",
        )
    }
}

/** Upstream `CDAFolderIE`: a folder listing. */
class CDAFolderIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "CDAFolder"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|m)\\.)?cda\\.pl/(?<channel>[\\w-]+)/folder/(?<id>\\d+)",
        )
    }
}
