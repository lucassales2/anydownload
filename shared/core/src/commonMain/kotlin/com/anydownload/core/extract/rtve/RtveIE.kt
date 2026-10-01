/*
 * RTVE extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `rtve.py` from
 * `yt_dlp/extractor/rtve.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rtve.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the PNG tEXt url cipher (a data transformation, no crypto), the
 * alacarta/audio/live metadata APIs, subtitles, the television redirect,
 * and the program paged entries. The JSON-LD merge and the season/episode
 * number fields the port does not carry are dropped. No cookie, token, or
 * signed media URL is stored here.
 */
package com.anydownload.core.extract.rtve

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `RTVEALaCartaIE`: the a la carta and Play videos. */
class RTVEALaCartaIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val metadata = apiItems(
            http,
            "http://www.rtve.es/api/videos/$videoId/config/alacarta_videos.json",
            videoId,
        ) ?: throw ExtractionError.Malformed("The alacarta API returned no video.")
        val (formats, subtitles) = extractPngFormats(http, videoId)
        val subtitleTracks = subtitles.toMutableList()
        val subtitleItems = try {
            (http.downloadJson("https://api2.rtve.es/api/videos/$videoId/subtitulos.json") as? JsonObject)
                ?.obj("page")?.array("items")
        } catch (error: ExtractionError) {
            null
        }
        for (element in subtitleItems.orEmpty()) {
            val subtitle = element as? JsonObject ?: continue
            val src = subtitle.str("src") ?: continue
            subtitleTracks += SubtitleTrack(
                language = subtitle.str("language") ?: "es",
                formats = listOf(SubtitleFormat(ext = ExtractorUtils.determineExt(src), url = src)),
            )
        }
        return parseMetadata(metadata).copy(
            id = videoId,
            formats = formats,
            subtitles = subtitleTracks,
            webpageUrl = url,
            extractor = "rtve.es:alacarta",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RTVEALaCarta"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?rtve\\.es/(?:alacarta|play)/videos/(?:[^/?#]+/){2}(?<id>\\d+)",
        )
    }
}

/** Upstream `RTVEAudioIE`: the audio pages. */
class RTVEAudioIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val audioId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val metadata = apiItems(http, "https://www.rtve.es/api/audios/$audioId.json", audioId)
            ?: throw ExtractionError.Malformed("The audio API returned no audio.")
        val (formats, subtitles) = extractPngFormats(http, audioId, mediaType = "audios")
        return parseMetadata(metadata).copy(
            id = audioId,
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "rtve.es:audio",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RTVEAudio"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?rtve\\.es/(alacarta|play)/audios/(?:[^/?#]+/){2}(?<id>\\d+)",
        )
    }
}

/** Upstream `RTVELiveIE`: the live channel pages. */
class RTVELiveIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val dataSetup = balancedAfter(
            webpage,
            Regex("<div[^>]+class=\"[^\"]*videoPlayer[^\"]*\"[^>]*data-setup='"),
        ) ?: throw ExtractionError.Malformed("The live page had no player setup.")
        val idAsset = dataSetup.str("idAsset")
            ?: throw ExtractionError.Malformed("The live player setup had no asset id.")
        val (formats, subtitles) = extractPngFormats(http, idAsset)
        return InfoDict(
            id = videoId,
            title = ExtractorUtils.searchRegex(
                "(?s)<title[^>]*>([^<]+)</title>",
                webpage,
                default = null,
            )?.trim(),
            isLive = true,
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "rtve.es:live",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RTVELive"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?rtve\\.es/(?:play/videos/directo|directo)/(?:[^/?#]+/)*(?<id>[^/?#]+)/?",
        )
    }
}

/** Upstream `RTVETelevisionIE`: the television pages. */
class RTVETelevisionIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val pageId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val playUrl = ExtractorUtils.htmlSearchMeta(webpage, "contentUrl")
            ?: throw ExtractionError.Unavailable("The webpage doesn't contain any video.")
        return InfoDict(
            id = pageId,
            redirectUrl = playUrl,
            webpageUrl = url,
            extractor = "rtve.es:television",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RTVETelevision"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?rtve\\.es/television/[^/?#]+/[^/?#]+/(?<id>\\d+).shtml",
        )
    }
}

/** Upstream `RTVEProgramIE`: the program video listings. */
class RTVEProgramIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val programId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val entries = mutableListOf<InfoEntry>()
        var page = 1
        while (page <= MAX_PAGES) {
            val pageData = try {
                http.downloadJson(
                    "https://www.rtve.es/api/programas/$programId/videos?type=39816&page=$page&size=60",
                ) as? JsonObject
            } catch (error: ExtractionError) {
                null
            } ?: break
            val items = pageData.obj("page")?.array("items").orEmpty()
            if (items.isEmpty()) break
            for (element in items) {
                val video = element as? JsonObject ?: continue
                val htmlUrl = video.str("htmlUrl") ?: continue
                entries += InfoEntry(
                    id = video.primitiveText("id"),
                    title = video.str("longTitle"),
                    url = htmlUrl,
                )
            }
            if (items.size < PAGE_SIZE) break
            page++
        }
        return InfoDict(
            id = programId,
            entries = entries,
            webpageUrl = url,
            extractor = "rtve.es:program",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RTVEProgram"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?rtve\\.es/play/videos/(?<id>[\\w-]+)/?(?:[?#]|$)")

        private const val MAX_PAGES = 5
        private const val PAGE_SIZE = 60
    }
}

// ------------------------------------------------------------------ helpers

