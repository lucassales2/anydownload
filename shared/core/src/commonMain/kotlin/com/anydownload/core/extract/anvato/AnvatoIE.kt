/*
 * Anvato extractor — AnyDownload
 *
 * Kotlin translation of the URL surface of `anvato.py` from
 * `yt_dlp/extractor/anvato.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `anvato.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `anvato:<access_key_or_mcp>:<id>` URL form matches and fails
 * typed. The MCP video JSON needs an AES-encrypted `X-Anvato-Adst-Auth`
 * header plus the access-key/MCP tables and the server-time signature; the
 * port has no AES-encryption helper, so the auth header, the video JSON, the
 * SMIL/m3u8/caption walk, and the webpage player scan are not translated. No
 * key, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.anvato

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

/** Upstream `AnvatoIE`: the MCP player API. */
class AnvatoIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(
        "The Anvato MCP API needs an AES-encrypted X-Anvato-Adst-Auth header; the port does not " +
            "add an AES-encryption helper.",
    )

    companion object {
        const val IE_KEY: String = "Anvato"

        val VALID_URL: Regex = Regex("anvato:(?<accessKeyOrMcp>[^:]+):(?<id>\\d+)")
    }
}
