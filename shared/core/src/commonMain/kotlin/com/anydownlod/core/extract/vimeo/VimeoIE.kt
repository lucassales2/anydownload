/*
 * Vimeo extractors — AnyDownload
 *
 * Kotlin translation of the public watch-page and player-page subset of
 * `VimeoIE` from `yt_dlp/extractor/vimeo.py` at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `vimeo.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `vimeo.com/<id>`, `www.vimeo.com/<id>`, the 10-hex unlisted suffix,
 * `vimeo.com/channels/<channel>/<id>`, and `player.vimeo.com/video/<id>`.
 * The watch page's `vimeo.clip_page_config` / `vimeo.vod_title_page_config`
 * points at the player config JSON; the player page carries `playerConfig`
 * inline. The config maps progressive files, the `hls`/`dash` CDN manifests,
 * text tracks, thumbs, chapters, owner, and the live-event flags. The engine
 * resolves the `m3u8_native` and `http_dash_segments` manifests at download
 * time.
 *
 * Not translated: the private API path (`_extract_from_api`, unlisted-hash
 * API, OAuth clients, original/source download), the other ten `vimeo.py`
 * classes (ondemand, channel, user, album, groups, review, watchlater, likes,
 * pro, event), password-protected playback, and logged-in-only formats. No
 * Vimeo API token, OAuth secret, cookie, or signed media URL is stored or
 * committed; fixture hosts are `*.example`.
 */
package com.anydownlod.core.extract.vimeo

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.Chapter
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.SubtitleFormat
import com.anydownlod.core.extract.SubtitleTrack
import com.anydownlod.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Upstream `VimeoIE` subset: one public video from the watch or player page.
 *
 * A multi-part or collection URL is not this extractor; `vimeo.com/album/...`
 * and friends match no registered extractor and fall through to the desktop
 * CLI or Android Chaquopy. Password-protected playback fails typed because
 * the app has no password field.
 */
class VimeoIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "Vimeo"

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val channelId = match.groups["channel"]?.value

        val webpage = http.downloadWebpage(url, headers = mapOf("referer" to url))

        if (url.substringAfter("://").startsWith("player.vimeo.com")) {
            val config = extractJsonObject(webpage, PLAYER_CONFIG)
                ?: throw ExtractionError.Malformed("The player page had no player configuration.")
            if (config.number("view")?.toLong() == PASSWORD_VIEW) {
                throw ExtractionError.LoginRequired("This Vimeo video needs a password.")
            }
            return finish(config, videoId, url, emptyMap())
        }

        val pageConfig = extractJsonObject(webpage, PAGE_CONFIG)
        val configUrl = (if (channelId != null) configUrlAttribute(webpage) else null)
            ?: pageConfig?.obj("player")?.str("config_url")
            ?: throw ExtractionError.Unavailable("This Vimeo page did not expose a player configuration.")
        val config = http.downloadJson(configUrl) as? JsonObject
            ?: throw ExtractionError.Malformed("The Vimeo player configuration was empty.")
        if (config.number("view")?.toLong() == PASSWORD_VIEW) {
            throw ExtractionError.LoginRequired("This Vimeo video needs a password.")
        }

        val clip = pageConfig?.obj("clip")
        val pageFields = buildMap {
            (clip?.str("description")?.let(::stripTags)
                ?: ExtractorUtils.htmlSearchMeta(webpage, "description", "og:description", "twitter:description"))
                ?.let { put(PAGE_DESCRIPTION, it) }
            (clip?.str("uploaded_on")?.let(ExtractorUtils::unifiedStrdate)
                ?: ExtractorUtils.searchRegex("<time[^>]+datetime=\"([^\"]+)\"", webpage)
                    ?.let(ExtractorUtils::unifiedStrdate))
                ?.let { put(PAGE_UPLOAD_DATE, it) }
            ExtractorUtils.searchRegex("UserPlays:(\\d+)", webpage)
                ?.toLongOrNull()?.let { put(PAGE_VIEW_COUNT, it) }
        }
        return finish(config, videoId, url, pageFields, channelId)
    }

    /**
     * One parsed player config, from the player page or the watch page's
     * `config_url`. [pageFields] and [channelId] come from the watch page.
     */
    private suspend fun finish(
        config: JsonObject,
        videoId: String,
        webpageUrl: String,
        pageFields: Map<String, Any?>,
        channelId: String? = null,
    ): InfoDict {
        val info = parseConfig(config, videoId)
        val formats = info.formats
        if (formats.isEmpty()) {
            val liveEvent = (config.obj("video") ?: JsonObject(emptyMap())).obj("live_event")
            if (liveEvent?.str("status") in setOf("pending", "active")) {
                throw ExtractionError.NotYetAvailable("This Vimeo event has not started.")
            }
            throw ExtractionError.NoFormats("The Vimeo config declared no format.")
        }
        return info.copy(
            description = pageFields[PAGE_DESCRIPTION] as? String ?: info.description,
            uploadDate = pageFields[PAGE_UPLOAD_DATE] as? String ?: info.uploadDate,
            viewCount = pageFields[PAGE_VIEW_COUNT] as? Long ?: info.viewCount,
            channelId = channelId ?: info.channelId,
            webpageUrl = webpageUrl,
        )
    }

    /** Upstream `_parse_config`: the `video`/`request` player config subset. */
    private suspend fun parseConfig(config: JsonObject, videoId: String): InfoDict {
        val video = config.obj("video")
            ?: throw ExtractionError.Malformed("The player configuration had no video.")
        val request = config.obj("request")
        val files = video.obj("files") ?: request?.obj("files") ?: JsonObject(emptyMap())
        val formats = mutableListOf<MediaFormat>()

        for (element in files.array("progressive").orEmpty()) {
            val file = element as? JsonObject ?: continue
            val mediaUrl = file.str("url") ?: continue
            formats += MediaFormat(
                formatId = "http-${file.str("quality").orEmpty()}",
                url = mediaUrl,
                ext = "mp4",
                sourcePreference = 10,
                width = file.number("width")?.toLong(),
                height = file.number("height")?.toLong(),
                fps = file.number("fps"),
                tbr = file.number("bitrate"),
            )
        }

        for (type in listOf("hls", "dash")) {
            val cdns = files.obj(type)?.obj("cdns") ?: continue
            for ((cdnName, element) in cdns) {
                val cdn = element as? JsonObject ?: continue
                var manifestUrl = cdn.str("url") ?: continue
                if (type == "dash") {
                    if (manifestUrl.contains("json=1")) {
                        val realUrl = try {
                            (http.downloadJson(manifestUrl) as? JsonObject)?.str("url")
                        } catch (_: ExtractionError) {
                            null
                        }
                        if (realUrl != null) manifestUrl = realUrl
                    }
                    manifestUrl = manifestUrl.replace("/master.json", "/master.mpd")
                }
                formats += MediaFormat(
                    formatId = "$type-$cdnName",
                    url = manifestUrl,
                    ext = "mp4",
                    protocol = if (type == "hls") "m3u8_native" else "http_dash_segments",
                    formatNote = if (type == "hls") "HLS" else "DASH",
                )
            }
        }

        val liveEvent = video.obj("live_event")
        val archive = liveEvent?.obj("archive")
        if (archive?.str("status") == "done") {
            archive.str("source_url")?.let { sourceUrl ->
                formats += MediaFormat(
                    formatId = "live-archive-source",
                    url = sourceUrl,
                    quality = "10",
                )
            }
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in request?.array("text_tracks").orEmpty()) {
            val track = element as? JsonObject ?: continue
            val language = track.str("lang") ?: continue
            val trackUrl = track.str("url") ?: continue
            subtitles += SubtitleTrack(
                language = language,
                name = track.str("label"),
                formats = listOf(SubtitleFormat(ext = "vtt", url = playerUrl(trackUrl))),
            )
        }

        val thumbnails = mutableListOf<Thumbnail>()
        for ((key, element) in video.obj("thumbs").orEmpty()) {
            val thumbUrl = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            thumbnails += Thumbnail(
                url = thumbUrl,
                id = key,
                width = key.toLongOrNull(),
            )
        }
        video.str("thumbnail_url")?.let { thumbnails += Thumbnail(url = it, id = "thumbnail_url") }
        video.str("thumbnail")?.let { thumbnails += Thumbnail(url = it, id = "thumbnail") }

        val chapters = mutableListOf<Chapter>()
        for (element in config.obj("embed")?.array("chapters").orEmpty()) {
            val chapter = element as? JsonObject ?: continue
            val timecode = chapter.number("timecode") ?: continue
            chapters += Chapter(startTime = timecode, title = chapter.str("title"))
        }

        val owner = video.obj("owner")
        return InfoDict(
            id = video.number("id")?.toLong()?.toString() ?: videoId,
            title = video.str("title"),
            formats = formats,
            thumbnails = thumbnails,
            duration = video.number("duration"),
            uploader = owner?.str("name"),
            subtitles = subtitles,
            chapters = chapters.sortedBy { it.startTime },
            isLive = (liveEvent?.str("status") == "started").takeIf { liveEvent != null },
            extractor = "vimeo",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Vimeo"

        /** Upstream `_VALID_URL` for the single-video watch and player forms. */
        val VALID_URL: Regex = Regex(
            "https?://(?:" +
                "(?:(?:www\\.)?vimeo\\.com/" +
                "(?!(?:album|showcase|ondemand|event|review|watchlater|home|manage|settings)(?:/|$))" +
                "(?:channels/(?<channel>[^/?#]+)/)?)|" +
                "player\\.vimeo\\.com/video/" +
                ")(?<id>[0-9]+)(?:/[0-9a-f]{10})?/?(?:[?#].*)?$",
        )

        private const val PASSWORD_VIEW = 4L
    }
}

