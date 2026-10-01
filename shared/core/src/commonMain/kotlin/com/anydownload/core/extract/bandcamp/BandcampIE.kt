/*
 * Bandcamp extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `bandcamp.py` from
 * `yt_dlp/extractor/bandcamp.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `bandcamp.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the track pages (the `data-tralbum` trackinfo file map, the
 * `data-embed`/albumTitle metadata), the album track lists, the radio show
 * player data, and the user discography scans. The free-download
 * statdownload flow is not translated (Bandcamp download limits); the
 * track/album/artist/release-date fields the port does not carry are
 * dropped. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.bandcamp

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

/** Upstream `BandcampIE`: a single track. */
class BandcampIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val uploader = match.groups["uploader"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val title = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val tralbum = dataAttribute(webpage, "tralbum")
            ?: throw ExtractionError.Malformed("The page had no tralbum data.")
        val trackInfo = tralbum.array("trackinfo")?.firstOrNull() as? JsonObject
        val formats = mutableListOf<MediaFormat>()
        if (trackInfo != null) {
            val file = trackInfo["file"]
            if (file is JsonObject) {
                for ((formatId, value) in file) {
                    val formatUrl = (value as? JsonPrimitive)?.content
                        ?.takeIf { it.startsWith("//") || it.startsWith("http") }
                        ?: continue
                    val ext = formatId.substringBefore('-', formatId)
                    val abr = formatId.substringAfter('-', "").toLongOrNull()
                    formats += MediaFormat(
                        formatId = formatId,
                        url = protoRelative(formatUrl),
                        ext = ext,
                        vcodec = MediaFormat.CODEC_NONE,
                        acodec = ext,
                        abr = abr?.toDouble(),
                    )
                }
            }
        }
        val embed = dataAttribute(webpage, "embed", fatal = false)
        val current = tralbum.obj("current") ?: JsonObject(emptyMap())
        val artist = embed?.str("artist") ?: current.str("artist") ?: tralbum.str("artist")
        val track = trackInfo?.str("title")
        val trackId = trackInfo?.let { info ->
            info.primitiveText("track_id") ?: info.primitiveText("id")
        } ?: tralbum.primitiveText("id")
        val duration = trackInfo?.number("duration")
            ?: ExtractorUtils.htmlSearchMeta(webpage, "duration")?.toDoubleOrNull()
        val uploadDate = ExtractorUtils.unifiedStrdate(
            current.str("publish_date") ?: tralbum.str("album_publish_date"),
        )
        return InfoDict(
            id = trackId,
            title = if (artist != null) "$artist - $track" else track,
            description = current.str("about"),
            duration = duration,
            uploader = artist,
            uploadDate = uploadDate,
            thumbnails = listOfNotNull(
                ExtractorUtils.htmlSearchMeta(webpage, "og:image")?.let { Thumbnail(url = it) },
            ),
            formats = formats,
            webpageUrl = url,
            extractor = "bandcamp",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Bandcamp"

        val VALID_URL: Regex = Regex("https?://(?<uploader>[^/]+)\\.bandcamp\\.com/track/(?<id>[^/?#&]+)")
    }
}

/** Upstream `BandcampAlbumIE`: an album's track list. */
class BandcampAlbumIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !BandcampWeeklyIE.VALID_URL.containsMatchIn(url) &&
            !BandcampIE.VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val uploader = match.groups["subdomain"]?.value
        val albumId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = albumId
        val webpage = http.downloadWebpage(url)
        val tralbum = dataAttribute(webpage, "tralbum")
            ?: throw ExtractionError.Malformed("The page had no tralbum data.")
        val trackInfo = tralbum.array("trackinfo")
            ?: throw ExtractionError.Malformed("The page doesn't contain any tracks.")
        val entries = trackInfo.mapNotNull { element ->
            val track = element as? JsonObject ?: return@mapNotNull null
            if (track.number("duration") == null) return@mapNotNull null
            val titleLink = track.str("title_link") ?: return@mapNotNull null
            InfoEntry(
                id = track.primitiveText("track_id") ?: track.primitiveText("id"),
                title = track.str("title"),
                url = urlJoin(url, titleLink),
            )
        }
        val current = tralbum.obj("current") ?: JsonObject(emptyMap())
        return InfoDict(
            id = playlistId,
            title = current.str("title"),
            description = current.str("about"),
            entries = entries,
            webpageUrl = url,
            extractor = "Bandcamp:album",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BandcampAlbum"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?<subdomain>[^.]+)\\.)?bandcamp\\.com/album/(?<id>[^/?#&]+)",
        )
    }
}

