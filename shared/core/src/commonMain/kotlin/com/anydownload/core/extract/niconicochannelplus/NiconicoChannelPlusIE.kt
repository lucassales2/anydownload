/*
 * Niconico Channel Plus extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `niconicochannelplus.py`
 * from `yt_dlp/extractor/niconicochannelplus.py` at upstream tag
 * `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `niconicochannelplus.py` is not
 * vendored; see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `nfc-api.nicochannel.jp/fc` channel/video/session APIs
 * (the session id is anonymous with the `fc_use_device: null` header), the
 * m3u8 formats, and the paged channel video/live lists (five pages eagerly).
 * The comment walk and the per-entry comment counters are not translated.
 * No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.niconicochannelplus

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `NiconicoChannelPlusIE`: a video or live page. */
class NiconicoChannelPlusIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val contentCode = match.groups["code"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val channelId = match.groups["channel"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val fanclubSiteId = findFanclubSiteId(http, channelId)
        val response = callApi(
            http,
            "video_pages/$contentCode",
            contentCode,
            headers = mapOf("fc_use_device" to "null"),
        ) ?: throw ExtractionError.Malformed("The video page API returned no data.")
        val data = response.obj("data")?.obj("video_page")
            ?: throw ExtractionError.Malformed("The video page API had no video_page.")
        val (liveStatus, sessionId) = liveStatusAndSessionId(http, contentCode, data)
        val formats = mutableListOf<MediaFormat>()
        if (liveStatus == "is_upcoming") {
            val scheduled = data.str("live_scheduled_start_at")
            throw ExtractionError.NotYetAvailable(
                if (scheduled != null) {
                    "This live event will begin at $scheduled UTC."
                } else {
                    "This event has not started yet."
                },
            )
        }
        val authenticatedUrl = data.obj("video_stream")?.str("authenticated_url")
            ?: throw ExtractionError.NoFormats("The video page had no stream URL.")
        formats += MediaFormat(
            formatId = "hls",
            url = authenticatedUrl.replace("{session_id}", sessionId),
            ext = "mp4",
            protocol = "m3u8_native",
        )
        val channelInfo = channelBaseInfo(http, fanclubSiteId)
        return InfoDict(
            id = contentCode,
            title = data.str("title"),
            description = data.str("description"),
            duration = data.obj("active_video_filename")?.number("length")?.toLong()?.toDouble(),
            uploadDate = ExtractorUtils.unifiedStrdate(data.str("released_at")),
            channel = channelInfo.str("fanclub_site_name"),
            channelId = channelId,
            viewCount = data.obj("video_aggregate_info")?.number("total_views")?.toLong(),
            isLive = liveStatus == "is_live",
            thumbnails = listOfNotNull(data.str("thumbnail_url")?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "NiconicoChannelPlus",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NiconicoChannelPlus"

        val VALID_URL: Regex = Regex(
            "https?://nicochannel\\.jp/(?<channel>[\\w.-]+)/(?:video|live)/(?<code>sm\\w+)",
        )
    }
}

/** Upstream `NiconicoChannelPlusChannelVideosIE`: the channel video list. */
class NiconicoChannelPlusChannelVideosIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val channelId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val fanclubSiteId = findFanclubSiteId(http, channelId)
        val channelName = channelBaseInfo(http, fanclubSiteId).str("fanclub_site_name")
        val entries = channelEntries(
            http,
            "fanclub_sites/$fanclubSiteId/video_pages",
            channelId,
            "",
        )
        return InfoDict(
            id = "$channelId-videos",
            title = "$channelName-videos",
            entries = entries,
            webpageUrl = url,
            extractor = "NiconicoChannelPlus:channel:videos",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NiconicoChannelPlusChannelVideos"

        val VALID_URL: Regex = Regex("https?://nicochannel\\.jp/(?<id>[a-z\\d._-]+)/videos(?:\\?.*)?")
    }
}

/** Upstream `NiconicoChannelPlusChannelLivesIE`: the channel live list. */
class NiconicoChannelPlusChannelLivesIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val channelId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val fanclubSiteId = findFanclubSiteId(http, channelId)
        val channelName = channelBaseInfo(http, fanclubSiteId).str("fanclub_site_name")
        val entries = channelEntries(
            http,
            "fanclub_sites/$fanclubSiteId/live_pages",
            channelId,
            "live_type=4&",
        )
        return InfoDict(
            id = "$channelId-lives",
            title = "$channelName-lives",
            entries = entries,
            webpageUrl = url,
            extractor = "NiconicoChannelPlus:channel:lives",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NiconicoChannelPlusChannelLives"

        val VALID_URL: Regex = Regex("https?://nicochannel\\.jp/(?<id>[a-z\\d._-]+)/lives")
    }
}

