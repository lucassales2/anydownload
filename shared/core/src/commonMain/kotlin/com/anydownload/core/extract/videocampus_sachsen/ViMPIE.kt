/*
 * ViMP extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of
 * `videocampus_sachsen.py` from `yt_dlp/extractor/videocampus_sachsen.py` at
 * upstream tag `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf),
 * read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `videocampus_sachsen.py` is not
 * vendored; see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the ViMP media pages (`/m/<id>`, `/video/<slug>/<32hex>`,
 * `media/embed?key=<32hex>`) with the og/video-js metadata and the HLS plus
 * direct mp4 rows, and the album/category/channel/tag boxList listing (the
 * paged POST `vars[...]` body) at most five pages eagerly. The upstream
 * instance list is translated; manifest parsing is not, so an m3u8 URL
 * becomes one HLS row, the display_id field is not carried, and no cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.videocampus_sachsen

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.platform.HttpMethods
import com.anydownload.core.platform.HttpRequest

private const val HEX32 = "[0-9a-f]{32}"
private const val MAX_PAGES = 5
private const val PAGE_SIZE = 10

/**
 * Upstream `_INSTANCES`, in the same order. Two entries carry a path
 * (`www.b-tu.de/media`, `www.hsbi.de/medienportal`), so the named `host`
 * group can include a path exactly like the Python source.
 */
private val INSTANCES: List<String> = listOf(
    "bergauf.tv",
    "campus.demo.vimp.com",
    "corporate.demo.vimp.com",
    "dancehalldatabase.com",
    "drehzahl.tv",
    "educhannel.hs-gesundheit.de",
    "emedia.ls.haw-hamburg.de",
    "globale-evolution.net",
    "hohu.tv",
    "htvideos.hightechhigh.org",
    "k210039.vimp.mivitec.net",
    "media.cmslegal.com",
    "media.fh-swf.de",
    "media.hs-furtwangen.de",
    "media.hwr-berlin.de",
    "mediathek.dkfz.de",
    "mediathek.htw-berlin.de",
    "mediathek.polizei-bw.de",
    "medien.hs-merseburg.de",
    "mitmedia.manukau.ac.nz",
    "mportal.europa-uni.de",
    "pacific.demo.vimp.com",
    "slctv.com",
    "streaming.prairiesouth.ca",
    "tube.isbonline.cn",
    "univideo.uni-kassel.de",
    "ursula2.genetics.emory.edu",
    "ursulablicklevideoarchiv.com",
    "v.agrarumweltpaedagogik.at",
    "video.eplay-tv.de",
    "video.fh-dortmund.de",
    "video.hs-nb.de",
    "video.hs-offenburg.de",
    "video.hs-pforzheim.de",
    "video.hspv.nrw.de",
    "video.irtshdf.fr",
    "video.pareygo.de",
    "video.tu-dortmund.de",
    "video.tu-freiberg.de",
    "videocampus.sachsen.de",
    "videoportal.uni-freiburg.de",
    "videoportal.vm.uni-freiburg.de",
    "videos.duoc.cl",
    "videos.uni-paderborn.de",
    "vimp-bemus.udk-berlin.de",
    "vimp.aekwl.de",
    "vimp.hs-mittweida.de",
    "vimp.landesfilmdienste.de",
    "vimp.oth-regensburg.de",
    "vimp.ph-heidelberg.de",
    "vimp.sma-events.com",
    "vimp.weka-fachmedien.de",
    "vimpdesk.com",
    "webtv.univ-montp3.fr",
    "www.b-tu.de/media",
    "www.bergauf.tv",
    "www.bigcitytv.de",
    "www.cad-videos.de",
    "www.drehzahl.tv",
    "www.hohu.tv",
    "www.hsbi.de/medienportal",
    "www.logistic.tv",
    "www.orvovideo.com",
    "www.printtube.co.uk",
    "www.rwe.tv",
    "www.salzi.tv",
    "www.signtube.co.uk",
    "www.twb-power.com",
    "www.wenglor-media.com",
    "www2.univ-sba.dz",
)

private val INSTANCE_PATTERN: String = INSTANCES.joinToString("|") { Regex.escape(it) }

