/*
 * CBC extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `cbc.py` from
 * `yt_dlp/extractor/cbc.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `cbc.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `window.__INITIAL_STATE__` page state (`CBCIE`,
 * `CBCPlayerIE`, `CBCPlayerPlaylistIE`), the CBC Gem show/media validation
 * APIs (`CBCGemBaseIE`, `CBCGemIE`, `CBCGemPlaylistIE`, `CBCGemContentIE`,
 * `CBCGemOlympicsIE`, `CBCGemLiveIE`), and the CBC Listen clips API with its
 * preloaded-state fallback. HLS manifests are recorded as `m3u8_native` and
 * parsed at download time, so the descriptor preference and the direct
 * https-mp4 HEAD probe are not available. The Gem OAuth login/claims flow is
 * not translated (no app login); the media validation error codes still map
 * to typed geo, login, and unavailable failures. The deprecated ThePlatform
 * fallback in `CBCPlayerIE`, live `is_upcoming` gating, and the `js_to_json`
 * leniency are ported at the level the fixtures cover. No cookie, bearer
 * token, or signed media URL is stored here.
 */
package com.anydownlod.core.extract.cbc

import com.anydownlod.core.extract.Chapter
import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoEntry
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.SubtitleFormat
import com.anydownlod.core.extract.SubtitleTrack
import com.anydownlod.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/** Upstream `CBCIE`: the cbc.ca article pages that carry player embeds. */
class CBCIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val title = pageTitle(webpage)
        val initialState = extractJsonElement(
            webpage,
            Regex("window\\.__INITIAL_STATE__\\s*="),
            ExtractorUtils::jsToJson,
        ) as? JsonObject

        val mediaIds = linkedSetOf<String>()
        for (match in Regex("CBC\\.APP\\.Caffeine\\.initInstance\\((\\{.+?\\})\\);").findAll(webpage)) {
            mediaIds += playerInitMediaIds(match.groupValues[1], displayId)
        }
        for (pattern in listOf(
            Regex("<iframe[^>]+src=\"[^\"]+?mediaId=(\\d+)\""),
            Regex("<div[^>]+\\bid=[\"']player-(\\d+)"),
            Regex("guid[\"']\\s*:\\s*[\"'](\\d+)"),
        )) {
            for (match in pattern.findAll(webpage)) mediaIds += match.groupValues[1]
        }
        initialState?.obj("detail")?.let { detail ->
            for (item in bodyItems(detail)) {
                if (item.str("type") == "polopoly_media") {
                    item.obj("content")?.str("sourceId")?.let { mediaIds += it }
                }
            }
        }
        initialState?.obj("app")?.str("contentId")?.let { mediaIds += it }

        return InfoDict(
            id = displayId,
            title = title,
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
            entries = mediaIds.map { InfoEntry(id = it, url = "https://www.cbc.ca/player/play/$it") },
            webpageUrl = url,
            extractor = "cbc",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_extract_player_init`: `mediaId`, or a clip-id feed lookup. */
    private suspend fun playerInitMediaIds(playerInit: String, displayId: String): List<String> {
        val info = ExtractorUtils.parseJson(ExtractorUtils.jsToJson(playerInit)) as? JsonObject
            ?: return emptyList()
        info.str("mediaId")?.let { return listOf(it) }
        val clipId = info.str("clipId") ?: return emptyList()
        val feed = try {
            http.downloadJson(
                "http://tpfeed.cbc.ca/f/ExhSPC/vms_5akSXx4Ng_Zn?byCustomValue={:mpsReleases}{$clipId}",
            ) as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        feed?.array("entries")?.firstOrNull()?.let { entry ->
            (entry as? JsonObject)?.str("guid")?.let { return listOf(it) }
        }
        val platform = try {
            http.downloadJson(
                "http://feed.theplatform.com/f/h9dtGB/punlNGjMlc1F?fields=id" +
                    "&byContent=byReleases%3DbyId%253D$clipId",
            ) as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        return platform?.array("entries")?.firstOrNull()?.let { entry ->
            (entry as? JsonObject)?.str("id")?.substringAfterLast('/')?.let { listOf(it) }
        }.orEmpty()
    }

    private fun bodyItems(detail: JsonObject): List<JsonObject> {
        val bodies = when (val body = detail.obj("content")?.get("body")) {
            is JsonArray -> body.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(body)
            else -> emptyList()
        }
        return bodies.flatMap { body ->
            body.array("content").orEmpty().mapNotNull { it as? JsonObject }
        }
    }

    companion object {
        const val IE_KEY: String = "CBC"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?cbc\\.ca/(?!player/|listen/|i/caffeine/syndicate/)" +
                "(?:[^/?#]+/)+(?<id>[^/?#]+)",
        )
    }
}

/** Upstream `CBCPlayerIE`: the CBC player page and its `cbcplayer:` id. */
class CBCPlayerIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage("https://www.cbc.ca/player/play/$videoId")
        val state = extractJsonElement(
            webpage,
            Regex("window\\.__INITIAL_STATE__\\s*="),
            ExtractorUtils::jsToJson,
        ) as? JsonObject ?: throw ExtractionError.Malformed("The CBC player page had no initial state.")
        val data = state.obj("video")?.obj("currentClip")
            ?: throw ExtractionError.Malformed("The CBC player page had no current clip.")
        val media = data.obj("media")
        val assets = media?.array("assets").orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { ExtractorUtils.urlOrNone(it.str("key")) != null && it.str("type") != null }
        if (assets.isEmpty() && data.str("mediaId") != null) {
            throw ExtractionError.Unavailable(
                "This CBC video only has the deprecated ThePlatform path; the ThePlatform player " +
                    "extractor lands with D18.",
            )
        }
        val isLive = media?.str("streamType") == "Live"
        val formats = mutableListOf<MediaFormat>()
        for (asset in assets) {
            val key = asset.str("key") ?: continue
            val type = asset.str("type") ?: continue
            if (type != "medianet") continue
            val assetData = http.downloadJson(key) as? JsonObject ?: continue
            val assetUrl = ExtractorUtils.urlOrNone(assetData.str("url")) ?: continue
            val ext = ExtractorUtils.mimetype2ext(param(assetData, "contentType"))
            if (ext == "m3u8") {
                formats += MediaFormat(
                    formatId = "hls",
                    url = assetUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
            } else {
                formats += MediaFormat(
                    url = assetUrl,
                    ext = ext,
                    vcodec = if (param(assetData, "mediaType") == "audio") MediaFormat.CODEC_NONE else null,
                )
            }
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in media?.array("textTracks").orEmpty()) {
            val track = element as? JsonObject ?: continue
            val src = ExtractorUtils.urlOrNone(track.str("src")) ?: continue
            subtitles += SubtitleTrack(
                language = track.str("language") ?: "und",
                name = track.str("label"),
                formats = listOf(SubtitleFormat(ext = "vtt", url = src)),
            )
        }

        val chapters = media?.array("chapters").orEmpty().mapNotNull { element ->
            val chapter = element as? JsonObject ?: return@mapNotNull null
            val start = chapter.number("startTime")?.div(1000.0) ?: return@mapNotNull null
            Chapter(
                startTime = start,
                endTime = chapter.number("endTime")?.div(1000.0),
                title = chapter.str("name"),
            )
        }.toList()
        val keepChapters = !(
            chapters.size == 1 && chapters[0].startTime == 0.0 && chapters[0].endTime == null
            )

        return InfoDict(
            id = videoId,
            title = data.str("title"),
            description = data.str("description"),
            thumbnails = cleanImage(data.obj("image")?.str("url"))?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            duration = media?.number("duration"),
            uploadDate = data.number("publishedAt")?.div(1000)?.toLong()
                ?.let(ExtractorUtils::epochSecondsToDate),
            chapters = if (keepChapters) chapters else emptyList(),
            subtitles = subtitles,
            formats = formats,
            isLive = isLive,
            webpageUrl = url,
            extractor = "cbc",
            extractorKey = IE_KEY,
        )
    }

    private fun param(assetData: JsonObject, name: String): String? =
        assetData.array("params").orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.str("name") == name }
            ?.str("value")

    companion object {
        const val IE_KEY: String = "CBCPlayer"

        val VALID_URL: Regex = Regex(
            "(?:cbcplayer:|https?://(?:www\\.)?cbc\\.ca/(?:player/play/(?:video/)?|" +
                "i/caffeine/syndicate/\\?mediaId=))(?<id>(?:\\d\\.)?\\d+)",
        )
    }
}

/** Upstream `CBCPlayerPlaylistIE`: the `/player/<category>` listing. */
class CBCPlayerPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = percentDecode(matchId(url) ?: throw ExtractionError.UnsupportedUrl()).lowercase()
        val webpage = http.downloadWebpage(url)
        val state = extractJsonElement(
            webpage,
            Regex("window\\.__INITIAL_STATE__\\s*="),
            ExtractorUtils::jsToJson,
        ) as? JsonObject ?: throw ExtractionError.Malformed("The CBC playlist page had no initial state.")
        val category = findKeyIgnoreCase(state.obj("video")?.obj("clipsByCategory"), playlistId)
        val entries = category?.array("items").orEmpty().mapNotNull { element ->
            val id = (element as? JsonObject)?.str("id") ?: return@mapNotNull null
            InfoEntry(id = id, url = "https://www.cbc.ca/player/play/$id")
        }
        return InfoDict(
            id = playlistId,
            entries = entries,
            webpageUrl = url,
            extractor = "cbc",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "CBCPlayerPlaylist"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?cbc\\.ca/(?:player/)(?!play/)(?<id>[^?#]+)")
    }
}

/** One item's mapped metadata from `CBCGemBaseIE._extract_item_info`. */
data class GemItemInfo(
    val title: String? = null,
    val episodeNumber: Long? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Double? = null,
    val uploadDate: String? = null,
    val ageLimit: Int? = null,
)

/**
 * Upstream `CBCGemBaseIE`: the radio-canada show/media validation APIs. The
 * OAuth login and claims-token flow is not translated; public items need no
 * token, and the validation error codes still fail typed.
 */
abstract class CBCGemBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_call_show_api`. */
    protected suspend fun callShowApi(itemId: String): JsonObject =
        http.downloadJson("https://services.radio-canada.ca/ott/catalog/v2/gem/show/$itemId?device=web")
            as? JsonObject ?: throw ExtractionError.Malformed("The CBC Gem show API returned no object.")

    /** Upstream `_call_media_api` with the mapped validation error codes. */
    protected suspend fun callMediaApi(mediaId: String, appCode: String = "gem"): JsonObject {
        val query = "?appCode=$appCode&connectionType=hd&deviceType=ipad&multibitrate=true&output=json" +
            "&tech=hls&manifestVersion=2&manifestType=desktop&idMedia=$mediaId"
        val json = http.downloadJson("https://services.radio-canada.ca/media/validation/v2/$query")
            as? JsonObject ?: throw ExtractionError.Malformed("The CBC media API returned no object.")
        val errorCode = json.number("errorCode")?.toInt()
        when {
            errorCode == 1 -> throw ExtractionError.GeoRestricted(listOf("CA"))
            errorCode == 35 -> throw ExtractionError.LoginRequired(
                "This CBC Gem video needs a CBC account sign-in.",
            )

            errorCode != 0 -> throw ExtractionError.Unavailable(
                listOfNotNull(errorCode?.toString(), json.str("message")).joinToString(" - ")
                    .ifEmpty { "The CBC media API rejected the request." },
            )
        }
        return json
    }

    /** Upstream `_extract_item_info` for the modeled fields. */
    protected fun extractItemInfo(item: JsonObject): GemItemInfo {
        var title = item.str("title")
        var episodeNumber: Long? = null
        if (title != null) {
            val match = EPISODE_TITLE.matchEntire(title)
            if (match != null) {
                episodeNumber = match.groups["episode"]?.value?.toLongOrNull()
                title = match.groups["title"]?.value
            }
        }
        val metadata = item.obj("metadata")
        return GemItemInfo(
            title = title,
            episodeNumber = metadata?.number("episodeNumber")?.toLong() ?: episodeNumber,
            description = item.str("description"),
            thumbnailUrl = cleanImage(item.obj("images")?.obj("card")?.str("url")),
            duration = metadata?.number("duration"),
            uploadDate = metadata?.str("availabilityDate")?.let(ExtractorUtils::unifiedStrdate),
            ageLimit = metadata?.str("rating")?.removePrefix("C")?.let(ExtractorUtils::parseAgeLimit),
        )
    }

    companion object {
        private val EPISODE_TITLE = Regex("(?<episode>\\d+)\\. (?<title>.+)")
    }
}

