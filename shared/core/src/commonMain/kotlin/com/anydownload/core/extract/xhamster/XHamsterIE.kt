/*
 * xHamster extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `xhamster.py` from
 * `yt_dlp/extractor/xhamster.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `xhamster.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `window.initials` videoModel (sources, xplayerSettings hls and
 * standard streams), the `_ByteGenerator` URL decipher (all seven algorithms
 * with int32 semantics), the old-layout page fallback, the embed re-dispatch,
 * and the user/creator listings. HLS masters are recorded as `m3u8_native`
 * and parsed at download time. Requests are never impersonated, so a page
 * that refuses the plain client fails typed; `display_id`, `uploader_url`,
 * `like_count`/`dislike_count`/`comment_count`, and `categories` are not
 * modeled on the port's InfoDict. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.xhamster

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

/** Upstream `_ByteGenerator`: the seven int32 keystream algorithms. */
class ByteGenerator(algoId: Int, seed: Int) {
    private var state: Int = seed
    private val algorithm: (Int) -> Int = when (algoId) {
        1 -> ::algo1
        2 -> ::algo2
        3 -> ::algo3
        4 -> ::algo4
        5 -> ::algo5
        6 -> ::algo6
        7 -> ::algo7
        else -> throw ExtractionError.Malformed("Unknown xHamster algorithm ID \"$algoId\".")
    }

    /** Upstream `__next__`: one keystream byte. */
    fun nextByte(): Int = algorithm(state) and 0xFF

    private fun algo1(s: Int): Int {
        state = s * 1664525 + 1013904223
        return state
    }

    private fun algo2(s: Int): Int {
        var value = s xor (s shl 13)
        value = value xor unsignedShiftRight(value, 17)
        state = value xor (value shl 5)
        return state
    }

    private fun algo3(s: Int): Int {
        var value = s + 0x9e3779b9.toInt()
        value = value xor unsignedShiftRight(value, 16)
        value *= 0x85ebca77.toInt()
        value = value xor unsignedShiftRight(value, 13)
        value *= 0xc2b2ae3d.toInt()
        state = value
        return value xor unsignedShiftRight(value, 16)
    }

    private fun algo4(s: Int): Int {
        var value = s + 0x6d2b79f5
        value = (value shl 7) or unsignedShiftRight(value, 25)
        value += 0x9e3779b9.toInt()
        value = value xor unsignedShiftRight(value, 11)
        state = value
        return value * 0x27d4eb2d
    }

    private fun algo5(s: Int): Int {
        var value = s xor (s shl 7)
        value = value xor unsignedShiftRight(value, 9)
        value = value xor (value shl 8)
        state = value + 0xa5a5a5a5.toInt()
        return state
    }

    private fun algo6(s: Int): Int {
        val value = s * 0x2c9277b5.toInt() + 0xac564b05.toInt()
        state = value
        val value2 = value xor unsignedShiftRight(value, 18)
        val shift = unsignedShiftRight(value, 27) and 31
        return unsignedShiftRight(value2, shift)
    }

    private fun algo7(s: Int): Int {
        val value = s + 0x9e3779b9.toInt()
        state = value
        var mixed = value xor (value shl 5)
        mixed *= 0x7feb352d
        mixed = mixed xor unsignedShiftRight(mixed, 15)
        return mixed * 0x846ca68b.toInt()
    }

    companion object {
        private fun unsignedShiftRight(value: Int, bits: Int): Int =
            ((value.toLong() and 0xFFFFFFFFL) shr bits).toInt()
    }
}

