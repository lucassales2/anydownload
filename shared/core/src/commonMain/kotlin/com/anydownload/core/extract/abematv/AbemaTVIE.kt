/*
 * AbemaTV extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `abematv.py` from
 * `yt_dlp/extractor/abematv.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `abematv.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: both URL forms match and fail typed. The API needs a device token
 * whose `applicationKeySecret` is an HMAC-SHA256 mix over a secret
 * application key, the media token needs that bearer, and the streams are
 * DRM-protected (upstream installs a Widevine license request handler), all
 * of which the port excludes. No key, token, or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.abematv

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "The Abema API needs a device token signed with a secret application key and its streams are " +
        "DRM-protected (Widevine license handler); the port excludes both."

/** Upstream `AbemaTVIE`: episodes, channels, and now-on-air slots. */
class AbemaTVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "AbemaTV"

        val VALID_URL: Regex = Regex(
            "https?://abema\\.tv/(?<type>now-on-air|video/episode|channels/.+?/slots)/(?<id>[^?/]+)",
        )
    }
}

/** Upstream `AbemaTVTitleIE`: title/season listings. */
class AbemaTVTitleIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(WALL)

    companion object {
        const val IE_KEY: String = "AbemaTVTitle"

        val VALID_URL: Regex = Regex(
            "https?://abema\\.tv/video/title/(?<id>[^?/#]+)/?(?:\\?(?:[^#]+&)?s=(?<season>[^&#]+))?",
        )
    }
}
