/*
 * Sky Italia extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `skyit.py` from
 * `yt_dlp/extractor/skyit.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `skyit.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public apid.sky.it getVideoData/getLivestream APIs, the player
 * URL redirects, the page video-id scans, the TV8 live stream, and the TV8
 * playlist. Only the default `sky` caller token upstream carries is stored
 * (a public app token, not a user credential); the per-domain token map is
 * not carried, so a domain-specific caller may fail typed. Manifest parsing
 * is not translated, so an m3u8 URL becomes one HLS row. No cookie, user
 * token, or private URL is stored here.
 */
package com.anydownload.core.extract.skyit

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

private const val SKY_CALLER_TOKEN = "F96WlOd8yoFmLQgiqv6fNQRvHZcsWk5jDaYnDvhbiJk"

/** Shared upstream `SkyItBaseIE` helpers. */
abstract class SkyItBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
    protected val domain: String = "sky",
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_player_url_result`. */
    protected fun playerUrlResult(videoId: String): InfoDict = InfoDict(
        id = videoId,
        redirectUrl = "https://player.sky.it/player/external.html?id=$videoId&domain=$domain",
        extractor = "skyit",
        extractorKey = ieKey,
    )

    /** Upstream `_parse_video`. */
    protected fun parseVideo(video: JsonObject, videoId: String): InfoDict {
        val isLive = video.str("type") == "live"
        val hlsUrl = video.str(if (isLive) "streaming_url" else "hls_url")
        if (hlsUrl == null && video.boolean(if (isLive) "geoblock" else "geob") == true) {
            throw ExtractionError.GeoRestricted(listOf("IT"))
        }
        if (hlsUrl == null) {
            throw ExtractionError.NoFormats("The Sky video data returned no stream URL.")
        }
        val thumbnail = video.str("video_still") ?: video.str("video_still_medium")
            ?: video.str("thumb")
        return InfoDict(
            id = videoId,
            title = video.str("title"),
            description = video.str("short_desc"),
            duration = video.number("duration_sec")
                ?: video.str("duration")?.let { parseDuration(it) },
            uploadDate = ExtractorUtils.unifiedStrdate(video.str("create_date")),
            isLive = isLive,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = listOf(
                MediaFormat(formatId = "hls", url = hlsUrl, ext = "mp4", protocol = "m3u8_native"),
            ),
            extractor = "skyit",
            extractorKey = ieKey,
        )
    }
}

/** Upstream `SkyItPlayerIE`: a player URL. */
class SkyItPlayerIE(
    http: ExtractorHttp,
) : SkyItBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val video = http.downloadJson(
            "https://apid.sky.it/vdp/v1/getVideoData?caller=sky&id=$videoId&token=$SKY_CALLER_TOKEN",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Sky video API returned no object.")
        return parseVideo(video, videoId).copy(webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "SkyItPlayer"

        val VALID_URL: Regex = Regex(
            "https?://player\\.sky\\.it/player/(?:external|social)\\.html\\?.*?\\bid=(?<id>\\d+)",
        )
    }
}

/** Upstream `SkyItVideoIE`: a video.sky.it page. */
open class SkyItVideoIE(
    http: ExtractorHttp,
    ieKey: String = IE_KEY,
    validUrl: Regex = VALID_URL,
    domain: String = "sky",
) : SkyItBaseIE(ieKey = ieKey, http = http, validUrl = validUrl, domain = domain) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return playerUrlResult(videoId).copy(webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "SkyItVideo"

        val VALID_URL: Regex = Regex(
            "https?://(?:masterchef|video|xfactor)\\.sky\\.it(?:/[^/]+)*/video/[0-9a-z-]+-(?<id>\\d+)",
        )
    }
}

/** Upstream `SkyItVideoLiveIE`: a video.sky.it live page. */
class SkyItVideoLiveIE(
    http: ExtractorHttp,
) : SkyItBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val nextData = nextJsData(webpage)
            ?: throw ExtractionError.Malformed("The Sky live page had no Next.js data.")
        val assetId = nextData.obj("props")?.obj("initialState")?.obj("livePage")
            ?.obj("content")?.primitive("asset_id")
            ?: throw ExtractionError.Malformed("The Sky live page had no asset id.")
        val livestream = http.downloadJson(
            "https://apid.sky.it/vdp/v1/getLivestream?id=$assetId",
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Sky livestream API returned no object.")
        return parseVideo(livestream, assetId).copy(webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "SkyItVideoLive"

        val VALID_URL: Regex = Regex("https?://video\\.sky\\.it/diretta/(?<id>[^/?\u0026#]+)")
    }
}

/** Upstream `SkyItIE`: a sport/tg24 article. */
open class SkyItIE(
    http: ExtractorHttp,
    ieKey: String = IE_KEY,
    validUrl: Regex = VALID_URL,
    domain: String = "sky",
    private val videoIdRegex: String = "data-videoid=\"(\\d+)\"",
) : SkyItBaseIE(ieKey = ieKey, http = http, validUrl = validUrl, domain = domain) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val videoId = Regex(videoIdRegex).find(webpage)?.groupValues?.get(1)
            ?: throw ExtractionError.Malformed("The Sky page had no video id.")
        return playerUrlResult(videoId).copy(webpageUrl = url)
    }

    companion object {
        const val IE_KEY: String = "SkyIt"

        val VALID_URL: Regex = Regex(
            "https?://(?:sport|tg24)\\.sky\\.it(?:/[^/]+)*/\\d{4}/\\d{2}/\\d{2}/(?<id>[^/?\u0026#]+)",
        )
    }
}

/** Upstream `SkyItArteIE`: arte.sky.it. */
class SkyItArteIE(
    http: ExtractorHttp,
) : SkyItIE(
    http = http,
    ieKey = "SkyItArte",
    validUrl = VALID_URL,
    domain = "skyarte",
    videoIdRegex = "\"embedUrl\"\\s*:\\s*\"(?:https:)?//player\\.sky\\.it/player/external\\.html\\?[^\"]*\\bid=(\\d+)",
) {
    companion object {
        val VALID_URL: Regex = Regex("https?://arte\\.sky\\.it/video/(?<id>[^/?\u0026#]+)")
    }
}

/** Upstream `CieloTVItIE`: cielotv.it. */
class CieloTVItIE(
    http: ExtractorHttp,
) : SkyItIE(
    http = http,
    ieKey = "CieloTVIt",
    validUrl = VALID_URL,
    domain = "cielo",
    videoIdRegex = "videoId\\s*=\\s*\"(\\d+)\"",
) {
    companion object {
        val VALID_URL: Regex = Regex("https?://(?:www\\.)?cielotv\\.it/video/(?<id>[^.]+)\\.html")
    }
}

/** Upstream `TV8ItIE`: tv8.it. */
class TV8ItIE(
    http: ExtractorHttp,
) : SkyItVideoIE(
    http = http,
    ieKey = "TV8It",
    validUrl = VALID_URL,
    domain = "mtv8",
) {
    companion object {
        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?tv8\\.it/(?:show)?video/(?:[0-9a-z-]+-)?(?<id>\\d+)",
        )
    }
}

/** Upstream `TV8ItLiveIE`: the TV8 live stream. */
class TV8ItLiveIE(
    http: ExtractorHttp,
) : SkyItBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL, domain = "mtv8") {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = "tv8"
        val livestream = http.downloadJson("https://apid.sky.it/vdp/v1/getLivestream?id=7")
            as? JsonObject ?: throw ExtractionError.Malformed("The TV8 livestream API returned no object.")
        val metadata = try {
            http.downloadJson("https://tv8.it/api/getStreaming") as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        val base = parseVideo(livestream, videoId)
        val info = metadata?.obj("info")
        return base.copy(
            title = info?.obj("title")?.str("text") ?: base.title,
            description = info?.obj("description")?.str("html")?.replace(Regex("<[^>]*>"), "")?.trim()
                ?: base.description,
            webpageUrl = url,
        )
    }

    companion object {
        const val IE_KEY: String = "TV8ItLive"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tv8\\.it/streaming")
    }
}

/** Upstream `TV8ItPlaylistIE`: a TV8 playlist. */
class TV8ItPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val nextData = nextJsData(webpage)
            ?: throw ExtractionError.Malformed("The TV8 page had no Next.js data.")
        val data = nextData.obj("props")?.obj("pageProps")?.obj("data")
            ?: throw ExtractionError.Malformed("The TV8 page had no playlist data.")
        val entries = mutableListOf<InfoEntry>()
        for (element in data.obj("lastContent")?.array("cards").orEmpty()) {
            val card = element as? JsonObject ?: continue
            val href = card.str("href") ?: continue
            entries += InfoEntry(
                id = card.obj("extraData")?.primitive("asset_id"),
                title = card.obj("title")?.obj("typography")?.str("text"),
                url = urlJoin("https://tv8.it", href),
            )
        }
        val card = data.obj("card")?.obj("desktop")
        return InfoDict(
            id = playlistId,
            title = card?.obj("title")?.str("text"),
            description = card?.obj("description")?.str("html")?.replace(Regex("<[^>]*>"), "")?.trim(),
            thumbnails = listOfNotNull(card?.obj("image")?.str("src")?.let { Thumbnail(url = it) }),
            entries = entries,
            webpageUrl = url,
            extractor = "tv8it:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "TV8ItPlaylist"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?tv8\\.it/(?!video)[^/#?]+/(?<id>[^/#?]+)")
    }
}

// ------------------------------------------------------------------ helpers

private fun nextJsData(webpage: String): JsonObject? {
    val raw = Regex("(?s)<script[^>]+id\\s*=\\s*[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>")
        .find(webpage)?.groupValues?.get(1) ?: return null
    return ExtractorUtils.parseJson(raw) as? JsonObject
}

private fun parseDuration(value: String): Double? {
    val parts = value.trim().split(':')
    if (parts.isEmpty() || parts.size > 3) return null
    var seconds = 0.0
    for (part in parts) {
        val number = part.toDoubleOrNull() ?: return null
        seconds = seconds * 60 + number
    }
    return seconds
}

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    return if (href.startsWith("/")) base + href else "$base/$href"
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun JsonObject.primitive(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
