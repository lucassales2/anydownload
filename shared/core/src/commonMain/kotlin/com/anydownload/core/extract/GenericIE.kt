/*
 * Generic extractor on the extractor base — AnyDownload
 *
 * `GenericIE` sits on [InfoExtractor] and reuses the T-045 HTML5-media subset
 * plus the T-137 embed/iframe/JSON-LD/meta-refresh slice of yt-dlp's
 * `yt_dlp/extractor/generic.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), re-read 2026-09-29. Unlicense;
 * see shared/core/NOTICE.md. `generic.py` is not vendored.
 */
package com.anydownload.core.extract

import com.anydownload.core.engine.UrlCheck
import com.anydownload.core.engine.UrlPolicy

/**
 * The fallback extractor. It downloads the page through [ExtractorHttp] (the
 * caller already validated the user URL), asks the T-045/T-137 subset for
 * exactly one media URL (every candidate still passes [UrlPolicy]), and maps
 * the result to an [InfoDict] with one [MediaFormat] or a typed
 * [ExtractionError]. When the page declares no media but carries a meta
 * refresh, the target is followed at most once: a direct media target is
 * returned without a second fetch, and a page target is fetched once and
 * scanned the same way.
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
            is GenericExtraction.Direct -> infoDict(extraction.url, html, url)
            is GenericExtraction.Failed -> {
                // T-137: a page with no media of its own may be a meta-refresh
                // hop. Follow it once, through the same policy, then fail.
                if (extraction.reason == GenericExtractionFailure.NoMedia) {
                    followRefreshOnce(url, html)?.let { return it }
                }
                throw failure(extraction.reason)
            }
        }
    }

    /** One bounded meta-refresh hop, or null when there is none usable. */
    private suspend fun followRefreshOnce(url: String, html: String): InfoDict? {
        val rawTarget = GenericExtractor.metaRefreshTarget(html) ?: return null
        val target = GenericExtractor.resolveAgainst(url, rawTarget) ?: return null
        if (target == url) return null
        if (UrlPolicy.check(target) !is UrlCheck.Allowed) return null
        if (GenericExtractor.isDirectMediaUrl(target)) {
            // The refresh points straight at a media file; do not fetch it as HTML.
            return infoDict(target, html, url)
        }
        val targetHtml = http.downloadWebpage(target)
        return when (val extraction = GenericExtractor.extract(target, targetHtml)) {
            is GenericExtraction.Direct -> infoDict(extraction.url, targetHtml, url)
            is GenericExtraction.Failed -> throw failure(extraction.reason)
        }
    }

    private fun infoDict(mediaUrl: String, titleHtml: String, webpageUrl: String): InfoDict = InfoDict(
        title = pageTitle(titleHtml),
        url = mediaUrl,
        formats = listOf(
            MediaFormat(
                formatId = "0",
                url = mediaUrl,
                ext = extensionFromUrl(mediaUrl),
            ),
        ),
        webpageUrl = webpageUrl,
        extractor = "Generic",
        extractorKey = ieKey,
    )

    private fun failure(reason: GenericExtractionFailure): ExtractionError = when (reason) {
        GenericExtractionFailure.UnsupportedPageUrl -> ExtractionError.UnsupportedUrl()
        GenericExtractionFailure.NoMedia -> ExtractionError.NoFormats()
        GenericExtractionFailure.MultipleMedia -> ExtractionError.Unavailable(
            "This page has more than one media element, so the app cannot choose one.",
        )
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
