/*
 * YouTube search — AnyDownload
 *
 * Kotlin translation of a subset of yt-dlp's `YoutubeSearchIE`,
 * `YoutubeSearchURLIE`, and `YoutubeMusicSearchURLIE`
 * (`yt_dlp/extractor/youtube/_search.py`) plus the innertube search request
 * and result parsing from `yt_dlp/extractor/youtube/_tab.py`
 * (`_search_results`/`_extract_search_results`), read at upstream tag
 * `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf) on
 * 2026-09-25.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. Only the fields D6's matcher scores
 * are read: title, channel/artists, album, duration, views, and the verified
 * song/video flag. No continuations, no playlist/channel/album result
 * sections, no other clients, and no download. `_search.py` and `_tab.py`
 * are not vendored; see shared/core/NOTICE.md and port/manifest.json.
 */
package com.anydownlod.core.extract.youtube

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.platform.HttpMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One search hit. [verified] is true only for a YouTube Music audio track
 * (`MUSIC_VIDEO_TYPE_ATV`), mirroring spotDL's `resultType == "song"`;
 * regular YouTube results are never verified.
 */
data class YoutubeSearchResult(
    val videoId: String,
    val url: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val channel: String? = null,
    val durationSeconds: Double? = null,
    val album: String? = null,
    val viewCount: Long? = null,
    val verified: Boolean = false,
)

/** The YouTube Music search sections the matcher asks for. */
enum class MusicSearchFilter(val params: String) {
    SONGS("EgWKAQIIAWoKEAoQAxAEEAkQBQ=="),
    VIDEOS("EgWKAQIQAWoKEAoQAxAEEAkQBQ=="),
}

/**
 * Innertube search over the existing [ExtractorHttp] seam. It adds no HTTP
 * stack: requests, headers, and error mapping are the same ones `YoutubeIE`
 * uses. A blank query returns no results without a request; a failed request
 * is a typed [ExtractionError].
 */
class YoutubeSearch(private val http: ExtractorHttp) {

    /** YouTube Music search; null [filter] sends no section params (ISRC use). */
    suspend fun searchMusic(
        query: String,
        filter: MusicSearchFilter? = MusicSearchFilter.SONGS,
    ): List<YoutubeSearchResult> {
        if (query.isBlank()) return emptyList()
        val root = request(
            url = MUSIC_SEARCH_URL,
            headerClientName = MUSIC_CLIENT_NAME,
            clientVersion = MUSIC_CLIENT_VERSION,
            origin = "https://music.youtube.com",
            body = searchBody(query, MUSIC_CLIENT_CONTEXT_NAME, MUSIC_CLIENT_VERSION, filter?.params),
        )
        return musicResults(root, filter)
    }

    /** Regular YouTube search, videos only, like upstream's `ytsearch`. */
    suspend fun searchYoutube(query: String): List<YoutubeSearchResult> {
        if (query.isBlank()) return emptyList()
        val root = request(
            url = YOUTUBE_SEARCH_URL,
            headerClientName = YoutubeIE.WEB_CLIENT_NAME,
            clientVersion = YoutubeIE.WEB_CLIENT_VERSION,
            origin = "https://www.youtube.com",
            body = searchBody(query, YOUTUBE_CLIENT_CONTEXT_NAME, YoutubeIE.WEB_CLIENT_VERSION, YOUTUBE_VIDEOS_PARAMS),
        )
        return youtubeResults(root)
    }

    // --------------------------------------------------------------- requests

    private suspend fun request(
        url: String,
        headerClientName: String,
        clientVersion: String,
        origin: String,
        body: ByteArray,
    ): JsonObject {
        val response = http.downloadJson(
            url = url,
            method = HttpMethods.POST,
            headers = mapOf(
                "content-type" to "application/json",
                "x-youtube-client-name" to headerClientName,
                "x-youtube-client-version" to clientVersion,
                "origin" to origin,
                "user-agent" to YoutubeIE.USER_AGENT,
            ),
            body = body,
        )
        return response as? JsonObject
            ?: throw ExtractionError.Malformed("The search response was not an object.")
    }