/** Upstream `CBCGemIE`: one gem.cbc.ca episode. */
class CBCGemIE(
    http: ExtractorHttp,
) : CBCGemBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val showInfo = callShowApi(videoId)
        val item = findGemItem(showInfo) { it.str("url") == videoId }
            ?: throw ExtractionError.Malformed("The CBC Gem show API had no item for $videoId.")
        val mediaId = item.str("idMedia")
            ?: throw ExtractionError.Malformed("The CBC Gem item had no media id.")
        val media = callMediaApi(mediaId)
        val m3u8Url = ExtractorUtils.urlOrNone(media.str("url"))
            ?: throw ExtractionError.Malformed("The CBC media API had no stream URL.")
        val itemInfo = extractItemInfo(item)
        return InfoDict(
            id = videoId,
            title = itemInfo.title,
            description = itemInfo.description,
            thumbnails = itemInfo.thumbnailUrl?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            duration = itemInfo.duration,
            uploadDate = itemInfo.uploadDate,
            ageLimit = itemInfo.ageLimit,
            formats = listOf(
                MediaFormat(
                    formatId = "hls",
                    url = setQueryParam(m3u8Url, "manifestType", ""),
                    ext = "mp4",
                    protocol = "m3u8_native",
                ),
            ),
            webpageUrl = url,
            extractor = "cbc",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "CBCGem"

        val VALID_URL: Regex = Regex(
            "https?://gem\\.cbc\\.ca/(?:media/)?(?<id>[0-9a-z-]+/s(?<season>[0-9]+)[a-z][0-9]{2,4})/?(?:[?#]|$)",
        )
    }
}

