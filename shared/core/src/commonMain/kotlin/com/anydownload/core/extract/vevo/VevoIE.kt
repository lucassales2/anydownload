/*
 * Vevo extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `vevo.py` from
 * `yt_dlp/extractor/vevo.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `vevo.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the anonymous token endpoint (the public client id upstream
 * carries; the legacy token is fetched at runtime and never stored), the
 * apiv2 video/streams calls (HLS and direct rows; ISM/MPD skipped), and the
 * playlist page initial-store scan. The port does not carry track, artist,
 * or genre fields, so they are dropped. No cookie, user token, or private
 * URL is stored here.
 */
package com.anydownload.core.extract.vevo

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

private const val ANONYMOUS_CLIENT_ID = "SPupX1tvqFEopQ1YS6SS"

/** Upstream `VevoIE`: a Vevo video or `vevo:` pseudo-URL. */
class VevoIE(
    http: ExtractorHttp,
) : VevoBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    private var apiUrlTemplate: String? = null

    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        initializeApi(videoId)
        val videoInfo = callApiObject("video/$videoId", videoId)
            ?: throw ExtractionError.Malformed("Failed to download video info.")
        val videoVersions = callApiArray("video/$videoId/streams", videoId)
            ?: throw ExtractionError.Malformed("Failed to download video versions info.")
        val formats = mutableListOf<MediaFormat>()
        for (element in videoVersions) {
            val version = element as? JsonObject ?: continue
            val versionUrl = version.str("url") ?: continue
            if (".ism" in versionUrl) continue
            val versionName = VERSIONS[version.number("version")?.toInt()] ?: "generic"
            when {
                ".mpd" in versionUrl -> Unit // MPEG-DASH manifests are not translated.
                ".m3u8" in versionUrl -> formats += MediaFormat(
                    formatId = "hls-$versionName",
                    url = versionUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                else -> {
                    val match = Regex(
                        "(?i)_([a-z0-9]+)_([0-9]+)x([0-9]+)_([a-z0-9]+)_([0-9]+)_([a-z0-9]+)_([0-9]+)\\.([a-z0-9]+)",
                    ).find(versionUrl) ?: continue
                    formats += MediaFormat(
                        formatId = "http-$versionName-${version.str("quality") ?: match.groupValues[1]}",
                        url = versionUrl,
                        ext = match.groupValues[8],
                        vcodec = match.groupValues[4],
                        acodec = match.groupValues[6],
                        vbr = match.groupValues[5].toDoubleOrNull(),
                        abr = match.groupValues[7].toDoubleOrNull(),
                        width = match.groupValues[2].toLongOrNull(),
                        height = match.groupValues[3].toLongOrNull(),
                    )
                }
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The Vevo API returned no playable format.")
        }
        var artist: String? = null
        var featuredArtist: String? = null
        for (element in videoInfo.array("artists").orEmpty()) {
            val current = element as? JsonObject ?: continue
            if (current.str("role") == "Featured") {
                featuredArtist = current.str("name")
            } else {
                artist = current.str("name")
            }
        }
        val track = videoInfo.str("title")
        if (featuredArtist != null && artist != null) {
            artist = "$artist ft. $featuredArtist"
        }
        val title = if (artist != null) "$artist - $track" else track
        val isExplicit = videoInfo.boolean("isExplicit")
        val ageLimit = when (isExplicit) {
            true -> 18
            false -> 0
            else -> null
        }
        return InfoDict(
            id = videoId,
            title = title,
            duration = videoInfo.number("duration"),
            uploadDate = ExtractorUtils.unifiedStrdate(videoInfo.str("releaseDate")),
            viewCount = videoInfo.obj("views")?.number("total")?.toLong(),
            ageLimit = ageLimit,
            thumbnails = listOfNotNull(
                (videoInfo.str("imageUrl") ?: videoInfo.str("thumbnailUrl"))?.let { Thumbnail(url = it) },
            ),
            formats = formats,
            webpageUrl = url,
            extractor = "vevo",
            extractorKey = IE_KEY,
        )
    }

    private suspend fun initializeApi(videoId: String) {
        if (apiUrlTemplate != null) return
        val response = try {
            http.downloadJson(
                "https://accounts.vevo.com/token",
                method = "POST",
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"client_id":"$ANONYMOUS_CLIENT_ID","grant_type":"urn:vevo:params:oauth:grant-type:anonymous"}"""
                    .encodeToByteArray(),
            ) as? JsonObject
        } catch (error: ExtractionError) {
            throw ExtractionError.GeoRestricted(emptyList())
        }
        val legacyToken = response?.str("legacy_token")
            ?: throw ExtractionError.Malformed("The Vevo token endpoint returned no legacy token.")
        apiUrlTemplate = "https://apiv2.vevo.com/%s?token=$legacyToken"
    }

    private suspend fun callApiObject(path: String, videoId: String): JsonObject? = try {
        http.downloadJson(apiUrlTemplate!!.replace("%s", path)) as? JsonObject
    } catch (error: ExtractionError) {
        null
    }

    private suspend fun callApiArray(path: String, videoId: String): JsonArray? = try {
        http.downloadJson(apiUrlTemplate!!.replace("%s", path)) as? JsonArray
    } catch (error: ExtractionError) {
        null
    }

    companion object {
        const val IE_KEY: String = "Vevo"

        val VALID_URL: Regex = Regex(
            "(?:https?://(?:www\\.)?vevo\\.com/watch/(?!playlist|genre)(?:[^/]+/(?:[^/]+/)?)?|" +
                "https?://cache\\.vevo\\.com/m/html/embed\\.html\\?video=|" +
                "https?://videoplayer\\.vevo\\.com/embed/embedded\\?videoId=|" +
                "https?://embed\\.vevo\\.com/.*?[?&]isrc=|" +
                "https?://tv\\.vevo\\.com/watch/artist/(?:[^/]+)/|vevo:)(?<id>[^&\u0026?#]+)",
        )

        private val VERSIONS = mapOf(
            0 to "youtube",
            1 to "level3",
            2 to "akamai",
            3 to "level3",
            4 to "amazon",
        )
    }
}

/** Upstream `VevoPlaylistIE`: a playlist or genre page. */
class VevoPlaylistIE(
    http: ExtractorHttp,
) : VevoBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistKind = match.groups["kind"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        queryParam(url, "index")?.let { index ->
            if (index.isNotBlank()) {
                val videoId = Regex("<meta[^>]+content=([\"'])vevo://video/(.+?)\\1[^>]*>")
                    .find(webpage)?.groupValues?.get(2)
                if (videoId != null) {
                    return InfoDict(
                        id = videoId,
                        redirectUrl = "vevo:$videoId",
                        webpageUrl = url,
                        extractor = "vevo:playlist",
                        extractorKey = IE_KEY,
                    )
                }
            }
        }
        val store = initialStore(webpage)
        val playlists = store?.obj("default")?.obj("${playlistKind}s")
            ?: throw ExtractionError.Malformed("The Vevo page had no initial store playlists.")
        val playlist = if (playlistKind == "playlist") {
            playlists.values.firstOrNull() as? JsonObject
        } else {
            playlists.obj(playlistId)
        } ?: throw ExtractionError.Malformed("The Vevo page had no playlist entry.")
        val entries = mutableListOf<InfoEntry>()
        for (element in playlist.array("isrcs").orEmpty()) {
            val isrc = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            entries += InfoEntry(id = isrc, url = "vevo:$isrc")
        }
        return InfoDict(
            id = playlist.str("playlistId") ?: playlistId,
            title = playlist.str("name"),
            description = playlist.str("description"),
            entries = entries,
            webpageUrl = url,
            extractor = "vevo:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "VevoPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?vevo\\.com/watch/(?<kind>playlist|genre)/(?<id>[^/?#\u0026]+)",
        )
    }
}

/** Shared upstream `VevoBaseIE` initial-store scan. */
abstract class VevoBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_extract_json`. */
    protected fun initialStore(webpage: String): JsonObject? {
        val match = Regex("window\\.__INITIAL_STORE__\\s*=\\s*(\\{.+?\\});\\s*</script>", RegexOption.DOT_MATCHES_ALL)
            .find(webpage) ?: return null
        return ExtractorUtils.parseJson(match.groupValues[1]) as? JsonObject
    }
}

// ------------------------------------------------------------------ helpers

private fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "")
    for (part in query.split('&')) {
        if (part.substringBefore('=', "") == name) {
            return part.substringAfter('=', "")
        }
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
