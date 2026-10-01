/*
 * Ustream (IBM Video) extractors — AnyDownload
 *
 * Kotlin translation of `ustream.py` from `yt_dlp/extractor/ustream.py` at
 * upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `ustream.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `recorded` JSON (direct `media_urls` rows with the upstream
 * filesize, or the HLS fallback through the `ums.ustream.tv` connection
 * info), the `embed` page's `offAirContentVideoIds` list, the
 * `embed/recorded` self-dispatch as a child entry, and the channel
 * `socialstream` pagination. Limitations: the upstream `_EMBED_REGEX`
 * generic discovery is not carried (GenericIE stays out); `uploader_id` is
 * not modeled and is dropped; an m3u8 fallback becomes one HLS row; the
 * commented-out segmented-MP4 DASH path stays unported. No cookie, token,
 * or signed media URL is stored here.
 */
package com.anydownload.core.extract.ustream

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/** Upstream `UstreamIE`: a recorded video, an embed, or an embed/recorded redirect. */
class UstreamIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val type = match.groups["type"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()

        if (type == "embed/recorded") {
            // Upstream `url_result(desktop_url, 'Ustream')`: the port expands one child job.
            return InfoDict(
                id = videoId,
                entries = listOf(InfoEntry(id = videoId, url = "http://www.ustream.tv/recorded/$videoId")),
                webpageUrl = url,
                extractor = "ustream",
                extractorKey = ieKey,
            )
        }

        if (type == "embed") {
            val webpage = http.downloadWebpage(url)
            val idsText = ExtractorUtils.searchRegex(
                "ustream\\.vars\\.offAirContentVideoIds=([^;]+);",
                webpage,
            ) ?: throw ExtractionError.Malformed("The Ustream embed carried no content video ids.")
            val ids = ExtractorUtils.parseJson(ExtractorUtils.jsToJson(idsText))
            val entries = (ids as? JsonArray).orEmpty().mapNotNull { element ->
                val id = (element as? JsonPrimitive)?.content ?: return@mapNotNull null
                InfoEntry(id = id, url = "http://www.ustream.tv/recorded/$id")
            }
            return InfoDict(
                id = videoId,
                entries = entries,
                webpageUrl = url,
                extractor = "ustream",
                extractorKey = ieKey,
            )
        }

        val params = http.downloadJson("https://api.ustream.tv/videos/$videoId.json") as? JsonObject
            ?: throw ExtractionError.Malformed("The Ustream video JSON was not an object.")
        params.str("error")?.let { error ->
            throw ExtractionError.Unavailable("ustream returned error: $error")
        }
        val video = params.obj("video")
            ?: throw ExtractionError.Malformed("The Ustream video JSON carried no video.")

        val title = video.str("title")
        val filesize = video.number("file_size")?.toLong()

        val formats = mutableListOf<MediaFormat>()
        for ((formatId, value) in video.obj("media_urls").orEmpty()) {
            val mediaUrl = (value as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
                ?.takeIf { it.isNotBlank() } ?: continue
            formats += MediaFormat(
                formatId = videoId,
                url = mediaUrl,
                ext = formatId,
                filesize = filesize,
            )
        }

        if (formats.isEmpty()) {
            val streamUrl = getStreams(url, videoId)
            if (streamUrl != null) {
                formats += MediaFormat(
                    formatId = "hls",
                    url = streamUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
            }
        }

        return InfoDict(
            id = videoId,
            title = title,
            description = video.str("description"),
            thumbnails = video.obj("thumbnail").orEmpty().mapNotNull { (id, value) ->
                (value as? JsonPrimitive)?.takeIf { it is JsonPrimitive && it !is JsonNull }?.content
                    ?.let { Thumbnail(url = it, id = id) }
            },
            uploadDate = video.number("created_at")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            duration = video.number("length"),
            viewCount = video.number("views")?.toLong(),
            uploader = video.obj("owner")?.str("username"),
            formats = formats,
            webpageUrl = url,
            extractor = "ustream",
            extractorKey = ieKey,
        )
    }

    /** Upstream `_get_streams`: the `ums` connection info, retried three times. */
    private suspend fun getStreams(url: String, videoId: String): String? {
        for (trial in 0 until 3) {
            val host = randomHex(Random.nextInt(100_000_000))
            val query = mapOf(
                "type" to "viewer",
                "appId" to "11",
                "appVersion" to "2",
                "rsid" to "${randomHex(Random.nextInt(100_000_000))}:${randomHex(Random.nextInt(100_000_000))}",
                "rpin" to "_rpin.${Random.nextLong(1_000_000_000_000_000)}",
                "referrer" to url,
                "media" to videoId,
                "application" to "recorded",
            )
            val queryString = query.entries.joinToString("&") {
                "${percentEncode(it.key)}=${percentEncode(it.value)}"
            }
            val connInfo = http.downloadJson(
                "http://r$host-1-$videoId-recorded-lp-live.ums.ustream.tv/1/ustream?$queryString",
            ) as? JsonArray
                ?: throw ExtractionError.Malformed("The Ustream connection info was not a list.")
            val first = ((connInfo.firstOrNull() as? JsonObject)?.array("args")?.firstOrNull() as? JsonObject)
                ?: throw ExtractionError.Malformed("The Ustream connection info carried no args.")
            val hostName = first.str("host")
                ?: throw ExtractionError.Malformed("The Ustream connection info carried no host.")
            val connectionId = first.str("connectionId")
                ?: throw ExtractionError.Malformed("The Ustream connection info carried no connection id.")

            val streamInfo = http.downloadJson("http://$hostName/1/ustream?connectionId=$connectionId")
                as? JsonArray
                ?: throw ExtractionError.Malformed("The Ustream stream info was not a list.")
            val stream = ((streamInfo.firstOrNull() as? JsonObject)?.array("args")?.firstOrNull() as? JsonObject)
                ?.obj("stream")
            stream?.str("url")?.let { return it }
        }
        return null
    }

    companion object {
        const val IE_KEY: String = "Ustream"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:ustream\\.tv|video\\.ibm\\.com)/" +
                "(?<type>recorded|embed|embed/recorded)/(?<id>\\d+)",
        )
    }
}