    private fun searchBody(
        query: String,
        contextClientName: String,
        clientVersion: String,
        params: String?,
    ): ByteArray = buildJsonObject {
        put(
            "context",
            buildJsonObject {
                put(
                    "client",
                    buildJsonObject {
                        put("clientName", contextClientName)
                        put("clientVersion", clientVersion)
                        put("hl", "en")
                        put("timeZone", "UTC")
                        put("utcOffsetMinutes", 0)
                    },
                )
            },
        )
        put("query", query)
        params?.let { put("params", it) }
    }.toString().encodeToByteArray()

    // ---------------------------------------------------------- music parsing

    private fun musicResults(root: JsonObject, filter: MusicSearchFilter?): List<YoutubeSearchResult> =
        collect(root, "musicResponsiveListItemRenderer").mapNotNull { renderer ->
            musicResult(renderer, filter)
        }

    private fun musicResult(renderer: JsonObject, filter: MusicSearchFilter?): YoutubeSearchResult? {
        val columns = renderer.array("flexColumns")?.mapNotNull { it as? JsonObject } ?: emptyList()
        val titleRuns = columns.getOrNull(0)
            ?.path("musicResponsiveListItemFlexColumnRenderer", "text", "runs")
            ?.asArray()
            ?: JsonArray(emptyList())
        val title = titleRuns.texts().joinToString("").trim()
        if (title.isEmpty()) return null
        val videoId = renderer.path("playlistItemData", "videoId").stringOrNull()
            ?: renderer.path(
                "overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
                "playNavigationEndpoint", "watchEndpoint", "videoId",
            ).stringOrNull()
            ?: titleRuns.firstWatchVideoId()
            ?: return null

        val subtitleRuns = columns.getOrNull(1)
            ?.path("musicResponsiveListItemFlexColumnRenderer", "text", "runs")
            ?.asArray()
            ?.mapNotNull { it as? JsonObject }
            ?: emptyList()
        val artists = subtitleRuns.filter { it.musicPageType() == "MUSIC_PAGE_TYPE_ARTIST" }
            .mapNotNull { it.stringOrNull("text") }
        val album = subtitleRuns.firstOrNull { it.musicPageType() == "MUSIC_PAGE_TYPE_ALBUM" }
            ?.stringOrNull("text")
        val channel = subtitleRuns.firstOrNull { run ->
            val pageType = run.musicPageType()
            run.path("navigationEndpoint", "browseEndpoint", "browseId").stringOrNull() != null &&
                pageType != "MUSIC_PAGE_TYPE_ARTIST" &&
                pageType != "MUSIC_PAGE_TYPE_ALBUM"
        }?.stringOrNull("text")
        val duration = subtitleRuns.mapNotNull { parseDurationSeconds(it.stringOrNull("text")) }.lastOrNull()
        val views = subtitleRuns.mapNotNull { parseViewCount(it.stringOrNull("text")) }.firstOrNull()

        val videoType = renderer.musicVideoType() ?: titleRuns.firstMusicVideoType()
        val verified = when {
            videoType != null -> videoType == MUSIC_VIDEO_TYPE_ATV
            filter == MusicSearchFilter.SONGS -> true
            else -> false
        }
        val url = if (verified) {
            "https://music.youtube.com/watch?v=$videoId"
        } else {
            "https://www.youtube.com/watch?v=$videoId"
        }
        return YoutubeSearchResult(
            videoId = videoId,
            url = url,
            title = title,
            artists = artists,
            channel = channel,
            durationSeconds = duration,
            album = album,
            viewCount = views,
            verified = verified,
        )
    }

    // -------------------------------------------------------- youtube parsing

    private fun youtubeResults(root: JsonObject): List<YoutubeSearchResult> =
        collect(root, "videoRenderer").mapNotNull { renderer ->
            val videoId = renderer.stringOrNull("videoId") ?: return@mapNotNull null
            val title = renderer.path("title", "runs").asArray().texts().joinToString("").trim()
            if (title.isEmpty()) return@mapNotNull null
            val channel = renderer.path("ownerText", "runs").asArray().texts().firstOrNull()
                ?: renderer.path("longBylineText", "runs").asArray().texts().firstOrNull()
            YoutubeSearchResult(
                videoId = videoId,
                url = "https://www.youtube.com/watch?v=$videoId",
                title = title,
                channel = channel,
                durationSeconds = parseDurationSeconds(renderer.path("lengthText", "simpleText").stringOrNull()),
                viewCount = parseViewCount(renderer.path("viewCountText", "simpleText").stringOrNull()),
                verified = false,
            )
        }

