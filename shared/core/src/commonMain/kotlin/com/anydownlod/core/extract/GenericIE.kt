/*
 * Generic extractor on the extractor base — AnyDownload
 *
 * `GenericIE` sits on [InfoExtractor] and reuses the T-045 HTML5-media subset
 * of yt-dlp's `yt_dlp/extractor/generic.py` at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-24.
 * Unlicense; see shared/core/NOTICE.md. `generic.py` is not vendored.
 */
package com.anydownlod.core.extract

import com.anydownlod.core.engine.UrlPolicy

/**
 * The fallback extractor. It downloads the page through [ExtractorHttp] (the
 * caller already validated the user URL), asks the T-045 subset for exactly
 * one media URL (every candidate still passes [UrlPolicy]), and maps the
 * result to an [InfoDict] with one [MediaFormat] or a typed
 * [ExtractionError].
 */
class GenericIE(http: ExtractorHttp) : InfoExtractor(
    ieKey = ExtractorRegistry.GENERIC_KEY,
    http = http,
    validUrl = Regex("""https?://[^<]+""", RegexOption.IGNORE_CASE),
) {
    override val displayName: String = "Generic"

    override suspend fun extract(url: String): InfoDict {
        val html = http.downloadWebpage(url)
        return when (val extraction = GenericExtractor.extract(url, html)) {
            is GenericExtraction.Direct -> InfoDict(
                title = pageTitle(html),
                url = extraction.url,
                formats = listOf(
                    MediaFormat(
                        formatId = "0",
                        url = extraction.url,
                        ext = extensionFromUrl(extraction.url),
                    ),
                ),
                webpageUrl = url,
                extractor = "Generic",
                extractorKey = ieKey,
            )

            is GenericExtraction.Failed -> throw when (extraction.reason) {
                GenericExtractionFailure.UnsupportedPageUrl -> ExtractionError.UnsupportedUrl()
                GenericExtractionFailure.NoMedia -> ExtractionError.NoFormats()
                GenericExtractionFailure.MultipleMedia -> ExtractionError.Unavailable(
                    "This page has more than one media element, so the app cannot choose one.",
                )
            }
        }
    }

    private fun pageTitle(html: String): String? =
        ExtractorUtils.searchRegex(
            pattern = "<title[^>]*>(.*?)</title>",
            string = html,
            flags = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )?.trim()?.takeIf { it.isNotEmpty() }?.let { ExtractorUtils.unescapeHtml(it) }

    private fun extensionFromUrl(url: String): String? =
        url.substringBefore('?').substringBefore('#').substringAfterLast('/', "")
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.isNotEmpty() && it.length <= 5 && it.all { char -> char.isLetterOrDigit() } }
}
