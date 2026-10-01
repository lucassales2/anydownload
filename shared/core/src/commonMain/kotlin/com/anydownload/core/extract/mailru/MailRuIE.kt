/*
 * Mail.Ru extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `mailru.py` from
 * `yt_dlp/extractor/mailru.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `mailru.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the my.mail.ru video page variants (id-from-URL, the
 * `sp-video__page-config`/`"video"` page config, the meta JSON, and the
 * api.video.mail.ru fallback) plus the music track and paged music search.
 * The upstream `video_key` cookie is read from the page and re-attached to
 * media hosts; the port has no named-cookie API, so the active cookie jar
 * carries it instead and no cookie value is stored here. The search playlist
 * keeps the direct audio URL on each entry; track/artist/album labels are
 * not carried. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.mailru

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

private const val SEARCH_URL = "https://my.mail.ru/cgi-bin/my/ajax"
private const val MUSIC_PAGE_SIZE = 100
private const val MAX_PAGES = 5

/** Upstream `MailRuIE`: a Видео@Mail.Ru video page. */
class MailRuIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val metaId = match.groups["metaid"]?.value
        var videoId: String? = null
        var metaUrl: String? = null
        if (metaId != null) {
            metaUrl = "https://my.mail.ru/+/video/meta/$metaId"
        } else {
            videoId = match.groups["idv1"]?.value
                ?: (match.groups["idv2prefix"]?.value.orEmpty() + match.groups["idv2suffix"]?.value.orEmpty())
            if (videoId.isNullOrEmpty()) throw ExtractionError.UnsupportedUrl()
            val webpage = http.downloadWebpage(url)
            val pageConfig = parsePageConfig(webpage)
            metaUrl = pageConfig?.str("metaUrl")
                ?: pageConfig?.obj("video")?.str("metaUrl")
                ?: pageConfig?.str("metadataUrl")
        }
        if (metaUrl != null && metaUrl.startsWith("/+/")) {
            metaUrl = "https://my.mail.ru$metaUrl"
        }
        var videoData: JsonObject? = null
        if (metaUrl != null) {
            videoData = try {
                http.downloadJson(metaUrl) as? JsonObject
            } catch (error: ExtractionError) {
                if (videoId == null) throw error else null
            }
        }
        if (videoData == null) {
            videoData = http.downloadJson("http://api.video.mail.ru/videos/$videoId.json?new=1") as? JsonObject
                ?: throw ExtractionError.Malformed("The Mail.Ru video API was not an object.")
        }

        val formats = videoData.array("videos").orEmpty().mapNotNull { element ->
            val video = element as? JsonObject ?: return@mapNotNull null
            val videoUrl = video.str("url") ?: return@mapNotNull null
            val formatId = video.str("key")
            val height = formatId?.let {
                ExtractorUtils.searchRegex("^(\\d+)[pP]$", it)?.toLongOrNull()
            }
            MediaFormat(formatId = formatId, url = videoUrl, height = height)
        }
        val meta = videoData.obj("meta")
            ?: throw ExtractionError.Malformed("The Mail.Ru video had no meta.")
        val title = meta.str("title")?.removeSuffix(".mp4")
            ?: throw ExtractionError.Malformed("The Mail.Ru video had no title.")
        val author = videoData.obj("author")
        val accId = meta.primitive("accId")
        val itemId = meta.primitive("itemId")
        val contentId = if (accId != null && itemId != null) "${accId}_$itemId" else videoId ?: metaId
        return InfoDict(
            id = contentId,
            title = title,
            duration = meta.number("duration"),
            viewCount = videoData.number("viewsCount")?.toLong()
                ?: videoData.number("views_count")?.toLong(),
            uploadDate = meta.number("timestamp")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
            uploader = author?.str("name"),
            thumbnails = listOfNotNull(meta.str("poster")?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = url,
            extractor = "mailru",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MailRu"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|m|videoapi)\\.)?my\\.mail\\.ru/+(?:" +
                "video/.*#video=/?(?<idv1>(?:[^/]+/){3}\\d+)|" +
                "(?:videos/embed/)?(?:(?<idv2prefix>(?:[^/]+/+){2})(?:video/(?:embed/)?)?" +
                "(?<idv2suffix>[^/]+/\\d+))(?:\\.html)?|" +
                "(?:video/embed|\\+/video/meta)/(?<metaid>\\d+)" +
                ")",
        )
    }
}

/**
 * Upstream `MailRuMusicSearchBaseIE`: the shared `music.search` endpoint and
 * track mapping for the two music classes.
 */
