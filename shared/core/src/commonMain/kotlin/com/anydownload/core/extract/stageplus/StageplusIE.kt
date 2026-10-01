/*
 * STAGE+ extractor — AnyDownload
 *
 * Kotlin translation of `stageplus.py` from
 * `yt_dlp/extractor/stageplus.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `stageplus.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `vod_concert_*` URL forms match and fail typed LoginRequired.
 * Upstream `_real_initialize` refuses to run without a `dgplus_access_token`
 * cookie or the OAuth password login, and the GraphQL concert query sends
 * that token as a Bearer header while every HLS URL carries it as a `token`
 * query parameter, so neither the query nor the stream walk is translated.
 * No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.stageplus

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

/** Upstream `StagePlusVODConcertIE`: a stage-plus.com VOD concert. */
class StagePlusVODConcertIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // Upstream `_real_initialize` raises a login requirement until the
        // `dgplus_access_token` cookie or the OAuth login sets `_TOKEN`.
        throw ExtractionError.LoginRequired(
            "STAGE+ needs the dgplus_access_token cookie or an account login for its concert query and streams.",
        )
    }

    companion object {
        const val IE_KEY: String = "StagePlusVODConcert"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?stage-plus\\.com/video/(?<id>vod_concert_\\w+)",
        )
    }
}
