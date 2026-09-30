/*
 * archive.org extractor — AnyDownload
 *
 * Kotlin translation of `ArchiveOrgIE` from `yt_dlp/extractor/archiveorg.py`
 * at upstream tag `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf),
 * read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `archiveorg.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `archive.org/details/<identifier>` and `/embed/<identifier>`, with an
 * optional `/<entry>` suffix. The embeddable player's `<play-av playlist=…>`
 * JSON demarks the playlist entries and subtitle tracks; the public
 * `metadata/<identifier>` API supplies the item metadata, files, thumbnails,
 * and formats. A single entry becomes the main info dict with `formats`; a
 * multi-entry item becomes bounded child-job entries addressed by their
 * `/details/<identifier>/<entry>` URLs. Unknown extensions and private files
 * (no archive.org sign-in) are skipped.
 *
 * Not translated: the account-only private files, the `YoutubeWebArchiveIE`
 * CDX/capture-date class (planned under T-124), reviews, and the `MixchArchive`
 * host. A login wall or DRM wall is Partial. No cookie or bearer token is
 * stored or committed; fixture hosts are `*.example`.
 */
package com.anydownlod.core.extract.archiveorg

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoEntry
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.SubtitleFormat
import com.anydownlod.core.extract.SubtitleTrack
import com.anydownlod.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `ArchiveOrgIE`: one item from the embed player and metadata API. */
class ArchiveOrgIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "archive.org"

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = percentDecode(match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl())
        val identifier = videoId.substringBefore('/')
        val entryId = videoId.substringAfter('/', "")

        val embedPage = http.downloadWebpage("https://archive.org/embed/$identifier")
        val playlist = playlistData(embedPage)
        val metadata = http.downloadJson("https://archive.org/metadata/$identifier") as? JsonObject
            ?: throw ExtractionError.Malformed("The archive.org metadata response was empty.")
        val meta = metadata.obj("metadata")
            ?: throw ExtractionError.Malformed("The archive.org item had no metadata.")
        val resolvedId = meta.str("identifier") ?: identifier

        val entries = linkedMapOf<String, ItemEntry>()
        for (element in playlist) {
            val node = element as? JsonObject ?: continue
            val original = node.str("orig") ?: continue
            if (entryId.isNotEmpty() && original != entryId) continue
            val entry = ItemEntry(title = node.str("title"))
            for (trackElement in node.array("tracks").orEmpty()) {
                val track = trackElement as? JsonObject ?: continue
                if (track.str("kind") != "subtitles") continue
                val file = track.str("file") ?: continue
                entry.subtitles += SubtitleTrack(
                    language = track.str("label") ?: "en",
                    formats = listOf(
                        SubtitleFormat(
                            ext = if (file.endsWith(".srt", ignoreCase = true)) "srt" else "vtt",
                            url = "https://archive.org/" + file.trimStart('/'),
                        ),
                    ),
                )
            }
            entries[original] = entry
        }
        // A direct `/details/<id>/<entry>` URL may not appear in the player data.
        if (entries.isEmpty() && entryId.isNotEmpty()) entries[entryId] = ItemEntry()

        for (element in metadata.array("files").orEmpty()) {
            val file = element as? JsonObject ?: continue
            val name = file.str("name") ?: continue
            val entry = entries[name] ?: entries[file.str("original") ?: ""]
            val fileUrl = "https://archive.org/download/$resolvedId/" + quoteSegment(name)

            if (file.str("format") == "Thumbnail") {
                (entry ?: entries.values.firstOrNull())?.thumbnails?.add(
                    Thumbnail(
                        url = fileUrl,
                        id = name,
                        width = file.number("width")?.toLong(),
                        height = file.number("height")?.toLong(),
                    ),
                )
                continue
            }
            val target = entry ?: continue
            val ext = ExtractorUtils.determineExt(name)
            if (ext == "unknown_video") continue
            if ((file["private"] as? JsonPrimitive)?.content == "true") continue
            target.formats += MediaFormat(
                formatId = file.str("format")?.replace(' ', '_'),
                url = fileUrl,
                ext = ext,
                width = file.number("width")?.toLong(),
                height = file.number("height")?.toLong(),
                filesize = file.number("size")?.toLong(),
                protocol = "https",
                sourcePreference = if (file.str("source") == "original") 0 else -1,
                formatNote = file.str("source"),
            )
        }

        val usable = entries.filterValues { it.formats.isNotEmpty() || it.subtitles.isNotEmpty() }
        if (usable.isEmpty()) throw ExtractionError.NoFormats("The archive.org item has no downloadable file.")

        val base = InfoDict(
            id = resolvedId,
            title = meta.str("title"),
            description = descriptionOf(meta),
            uploader = meta.str("uploader") ?: meta.str("adder"),
            uploadDate = (meta.str("publicdate") ?: meta.str("addeddate"))?.let(ExtractorUtils::unifiedStrdate),
            webpageUrl = "https://archive.org/details/$resolvedId",
            extractor = "archive",
            extractorKey = IE_KEY,
        )

        if (usable.size == 1) {
            val entry = usable.values.first()
            return base.copy(
                title = base.title ?: entry.title,
                formats = entry.formats,
                subtitles = entry.subtitles,
                thumbnails = entry.thumbnails,
            )
        }
        return base.copy(
            entries = usable.map { (original, entry) ->
                InfoEntry(
                    id = "$resolvedId/$original",
                    title = entry.title ?: original,
                    url = "https://archive.org/details/$resolvedId/$original",
                )
            },
        )
    }

    companion object {
        const val IE_KEY: String = "ArchiveOrg"

        /** Upstream `_VALID_URL`. */
        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?archive\\.org/(?:details|embed)/(?<id>[^?#]+)(?:[?].*)?$",
        )
    }
}

