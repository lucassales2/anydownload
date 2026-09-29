/*
 * YouTube playlist tab — AnyDownload
 *
 * Kotlin translation of a subset of yt-dlp's `YoutubeTabIE` playlist handling
 * (`yt_dlp/extractor/youtube/_tab.py`) at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-29.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. Only `/playlist?list=` is handled: one
 * innertube `browse` request, flat `playlistVideoRenderer` id/title pairs in
 * document order, and the first page only. Channels, mixes (`RD...`), the
 * watch-page playlist parameter, cookies, continuations, and live are not
 * translated. `_tab.py` is not vendored; see shared/core/NOTICE.md and
 * port/manifest.json.
 */
package com.anydownlod.core.extract.youtube

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoEntry
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.platform.HttpMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * YouTube playlist URL subset: `youtube.com/playlist?list=` and
 * `music.youtube.com/playlist?list=` (with `www`/`m` forms). The result is an
 * [InfoDict] with flat [InfoEntry] rows that the engine's T-107 expander turns
 * into child jobs. Child URLs are watch URLs, so `YoutubeIE` downloads them
 * through the existing single-video path.
 *
 * A private, deleted, or empty playlist fails typed with no entries. Mixes
 * (`list=RD...`) and watch URLs with a `list` parameter do not match.
 */
class YoutubeTabIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "YouTube playlist"

    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val root = requestBrowse(playlistId)
        val entries = playlistEntries(root).take(MAX_PLAYLIST_ENTRIES)
        if (entries.isEmpty()) {
            throw ExtractionError.Unavailable("This playlist is private, unavailable, or empty.")
        }
        return InfoDict(
            id = playlistId,
            title = playlistTitle(root),
            entries = entries,
            webpageUrl = "https://www.youtube.com/playlist?list=$playlistId",
            extractor = "youtube",
            extractorKey = ieKey,
        )
    }

    /**
     * One innertube `browse` request with the upstream `web` client context,
     * mirroring the request shape `YoutubeSearch` uses. No visitor id, cookie,
     * or PO token is sent.
     */
    private suspend fun requestBrowse(playlistId: String): JsonObject {
        val body = buildJsonObject {
            put(
                "context",
                buildJsonObject {
                    put(
                        "client",
                        buildJsonObject {
                            put("clientName", YoutubeSearch.YOUTUBE_CLIENT_CONTEXT_NAME)
                            put("clientVersion", YoutubeIE.WEB_CLIENT_VERSION)
                            put("hl", "en")
                            put("timeZone", "UTC")
                            put("utcOffsetMinutes", 0)
                        },
                    )
                },
            )
            put("browseId", "VL$playlistId")
        }
        val response = http.downloadJson(
            url = BROWSE_URL,
            method = HttpMethods.POST,
            headers = mapOf(
                "content-type" to "application/json",
                "x-youtube-client-name" to YoutubeIE.WEB_CLIENT_NAME,
                "x-youtube-client-version" to YoutubeIE.WEB_CLIENT_VERSION,
                "origin" to "https://www.youtube.com",
                "user-agent" to YoutubeIE.USER_AGENT,
            ),
            body = body.toString().encodeToByteArray(),
        )
        return response as? JsonObject
            ?: throw ExtractionError.Malformed("The playlist response was not an object.")
    }

    /** Flat id/title/url rows in document order, first occurrence per id. */
    private fun playlistEntries(root: JsonObject): List<InfoEntry> {
        val entries = mutableListOf<InfoEntry>()
        val seen = mutableSetOf<String>()
        collect(root, "playlistVideoRenderer").forEach { renderer ->
            val videoId = renderer.stringOrNull("videoId")
                ?: renderer.path("navigationEndpoint", "watchEndpoint", "videoId").stringOrNull()
                ?: return@forEach
            if (!seen.add(videoId)) return@forEach
            val title = renderer.path("title", "runs").asArray().texts().joinToString("").trim()
                .ifEmpty { renderer.path("title", "simpleText").stringOrNull() ?: videoId }
            entries += InfoEntry(
                id = videoId,
                title = title,
                url = "https://www.youtube.com/watch?v=$videoId",
            )
        }
        return entries
    }

    private fun playlistTitle(root: JsonObject): String? {
        collect(root, "playlistMetadataRenderer").firstOrNull()?.stringOrNull("title")?.let { return it }
        val header = collect(root, "playlistHeaderRenderer").firstOrNull() ?: return null
        return header.path("title", "simpleText").stringOrNull()
            ?: header.path("title", "runs").asArray().texts().joinToString("").trim().takeIf { it.isNotEmpty() }
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
        const val IE_KEY: String = "YoutubeTab"

        /** Same hard cap as the engine expander; never read past 50 entries. */
        const val MAX_PLAYLIST_ENTRIES: Int = 50

        const val BROWSE_URL: String = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"

        /**
         * `/playlist?list=` only, on the plain, `www`, `m`, or `music` host.
         * A watch URL with `list=` does not match, and mixes (`RD...`) are
         * refused so they stay unhandled.
         */
        val VALID_URL: Regex = Regex(
            "^https?://(?:www\\.|m\\.|music\\.)?youtube\\.com/playlist" +
                "\\?(?:[^#]*?&)?list=(?<id>(?!RD)[0-9A-Za-z_-]+)(?:[&#].*)?$",
        )
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

private fun JsonArray?.texts(): List<String> =
    this?.mapNotNull { (it as? JsonObject)?.stringOrNull("text") } ?: emptyList()

private fun JsonObject.path(vararg keys: String): JsonElement? {
    var current: JsonElement? = this
    for (key in keys) {
        current = (current as? JsonObject)?.get(key) ?: return null
    }
    return current
}
