/*
 * Adobe Pass base extractor — AnyDownload
 *
 * Kotlin translation of `AdobePassIE` from `yt_dlp/extractor/adobepass.py` at
 * upstream tag `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf),
 * read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `adobepass.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the parts of the TV Everywhere base class that need no account
 * state. `_get_mvpd_resource` builds the v-chip RSS resource string (with
 * XML text escaping, matching ElementTree's serialization) and
 * `_extract_mvpd_auth` becomes the typed login-wall failure: the upstream
 * flow loads the MSO list, posts provider credentials, registers a device,
 * and caches SAML/OAuth tokens. None of that is translated, so no credential,
 * cookie, token, or provider endpoint is stored here.
 *
 * `AdobePassIE` has no `_VALID_URL` upstream and is not a registered
 * extractor; it is a base class for the TV Everywhere site extractors
 * (NBC, ABC, ESPN, Brightcove, and others), which extend it to reach this
 * resource string and the typed failure.
 */
package com.anydownload.core.extract.adobepass

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoExtractor

/**
 * Upstream `AdobePassIE`: the Adobe Pass Multiple-system operator (MSO)
 * base class. A site extractor calls [mvpdAuthRequired] when the upstream
 * flow would ask for TV provider credentials; the port fails typed.
 */
abstract class AdobePassIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /**
     * Upstream `_real_extract`'s `_extract_mvpd_auth` boundary. The Adobe
     * Pass flow needs a participating TV provider account (upstream
     * `--ap-mso` / `--ap-username` / `--ap-password`), so the port raises the
     * typed login wall before any credential or device request.
     */
    fun mvpdAuthRequired(): Nothing = throw ExtractionError.LoginRequired(LOGIN_WALL_MESSAGE)

    /**
     * Upstream `_get_mvpd_resource`: the v-chip RSS resource string sent as
     * the `resource` query parameter. ElementTree XML-escapes text nodes and
     * self-closes the rating element when there is no rating, which this
     * mirrors.
     */
    fun mvpdResource(
        providerId: String,
        title: String,
        guid: String,
        rating: String?,
    ): String {
        val ratingElement = if (rating.isNullOrEmpty()) {
            "<media:rating scheme=\"urn:v-chip\" />"
        } else {
            "<media:rating scheme=\"urn:v-chip\">" + xmlText(rating) + "</media:rating>"
        }
        return "<rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss/\"><channel>" +
            "<title>" + xmlText(providerId) + "</title>" +
            "<item><title>" + xmlText(title) + "</title>" +
            "<guid>" + xmlText(guid) + "</guid>" +
            ratingElement +
            "</item></channel></rss>"
    }

    private fun xmlText(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    companion object {
        /** The typed reason on every Adobe Pass path the port does not translate. */
        const val LOGIN_WALL_MESSAGE: String =
            "This video is only available to users of participating TV providers; " +
                "a TV provider sign-in is needed."
    }
}