abstract class MailRuMusicSearchBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected suspend fun search(
        query: String,
        url: String,
        audioId: String,
        limit: Int = MUSIC_PAGE_SIZE,
        offset: Int = 0,
    ): JsonObject {
        val parameters = listOf(
            "xemail" to "",
            "ajax_call" to "1",
            "func_name" to "music.search",
            "mna" to "",
            "mnb" to "",
            "arg_query" to query,
            "arg_extended" to "1",
            "arg_search_params" to "{\"music\":{\"limit\":$limit,\"offset\":$offset}}",
            "arg_limit" to limit.toString(),
            "arg_offset" to offset.toString(),
        )
        val response = http.downloadJson(
            SEARCH_URL + "?" + parameters.joinToString("&") { (key, value) ->
                "${percentEncode(key)}=${percentEncode(value)}"
            },
            headers = mapOf(
                "Referer" to url,
                "X-Requested-With" to "XMLHttpRequest",
            ),
        ) as? JsonArray ?: throw ExtractionError.Malformed("The Mail.Ru search response was not an array.")
        return response.firstNotNullOfOrNull { it as? JsonObject }
            ?: throw ExtractionError.Malformed("The Mail.Ru search response had no object.")
    }

    /** Upstream `_extract_track`: one music track as an info dict. */
    protected fun extractTrack(track: JsonObject, fatal: Boolean): InfoDict? {
        val audioUrl = track.str("URL") ?: return null
        val audioId = track.str("File") ?: return null
        val name = track.str("Name") ?: track.str("Name_Text_HTML")
        val artist = track.str("Author") ?: track.str("Author_Text_HTML")
        val title = if (name != null) {
            if (artist != null) "$artist - $name" else name
        } else {
            audioId
        }
        val duration = track.number("DurationInSeconds")?.toLong()
            ?: ExtractorUtils.parseDuration(track.str("Duration") ?: track.str("DurationStr"))?.toLong()
        return InfoDict(
            id = audioId,
            title = title,
            uploader = track.str("OwnerName") ?: track.str("OwnerName_Text_HTML"),
            duration = duration?.toDouble(),
            viewCount = track.number("PlayCount")?.toLong() ?: track.number("PlayCount_hr")?.toLong(),
            thumbnails = listOfNotNull(
                (track.str("AlbumCoverURL") ?: track.str("FiledAlbumCover"))?.let { Thumbnail(url = it) },
            ),
            formats = listOf(
                MediaFormat(
                    url = audioUrl,
                    ext = ExtractorUtils.determineExt(audioUrl),
                    abr = track.number("BitRate"),
                    vcodec = MediaFormat.CODEC_NONE,
                ),
            ),
            webpageUrl = null,
            extractorKey = ieKey,
        )
    }
}

/** Upstream `MailRuMusicIE`: one music track page. */
class MailRuMusicIE(
    http: ExtractorHttp,
) : MailRuMusicSearchBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val audioId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val title = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
            ?: throw ExtractionError.Malformed("The Mail.Ru music page had no title.")
        val response = search(title, url, audioId)
        val track = response.array("MusicData").orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.str("File") == audioId }
            ?: throw ExtractionError.Malformed("The Mail.Ru search returned no matching track.")
        val info = extractTrack(track, fatal = true)
            ?: throw ExtractionError.Malformed("The Mail.Ru track had no audio URL.")
        return info.copy(title = title, webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "MailRuMusic"

        val VALID_URL: Regex = Regex(
            "https?://my\\.mail\\.ru/+music/+songs/+[^/?#&]+-(?<id>[\\da-f]+)",
        )
    }
}

/** Upstream `MailRuMusicSearchIE`: a paged music search playlist. */
class MailRuMusicSearchIE(
    http: ExtractorHttp,
) : MailRuMusicSearchBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val query = percentDecode(displayId)
        val entries = mutableListOf<InfoEntry>()
        var offset = 0
        var page = 0
        while (page < MAX_PAGES) {
            val response = search(query, url, query, MUSIC_PAGE_SIZE, offset)
            val musicData = response.array("MusicData").orEmpty()
            if (musicData.isEmpty()) break
            for (element in musicData) {
                val track = element as? JsonObject ?: continue
                val info = extractTrack(track, fatal = false) ?: continue
                entries += InfoEntry(
                    id = info.id,
                    title = info.title,
                    url = info.formats.firstOrNull()?.url,
                )
            }
            val total = response.obj("Results")?.obj("music")?.number("Total")?.toLong()
            if (total != null && offset > total) break
            offset += MUSIC_PAGE_SIZE
            page++
        }
        return InfoDict(
            id = query,
            entries = entries,
            webpageUrl = url,
            extractor = "mailru:music:search",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MailRuMusicSearch"

        val VALID_URL: Regex = Regex(
            "https?://my\\.mail\\.ru/+music/+search/+(?<id>[^/?#&]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** The `sp-video__page-config` script or the `"video": {...}` page config. */
private fun parsePageConfig(webpage: String): JsonObject? {
    val script = Regex(
        "(?s)<script[^>]+class=\"sp-video__page-config\"[^>]*>(.+?)</script>",
    ).find(webpage)?.groupValues?.get(1)
    val video = Regex("(?s)\"video\":\\s*(\\{.+?\\}),").find(webpage)?.groupValues?.get(1)
    val body = script ?: video ?: return null
    return ExtractorUtils.parseJson(ExtractorUtils.jsToJson(body)) as? JsonObject
}

private const val HEX_DIGITS = "0123456789ABCDEF"

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

private fun percentDecode(value: String): String {
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%' && index + 2 < value.length) {
            val code = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (code != null) {
                bytes += code.toByte()
                index += 3
                continue
            }
        }
        bytes += character.toString().encodeToByteArray().toList()
        index++
    }
    return bytes.toByteArray().decodeToString()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