private suspend fun apiItems(
    http: ExtractorHttp,
    url: String,
    videoId: String,
): JsonObject? {
    val response = try {
        http.downloadJson(url) as? JsonObject
    } catch (error: ExtractionError) {
        null
    } ?: return null
    return response.obj("page")?.array("items")?.firstOrNull() as? JsonObject
}

private val QUALITY_ORDER = listOf("Media", "Alta", "HQ", "HD_READY", "HD_FULL")

private suspend fun extractPngFormats(
    http: ExtractorHttp,
    videoId: String,
    mediaType: String = "videos",
): Pair<List<MediaFormat>, List<SubtitleTrack>> {
    val formats = mutableListOf<MediaFormat>()
    for (manager in listOf("rtveplayw", "default")) {
        val png = try {
            http.downloadWebpage(
                "http://www.rtve.es/ztnr/movil/thumbnail/$manager/$mediaType/$videoId.png?q=v2",
            )
        } catch (error: ExtractionError) {
            null
        } ?: continue
        for ((quality, videoUrl) in decryptUrl(png)) {
            val ext = ExtractorUtils.determineExt(videoUrl)
            val preference = QUALITY_ORDER.indexOf(quality).takeIf { it >= 0 }?.plus(1)
            when (ext) {
                "m3u8" -> formats += MediaFormat(
                    formatId = quality.takeIf { it.isNotEmpty() },
                    url = videoUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                    preference = preference,
                )

                "mpd" -> formats += MediaFormat(
                    formatId = quality.takeIf { it.isNotEmpty() },
                    url = videoUrl,
                    ext = "mp4",
                    protocol = "mpd",
                    preference = preference,
                )

                else -> formats += MediaFormat(
                    formatId = quality.takeIf { it.isNotEmpty() },
                    url = videoUrl,
                    preference = preference,
                )
            }
        }
    }
    if (formats.isEmpty()) {
        throw ExtractionError.NoFormats("The RTVE PNG endpoint returned no playable URL.")
    }
    return Pair(formats, emptyList())
}

private fun parseMetadata(metadata: JsonObject): InfoDict = InfoDict(
    title = metadata.str("title")?.trim(),
    description = cleanHtml(metadata.str("description")),
    duration = metadata.number("duration")?.div(1000),
    uploadDate = ExtractorUtils.unifiedStrdate(metadata.str("dateOfEmission")),
    isLive = metadata.bool("live"),
    thumbnails = listOfNotNull(
        (metadata.str("thumbnail") ?: metadata.str("image") ?: metadata.str("imageSEO"))
            ?.let { Thumbnail(url = it) },
    ),
    channel = cleanHtml(
        metadata.str("programTitle") ?: metadata.obj("programInfo")?.str("title"),
    ),
)

/** Upstream `RTVEBaseIE._decrypt_url`: the PNG tEXt url cipher. */
private fun decryptUrl(png: String): List<Pair<String, String>> {
    val bytes = runCatching {
        kotlin.io.encoding.Base64.Default.decode(png)
    }.getOrNull() ?: return emptyList()
    var offset = 8
    val out = mutableListOf<Pair<String, String>>()
    while (offset + 8 <= bytes.size) {
        var length = 0
        for (i in 0 until 4) length = (length shl 8) or (bytes[offset + i].toInt() and 0xff)
        val type = (offset + 4 until offset + 8).map { (bytes[it].toInt() and 0xff).toChar() }.joinToString("")
        offset += 8
        if (type == "IEND") break
        if (offset + length > bytes.size) break
        val data = bytes.copyOfRange(offset, offset + length)
        offset += length + 4
        if (type != "tEXt") continue
        val filtered = data.filter { it.toInt() != 0 }.toByteArray()
        val text = filtered.map { (it.toInt() and 0xff).toChar() }.joinToString("")
        val hashIndex = text.indexOf('#')
        if (hashIndex < 0) continue
        val alphabetData = text.substring(0, hashIndex)
        val urlPart = text.substring(hashIndex + 1)
        val separator = urlPart.lastIndexOf("%%")
        val quality = if (separator >= 0) urlPart.substring(0, separator) else ""
        val urlData = if (separator >= 0) urlPart.substring(separator + 2) else urlPart
        out += quality to getUrl(getAlphabet(alphabetData), urlData)
    }
    return out
}

private fun getAlphabet(alphabetData: String): String {
    val alphabet = StringBuilder()
    var e = 0
    var d = 0
    for (char in alphabetData) {
        if (d == 0) {
            alphabet.append(char)
            e = (e + 1) % 4
            d = e
        } else {
            d -= 1
        }
    }
    return alphabet.toString()
}

private fun getUrl(alphabet: String, urlData: String): String {
    val url = StringBuilder()
    var f = 0
    var e = 3
    var b = 1
    var l = 0
    for (char in urlData) {
        if (f == 0) {
            l = (char.digitToIntOrNull() ?: 0) * 10
            f = 1
        } else {
            if (e == 0) {
                l += char.digitToIntOrNull() ?: 0
                if (l in alphabet.indices) url.append(alphabet[l])
                e = (b + 3) % 4
                f = 0
                b += 1
            } else {
                e -= 1
            }
        }
    }
    return url.toString()
}

private fun balancedAfter(html: String, marker: Regex): JsonObject? {
    val match = marker.find(html) ?: return null
    val start = html.indexOf('{', match.range.last + 1)
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var i = start
    while (i < html.length) {
        val c = html[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return ExtractorUtils.parseJson(html.substring(start, i + 1)) as? JsonObject
                    }
                }
            }
        }
        i++
    }
    return null
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
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