/** Upstream `XHamsterIE`: the video pages across the xHamster domains. */
class XHamsterIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: match.groups["id2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["displayId"]?.value ?: match.groups["displayId2"]?.value
        val desktopUrl = Regex("^(https?://(?:.+?\\.)?)m\\.").replace(url) { it.groupValues[1] }
        val webpage = http.downloadWebpage(desktopUrl)

        val error = ExtractorUtils.searchRegex(
            "<div[^>]+id=[\"']videoClosed[\"'][^>]*>(.+?)</div>",
            webpage,
            default = null,
        )
        if (error != null) throw ExtractionError.Unavailable(error)

        val initials = extractJsonElement(webpage, Regex("window\\.initials\\s*=\\s*")) as? JsonObject
        if (initials != null) {
            val video = initials.obj("videoModel")
                ?: throw ExtractionError.Malformed("The xHamster initials had no video model.")
            val formats = mutableListOf<MediaFormat>()
            val formatUrls = mutableSetOf<String>()
            val formatSizes = mutableMapOf<String, Double?>()
            val sources = video.obj("sources") ?: JsonObject(emptyMap())
            for ((formatId, formatsElement) in sources) {
                val formatsDict = formatsElement as? JsonObject ?: continue
                sources.obj("download")?.forEach { (quality, formatElement) ->
                    val size = (formatElement as? JsonObject)?.number("size")
                    formatSizes[quality] = size
                }
                for ((quality, formatElement) in formatsDict) {
                    if (formatId == "download") continue
                    val formatUrl = ExtractorUtils.urlOrNone((formatElement as? JsonPrimitive)?.content)
                        ?: continue
                    if (!formatUrls.add(formatUrl)) continue
                    formats += MediaFormat(
                        formatId = "$formatId-$quality",
                        url = formatUrl,
                        ext = ExtractorUtils.determineExt(formatUrl, defaultExt = "mp4"),
                        height = heightOf(quality),
                        filesize = formatSizes[quality]?.toLong(),
                        httpHeaders = mapOf("referer" to desktopUrl),
                    )
                }
            }

            val xplayerSources = initials.obj("xplayerSettings")?.obj("sources")
            val hlsSources = xplayerSources?.obj("hls")
            if (hlsSources != null) {
                for (key in listOf("url", "fallback")) {
                    val hlsUrl = hlsSources.str(key) ?: continue
                    val deciphered = decipherFormatUrl(hlsUrl, "hls-$key") ?: continue
                    if (!formatUrls.add(deciphered)) continue
                    formats += hlsFormat(deciphered, desktopUrl)
                }
            }
            val standardSources = xplayerSources?.obj("standard")
            if (standardSources != null) {
                for ((identifier, listElement) in standardSources) {
                    val list = listElement as? JsonArray ?: continue
                    for (element in list) {
                        val standardFormat = element as? JsonObject ?: continue
                        for (key in listOf("url", "fallback")) {
                            val standardUrl = standardFormat.str(key) ?: continue
                            val quality = standardFormat.str("quality") ?: standardFormat.str("label") ?: ""
                            val formatId = joinNonEmpty(identifier, quality) ?: identifier
                            val deciphered = decipherFormatUrl(standardUrl, formatId) ?: continue
                            if (!formatUrls.add(deciphered)) continue
                            if (ExtractorUtils.determineExt(deciphered, defaultExt = "") == "m3u8") {
                                formats += hlsFormat(deciphered, desktopUrl)
                            } else {
                                formats += MediaFormat(
                                    formatId = formatId,
                                    url = deciphered,
                                    ext = ExtractorUtils.determineExt(deciphered, defaultExt = "mp4"),
                                    height = heightOf(quality),
                                    filesize = formatSizes[quality]?.toLong(),
                                    httpHeaders = mapOf("referer" to desktopUrl),
                                )
                            }
                        }
                    }
                }
            }

            val author = video.obj("author")
            val uploaderUrl = ExtractorUtils.urlOrNone(author?.str("pageURL"))
            return InfoDict(
                id = videoId,
                title = video.str("title"),
                description = video.str("description"),
                duration = video.number("duration"),
                uploadDate = video.number("created")?.toLong()?.let(ExtractorUtils::epochSecondsToDate),
                uploader = author?.str("name"),
                viewCount = video.number("views")?.toLong(),
                ageLimit = 18,
                thumbnails = ExtractorUtils.urlOrNone(video.str("thumbURL"))
                    ?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
                formats = fixupFormats(formats),
                webpageUrl = desktopUrl,
                extractor = "xhamster",
                extractorKey = IE_KEY,
            )
        }

        // Old layout fallback.
        val title = ExtractorUtils.searchRegex(
            "<h1[^>]*>([^<]+)</h1>",
            webpage,
            default = null,
        ) ?: ExtractorUtils.htmlSearchMeta(webpage, "og:title")
        val formats = mutableListOf<MediaFormat>()
        val formatUrls = mutableSetOf<String>()
        val sources = extractJsonElement(webpage, Regex("sources\\s*:\\s*")) as? JsonObject
        for ((formatId, formatElement) in sources.orEmpty()) {
            val formatUrl = ExtractorUtils.urlOrNone((formatElement as? JsonPrimitive)?.content) ?: continue
            if (!formatUrls.add(formatUrl)) continue
            formats += MediaFormat(formatId = formatId, url = formatUrl, height = heightOf(formatId))
        }
        ExtractorUtils.searchRegex(
            "file\\s*:\\s*([\"'])(?<mp4>.+?)\\1",
            webpage,
            group = 2,
            default = null,
        )?.let { videoUrl ->
            if (formatUrls.add(videoUrl)) formats += MediaFormat(url = videoUrl)
        }
        val description = Regex("<span>Description: </span>([^<]+)").find(webpage)?.groupValues?.get(1)
        val uploadDate = ExtractorUtils.searchRegex(
            "hint=[\"'](\\d{4}-\\d{2}-\\d{2}) \\d{2}:\\d{2}:\\d{2} [A-Z]{3,4}",
            webpage,
            default = null,
        )?.let(ExtractorUtils::unifiedStrdate)
        val uploader = ExtractorUtils.searchRegex(
            "<span[^>]+itemprop=[\"']author[^>]+><a[^>]+><span[^>]+>([^<]+)",
            webpage,
            default = "anonymous",
        )
        val thumbnail = ExtractorUtils.searchRegex(
            "[\"']thumbUrl[\"']\\s*:\\s*([\"'])(?<thumbnail>.+?)\\1",
            webpage,
            group = 2,
            default = null,
        )
        val duration = ExtractorUtils.parseDuration(
            ExtractorUtils.searchRegex(
                "Runtime:\\s*</span>\\s*([\\d:]+)",
                webpage,
                default = null,
            ),
        )
        val viewCount = ExtractorUtils.searchRegex(
            "content=[\"']User(?:View|Play)s:(\\d+)",
            webpage,
            default = null,
        )?.toLongOrNull()

        return InfoDict(
            id = videoId,
            title = title ?: displayId,
            description = description,
            uploadDate = uploadDate,
            uploader = uploader,
            duration = duration,
            viewCount = viewCount,
            ageLimit = 18,
            thumbnails = ExtractorUtils.urlOrNone(thumbnail)?.let { listOf(Thumbnail(url = it)) }.orEmpty(),
            formats = formats,
            webpageUrl = desktopUrl,
            extractor = "xhamster",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_decipher_format_url`. */
    private fun decipherFormatUrl(formatUrl: String, formatId: String): String? {
        if (HEX_RE.matches(formatUrl)) return decipherHexString(formatUrl, formatId)
        if (!formatUrl.startsWith("http://") && !formatUrl.startsWith("https://")) return null
        val afterScheme = formatUrl.substringAfter("://")
        val host = afterScheme.substringBefore('/')
        val pathAndQuery = afterScheme.substringAfter('/', "")
        val path = pathAndQuery.substringBefore('?').substringBefore('#')
        val remainderQuery = pathAndQuery.substring(path.length)
        val match = Regex("^/(?<hex>$HEX)(?<rem>[/,].+)$").find("/$path") ?: return null
        val deciphered = decipherHexString(match.groups["hex"]!!.value, formatId) ?: return null
        return "${formatUrl.substringBefore("://")}://$host/${deciphered}${match.groups["rem"]!!.value}$remainderQuery"
    }

    /** Upstream `_decipher_hex_string`. */
    private fun decipherHexString(hexString: String, formatId: String): String? {
        val byteData = hexToBytes(hexString) ?: return null
        if (byteData.size < 5) return null
        val seed = (byteData[1].toInt() and 0xFF) or
            ((byteData[2].toInt() and 0xFF) shl 8) or
            ((byteData[3].toInt() and 0xFF) shl 16) or
            ((byteData[4].toInt() and 0xFF) shl 24)
        val generator = try {
            ByteGenerator(byteData[0].toInt() and 0xFF, seed)
        } catch (error: ExtractionError) {
            return null
        }
        return buildString {
            for (index in 5 until byteData.size) {
                val value = (byteData[index].toInt() and 0xFF) xor generator.nextByte()
                append(value.toChar())
            }
        }
    }

    /** Upstream `_fixup_formats`: fill a missing vcodec from the URL. */
    private fun fixupFormats(formats: List<MediaFormat>): List<MediaFormat> =
        formats.map { format ->
            if (!format.vcodec.isNullOrEmpty()) return@map format
            val urls = listOfNotNull(format.url, format.manifestUrl)
            val vcodec = when {
                urls.any { ".av1." in it } -> "av1"
                urls.any { ".h264." in it } -> "h264"
                else -> null
            }
            if (vcodec == null) format else format.copy(vcodec = vcodec)
        }

    private fun hlsFormat(url: String, referer: String): MediaFormat = MediaFormat(
        formatId = "hls",
        url = url,
        ext = "mp4",
        protocol = "m3u8_native",
        httpHeaders = mapOf("referer" to referer),
    )

    private fun heightOf(quality: String?): Long? =
        Regex("^(\\d+)[pP]").find(quality ?: "")?.groupValues?.get(1)?.toLongOrNull()

    companion object {
        const val IE_KEY: String = "XHamster"

        const val DOMAINS: String =
            "(?:xhamster\\.(?:com|one|desi)|xhms\\.pro|xhamster\\d+\\.(?:com|desi)|" +
                "xhday\\.com|xhvid\\.com)"

        private const val HEX = "[0-9a-fA-F]{12,}"
        private val HEX_RE = Regex("^$HEX$")

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/?#]+\\.)?$DOMAINS/(?:" +
                "movies/(?<id>[\\dA-Za-z]+)/(?<displayId>[^/]*)\\.html|" +
                "videos/(?<displayId2>[^/]*)-(?<id2>[\\dA-Za-z]+))",
        )
    }
}

/** Upstream `XHamsterEmbedIE`: the xembed pages. */
class XHamsterEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        var videoUrl = ExtractorUtils.searchRegex(
            "href=\"(https?://xhamster\\.com/(?:movies/$videoId/[^\"]*\\.html|" +
                "videos/[^/]*-$videoId)[^\"]*)\"",
            webpage,
            default = null,
        )
        if (videoUrl == null) {
            val playerVars = extractJsonElement(webpage, Regex("vars\\s*:\\s*")) as? JsonObject
            videoUrl = playerVars?.str("downloadLink")
                ?: playerVars?.str("homepageLink")
                ?: playerVars?.str("commentsLink")
                ?: playerVars?.str("shareUrl")
        }
        val resolved = ExtractorUtils.urlOrNone(videoUrl)
            ?: throw ExtractionError.Unavailable("The xHamster embed had no video link.")
        return InfoDict(
            id = videoId,
            webpageUrl = url,
            redirectUrl = resolved,
            extractor = "xhamster",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "XHamsterEmbed"

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/?#]+\\.)?${XHamsterIE.DOMAINS}/xembed\\.php\\?video=(?<id>\\d+)",
        )
    }
}

/** Upstream `XHamsterUserIE`: the user and creator listings. */
class XHamsterUserIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val isUser = match.groups["user"]?.value != null
        val userId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val prefix = if (isUser) "users" else "creators"
        val suffix = if (isUser) "videos" else "exclusive"
        var nextPageUrl = "https://xhamster.com/$prefix/$userId/$suffix/1"
        val entries = mutableListOf<InfoEntry>()
        var pageNumber = 1
        var iterations = 0
        while (iterations < MAX_PAGES) {
            iterations++
            val page = try {
                http.downloadWebpage(nextPageUrl)
            } catch (error: ExtractionError) {
                break
            }
            for (tag in Regex(
                "(<a[^>]+class=[\"'].*?\\bvideo-thumb__image-container[^>]+>)",
            ).findAll(page)) {
                val href = Regex("href=\"([^\"]+)\"").find(tag.value)?.groupValues?.get(1) ?: continue
                val videoUrl = ExtractorUtils.urlOrNone(href) ?: continue
                if (!XHamsterIE.VALID_URL.containsMatchIn(videoUrl)) continue
                val videoId = XHamsterIE.VALID_URL.find(videoUrl)?.groups?.get("id")?.value
                    ?: XHamsterIE.VALID_URL.find(videoUrl)?.groups?.get("id2")?.value
                entries += InfoEntry(id = videoId, url = videoUrl)
            }
            val nextTag = Regex("<a[^>]+data-page=[\"']next[^>]+>").find(page)?.value ?: break
            val nextHref = Regex("href=\"([^\"]+)\"").find(nextTag)?.groupValues?.get(1) ?: break
            nextPageUrl = ExtractorUtils.urlOrNone(nextHref) ?: break
            pageNumber++
        }
        return InfoDict(
            id = userId,
            entries = entries.distinctBy { it.url },
            webpageUrl = url,
            extractor = "xhamster",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "XHamsterUser"
        private const val MAX_PAGES = 200

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/?#]+\\.)?${XHamsterIE.DOMAINS}/(?:(?<user>users)|creators)/(?<id>[^/?#&]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `_search_json` subset: balanced JSON after [marker]. */
private fun extractJsonElement(html: String, marker: Regex): JsonElement? {
    val match = marker.find(html) ?: return null
    var index = match.range.last + 1
    while (index < html.length && html[index].isWhitespace()) index++
    val opening = html.getOrNull(index) ?: return null
    if (opening != '{' && opening != '[') return null
    var depth = 0
    var inString = false
    var quote = ' '
    var escaped = false
    for (position in index until html.length) {
        val character = html[position]
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

            '{', '[' -> depth++
            '}', ']' -> {
                depth--
                if (depth == 0) {
                    return ExtractorUtils.parseJson(html.substring(index, position + 1))
                }
            }
        }
    }
    return null
}

private fun hexToBytes(value: String): ByteArray? {
    if (value.length % 2 != 0) return null
    val out = ByteArray(value.length / 2)
    for (index in out.indices) {
        val byte = value.substring(index * 2, index * 2 + 2).toIntOrNull(16) ?: return null
        out[index] = byte.toByte()
    }
    return out
}

private fun joinNonEmpty(vararg values: String?): String? =
    values.filterNotNull().filter { it.isNotEmpty() }.joinToString("-").ifEmpty { null }

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
