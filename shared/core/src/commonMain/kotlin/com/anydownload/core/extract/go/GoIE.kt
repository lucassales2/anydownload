/*
 * Go (TV Everywhere) extractor — AnyDownload
 *
 * Kotlin translation of `go.py` from `yt_dlp/extractor/go.py` at upstream
 * tag `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf),
 * read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `go.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the URL surface of the ABC/Freeform/DisneyNOW/FX/National
 * Geographic TV Everywhere pages. Every upstream test is skipped because
 * the flow needs Adobe Pass MSO credentials, so each URL form matches and
 * fails typed through the Adobe Pass base. The site software statements and
 * requestor ids the upstream file carries are not stored here.
 */
package com.anydownload.core.extract.go

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.adobepass.AdobePassIE
import com.anydownload.core.extract.InfoDict

/** Upstream `GoIE`: an ABC-family TV Everywhere page. */
class GoIE(
    http: ExtractorHttp,
) : AdobePassIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        mvpdAuthRequired()
    }

    companion object {
        const val IE_KEY: String = "Go"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:" +
                "(?<site>abc)\\.com|" +
                "(?<site2>freeform)\\.com|" +
                "(?<site3>disneynow)\\.com|" +
                "fxnow\\.(?<site4>fxnetworks)\\.com|" +
                "(?<site5>nationalgeographic)\\.com/tv" +
                ")/(?:video|episode|movies-and-specials)/" +
                "(?<id>[\\da-f]{8}-(?:[\\da-f]{4}-){3}[\\da-f]{12})",
        )
    }
}
