/*
 * Rozhlas extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `rozhlas.py` from
 * `yt_dlp/extractor/rozhlas.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `rozhlas.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the prehravac page scan, the mujRozhlasPlayer data-player JSON, and
 * the public api.mujrozhlas.cz episode/show/serial endpoints with their
 * paged episode links (five pages eagerly). MPEG-DASH links are skipped and
 * the artist/chapter fields the port does not carry are dropped. No cookie,
 * token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.rozhlas

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MAX_PAGES = 5

/** Upstream `RozhlasIE`: a prehravac audio page. */
class RozhlasIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val audioId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage("http://prehravac.rozhlas.cz/audio/$audioId")
        val title = Regex(
            "<h3>(.+?)</h3>\\s*<p[^>]*>.*?</p>\\s*<div[^>]+id=[\"']player-track",
        ).find(webpage)?.groupValues?.get(1)
            ?: metaContent(webpage, "og:title")?.removePrefix("Radio Wave - ")
        val description = Regex(
            "<p[^>]+title=([\"'])((?:(?!\\1).)+)\\1[^>]*>.*?</p>\\s*<div[^>]+id=[\"']player-track",
        ).find(webpage)?.groupValues?.get(2)
        val duration = Regex("data-duration=[\"'](\\d+)").find(webpage)?.groupValues?.get(1)
            ?.toDoubleOrNull()
        return InfoDict(
            id = audioId,
            title = title,
            description = description,
            duration = duration,
            formats = listOf(
                MediaFormat(
                    url = "http://media.rozhlas.cz/_audio/$audioId.mp3",
                    ext = "mp3",
                    vcodec = MediaFormat.CODEC_NONE,
                ),
            ),
            webpageUrl = url,
            extractor = "rozhlas",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Rozhlas"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?prehravac\\.rozhlas\\.cz/audio/(?<id>[0-9]+)")
    }
}

/** Upstream `RozhlasVltavaIE`: a Vltava/Radio Wave article. */
class RozhlasVltavaIE(
    http: ExtractorHttp,
) : RozhlasBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val playerTag = Regex("(<div class=\"mujRozhlasPlayer\" data-player='[^']+'>)").find(webpage)
            ?: throw ExtractionError.Malformed("The Rozhlas page had no player element.")
        val dataPlayer = tagAttributes(playerTag.value)["data-player"]
            ?: throw ExtractionError.Malformed("The Rozhlas player element had no data-player.")
        val data = (ExtractorUtils.parseJson(dataPlayer) as? JsonObject)?.obj("data")
            ?: throw ExtractionError.Malformed("The Rozhlas player data was not JSON.")
        val entries = mutableListOf<InfoEntry>()
        for (element in data.array("playlist").orEmpty()) {
            val entry = element as? JsonObject ?: continue
            val info = extractVideo(entry)
            entries += InfoEntry(id = info.id, title = info.title, url = url)
        }
        return InfoDict(
            id = data.primitive("embedId") ?: videoId,
            title = data.obj("series")?.str("title"),
            entries = entries,
            webpageUrl = url,
            extractor = "rozhlas:vltava",
            extractorKey = IE_KEY,
        )
    }

    private fun extractVideo(entry: JsonObject): InfoDict {
        val ga = entry.obj("meta")?.obj("ga")
        val audioId = ga?.primitive("contentId")
            ?: throw ExtractionError.Malformed("The Rozhlas playlist entry had no audio id.")
        return InfoDict(
            id = audioId,
            title = ga.str("contentName"),
            description = entry.str("title"),
            duration = entry.number("duration"),
            formats = extractFormats(entry, audioId),
            extractor = "rozhlas:vltava",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "RozhlasVltava"

        val VALID_URL: Regex = Regex(
            "https?://(?:\\w+\\.rozhlas|english\\.radio)\\.cz/[\\w-]+-(?<id>\\d+)",
        )
    }
}

