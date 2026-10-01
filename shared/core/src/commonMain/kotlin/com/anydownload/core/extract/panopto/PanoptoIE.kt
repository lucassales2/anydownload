/*
 * Panopto extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `panopto.py` from
 * `yt_dlp/extractor/panopto.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `panopto.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the DeliveryInfo stream map (HLS and progressive streams, podcast
 * first), the session metadata, chapters, the playlist API, and the session
 * list with its subfolders. HLS masters are recorded as `m3u8_native` and
 * parsed at download time. The `_mark_watched` analytics call, the MHTML
 * slides/storyboard formats, and the inline-SRT captions (the port's subtitle
 * model carries URLs only) are not translated; `cast`, `tags`, and
 * `average_rating` are not modeled on the port's InfoDict. No cookie, token,
 * or signed media URL is stored here.
 */
package com.anydownload.core.extract.panopto

import com.anydownload.core.extract.Chapter
import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Upstream `PanoptoBaseIE`: the JSON API helper. */
abstract class PanoptoBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_call_api`; `ErrorCode == 2` is the cookie login wall. */
    protected suspend fun callApi(
        baseUrl: String,
        path: String,
        videoId: String,
        query: Map<String, String> = emptyMap(),
        data: String? = null,
        fatal: Boolean = true,
    ): JsonObject? {
        val queryString = if (query.isEmpty()) {
            ""
        } else {
            "?" + query.entries.joinToString("&") { (key, value) -> "$key=" + encodeQuery(value) }
        }
        val json = try {
            http.downloadJson(
                baseUrl + path + queryString,
                method = if (data != null) {
                    com.anydownload.core.platform.HttpMethods.POST
                } else {
                    com.anydownload.core.platform.HttpMethods.GET
                },
                headers = mapOf("accept" to "application/json", "content-type" to "application/json"),
                body = data?.encodeToByteArray(),
            )
        } catch (error: ExtractionError) {
            if (fatal) throw error else return null
        }
        val response = json as? JsonObject ?: return null
        when (response.number("ErrorCode")?.toInt()) {
            2 -> throw ExtractionError.LoginRequired(
                "Panopto needs a sign-in cookie for this session.",
            )

            null -> Unit
            else -> {
                val message = "Panopto said: ${response.str("ErrorMessage") ?: "unknown error"}"
                if (fatal) throw ExtractionError.Unavailable(message)
            }
        }
        return response
    }

    /** Upstream `_parse_fragment`: percent-decoded JSON values. */
    protected fun parseFragment(url: String): Map<String, JsonElement> {
        val fragment = url.substringAfter('#', "")
        if (fragment.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, JsonElement>()
        for (pair in fragment.split('&')) {
            if (pair.isEmpty()) continue
            val key = percentDecode(pair.substringBefore('='))
            val value = percentDecode(pair.substringAfter('=', ""))
            ExtractorUtils.parseJson(value)?.let { result[key] = it }
        }
        return result
    }

    companion object {
        const val BASE_URL_RE: String = "(?<baseUrl>https?://[\\w.-]+\\.panopto\\.(?:com|eu)/Panopto)"

        internal fun encodeQuery(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val character = byte.toInt().toChar()
                if (character in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~") {
                    append(character)
                } else {
                    append('%')
                        .append("0123456789ABCDEF"[(byte.toInt() shr 4) and 0xF])
                        .append("0123456789ABCDEF"[byte.toInt() and 0xF])
                }
            }
        }
    }
}

/** Upstream `PanoptoIE`: the Viewer/Embed session pages. */
class PanoptoIE(
    http: ExtractorHttp,
) : PanoptoBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override fun suitable(url: String): Boolean =
        !PanoptoPlaylistIE.VALID_URL.containsMatchIn(url) && super.suitable(url)

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val baseUrl = match.groups["baseUrl"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val deliveryInfo = callApi(
            baseUrl,
            "/Pages/Viewer/DeliveryInfo.aspx",
            videoId,
            query = mapOf(
                "deliveryId" to videoId,
                "invocationId" to "",
                "isLiveNotes" to "false",
                "refreshAuthCookie" to "true",
                "isActiveBroadcast" to "false",
                "isEditing" to "false",
                "isKollectiveAgentInstalled" to "false",
                "isEmbed" to "false",
                "responseType" to "json",
            ),
        ) ?: throw ExtractionError.Unavailable("Panopto returned no delivery info.")
        val delivery = deliveryInfo.obj("Delivery")
            ?: throw ExtractionError.Malformed("Panopto returned no delivery object.")

        val formats = mutableListOf<MediaFormat>()
        formats += extractStreams(delivery.array("PodcastStreams"), "PODCAST", null)
        formats += extractStreams(delivery.array("Streams"), null, -10)

        val timestamps = delivery.array("Timestamps")
        val chapters = mutableListOf<Chapter>()
        for (element in timestamps.orEmpty()) {
            val timestamp = element as? JsonObject ?: continue
            val caption = timestamp.str("Caption") ?: continue
            val start = timestamp.number("Time")?.toLong() ?: continue
            val duration = timestamp.number("Duration")?.toLong() ?: continue
            chapters += Chapter(startTime = start.toDouble(), endTime = (start + duration).toDouble(), title = caption)
        }

        val sessionStartTime = delivery.number("SessionStartTime")?.toLong()
        return InfoDict(
            id = videoId,
            title = delivery.str("SessionName"),
            description = delivery.str("SessionAbstract"),
            duration = delivery.number("Duration"),
            uploadDate = sessionStartTime?.minus(11_640_000_000L)
                ?.let(ExtractorUtils::epochSecondsToDate),
            uploader = delivery.str("OwnerDisplayName"),
            channelId = delivery.str("SessionGroupPublicID"),
            channel = delivery.str("SessionGroupLongName") ?: delivery.str("SessionGroupShortName"),
            thumbnails = listOf(
                Thumbnail(
                    url = baseUrl + "/Services/FrameGrabber.svc/FrameRedirect?objectId=$videoId&mode=Delivery",
                ),
            ),
            formats = formats,
            chapters = chapters,
            webpageUrl = url,
            extractor = "panopto",
            extractorKey = IE_KEY,
        )
    }

    private fun extractStreams(
        streams: JsonArray?,
        formatNote: String?,
        preference: Int?,
    ): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        for (element in streams.orEmpty()) {
            val stream = element as? JsonObject ?: continue
            val urls = listOf("StreamHttpUrl", "StreamUrl")
                .mapNotNull { ExtractorUtils.urlOrNone(stream.str(it)) }
                .distinct()
            val mediaType = stream.str("ViewerMediaFileTypeName")
            for (streamUrl in urls) {
                if (mediaType == "hls") {
                    formats += MediaFormat(
                        formatId = "hls",
                        url = streamUrl,
                        ext = "mp4",
                        protocol = "m3u8_native",
                        formatNote = formatNote ?: stream.str("Tag"),
                        preference = preference,
                    )
                } else {
                    formats += MediaFormat(
                        url = streamUrl,
                        ext = mediaType,
                        formatNote = formatNote ?: stream.str("Tag"),
                        preference = preference,
                    )
                }
            }
        }
        return formats
    }

    companion object {
        const val IE_KEY: String = "Panopto"

        val VALID_URL: Regex = Regex(
            PanoptoBaseIE.BASE_URL_RE + "/Pages/(Viewer|Embed)\\.aspx.*(?:\\?|&)id=(?<id>[a-f0-9-]+)",
        )
    }
}

/** Upstream `PanoptoPlaylistIE`: the `pid=` playlist pages. */
class PanoptoPlaylistIE(
    http: ExtractorHttp,
) : PanoptoBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val baseUrl = match.groups["baseUrl"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistInfo = callApi(baseUrl, "/Api/Playlists/$playlistId", playlistId)
            ?: throw ExtractionError.Unavailable("Panopto returned no playlist info.")
        val sessionListId = playlistInfo.str("SessionListId")
            ?: throw ExtractionError.Malformed("Panopto returned no session list id.")
        val sessionList = callApi(
            baseUrl,
            "/Api/SessionLists/$sessionListId" +
                "?collections[0].maxCount=500&collections[0].name=items",
            playlistId,
        ) ?: throw ExtractionError.Unavailable("Panopto returned no session list.")

        val entries = mutableListOf<InfoEntry>()
        for (element in sessionList.array("Items").orEmpty()) {
            val item = element as? JsonObject ?: continue
            if (item.str("TypeName") != "Session") continue
            val viewerUri = item.str("ViewerUri") ?: continue
            entries += InfoEntry(
                id = item.str("Id"),
                title = item.str("Name"),
                url = urlJoin(baseUrl, viewerUri),
            )
        }
        return InfoDict(
            id = playlistId,
            title = playlistInfo.str("Name"),
            description = playlistInfo.str("Description"),
            entries = entries,
            webpageUrl = url,
            extractor = "panopto",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PanoptoPlaylist"

        val VALID_URL: Regex = Regex(
            PanoptoBaseIE.BASE_URL_RE + "/Pages/(Viewer|Embed)\\.aspx.*(?:\\?|&)pid=(?<id>[a-f0-9-]+)",
        )
    }
}

/** Upstream `PanoptoListIE`: the Sessions/List pages. */
class PanoptoListIE(
    http: ExtractorHttp,
) : PanoptoBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val baseUrl = match.groups["baseUrl"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val queryParams = parseFragment(url)
        var displayId = "panopto_list"
        val folderId = (queryParams["folderID"] as? JsonPrimitive)?.content
        if (queryParams["isSubscriptionsPage"] != null) {
            displayId = "subscriptions"
        } else if (queryParams["isSharedWithMe"] != null) {
            displayId = "sharedwithme"
        } else if (folderId != null) {
            displayId = folderId
        }
        (queryParams["query"] as? JsonPrimitive)?.content?.let { displayId += ": query \"$it\"" }

        var title = displayId
        if (folderId != null) {
            val folderInfo = callApi(
                baseUrl,
                "/Services/Data.svc/GetFolderInfo",
                folderId,
                data = buildJsonObject { put("folderID", folderId) }.toString(),
                fatal = false,
            )
            folderInfo?.str("Name")?.let { title = it }
        }

        val entries = mutableListOf<InfoEntry>()
        var page = 0
        var iterations = 0
        while (iterations < MAX_PAGES) {
            iterations++
            val params = buildJsonObject {
                put("sortColumn", 1)
                put("getFolderData", true)
                put("includePlaylists", true)
                for ((key, value) in queryParams) {
                    if (key == "folderID" || key == "query") {
                        put(key, value)
                    }
                }
                put("page", page)
                put("maxResults", PAGE_SIZE)
            }
            val response = callApi(
                baseUrl,
                "/Services/Data.svc/GetSessions",
                "$displayId page ${page + 1}",
                data = buildJsonObject { put("queryParameters", params) }.toString(),
                fatal = false,
            ) ?: break
            val results = response.array("Results").orEmpty().mapNotNull { it as? JsonObject }
            if (results.isEmpty() && response.array("Subfolders").orEmpty().isEmpty()) break
            for (result in results) {
                val itemId = result.str("DeliveryID")
                entries += InfoEntry(
                    id = itemId,
                    title = result.str("SessionName"),
                    url = result.str("ViewerUrl") ?: result.str("EmbedUrl")
                        ?: itemId?.let { "$baseUrl/Pages/Viewer.aspx?id=$it" },
                )
            }
            for (element in response.array("Subfolders").orEmpty()) {
                val folder = element as? JsonObject ?: continue
                val subfolderId = folder.str("ID") ?: continue
                entries += InfoEntry(
                    id = subfolderId,
                    title = folder.str("Name"),
                    url = "$baseUrl/Pages/Sessions/List.aspx#folderID=\"$subfolderId\"",
                )
            }
            page++
        }
        return InfoDict(
            id = displayId,
            title = title,
            entries = entries,
            webpageUrl = url,
            extractor = "panopto",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PanoptoList"
        private const val PAGE_SIZE = 250
        private const val MAX_PAGES = 100

        val VALID_URL: Regex = Regex(PanoptoBaseIE.BASE_URL_RE + "/Pages/Sessions/List\\.aspx")
    }
}

// ------------------------------------------------------------------ helpers

/** `urllib.parse.unquote` for the fragment values. */
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

private fun urlJoin(base: String, value: String): String = when {
    value.startsWith("http://") || value.startsWith("https://") -> value
    value.startsWith("/") -> base.substringBefore("://") + "://" +
        base.substringAfter("://").substringBefore('/') + value

    else -> base.trimEnd('/') + "/" + value
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
