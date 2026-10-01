/*
 * RCS extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `rcs.py` from
 * `yt_dlp/extractor/rcs.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rcs.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `video.<cdn>/video-json/<id>` API, the `data-config`
 * and fragment-include page scans, the host migration map, the m3u8/https
 * and mp3 formats, and the iframe embed redirect. Manifest parsing is not
 * translated, so an m3u8 source yields one HLS row plus its https variant
 * instead of the per-variant rows upstream derives from the manifest; the
 * HEAD filesize probe is skipped. No cookie, token, or signed media URL is
 * stored here.
 */
package com.anydownload.core.extract.rcs

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val UUID_RE = "[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}"
private const val RCS_ID_RE = "[\\w-]+-\\d{10}"

/** Shared upstream `RCSBaseIE` behaviour. */
abstract class RcsBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected suspend fun extractVideo(url: String, cdn: String, rawId: String): InfoDict {
        var cdnName = cdn
        var videoId = rawId
        var displayId: String? = null
        var videoData: JsonObject? = null
        var jsonUrl = if (Regex(UUID_RE).matches(videoId) || Regex(RCS_ID_RE).matches(videoId)) {
            "https://video.$cdnName/video-json/$videoId"
        } else {
            null
        }
        var webpage: String? = null

        if (jsonUrl == null) {
            webpage = http.downloadWebpage(url)
            val dataConfig = findDataConfig(webpage)?.let { ExtractorUtils.unescapeHtml(it) ?: it }
            if (dataConfig != null) {
                val config = ExtractorUtils.parseJson(dataConfig) as? JsonObject ?: JsonObject(emptyMap())
                config.str("newspaper")?.let { cdnName = "$it.it" }
                displayId = videoId
                videoId = config.str("uuid") ?: videoId
                jsonUrl = "https://video.$cdnName/video-json/$videoId"
            } else {
                val fragment = Regex(
                    "url\\s*=\\s*([\"'])((?:https?:)?//video\\.rcs\\.it/fragment-includes/video-includes/[^\"']+?\\.json)\\1;",
                ).find(webpage)?.groupValues?.get(2)
                if (fragment != null) {
                    val normalized = fragment.replace("^//".toRegex(), "https://")
                    videoData = try {
                        http.downloadJson(normalized) as? JsonObject
                    } catch (error: ExtractionError) {
                        null
                    }
                    displayId = videoId
                    videoId = videoData?.str("id") ?: videoId
                }
            }
        }

        if (videoData == null) {
            val page = if (jsonUrl != null) http.downloadWebpage(jsonUrl) else webpage ?: http.downloadWebpage(url)
            videoData = ExtractorUtils.parseJson(between(page, "##start-video##", "##end-video##")) as? JsonObject
            if (videoData == null) {
                val embed = Regex(
                    "(?:data-frame-src=|<iframe[^\\n]+src=)([\"'])((?:https?:)?//video\\." +
                        "(?:rcs|(?:corriere\\w+\\.)?corriere|(?:gazzanet\\.)?gazzetta)\\.it/video-embed/.+?)\\1",
                ).find(page)?.groupValues?.get(2)
                if (embed != null) {
                    return InfoDict(
                        id = videoId,
                        redirectUrl = embed.replace("^//".toRegex(), "https://"),
                        webpageUrl = url,
                        extractor = "rcs:embed",
                        extractorKey = "RCSEmbeds",
                    )
                }
            }
        }

        if (videoData == null) {
            throw ExtractionError.Malformed("The RCS page had no video data.")
        }