private const val PAGE_DESCRIPTION = "description"
private const val PAGE_UPLOAD_DATE = "uploadDate"
private const val PAGE_VIEW_COUNT = "viewCount"

private val PLAYER_CONFIG = Regex("\\b(?:playerC|c)onfig\\s*=\\s*")

private val PAGE_CONFIG = Regex("vimeo\\.(?:clip|vod_title)_page_config\\s*=\\s*")

/** Upstream `_extract_config_url`: the `data-config-url` attribute. */
private fun configUrlAttribute(html: String): String? = ExtractorUtils.searchRegex(
    "\\bdata-config-url=\"([^\"]+)\"",
    html,
)

private fun playerUrl(value: String): String =
    if (value.startsWith("http")) value else "https://player.vimeo.com/" + value.trimStart('/')

/** Upstream `clean_html` subset: drop tags, then decode the entities it emits. */
private fun stripTags(value: String): String {
    val withoutTags = Regex("<[^>]*>").replace(value, " ")
    return (ExtractorUtils.unescapeHtml(withoutTags) ?: withoutTags)
        .replace(Regex("\\s+"), " ")
        .trim()
}

/**
 * Upstream `_search_json` subset: the balanced JSON object after [marker].
 * The scanner tracks braces and string escapes so a `}` inside a string does
 * not end the object.
 */
private fun extractJsonObject(html: String, marker: Regex): JsonObject? {
    val match = marker.find(html) ?: return null
    var index = match.range.last + 1
    while (index < html.length && html[index].isWhitespace()) index++
    if (html.getOrNull(index) != '{') return null
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

            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return ExtractorUtils.parseJson(html.substring(index, position + 1)) as? JsonObject
                }
            }
        }
    }
    return null
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