/** Upstream `MujRozhlasIE`: a mujrozhlas.cz episode, show, or serial. */
class MujRozhlasIE(
    http: ExtractorHttp,
) : RozhlasBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val marker = Regex("\\bvar\\s+dl\\s*=").find(webpage)
            ?: throw ExtractionError.Malformed("The mujrozhlas page had no info JSON.")
        val info = balancedAfter(webpage, marker.range.last + 1)
            ?.let { ExtractorUtils.parseJson(it) as? JsonObject }
            ?: throw ExtractionError.Malformed("The mujrozhlas info JSON was not an object.")
        val entity = info.str("siteEntityBundle") ?: "episode"
        if (entity == "episode" || entity == "serialPart") {
            val contentId = info.primitive("contentId")
                ?: throw ExtractionError.Malformed("The mujrozhlas info had no content id.")
            return extractAudioEntry(callApi("episodes", contentId)).copy(
                webpageUrl = url,
                extractorKey = IE_KEY,
            )
        }
        if (entity == "show" || entity == "serial") {
            val playlistId = if (entity == "show") {
                info.str("contentShow")?.substringBefore(':')
            } else {
                info.primitive("contentId")
            } ?: throw ExtractionError.Malformed("The mujrozhlas info had no playlist id.")
            val data = callApi("${entity}s", playlistId)
            val apiUrl = data.obj("relationships")?.obj("episodes")?.obj("links")?.str("related")
                ?: throw ExtractionError.Malformed("The mujrozhlas listing had no episodes link.")
            return InfoDict(
                id = playlistId,
                title = data.obj("attributes")?.str("title"),
                description = data.obj("attributes")?.str("description"),
                entries = entries(apiUrl, playlistId),
                webpageUrl = url,
                extractor = "rozhlas:mujrozhlas",
                extractorKey = IE_KEY,
            )
        }
        throw ExtractionError.Unavailable("Unsupported entity type \"$entity\"")
    }

    private suspend fun callApi(path: String, itemId: String): JsonObject {
        val response = http.downloadJson("https://api.mujrozhlas.cz/$path/$itemId") as? JsonObject
            ?: throw ExtractionError.Malformed("The mujrozhlas API returned no object.")
        return response.obj("data")
            ?: throw ExtractionError.Malformed("The mujrozhlas API returned no data.")
    }

    private fun extractAudioEntry(entry: JsonObject): InfoDict {
        val attributes = entry.obj("attributes")
        val ga = entry.obj("meta")?.obj("ga")
        val audioId = ga?.primitive("contentId")
            ?: throw ExtractionError.Malformed("The mujrozhlas episode had no audio id.")
        return InfoDict(
            id = audioId,
            title = attributes?.str("title"),
            description = attributes?.str("description"),
            uploadDate = ExtractorUtils.unifiedStrdate(attributes?.str("since")),
            formats = extractFormats(attributes ?: JsonObject(emptyMap()), audioId),
            extractor = "rozhlas:mujrozhlas",
            extractorKey = IE_KEY,
        )
    }

    private suspend fun entries(apiUrl: String, playlistId: String): List<InfoEntry> {
        val out = mutableListOf<InfoEntry>()
        var next: String? = apiUrl
        var page = 0
        while (next != null && page < MAX_PAGES) {
            val episodes = try {
                http.downloadJson(next) as? JsonObject
            } catch (error: Exception) {
                break
            } ?: break
            for (element in episodes.array("data").orEmpty()) {
                val episode = element as? JsonObject ?: continue
                val audioId = episode.obj("meta")?.obj("ga")?.primitive("contentId") ?: continue
                out += InfoEntry(
                    id = audioId,
                    title = episode.obj("attributes")?.str("title"),
                    url = "https://www.mujrozhlas.cz/episodes/$audioId",
                )
            }
            next = episodes.obj("links")?.str("next")
            page++
        }
        return out
    }

    companion object {
        const val IE_KEY: String = "MujRozhlas"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?mujrozhlas\\.cz/(?:[^/]+/)*(?<id>[^/?#\u0026]+)",
        )
    }
}

/** Shared upstream `RozhlasBaseIE` format extraction. */
abstract class RozhlasBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_extract_formats`; DASH links are skipped. */
    protected fun extractFormats(entry: JsonObject, audioId: String): List<MediaFormat> {
        val formats = mutableListOf<MediaFormat>()
        for (element in entry.array("audioLinks").orEmpty()) {
            val audio = element as? JsonObject ?: continue
            val audioUrl = audio.str("url") ?: continue
            val variant = audio.str("variant")
            when (variant) {
                "dash" -> Unit // MPEG-DASH manifests are not translated.
                "hls" -> formats += MediaFormat(
                    formatId = variant,
                    url = audioUrl,
                    ext = "m4a",
                    protocol = "m3u8_native",
                    vcodec = MediaFormat.CODEC_NONE,
                )

                else -> formats += MediaFormat(
                    formatId = variant,
                    url = audioUrl,
                    ext = variant,
                    abr = audio.number("bitrate"),
                    acodec = variant,
                    vcodec = MediaFormat.CODEC_NONE,
                )
            }
        }
        return formats
    }
}

// ------------------------------------------------------------------ helpers

private val ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

private fun tagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        out[match.groupValues[1].lowercase()] = match.groupValues[2].ifEmpty { match.groupValues[3] }
    }
    return out
}

private fun metaContent(webpage: String, property: String): String? {
    val name = Regex.escape(property)
    val patterns = listOf(
        "<meta[^>]+(?:property|name)\\s*=\\s*[\"']$name[\"'][^>]+content\\s*=\\s*[\"']([^\"']*)[\"']",
        "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]+(?:property|name)\\s*=\\s*[\"']$name[\"']",
    )
    for (pattern in patterns) {
        Regex(pattern).find(webpage)?.let { return it.groupValues[1].ifBlank { null } }
    }
    return null
}

private fun balancedAfter(html: String, start: Int): String? {
    val open = html.indexOf('{', start)
    if (open < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    var i = open
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
                    if (depth == 0) return html.substring(open, i + 1)
                }
            }
        }
        i++
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