    /** Depth-first document-order collection of every object under [key]. */
    private fun collect(root: JsonElement, key: String): List<JsonObject> {
        val found = mutableListOf<JsonObject>()
        fun walk(element: JsonElement?) {
            when (element) {
                is JsonObject -> {
                    (element[key] as? JsonObject)?.let { found += it }
                    element.values.forEach(::walk)
                }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return found
    }

    companion object {
        const val MUSIC_SEARCH_URL: String =
            "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
        const val YOUTUBE_SEARCH_URL: String =
            "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"

        /** Upstream `_base.py` `web_music` client values, read at the pin. */
        const val MUSIC_CLIENT_NAME: String = "67"
        const val MUSIC_CLIENT_VERSION: String = "1.20260707.12.00"
        const val MUSIC_CLIENT_CONTEXT_NAME: String = "WEB_REMIX"

        /** Upstream `_base.py` `web` context name; the header name is 1. */
        const val YOUTUBE_CLIENT_CONTEXT_NAME: String = "WEB"

        /** Upstream `YoutubeSearchIE._SEARCH_PARAMS` (videos only). */
        const val YOUTUBE_VIDEOS_PARAMS: String = "EgIQAfABAQ=="

        private const val MUSIC_VIDEO_TYPE_ATV = "MUSIC_VIDEO_TYPE_ATV"
    }
}

// ------------------------------------------------------------- parse helpers

/** `m:ss` or `h:mm:ss` to seconds; anything else is null. */
internal fun parseDurationSeconds(text: String?): Double? {
    val trimmed = text?.trim() ?: return null
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(':')
    if (parts.size !in 2..3) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    if (numbers.any { it < 0 }) return null
    return when (numbers.size) {
        2 -> numbers[0] * 60.0 + numbers[1]
        else -> numbers[0] * 3600.0 + numbers[1] * 60.0 + numbers[2]
    }
}

/** `1,819,912,851 views`, `77K views`, or `1.8B plays` to a count. */
internal fun parseViewCount(text: String?): Long? {
    val trimmed = text?.trim() ?: return null
    val match = Regex("""([\d.,]+)\s*([KMB]?)\s*(?:views?|plays?)""", RegexOption.IGNORE_CASE)
        .find(trimmed) ?: return null
    val raw = match.groupValues[1].replace(",", "")
    val value = raw.toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2].uppercase()) {
        "K" -> 1_000.0
        "M" -> 1_000_000.0
        "B" -> 1_000_000_000.0
        else -> 1.0
    }
    return (value * multiplier).toLong()
}

private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

private fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.stringOrNull(key: String): String? = this[key].stringOrNull()

private fun JsonObject.path(vararg keys: String): JsonElement? {
    var current: JsonElement = this
    for (key in keys) {
        current = (current as? JsonObject)?.get(key) ?: return null
    }
    return current
}

private fun JsonArray?.texts(): List<String> =
    this?.mapNotNull { (it as? JsonObject)?.stringOrNull("text") } ?: emptyList()

private fun JsonArray.firstWatchVideoId(): String? =
    firstNotNullOfOrNull { run ->
        (run as? JsonObject)
            ?.path("navigationEndpoint", "watchEndpoint", "videoId")
            .stringOrNull()
    }

private fun JsonArray.firstMusicVideoType(): String? =
    firstNotNullOfOrNull { run ->
        (run as? JsonObject)
            ?.path(
                "navigationEndpoint", "watchEndpoint", "watchEndpointMusicSupportedConfigs",
                "watchEndpointMusicConfig", "musicVideoType",
            )
            .stringOrNull()
    }

private fun JsonObject.musicPageType(): String? =
    path(
        "navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs",
        "browseEndpointContextMusicConfig", "pageType",
    ).stringOrNull()

private fun JsonObject.musicVideoType(): String? =
    path(
        "overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
        "playNavigationEndpoint", "watchEndpoint", "watchEndpointMusicSupportedConfigs",
        "watchEndpointMusicConfig", "musicVideoType",
    ).stringOrNull()