/** Upstream `CBCGemPlaylistIE`: one season of a CBC Gem show. */
class CBCGemPlaylistIE(
    http: ExtractorHttp,
) : CBCGemBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val seasonId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val show = match.groups["show"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val season = match.groups["season"]?.value?.toIntOrNull()
        val showInfo = callShowApi(show)
        val seasonInfo = findGemLineup(showInfo) { it.number("seasonNumber")?.toInt() == season }
            ?: throw ExtractionError.Malformed("The CBC Gem show API had no season $season for $show.")
        val entries = seasonInfo.array("items").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val itemUrl = item.str("url") ?: return@mapNotNull null
            InfoEntry(
                id = item.str("id") ?: itemUrl,
                title = extractItemInfo(item).title,
                url = "https://gem.cbc.ca/media/$itemUrl",
            )
        }
        return InfoDict(
            id = seasonId,
            title = seasonInfo.str("title"),
            entries = entries,
            webpageUrl = url,
            extractor = "cbc",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "CBCGemPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://gem\\.cbc\\.ca/(?:media/)?(?<id>(?<show>[0-9a-z-]+)/s(?<season>[0-9]+))/?(?:[?#]|$)",
        )
    }
}

/** Upstream `CBCGemContentIE`: the gem.cbc.ca show landing pages. */
class CBCGemContentIE(
    http: ExtractorHttp,
) : CBCGemBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val data = searchNextJsData(webpage)?.obj("props")?.obj("pageProps")?.obj("data")
            ?: throw ExtractionError.Malformed("The CBC Gem page had no Next.js data.")

        if (data.str("contentType") == "Standalone") {
            val target = data.obj("header")?.obj("cta")?.obj("media")?.str("url")
                ?.let { urlJoin(GEM_BASE, it) }
            if (target != null && CBCGemOlympicsIE.VALID_URL.containsMatchIn(target)) {
                return InfoDict(
                    id = displayId,
                    webpageUrl = url,
                    redirectUrl = target,
                    extractor = "cbc",
                    extractorKey = IE_KEY,
                )
            }
            return InfoDict(
                id = displayId,
                webpageUrl = url,
                redirectUrl = "$GEM_BASE$displayId/s01e01",
                extractor = "cbc",
                extractorKey = IE_KEY,
            )
        }

        val entries = mutableListOf<InfoEntry>()
        for (content in data.array("content").orEmpty()) {
            val contentObject = content as? JsonObject ?: continue
            for (lineup in contentObject.array("lineups").orEmpty()) {
                val lineupUrl = (lineup as? JsonObject)?.str("url") ?: continue
                val resolved = urlJoin(GEM_BASE, lineupUrl)
                if (CBCGemPlaylistIE.VALID_URL.containsMatchIn(resolved)) {
                    entries += InfoEntry(url = resolved)
                }
            }
        }
        return InfoDict(
            id = displayId,
            entries = entries,
            webpageUrl = url,
            extractor = "cbc",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "CBCGemContent"

        val VALID_URL: Regex = Regex("https?://gem\\.cbc\\.ca/(?<id>[0-9a-z-]+)/?(?:[?#]|$)")
    }
}

