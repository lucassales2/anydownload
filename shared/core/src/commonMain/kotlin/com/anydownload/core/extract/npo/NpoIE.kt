/*
 * NPO extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `npo.py` from
 * `yt_dlp/extractor/npo.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `npo.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the radio pages (live channel and fragment), the live/SchoolTV/
 * HetKlokhuis `data-mid`/`media-id` scans that re-dispatch to the `npo:`
 * scheme, and the VPRO/WNL/AndereTijden playlist scans. `NPOIE` matches and
 * fails typed: the player JSON needs an XSRF token from npostart.nl and the
 * streams API takes that player token, which the port does not translate.
 * The playlist entries that point at `npo:` pseudo-URLs stay pseudo; the
 * engine resolves them through `NPOIE`, which fails typed. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.npo

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `NPOIE`: the NPO player (token wall). */
class NPOIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !(NPOLiveIE.VALID_URL.containsMatchIn(url) ||
            NPORadioIE.VALID_URL.containsMatchIn(url) || NPORadioFragmentIE.VALID_URL.containsMatchIn(url))

    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(
        "The NPO player JSON needs an XSRF token from npostart.nl and the streams API takes that " +
            "player token; the port does not translate the token flow.",
    )

    companion object {
        const val IE_KEY: String = "NPO"

        val VALID_URL: Regex = Regex(
            "(?:npo:|https?://(?:www\\.)?(?:" +
                "npo\\.nl/(?:[^/]+/)*|" +
                "(?:ntr|npostart)\\.nl/(?:[^/]+/){2,}|" +
                "omroepwnl\\.nl/video/fragment/[^/]+__|" +
                "(?:zapp|npo3)\\.nl/(?:[^/]+/){2,})" +
                ")(?<id>[^/?#]+)",
        )
    }
}

/** Upstream `NPOLiveIE`: a live channel page. */
class NPOLiveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: "npo-1"
        val webpage = http.downloadWebpage(url)
        val liveId = ExtractorUtils.searchRegex("media-id=\"([^\"]+)\"", webpage, default = null)
            ?: ExtractorUtils.searchRegex("data-prid=\"([^\"]+)\"", webpage, default = null)
            ?: throw ExtractionError.Malformed("The live page had no media id.")
        return InfoDict(
            id = liveId,
            redirectUrl = "npo:$liveId",
            webpageUrl = url,
            extractor = "npo.nl:live",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NPOLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?npo(?:start)?\\.nl/live(?:/(?<id>[^/?#&]+))?")
    }
}

/** Upstream `NPORadioIE`: a live radio channel. */
class NPORadioIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !NPORadioFragmentIE.VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val title = ExtractorUtils.searchRegex("data-channel\\s*=\\s*'([^']+)'", webpage, default = null)
            ?: throw ExtractionError.Malformed("The radio page had no channel name.")
        val streamsRaw = ExtractorUtils.searchRegex("data-streams\\s*=\\s*'([^']+)'", webpage, default = null)
            ?: throw ExtractionError.Malformed("The radio page had no streams.")
        val stream = ExtractorUtils.parseJson(streamsRaw) as? JsonObject
            ?: throw ExtractionError.Malformed("The radio streams were not an object.")
        val streamUrl = stream.str("url")
            ?: throw ExtractionError.NoFormats("The radio streams carried no URL.")
        val codec = stream.str("codec")
        return InfoDict(
            id = videoId,
            title = title,
            url = streamUrl,
            ext = codec,
            isLive = true,
            webpageUrl = url,
            extractor = "npo.nl:radio",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NPORadio"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?npo\\.nl/radio/(?<id>[^/]+)")
    }
}

/** Upstream `NPORadioFragmentIE`: a radio fragment. */
class NPORadioFragmentIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val audioId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val title = ExtractorUtils.searchRegex(
            "href=\"/radio/[^/]+/fragment/$audioId\" title=\"([^\"]+)\"",
            webpage,
            default = null,
        ) ?: throw ExtractionError.Malformed("The fragment page had no title.")
        val audioUrl = ExtractorUtils.searchRegex("data-streams='([^']+)'", webpage, default = null)
            ?: throw ExtractionError.Malformed("The fragment page had no audio URL.")
        return InfoDict(
            id = audioId,
            title = title,
            url = audioUrl,
            webpageUrl = url,
            extractor = "npo.nl:radio:fragment",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NPORadioFragment"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?npo\\.nl/radio/[^/]+/fragment/(?<id>\\d+)")
    }
}

