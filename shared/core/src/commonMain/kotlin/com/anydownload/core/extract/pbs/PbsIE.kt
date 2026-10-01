/*
 * PBS extractors — AnyDownload
 *
 * Kotlin translation of the public page/player subset of `pbs.py` from
 * `yt_dlp/extractor/pbs.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `pbs.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the station-host URL list, the presumptive-slug page scan (multi-part
 * tabs, the media-id regexes, the partnerplayer iframe / `og:url` fallback),
 * the player-page `PBS.videoData` / `videoBridge` state, the
 * `widget/partnerplayer` and `portalplayer` redirects, chapters, captions,
 * the program-title join, and the `PBSKidsIE` deeplink state. HLS redirects
 * are recorded as `m3u8_native` and parsed at download time, so the HTTP
 * quality variants derived from the master and the audio-description
 * preference are not available. The Frontline `getdir` JSONP path, the
 * localization-cookie station setup, and the `US_RATINGS` full table are not
 * translated (the rating falls back to `parseAgeLimit`); geo and expired
 * redirect responses still fail typed. No cookie, token, or signed media URL
 * is stored here.
 */
package com.anydownload.core.extract.pbs

import com.anydownload.core.extract.Chapter
import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `PBSIE`: the station pages and the player pages. */
class PBSIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val page = extractWebpage(url)
        if (page.videoIds.isNotEmpty()) {
            return InfoDict(
                id = page.displayId,
                entries = page.videoIds.map {
                    InfoEntry(id = it, url = "http://video.pbs.org/video/$it")
                },
                webpageUrl = url,
                extractor = "pbs",
                extractorKey = IE_KEY,
            )
        }
        val videoId = page.videoId
            ?: throw ExtractionError.Unavailable("Could not find the PBS video id.")

        var info: JsonObject? = null
        val redirects = mutableListOf<JsonObject>()
        val redirectUrls = mutableSetOf<String>()
        val chapters = mutableListOf<Chapter>()
        for (pageName in listOf("widget/partnerplayer", "portalplayer")) {
            val player = try {
                http.downloadWebpage("http://player.pbs.org/$pageName/$videoId")
            } catch (error: ExtractionError) {
                null
            } ?: continue
            val videoInfo = extractVideoData(player)
            if (videoInfo != null) {
                collectRedirects(videoInfo, redirects, redirectUrls)
                if (info == null) info = videoInfo
            }
            if (chapters.isEmpty()) {
                val rawChapters = mutableListOf<JsonObject>()
                videoInfo?.array("chapters")?.let { array ->
                    rawChapters += array.mapNotNull { it as? JsonObject }
                }
                if (rawChapters.isEmpty()) {
                    for (match in Regex("(?s)chapters\\.push\\((.+?)\\)").findAll(player)) {
                        val chapter = ExtractorUtils.parseJson(
                            ExtractorUtils.jsToJson(match.groupValues[1]),
                        ) as? JsonObject ?: continue
                        rawChapters += chapter
                    }
                }
                for (chapter in rawChapters) {
                    val start = chapter.number("start_time")?.div(1000.0) ?: continue
                    val duration = chapter.number("duration")?.div(1000.0) ?: continue
                    chapters += Chapter(
                        startTime = start,
                        endTime = start + duration,
                        title = chapter.str("title"),
                    )
                }
            }
        }

        val formats = mutableListOf<MediaFormat>()
        for ((number, redirect) in redirects.withIndex()) {
            val redirectUrl = ExtractorUtils.urlOrNone(redirect.str("url")) ?: continue
            val redirectId = redirect.str("eeid")
            val redirectInfo = http.downloadJson("$redirectUrl?format=json") as? JsonObject
                ?: throw ExtractionError.Malformed("The PBS redirect response was not an object.")
            if (redirectInfo.str("status") == "error") {
                val httpCode = redirectInfo.number("http_code")?.toInt()
                val message = ERRORS[httpCode] ?: redirectInfo.str("message")
                    ?: "The PBS video is unavailable."
                if (httpCode == 403) {
                    throw ExtractionError.GeoRestricted(listOf("US"))
                }
                throw ExtractionError.Unavailable(message)
            }
            val formatUrl = ExtractorUtils.urlOrNone(redirectInfo.str("url")) ?: continue
            if (ExtractorUtils.determineExt(formatUrl, defaultExt = "") == "m3u8") {
                formats += MediaFormat(
                    formatId = redirectId ?: "hls-$number",
                    url = formatUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
            } else {
                formats += MediaFormat(formatId = redirectId, url = formatUrl)
            }
        }
        val dedupedFormats = formats.distinctBy { it.url }

        val subtitles = mutableListOf<SubtitleTrack>()
        info?.obj("cc")?.forEach { (_, value) ->
            val url = ExtractorUtils.urlOrNone((value as? JsonPrimitive)?.content) ?: return@forEach
            subtitles += SubtitleTrack(
                language = "en",
                formats = listOf(SubtitleFormat(ext = "vtt", url = url)),
            )
        }

        val programTitle = info?.obj("program")?.str("title")
        val rawTitle = info?.str("title")
        val title = if (programTitle != null && rawTitle != null) {
            programTitle + " - " + Regex("^" + Regex.escape(programTitle) + "[\\s\\-:]+").replace(rawTitle, "")
        } else {
            rawTitle
        }
        val description = info?.str("description")
            ?: info?.obj("program")?.str("description")
            ?: page.description
        val rating = info?.str("rating")?.substringAfterLast('-')

        return InfoDict(
            id = videoId,
            title = title,
            description = description,
            duration = info?.number("duration"),
            ageLimit = rating?.let(ExtractorUtils::parseAgeLimit),
            uploadDate = page.uploadDate,
            thumbnails = ExtractorUtils.urlOrNone(info?.str("image_url"))
                ?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            formats = dedupedFormats,
            subtitles = subtitles,
            chapters = chapters,
            webpageUrl = url,
            extractor = "pbs",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_extract_webpage`: the presumptive slug and player-id paths. */
    private suspend fun extractWebpage(url: String): PbsPage {
        var match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        var currentUrl = url
        var description: String? = null
        var uploadDate: String? = null
        var displayId = match.groups["presumptiveId"]?.value
        val presumptiveId = displayId

        if (presumptiveId != null) {
            val webpage = http.downloadWebpage(url)
            description = stripOrNone(
                ExtractorUtils.htmlSearchMeta(webpage, "og:description", "description"),
            )
            uploadDate = ExtractorUtils.searchRegex(
                "<input type=\"hidden\" id=\"air_date_[0-9]+\" value=\"([^\"]+)\"",
                webpage,
                default = null,
            )?.let(ExtractorUtils::unifiedStrdate)

            for (pattern in MULTI_PART_REGEXES) {
                val ids = Regex(pattern).findAll(webpage)
                    .map { it.groupValues[1] }
                    .distinct()
                    .toList()
                if (ids.isNotEmpty()) {
                    return PbsPage(ids, displayId = presumptiveId, uploadDate = uploadDate, description = description)
                }
            }

            var mediaId: String? = null
            for (pattern in MEDIA_ID_REGEXES) {
                mediaId = ExtractorUtils.searchRegex(pattern, webpage, default = null)
                if (mediaId != null) break
            }
            if (mediaId != null) {
                return PbsPage(videoId = mediaId, displayId = presumptiveId, uploadDate = uploadDate, description = description)
            }

            var candidateUrl: String? = null
            for (iframe in Regex("(?s)<iframe(.+?)></iframe>").findAll(webpage)) {
                candidateUrl = ExtractorUtils.searchRegex(
                    "src=([\"'])(?<url>.+?partnerplayer.+?)\\1",
                    iframe.value,
                    group = 2,
                    default = null,
                )
                if (candidateUrl != null) break
            }
            if (candidateUrl == null) candidateUrl = ExtractorUtils.htmlSearchMeta(webpage, "og:url")
            if (candidateUrl != null) {
                currentUrl = protoRelative(candidateUrl.trim())
                VALID_URL.find(currentUrl)?.let { match = it }
            }
        }

        val playerId = match.groups["playerId"]?.value
        if (displayId == null) displayId = playerId
        val videoId: String?
        if (playerId != null) {
            val playerPage = http.downloadWebpage(currentUrl)
            videoId = ExtractorUtils.searchRegex(
                "<div\\s+id=[\"']video_(\\d+)",
                playerPage,
                default = null,
            ) ?: extractVideoData(playerPage)?.let { data ->
                data.str("id") ?: data.str("contentID")
            }
        } else {
            videoId = match.groups["id"]?.value
            displayId = videoId
        }
        return PbsPage(videoId = videoId, displayId = displayId, uploadDate = uploadDate, description = description)
    }

    private fun collectRedirects(
        info: JsonObject,
        redirects: MutableList<JsonObject>,
        seen: MutableSet<String>,
    ) {
        for (name in listOf("recommended_encoding", "alternate_encoding")) {
            val redirect = info.obj(name) ?: continue
            val url = ExtractorUtils.urlOrNone(redirect.str("url")) ?: continue
            if (seen.add(url)) redirects += redirect
        }
        for (element in info.array("encodings").orEmpty()) {
            val url = ExtractorUtils.urlOrNone((element as? JsonPrimitive)?.content) ?: continue
            if (seen.add(url)) redirects += JsonObject(mapOf("url" to JsonPrimitive(url)))
        }
    }

    companion object {
        const val IE_KEY: String = "PBS"

        /** Upstream `_ERRORS`. */
        private val ERRORS = mapOf(
            101 to "We're sorry, but this video is not yet available.",
            403 to "We're sorry, but this video is not available in your region due to right restrictions.",
            404 to "We are experiencing technical difficulties that are preventing us from playing the video at this time. Please check back again soon.",
            410 to "This video has expired and is no longer available for online streaming.",
        )

        /** Upstream `MULTI_PART_REGEXES` for the tabbed Frontline videos. */
        private val MULTI_PART_REGEXES = listOf(
            "<div[^>]+class=\"videotab[^\"]*\"[^>]+vid=\"(\\d+)\"",
            "<a[^>]+href=[\"']#(?:video-|part)\\d+[\"'][^>]+data-cove[Ii]d=[\"'](\\d+)",
        )

        /** Upstream `MEDIA_ID_REGEXES`. */
        private val MEDIA_ID_REGEXES = listOf(
            "div\\s*:\\s*'videoembed'\\s*,\\s*mediaid\\s*:\\s*'(\\d+)'",
            "class=\"coveplayerid\">([^<]+)<",
            "<section[^>]+data-coveid=\"(\\d+)\"",
            "\\sclass=\"passportcoveplayer\"[^>]*\\sdata-media=\"(\\d+)",
            "<input type=\"hidden\" id=\"pbs_video_id_[0-9]+\" value=\"([0-9]+)\"/>",
            "(?s)window\\.PBS\\.playerConfig\\s*=\\s*\\{.*?id\\s*:\\s*'([0-9]+)',",
            "<div[^>]+\\bdata-cove-id=[\"'](\\d+)\"",
            "<iframe[^>]+\\bsrc=[\"'](?:https?:)?//video\\.pbs\\.org/widget/partnerplayer/(\\d+)",
            "\\\\\"videoTPMediaId\\\\\":\\\\\"(\\d+)\\\\\"",
            "\\bhttps?://player\\.pbs\\.org/[\\w-]+player/(\\d+)",
        )

        /** Upstream `_STATIONS` host patterns. */
        private val STATION_HOSTS = listOf(
            "(?:video|www|player)\\.pbs\\.org",
            "video\\.aptv\\.org",
            "video\\.gpb\\.org",
            "video\\.mpbonline\\.org",
            "video\\.wnpt\\.org",
            "video\\.wfsu\\.org",
            "video\\.wsre\\.org",
            "video\\.wtcitv\\.org",
            "video\\.pba\\.org",
            "video\\.alaskapublic\\.org",
            "video\\.azpbs\\.org",
            "portal\\.knme\\.org",
            "video\\.vegaspbs\\.org",
            "watch\\.aetn\\.org",
            "video\\.ket\\.org",
            "video\\.wkno\\.org",
            "video\\.lpb\\.org",
            "videos\\.oeta\\.tv",
            "video\\.optv\\.org",
            "watch\\.wsiu\\.org",
            "video\\.keet\\.org",
            "pbs\\.kixe\\.org",
            "video\\.kpbs\\.org",
            "video\\.kqed\\.org",
            "vids\\.kvie\\.org",
            "(?:video\\.|www\\.)pbssocal\\.org",
            "video\\.valleypbs\\.org",
            "video\\.cptv\\.org",
            "watch\\.knpb\\.org",
            "video\\.soptv\\.org",
            "video\\.rmpbs\\.org",
            "video\\.kenw\\.org",
            "video\\.kued\\.org",
            "video\\.wyomingpbs\\.org",
            "video\\.cpt12\\.org",
            "video\\.kbyueleven\\.org",
            "(?:video\\.|www\\.)thirteen\\.org",
            "video\\.wgbh\\.org",
            "video\\.wgby\\.org",
            "watch\\.njtvonline\\.org",
            "watch\\.wliw\\.org",
            "video\\.mpt\\.tv",
            "watch\\.weta\\.org",
            "video\\.whyy\\.org",
            "video\\.wlvt\\.org",
            "video\\.wvpt\\.net",
            "video\\.whut\\.org",
            "video\\.wedu\\.org",
            "video\\.wgcu\\.org",
            "video\\.wpbt2\\.org",
            "video\\.wucftv\\.org",
            "video\\.wuft\\.org",
            "watch\\.wxel\\.org",
            "video\\.wlrn\\.org",
            "video\\.wusf\\.usf\\.edu",
            "video\\.scetv\\.org",
            "video\\.unctv\\.org",
            "video\\.pbshawaii\\.org",
            "video\\.idahoptv\\.org",
            "video\\.ksps\\.org",
            "watch\\.opb\\.org",
            "watch\\.nwptv\\.org",
            "video\\.will\\.illinois\\.edu",
            "video\\.networkknowledge\\.tv",
            "video\\.wttw\\.com",
            "video\\.iptv\\.org",
            "video\\.ninenet\\.org",
            "video\\.wfwa\\.org",
            "video\\.wfyi\\.org",
            "video\\.mptv\\.org",
            "video\\.wnin\\.org",
            "video\\.wnit\\.org",
            "video\\.wpt\\.org",
            "video\\.wvut\\.org",
            "video\\.weiu\\.net",
            "video\\.wqpt\\.org",
            "video\\.wycc\\.org",
            "video\\.wipb\\.org",
            "video\\.indianapublicmedia\\.org",
            "watch\\.cetconnect\\.org",
            "video\\.thinktv\\.org",
            "video\\.wbgu\\.org",
            "video\\.wgvu\\.org",
            "video\\.netnebraska\\.org",
            "video\\.pioneer\\.org",
            "watch\\.sdpb\\.org",
            "video\\.tpt\\.org",
            "watch\\.ksmq\\.org",
            "watch\\.kpts\\.org",
            "watch\\.ktwu\\.org",
            "watch\\.easttennesseepbs\\.org",
            "video\\.wcte\\.tv",
            "video\\.wljt\\.org",
            "video\\.wosu\\.org",
            "video\\.woub\\.org",
            "video\\.wvpublic\\.org",
            "video\\.wkyupbs\\.org",
            "video\\.kera\\.org",
            "video\\.mpbn\\.net",
            "video\\.mountainlake\\.org",
            "video\\.nhptv\\.org",
            "video\\.vpt\\.org",
            "video\\.witf\\.org",
            "watch\\.wqed\\.org",
            "video\\.wmht\\.org",
            "video\\.deltabroadcasting\\.org",
            "video\\.dptv\\.org",
            "video\\.wcmu\\.org",
            "video\\.wkar\\.org",
            "wnmuvideo\\.nmu\\.edu",
            "video\\.wdse\\.org",
            "video\\.wgte\\.org",
            "video\\.lptv\\.org",
            "video\\.kmos\\.org",
            "watch\\.montanapbs\\.org",
            "video\\.krwg\\.org",
            "video\\.kacvtv\\.org",
            "video\\.kcostv\\.org",
            "video\\.wcny\\.org",
            "video\\.wned\\.org",
            "watch\\.wpbstv\\.org",
            "video\\.wskg\\.org",
            "video\\.wxxi\\.org",
            "video\\.wpsu\\.org",
            "on-demand\\.wvia\\.org",
            "video\\.wtvi\\.org",
            "video\\.westernreservepublicmedia\\.org",
            "video\\.ideastream\\.org",
            "video\\.kcts9\\.org",
            "video\\.basinpbs\\.org",
            "video\\.houstonpbs\\.org",
            "video\\.klrn\\.org",
            "video\\.klru\\.tv",
            "video\\.wtjx\\.org",
            "video\\.ideastations\\.org",
            "video\\.kbtc\\.org",
        )

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:video|player)\\.pbs\\.org/(?:widget/)?partnerplayer/(?<playerId>[^/?#]+)|" +
                "(?:" + STATION_HOSTS.joinToString("|") + ")/(?:" +
                "(?:(?:vir|port)alplayer|video)/(?<id>[0-9]+)(?:[?/#]|$)|" +
                "(?:[^/?#]+/){1,5}(?<presumptiveId>[^/?#]+?)(?:\\.html)?/?(?:$|[?#])))",
        )
    }
}

/** Upstream `PBSKidsIE`: the pbskids.org deeplink state. */
class PBSKidsIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val meta = extractJsonElement(webpage, Regex("window\\._PBS_KIDS_DEEPLINK\\s*=")) as? JsonObject
            ?: throw ExtractionError.Malformed("The PBS Kids page had no deeplink state.")
        val videoObj = meta.obj("video_obj")
            ?: throw ExtractionError.Malformed("The PBS Kids state had no video object.")
        val uri = ExtractorUtils.urlOrNone(videoObj.str("URI"))
            ?: throw ExtractionError.Malformed("The PBS Kids state had no video URI.")
        return InfoDict(
            id = videoId,
            title = videoObj.str("title"),
            description = videoObj.str("description"),
            duration = videoObj.number("duration"),
            channel = meta.str("show_slug"),
            uploadDate = videoObj.str("air_date")?.let(ExtractorUtils::unifiedStrdate),
            formats = listOf(
                MediaFormat(formatId = "hls", url = uri, ext = "mp4", protocol = "m3u8_native"),
            ),
            webpageUrl = url,
            extractor = "pbs",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PBSKids"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?pbskids\\.org/video/[\\w-]+/(?<id>\\d+)")
    }
}

/** The result of the presumptive-slug or player-id page walk. */
private data class PbsPage(
    val videoIds: List<String> = emptyList(),
    val videoId: String? = null,
    val displayId: String? = null,
    val uploadDate: String? = null,
    val description: String? = null,
)

/** Upstream `_extract_video_data`; null when neither marker is present. */
private fun extractVideoData(webpage: String): JsonObject? {
    for (marker in listOf(
        Regex("PBS\\.videoData\\s*=\\s*"),
        Regex("window\\.videoBridge\\s*=\\s*"),
    )) {
        extractJsonElement(webpage, marker)?.let { return it as? JsonObject }
    }
    return null
}

/** Upstream `_search_json` subset: balanced JSON after [marker]. */
private fun extractJsonElement(html: String, marker: Regex): JsonElement? {
    val match = marker.find(html) ?: return null
    var index = match.range.last + 1
    while (index < html.length && html[index].isWhitespace()) index++
    val opening = html.getOrNull(index) ?: return null
    if (opening != '{' && opening != '[') return null
    var depth = 0
    var inString = false
    var quote = ' '
    var escaped = false
    for (position in index until html.length) {
        val character = html[position]
        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == quote -> inString = false
            }
            continue
        }
        when (character) {
            '"', '\'' -> {
                inString = true
                quote = character
            }

            '{', '[' -> depth++
            '}', ']' -> {
                depth--
                if (depth == 0) {
                    return ExtractorUtils.parseJson(html.substring(index, position + 1))
                }
            }
        }
    }
    return null
}

private fun protoRelative(value: String): String =
    if (value.startsWith("//")) "https:$value" else value

private fun stripOrNone(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
