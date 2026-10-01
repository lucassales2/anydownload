/*
 * QQ Music extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `qqmusic.py` from
 * `yt_dlp/extractor/qqmusic.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `qqmusic.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: every URL form matches and fails typed. The QQ Music `musicu.fcg`
 * API signs requests with a `g_tk` derived from the `qqmusic_key`/`uin`
 * cookies, and the song/album/playlist pages need that logged-in session;
 * the port has no QQ Music login. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.qqmusic

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val WALL =
    "The QQ Music API signs requests with a g_tk derived from the qqmusic_key/uin cookies and the " +
        "song/album/playlist pages need that logged-in session; the port has no QQ Music login."

private fun wall(): Nothing = throw ExtractionError.LoginRequired(WALL)

/** Upstream `QQMusicIE`: a song detail page. */
class QQMusicIE(http: ExtractorHttp) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "QQMusic"

        val VALID_URL: Regex = Regex("https?://y\\.qq\\.com/n/ryqq/songDetail/(?<id>[0-9A-Za-z]+)")
    }
}

/** Upstream `QQMusicSingerIE`: a singer page. */
class QQMusicSingerIE(http: ExtractorHttp) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "QQMusicSinger"

        val VALID_URL: Regex = Regex("https?://y\\.qq\\.com/n/ryqq/singer/(?<id>[0-9A-Za-z]+)")
    }
}

/** Upstream `QQMusicAlbumIE`: an album page. */
class QQMusicAlbumIE(http: ExtractorHttp) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "QQMusicAlbum"

        val VALID_URL: Regex = Regex("https?://y\\.qq\\.com/n/ryqq/albumDetail/(?<id>[0-9A-Za-z]+)")
    }
}

/** Upstream `QQMusicToplistIE`: a toplist page. */
class QQMusicToplistIE(http: ExtractorHttp) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "QQMusicToplist"

        val VALID_URL: Regex = Regex("https?://y\\.qq\\.com/n/ryqq/toplist/(?<id>[0-9]+)")
    }
}

/** Upstream `QQMusicPlaylistIE`: a playlist page. */
class QQMusicPlaylistIE(http: ExtractorHttp) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "QQMusicPlaylist"

        val VALID_URL: Regex = Regex("https?://y\\.qq\\.com/n/ryqq/playlist/(?<id>[0-9]+)")
    }
}

/** Upstream `QQMusicVideoIE`: an MV page. */
class QQMusicVideoIE(http: ExtractorHttp) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = wall()

    companion object {
        const val IE_KEY: String = "QQMusicVideo"

        val VALID_URL: Regex = Regex("https?://y\\.qq\\.com/n/ryqq/mv/(?<id>[0-9A-Za-z]+)")
    }
}