/** Upstream `CBCGemOlympicsIE`: the numbered Olympics standalone pages. */
class CBCGemOlympicsIE(
    http: ExtractorHttp,
) : CBCGemBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val mediaId = match.groups["mediaId"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val videoInfo = callShowApi(videoId)
        val item = findGemItem(videoInfo) { it.str("formattedIdMedia") == mediaId }
            ?: throw ExtractionError.Malformed("The CBC Gem show API had no item for media $mediaId.")

        val isLive = when (item.str("type")) {
            "LiveEvent" -> true
            "Replay" -> false
            else -> null
        }
        val releaseDate = item.obj("metadata")?.let { metadata ->
            metadata.obj("live")?.str("startDate") ?: metadata.obj("replay")?.str("airDate")
        }?.let(ExtractorUtils::unifiedStrdate)
        if (isLive == true && releaseDate != null && releaseDate > currentDate()) {
            throw ExtractionError.NotYetAvailable("This livestream has not yet started.")
        }
        val media = callMediaApi(mediaId, appCode = "medianetlive")
        val m3u8Url = ExtractorUtils.urlOrNone(media.str("url"))
            ?: throw ExtractionError.Malformed("The CBC media API had no stream URL.")
        return InfoDict(
            id = videoId,
            title = item.str("title"),
            description = item.str("description"),
            thumbnails = cleanImage(item.obj("images")?.obj("card")?.str("url"))
                ?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            duration = item.obj("metadata")?.obj("replay")?.number("duration"),
            uploadDate = releaseDate,
            formats = listOf(
                MediaFormat(formatId = "hls", url = m3u8Url, ext = "mp4", protocol = "m3u8_native"),
            ),
            isLive = isLive,
            webpageUrl = url,
            extractor = "cbc",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "CBCGemOlympics"

        val VALID_URL: Regex = Regex(
            "https?://gem\\.cbc\\.ca/(?<id>(?:[0-9a-z]+-)+[0-9]{5,})/s01e(?<mediaId>[0-9]{5,})",
        )
    }
}

