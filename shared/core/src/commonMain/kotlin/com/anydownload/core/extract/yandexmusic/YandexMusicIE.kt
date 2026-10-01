/*
 * Yandex Music extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `yandexmusic.py` from
 * `yt_dlp/extractor/yandexmusic.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `yandexmusic.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: every URL form matches and fails typed. The track download URL is
 * signed with a fixed secret key that the port does not embed, and the API
 * CAPTCHA-blocks automated requests. No key, cookie, token, or signed media
 * URL is stored here.
 */
package com.anydownload.core.extract.yandexmusic

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val BASE_URL = "https?://music\\.yandex\\.(?<tld>ru|kz|ua|by|com)"

private const val WALL =
    "The Yandex Music track download URL is signed with a fixed secret key the port does not " +
        "embed, and the API CAPTCHA-blocks automated requests."

private fun wall(): Nothing = throw ExtractionError.Unavailable(WALL)

/** Upstream `YandexMusicTrackIE`: a track. */
class YandexMusicTrackIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "YandexMusicTrack"

        val VALID_URL: Regex = Regex("$BASE_URL/album/(?<albumId>\\d+)/track/(?<id>\\d+)")
    }
}

/** Upstream `YandexMusicAlbumIE`: an album. */
class YandexMusicAlbumIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "YandexMusicAlbum"

        val VALID_URL: Regex = Regex("$BASE_URL/album/(?<id>\\d+)")
    }
}

/** Upstream `YandexMusicPlaylistIE`: a user playlist. */
class YandexMusicPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "YandexMusicPlaylist"

        val VALID_URL: Regex = Regex("$BASE_URL/users/(?<user>[^/]+)/playlists/(?<id>\\d+)")
    }
}

/** Upstream `YandexMusicArtistTracksIE`: an artist's tracks. */
class YandexMusicArtistTracksIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "YandexMusicArtistTracks"

        val VALID_URL: Regex = Regex("$BASE_URL/artist/(?<id>\\d+)/tracks")
    }
}

/** Upstream `YandexMusicArtistAlbumsIE`: an artist's albums. */
class YandexMusicArtistAlbumsIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "YandexMusicArtistAlbums"

        val VALID_URL: Regex = Regex("$BASE_URL/artist/(?<id>\\d+)/albums")
    }
}
