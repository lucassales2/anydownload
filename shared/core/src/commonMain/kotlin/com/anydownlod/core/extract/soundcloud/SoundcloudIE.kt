/*
 * SoundCloud extractors — AnyDownload
 *
 * Kotlin translation of the track subset of `SoundcloudIE` and the redirect
 * of `SoundcloudEmbedIE` from `yt_dlp/extractor/soundcloud.py` at upstream tag
 * `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `soundcloud.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `soundcloud.com/<uploader>/<title>[/<token>]` and the
 * `api.soundcloud.com`/`api-v2` track URLs. The API client id is discovered
 * at runtime from the main page's script assets (never stored or committed)
 * and refreshed once on an auth failure. `media.transcodings` map to
 * progressive and `m3u8_native` audio formats; DRM protocols (`ctr-`/`cbc-`)
 * are dropped and a track with only DRM fails typed. Artwork sizes, metadata,
 * and the secret token are carried. The original download format, OAuth
 * login, sets/playlists, users, stations, related, search, and comments are
 * not translated.
 *
 * No client id, OAuth token, cookie, or signed media URL is stored or
 * committed; fixture hosts are `*.example`.
 */
package com.anydownlod.core.extract.soundcloud

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Upstream `SoundcloudIE`: one track from its permalink or API URL. */
class SoundcloudIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "SoundCloud"

    private var cachedClientId: String? = null

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val trackId = match.groups["trackId"]?.value
        val query = mutableMapOf<String, String>()
        val infoUrl: String
        var token: String?
        if (trackId != null) {
            infoUrl = API_V2_BASE + "tracks/" + trackId
            token = match.groups["secretToken"]?.value
        } else {
            val uploader = match.groups["uploader"]?.value ?: throw ExtractionError.UnsupportedUrl()
            val title = match.groups["title"]?.value ?: throw ExtractionError.UnsupportedUrl()
            token = match.groups["token"]?.value
            val fullTitle = "$uploader/$title" + (token?.let { "/$it" } ?: "")
            infoUrl = API_V2_BASE + "resolve?url=" + percentEncode(BASE_URL + fullTitle)
        }
        if (token != null) query["secret_token"] = token

        val info = callApi(infoUrl, query) as? JsonObject
            ?: throw ExtractionError.Malformed("The SoundCloud track response was empty.")
        return extractInfoDict(info, info.str("permalink_url") ?: url, token)
    }

    /** Upstream `_call_api`: the client id query, refreshed once on failure. */
    private suspend fun callApi(url: String, query: Map<String, String>): JsonElement {
        var attempt = 0
        while (true) {
            val parameters = query + ("client_id" to clientId())
            val separator = if (url.contains('?')) '&' else '?'
            val requestUrl = url + separator + parameters.entries.joinToString("&") { (name, value) ->
                percentEncode(name) + "=" + percentEncode(value)
            }
            try {
                return http.downloadJson(requestUrl)
            } catch (error: ExtractionError) {
                if (attempt == 0) {
                    attempt++
                    cachedClientId = null
                    continue
                }
                throw error
            }
        }
    }

    /** Upstream `_update_client_id`: the main page's script asset. */
    private suspend fun clientId(): String {
        cachedClientId?.let { return it }
        val webpage = http.downloadWebpage(BASE_URL)
        for (scriptSource in SCRIPT_SOURCE.findAll(webpage).map { it.groupValues[1] }.toList().asReversed()) {
            val scriptUrl = if (scriptSource.startsWith("//")) "https:$scriptSource" else scriptSource
            val script = try {
                http.downloadWebpage(scriptUrl)
            } catch (_: ExtractionError) {
                continue
            }
            val clientId = CLIENT_ID.find(script)?.groupValues?.get(1)
            if (clientId != null) {
                cachedClientId = clientId
                return clientId
            }
        }
        throw ExtractionError.Malformed("Unable to extract the SoundCloud client id.")
    }

    /** Upstream `_extract_info_dict` for the track formats and metadata. */
    private suspend fun extractInfoDict(info: JsonObject, webpageUrl: String, token: String?): InfoDict {
        val trackId = info.str("id") ?: info.number("id")?.toLong()?.toString()
            ?: throw ExtractionError.Malformed("The SoundCloud track had no id.")
        val query = mutableMapOf<String, String>()
        if (token != null) query["secret_token"] = token

        val formats = mutableListOf<MediaFormat>()
        var hasDrm = false
        for (element in info.obj("media")?.array("transcodings").orEmpty()) {
            val transcoding = element as? JsonObject ?: continue
            val formatUrl = transcoding.str("url") ?: continue
            val preset = transcoding.str("preset") ?: continue
            val protocol = varProtocol(transcoding, formatUrl)
            if (protocol.startsWith("ctr-") || protocol.startsWith("cbc-")) {
                hasDrm = true
                continue
            }
            val presetBase = preset.substringBefore('_')
            if (presetBase == "abr") continue

            val streamUrl = try {
                (callApi(formatUrl, query) as? JsonObject)?.str("url")
            } catch (_: ExtractionError) {
                null
            } ?: continue

            val mimeType = transcoding.obj("format")?.str("mime_type")
            val codec = mimeType?.let { CODECS.find(it)?.groupValues?.get(1) }
            var ext = when (codec?.take(4)) {
                "mp4a" -> "m4a"
                "opus" -> "opus"
                else -> ExtractorUtils.mimetype2ext(mimeType)
            }
            if (ext.isNullOrBlank() || ext == "m3u8") ext = presetBase
            val isPremium = transcoding.str("quality") == "hq"
            val abr = Regex("(\\d+)k$").find(preset)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: ABR_IN_URL.find(streamUrl)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: if (isPremium && preset.contains("aac")) 256.0 else null
            val isPreview = transcoding["snipped"]?.let { (it as? JsonPrimitive)?.booleanOrNull } == true ||
                formatUrl.contains("/preview/") ||
                PREVIEW_IN_URL.containsMatchIn(streamUrl)

            formats += MediaFormat(
                formatId = joinNonempty(protocol, preset, if (isPreview) "preview" else null),
                url = streamUrl,
                ext = ext,
                acodec = codec,
                vcodec = MediaFormat.CODEC_NONE,
                abr = abr,
                protocol = if (protocol == "hls" || protocol == "hls-aes") "m3u8_native" else "http",
                container = if (ext == "m4a") "m4a_dash" else null,
                quality = when {
                    isPremium -> "5"
                    abr != null && abr >= 160 -> "0"
                    else -> "-1"
                },
                formatNote = if (isPremium) "Premium" else null,
                preference = if (isPreview) -10 else null,
            )
        }

        if (formats.isEmpty() && hasDrm) {
            throw ExtractionError.NoFormats("The SoundCloud track's formats need DRM playback.")
        }
        if (formats.isEmpty()) throw ExtractionError.NoFormats()

        val user = info.obj("user")
        return InfoDict(
            id = trackId,
            title = info.str("title"),
            description = info.str("description"),
            duration = info.number("duration")?.div(1000),
            uploader = user?.str("username"),
            channelId = user?.str("id") ?: user?.number("id")?.toLong()?.toString(),
            uploadDate = info.str("created_at")?.let(ExtractorUtils::unifiedStrdate),
            viewCount = info.number("playback_count")?.toLong(),
            thumbnails = thumbnails(info),
            formats = formats.distinctBy { it.url },
            webpageUrl = webpageUrl,
            extractor = "soundcloud",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `_extract_thumbnails` for the artwork size family. */
    private fun thumbnails(info: JsonObject): List<Thumbnail> {
        val artworkUrl = ExtractorUtils.urlOrNone(info.str("artwork_url"))
        val sourceUrl = artworkUrl ?: ExtractorUtils.urlOrNone(info.obj("user")?.str("avatar_url"))
        if (sourceUrl == null) return emptyList()
        val match = IMAGE_REPLACEMENT.find(sourceUrl) ?: return listOf(Thumbnail(url = sourceUrl))
        val ext = match.groups["ext"]?.value ?: "jpg"
        val thumbnails = mutableListOf<Thumbnail>()
        for ((id, size) in ARTWORK_MAP) {
            // The "original" thumbnail keeps the URL's extension; the rest are jpg.
            val thumbExt = if (id == "original") ext else "jpg"
            val url = IMAGE_REPLACEMENT.replace(sourceUrl) { "-$id.$thumbExt" }
            thumbnails += Thumbnail(
                url = url,
                id = id,
                width = size.takeIf { it > 0 }?.toLong(),
                height = size.takeIf { it > 0 }?.toLong(),
                preference = if (id == "original") 10 else null,
            )
        }
        return thumbnails
    }

    companion object {
        const val IE_KEY: String = "Soundcloud"

        private const val API_V2_BASE = "https://api-v2.soundcloud.com/"
        private const val BASE_URL = "https://soundcloud.com/"

        /** Upstream `_VALID_URL` for the permalink and API track forms. */
        val VALID_URL: Regex = Regex(
            "^(?:https?://)?" +
                "(?:(?:(?:www\\.|m\\.)?soundcloud\\.com/" +
                "(?!stations/track)" +
                "(?<uploader>[\\w\\d-]+)/" +
                "(?!(?:tracks|albums|sets(?:/.+?)?|reposts|likes|spotlight|comments)/?(?:\$|[?#]))" +
                "(?<title>[\\w\\d-]+)" +
                "(?:/(?<token>(?!(?:albums|sets|recommended))[^?]+?))?" +
                "(?:[?].*)?$)" +
                "|" +
                "(?:api(?:-v2)?\\.soundcloud\\.com/tracks/(?:soundcloud%3Atracks%3A)?(?<trackId>\\d+)" +
                "(?:/?\\?secret_token=(?<secretToken>[^&]+))?))",
        )

        private val SCRIPT_SOURCE = Regex("<script[^>]+src=\"([^\"]+)\"")
        private val CLIENT_ID = Regex("client_id\\s*:\\s*\"([0-9a-zA-Z]{32})\"")
        private val CODECS = Regex("codecs=\"([^\"]+)\"")
        private val ABR_IN_URL = Regex("\\.(\\d+)\\.(?:opus|mp3)[/?]")
        private val PREVIEW_IN_URL = Regex("/(?:preview|playlist)/0/30/")
        private val IMAGE_REPLACEMENT = Regex("-[0-9a-z]+\\.(?<ext>jpg|png)")
        private val ARTWORK_MAP = linkedMapOf(
            "mini" to 16,
            "tiny" to 20,
            "small" to 32,
            "badge" to 47,
            "t67x67" to 67,
            "large" to 100,
            "t300x300" to 300,
            "crop" to 400,
            "t500x500" to 500,
            "original" to 0,
        )
    }
}

/** Upstream `SoundcloudEmbedIE`: decode `?url=` and re-enter the registry. */
class SoundcloudEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override val displayName: String = "SoundCloud embed"

    override suspend fun extract(url: String): InfoDict {
        val raw = VALID_URL.find(url)?.groups?.get("id")?.value ?: throw ExtractionError.UnsupportedUrl()
        var apiUrl = percentDecode(raw)
        val secretToken = queryParam(url, "secret_token")
        if (secretToken != null && !apiUrl.contains("secret_token=")) {
            apiUrl += (if (apiUrl.contains('?')) "&" else "?") + "secret_token=" + secretToken
        }
        return InfoDict(
            id = apiUrl,
            webpageUrl = url,
            extractor = "soundcloud",
            extractorKey = IE_KEY,
            redirectUrl = apiUrl,
        )
    }

    companion object {
        const val IE_KEY: String = "SoundcloudEmbed"

        /** Upstream `_VALID_URL`. */
        val VALID_URL: Regex = Regex(
            "https?://(?:w|player|p)\\.soundcloud\\.com/player/?.*?\\burl=(?<id>.+)",
        )
    }
}

