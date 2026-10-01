/*
 * SoopLive (AfreecaTV) extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `afreecatv.py` from
 * `yt_dlp/extractor/afreecatv.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `afreecatv.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public VOD view API (single and multi-part), the catch-story
 * API, the public live API with the CDN stream assign, and the station VOD
 * pages (five pages eagerly). Subscriber-only/private/adult-without-login
 * content fails typed with a login requirement; the CloudFront cookie
 * refresh, the video-password option, and the CDN extractor-arg are not
 * translated. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.afreecatv

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `AfreecaTVIE`: the VOD view API. */
class AfreecaTVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = http.downloadJson(
            "https://api.m.sooplive.com/station/video/a/view",
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "Referer" to url,
            ),
            body = "nTitleNo=$videoId&nApiLevel=10".encodeToByteArray(),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The VOD API was not an object.")
        val data = response.obj("data") ?: throw ExtractionError.Malformed("The VOD API had no data.")
        when (data.number("code")?.toInt()) {
            -6221 -> throw ExtractionError.Unavailable("The VOD does not exist.")
            -6205 -> throw ExtractionError.Unavailable("This VOD is private.")
        }
        val title = data.str("title")
        val uploader = data.str("writer_nick")
        val uploaderId = data.str("bj_id")
        val duration = data.number("total_file_duration")?.div(1000)
        val thumbnails = listOfNotNull(data.str("thumb")?.let { Thumbnail(url = it) })
        val files = data.array("files").orEmpty().mapNotNull { it as? JsonObject }
            .filter { it.str("file") != null }
        val media = files.mapIndexed { index, file ->
            val fileUrl = file.str("file")!!
            val formats = if (ExtractorUtils.determineExt(fileUrl) == "m3u8") {
                listOf(MediaFormat(formatId = "hls", url = fileUrl, ext = "mp4", protocol = "m3u8_native"))
            } else {
                listOf(MediaFormat(formatId = "http", url = fileUrl, ext = ExtractorUtils.determineExt(fileUrl)))
            }
            InfoMedia(
                mediaId = file.str("file_info_key") ?: "${videoId}_${index + 1}",
                title = "${title ?: "Untitled"} (part ${index + 1})",
                duration = file.number("duration")?.div(1000),
                thumbnails = thumbnails,
                formats = formats,
            )
        }
        if (media.isEmpty()) {
            if (data.str("sub_upload_type") != null) {
                throw ExtractionError.LoginRequired("This VOD is for subscribers only.")
            }
            if (data.str("adult_status") == "notLogin") {
                throw ExtractionError.LoginRequired("Only users older than 19 are able to watch this video.")
            }
            throw ExtractionError.NoFormats("The VOD API returned no playable file.")
        }
        if (media.size == 1) {
            return InfoDict(
                id = media.single().mediaId,
                title = title,
                duration = duration,
                uploader = uploader,
                thumbnails = thumbnails,
                formats = media.single().formats,
                webpageUrl = url,
                extractor = "soop",
                extractorKey = IE_KEY,
            )
        }
        return InfoDict(
            id = videoId,
            title = title,
            duration = duration,
            uploader = uploader,
            channelId = uploaderId,
            thumbnails = thumbnails,
            media = media,
            webpageUrl = url,
            extractor = "soop",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "AfreecaTV"

        val VALID_URL: Regex = Regex(
            "https?://vod\\.sooplive\\.com/(?:PLAYER/STATION|player)/(?<id>\\d+)/?(?:$|[?#&])",
        )
    }
}

/** Upstream `AfreecaTVCatchStoryIE`: the catch-story API. */
class AfreecaTVCatchStoryIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = http.downloadJson(
            "https://api.m.sooplive.com/catchstory/a/view?aStoryListIdx=&nStoryIdx=$videoId",
            headers = mapOf("Referer" to url),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The catch-story API was not an object.")
        val media = response.array("data").orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.str("story_type") == "catch" }
            .flatMap { story ->
                story.array("catch_list").orEmpty().mapNotNull { element ->
                    val catch = element as? JsonObject ?: return@mapNotNull null
                    val file = catch.array("files")?.firstOrNull() as? JsonObject ?: return@mapNotNull null
                    val fileUrl = file.str("file") ?: return@mapNotNull null
                    InfoMedia(
                        mediaId = file.str("file_info_key") ?: videoId,
                        title = catch.str("title"),
                        duration = file.number("duration")?.div(1000),
                        thumbnails = listOfNotNull(catch.str("thumb")?.let { Thumbnail(url = it) }),
                        formats = listOf(MediaFormat(url = fileUrl, ext = ExtractorUtils.determineExt(fileUrl))),
                    )
                }
            }
        if (media.isEmpty()) {
            throw ExtractionError.NoFormats("The catch-story API returned no playable file.")
        }
        return InfoDict(
            id = videoId,
            media = media,
            webpageUrl = url,
            extractor = "soop:catchstory",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "AfreecaTVCatchStory"

        val VALID_URL: Regex = Regex("https?://vod\\.sooplive\\.com/player/(?<id>\\d+)/catchstory")
    }
}

