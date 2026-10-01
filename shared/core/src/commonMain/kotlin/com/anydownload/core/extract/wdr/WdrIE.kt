/*
 * WDR extractors — AnyDownload
 *
 * Kotlin translation of the public metadata subset of `wdr.py` from
 * `yt_dlp/extractor/wdr.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `wdr.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public deviceids-medp JSONP metadata (HLS and direct formats,
 * caption URLs and caption hashes), the page data-extension scan, the
 * playlist link scan, and the Elefant table-of-contents XML path. F4M/SMIL
 * manifests are skipped and manifest parsing is not translated, so an m3u8
 * URL becomes one HLS row. No cookie, token, or signed media URL is stored
 * here.
 */
package com.anydownload.core.extract.wdr

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `WDRIE`: a deviceids-medp asset. */
open class WDRIE(
    http: ExtractorHttp,
    ieKey: String = IE_KEY,
    validUrl: Regex = VALID_URL,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    override suspend fun extract(url: String): InfoDict {
        var videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        var assetUrl = url
        if (url.startsWith("wdr:")) {
            videoId = url.substring(4)
            assetUrl = assetUrl(videoId)
        }
        val metadata = ExtractorUtils.parseJson(stripJsonp(http.downloadWebpage(assetUrl))) as? JsonObject
            ?: throw ExtractionError.Malformed("The WDR asset was not JSON.")
        val isLive = metadata.str("mediaType") == "live"
        val trackerData = metadata.obj("trackerData")
            ?: throw ExtractionError.Malformed("The WDR asset had no tracker data.")
        val mediaResource = metadata.obj("mediaResource") ?: JsonObject(emptyMap())
        val formats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()
        val germanCaptions = mutableListOf<SubtitleFormat>()
        for ((kind, value) in mediaResource) {
            if (kind == "captionsHash") {
                val captions = value as? JsonObject ?: continue
                for ((ext, captionUrl) in captions) {
                    val urlValue = (captionUrl as? JsonPrimitive)?.content ?: continue
                    germanCaptions += SubtitleFormat(ext = ext, url = urlValue)
                }
                continue
            }
            if (kind != "dflt" && kind != "alt") continue
            val media = value as? JsonObject ?: continue
            for ((tagName, medium) in media) {
                if (tagName != "videoURL" && tagName != "audioURL") continue
                val mediumUrl = (medium as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                when (ExtractorUtils.determineExt(mediumUrl)) {
                    "m3u8" -> formats += MediaFormat(
                        formatId = "hls",
                        url = mediumUrl,
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )

                    "f4m", "smil" -> Unit // F4M/SMIL manifests are not translated.

                    else -> formats += MediaFormat(
                        url = mediumUrl,
                        ext = ExtractorUtils.determineExt(mediumUrl),
                    )
                }
            }
        }
        mediaResource.str("captionURL")?.let {
            germanCaptions += SubtitleFormat(ext = "ttml", url = it)
        }
        if (germanCaptions.isNotEmpty()) {
            subtitles += SubtitleTrack(language = "de", formats = germanCaptions)
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The WDR asset returned no playable format.")
        }
        return InfoDict(
            id = trackerData.str("trackerClipId") ?: videoId,
            title = trackerData.str("trackerClipTitle"),
            uploadDate = ExtractorUtils.unifiedStrdate(trackerData.str("trackerClipAirTime")),
            isLive = isLive,
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "wdr",
            extractorKey = IE_KEY,
        )
    }

    protected fun assetUrl(wdrId: String): String {
        val idLength = maxOf(wdrId.length, 5)
        return "https://deviceids-medp.wdr.de/ondemand/${wdrId.take(idLength - 4)}/$wdrId.js"
    }

    companion object {
        const val IE_KEY: String = "WDR"

        val VALID_URL: Regex = Regex(
            "https?://(?:deviceids-medp\\.wdr\\.de/ondemand/\\d+/|kinder\\.wdr\\.de/(?!mediathek/)[^#?]+-)" +
                "(?<id>\\d+)\\.(?:js|assetjsonp)",
        )
    }
}

/** Upstream `WDRPageIE`: a WDR/ sportschau/ wdrmaus page. */
class WDRPageIE(
    http: ExtractorHttp,
) : WDRIE(http = http, ieKey = "WDRPage", validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["display"]?.value
            ?: match.groups["maus"]?.value
            ?: "wdrmaus"
        val webpage = http.downloadWebpage(url)
        val entries = mutableListOf<InfoEntry>()
        for (dataMatch in Regex(
            "(?s)class=(?:([\"'])(?:mediaLink|wdrrPlayerPlayBtn|videoButton)\\b.*?\\1[^>]+|" +
                "([\"'])videoLink\\b.*?\\2[\\s]*>\\n[^\\n]*)" +
                "data-extension(?:-ard)?=([\"'])((?:(?!\\3).)+)\\3",
        ).findAll(webpage)) {
            val data = dataMatch.groupValues[4]
            val mediaLink = ExtractorUtils.parseJson(data) as? JsonObject ?: continue
            var jsonpUrl = mediaLink.obj("mediaObj")?.str("url") ?: continue
            var clipId = mediaLink.obj("mediaObj")?.str("ref")
            if (jsonpUrl.endsWith(".assetjsonp")) {
                val asset = try {
                    ExtractorUtils.parseJson(stripJsonp(http.downloadWebpage(jsonpUrl))) as? JsonObject
                } catch (error: ExtractionError) {
                    null
                }
                clipId = asset?.obj("trackerData")?.str("trackerClipId") ?: clipId
            }
            if (clipId != null && clipId.length > 4) {
                jsonpUrl = assetUrl(clipId.substring(4))
            }
            entries += InfoEntry(url = jsonpUrl)
        }
        if (entries.isEmpty()) {
            for (linkMatch in Regex(
                "<a[^>]+\\bhref=([\"'])((?:(?!\\1).)+)\\1[^>]+\\bdata-extension(?:-ard)?=",
            ).findAll(webpage)) {
                val href = linkMatch.groupValues[2]
                if (!Regex(PAGE_REGEX).containsMatchIn(href)) continue
                entries += InfoEntry(url = urlJoin(url, href))
            }
        }
        return InfoDict(
            id = displayId,
            entries = entries,
            webpageUrl = url,
            extractor = "wdr:page",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "WDRPage"

        val MAUS_REGEX: String =
            "https?://(?:www\\.)wdrmaus\\.de/(?:[^/]+/)*?(?<maus>[^/?#.]+)(?:/?|/index\\.php5|\\.php5)$"
        val PAGE_REGEX: String = "/(?:mediathek/)?(?:[^/]+/)*(?<display>[^/]+)\\.html"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\d?\\.)?(?:(?:kinder\\.)?wdr\\d?|sportschau)\\.de$PAGE_REGEX|$MAUS_REGEX",
        )
    }
}

