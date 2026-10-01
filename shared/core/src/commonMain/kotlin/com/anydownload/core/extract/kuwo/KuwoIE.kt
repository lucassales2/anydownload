/*
 * Kuwo extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `kuwo.py` from
 * `yt_dlp/extractor/kuwo.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `kuwo.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the song page plus the anti.s format list, the album, chart,
 * singer (paged AJAX), category (`var jsonm`) and MV pages. Upstream marks
 * every class `_WORKING = False`; the port still matches the public URL forms
 * and fails typed on the anti.s `IPDeny` geo wall or on removed songs. Lyrics
 * fill the description. The port does not carry the upstream `creator`
 * (mapped to uploader), `quality` (folded into preference), or the
 * `geo_verification_headers` bypass. No cookie, token, or signed media URL
 * is stored here.
 */
package com.anydownload.core.extract.kuwo

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val OFFLINE_MESSAGE = "对不起，该歌曲由于版权问题已被下线，将返回网站首页"
private const val MAX_PAGES = 5
private const val SINGER_PAGE_SIZE = 15

/** One upstream `_FORMATS` entry. */
internal data class KuwoFormat(
    val format: String,
    val ext: String,
    val br: String? = null,
    val abr: Double? = null,
    val preference: Int,
)

private val KUWO_FORMATS = listOf(
    KuwoFormat(format = "ape", ext = "ape", preference = 100),
    KuwoFormat(format = "mp3-320", ext = "mp3", br = "320kmp3", abr = 320.0, preference = 80),
    KuwoFormat(format = "mp3-192", ext = "mp3", br = "192kmp3", abr = 192.0, preference = 70),
    KuwoFormat(format = "mp3-128", ext = "mp3", br = "128kmp3", abr = 128.0, preference = 60),
    KuwoFormat(format = "wma", ext = "wma", preference = 20),
    KuwoFormat(format = "aac", ext = "aac", abr = 48.0, preference = 10),
)

private val KUWO_MV_FORMATS = KUWO_FORMATS + listOf(
    KuwoFormat(format = "mkv", ext = "mkv", preference = 250),
    KuwoFormat(format = "mp4", ext = "mp4", preference = 200),
)

/** Upstream `KuwoBaseIE`: the shared anti.s format list. */
abstract class KuwoBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /**
     * Upstream `_get_formats`: every entry asks anti.s for a redirect URL; an
     * `IPDeny` body is a typed geo wall unless the caller tolerates it.
     */
    internal suspend fun getFormats(
        songId: String,
        formats: List<KuwoFormat>,
        tolerateIpDeny: Boolean,
    ): List<MediaFormat> {
        val out = mutableListOf<MediaFormat>()
        for (entry in formats) {
            val query = "format=${entry.ext}&br=${entry.br ?: ""}&rid=MUSIC_$songId" +
                "&type=convert_url&response=url"
            val songUrl = http.downloadWebpage("http://antiserver.kuwo.cn/anti.s?$query")
            if (songUrl == "IPDeny" && !tolerateIpDeny) {
                throw ExtractionError.GeoRestricted()
            }
            if (songUrl.startsWith("http://") || songUrl.startsWith("https://")) {
                out += MediaFormat(
                    formatId = entry.format,
                    url = songUrl,
                    ext = entry.ext,
                    abr = entry.abr,
                    preference = entry.preference,
                )
            }
        }
        return out
    }
}

