/*
 * LBRY / Odysee extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `lbry.py` from
 * `yt_dlp/extractor/lbry.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `lbry.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public resolve/get/claim_search JSON-RPC proxy, the stream
 * metadata, the streaming URL formats (m3u8 or plain), the live check, and
 * the channel/playlist paged entries (five pages eagerly). The original
 * v3-quality probe (a HEAD request) and the optional auth-token header are
 * not translated. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.lbry

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

private const val BASE_URL_REGEX = "(?:https?://(?:www\\.)?(?:lbry\\.tv|odysee\\.com)/|lbry://)"
private const val CLAIM_ID_REGEX = "[0-9a-f]{1,40}"
private const val OPT_CLAIM_ID = "[^$@:/?#&]+(?:[:#]$CLAIM_ID_REGEX)?"
private const val PAGE_SIZE = 50

/** Upstream `LBRYIE`: a stream claim. */
class LBRYIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        var displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        displayId = if (displayId.startsWith("@")) {
            displayId.replace(':', '#')
        } else {
            displayId.replace('/', ':')
        }
        displayId = percentDecode(displayId)
        val uri = "lbry://$displayId"
        val result = resolveUrl(http, uri, displayId)
        val streamType = result.obj("value")?.str("stream_type")
        val formats = mutableListOf<MediaFormat>()
        var isLive = false
        val claimId: String
        if (streamType == "video" || streamType == "audio") {
            claimId = result.str("claim_id") ?: throw ExtractionError.Malformed("The stream had no claim id.")
            val streamingUrl = (callApiProxy(
                http,
                "get",
                claimId,
                """{"uri":"$uri"}""",
            ) as? JsonObject)?.str("streaming_url")
                ?: throw ExtractionError.NoFormats("The get API returned no streaming URL.")
            val ext = ExtractorUtils.determineExt(streamingUrl)
            formats += MediaFormat(
                formatId = "original",
                url = streamingUrl,
                ext = if (ext == "m3u8") "mp4" else ext,
                protocol = if (ext == "m3u8") "m3u8_native" else null,
                vcodec = if (streamType == "audio") MediaFormat.CODEC_NONE else null,
                width = result.obj("value")?.obj("video")?.number("width")?.toLong(),
                height = result.obj("value")?.obj("video")?.number("height")?.toLong(),
                filesize = result.obj("value")?.obj("source")?.number("size")?.toLong(),
            )
        } else if (result.str("value_type") == "stream") {
            claimId = result.obj("signing_channel")?.str("claim_id")
                ?: throw ExtractionError.Malformed("The live stream had no channel claim id.")
            val liveData = http.downloadJson(
                "https://api.odysee.live/livestream/is_live?channel_claim_id=$claimId",
            ) as? JsonObject ?: throw ExtractionError.Malformed("The live API was not an object.")
            val data = liveData.obj("data")
            val videoUrl = data?.str("VideoURL")
            if (data?.bool("Live") != true || videoUrl == null) {
                throw ExtractionError.NoFormats("This stream is not live.")
            }
            isLive = true
            val ext = ExtractorUtils.determineExt(videoUrl)
            formats += MediaFormat(
                formatId = "hls",
                url = videoUrl,
                ext = if (ext == "m3u8") "mp4" else ext,
                protocol = if (ext == "m3u8") "m3u8_native" else null,
            )
        } else {
            throw ExtractionError.UnsupportedUrl("This LBRY stream type is not supported.")
        }
        return parseStream(result, url).copy(
            id = claimId,
            formats = formats,
            isLive = isLive,
        )
    }

    companion object {
        const val IE_KEY: String = "LBRY"

        val VALID_URL: Regex = Regex(
            BASE_URL_REGEX + "(?:\\$/(?:download|embed)/)?" +
                "(?<id>[^$@:/?#]+/$CLAIM_ID_REGEX|(?:@$OPT_CLAIM_ID/)?$OPT_CLAIM_ID)",
        )
    }
}

/** Upstream `LBRYChannelIE`: the channel listings. */
class LBRYChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = (matchId(url) ?: throw ExtractionError.UnsupportedUrl()).replace(':', '#')
        val result = resolveUrl(http, "lbry://$displayId", displayId)
        val claimId = result.str("claim_id") ?: throw ExtractionError.Malformed("The channel had no claim id.")
        return playlistEntries(http, url, displayId, """ "channel_ids":["$claimId"] """.trim(), result)
    }

    companion object {
        const val IE_KEY: String = "LBRYChannel"

        val VALID_URL: Regex = Regex(BASE_URL_REGEX + "(?<id>@$OPT_CLAIM_ID)/?(?:[?&]|$)")
    }
}

