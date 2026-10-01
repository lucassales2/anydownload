/*
 * iQIYI extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `iqiyi.py` from
 * `yt_dlp/extractor/iqiyi.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `iqiyi.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the Chinese iqiyi.com/pps.tv pages (the tvid/videoid scan, the
 * MD5-signed `tmts` API, the m3u8 format map, the geo error code, and the
 * album pagination), plus the iq.com album listing. HLS masters are recorded
 * as `m3u8_native` and parsed at download time. The international iq.com
 * player needs its `cmd5x` signature function executed in a JS runtime
 * (upstream uses PhantomJS), which the port excludes, so `IqIE` matches and
 * fails typed; the iq.com VIP/uid cookies, subtitles, and the intl format
 * data path are not translated. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.iqiyi

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.md5Hex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/** Upstream `IqiyiIE`: the Chinese iqiyi.com and pps.tv pages. */
class IqiyiIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val webpage = http.downloadWebpage(url)
        val tvid = ExtractorUtils.searchRegex(
            "data-(?:player|shareplattrigger)-tvid\\s*=\\s*['\"](\\d+)",
            webpage,
            default = null,
        )
        if (tvid == null) {
            return extractPlaylist(webpage, url)
        }
        val videoId = ExtractorUtils.searchRegex(
            "data-(?:player|shareplattrigger)-videoid\\s*=\\s*['\"]([a-f\\d]+)",
            webpage,
            default = null,
        ) ?: throw ExtractionError.Malformed("The iQIYI page had no video id.")

        val formats = mutableListOf<MediaFormat>()
        var code = ""
        var attempts = 0
        while (formats.isEmpty() && attempts < 2) {
            attempts++
            val rawData = getRawData(tvid, videoId)
            code = rawData.str("code") ?: ""
            if (code != "A00000") {
                if (code == "A00111") {
                    throw ExtractionError.GeoRestricted()
                }
                throw ExtractionError.Unavailable("Unable to load data. Error code: $code")
            }
            for (element in rawData.obj("data")?.array("vidl").orEmpty()) {
                val stream = element as? JsonObject ?: continue
                val m3u8 = ExtractorUtils.urlOrNone(stream.str("m3utx")) ?: continue
                val vd = stream.number("vd")?.toLong()?.toString() ?: continue
                formats += MediaFormat(
                    formatId = vd,
                    url = m3u8,
                    ext = "mp4",
                    protocol = "m3u8_native",
                    quality = FORMATS_MAP[vd]?.toString(),
                    preference = FORMATS_MAP[vd],
                )
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("iQIYI returned no m3u8 streams (code $code).")
        }

        val title = ExtractorUtils.searchRegex(
            "<[^>]*id=\"widget-videotitle\"[^>]*>(.*?)<",
            webpage,
            default = null,
        ) ?: ExtractorUtils.searchRegex(
            "<[^>]*class=\"mod-play-tit\"[^>]*>(.*?)<",
            webpage,
            default = null,
        ) ?: ExtractorUtils.searchRegex(
            "<span[^>]+data-videochanged-title=\"word\"[^>]*>([^<]+)</span>",
            webpage,
            default = null,
        )
        return InfoDict(
            id = videoId,
            title = title?.let(::cleanHtml),
            formats = formats,
            webpageUrl = url,
            extractor = "iqiyi",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `get_raw_data`: the MD5-signed tmts API. */
    private suspend fun getRawData(tvid: String, videoId: String): JsonObject {
        val tm = Clock.System.now().toEpochMilliseconds()
        val sc = md5Hex((tm.toString() + SIGNING_KEY + tvid).encodeToByteArray())
        val body = http.downloadWebpage(
            "http://cache.m.iqiyi.com/jp/tmts/$tvid/$videoId/" +
                "?tvid=$tvid&vid=$videoId&src=$SRC&sc=$sc&t=$tm",
        )
        val json = body.removePrefix("var tvInfoJs=")
        return ExtractorUtils.parseJson(json) as? JsonObject
            ?: throw ExtractionError.Malformed("The iQIYI tmts response was not an object.")
    }

    /** Upstream `_extract_playlist` for the album pages. */
    private suspend fun extractPlaylist(webpage: String, url: String): InfoDict {
        val links = Regex(
            "<a[^>]+class=\"site-piclist_pic_link\"[^>]+href=\"(http://www\\.iqiyi\\.com/.+\\.html)\"",
        ).findAll(webpage).map { it.groupValues[1] }.toList()
        if (links.isEmpty()) {
            throw ExtractionError.Unavailable("Can't find any video on the iQIYI page.")
        }
        val albumId = ExtractorUtils.searchRegex("albumId\\s*:\\s*(\\d+),", webpage, default = null)
            ?: throw ExtractionError.Malformed("The iQIYI album page had no album id.")
        val albumTitle = ExtractorUtils.searchRegex("data-share-title=\"([^\"]+)\"", webpage, default = null)
        val entries = links.map { InfoEntry(url = it) }.toMutableList()
        var page = 2
        while (page <= MAX_PAGES) {
            val pageBody = try {
                http.downloadWebpage(
                    "http://cache.video.qiyi.com/jp/avlist/$albumId/$page/$PAGE_SIZE/",
                )
            } catch (error: ExtractionError) {
                break
            }
            val parsed = ExtractorUtils.parseJson(pageBody.removePrefix("var tvInfoJs=")) as? JsonObject
                ?: break
            val vlist = parsed.obj("data")?.array("vlist").orEmpty()
            for (element in vlist) {
                val vurl = ExtractorUtils.urlOrNone((element as? JsonObject)?.str("vurl")) ?: continue
                entries += InfoEntry(url = vurl)
            }
            if (vlist.size < PAGE_SIZE) break
            page++
        }
        return InfoDict(
            id = albumId,
            title = albumTitle,
            entries = entries,
            webpageUrl = url,
            extractor = "iqiyi",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Iqiyi"
        private const val SIGNING_KEY = "d5fb4bd9d50c4be6948c97edd7254b0e"
        private const val SRC = "76f90cbd92f94a2e925d83e8ccd22cb7"
        private const val PAGE_SIZE = 50
        private const val MAX_PAGES = 100

        private val FORMATS_MAP = mapOf(
            "96" to 1, "1" to 2, "2" to 3, "21" to 4,
            "4" to 5, "17" to 5, "5" to 6, "18" to 7,
        )

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:[^.]+\\.)?iqiyi\\.com|www\\.pps\\.tv)/.+\\.html",
        )
    }
}