/** Upstream `KuwoIE`: a song detail page. */
class KuwoIE(
    http: ExtractorHttp,
) : KuwoBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val songId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        // `_download_webpage_handle`: the final URL decides whether the song
        // page redirected away (a copyright removal), and the page carries the
        // same message.
        val finalUrl = try {
            http.followRedirects(url)
        } catch (error: ExtractionError) {
            url
        }
        if (!finalUrl.contains(songId)) {
            throw ExtractionError.Unavailable(
                "This song has been offline because of copyright issues.",
            )
        }
        val webpage = http.downloadWebpage(url)
        if (webpage.contains(OFFLINE_MESSAGE)) {
            throw ExtractionError.Unavailable(
                "This song has been offline because of copyright issues.",
            )
        }

        val songName = ExtractorUtils.searchRegex(
            "<p[^>]+id=\"lrcName\">([^<]+)</p>",
            webpage,
        ) ?: throw ExtractionError.Malformed("The Kuwo song page had no name.")
        val singerName = ExtractorUtils.searchRegex(
            "<a[^>]+href=\"http://www\\.kuwo\\.cn/artist/content\\?name=([^\"]+)\">",
            webpage,
        )?.removePrefix("歌手")
        var lrcContent = cleanHtml(elementById(webpage, "lrcContent"))
        if (lrcContent == "暂无") lrcContent = null

        val formats = getFormats(songId, KUWO_FORMATS, tolerateIpDeny = false)

        val albumId = ExtractorUtils.searchRegex(
            "<a[^>]+href=\"http://www\\.kuwo\\.cn/album/(\\d+)/\"",
            webpage,
        )
        var publishTime: String? = null
        if (albumId != null) {
            val albumPage = http.downloadWebpage("http://www.kuwo.cn/album/$albumId/")
            publishTime = ExtractorUtils.searchRegex(
                "发行时间：(\\d{4}-\\d{2}-\\d{2})",
                albumPage,
            )?.replace("-", "")
        }

        return InfoDict(
            id = songId,
            title = songName,
            uploader = singerName,
            description = lrcContent,
            uploadDate = publishTime,
            formats = formats,
            webpageUrl = url,
            extractor = "kuwo:song",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Kuwo"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?kuwo\\.cn/yinyue/(?<id>\\d+)",
        )
    }
}

/** Upstream `KuwoAlbumIE`: an album track list. */
class KuwoAlbumIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val albumId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val albumName = ExtractorUtils.searchRegex(
            "<div[^>]+class=\"comm\"[^<]+<h1[^>]+title=\"([^\"]+)\"",
            webpage,
        ) ?: throw ExtractionError.Malformed("The Kuwo album page had no name.")
        val albumIntro = removeStart(
            cleanHtml(elementById(webpage, "intro")),
            "${albumName}简介：",
        )
        val entries = Regex(
            "<p[^>]+class=\"listen\"><a[^>]+href=\"(http://www\\.kuwo\\.cn/yinyue/\\d+/)\"",
        ).findAll(webpage).map { InfoEntry(url = it.groupValues[1]) }.toList()
        return InfoDict(
            id = albumId,
            title = albumName,
            description = albumIntro,
            entries = entries,
            webpageUrl = url,
            extractor = "kuwo:album",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "KuwoAlbum"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?kuwo\\.cn/album/(?<id>\\d+?)/",
        )
    }
}

/** Upstream `KuwoChartIE`: a chart song list. */
class KuwoChartIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val chartId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val entries = Regex(
            "<a[^>]+href=\"(http://www\\.kuwo\\.cn/yinyue/\\d+)",
        ).findAll(webpage).map { InfoEntry(url = it.groupValues[1]) }.toList()
        return InfoDict(
            id = chartId,
            entries = entries,
            webpageUrl = url,
            extractor = "kuwo:chart",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "KuwoChart"

        val VALID_URL: Regex = Regex(
            "https?://yinyue\\.kuwo\\.cn/billboard_(?<id>[^.]+)\\.htm",
        )
    }
}

/** Upstream `KuwoSingerIE`: a singer's paged song list. */
class KuwoSingerIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val singerId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val singerName = ExtractorUtils.searchRegex("<h1>([^<]+)</h1>", webpage)
            ?: throw ExtractionError.Malformed("The Kuwo singer page had no name.")
        val artistId = ExtractorUtils.searchRegex("data-artistid=\"(\\d+)\"", webpage)
            ?: throw ExtractionError.Malformed("The Kuwo singer page had no artist id.")
        val pageCount = ExtractorUtils.searchRegex("data-page=\"(\\d+)\"", webpage)?.toIntOrNull()
            ?: throw ExtractionError.Malformed("The Kuwo singer page had no page count.")
        val entries = mutableListOf<InfoEntry>()
        // Upstream InAdvancePagedList is 0-based; the port bounds it at five pages.
        var page = 0
        while (page < pageCount && page < MAX_PAGES) {
            val listPage = http.downloadWebpage(
                "http://www.kuwo.cn/artist/contentMusicsAjax" +
                    "?artistId=$artistId&pn=$page&rn=$SINGER_PAGE_SIZE",
            )
            for (match in Regex("<div[^>]+class=\"name\"><a[^>]+href=\"(/yinyue/\\d+)").findAll(listPage)) {
                entries += InfoEntry(url = urlJoin(url, match.groupValues[1]))
            }
            page++
        }
        return InfoDict(
            id = singerId,
            title = singerName,
            entries = entries,
            webpageUrl = url,
            extractor = "kuwo:singer",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "KuwoSinger"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?kuwo\\.cn/mingxing/(?<id>[^/]+)",
        )
    }
}

