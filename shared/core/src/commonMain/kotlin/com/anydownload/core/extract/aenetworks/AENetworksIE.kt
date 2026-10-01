/*
 * A+E Networks extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `aenetworks.py` from
 * `yt_dlp/extractor/aenetworks.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `aenetworks.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: every URL form matches and fails typed. The A&E/ThePlatform SMIL
 * URLs are signed with the embedded ThePlatform key/secret, which the port
 * does not embed; the MVPD auth flow is also out of scope. No key, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.aenetworks

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "The A&E/ThePlatform SMIL URLs need an HMAC signature with the embedded ThePlatform " +
        "key/secret and the walled titles need MVPD auth; the port does not embed those secrets."

private fun wall(): Nothing = throw ExtractionError.Unavailable(WALL)

private const val BASE_URL_REGEX =
    "https?://(?:(?:www|play|watch)\\.)?" +
        "(?:(?:history(?:vault)?|aetv|mylifetime|lifetimemovieclub)\\.com|fyi\\.tv)/"

/** Upstream `AENetworksIE`: episodes, movies, specials, and videos. */
class AENetworksIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "AENetworks"

        val VALID_URL: Regex = Regex(
            BASE_URL_REGEX + "(?<id>shows/[^/?#]+/season-\\d+/episode-\\d+|" +
                "(?<type>movie|special)s/[^/?#]+(?<extra>/[^/?#]+)?|" +
                "(?:shows/[^/?#]+/)?videos/[^/?#]+)",
        )
    }
}

/** Upstream `AENetworksCollectionIE`: the list/collection pages. */
class AENetworksCollectionIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "AENetworksCollection"

        val VALID_URL: Regex = Regex(BASE_URL_REGEX + "(?:[^/]+/)*(?:list|collections)/(?<id>[^/?#&]+)/?(?:[?#&]|$)")
    }
}

/** Upstream `AENetworksShowIE`: the show pages. */
class AENetworksShowIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "AENetworksShow"

        val VALID_URL: Regex = Regex(BASE_URL_REGEX + "shows/(?<id>[^/?#&]+)/?(?:[?#&]|$)")
    }
}

/** Upstream `HistoryTopicIE`: history.com topic videos. */
class HistoryTopicIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "HistoryTopic"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?history\\.com/topics/[^/]+/(?<id>[\\w+-]+?)-video")
    }
}

/** Upstream `HistoryPlayerIE`: the history/biography player pages. */
class HistoryPlayerIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "HistoryPlayer"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?(?<domain>(?:history|biography)\\.com)/player/(?<id>\\d+)")
    }
}

/** Upstream `BiographyIE`: biography.com videos. */
class BiographyIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "Biography"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?biography\\.com/video/(?<id>[^/?#&]+)")
    }
}