private class ItemEntry(
    val title: String? = null,
    val formats: MutableList<MediaFormat> = mutableListOf(),
    val thumbnails: MutableList<Thumbnail> = mutableListOf(),
    val subtitles: MutableList<SubtitleTrack> = mutableListOf(),
)

private val PLAY_AV = Regex(
    "<play-av\\b[^>]*\\bplaylist=([\"'])(.*?)\\1",
    RegexOption.DOT_MATCHES_ALL,
)

/** Upstream `_playlist_data`: the `<play-av playlist=…>` JSON attribute. */
private fun playlistData(webpage: String): List<JsonElement> {
    val raw = PLAY_AV.find(webpage)?.groupValues?.get(2) ?: return emptyList()
    val decoded = ExtractorUtils.unescapeHtml(raw) ?: raw
    return (ExtractorUtils.parseJson(decoded) as? JsonArray).orEmpty()
}

private fun descriptionOf(meta: JsonObject): String? {
    val raw = when (val element = meta["description"]) {
        is JsonPrimitive -> element.content
        is JsonArray -> element.filterIsInstance<JsonPrimitive>().joinToString(" ") { it.content }
        else -> null
    } ?: return null
    val withoutTags = Regex("<[^>]*>").replace(raw, " ")
    return (ExtractorUtils.unescapeHtml(withoutTags) ?: withoutTags)
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun quoteSegment(value: String): String {
    val out = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xff
        val character = code.toChar()
        when {
            character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' ||
                character in "-_.~/" -> out.append(character)

            else -> {
                val hex = "0123456789ABCDEF"
                out.append('%').append(hex[code ushr 4]).append(hex[code and 0x0f])
            }
        }
    }
    return out.toString()
}

private fun percentDecode(value: String): String {
    val out = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%' && index + 2 < value.length) {
            val code = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (code != null) {
                out.append(code.toChar())
                index += 3
                continue
            }
        }
        out.append(if (character == '+') ' ' else character)
        index++
    }
    return out.toString()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
