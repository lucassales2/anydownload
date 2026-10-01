/*
 * Mixcloud extractors — AnyDownload
 *
 * Kotlin translation of the public GraphQL subset of `mixcloud.py` from
 * `yt_dlp/extractor/mixcloud.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `mixcloud.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public app.mixcloud.com GraphQL lookups (cloudcast, user
 * listings, playlists), the XOR/base64 stream URL cipher (a pure data
 * transformation with the upstream constant key), and the HLS/direct rows.
 * Upstream passes `impersonate=True` to the GraphQL call; the port sends a
 * plain request, so a Cloudflare challenge would fail typed. DASH manifests
 * are skipped, an exclusive track fails typed, and the port does not carry
 * comment bodies, tags, or artist lists. No cookie, user token, or private
 * URL is stored here.
 */
package com.anydownload.core.extract.mixcloud

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

private const val GRAPHQL_URL = "https://app.mixcloud.com/graphql"
private const val DECRYPTION_KEY = "IFYOUWANTTHEARTISTSTOGETPAIDDONOTDOWNLOADFROMMIXCLOUD"
private const val MAX_PAGES = 5

/** Upstream `MixcloudBaseIE`: the GraphQL lookup. */
abstract class MixcloudBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected suspend fun callApi(
        objectType: String,
        objectFields: String,
        username: String,
        slug: String? = null,
    ): JsonObject? {
        val lookupKey = "${objectType}Lookup"
        val query = "{\n  $lookupKey(lookup: {username: \"$username\"" +
            (slug?.let { ", slug: \"$it\"" } ?: "") + "}) {\n    $objectFields\n  }\n}"
        val encoded = encodeQueryValue(query)
        val response = try {
            http.downloadJson("$GRAPHQL_URL?query=$encoded") as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        return response?.obj("data")?.obj(lookupKey)
    }

    /** Upstream `_decrypt_xor_cipher`. */
    protected fun decryptXorCipher(key: String, ciphertext: String): String {
        val out = StringBuilder()
        for ((index, character) in ciphertext.withIndex()) {
            out.append((character.code xor key[index % key.length].code).toChar())
        }
        return out.toString()
    }

    protected fun decryptStreamUrl(value: String): String? = try {
        val decoded = kotlin.io.encoding.Base64.Default.decode(value).decodeToString()
        decryptXorCipher(DECRYPTION_KEY, decoded)
    } catch (error: IllegalArgumentException) {
        null
    }
}

/** Upstream `MixcloudIE`: one cloudcast. */
class MixcloudIE(
    http: ExtractorHttp,
) : MixcloudBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val username = decodeUrlComponent(match.groupValues[1])
        val slug = decodeUrlComponent(match.groupValues[2])
        val trackId = "${username}_$slug"
        val cloudcast = callApi(
            "cloudcast",
            "audioLength description featuringArtistList isExclusive name owner { displayName url username } " +
                "picture(width: 1024, height: 1024) { url } plays publishDate streamInfo { dashUrl hlsUrl url } " +
                "restrictedReason",
            username,
            slug,
        ) ?: throw ExtractionError.Unavailable("Track not found")
        when (cloudcast.str("restrictedReason")) {
            "tracklist" -> throw ExtractionError.GeoRestricted(emptyList())
            "repeat_play" -> throw ExtractionError.Unavailable(
                "You have reached your play limit for this track",
            )

            null -> Unit
            else -> throw ExtractionError.Unavailable("Track is restricted")
        }
        val streamInfo = cloudcast.obj("streamInfo") ?: JsonObject(emptyMap())
        val formats = mutableListOf<MediaFormat>()
        for (key in listOf("url", "hlsUrl", "dashUrl")) {
            val encrypted = streamInfo.str(key) ?: continue
            val decrypted = decryptStreamUrl(encrypted) ?: continue
            when (key) {
                "hlsUrl" -> formats += MediaFormat(
                    formatId = "hls",
                    url = decrypted,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                "dashUrl" -> Unit // MPEG-DASH manifests are not translated.

                else -> formats += MediaFormat(
                    formatId = "http",
                    url = decrypted,
                    vcodec = MediaFormat.CODEC_NONE,
                )
            }
        }
        if (formats.isEmpty() && cloudcast.boolean("isExclusive") == true) {
            throw ExtractionError.LoginRequired()
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The Mixcloud cloudcast returned no playable stream.")
        }
        val owner = cloudcast.obj("owner")
        return InfoDict(
            id = trackId,
            title = cloudcast.str("name"),
            description = cloudcast.str("description"),
            duration = cloudcast.number("audioLength"),
            uploadDate = ExtractorUtils.unifiedStrdate(cloudcast.str("publishDate")),
            uploader = owner?.str("displayName"),
            channel = owner?.str("displayName"),
            viewCount = cloudcast.number("plays")?.toLong(),
            thumbnails = listOfNotNull(
                cloudcast.obj("picture")?.str("url")?.let { Thumbnail(url = it) },
            ),
            formats = formats,
            webpageUrl = url,
            extractor = "mixcloud",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Mixcloud"

        val VALID_URL: Regex = Regex(
            "https?://(?:(?:www|beta|m)\\.)?mixcloud\\.com/([^/]+)/" +
                "(?!stream|uploads|favorites|listens|playlists)([^/]+)",
        )
    }
}

