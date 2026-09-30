/*
 * VK extractor — AnyDownload
 *
 * Kotlin translation of the `VKIE` embed and public-AJAX subset from
 * `yt_dlp/extractor/vk.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `vk.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `vk.com/video_ext.php?oid=…&id=…` (and the `daxab.com` forms) read
 * the public embed page's `playerParams`, and `vk.com/video<oid>_<id>` and
 * `vk.com/clip<oid>_<id>` post to the public `al_video.php` AJAX endpoint.
 * Both map the `url<cache>` progressive formats, HLS (`m3u8_native`), DASH
 * (`http_dash_segments`), RTMP, subtitle entries, title/author/duration/
 * thumbnail/date, the live flag, and the view count. Login code 3, error
 * code 8, region blocks, and removal messages fail typed.
 *
 * Not translated: user-video listings, wall posts, VK Play records and live
 * channels, chapter `time_codes`, and the YouTube/RuTube/Dailymotion/OK/
 * Sibnet embed re-dispatch. A login wall or DRM wall is Partial. No cookie,
 * bearer token, or signed media URL is stored or committed; fixture hosts are
 * `*.example`.
 */
package com.anydownlod.core.extract.vk

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.SubtitleFormat
import com.anydownlod.core.extract.SubtitleTrack
import com.anydownlod.core.extract.Thumbnail
import com.anydownlod.core.platform.HttpMethods
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `VKIE`: one public VK video. */
class VKIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "VK"

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val embedQuery = match.groups["embedQuery"]?.value
        val videoId: String
        val player: JsonObject
        val sourcePage: String

        if (embedQuery != null) {
            val oid = match.groups["oid"]?.value ?: throw ExtractionError.UnsupportedUrl()
            val id = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
            videoId = "${oid}_$id"
            sourcePage = http.downloadWebpage("https://vk.com/video_ext.php?$embedQuery")
            checkEmbedErrors(sourcePage)
            player = extractBalancedJson(sourcePage, PLAYER_PARAMS)
                ?: throw ExtractionError.Malformed("The VK embed page had no player params.")
        } else {
            videoId = match.groups["videoid"]?.value ?: throw ExtractionError.UnsupportedUrl()
            val response = http.downloadJson(
                VK_AJAX_URL,
                method = HttpMethods.POST,
                headers = mapOf(
                    "referer" to VK_AJAX_URL,
                    "x-requested-with" to "XMLHttpRequest",
                    "content-type" to "application/x-www-form-urlencoded",
                ),
                body = "act=show&video=$videoId&al=1".encodeToByteArray(),
            ) as? JsonObject ?: throw ExtractionError.Malformed("The VK AJAX response was empty.")
            val payload = response.array("payload")
                ?: throw ExtractionError.Malformed("The VK AJAX response had no payload.")
            when ((payload.getOrNull(0) as? JsonPrimitive)?.content) {
                "3" -> throw ExtractionError.LoginRequired()
                "8" -> throw ExtractionError.Unavailable("The VK video is not available.")
            }
            val opts = payload.lastOrNull() as? JsonObject
                ?: throw ExtractionError.Malformed("The VK AJAX payload was malformed.")
            sourcePage = (payload.getOrNull(1) as? JsonPrimitive)?.content.orEmpty()
            player = opts.obj("player")
                ?: throw ExtractionError.Malformed("The VK AJAX payload had no player.")
        }

        val data = player.array("params")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The VK player had no params.")
        val formats = formats(data)
        if (formats.isEmpty()) throw ExtractionError.NoFormats("VK declared no format.")
        val subtitles = subtitles(data)

        return InfoDict(
            id = videoId,
            title = (data.str("md_title") ?: player.str("title"))?.let(ExtractorUtils::unescapeHtml),
            description = data.str("description"),
            duration = data.number("duration") ?: data.number("md_duration"),
            uploader = data.str("md_author")?.let(ExtractorUtils::unescapeHtml),
            uploadDate = data.number("date")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            viewCount = viewCountOf(sourcePage),
            formats = formats,
            subtitles = subtitles,
            thumbnails = listOfNotNull(
                data.str("jpg")?.let { Thumbnail(url = protoRelative(it)) },
            ),
            isLive = data.number("live")?.toLong() == 2L,
            webpageUrl = url,
            extractor = "vk",
            extractorKey = IE_KEY,
        )
    }

    private fun checkEmbedErrors(page: String) {
        when {
            Regex("<!>Please log in or").containsMatchIn(page) -> throw ExtractionError.LoginRequired()
            Regex("not available in your region").containsMatchIn(page) -> throw ExtractionError.GeoRestricted()
            Regex("<!>(?:Unknown error|Access denied)").containsMatchIn(page) ||
                Regex("removed from public access").containsMatchIn(page) ||
                Regex("<!>Видео временно недоступно").containsMatchIn(page) ->
                throw ExtractionError.Unavailable("The VK video is not available.")
        }
    }

    /** Upstream `data == player['params'][0]` format mapping. */
    private fun formats(data: JsonObject): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        for ((formatId, element) in data) {
            val rawUrl = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            if (!rawUrl.startsWith("http") && !rawUrl.startsWith("//") && !rawUrl.startsWith("rtmp")) continue
            val formatUrl = protoRelative(rawUrl)
            when {
                formatId.startsWith("url") || formatId.startsWith("cache") ||
                    formatId in setOf("extra_data", "live_mp4", "postlive_mp4") -> formats += MediaFormat(
                    formatId = formatId,
                    url = formatUrl,
                    ext = "mp4",
                    sourcePreference = 1,
                    height = Regex("^(?:url|cache)(\\d+)").find(formatId)
                        ?.groupValues?.get(1)?.toLongOrNull(),
                )

                formatId.startsWith("hls") && formatId != "hls_live_playback" -> formats += MediaFormat(
                    formatId = formatId,
                    url = formatUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                    formatNote = "HLS",
                )

                formatId.startsWith("dash") && formatId !in setOf("dash_live_playback", "dash_uni") ->
                    formats += MediaFormat(
                        formatId = formatId,
                        url = formatUrl,
                        ext = "mp4",
                        protocol = "http_dash_segments",
                        formatNote = "DASH",
                    )

                formatId == "rtmp" -> formats += MediaFormat(
                    formatId = formatId,
                    url = formatUrl,
                    ext = "flv",
                )
            }
        }
        return formats
    }

    private fun subtitles(data: JsonObject): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()
        for (element in data.array("subs").orEmpty()) {
            val subtitle = element as? JsonObject ?: continue
            val subtitleUrl = subtitle.str("url")?.let(::protoRelative) ?: continue
            val title = subtitle.str("title")
            val ext = title?.substringAfterLast('.')?.takeIf { it in setOf("srt", "vtt") }
                ?: if (subtitleUrl.substringBefore('?').endsWith(".srt", ignoreCase = true)) "srt" else "vtt"
            tracks += SubtitleTrack(
                language = subtitle.str("lang") ?: "en",
                formats = listOf(SubtitleFormat(ext = ext, url = subtitleUrl)),
            )
        }
        return tracks
    }

    companion object {
        const val IE_KEY: String = "VK"

        private const val VK_AJAX_URL = "https://vk.com/al_video.php"

        /** Upstream `_VALID_URL` with Java-safe group names. */
        val VALID_URL: Regex = Regex(
            "https?://" +
                "(?:" +
                "(?:(?:(?:m|new|vksport)\\.)?vk(?:(?:video)?\\.ru|\\.com)/video_|(?:www\\.)?daxab\\.com/)" +
                "ext\\.php\\?(?<embedQuery>.*?\\boid=(?<oid>-?\\d+).*?\\bid=(?<id>\\d+).*)" +
                "|" +
                "(?:(?:(?:m|new|vksport)\\.)?vk(?:(?:video)?\\.ru|\\.com)/(?:.+?\\?.*?z=)?(?:video|clip)|" +
                "(?:www\\.)?daxab\\.com/embed/)" +
                "(?<videoid>-?\\d+_\\d+)(?:.*\\blist=(?<listId>[\\da-f]+|ln-[\\da-zA-Z]+))?" +
                ")",
        )

        private val PLAYER_PARAMS = Regex("var\\s+playerParams\\s*=\\s*")
    }
}

private fun protoRelative(value: String): String = if (value.startsWith("//")) "https:$value" else value

private fun viewCountOf(page: String): Long? = Regex("mv_views_count[^>]*>\\s*([\\d,.]+)")
    .find(page)?.groupValues?.get(1)?.filter { it.isDigit() }?.toLongOrNull()

/** The balanced object after a `var x = ({...})` marker, skipping one `(`. */
private fun extractBalancedJson(text: String, marker: Regex): JsonObject? {
    val match = marker.find(text) ?: return null
    var index = match.range.last + 1
    while (index < text.length && text[index].isWhitespace()) index++
    if (text.getOrNull(index) == '(') {
        index++
        while (index < text.length && text[index].isWhitespace()) index++
    }
    if (text.getOrNull(index) != '{') return null
    var depth = 0
    var inString = false
    var quote = ' '
    var escaped = false
    for (position in index until text.length) {
        val character = text[position]
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

            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return ExtractorUtils.parseJson(text.substring(index, position + 1)) as? JsonObject
                }
            }
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
