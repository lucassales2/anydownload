/*
 * Play Suisse extractor — AnyDownload
 *
 * Kotlin translation of `playsuisse.py` from
 * `yt_dlp/extractor/playsuisse.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `playsuisse.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the watch/detail URL forms match and fail typed LoginRequired. The
 * upstream extractor refuses to run without `_ID_TOKEN` (`raise_login_required
 * (method='password')`), and that token comes from the OAuth password login
 * flow; the GraphQL asset query is only the first step after it, and the HLS
 * URLs carry the `id_token` query, so neither is translated. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.playsuisse

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

/** Upstream `PlaySuisseIE`: a playsuisse.ch watch/detail page. */
class PlaySuisseIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_real_extract` starts with
        // `self.raise_login_required(method='password')` until `_ID_TOKEN` is
        // set by the OAuth login flow.
        throw ExtractionError.LoginRequired(
            "Play Suisse needs the OAuth password login token for its asset query and streams.",
        )
    }

    companion object {
        const val IE_KEY: String = "PlaySuisse"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?playsuisse\\.ch/(?:watch|detail)/" +
                "(?:[^#]*[?&]episodeId=)?(?<id>\\d+)",
        )
    }
}