/** Upstream `MixcloudUserIE`: a user listing. */
class MixcloudUserIE(
    http: ExtractorHttp,
) : MixcloudBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val username = decodeUrlComponent(match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl())
        val listType = match.groups["type"]?.value ?: "uploads"
        val playlistId = "${username}_$listType"
        val entries = mutableListOf<InfoEntry>()
        var after: String? = null
        var title: String? = null
        var description: String? = null
        var page = 0
        while (page < MAX_PAGES) {
            val filter = after?.let { ", after: \"$it\"" } ?: ""
            val user = callApi(
                "user",
                "displayName biog $listType(first: 100$filter) { edges { node { slug url owner { username } } } " +
                    "pageInfo { endCursor hasNextPage } }",
                username,
            ) ?: break
            title = title ?: user.str("displayName")
            description = description ?: user.str("biog")
            val items = user.obj(listType) ?: break
            val edges = items.array("edges").orEmpty()
            if (edges.isEmpty()) break
            for (element in edges) {
                val node = (element as? JsonObject)?.obj("node") ?: continue
                val cloudcastUrl = node.str("url") ?: continue
                entries += InfoEntry(id = node.str("slug"), url = cloudcastUrl)
            }
            val pageInfo = items.obj("pageInfo") ?: break
            if (pageInfo.boolean("hasNextPage") != true) break
            after = pageInfo.str("endCursor") ?: break
            page++
        }
        return InfoDict(
            id = playlistId,
            title = title?.let { "$it ($listType)" },
            description = description,
            entries = entries,
            webpageUrl = url,
            extractor = "mixcloud:user",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MixcloudUser"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?mixcloud\\.com/(?<id>[^/]+)/(?<type>uploads|favorites|listens|stream)?/?$",
        )
    }
}

/** Upstream `MixcloudPlaylistIE`: a playlist. */
class MixcloudPlaylistIE(
    http: ExtractorHttp,
) : MixcloudBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val user = decodeUrlComponent(match.groups["user"]?.value ?: throw ExtractionError.UnsupportedUrl())
        val playlist = decodeUrlComponent(
            match.groups["playlist"]?.value ?: throw ExtractionError.UnsupportedUrl(),
        )
        val playlistId = "${user}_$playlist"
        val entries = mutableListOf<InfoEntry>()
        var after: String? = null
        var title: String? = null
        var description: String? = null
        var page = 0
        while (page < MAX_PAGES) {
            val filter = after?.let { ", after: \"$it\"" } ?: ""
            val data = callApi(
                "playlist",
                "name description items(first: 100$filter) { edges { node { cloudcast { slug url owner " +
                    "{ username } } } } pageInfo { endCursor hasNextPage } }",
                user,
                playlist,
            ) ?: break
            title = title ?: data.str("name")
            description = description ?: data.str("description")
            val items = data.obj("items") ?: break
            val edges = items.array("edges").orEmpty()
            if (edges.isEmpty()) break
            for (element in edges) {
                val node = (element as? JsonObject)?.obj("node")?.obj("cloudcast") ?: continue
                val cloudcastUrl = node.str("url") ?: continue
                entries += InfoEntry(id = node.str("slug"), url = cloudcastUrl)
            }
            val pageInfo = items.obj("pageInfo") ?: break
            if (pageInfo.boolean("hasNextPage") != true) break
            after = pageInfo.str("endCursor") ?: break
            page++
        }
        return InfoDict(
            id = playlistId,
            title = title,
            description = description,
            entries = entries,
            webpageUrl = url,
            extractor = "mixcloud:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MixcloudPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?mixcloud\\.com/(?<user>[^/]+)/playlists/(?<playlist>[^/]+)/?$",
        )
    }
}

// ------------------------------------------------------------------ helpers

internal fun encodeQueryValue(value: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
    val hex = "0123456789ABCDEF"
    val out = StringBuilder()
    for (byte in value.encodeToByteArray()) {
        val character = byte.toInt().toChar()
        when {
            character in unreserved -> out.append(character)
            character == ' ' -> out.append('+')
            else -> out.append('%').append(hex[(byte.toInt() shr 4) and 0xF])
                .append(hex[byte.toInt() and 0xF])
        }
    }
    return out.toString()
}

private fun decodeUrlComponent(value: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                out.append(hex.toChar())
                i += 3
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