/** Upstream `SchoolTVIE`: a SchoolTV video page that re-dispatches to `npo:`. */
class SchoolTVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractDataMid(http, url, "schooltv", IE_KEY)

    companion object {
        const val IE_KEY: String = "SchoolTV"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?schooltv\\.nl/video/(?<id>[^/?#&]+)")
    }
}

/** Upstream `HetKlokhuisIE`: a Het Klokhuis page that re-dispatches to `npo:`. */
class HetKlokhuisIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict =
        extractDataMid(http, url, "hetklokhuis", IE_KEY)

    companion object {
        const val IE_KEY: String = "HetKlokhuis"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?hetklokhuis\\.nl/[^/]+/\\d+/(?<id>[^/?#&]+)")
    }
}

/** Upstream `VPROIE`: the VPRO/2doc/tegenlicht playlist pages. */
class VPROIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = extractPlaylist(
        http,
        url,
        IE_KEY,
        entryRegex = Regex("data-media-id=\"([^\"]+)\""),
        titleRegexes = listOf(
            "<h1[^>]+class=[\"'][^\"']*\\bmedia-platform-title\\b[^\"']*[\"'][^>]*>([^<]+)",
            "<h5[^>]+class=[\"'][^\"']*\\bmedia-platform-subtitle\\b[^\"']*[\"'][^>]*>([^<]+)",
        ),
    )

    companion object {
        const val IE_KEY: String = "VPRO"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:(?:tegenlicht\\.)?vpro|2doc)\\.nl/(?:[^/]+/)*(?<id>[^/]+)\\.html",
        )
    }
}

/** Upstream `WNLIE`: the omroepwnl playlist pages. */
class WNLIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = extractPlaylist(
        http,
        url,
        IE_KEY,
        entryRegex = Regex("<a[^>]+href=\"([^\"]+)\"[^>]+class=\"js-mid\"[^>]*>Deel \\d+"),
        titleRegexes = listOf("(?s)<h1[^>]+class=\"subject\"[^>]*>(.+?)</h1>"),
    )

    companion object {
        const val IE_KEY: String = "WNL"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?omroepwnl\\.nl/video/detail/(?<id>[^/]+)__\\d+")
    }
}

/** Upstream `AndereTijdenIE`: the anderetijden playlist pages. */
class AndereTijdenIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = extractPlaylist(
        http,
        url,
        IE_KEY,
        entryRegex = Regex("<figure[^>]+class=[\"']episode-container episode-page[\"'][^>]+data-prid=[\"'](.+?)[\"']"),
        titleRegexes = listOf("(?s)<h1[^>]+class=[\"'][^\"']*\\bpage-title\\b[^\"']*[\"'][^>]*>(.+?)</h1>"),
    )

    companion object {
        const val IE_KEY: String = "AndereTijden"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?anderetijden\\.nl/programma/(?:[^/]+/)+(?<id>[^/?#&]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun extractDataMid(
    http: ExtractorHttp,
    url: String,
    extractorName: String,
    ieKey: String,
): InfoDict {
    val displayId = ieMatchId(url) ?: throw ExtractionError.UnsupportedUrl()
    val webpage = http.downloadWebpage(url)
    val videoId = ExtractorUtils.searchRegex(
        "data-mid=([\"'])(?<id>(?:(?!\\1).)+)\\1",
        webpage,
        group = 2,
        default = null,
    ) ?: throw ExtractionError.Malformed("The page had no data-mid attribute.")
    return InfoDict(
        id = displayId,
        redirectUrl = "npo:$videoId",
        webpageUrl = url,
        extractor = extractorName,
        extractorKey = ieKey,
    )
}

private suspend fun extractPlaylist(
    http: ExtractorHttp,
    url: String,
    ieKey: String,
    entryRegex: Regex,
    titleRegexes: List<String>,
): InfoDict {
    val playlistId = ieMatchId(url) ?: throw ExtractionError.UnsupportedUrl()
    val webpage = http.downloadWebpage(url)
    val entries = entryRegex.findAll(webpage).map { match ->
        val videoId = match.groupValues[1]
        InfoEntry(url = if (videoId.startsWith("http")) videoId else "npo:$videoId")
    }.toList()
    val title = titleRegexes.firstNotNullOfOrNull {
        ExtractorUtils.searchRegex(it, webpage, default = null)
    } ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title")
    return InfoDict(
        id = playlistId,
        title = title?.replace(Regex("<[^>]*>"), "")?.trim(),
        entries = entries,
        webpageUrl = url,
        extractor = "npo",
        extractorKey = ieKey,
    )
}

/** The per-class id group lives in the class URL, not in a shared pattern. */
private fun ieMatchId(url: String): String? {
    val path = url.substringAfter("://", "").substringBefore('?').substringBefore('#')
    val segments = path.split('/').filter { it.isNotEmpty() }
    return segments.lastOrNull()?.substringBefore('.')
}

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