/** Upstream `WDRElefantIE`: the Elefant page fragment. */
class WDRElefantIE(
    http: ExtractorHttp,
) : WDRIE(http = http, ieKey = "WDRElefant", validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url)
            ?: url.substringAfter('#', "").takeIf { it.isNotBlank() }
            ?: throw ExtractionError.UnsupportedUrl()
        val tableOfContents = http.downloadJson(
            "https://www.wdrmaus.de/elefantenseite/data/tableOfContentsJS.php5",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Elefant table of contents was not JSON.")
        val entry = tableOfContents[displayId] as? JsonObject
            ?: throw ExtractionError.Unavailable(
                "No entry in the site's table of contents for this URL.",
            )
        val xmlPath = entry.str("xmlPath")
            ?: throw ExtractionError.Malformed("The Elefant entry had no XML path.")
        val xml = http.downloadWebpage("https://www.wdrmaus.de/elefantenseite/$xmlPath")
        val zmdbUrl = Regex("<zmdb_url>(.*?)</zmdb_url>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.trim()
            ?: throw ExtractionError.Unavailable("$displayId is not a video")
        return InfoDict(
            id = displayId,
            redirectUrl = zmdbUrl,
            webpageUrl = url,
            extractor = "wdr:elefant",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "WDRElefant"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)wdrmaus\\.de/elefantenseite/#(?<id>.+)")
    }
}

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    val origin = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: return href
    return if (href.startsWith("/")) origin + href else "$origin/$href"
}

/** Upstream `strip_jsonp`: a JSONP wrapper or a comment wrapper. */
private fun stripJsonp(value: String): String {
    val trimmed = value.trim()
    if (trimmed.startsWith("/*") && trimmed.endsWith("*/")) {
        return trimmed.substring(2, trimmed.length - 2).trim()
    }
    val match = Regex("^[\\w$.]+\\((.*)\\)\\s*;?$", RegexOption.DOT_MATCHES_ALL).find(trimmed)
    return match?.groupValues?.get(1) ?: trimmed
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