        val sources = videoSources(videoData)
        val formats = mutableListOf<MediaFormat>()
        for (source in sources) {
            when (source.type) {
                "m3u8" -> {
                    formats += MediaFormat(
                        formatId = "hls",
                        url = source.url,
                        ext = "mp4",
                        protocol = "m3u8_native",
                    )
                    val httpUrl = Regex("(https?://[^/]+)/hls/([^?#]+?\\.mp4).+")
                        .find(source.url)?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
                    if (httpUrl != null && httpUrl != source.url) {
                        formats += MediaFormat(
                            formatId = "https",
                            url = httpUrl,
                            ext = "mp4",
                            protocol = "https",
                        )
                    }
                }

                "mp3" -> formats += MediaFormat(
                    formatId = "https-mp3",
                    url = source.url,
                    ext = "mp3",
                    acodec = "mp3",
                    vcodec = MediaFormat.CODEC_NONE,
                    abr = source.bitrate,
                )

                else -> formats += MediaFormat(
                    formatId = source.type,
                    url = source.url,
                    ext = source.type,
                )
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The RCS video data had no playable source.")
        }
        return InfoDict(
            id = videoId,
            title = videoData.str("title"),
            description = cleanHtml(videoData.str("description"))
                ?: cleanHtml(videoData.str("htmlDescription")),
            channel = videoData.str("provider") ?: cdnName,
            formats = formats,
            webpageUrl = url,
            extractor = "rcs",
            extractorKey = ieKey,
        )
    }

    private fun videoSources(videoData: JsonObject): List<Source> {
        val mediaFiles = videoData.obj("mediaProfile")?.array("mediaFile") ?: return emptyList()
        val out = mutableListOf<Source>()
        for (element in mediaFiles) {
            val source = element as? JsonObject ?: continue
            val mimeType = source.str("mimeType") ?: continue
            var sourceUrl = source.str("value") ?: continue
            for ((from, to) in URL_REPLACEMENTS) {
                sourceUrl = sourceUrl.replace(from, to)
            }
            var type = ExtractorUtils.mimetype2ext(mimeType)
            if (type == "m3u8" && sourceUrl.contains("-vh.akamaihd")) {
                val match = Regex("(?:https?:)?//([\\w.\\-]+)\\.net/i(.+)$").find(sourceUrl)
                val host = match?.groupValues?.get(1)
                if (host != null) {
                    val name = MIGRATION_MAP[host]
                    if (name != null) {
                        sourceUrl = "https://vod.rcsobjects.it/hls/$name${match.groupValues[2]}"
                    }
                }
            }
            val geoblocking = videoData.obj("mediaProfile")?.get("geoblocking") != null
            if (geoblocking || (type == "m3u8" && sourceUrl.contains("fcs.quotidiani_!"))) {
                sourceUrl = sourceUrl.replace("vod.rcsobjects", "vod-it.rcsobjects")
            }
            if (type == "m3u8" && sourceUrl.contains("vod")) {
                sourceUrl = sourceUrl.replace(".csmil", ".urlset")
            }
            if (type == "mp3") {
                sourceUrl = sourceUrl.replace("media2vam-corriere-it.akamaized.net", "vod.rcsobjects.it/corriere")
            }
            out += Source(type = type ?: "mp4", url = sourceUrl, bitrate = source.number("bitrate"))
        }
        return out
    }

    companion object {
        private val URL_REPLACEMENTS = listOf(
            "media2vam.corriere.it.edgesuite.net" to "media2vam-corriere-it.akamaized.net",
            "media.youreporter.it.edgesuite.net" to "media-youreporter-it.akamaized.net",
            "corrierepmd.corriere.it.edgesuite.net" to "corrierepmd-corriere-it.akamaized.net",
            "media2vam-corriere-it.akamaized.net/fcs.quotidiani/vr/videos/" to "video.corriere.it/vr360/videos/",
            "http://" to "https://",
        )

        private val MIGRATION_MAP = mapOf(
            "videoamica" to "amica",
            "media2-amica-it" to "amica",
            "corrierevam" to "corriere",
            "media2vam-corriere-it" to "corriere",
            "cormezzogiorno" to "corrieredelmezzogiorno",
            "media2vam-mezzogiorno-corriere-it" to "corrieredelmezzogiorno",
            "corveneto" to "corrieredelveneto",
            "media2vam-veneto-corriere-it" to "corrieredelveneto",
            "corbologna" to "corrieredibologna",
            "media2vam-bologna-corriere-it" to "corrieredibologna",
            "corfiorentino" to "corrierefiorentino",
            "media2vam-fiorentino-corriere-it" to "corrierefiorentino",
            "corinnovazione" to "corriereinnovazione",
            "media2-gazzanet-gazzetta-it" to "gazzanet",
            "videogazzanet" to "gazzanet",
            "videogazzaworld" to "gazzaworld",
            "gazzettavam" to "gazzetta",
            "media2vam-gazzetta-it" to "gazzetta",
            "videoiodonna" to "iodonna",
            "media2-leitv-it" to "leitv",
            "videoleitv" to "leitv",
            "videoliving" to "living",
            "media2-living-corriere-it" to "living",
            "media2-oggi-it" to "oggi",
            "videooggi" to "oggi",
            "media2-quimamme-it" to "quimamme",
            "quimamme" to "quimamme",
            "videorunning" to "running",
            "media2-style-corriere-it" to "style",
            "style" to "style",
            "videostyle" to "style",
            "media2-stylepiccoli-it" to "stylepiccoli",
            "stylepiccoli" to "stylepiccoli",
            "doveviaggi" to "viaggi",
            "media2-doveviaggi-it" to "viaggi",
            "media2-vivimilano-corriere-it" to "vivimilano",
            "vivimilano" to "vivimilano",
            "media2-youreporter-it" to "youreporter",
        )
    }
}

