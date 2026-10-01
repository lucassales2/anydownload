/*
 * Hotstar extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `hotstar.py` from
 * `yt_dlp/extractor/hotstar.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `hotstar.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: every URL form matches and fails typed. The Hotstar API sends
 * `x-hs-usertoken` and device-id headers from cookies and requests Widevine
 * DRM playback parameters; the port excludes both. No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.hotstar

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "The Hotstar API needs an x-hs-usertoken cookie and a device id, and it requests Widevine DRM " +
        "playback parameters; the port excludes both."

/** Upstream `HotStarIE`: a movie, episode, or clip. */
class HotStarIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "HotStar"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?hotstar\\.com/(?:[^/?#]+/)*(?<id>\\d+)",
        )
    }
}

/** Upstream `HotStarPrefixIE`: the legacy `hotstar:` prefix. */
class HotStarPrefixIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "HotStarPrefix"

        val VALID_URL: Regex = Regex("hotstar:(?:(?<type>\\w+):)?(?<id>\\d+)$")
    }
}

/** Upstream `HotStarSeriesIE`: a series page. */
class HotStarSeriesIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.LoginRequired(WALL)

    companion object {
        const val IE_KEY: String = "HotStarSeries"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?hotstar\\.com(?:/in)?/(?:tv|shows)/[^/]+/(?<id>\\d+)/?(?:[#?]|$)",
        )
    }
}