// ------------------------------------------------------------------ helpers

private const val PAGE_SIZE = 12
private const val MAX_PAGES = 5

private suspend fun callApi(
    http: ExtractorHttp,
    path: String,
    itemId: String,
    query: String = "",
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
): JsonObject? {
    val url = "https://nfc-api.nicochannel.jp/fc/$path" + if (query.isEmpty()) "" else "?$query"
    return try {
        if (body != null) {
            http.downloadJson(
                url,
                method = "POST",
                headers = headers + ("Content-Type" to "application/json"),
                body = body.encodeToByteArray(),
            ) as? JsonObject
        } else {
            http.downloadJson(url, headers = headers) as? JsonObject
        }
    } catch (error: ExtractionError) {
        null
    } catch (error: IllegalStateException) {
        null
    }
}

private suspend fun findFanclubSiteId(http: ExtractorHttp, channelName: String): String {
    val response = callApi(http, "content_providers/channels", "channels/$channelName")
        ?: throw ExtractionError.Malformed("The channel list API returned no data.")
    val providers = response.obj("data")?.array("content_providers").orEmpty()
    for (element in providers) {
        val provider = element as? JsonObject ?: continue
        if (provider.str("domain") == "https://nicochannel.jp/$channelName") {
            return provider.primitiveText("id")
                ?: throw ExtractionError.Malformed("The channel had no id.")
        }
    }
    throw ExtractionError.Unavailable("Channel $channelName does not exist.")
}

private suspend fun channelBaseInfo(http: ExtractorHttp, fanclubSiteId: String): JsonObject =
    callApi(http, "fanclub_sites/$fanclubSiteId/page_base_info", "fanclub_sites/$fanclubSiteId")
        ?.obj("data")?.obj("fanclub_site") ?: JsonObject(emptyMap())

private suspend fun liveStatusAndSessionId(
    http: ExtractorHttp,
    contentCode: String,
    data: JsonObject,
): Pair<String, String> {
    val videoType = data.str("type")
    val liveFinishedAt = data.str("live_finished_at")
    var payload = "{}"
    val liveStatus = when (videoType) {
        "vod" -> if (liveFinishedAt != null) "was_live" else "not_live"
        "live" -> when {
            data.str("live_started_at") == null -> return Pair("is_upcoming", "")
            liveFinishedAt == null -> "is_live"
            else -> {
                payload = """{"broadcast_type":"dvr"}"""
                val video = data.obj("video")
                val allowDvr = video?.bool("allow_dvr_flg") == true
                val convertToVod = video?.bool("convert_to_vod_flg") == true
                if (!allowDvr || !convertToVod) {
                    throw ExtractionError.Unavailable("Live was ended, there is no video for download.")
                }
                "was_live"
            }
        }

        else -> throw ExtractionError.Unavailable("Unknown type: ${videoType ?: "null"}.")
    }
    val sessionResponse = callApi(
        http,
        "video_pages/$contentCode/session_ids",
        "$contentCode/session",
        headers = mapOf(
            "Content-Type" to "application/json",
            "fc_use_device" to "null",
            "origin" to "https://nicochannel.jp",
        ),
        body = payload,
    ) ?: throw ExtractionError.Malformed("The session API returned no data.")
    val sessionId = sessionResponse.obj("data")?.primitiveText("session_id")
        ?: throw ExtractionError.Malformed("The session API returned no session id.")
    return Pair(liveStatus, sessionId)
}

private suspend fun channelEntries(
    http: ExtractorHttp,
    path: String,
    channelId: String,
    extraQuery: String,
): List<InfoEntry> {
    val entries = mutableListOf<InfoEntry>()
    var page = 1
    while (page <= MAX_PAGES) {
        val response = callApi(
            http,
            path,
            "$channelId/list",
            query = "${extraQuery}page=$page&per_page=$PAGE_SIZE",
            headers = mapOf("fc_use_device" to "null"),
        ) ?: break
        val list = response.obj("data")?.obj("video_pages")?.array("list").orEmpty()
        if (list.isEmpty()) break
        for (element in list) {
            val item = element as? JsonObject ?: continue
            val contentCode = item.str("content_code") ?: continue
            entries += InfoEntry(
                id = contentCode,
                title = item.str("title"),
                url = "https://nicochannel.jp/$channelId/video/$contentCode",
            )
        }
        if (list.size < PAGE_SIZE) break
        page++
    }
    return entries
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.let {
        when (it.content) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
