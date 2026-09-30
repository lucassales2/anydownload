/*
 * Brightcove extractors — AnyDownload
 *
 * Kotlin translation of the public subset of `brightcove.py` from
 * `yt_dlp/extractor/brightcove.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `brightcove.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the legacy `brightcove:` / `c.brightcove.com` query form resolves a
 * new `players.brightcove.net` URL (publisher id from the query, the
 * `playerKey` decode, or the bcpid player page) and re-enters the registry;
 * the new player form extracts the policy key from `config.json` or
 * `index.min.js`, calls the Playback API, and maps progressive, HLS, and DASH
 * sources, DRM flags, captions, thumbnails, and duration. HLS/DASH masters are
 * recorded for the engine's download-time parse. The 401/403 API error body
 * (CLIENT_GEO, INVALID_POLICY_KEY, TVE_AUTH) cannot be read through the typed
 * HTTP seam, so those refusals fail typed as unavailable; the Adobe Pass TVE
 * path stops at the typed login wall. RTMP-only sources and the webpage
 * <object>/<video> scrapers are not translated, the smuggled referrer is not
 * carried, and `tags`/`uploader_id` are not modeled (the account id fills
 * `channelId`). No cookie, bearer token, or signed media URL is stored here.
 */
package com.anydownlod.core.extract.brightcove

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
import com.anydownlod.core.extract.adobepass.AdobePassIE
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `BrightcoveLegacyIE`: the old query-string embed form. */
class BrightcoveLegacyIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        var queryString = match.groups["query"]?.value.orEmpty().trimStart('?', '&')
        queryString = queryString
            .replace(Regex("(?<=[?&])(videoI(d|D)|idVideo|bctid)"), "%40videoPlayer")
            .replace(Regex("(?<=[?&])bckey"), "playerKey")
        val query = parseQuery(queryString)
        val videoPlayer = query["@videoPlayer"]?.firstOrNull()
            ?: throw ExtractionError.UnsupportedUrl()
        val referer = query["linkBaseURL"]?.firstOrNull() ?: url
        val videoId = videoPlayer
        var publisherId = query["publisherId"]?.firstOrNull()
            ?.takeIf { candidate -> candidate.isNotEmpty() && candidate.all { it.isDigit() } }

        if (publisherId == null) {
            var playerKey = query["playerKey"]?.firstOrNull()
            if (playerKey == null || !playerKey.contains(',')) {
                val playerId = query["playerID"]?.firstOrNull()
                    ?.takeIf { candidate -> candidate.firstOrNull()?.isDigit() == true }
                playerKey = if (playerId != null) {
                    val page = runCatching {
                        http.downloadWebpage(
                            "https://link.brightcove.com/services/player/bcpid$playerId",
                            headers = mapOf("referer" to referer),
                        )
                    }.getOrNull()
                    page?.let {
                        ExtractorUtils.searchRegex(
                            "<param\\s+name=\"playerKey\"\\s+value=\"([\\w~,-]+)\"",
                            it,
                        )
                    }
                } else {
                    null
                }
            }
            if (playerKey != null && playerKey.contains(',')) {
                publisherId = decodePublisherId(playerKey)
            }
        }

        val resolvedPublisher = publisherId
            ?: throw ExtractionError.UnsupportedUrl(
                "The legacy Brightcove URL did not resolve to a publisher id.",
            )
        return InfoDict(
            id = videoId,
            webpageUrl = url,
            redirectUrl = "https://players.brightcove.net/$resolvedPublisher/default_default/" +
                "index.html?videoId=$videoId",
            extractor = "brightcove",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "BrightcoveLegacy"

        val VALID_URL: Regex = Regex(
            "(?:https?://.*brightcove\\.com/(services|viewer).*?\\?|brightcove:)(?<query>.*)",
        )
    }
}

/**
 * Upstream `BrightcoveNewBaseIE`: the shared Playback API metadata mapping.
 */
