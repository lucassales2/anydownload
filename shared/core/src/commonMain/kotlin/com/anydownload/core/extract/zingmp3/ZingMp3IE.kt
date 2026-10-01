/*
 * Zing MP3 extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `zingmp3.py` from
 * `yt_dlp/extractor/zingmp3.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `zingmp3.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: every `mp3.zing.vn`/`zingmp3.vn` URL form matches and fails typed.
 * The page API needs an HMAC-SHA512 signature over a fixed secret key plus a
 * SHA-256 digest of the query, and the streaming URLs are resolved through
 * that signed API; the port does not add crypto helpers that embed the key,
 * so the API call, the paged listings, the charts, and the podcast/user
 * walks are not translated. No key, token, or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.zingmp3

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val BASE_URL = "https?://(?:mp3\\.zing|zingmp3)\\.vn"
private const val TYPES = "bai-hat|video-clip|embed|eps"

private fun pageUrl(types: String): Regex =
    Regex("$BASE_URL/(?<type>(?:$types))/[^/?#]+/(?<id>\\w+)(?:\\.html|\\?)")

private const val WALL =
    "The Zing MP3 API needs an HMAC-SHA512 signature over a fixed secret key plus a SHA-256 " +
        "query digest; the port does not add crypto helpers that embed the key."

private fun wall(): Nothing = throw ExtractionError.Unavailable(WALL)

/** Upstream `ZingMp3IE`: songs, video clips, embeds, and episodes. */
class ZingMp3IE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "ZingMp3"

        val VALID_URL: Regex = pageUrl(TYPES)
    }
}

/** Upstream `ZingMp3AlbumIE`: albums and playlists. */
class ZingMp3AlbumIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "ZingMp3Album"

        val VALID_URL: Regex = pageUrl("album|playlist")
    }
}

/** Upstream `ZingMp3ChartHomeIE`: the chart home pages. */
class ZingMp3ChartHomeIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "ZingMp3ChartHome"

        val VALID_URL: Regex = Regex(
            "$BASE_URL/(?<id>(?:zing-chart|moi-phat-hanh|top100|podcast-discover))/?(?:[#?]|$)",
        )
    }
}

/** Upstream `ZingMp3WeekChartIE`: the weekly chart. */
class ZingMp3WeekChartIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "ZingMp3WeekChart"

        val VALID_URL: Regex = pageUrl("zing-chart-tuan")
    }
}

/** Upstream `ZingMp3ChartMusicVideoIE`: the video genre charts. */
class ZingMp3ChartMusicVideoIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "ZingMp3ChartMusicVideo"

        val VALID_URL: Regex = Regex(
            "$BASE_URL/(?<type>the-loai-video)/(?<regions>[^/]+)/(?<id>[^.]+)",
        )
    }
}

/** Upstream `ZingMp3UserIE`: the artist/user pages. */
class ZingMp3UserIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "ZingMp3User"

        val VALID_URL: Regex = Regex(
            "$BASE_URL/(?<user>[^/]+)/(?<type>bai-hat|single|album|video|song)/?(?:[?#]|$)",
        )
    }
}

/** Upstream `ZingMp3HubIE`: the hub detail pages. */
class ZingMp3HubIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "ZingMp3Hub"

        val VALID_URL: Regex = Regex("$BASE_URL/(?<type>hub)/[^/?#]+/(?<id>[^./?#]+)")
    }
}

/** Upstream `ZingMp3LiveRadioIE`: the live radio channels. */
class ZingMp3LiveRadioIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "ZingMp3LiveRadio"

        val VALID_URL: Regex = Regex("$BASE_URL/(?<type>(?:liveradio))/(?<id>\\w+)(?:\\.html|\\?)")
    }
}

/** Upstream `ZingMp3PodcastEpisodeIE`: podcast programs and categories. */
class ZingMp3PodcastEpisodeIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "ZingMp3PodcastEpisode"

        val VALID_URL: Regex = pageUrl("pgr|cgr")
    }
}

/** Upstream `ZingMp3PodcastIE`: the podcast home pages. */
class ZingMp3PodcastIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "ZingMp3Podcast"

        val VALID_URL: Regex = Regex("$BASE_URL/(?<id>(?:cgr|top-podcast|podcast-new))/?(?:[#?]|$)")
    }
}