/** Upstream `VideocampusSachsenIE`: a ViMP media page. */
class VideocampusSachsenIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val host = match.groups["host"]?.value ?: throw ExtractionError.UnsupportedUrl()
        var videoId = match.groups["id"]?.value
        val tmpId = match.groups["tmpid"]?.value
        val displayId = match.groups["displayid"]?.value
        val embedId = match.groups["embedid"]?.value
        // Upstream downloads the page with fatal=False.
        val webpage = try {
            http.downloadWebpage(url)
        } catch (error: ExtractionError) {
            ""
        }
        if (videoId == null) {
            videoId = embedId ?: ExtractorUtils.searchRegex(
                "src=\"https?://" + Regex.escape(host) + "/media/embed.*(?:\\?|&)key=([0-9a-f]+)&?",
                webpage,
            ) ?: throw ExtractionError.Malformed("The ViMP page had no media key.")
        }
        val title: String?
        val description: String?
        val thumbnail: String?
        if (displayId == null && tmpId == null) {
            // Title, description from the embedded page's meta would not be correct.
            title = ExtractorUtils.searchRegex(
                "<video-js[^>]* data-piwik-title=\"([^\"<]+)\"",
                webpage,
            )
            description = null
            thumbnail = null
        } else {
            title = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "twitter:title")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "title")
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "twitter:description")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "description")
            thumbnail = ExtractorUtils.htmlSearchMeta(webpage, "og:image")
                ?: ExtractorUtils.htmlSearchMeta(webpage, "twitter:image")
        }
        val formats = mutableListOf(
            MediaFormat(
                formatId = "hls",
                url = "https://$host/media/hlsMedium/key/$videoId/format/auto/ext/mp4/learning/0/path/m3u8",
                ext = "mp4",
                protocol = "m3u8_native",
            ),
            MediaFormat(
                formatId = "http",
                url = "https://$host/getMedium/$videoId.mp4",
                ext = "mp4",
            ),
        )
        return InfoDict(
            id = videoId,
            title = title,
            description = description,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "vimp",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ViMP"

        val VALID_URL: Regex = Regex(
            "https?://(?<host>" + INSTANCE_PATTERN + ")/(?:" +
                "m/(?<tmpid>[0-9a-f]+)|" +
                "(?:category/)?video/(?<displayid>[\\w-]+)/(?<id>$HEX32)|" +
                "media/embed.*(?:\\?|&)key=(?<embedid>$HEX32)&?" +
                ")",
        )
    }
}

/** Upstream `ViMPPlaylistIE`: an album/category/channel/tag listing. */
class ViMPPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val host = match.groups["host"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val albumId = match.groups["albumid"]?.value
        val name = match.groups["name"]?.value
        val channelId = match.groups["channelid"]?.value
        val tagId = match.groups["tagid"]?.value
        val mode = match.groups["mode1"]?.value
            ?: match.groups["mode2"]?.value
            ?: match.groups["mode3"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = albumId ?: channelId ?: tagId ?: throw ExtractionError.UnsupportedUrl()
        // Upstream downloads the page with fatal=False.
        val webpage = try {
            http.downloadWebpage(url)
        } catch (error: ExtractionError) {
            ""
        }
        val title = ExtractorUtils.htmlSearchMeta(webpage, "title")
            ?: ExtractorUtils.searchRegex(
                "<title[^>]*>(.*?)</title>",
                webpage,
                setOf(RegexOption.DOT_MATCHES_ALL),
            )
        val urlPart = when {
            albumId != null -> "aid/$albumId"
            mode == "category" -> "category/$name/category_id/$channelId"
            mode == "channel" -> "title/$name/channel/$channelId"
            else -> "tag/$tagId"
        }
        val context = if (albumId != null) "4" else if (mode == "category") "1" else "0"
        val body = urlencodePostdata(
            listOf(
                "vars[mode]" to mode,
                "vars[$mode]" to playlistId,
                "vars[context]" to context,
                "vars[context_id]" to playlistId,
                "vars[layout]" to "thumb",
                "vars[per_page][thumb]" to PAGE_SIZE.toString(),
            ),
        ).encodeToByteArray()
        val entries = mutableListOf<InfoEntry>()
        // Upstream OnDemandPagedList starts at page 0 and stops on a short page.
        var page = 0
        while (page < MAX_PAGES) {
            val pageWebpage = try {
                http.downloadBytes(
                    HttpRequest(
                        url = "$host/media/ajax/component/boxList/$urlPart?page=$page&page_only=1",
                        method = HttpMethods.POST,
                        headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                        body = body,
                    ),
                ).decodeToString()
            } catch (error: ExtractionError) {
                break
            }
            val urls = Regex("\"([^\"]*/video/[^\"]+)\"").findAll(pageWebpage)
                .map { it.groupValues[1] }
                .toList()
            if (urls.isEmpty()) break
            for (path in urls) {
                entries += InfoEntry(url = host + path)
            }
            if (urls.size < PAGE_SIZE) break
            page++
        }
        return InfoDict(
            id = "$mode-$playlistId",
            title = title,
            entries = entries,
            webpageUrl = url,
            extractor = "vimp:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ViMPPlaylist"

        val VALID_URL: Regex = Regex(
            "(?<host>https?://(?:" + INSTANCE_PATTERN + "))/(?:" +
                "(?<mode1>album)/view/aid/(?<albumid>[0-9]+)|" +
                "(?<mode2>category|channel)/(?<name>[\\w-]+)/(?<channelid>[0-9]+)|" +
                "(?<mode3>tag)/(?<tagid>[0-9]+)" +
                ")",
        )
    }
}

// ------------------------------------------------------------------ helpers

/**
 * Upstream `urlencode_postdata` for the boxList form body: `[` and `]` are
 * percent-encoded and every non-unreserved byte becomes `%XX`.
 */
private fun urlencodePostdata(values: List<Pair<String, String>>): String =
    values.joinToString("&") { (key, value) -> "${percentEncode(key)}=${percentEncode(value)}" }

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (char.isLetterOrDigit() || char in "-_.~") {
            append(char)
        } else {
            append('%')
            append(HEX_DIGITS[code shr 4])
            append(HEX_DIGITS[code and 0x0F])
        }
    }
}