/** Upstream `KuwoCategoryIE`: a category page with a `var jsonm` list. */
class KuwoCategoryIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val categoryId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val categoryName = ExtractorUtils.searchRegex(
            "<h1[^>]+title=\"([^<>]+?)\">[^<>]+?</h1>",
            webpage,
        ) ?: throw ExtractionError.Malformed("The Kuwo category page had no name.")
        var categoryDesc = removeStart(
            elementById(webpage, "intro")?.trim(),
            "${categoryName}简介：",
        )
        if (categoryDesc == "暂无") categoryDesc = null
        val jsonText = ExtractorUtils.searchRegex("var\\s+jsonm\\s*=\\s*([^;]+);", webpage)
            ?: throw ExtractionError.Malformed("The Kuwo category page had no jsonm.")
        val json = ExtractorUtils.parseJson(jsonText, fatal = true) as? JsonObject
            ?: throw ExtractionError.Malformed("The Kuwo jsonm was not an object.")
        val entries = json.array("musiclist").orEmpty()
            .mapNotNull { (it as? JsonObject)?.primitive("musicrid") }
            .map { InfoEntry(url = "http://www.kuwo.cn/yinyue/$it/") }
        return InfoDict(
            id = categoryId,
            title = categoryName,
            description = categoryDesc,
            entries = entries,
            webpageUrl = url,
            extractor = "kuwo:category",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "KuwoCategory"

        val VALID_URL: Regex = Regex(
            "https?://yinyue\\.kuwo\\.cn/yy/cinfo_(?<id>\\d+?)\\.htm",
        )
    }
}

/** Upstream `KuwoMvIE`: an MV page; the music formats are tolerated as absent. */
class KuwoMvIE(
    http: ExtractorHttp,
) : KuwoBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val songId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val match = Regex(
            "<h1[^>]+title=\"([^\"]+)\">[^<]+<span[^>]+title=\"([^\"]+)\"",
        ).find(webpage) ?: throw ExtractionError.Unavailable("Unable to find song or singer names.")
        val songName = match.groupValues[1]
        val singerName = match.groupValues[2]

        val formats = getFormats(songId, KUWO_MV_FORMATS, tolerateIpDeny = true).toMutableList()
        val mvUrl = http.downloadWebpage("http://www.kuwo.cn/yy/st/mvurl?rid=MUSIC_$songId")
        formats += MediaFormat(
            formatId = "mv",
            url = mvUrl,
            ext = ExtractorUtils.determineExt(mvUrl),
        )
        return InfoDict(
            id = songId,
            title = songName,
            uploader = singerName,
            formats = formats,
            webpageUrl = url,
            extractor = "kuwo:mv",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "KuwoMv"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?kuwo\\.cn/mv/(?<id>\\d+?)/",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `clean_html`: tags stripped, entities decoded, whitespace collapsed. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), " "))
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

/** Upstream `get_element_by_id` for the simple, non-nested page sections. */
private fun elementById(webpage: String, id: String): String? = Regex(
    "<(\\w+)[^>]*\\bid\\s*=\\s*[\"']" + Regex.escape(id) + "[\"'][^>]*>(.*?)</\\1>",
    RegexOption.DOT_MATCHES_ALL,
).find(webpage)?.groupValues?.get(2)

/** Upstream `remove_start`. */
private fun removeStart(value: String?, prefix: String): String? =
    if (value == null) null else value.removePrefix(prefix)

/** Upstream `urllib.parse.urljoin` for an absolute base and a path. */
private fun urlJoin(base: String, value: String): String {
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    val root = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: return value
    return if (value.startsWith("/")) root + value else "$root/$value"
}

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

/** Accepts a string or number `JsonPrimitive`, like Python indexing. */
private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
