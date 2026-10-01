/*
 * ViewLift extractors — AnyDownload
 *
 * Kotlin translation of the URL surface of `viewlift.py` from
 * `yt_dlp/extractor/viewlift.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `viewlift.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the URL surface of the ViewLift sites (SnagFilms and the other
 * domains in the upstream list). Every API call needs the `token` cookie
 * the upstream `_fetch_token` reads an authorization token from, so each
 * URL form matches and fails typed. The site map and the cookie-derived
 * token are not stored here.
 */
package com.anydownload.core.extract.viewlift

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor

private const val DOMAINS =
    "(?:(?:main\\.)?snagfilms|snagxtreme|funnyforfree|kiddovid|winnersview|" +
        "(?:monumental|lax)sportsnetwork|vayafilm|failarmy|ftfnext|" +
        "lnppass\\.legapallacanestro|moviespree|app\\.myoutdoortv|neoufitness|pflmma|" +
        "theidentitytb|chorki)\\.com|(?:hoichoi|app\\.horseandcountry|kronon|marquee|supercrosslive)\\.tv"

private const val LOGIN_WALL =
    "Cookies (not necessarily logged in) are needed to download from this website"

/** Upstream `ViewLiftEmbedIE`: an embed player. */
class ViewLiftEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "ViewLiftEmbed"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|embed)\\.)?(?<domain>$DOMAINS)/embed/player\\?.*?\\bfilmId=" +
                "(?<id>[\\da-f]{8}-(?:[\\da-f]{4}-){3}[\\da-f]{12})",
        )

        /** Upstream `_EMBED_REGEX`: an embed iframe URL. */
        val EMBED_URL: Regex = Regex(
            "<iframe[^>]+?src=([\"'])((?:https?:)?//(?:embed\\.)?(?:$DOMAINS)/embed/player.+?)\\1",
        )
    }
}

/** Upstream `ViewLiftIE`: a film, show, video, or watch page. */
class ViewLiftIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(LOGIN_WALL)
    }

    companion object {
        const val IE_KEY: String = "ViewLift"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?<domain>$DOMAINS)" +
                "(?<path>(?:/(?:films/title|show|(?:news/)?videos?|watch))?/(?<id>[^?#]+))",
        )
    }
}
