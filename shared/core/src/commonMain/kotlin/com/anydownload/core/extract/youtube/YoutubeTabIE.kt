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
package com.anydownload.core.extract.youtube

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.platform.HttpMethods
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

    override fun matchId(url: String): String? {
        val groups = VALID_URL.find(url)?.groups ?: return null
        return listOf("playlist", "watchlist", "channel", "handle", "custom", "user")
            .firstNotNullOfOrNull { groups[it]?.value }
    }

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["playlist"]?.value ?: match.groups["watchlist"]?.value
        val channelId = match.groups["channel"]?.value
        val handle = match.groups["handle"]?.value
        val browseId: String
        val displayId: String
        when {
            playlistId != null -> {
                browseId = "VL$playlistId"
                displayId = playlistId
            }

            channelId != null -> {
                browseId = channelId
                displayId = channelId
            }

            handle != null -> {
                browseId = "@$handle"
                displayId = "@$handle"
            }

            else -> throw ExtractionError.UnsupportedUrl(
                "This YouTube channel URL needs the URL resolver, which is not translated yet.",
            )
        }
        val root = requestBrowse(browseId)
        val entries = playlistEntriesWithContinuations(root)
        if (entries.isEmpty()) {
            throw ExtractionError.Unavailable("This playlist or channel is private, unavailable, or empty.")
        }
        return InfoDict(
            id = displayId,
            title = playlistTitle(root),
            entries = entries,
            webpageUrl = url,
            extractor = "youtube",
            extractorKey = ieKey,
        )
    }

    private suspend fun requestBrowse(playlistId: String): JsonObject =
        requestBrowse(browseBody(browseId = playlistId))

    /** Follows at most [MAX_CONTINUATION_PAGES] continuations, stopping at the cap. */
    private suspend fun playlistEntriesWithContinuations(first: JsonObject): List<InfoEntry> {
        val entries = mutableListOf<InfoEntry>()
        val seen = mutableSetOf<String>()
        var root = first
        var pages = 0
        while (entries.size < MAX_PLAYLIST_ENTRIES && pages <= MAX_CONTINUATION_PAGES) {
            collectPlaylistEntries(root, entries, seen)
            if (entries.size >= MAX_PLAYLIST_ENTRIES) break
            val token = continuationToken(root) ?: break
            if (pages == MAX_CONTINUATION_PAGES) break
            root = requestBrowse(browseBody(continuation = token))
            pages++
        }
        return entries.take(MAX_PLAYLIST_ENTRIES)
    }

    /**
     * One innertube `browse` request with the upstream `web` client context,
     * mirroring the request shape `YoutubeSearch` uses. No visitor id, cookie,
     * or PO token is sent.
     */
    private suspend fun requestBrowse(body: JsonObject): JsonObject {
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

    private fun browseBody(browseId: String? = null, continuation: String? = null): JsonObject = buildJsonObject {
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
        browseId?.let { put("browseId", it) }
        continuation?.let { put("continuation", it) }
    }

    /** The first `continuationItemRenderer` token in document order. */
    private fun continuationToken(root: JsonObject): String? =
        collect(root, "continuationItemRenderer").firstNotNullOfOrNull { renderer ->
            renderer.path("continuationEndpoint", "continuationCommand", "token").stringOrNull()
        }

    /**
     * Flat id/title/url rows in document order, first occurrence per id. The
     * playlist, channel grid, and mix renderers the pin's tab endpoint returns
     * are all read; the id set dedupes nested renderers.
     */
    private fun collectPlaylistEntries(
        root: JsonObject,
        entries: MutableList<InfoEntry>,
        seen: MutableSet<String>,
    ) {
        for (key in listOf(
            "playlistVideoRenderer",
            "playlistPanelVideoRenderer",
            "videoRenderer",
            "gridVideoRenderer",
            "reelItemRenderer",
        )) {
            collect(root, key).forEach { renderer ->
                val videoId = renderer.stringOrNull("videoId")
                    ?: renderer.path("navigationEndpoint", "watchEndpoint", "videoId").stringOrNull()
                    ?: return@forEach
                if (!seen.add(videoId)) return@forEach
                val title = rendererTitle(renderer) ?: videoId
                entries += InfoEntry(
                    id = videoId,
                    title = title,
                    url = "https://www.youtube.com/watch?v=$videoId",
                )
            }
        }
    }

    /** `title.runs[].text`, `title.simpleText`, or `headline.simpleText`. */
    private fun rendererTitle(renderer: JsonObject): String? =
        renderer.path("title", "runs").asArray().texts().joinToString("").trim().takeIf { it.isNotEmpty() }
            ?: renderer.path("title", "simpleText").stringOrNull()
            ?: renderer.path("headline", "simpleText").stringOrNull()

    private fun playlistTitle(root: JsonObject): String? {
        collect(root, "playlistMetadataRenderer").firstOrNull()?.stringOrNull("title")?.let { return it }
        collect(root, "channelMetadataRenderer").firstOrNull()?.stringOrNull("title")?.let { return it }
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

        /** Bound on continuation pages so a malformed token cannot loop forever. */
        const val MAX_CONTINUATION_PAGES: Int = 10

        const val BROWSE_URL: String = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"

        /**
         * `/playlist?list=`, `/watch?...&list=` (including `RD...` mixes),
         * `/channel/<UC id>`, and `/@handle` with an optional tab. `/c/` and
         * `/user/` need the `navigation/resolve_url` call and fail typed.
         */
        val VALID_URL: Regex = Regex(
            "^https?://(?:www\\.|m\\.|music\\.)?youtube\\.com/" +
                "(?:" +
                "playlist\\?(?:[^#]*?&)?list=(?<playlist>[0-9A-Za-z_-]+)" +
                "|watch\\?(?:[^#]*?&)?list=(?<watchlist>[0-9A-Za-z_-]+)" +
                "|channel/(?<channel>UC[0-9A-Za-z_-]{22})" +
                "|@(?<handle>[^/?#]+)" +
                "|c/(?<custom>[^/?#]+)" +
                "|user/(?<user>[^/?#]+)" +
                ")" +
                "(?:/(?:videos|streams|shorts|playlists|live))?" +
                "(?:[?#&].*)?$",
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