/** Upstream `UstreamChannelIE`: a channel page's socialstream. */
class UstreamChannelIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val channelId = ExtractorUtils.htmlSearchMeta(webpage, "ustream:channel_id")
            ?: throw ExtractionError.Malformed("The Ustream channel page carried no channel id.")

        val videoIds = mutableListOf<String>()
        var nextUrl: String? = "/ajax/socialstream/videos/$channelId/1.json"
        var pages = 0
        while (nextUrl != null && pages < MAX_PAGES) {
            pages++
            val absolute = if (nextUrl.startsWith("http")) nextUrl else "$BASE$nextUrl"
            val reply = http.downloadJson(absolute) as? JsonObject
                ?: throw ExtractionError.Malformed("The Ustream channel JSON was not an object.")
            val data = reply.str("data").orEmpty()
            for (match in CONTENT_ID.findAll(data)) {
                videoIds += match.groupValues[1]
            }
            nextUrl = reply.str("nextUrl")
        }

        return InfoDict(
            id = channelId,
            entries = videoIds.map { InfoEntry(id = it, url = "http://www.ustream.tv/recorded/$it") },
            webpageUrl = url,
            extractor = "ustream:channel",
            extractorKey = ieKey,
        )
    }

    companion object {
        const val IE_KEY: String = "UstreamChannel"

        private const val BASE = "http://www.ustream.tv"
        private const val MAX_PAGES = 100
        private val CONTENT_ID = Regex("data-content-id=\"(\\d.*)\"")

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?ustream\\.tv/channel/(?<id>.+)")
    }
}

// ------------------------------------------------------------------ helpers

private fun randomHex(value: Int): String = value.toString(16)

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    val content = primitive.content
    return if (primitive.isString) content.takeIf { it.isNotBlank() } else content
}

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

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

private const val HEX_DIGITS = "0123456789ABCDEF"