/** Upstream `IqIE`: the international iq.com player (JS signature wall). */
class IqIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(
        "The iq.com player needs its cmd5x signature function executed in a JS runtime; the " +
            "port excludes JS execution and PhantomJS.",
    )

    companion object {
        const val IE_KEY: String = "Iq"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?iq\\.com/play/(?:[\\w%-]*-)?(?<id>\\w+)",
        )
    }
}

/** Upstream `IqAlbumIE`: the iq.com album listings. */
class IqAlbumIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val albumId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val nextData = extractNextJsData(webpage)
            ?: throw ExtractionError.Malformed("The iq.com album page had no Next.js data.")
        val albumData = nextData.obj("props")?.obj("initialState")?.obj("album")?.obj("videoAlbumInfo")
            ?: throw ExtractionError.Malformed("The iq.com album page had no album data.")
        if (albumData.str("videoType") == "singleVideo") {
            return InfoDict(
                id = albumId,
                webpageUrl = url,
                redirectUrl = "https://www.iq.com/play/$albumId",
                extractor = "iqiyi",
                extractorKey = IE_KEY,
            )
        }
        val albumIdNum = albumData.str("albumId") ?: albumId
        val pageProps = nextData.obj("props")?.obj("initialProps")?.obj("pageProps")
        val modeCode = pageProps?.str("modeCode") ?: "intl"
        val langCode = pageProps?.str("langCode") ?: "en_us"
        val entries = mutableListOf<InfoEntry>()
        for (element in albumData.array("totalPageRange").orEmpty()) {
            val range = element as? JsonObject ?: continue
            val from = range.number("from")?.toLong() ?: continue
            val to = range.number("to")?.toLong() ?: continue
            val page = try {
                http.downloadJson(
                    "https://pcw-api.iq.com/api/episodeListSource/$albumIdNum" +
                        "?platformId=3&modeCode=$modeCode&langCode=$langCode" +
                        "&endOrder=$to&startOrder=$from",
                ) as? JsonObject
            } catch (error: ExtractionError) {
                null
            } ?: continue
            for (videoElement in page.obj("data")?.array("epg").orEmpty()) {
                val video = videoElement as? JsonObject ?: continue
                val suffix = video.str("playLocSuffix") ?: video.str("qipuIdStr") ?: continue
                entries += InfoEntry(
                    id = video.str("qipuIdStr"),
                    title = video.str("name"),
                    url = "https://www.iq.com/play/$suffix",
                )
            }
        }
        return InfoDict(
            id = albumId,
            title = albumData.str("name"),
            description = albumData.str("description"),
            entries = entries,
            webpageUrl = url,
            extractor = "iqiyi",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "IqAlbum"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?iq\\.com/album/(?:[\\w%-]*-)?(?<id>\\w+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private val NEXT_DATA = Regex(
    "<script[^>]+id=\"__NEXT_DATA__\"[^>]*>(.+?)</script>",
    RegexOption.DOT_MATCHES_ALL,
)

private fun extractNextJsData(html: String): JsonObject? =
    NEXT_DATA.find(html)?.groupValues?.get(1)?.let { ExtractorUtils.parseJson(it) as? JsonObject }

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    val withoutTags = text.replace(Regex("<[^>]*>"), " ")
    return ExtractorUtils.unescapeHtml(withoutTags)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