/** Upstream `AfreecaTVLiveIE`: the public live streams. */
class AfreecaTVLiveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        var broadcasterId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        var broadcastNo = match.groups["bno"]?.value
        val channelInfo = liveApi(http, broadcasterId)
        broadcasterId = channelInfo.str("BJID") ?: broadcasterId
        broadcastNo = channelInfo.str("BNO") ?: broadcastNo
        if (broadcastNo == null) {
            when (channelInfo.number("RESULT")?.toInt()) {
                0 -> throw ExtractionError.Unavailable("This livestream has ended.")
                -6 -> throw ExtractionError.LoginRequired("This channel is streaming for subscribers only.")
            }
            throw ExtractionError.Malformed("Unable to extract the broadcast number.")
        }
        if (channelInfo.str("BPWD") == "Y") {
            throw ExtractionError.LoginRequired("This livestream is protected by a password.")
        }
        val tokenInfo = liveApi(
            http,
            broadcasterId,
            data = "bno=$broadcastNo&stream_type=common&type=aid&quality=master",
            displayId = broadcastNo,
        )
        val aid = tokenInfo.str("AID")
        if (aid == null) {
            when (tokenInfo.number("RESULT")?.toInt()) {
                0 -> throw ExtractionError.Unavailable("This livestream has ended.")
                -6 -> throw ExtractionError.LoginRequired("This livestream is for subscribers only.")
            }
            throw ExtractionError.Malformed("Unable to extract the access token.")
        }
        val streamBase = channelInfo.str("RMD") ?: "https://livestream-manager.sooplive.com"
        val cdnIds = buildList {
            channelInfo.array("CDN").orEmpty().forEach { (it as? JsonPrimitive)?.content?.let(::add) }
            add("gcp_cdn")
            add("gs_cdn_mobile_web")
            add("gs_cdn_pc_web")
        }.distinct().filterNot { it in BAD_CDNS }
        var m3u8Url: String? = null
        for (cdn in cdnIds) {
            val assign = try {
                http.downloadJson(
                    "$streamBase/broad_stream_assign.html?return_type=$cdn&broad_key=$broadcastNo-common-master-hls",
                ) as? JsonObject
            } catch (error: ExtractionError) {
                null
            } ?: continue
            m3u8Url = assign.str("view_url") ?: continue
            break
        }
        if (m3u8Url == null) {
            throw ExtractionError.NoFormats("The live API returned no stream URL.")
        }
        return InfoDict(
            id = broadcastNo,
            title = channelInfo.str("TITLE"),
            uploader = channelInfo.str("BJNICK"),
            channelId = broadcasterId,
            isLive = true,
            formats = listOf(
                MediaFormat(formatId = "hls", url = "$m3u8Url?aid=$aid", ext = "mp4", protocol = "m3u8_native"),
            ),
            webpageUrl = url,
            extractor = "soop:live",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "AfreecaTVLive"

        val VALID_URL: Regex = Regex("https?://play\\.sooplive\\.com/(?<id>[^/?#]+)(?:/(?<bno>\\d+))?")

        private val BAD_CDNS = setOf(
            "gs_cdn", "gs_cdn_chromecast", "lg_cdn_chromecast", "gs_cdn_pc_app", "kt_cdn",
        )
    }
}

/** Upstream `AfreecaTVUserIE`: the station VOD pages. */
class AfreecaTVUserIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val userId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val userType = match.groups["slugType"]?.value ?: "all"
        val entries = mutableListOf<InfoEntry>()
        var page = 1
        while (page <= MAX_PAGES) {
            val data = try {
                http.downloadJson(
                    "https://chapi.sooplive.com/api/$userId/vods/$userType" +
                        "?page=$page&per_page=$PER_PAGE&orderby=reg_date",
                ) as? JsonObject
            } catch (error: ExtractionError) {
                break
            } ?: break
            val items = data.array("data").orEmpty()
            if (items.isEmpty()) break
            for (element in items) {
                val item = element as? JsonObject ?: continue
                val titleNo = item.primitiveText("title_no") ?: continue
                entries += InfoEntry(
                    id = titleNo,
                    title = item.str("title_name"),
                    url = "https://vod.sooplive.com/player/$titleNo/",
                )
            }
            page++
        }
        return InfoDict(
            id = userId,
            title = "$userId - $userType",
            entries = entries,
            webpageUrl = url,
            extractor = "soop:user",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "AfreecaTVUser"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?sooplive\\.com/station/(?<id>[^/?#]+)/vod/?(?<slugType>[^/?#]+)?",
        )

        private const val PER_PAGE = 60
        private const val MAX_PAGES = 5
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun liveApi(
    http: ExtractorHttp,
    broadcasterId: String,
    data: String? = null,
    displayId: String = broadcasterId,
): JsonObject {
    val body = (data ?: "bid=$broadcasterId").encodeToByteArray()
    val response = http.downloadJson(
        "https://live.sooplive.com/afreeca/player_live_api.php",
        method = "POST",
        headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
        body = body,
    ) as? JsonObject ?: throw ExtractionError.Malformed("The live API was not an object.")
    return response.obj("CHANNEL") ?: JsonObject(emptyMap())
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