abstract class BrightcoveNewBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : AdobePassIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_parse_brightcove_metadata`. */
    protected fun parseBrightcoveMetadata(
        jsonData: JsonObject,
        videoId: String,
        httpHeaders: Map<String, String> = emptyMap(),
    ): InfoDict {
        val formats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()

        for (element in jsonData.array("sources").orEmpty()) {
            val source = element as? JsonObject ?: continue
            val container = source.str("container")
            val ext = ExtractorUtils.mimetype2ext(source.str("type"))
            val src = ExtractorUtils.urlOrNone(source.str("src"))
            val streamingSrc = ExtractorUtils.urlOrNone(source.str("streaming_src"))
            val streamName = source.str("stream_name")
            val appName = source.str("app_name")

            val sourceFormats = when {
                ext == "m3u8" || container == "M2TS" -> {
                    if (src == null) emptyList()
                    else listOf(
                        MediaFormat(
                            formatId = "hls",
                            url = src,
                            ext = "mp4",
                            protocol = "m3u8_native",
                            httpHeaders = httpHeaders.takeIf { it.isNotEmpty() },
                        ),
                    )
                }

                ext == "mpd" -> {
                    if (src == null) emptyList()
                    else listOf(
                        MediaFormat(
                            formatId = "dash",
                            url = src,
                            ext = "mp4",
                            protocol = "http_dash_segments",
                            httpHeaders = httpHeaders.takeIf { it.isNotEmpty() },
                        ),
                    )
                }

                src == null && streamingSrc == null && (streamName == null || appName == null) -> emptyList()

                src == null && streamingSrc == null -> emptyList()

                else -> {
                    val url = src ?: streamingSrc
                    val tbr = source.number("avg_bitrate")?.div(1000.0)
                    val height = source.number("height")?.toLong()
                    val width = source.number("width")?.toLong()
                    val audioOnly = width == 0L && height == 0L
                    listOf(
                        MediaFormat(
                            formatId = joinNonEmpty(
                                if (src != null) "http" else "http-streaming",
                                tbr?.toLong()?.let { "${it}k" },
                                height?.let { "${it}p" },
                            ),
                            url = url,
                            tbr = tbr,
                            filesize = source.number("size")?.toLong(),
                            container = container,
                            ext = ext ?: container?.lowercase(),
                            width = if (audioOnly) null else width,
                            height = if (audioOnly) null else height,
                            vcodec = if (audioOnly) MediaFormat.CODEC_NONE else source.str("codec"),
                            sourcePreference = if (src != null) 0 else -1,
                            httpHeaders = httpHeaders.takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            }

            val drm = container == "WVM" || source["key_systems"] != null || ext == "ism"
            for (format in sourceFormats) {
                formats += if (drm) format.copy(hasDrm = true) else format
            }
        }

        if (formats.isEmpty()) {
            val error = jsonData.array("errors")?.firstOrNull() as? JsonObject
            val message = error?.str("message") ?: error?.str("error_subcode") ?: error?.str("error_code")
            throw ExtractionError.NoFormats(message ?: "The Brightcove video had no playable sources.")
        }

        for (element in jsonData.array("text_tracks").orEmpty()) {
            val track = element as? JsonObject ?: continue
            if (track.str("kind") != "captions") continue
            val url = ExtractorUtils.urlOrNone(track.str("src")) ?: continue
            val language = (track.str("srclang") ?: track.str("label") ?: "en").lowercase()
            subtitles += SubtitleTrack(
                language = language,
                formats = listOf(SubtitleFormat(ext = "vtt", url = url)),
            )
        }

        var duration = jsonData.number("duration")?.div(1000.0)
        val isLive = duration != null && duration <= 0
        if (isLive) duration = null
        val poster = ExtractorUtils.urlOrNone(jsonData.str("poster") ?: jsonData.str("thumbnail"))

        return InfoDict(
            id = videoId,
            title = cleanHtml(jsonData.str("name")),
            description = cleanHtml(jsonData.str("description")),
            duration = duration,
            uploadDate = jsonData.str("published_at")?.let(ExtractorUtils::unifiedStrdate),
            channelId = jsonData.str("account_id"),
            isLive = isLive,
            thumbnails = thumbnails(poster),
            formats = formats,
            subtitles = subtitles,
            extractor = "brightcove",
            extractorKey = ieKey,
        )
    }
}

/** Upstream `BrightcoveNewIE`: the players.brightcove.net player pages. */
class BrightcoveNewIE(
    http: ExtractorHttp,
) : BrightcoveNewBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val accountId = match.groups["account_id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playerId = match.groups["player_id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val embed = match.groups["embed"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val contentType = match.groups["content_type"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["video_id"]?.value ?: throw ExtractionError.UnsupportedUrl()

        val policyKey = extractPolicyKey(accountId, playerId, embed, videoId)
            ?: throw ExtractionError.Unavailable("The Brightcove player config had no policy key.")
        val apiUrl = "https://edge.api.brightcove.com/playback/v1/accounts/$accountId/" +
            "${contentType}s/$videoId"
        val json = http.downloadJson(
            apiUrl,
            headers = mapOf("accept" to "application/json;pk=$policyKey"),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Brightcove Playback API returned no object.")

        if (contentType == "playlist") {
            val entries = json.array("videos").orEmpty().mapNotNull { element ->
                val id = (element as? JsonObject)?.str("id") ?: return@mapNotNull null
                InfoEntry(
                    id = id,
                    title = (element as? JsonObject)?.str("name"),
                    url = "https://players.brightcove.net/$accountId/${playerId}_$embed/" +
                        "index.html?videoId=$id",
                )
            }
            return InfoDict(
                id = json.str("id") ?: videoId,
                title = json.str("name"),
                description = json.str("description"),
                entries = entries,
                webpageUrl = url,
                extractor = "brightcove",
                extractorKey = IE_KEY,
            )
        }

        val errors = json.array("errors")
        if (errors != null && errors.isNotEmpty()) {
            val subcode = (errors.firstOrNull() as? JsonObject)?.str("error_subcode")
            if (subcode == "TVE_AUTH") {
                // Upstream runs the Adobe Pass flow with smuggled fields; the
                // port stops at the typed login wall.
                mvpdAuthRequired()
            }
        }
        return parseBrightcoveMetadata(json, videoId)
    }

    /** Upstream `extract_policy_key`: `config.json`, then `index.min.js`. */
    private suspend fun extractPolicyKey(
        accountId: String,
        playerId: String,
        embed: String,
        videoId: String,
    ): String? {
        val baseUrl = "https://players.brightcove.net/$accountId/${playerId}_$embed/"
        val config = try {
            http.downloadJson(baseUrl + "config.json") as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        config?.obj("video_cloud")?.str("policy_key")?.let { return it }
        val webpage = http.downloadWebpage(baseUrl + "index.min.js")
        val catalog = ExtractorUtils.searchRegex("catalog\\((.+?)\\);", webpage, default = null)
        if (catalog != null) {
            val parsed = ExtractorUtils.parseJson(ExtractorUtils.jsToJson(catalog)) as? JsonObject
            parsed?.str("policyKey")?.let { return it }
        }
        return ExtractorUtils.searchRegex(
            "policyKey\\s*:\\s*([\"'])(?<pk>.+?)\\1",
            webpage,
            group = 2,
            default = null,
        )
    }

    companion object {
        const val IE_KEY: String = "BrightcoveNew"

        val VALID_URL: Regex = Regex(
            "https?://players\\.brightcove\\.net/(?<account_id>\\d+)/(?<player_id>[^/]+)_" +
                "(?<embed>[^/]+)/index\\.html\\?.*(?<content_type>video|playlist)Id=" +
                "(?<video_id>\\d+|ref:[^&]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers


private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/** Upstream `struct.unpack('>Q', base64.urlsafe_b64decode(...))[0]`. */
internal fun decodePublisherId(playerKey: String): String? {
    val parts = playerKey.split(',')
    if (parts.size < 2) return null
    val bytes = base64UrlDecode(parts[1].replace('~', '=')) ?: return null
    if (bytes.size != 8) return null
    var value = 0uL
    for (byte in bytes) {
        value = (value shl 8) or byte.toUByte().toULong()
    }
    return value.toString()
}

private fun base64UrlDecode(value: String): ByteArray? {
    val cleaned = value.trimEnd('=')
    val out = ByteArray(cleaned.length * 6 / 8)
    var buffer = 0
    var bits = 0
    var index = 0
    for (character in cleaned) {
        val decoded = BASE64_URL_ALPHABET.indexOf(character)
        if (decoded < 0) return null
        buffer = (buffer shl 6) or decoded
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out[index++] = ((buffer shr bits) and 0xFF).toByte()
        }
    }
    return out.copyOf(index)
}

/** `urllib.parse.parse_qs` for the legacy query string. */
private fun parseQuery(query: String): Map<String, List<String>> {
    val result = mutableMapOf<String, MutableList<String>>()
    for (pair in query.split('&')) {
        if (pair.isEmpty()) continue
        val key = percentDecode(pair.substringBefore('='))
        val value = percentDecode(pair.substringAfter('=', ""))
        result.getOrPut(key) { mutableListOf() } += value
    }
    return result
}

private fun percentDecode(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        when {
            character == '+' -> {
                append(' ')
                index++
            }

            character == '%' && index + 2 < value.length -> {
                val code = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (code != null) {
                    append(code.toChar())
                    index += 3
                } else {
                    append(character)
                    index++
                }
            }

            else -> {
                append(character)
                index++
            }
        }
    }
}

/** Upstream `join_nonempty`. */
private fun joinNonEmpty(vararg values: String?): String =
    values.filterNotNull().filter { it.isNotEmpty() }.joinToString("-")

private val THUMBNAIL_SIZE = Regex("\\d+x\\d+")

private val COMMON_RESOLUTIONS = listOf(
    160 to 90,
    320 to 180,
    480 to 720,
    640 to 360,
    768 to 432,
    1024 to 576,
    1280 to 720,
    1366 to 768,
    1920 to 1080,
)

/** Upstream's common-resolution poster rewriting. */
private fun thumbnails(poster: String?): List<Thumbnail> {
    val base = poster?.takeIf { THUMBNAIL_SIZE.containsMatchIn(it) } ?: return emptyList()
    return COMMON_RESOLUTIONS.map { (width, height) ->
        Thumbnail(
            url = THUMBNAIL_SIZE.replace(base, "${width}x$height"),
            width = width.toLong(),
            height = height.toLong(),
        )
    }
}

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    val withoutTags = text.replace(Regex("<[^>]*>"), " ")
    return ExtractorUtils.unescapeHtml(withoutTags)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
