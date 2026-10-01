/*
 * Česká televize extractor — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `ceskatelevize.py`
 * from `yt_dlp/extractor/ceskatelevize.py` at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `ceskatelevize.py` is not vendored;
 * see shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the page/Next.js IDEC discovery, the iframe-hash player page, the
 * `get-client-playlist` POST with the Safari retry, the playlist JSON, and
 * the HLS/DASH stream rows with the DRM flag and audio-description
 * preference. A single item becomes one info dict; several items become
 * selectable media. The upstream `x-addr` and `X-Requested-With` headers are
 * refused by the port's header allowlist (no impersonation), so a rejected
 * playlist call surfaces as typed Unavailable; the inline millisecond-to-SRT
 * subtitle conversion is not translated because the port's subtitle shape is
 * URL-only. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.ceskatelevize

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.InfoMedia
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val AJAX_PLAYLIST_URL = "https://www.ceskatelevize.cz/ivysilani/ajax/get-client-playlist/"
private const val SAFARI_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64; rv:10.0) AppleWebKit/533.20.25 " +
        "(KHTML, like Gecko) Version/5.0.4 Safari/533.20.27"

/** Upstream `CeskaTelevizeIE`: an iVysilani / porady / zive page. */
class CeskaTelevizeIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val finalUrl = try {
            http.followRedirects(url)
        } catch (error: ExtractionError) {
            url
        }
        var webpage = http.downloadWebpage(url)
        val path = "/" + finalUrl.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')

        val siteName = ExtractorUtils.htmlSearchMeta(webpage, "og:site_name") ?: "Česká televize"
        var playlistTitle = ExtractorUtils.htmlSearchMeta(webpage, "og:title")
        if (playlistTitle != null) {
            playlistTitle = playlistTitle.split(Regex("\\s*[—|]\\s*" + Regex.escape(siteName)), limit = 2)[0]
        }
        var playlistDescription = ExtractorUtils.htmlSearchMeta(webpage, "og:description")
            ?.replace('\u00A0', ' ')

        var type = "IDEC"
        if (Regex("(^/porady|/zive)/").containsMatchIn(path)) {
            val nextData = nextJsData(webpage)
                ?: throw ExtractionError.Malformed("The Ceská televize page had no Next.js data.")
            val data = nextData.obj("props")?.obj("pageProps")?.obj("data")
            var idec: String?
            if (path.contains("/zive/")) {
                idec = data?.obj("liveBroadcast")?.obj("current")?.str("idec")
            } else {
                idec = data?.obj("show")?.obj("mediaMeta")?.str("idec")
                if (idec == null) {
                    idec = data?.obj("videobonusDetail")?.str("bonusId")
                    if (idec != null) type = "bonus"
                }
            }
            if (idec == null) throw ExtractionError.Malformed("Failed to find the IDEC id.")
            val iframeHash = http.downloadWebpage("https://www.ceskatelevize.cz/v-api/iframe-hash/")
            webpage = http.downloadWebpage(
                "https://www.ceskatelevize.cz/ivysilani/embed/iFramePlayer.php" +
                    "?hash=${percentEncode(iframeHash)}&origin=iVysilani&autoStart=true" +
                    "&$type=${percentEncode(idec)}",
            )
        }

        val notAvailable = "This content is not available at your territory due to limited copyright."
        if (webpage.contains("$notAvailable</p>")) throw ExtractionError.GeoRestricted()
        if (
            webpage.contains("Neplatný parametr pro videopřehrávač") ||
            webpage.contains("IDEC nebyl nalezen")
        ) {
            throw ExtractionError.Unavailable("No video with IDEC available.")
        }

        var playlistType: String? = null
        var episodeId: String? = null
        ExtractorUtils.searchRegex("getPlaylistUrl\\(\\[(\\{.+?\\})\\]", webpage)?.let { json ->
            val parsed = ExtractorUtils.parseJson(json) as? JsonObject
            playlistType = parsed?.str("type")
            episodeId = parsed?.str("id")
        }
        if (playlistType == null) {
            playlistType = ExtractorUtils.searchRegex(
                "getPlaylistUrl\\(\\[\\{\"type\":\"(.+?)\",\"id\":\".+?\"\\}\\],",
                webpage,
            )
        }
        if (episodeId == null) {
            episodeId = ExtractorUtils.searchRegex(
                "getPlaylistUrl\\(\\[\\{\"type\":\".+?\",\"id\":\"(.+?)\"\\}\\],",
                webpage,
            )
        }
        if (playlistType == null || episodeId == null) {
            throw ExtractionError.Malformed("The Ceská televize playlist id was not found.")
        }
        val body = "playlist[0][type]=${percentEncode(playlistType)}" +
            "&playlist[0][id]=${percentEncode(episodeId)}" +
            "&requestUrl=${percentEncode(path)}&requestSource=iVysilani"

        val entries = mutableListOf<InfoDict>()
        for (userAgent in listOf(null, SAFARI_USER_AGENT)) {
            val headers = linkedMapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "x-addr" to "127.0.0.1",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to url,
            )
            if (userAgent != null) headers["User-Agent"] = userAgent
            val playlistPage = try {
                http.downloadJson(
                    AJAX_PLAYLIST_URL,
                    method = "POST",
                    headers = headers,
                    body = body.encodeToByteArray(),
                ) as? JsonObject
            } catch (error: ExtractionError) {
                null
            } ?: continue
            val playlistUrl = playlistPage.str("url") ?: continue
            if (playlistUrl == "error_region") throw ExtractionError.GeoRestricted()
            val playlistResponse = try {
                http.downloadJson(percentDecode(playlistUrl)) as? JsonObject
            } catch (error: ExtractionError) {
                null
            } ?: continue
            val playlist = playlistResponse.array("playlist") ?: continue
            val playlistLen = playlist.size
            for ((num, element) in playlist.withIndex()) {
                val item = element as? JsonObject ?: continue
                val formats = mutableListOf<MediaFormat>()
                for ((formatId, value) in item.obj("streamUrls").orEmpty()) {
                    val streamUrl = (value as? JsonPrimitive)?.content ?: continue
                    var format = if (streamUrl.contains("playerType=flash")) {
                        MediaFormat(
                            formatId = "hls-$formatId",
                            url = streamUrl,
                            ext = "mp4",
                            protocol = "m3u8_native",
                        )
                    } else {
                        MediaFormat(formatId = "dash-$formatId", url = streamUrl, ext = "mp4", protocol = "mpd")
                    }
                    if (streamUrl.contains("drmOnly=true")) format = format.copy(hasDrm = true)
                    if (formatId == "audioDescription") format = format.copy(sourcePreference = -10)
                    formats += format
                }
                if (userAgent != null && entries.size == playlistLen) {
                    // The Safari pass only adds the streams the first pass missed;
                    // identical rows are dropped instead of doubled.
                    entries[num] = entries[num].copy(
                        formats = (entries[num].formats + formats).distinctBy { it.url },
                    )
                    continue
                }
                val itemId = item.primitive("id") ?: item.primitive("assetId")
                val title = item.str("title").orEmpty()
                val finalTitle = if (playlistLen == 1) {
                    playlistTitle ?: title
                } else {
                    "${playlistTitle ?: ""} ($title)"
                }
                entries += InfoDict(
                    id = itemId,
                    title = finalTitle,
                    description = if (playlistLen == 1) playlistDescription else null,
                    duration = item.number("duration"),
                    thumbnails = listOfNotNull(
                        item.str("previewImageUrl")?.let { Thumbnail(url = it) },
                    ),
                    formats = formats,
                    isLive = item.str("type") == "LIVE",
                    webpageUrl = url,
                    extractor = "ceskatelevize",
                    extractorKey = IE_KEY,
                )
            }
        }
        if (entries.isEmpty()) {
            throw ExtractionError.NoFormats("No Ceská televize playlist was found.")
        }
        if (entries.size == 1) return entries[0]
        return InfoDict(
            id = playlistId,
            title = playlistTitle,
            description = playlistDescription,
            media = entries.map { entry ->
                InfoMedia(
                    mediaId = entry.id ?: playlistId,
                    title = entry.title,
                    duration = entry.duration,
                    thumbnails = entry.thumbnails,
                    formats = entry.formats,
                )
            },
            webpageUrl = url,
            extractor = "ceskatelevize",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "CeskaTelevize"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?ceskatelevize\\.cz/(?:ivysilani|porady|zive)/" +
                "(?:[^/?#&]+/)*(?<id>[^/#?]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun nextJsData(webpage: String): JsonObject? {
    val body = Regex("(?s)<script[^>]+id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>")
        .find(webpage)?.groupValues?.get(1) ?: return null
    return ExtractorUtils.parseJson(body) as? JsonObject
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
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