/** Upstream `CBCGemLiveIE`: the gem.cbc.ca live pages and replays. */
class CBCGemLiveIE(
    http: ExtractorHttp,
) : CBCGemBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val root = searchNextJsData(webpage)?.obj("props")?.obj("pageProps")?.obj("data")
            ?: throw ExtractionError.Malformed("The CBC Gem live page had no Next.js data.")

        var videoInfo = root
        if (videoInfo.str("formattedIdMedia") == null) {
            val event = root.obj("event")
            videoInfo = if (event?.str("key") == videoId) {
                event
            } else {
                findFreeTvStream(root, videoId)
                    ?: throw ExtractionError.Unavailable(
                        "Could not find the video metadata; this livestream may be offline.",
                    )
            }
        }
        val streamId = videoInfo.str("formattedIdMedia")
            ?: throw ExtractionError.Unavailable("Could not find the video metadata; this livestream may be offline.")
        val isLive = videoInfo.bool("isVodEnabled") != true
        val releaseDate = videoInfo.str("airDate")?.let(ExtractorUtils::unifiedStrdate)
        if (isLive && releaseDate != null && releaseDate > currentDate()) {
            throw ExtractionError.NotYetAvailable("This livestream has not yet started.")
        }
        val media = callMediaApi(streamId, appCode = "medianetlive")
        val m3u8Url = ExtractorUtils.urlOrNone(media.str("url"))
            ?: throw ExtractionError.Malformed("The CBC media API had no stream URL.")
        return InfoDict(
            id = videoId,
            title = videoInfo.str("title"),
            description = videoInfo.str("description"),
            thumbnails = cleanImage(videoInfo.obj("images")?.obj("card")?.str("url"))
                ?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            uploadDate = releaseDate,
            formats = listOf(
                MediaFormat(formatId = "hls", url = m3u8Url, ext = "mp4", protocol = "m3u8_native"),
            ),
            isLive = isLive,
            webpageUrl = url,
            extractor = "cbc",
            extractorKey = IE_KEY,
        )
    }

    private fun findFreeTvStream(root: JsonObject, videoId: String): JsonObject? {
        val streams = root.obj("freeTv")?.array("streams").orEmpty()
        for (stream in streams) {
            val items = (stream as? JsonObject)?.array("items").orEmpty()
            for (item in items) {
                val itemObject = item as? JsonObject ?: continue
                if (itemObject.str("key")?.substringBefore('-') == videoId) return itemObject
            }
        }
        return null
    }

    companion object {
        const val IE_KEY: String = "CBCGemLive"

        val VALID_URL: Regex = Regex("https?://gem\\.cbc\\.ca/live(?:-event)?/(?<id>\\d+)")
    }
}