private data class Source(val type: String, val url: String, val bitrate: Double?)

/** Upstream `RCSEmbedsIE`: a `video-embed` URL. */
class RCSEmbedsIE(
    http: ExtractorHttp,
) : RcsBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val cdn = match.groups["cdn"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val id = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        return extractVideo(url, cdn, id)
    }

    companion object {
        const val IE_KEY: String = "RCSEmbeds"

        val VALID_URL: Regex = Regex(
            "https?://(?<vid>video)\\.(?<cdn>(?:rcs|(?:corriere\\w+\\.)?corriere|(?:gazzanet\\.)?gazzetta)\\.it)" +
                "/video-embed/(?<id>[^/=&\u0026?]+?)(?:$|\\?)",
        )

        /** Upstream `_extract_embed_urls`. */
        val EMBED_URL: Regex = Regex(
            "(?:data-frame-src=|<iframe[^\\n]+src=)([\"'])" +
                "((?:https?:)?//video\\.(?:rcs|(?:corriere\\w+\\.)?corriere|(?:gazzanet\\.)?gazzetta)\\.it/video-embed/.+?)\\1",
        )
    }
}

/** Upstream `RCSIE`: a Corriere/Gazzetta video page. */
class RCSIE(
    http: ExtractorHttp,
) : RcsBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val cdn = match.groups["cdn"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val id = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        return extractVideo(url, cdn, id)
    }

    companion object {
        const val IE_KEY: String = "RCS"

        val VALID_URL: Regex = Regex(
            "https?://(?<vid>video|viaggi)\\." +
                "(?<cdn>(?:(?:corrieredelmezzogiorno|corrieredelveneto|corrieredibologna|corrierefiorentino)\\.)?" +
                "corriere\\.it|(?:gazzanet\\.)?gazzetta\\.it)" +
                "/(?!video-embed/)[^?#]+?/(?<id>[^/?]+)(?=\\?|/$|$)",
        )
    }
}

/** Upstream `RCSVariousIE`: a Leitv/Youreporter/Amica page. */
class RCSVariousIE(
    http: ExtractorHttp,
) : RcsBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val cdn = match.groups["cdn"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val id = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        return extractVideo(url, cdn, id)
    }

    companion object {
        const val IE_KEY: String = "RCSVarious"

        val VALID_URL: Regex = Regex(
            "https?://www\\.(?<cdn>leitv\\.it|youreporter\\.it|amica\\.it)/(?:[^/]+/)?(?<id>[^/]+?)(?:$|\\?|/)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun findDataConfig(webpage: String): String? {
    val marker = webpage.indexOf("divVideoPlayer")
    if (marker < 0) return null
    val start = webpage.lastIndexOf('<', marker)
    val end = webpage.indexOf('>', marker)
    if (start < 0 || end < 0) return null
    val tag = webpage.substring(start, end + 1)
    val match = Regex("data-config\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')").find(tag) ?: return null
    return match.groupValues[1].ifEmpty { match.groupValues[2] }
}

private fun between(value: String, startMarker: String, endMarker: String): String? {
    val start = value.indexOf(startMarker)
    if (start < 0) return null
    val end = value.indexOf(endMarker, start + startMarker.length)
    if (end < 0) return null
    return value.substring(start + startMarker.length, end).trim()
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
