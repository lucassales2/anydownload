/*
 * Stacommu / Theater Complex Town extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `stacommu.py` from
 * `yt_dlp/extractor/stacommu.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `stacommu.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the VOD/live URL surfaces of Stacommu and Theater Complex Town.
 * Both extend `WrestleUniverseBaseIE`, whose API needs the `token` cookie
 * (or a Firebase email/password login) and whose stream endpoint is an
 * RSA-OAEP encrypted exchange that needs pycryptodomex; the port carries
 * neither (the same rule as `wrestleuniverse`, `naver`, and `zingmp3`), so
 * every URL form matches and fails typed. No API key, device id, token, or
 * media URL is stored here.
 */
package com.anydownload.core.extract.stacommu

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val STACOMMU_WALL =
    "The Stacommu API needs the Firebase token cookie (or a login) and the encrypted " +
        "stream API needs RSA key exchange, which the port does not carry."

private const val THEATER_WALL =
    "The Theater Complex Town API needs the Firebase token cookie (or a login) and the " +
        "encrypted stream API needs RSA key exchange, which the port does not carry."

/** Upstream `StacommuVODIE`: a Stacommu video episode. */
class StacommuVODIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(STACOMMU_WALL)
    }

    companion object {
        const val IE_KEY: String = "StacommuVOD"

        val VALID_URL: Regex = Regex(
            "https?://www\\.stacommu\\.jp/(?:en/)?videos/episodes/(?<id>[\\da-zA-Z]+)",
        )
    }
}

/** Upstream `StacommuLiveIE`: a Stacommu live/PPV event. */
class StacommuLiveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(STACOMMU_WALL)
    }

    companion object {
        const val IE_KEY: String = "StacommuLive"

        val VALID_URL: Regex = Regex(
            "https?://www\\.stacommu\\.jp/(?:en/)?live/(?<id>[\\da-zA-Z]+)",
        )
    }
}

/** Upstream `TheaterComplexTownVODIE`: a Theater Complex Town video episode. */
class TheaterComplexTownVODIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(THEATER_WALL)
    }

    companion object {
        const val IE_KEY: String = "TheaterComplexTownVOD"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?theater-complex\\.town/(?:(?:en|ja)/)?" +
                "videos/episodes/(?<id>\\w+)",
        )
    }
}

/** Upstream `TheaterComplexTownPPVIE`: a Theater Complex Town PPV/live event. */
class TheaterComplexTownPPVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(THEATER_WALL)
    }

    companion object {
        const val IE_KEY: String = "TheaterComplexTownPPV"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?theater-complex\\.town/(?:(?:en|ja)/)?" +
                "(?:ppv|live)/(?<id>\\w+)",
        )
    }
}
