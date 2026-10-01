/*
 * Nitter extractor — AnyDownload
 *
 * Kotlin translation of the public page subset of `nitter.py` from
 * `yt_dlp/extractor/nitter.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nitter.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the status-page video scan (`data-url`/`source src`), the main-tweet
 * slice, the HLS or direct media row, and the page metadata. The
 * `hlsPlayback` cookie the upstream sets is not sent by the port, the
 * instance list is matched by host shape instead of an enumerated list, and
 * like/repost/comment counters the port does not carry are dropped. No
 * cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.nitter

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail

private const val INSTANCE_HOSTS =
    "(?:(?:[\\w-]+\\.)*[\\w-]*nitter[\\w-]*\\.(?:[a-z]{2,}|onion|i2p)(?:\\.[a-z]{2,})*|" +
        "twitter\\.(?:censors\\.us|dr460nf1r3\\.org|femboy\\.hu)|bird\\.trom\\.tf|" +
        "notabird\\.site|unofficialbird\\.com|read\\.whatever\\.social|" +
        "tw\\.artemislena\\.eu|tweet\\.lambda\\.dance)"

/** Upstream `NitterIE`: a status page on a Nitter instance. */
class NitterIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val uploaderId = match.groups["uploader"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val origin = Regex("^(https?://[^/]+)").find(url)?.groupValues?.get(1)
            ?: throw ExtractionError.UnsupportedUrl()
        val fullWebpage = http.downloadWebpage(url)
        val mainTweetStart = fullWebpage.indexOf("class=\"main-tweet\"")
        val webpage = if (mainTweetStart > 0) fullWebpage.substring(mainTweetStart) else fullWebpage

        val videoMatch = Regex("(?:<video[^>]+data-url|<source[^>]+src)=\"([^\"]+)\"").find(webpage)
            ?: throw ExtractionError.NoFormats("The Nitter page had no video URL.")
        val rawVideoUrl = videoMatch.groupValues[1]
        val videoUrl = if (rawVideoUrl.startsWith("http://") || rawVideoUrl.startsWith("https://")) {
            rawVideoUrl
        } else {
            origin + rawVideoUrl
        }
        val ext = ExtractorUtils.determineExt(videoUrl)
        val formats = if (ext == "unknown_video" || ext == "m3u8") {
            listOf(MediaFormat(formatId = "hls", url = videoUrl, ext = "mp4", protocol = "m3u8_native"))
        } else {
            listOf(MediaFormat(url = videoUrl, ext = ext))
        }

        val uploaderName = Regex("<a class=\"fullname\"[^>]+title=\"([^\"]+)\"").find(webpage)
            ?.groupValues?.get(1)
        val resolvedUploaderId = Regex("<a class=\"username\"[^>]+title=\"@([^\"]+)\"").find(webpage)
            ?.groupValues?.get(1) ?: uploaderId
        var title = metaContent(fullWebpage, "og:description")
            ?: Regex("<div class=\"tweet-content[^>]+>([^<]+)</div>").find(webpage)?.groupValues?.get(1)
        if (uploaderName != null && title != null) {
            title = "$uploaderName - $title"
        }
        val thumbnail = metaContent(fullWebpage, "og:image")
            ?: Regex("<video[^>]+poster=\"([^\"]+)\"").find(webpage)?.groupValues?.get(1)
                ?.let { poster -> if (poster.startsWith("http")) poster else origin + poster }
        val date = Regex("<span[^>]+class=\"tweet-date\"[^>]*><a[^>]+title=\"([^\"]+)\"")
            .find(webpage)?.groupValues?.get(1)?.replace("·", "")?.trim()
        val viewCount = Regex("<span[^>]+class=\"icon-play[^>]*></span>([^<]*)</div>")
            .find(webpage)?.groupValues?.get(1)?.trim()?.let { parseCount(it) }
        return InfoDict(
            id = videoId,
            title = title,
            description = metaContent(fullWebpage, "og:description"),
            uploadDate = ExtractorUtils.unifiedStrdate(date),
            channel = uploaderName,
            viewCount = viewCount,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "nitter",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Nitter"

        val VALID_URL: Regex = Regex(
            "https?://$INSTANCE_HOSTS/(?<uploader>.+)/status/(?<id>[0-9]+)(#.)?",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `parse_count` for the plain and `K`/`M` forms. */
private fun parseCount(value: String): Long? {
    val text = value.trim().uppercase().replace(",", "")
    if (text.isEmpty()) return 0
    val multiplier = when {
        text.endsWith("K") -> 1_000L
        text.endsWith("M") -> 1_000_000L
        text.endsWith("B") -> 1_000_000_000L
        else -> 1L
    }
    val number = text.removeSuffix("K").removeSuffix("M").removeSuffix("B").trim().toDoubleOrNull()
        ?: return null
    return (number * multiplier).toLong()
}

private fun metaContent(webpage: String, property: String): String? {
    val name = Regex.escape(property)
    val patterns = listOf(
        "<meta[^>]+(?:property|name)\\s*=\\s*[\"']$name[\"'][^>]+content\\s*=\\s*[\"']([^\"']*)[\"']",
        "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]+(?:property|name)\\s*=\\s*[\"']$name[\"']",
    )
    for (pattern in patterns) {
        Regex(pattern).find(webpage)?.let { return it.groupValues[1].ifBlank { null } }
    }
    return null
}
