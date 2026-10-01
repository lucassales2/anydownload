/*
 * NetEase Music extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `neteasemusic.py` from
 * `yt_dlp/extractor/neteasemusic.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `neteasemusic.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the song, album, artist, playlist/toplist, MV, program, and DJ-radio
 * URL forms match and fail typed. The player API request body is AES-128-ECB
 * encrypted with the web client key plus an MD5 digest, and the higher levels
 * are gated behind a login/VIP account; the port has no AES-ECB encryption
 * helper, so the eapi cipher, the player levels, the lyrics, and the
 * playlist/metadata APIs are not translated. No cookie, token, or signed
 * media URL is stored here.
 */
package com.anydownload.core.extract.neteasemusic

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

/** Upstream `NetEaseMusicBaseIE`: the shared eapi wall. */
abstract class NetEaseMusicBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Every NetEase player path needs the eapi request cipher. */
    protected fun eapiWall(): Nothing = throw ExtractionError.Unavailable(EAPI_WALL_MESSAGE)

    companion object {
        /** The one-sentence reason on every NetEase URL form. */
        const val EAPI_WALL_MESSAGE: String =
            "The NetEase player API needs the AES-128-ECB eapi request cipher and an account " +
                "for the higher levels; the cipher and the player APIs are not translated."
    }
}

/** Upstream `NetEaseMusicIE`: the song pages. */
class NetEaseMusicIE(
    http: ExtractorHttp,
) : NetEaseMusicBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = eapiWall()

    companion object {
        const val IE_KEY: String = "NetEaseMusic"

        val VALID_URL: Regex = Regex(
            "https?://(?:y\\.)?music\\.163\\.com/(?:[#m]/)?song\\?.*?\\bid=(?<id>[0-9]+)",
        )
    }
}

/** Upstream `NetEaseMusicAlbumIE`: the album pages. */
class NetEaseMusicAlbumIE(
    http: ExtractorHttp,
) : NetEaseMusicBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = eapiWall()

    companion object {
        const val IE_KEY: String = "NetEaseMusicAlbum"

        val VALID_URL: Regex = Regex(
            "https?://music\\.163\\.com/(?:#/)?album\\?id=(?<id>[0-9]+)",
        )
    }
}

/** Upstream `NetEaseMusicSingerIE`: the artist pages. */
class NetEaseMusicSingerIE(
    http: ExtractorHttp,
) : NetEaseMusicBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = eapiWall()

    companion object {
        const val IE_KEY: String = "NetEaseMusicSinger"

        val VALID_URL: Regex = Regex(
            "https?://music\\.163\\.com/(?:#/)?artist\\?id=(?<id>[0-9]+)",
        )
    }
}

/** Upstream `NetEaseMusicListIE`: the playlist and toplist pages. */
class NetEaseMusicListIE(
    http: ExtractorHttp,
) : NetEaseMusicBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = eapiWall()

    companion object {
        const val IE_KEY: String = "NetEaseMusicList"

        val VALID_URL: Regex = Regex(
            "https?://music\\.163\\.com/(?:#/)?(?:playlist|discover/toplist)\\?id=(?<id>[0-9]+)",
        )
    }
}

/** Upstream `NetEaseMusicMvIE`: the MV pages. */
class NetEaseMusicMvIE(
    http: ExtractorHttp,
) : NetEaseMusicBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = eapiWall()

    companion object {
        const val IE_KEY: String = "NetEaseMusicMv"

        val VALID_URL: Regex = Regex(
            "https?://music\\.163\\.com/(?:#/)?mv\\?id=(?<id>[0-9]+)",
        )
    }
}

/** Upstream `NetEaseMusicProgramIE`: the DJ program pages. */
class NetEaseMusicProgramIE(
    http: ExtractorHttp,
) : NetEaseMusicBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = eapiWall()

    companion object {
        const val IE_KEY: String = "NetEaseMusicProgram"

        val VALID_URL: Regex = Regex(
            "https?://music\\.163\\.com/(?:#/)?(?:dj|program)\\?id=(?<id>[0-9]+)",
        )
    }
}

/** Upstream `NetEaseMusicDjRadioIE`: the DJ radio pages. */
class NetEaseMusicDjRadioIE(
    http: ExtractorHttp,
) : NetEaseMusicBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = eapiWall()

    companion object {
        const val IE_KEY: String = "NetEaseMusicDjRadio"

        val VALID_URL: Regex = Regex(
            "https?://music\\.163\\.com/(?:#/)?djradio\\?id=(?<id>[0-9]+)",
        )
    }
}