/** Upstream `BandcampWeeklyIE`: the radio shows. */
class BandcampWeeklyIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val showId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = http.downloadJson(
            "https://bandcamp.com/api/player/2/player_data_web",
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"item_id": $showId, "item_type": "radio"}""".encodeToByteArray(),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The radio show API was not an object.")
        val showData = data.obj("tracklist") ?: throw ExtractionError.Malformed("The radio show had no tracklist.")
        val audioData = showData.obj("compiledTrack")
            ?: throw ExtractionError.Malformed("The radio show had no compiled track.")
        val streamUrl = audioData.str("streamUrl")
            ?: throw ExtractionError.NoFormats("The radio show had no stream URL.")
        val enc = queryParam(streamUrl, "enc")
        val encoding = enc?.substringBefore('-')
        val bitrate = enc?.substringAfter('-', "")?.toLongOrNull()
        val seriesTitle = showData.str("subtitle")
        val releaseDate = ExtractorUtils.unifiedStrdate(showData.str("date"))
        val title = if (seriesTitle != null && releaseDate != null) {
            "$seriesTitle, ${releaseDate.substring(0, 4)}-${releaseDate.substring(4, 6)}-${releaseDate.substring(6, 8)}"
        } else {
            seriesTitle
        }
        return InfoDict(
            id = showId,
            title = title,
            description = showData.str("description"),
            duration = audioData.number("duration"),
            uploadDate = releaseDate,
            thumbnails = listOfNotNull(
                showData.str("imageId")?.let { Thumbnail(url = "https://f4.bcbits.com/img/${it}_0.jpg") },
            ),
            formats = listOf(
                MediaFormat(
                    url = streamUrl,
                    formatId = enc,
                    ext = encoding ?: "mp3",
                    acodec = encoding,
                    vcodec = MediaFormat.CODEC_NONE,
                    abr = bitrate?.toDouble(),
                ),
            ),
            webpageUrl = url,
            extractor = "Bandcamp:weekly",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BandcampWeekly"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?bandcamp\\.com/radio/?\\?(?:[^#]+&)?show=(?<id>\\d+)",
        )
    }
}

/** Upstream `BandcampUserIE`: a user's discography page. */
class BandcampUserIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val uploader = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val entries = mutableListOf<InfoEntry>()
        val itemHrefs = Regex("<li data-item-id=[\"'][^>]+>\\s*<a href=[\"'](?![^\"'/]*?/merch)([^\"']+)")
            .findAll(webpage).map { it.groupValues[1] }.toList()
        val trackTitles = if (itemHrefs.isEmpty()) {
            Regex("<div[^>]+trackTitle[\"'][^\"']+[\"']([^\"']+)")
                .findAll(webpage).map { it.groupValues[1] }.toList()
        } else {
            emptyList()
        }
        for (href in itemHrefs + trackTitles) {
            entries += InfoEntry(url = urlJoin(url, href))
        }
        val musicGrid = Regex("<div[^>]+id=[\"']music-grid[\"'][^>]*>").find(webpage)
        if (musicGrid != null) {
            val attributes = tagAttributes(musicGrid.value)
            val clientItems = attributes["data-client-items"]
            if (clientItems != null) {
                val parsed = ExtractorUtils.parseJson(ExtractorUtils.unescapeHtml(clientItems))
                for (element in (parsed as? JsonArray).orEmpty()) {
                    val pageUrl = (element as? JsonObject)?.str("page_url") ?: continue
                    entries += InfoEntry(url = urlJoin(url, pageUrl))
                }
            }
        }
        return InfoDict(
            id = uploader,
            title = "Discography of $uploader",
            entries = entries,
            webpageUrl = url,
            extractor = "Bandcamp:user",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BandcampUser"

        val VALID_URL: Regex = Regex(
            "https?://(?!www\\.)(?<id>[^.]+)\\.bandcamp\\.com(?:/music)?/?(?:[#?]|$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

private val DATA_ATTR = Regex("data-(?<name>[\\w-]+)=([\"'])(?<json>\\{.+?\\})\\2", RegexOption.DOT_MATCHES_ALL)

private fun dataAttribute(webpage: String, name: String, fatal: Boolean = true): JsonObject? {
    val match = DATA_ATTR.findAll(webpage).firstOrNull { it.groups["name"]?.value == name }
        ?: return if (fatal) null else null
    val parsed = ExtractorUtils.parseJson(match.groups["json"]?.value)
    return parsed as? JsonObject
}

private fun protoRelative(url: String): String =
    if (url.startsWith("//")) "https:$url" else url

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    val origin = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: return href
    return if (href.startsWith("/")) origin + href else "$origin/$href"
}

private fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    return query.split('&').firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")
}

private val ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

private fun tagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in ATTRIBUTE.findAll(tag)) {
        out[match.groupValues[1].lowercase()] = match.groupValues[2].ifEmpty { match.groupValues[3] }
    }
    return out
}

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
