/*
 * VRT extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `vrt.py` from
 * `yt_dlp/extractor/vrt.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `vrt.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the VRT NWS/Sporza article pages, the VRT MAX (formerly VRT NU)
 * pages, the dagelijksekost recipe pages, and the Radio 1 pages match and
 * fail typed. Every media URL comes from the VRT media-services API, whose
 * `vrtPlayerToken` is a JWT signed with the web client key (HMAC-SHA256); the
 * port does not add a signing helper, so the token call, the API, the format
 * walk, and the VRT MAX login are not translated. No cookie, token, or signed
 * media URL is stored here.
 */
package com.anydownload.core.extract.vrt

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

/** Upstream `VRTBaseIE`: the shared player-token wall. */
abstract class VRTBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Every VRT media path needs the signed player token. */
    protected fun tokenWall(): Nothing = throw ExtractionError.Unavailable(TOKEN_WALL_MESSAGE)

    companion object {
        /** The one-sentence reason on every VRT URL form. */
        const val TOKEN_WALL_MESSAGE: String =
            "The VRT media-services API needs a JWT vrtPlayerToken signed with the web client " +
                "key (HMAC-SHA256); the signing helper and the API are not translated."
    }
}

/** Upstream `VRTIE`: the VRT NWS and Sporza article pages. */
class VRTIE(
    http: ExtractorHttp,
) : VRTBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = tokenWall()

    companion object {
        const val IE_KEY: String = "VRT"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?<site>vrt\\.be/vrtnws|sporza\\.be)/[a-z]{2}/\\d{4}/\\d{2}/\\d{2}/" +
                "(?<id>[^/?&#]+)",
        )
    }
}

/** Upstream `VrtNUIE`: the VRT MAX / VRT NU pages. */
class VrtNUIE(
    http: ExtractorHttp,
) : VRTBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = tokenWall()

    companion object {
        const val IE_KEY: String = "VrtNU"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?vrt\\.be/(?:vrtnu|vrtmax)/a-z/(?:[^/]+/){2}(?<id>[^/?#&]+)",
        )
    }
}

/** Upstream `DagelijkseKostIE`: the dagelijksekost recipe pages. */
class DagelijkseKostIE(
    http: ExtractorHttp,
) : VRTBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = tokenWall()

    companion object {
        const val IE_KEY: String = "DagelijkseKost"

        val VALID_URL: Regex = Regex("https?://dagelijksekost\\.een\\.be/gerechten/(?<id>[^/?#&]+)")
    }
}

/** Upstream `Radio1BeIE`: the Radio 1 pages. */
class Radio1BeIE(
    http: ExtractorHttp,
) : VRTBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = tokenWall()

    companion object {
        const val IE_KEY: String = "Radio1Be"

        val VALID_URL: Regex = Regex("https?://radio1\\.be/(?:lees|luister/select)/(?<id>[\\w/-]+)")
    }
}