private fun varProtocol(transcoding: JsonObject, formatUrl: String): String {
    var protocol = transcoding.obj("format")?.str("protocol") ?: "http"
    if (protocol == "progressive") protocol = "http"
    if (protocol != "hls" && formatUrl.contains("/hls")) protocol = "hls"
    if (protocol == "encrypted-hls" || formatUrl.contains("/encrypted-hls")) protocol = "hls-aes"
    return protocol
}

private fun joinNonempty(vararg values: String?): String =
    values.filterNot { it.isNullOrBlank() }.joinToString("_")

/** The `url` query value of an embed URL, percent-decoded. */
private fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    for (pair in query.split('&')) {
        if (pair.substringBefore('=') == name) return percentDecode(pair.substringAfter('=', ""))
    }
    return null
}

/** A small `urllib.parse.unquote` subset; `+` decodes to a space. */
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

/** `urllib.parse.quote_plus`-style encoding for the query values. */
private fun percentEncode(value: String): String {
    val out = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xff
        val character = code.toChar()
        when {
            character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' -> out.append(character)
            character in "-_.~" -> out.append(character)
            character == ' ' -> out.append('+')
            else -> {
                val hex = "0123456789ABCDEF"
                out.append('%').append(hex[code ushr 4]).append(hex[code and 0x0f])
            }
        }
    }
    return out.toString()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
