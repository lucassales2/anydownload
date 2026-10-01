/*
 * VGTV / BT extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `vgtv.py` from
 * `yt_dlp/extractor/vgtv.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `vgtv.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `svp.vg.no` asset JSON (host/appname/vendor mapping, HLS and
 * direct rows with the width_height_tbr pattern, the geo-blocked typed
 * failure, and the inactive typed failure) and the two Bergens Tidende
 * article redirects. Upstream marks `VGTVIE` `_WORKING = False`; hds/f4m is
 * skipped (no f4m helper), the 5-digit bttv `_extract_video_info` branch is
 * not translated (the method does not exist upstream), and the extra f4m
 * segment param is not carried. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.vgtv

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val HOST_TO_APPNAME = mapOf(
    "tv.vg.no" to "vgtv",
    "vgtv.no" to "vgtv",
    "bt.no/tv" to "bttv",
    "aftenbladet.no/tv" to "satv",
    "fvn.no/fvntv" to "fvntv",
    "aftenposten.no/webtv" to "aptv",
    "ap.vgtv.no/webtv" to "aptv",
    "tv.aftonbladet.se" to "abtv",
    "tv.aftonbladet.se/abtv" to "abtv",
    "www.aftonbladet.se/tv" to "abtv",
)

private val APP_NAME_TO_VENDOR = mapOf(
    "vgtv" to "vgtv",
    "bttv" to "bt",
    "satv" to "sa",
    "fvntv" to "fvn",
    "aptv" to "ap",
    "abtv" to "ab",
)

/** Upstream `VGTVIE`: a VGTV/BTTV/SATV/FVNTV/APTV/ABTV asset. */
class VGTVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val host = match.groups["host"]?.value
        val appName = if (host != null) HOST_TO_APPNAME[host] else match.groups["appname"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val vendor = APP_NAME_TO_VENDOR[appName] ?: throw ExtractionError.UnsupportedUrl()

        val data = http.downloadJson(
            "http://svp.vg.no/svp/api/v1/$vendor/assets/$videoId?appName=$appName-website",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The VGTV asset API was not an object.")
        if (data.str("status") == "inactive") {
            throw ExtractionError.Unavailable("Video $videoId is no longer available.")
        }

        val streams = data.obj("streamUrls") ?: JsonObject(emptyMap())
        val isLive = data.str("streamType") == "live"
        val formats = mutableListOf<MediaFormat>()
        streams.str("hls")?.let {
            formats += MediaFormat(formatId = "hls", url = it, ext = "mp4", protocol = "m3u8_native")
        }
        // hds/f4m is skipped: the port has no f4m helper.
        val mp4Urls = mutableListOf<String>()
        streams.array("pseudostreaming").orEmpty().forEach { element ->
            (element as? JsonPrimitive)?.content?.let { mp4Urls += it }
        }
        streams.str("mp4")?.let { mp4Urls += it }
        for (mp4Url in mp4Urls) {
            val resolution = Regex("(\\d+)_(\\d+)_(\\d+)").find(mp4Url)
            val tbr = resolution?.groupValues?.get(3)?.toLongOrNull()
            formats += MediaFormat(
                formatId = tbr?.let { "mp4-$it" },
                url = mp4Url,
                ext = "mp4",
                width = resolution?.groupValues?.get(1)?.toLongOrNull(),
                height = resolution?.groupValues?.get(2)?.toLongOrNull(),
                tbr = tbr?.toDouble(),
            )
        }

        if (formats.isEmpty()) {
            val properties = data.obj("streamConfiguration")?.array("properties").orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.content }
            if ("geoblocked" in properties) {
                val country = (host ?: "").substringAfterLast('.').substringBefore('/').uppercase()
                throw ExtractionError.GeoRestricted(listOf(country))
            }
        }
        return InfoDict(
            id = videoId,
            title = data.str("title"),
            description = data.str("description"),
            duration = data.number("duration")?.div(1000),
            uploadDate = data.number("published")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            viewCount = data.number("displays")?.toLong(),
            isLive = isLive,
            thumbnails = listOfNotNull(
                data.obj("images")?.str("main")?.let { Thumbnail(url = "$it?t[]=900x506q80") },
            ),
            formats = formats,
            webpageUrl = url,
            extractor = "vgtv",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "VGTVIE"

        val VALID_URL: Regex = Regex(
            "(?:https?://(?:www\\.)?" +
                "(?<host>" + HOST_TO_APPNAME.keys.joinToString("|") { Regex.escape(it) } + ")" +
                "/?(?:(?:#!/)?(?:video|live)/|embed?.*id=|a(?:rticles)?/)|" +
                "(?<appname>" + APP_NAME_TO_VENDOR.keys.joinToString("|") { Regex.escape(it) } + "):)" +
                "(?<id>\\d+)",
        )
    }
}

/** Upstream `BTArticleIE`: a bt.no article with an embedded bttv video. */
class BTArticleIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val articleId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val videoId = ExtractorUtils.searchRegex("<video[^>]+data-id=\"(\\d+)\"", webpage)
            ?: throw ExtractionError.Malformed("The BT article had no video id.")
        return InfoDict(
            id = articleId,
            redirectUrl = "bttv:$videoId",
            webpageUrl = url,
            extractor = "bt:article",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BTArticle"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?bt\\.no/(?:[^/]+/)+(?<id>[^/]+)-\\d+\\.html",
        )
    }
}

/** Upstream `BTVestlendingenIE`: the bt.no Vestlendingen series. */
class BTVestlendingenIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return InfoDict(
            redirectUrl = "bttv:$videoId",
            webpageUrl = url,
            extractor = "bt:vestlendingen",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BTVestlendingen"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?bt\\.no/spesial/vestlendingen/#!/(?<id>\\d+)",
        )
    }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