/** Upstream `CBCListenIE`: the cbc.ca/listen clips API. */
class CBCListenIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        var data = (try {
            http.downloadJson("https://www.cbc.ca/listen/api/v1/clips/$videoId")
        } catch (error: ExtractionError) {
            null
        } as? JsonObject)?.obj("data")
        if (data == null) {
            val webpage = http.downloadWebpage(url)
            val preloaded = extractJsonElement(
                webpage,
                Regex("window\\.__PRELOADED_STATE__\\s*="),
                ExtractorUtils::jsToJson,
            ) as? JsonObject
            data = findListenEpisode(preloaded, videoId)
        }
        val episode = data ?: throw ExtractionError.Unavailable("The CBC Listen clip has no data.")
        return InfoDict(
            id = videoId,
            title = episode.str("title"),
            description = episode.str("description"),
            duration = episode.number("duration"),
            uploadDate = episode.number("airdate")?.div(1000)?.toLong()
                ?.let(ExtractorUtils::epochSecondsToDate),
            url = ExtractorUtils.urlOrNone(episode.str("url") ?: episode.str("src")),
            webpageUrl = url,
            extractor = "cbc",
            extractorKey = IE_KEY,
        )
    }

    private fun findListenEpisode(state: JsonObject?, videoId: String): JsonObject? {
        for (key in listOf("podcastDetailData", "showDetailData")) {
            val node = state?.get(key) ?: continue
            val shows = when (node) {
                is JsonObject -> node.values.toList()
                is JsonArray -> node.toList()
                else -> continue
            }
            for (show in shows) {
                val episodes = when (val value = (show as? JsonObject)?.get("episodes")) {
                    is JsonArray -> value.toList()
                    is JsonObject -> value.values.toList()
                    else -> continue
                }
                for (episode in episodes) {
                    val episodeObject = episode as? JsonObject ?: continue
                    if (episodeObject.str("clipID") == videoId) return episodeObject
                }
            }
        }
        return null
    }

    companion object {
        const val IE_KEY: String = "CBCListen"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?cbc\\.ca/listen/(?:cbc-podcasts|live-radio)/[\\w-]+/[\\w-]+/(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private const val GEM_BASE = "https://gem.cbc.ca/"

private val NEXT_DATA = Regex(
    "<script[^>]+id=\"__NEXT_DATA__\"[^>]*>(.+?)</script>",
    RegexOption.DOT_MATCHES_ALL,
)

private val TITLE_TAG = Regex("<title[^>]*>(.+?)</title>", RegexOption.DOT_MATCHES_ALL)

/** Upstream `_search_nextjs_data`. */
private fun searchNextJsData(html: String): JsonObject? =
    NEXT_DATA.find(html)?.groupValues?.get(1)?.let { ExtractorUtils.parseJson(it) as? JsonObject }

/** Upstream `_search_json` subset: balanced JSON after [marker], then [transform]. */
private fun extractJsonElement(
    html: String,
    marker: Regex,
    transform: (String) -> String = { it },
): JsonElement? {
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
                    return ExtractorUtils.parseJson(transform(html.substring(index, position + 1)))
                }
            }
        }
    }
    return null
}

