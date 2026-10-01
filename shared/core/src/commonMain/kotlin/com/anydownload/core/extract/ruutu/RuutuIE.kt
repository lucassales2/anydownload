/*
 * Ruutu extractor — AnyDownload
 *
 * Kotlin translation of the public API subset of `ruutu.py` from
 * `yt_dlp/extractor/ruutu.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `ruutu.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the `media-xml-cache` XML (the File/AudioMediaFile walk, the
 * auth/access lookup, m3u8 and audio rows, the resolution/bitrate/label
 * fields), the PassthroughVariables metadata, and the DRM/non-free typed
 * failures. Upstream marks the class `_WORKING = False`. f4m and mpd rows are
 * skipped (no f4m helper and the upstream mpd skip), the `_is_valid_url`
 * probe is not translated, the embed-url classmethod is not translated, and
 * the port does not carry categories, season/episode numbers, or the rtmp
 * preference. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.ruutu

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.Thumbnail

private const val API_BASE = "https://gatling.nelonemedia.fi"

/** Upstream `RuutuIE`: a ruutu.fi/supla.fi video or audio page. */
class RuutuIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoXml = http.downloadWebpage("$API_BASE/media-xml-cache?id=$videoId")

        val formats = mutableListOf<MediaFormat>()
        val processedUrls = mutableSetOf<String>()
        for (match in Regex("<(\\w*File)([^>]*)>([^<]*)</\\1>").findAll(videoXml)) {
            val tag = match.groupValues[1]
            val attributes = tagAttributes(match.groupValues[2])
            var videoUrl = match.groupValues[3].trim()
            if (videoUrl.isEmpty() || videoUrl in processedUrls) continue
            if ("NOT_USED" in videoUrl || "NOT-USED" in videoUrl) continue
            processedUrls += videoUrl
            val ext = ExtractorUtils.determineExt(videoUrl)
            val authUrl = try {
                http.downloadWebpage(
                    "$API_BASE/auth/access/v2?stream=" + percentEncode(videoUrl),
                ).trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
            } catch (error: Exception) {
                null
            }
            if (authUrl != null) {
                processedUrls += authUrl
                videoUrl = authUrl
            }
            when {
                ext == "m3u8" -> formats += MediaFormat(
                    formatId = "hls",
                    url = videoUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                ext == "f4m" -> Unit // No f4m helper in the port.
                ext == "mpd" -> Unit // Upstream skips DASH (out-of-sync durations).
                ext == "mp3" || tag == "AudioMediaFile" -> formats += MediaFormat(
                    formatId = "audio",
                    url = videoUrl,
                    vcodec = MediaFormat.CODEC_NONE,
                )

                else -> {
                    val proto = videoUrl.substringBefore("://", "")
                    if (!tag.startsWith("HTTP") && proto != "rtmp") continue
                    val label = attributes["label"]
                    val tbr = attributes["bitrate"]?.toDoubleOrNull()
                    val resolution = attributes["resolution"]?.split('x')
                    formats += MediaFormat(
                        formatId = if (label != null || tbr != null) {
                            "$proto-${label ?: tbr?.toLong()}"
                        } else {
                            proto
                        },
                        url = videoUrl,
                        width = resolution?.getOrNull(0)?.toLongOrNull(),
                        height = resolution?.getOrNull(1)?.toLongOrNull(),
                        tbr = tbr,
                        preference = if (proto == "rtmp") -1 else 1,
                    )
                }
            }
        }

        if (formats.isEmpty()) {
            if (Regex("<DRM\\b[^>]*>").containsMatchIn(videoXml)) {
                throw ExtractionError.Unavailable("The video is DRM protected.")
            }
            val nsStCds = passthroughVariable(videoXml, "ns_st_cds")
            if (nsStCds != null && nsStCds != "free") {
                throw ExtractionError.Unavailable("This video is $nsStCds.")
            }
        }

        val program = Regex("<Program\\b[^>]*>").find(videoXml)?.value.orEmpty()
        val programAttrs = tagAttributes(program)
        return InfoDict(
            id = videoId,
            title = programAttrs["program_name"],
            description = programAttrs["description"],
            duration = xmlText(videoXml, "Runtime")?.toDoubleOrNull()
                ?: passthroughVariable(videoXml, "runtime")?.toDoubleOrNull(),
            uploadDate = passthroughVariable(videoXml, "date_start")?.let(ExtractorUtils::unifiedStrdate),
            ageLimit = xmlText(videoXml, "AgeLimit")?.trim()?.toIntOrNull(),
            channel = passthroughVariable(videoXml, "series_name"),
            thumbnails = listOfNotNull(
                Regex("<Startpicture\\b[^>]*\\bhref=\"([^\"]*)\"").find(videoXml)
                    ?.groupValues?.get(1)?.let { Thumbnail(url = it) },
            ),
            formats = formats,
            webpageUrl = url,
            extractor = "ruutu",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Ruutu"

        val VALID_URL: Regex = Regex(
            "https?://(?:" +
                "(?:www\\.)?(?:ruutu|supla)\\.fi/(?:video|supla|audio)/|" +
                "static\\.nelonenmedia\\.fi/player/misc/embed_player\\.html\\?.*?\\bnid=" +
                ")(?<id>\\d+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `find_xpath_attr(... 'name', name).get('value')`, `NA` means absent. */
private fun passthroughVariable(xml: String, name: String): String? {
    for (match in Regex("<variable\\b[^>]*/?>").findAll(xml)) {
        val attributes = tagAttributes(match.value)
        if (attributes["name"] == name) {
            val value = attributes["value"] ?: return null
            return if (value == "NA") null else value
        }
    }
    return null
}

/** The text of the first `tag` element. */
private fun xmlText(xml: String, tag: String): String? =
    Regex("<$tag\\b[^>]*>([^<]*)</$tag>").find(xml)?.groupValues?.get(1)

private val ATTRIBUTE = Regex(
    "([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))",
)

private fun tagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        val value = match.groupValues[2].ifEmpty { match.groupValues[3] }.ifEmpty { match.groupValues[4] }
        out[match.groupValues[1].lowercase()] = value
    }
    return out
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