/** Upstream `LBRYPlaylistIE`: the playlist listings. */
class LBRYPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val response = callApiProxy(
            http,
            "claim_search",
            displayId,
            """{"claim_ids":["$displayId"],"no_totals":true,"page":1,"page_size":$PAGE_SIZE}""",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The playlist search was not an object.")
        val item = response.array("items")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The playlist search returned no items.")
        val claimIds = item.obj("value")?.array("claims").orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content }
        val claimParam = claimIds.joinToString(",") { "\"$it\"" }
        return playlistEntries(http, url, displayId, """ "claim_ids":[$claimParam] """.trim(), item)
    }

    companion object {
        const val IE_KEY: String = "LBRYPlaylist"

        val VALID_URL: Regex = Regex(BASE_URL_REGEX + "\\$/(?:play)?list/(?<id>[0-9a-f-]+)")
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun callApiProxy(
    http: ExtractorHttp,
    method: String,
    displayId: String,
    params: String,
): kotlinx.serialization.json.JsonElement? {
    val body = """{"method":"$method","params":$params}"""
    val response = http.downloadJson(
        "https://api.lbry.tv/api/v1/proxy",
        method = "POST",
        headers = mapOf("Content-Type" to "application/json-rpc"),
        body = body.encodeToByteArray(),
    ) as? JsonObject ?: throw ExtractionError.Malformed("The LBRY proxy was not an object.")
    response.obj("error")?.let { error ->
        throw ExtractionError.Unavailable(
            "LBRY said: ${error.str("code")} - ${error.str("message")}",
        )
    }
    return response["result"]
}

private suspend fun resolveUrl(http: ExtractorHttp, uri: String, displayId: String): JsonObject {
    val result = callApiProxy(http, "resolve", displayId, """{"urls":["$uri"]}""") as? JsonObject
        ?: throw ExtractionError.Malformed("The resolve API was not an object.")
    return result.obj(uri)
        ?: result.values.firstOrNull() as? JsonObject
        ?: throw ExtractionError.Malformed("The resolve API returned no stream.")
}

private suspend fun playlistEntries(
    http: ExtractorHttp,
    url: String,
    displayId: String,
    claimParam: String,
    metadata: JsonObject,
): InfoDict {
    val entries = mutableListOf<InfoEntry>()
    var page = 1
    while (page <= MAX_PAGES) {
        val params = """{"no_totals":true,"page":$page,"page_size":$PAGE_SIZE,"claim_type":"stream",$claimParam}"""
        val response = try {
            callApiProxy(http, "claim_search", displayId, params) as? JsonObject
        } catch (error: ExtractionError) {
            null
        } ?: break
        val items = response.array("items").orEmpty()
        if (items.isEmpty()) break
        if (items.size < PAGE_SIZE) {
            for (element in items) {
                val item = element as? JsonObject ?: continue
                val name = item.str("name") ?: continue
                val claimId = item.str("claim_id") ?: continue
                entries += InfoEntry(
                    id = claimId,
                    title = item.obj("value")?.str("title"),
                    url = permanentUrl(url, name, claimId),
                )
            }
            break
        }
        for (element in items) {
            val item = element as? JsonObject ?: continue
            val name = item.str("name") ?: continue
            val claimId = item.str("claim_id") ?: continue
            entries += InfoEntry(
                id = claimId,
                title = item.obj("value")?.str("title"),
                url = permanentUrl(url, name, claimId),
            )
        }
        page++
    }
    val value = metadata.obj("value")
    return InfoDict(
        id = metadata.str("claim_id") ?: displayId,
        title = value?.str("title"),
        description = value?.str("description"),
        entries = entries,
        webpageUrl = url,
        extractor = "lbry:playlist",
        extractorKey = "LBRY",
    )
}

private const val MAX_PAGES = 5

private fun parseStream(stream: JsonObject, url: String): InfoDict {
    val value = stream.obj("value")
    val streamType = value?.str("stream_type")
    val channel = stream.obj("signing_channel")
    return InfoDict(
        title = value?.str("title"),
        description = value?.str("description"),
        duration = streamType?.let { value?.obj(it)?.number("duration") },
        uploadDate = stream.number("timestamp")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
        uploader = channel?.obj("value")?.str("title"),
        channel = channel?.obj("value")?.str("title"),
        channelId = channel?.str("claim_id"),
        thumbnails = listOfNotNull(
            value?.obj("thumbnail")?.str("url")?.let { Thumbnail(url = it) },
        ),
        webpageUrl = url,
        extractor = "lbry",
        extractorKey = "LBRY",
    )
}

private fun permanentUrl(url: String, claimName: String, claimId: String): String {
    val base = url.replace("lbry://", "https://lbry.tv/")
    val origin = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: "https://lbry.tv"
    return "$origin/$claimName:$claimId"
}

private fun percentDecode(value: String): String = runCatching {
    buildString {
        var i = 0
        while (i < value.length) {
            if (value[i] == '%' && i + 2 < value.length) {
                val code = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (code != null) {
                    append(code.toByte().toInt().toChar())
                    i += 3
                    continue
                }
            }
            append(value[i])
            i++
        }
    }
}.getOrDefault(value)

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

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