/** Upstream `_og_search_title` / `_html_extract_title` subset. */
private fun pageTitle(html: String): String? =
    ExtractorUtils.htmlSearchMeta(html, "og:title", "twitter:title")
        ?: TITLE_TAG.find(html)?.groupValues?.get(1)?.let(ExtractorUtils::unescapeHtml)?.trim()

/** Upstream `update_url(query=None)`: drop the image URL's query. */
private fun cleanImage(url: String?): String? =
    ExtractorUtils.urlOrNone(url)?.substringBefore('?')?.substringBefore('#')

/** `collections.OrderedDict`-style first match with case-insensitive keys. */
private fun findKeyIgnoreCase(obj: JsonObject?, key: String): JsonObject? {
    val node = obj ?: return null
    val match = node.keys.firstOrNull { it.equals(key, ignoreCase = true) } ?: return null
    return node[match] as? JsonObject
}

/** Upstream `update_url_query` for one key. */
private fun setQueryParam(url: String, key: String, value: String): String {
    val hash = url.indexOf('#').let { if (it >= 0) url.substring(it) else "" }
    val withoutHash = if (hash.isEmpty()) url else url.substring(0, url.length - hash.length)
    val question = withoutHash.indexOf('?')
    val base = if (question >= 0) withoutHash.substring(0, question) else withoutHash
    val query = if (question >= 0) withoutHash.substring(question + 1) else ""
    val params = query.split('&').filter { it.isNotEmpty() && it.substringBefore('=') != key }.toMutableList()
    params += "$key=$value"
    return base + "?" + params.joinToString("&") + hash
}

/** Upstream `urljoin('https://gem.cbc.ca/')` for the two relative forms. */
private fun urlJoin(base: String, value: String): String = when {
    value.startsWith("http://") || value.startsWith("https://") -> value
    value.startsWith("/") -> base.trimEnd('/') + value
    else -> base.trimEnd('/') + "/" + value
}

/** `urllib.parse.unquote` for the playlist id. */
private fun percentDecode(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%' && index + 2 < value.length) {
            val code = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (code != null) {
                append(code.toChar())
                index += 3
                continue
            }
        }
        append(character)
        index++
    }
}

private fun nowSeconds(): Long = Clock.System.now().toEpochMilliseconds() / 1000

/** Today's `YYYYMMDD`, for the day-granularity upcoming-live check. */
private fun currentDate(): String = ExtractorUtils.epochSecondsToDate(nowSeconds())

/** The first item under `content[].lineups[].items[]` matching [predicate]. */
private fun findGemItem(showInfo: JsonObject, predicate: (JsonObject) -> Boolean): JsonObject? {
    for (content in showInfo.array("content").orEmpty()) {
        val contentObject = content as? JsonObject ?: continue
        for (lineup in contentObject.array("lineups").orEmpty()) {
            val items = (lineup as? JsonObject)?.array("items").orEmpty()
            for (item in items) {
                val itemObject = item as? JsonObject ?: continue
                if (predicate(itemObject)) return itemObject
            }
        }
    }
    return null
}

/** The first `content[].lineups[]` matching [predicate]. */
private fun findGemLineup(showInfo: JsonObject, predicate: (JsonObject) -> Boolean): JsonObject? {
    for (content in showInfo.array("content").orEmpty()) {
        val contentObject = content as? JsonObject ?: continue
        for (lineup in contentObject.array("lineups").orEmpty()) {
            val lineupObject = lineup as? JsonObject ?: continue
            if (predicate(lineupObject)) return lineupObject
        }
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
