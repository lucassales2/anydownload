/*
 * Arte TV extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `arte.py` from
 * `yt_dlp/extractor/arte.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `arte.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public player config API (streams, version-code language
 * preference, subtitles, metadata, chapters), the embed redirect, and the
 * category page scan. `ArteTVPlaylistIE` matches and fails typed: its
 * playlist API needs an embedded bearer token the port does not carry. No
 * token or signed media URL is stored here.
 */
package com.anydownload.core.extract.arte

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Chapter
import com.anydownload.core.extract.Thumbnail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val LANGUAGES = "fr|de|en|es|it|pl"
private const val API_BASE = "https://api.arte.tv/api/player/v2"

private val LANG_MAP = mapOf(
    "fr" to "F",
    "de" to "A",
    "en" to "E[ANG]",
    "es" to "E[ESP]",
    "it" to "E[ITA]",
    "pl" to "E[POL]",
    "mul" to "EU",
)

/** Upstream `ArteTVIE`: a video. */
class ArteTVIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val lang = match.groups["lang"]?.value ?: match.groups["lang2"]?.value
            ?: throw ExtractionError.UnsupportedUrl()
        val languageCode = LANG_MAP[lang]
        val config = http.downloadJson(
            "$API_BASE/config/$lang/$videoId",
            headers = mapOf("x-validated-age" to "18"),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Arte config API was not an object.")
        val attributes = config.obj("data")?.obj("attributes")
            ?: throw ExtractionError.Malformed("The Arte config had no attributes.")
        if (attributes.obj("restriction")?.obj("geoblocking")?.primitiveText("restrictedArea")
            ?.lowercase() == "true"
        ) {
            throw ExtractionError.GeoRestricted()
        }
        if (attributes.obj("rights") == null) {
            throw ExtractionError.Unavailable(
                "The video is not available in this language edition of Arte or the rights expired.",
            )
        }
        val formats = mutableListOf<MediaFormat>()
        val secondaryFormats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()
        for (element in attributes.array("streams").orEmpty()) {
            val stream = element as? JsonObject ?: continue
            val version = stream.array("versions")?.firstOrNull() as? JsonObject ?: continue
            val versionCode = version.obj("eStat")?.str("ml5") ?: "?"
            val languagePreference = versionLanguagePreference(versionCode, languageCode)
            val shortLabel = version.str("shortLabel") ?: "?"
            val protocol = stream.str("protocol") ?: continue
            val streamUrl = stream.str("url") ?: continue
            when {
                protocol.contains("HLS") -> {
                    val format = MediaFormat(
                        formatId = versionCode,
                        url = streamUrl,
                        ext = "mp4",
                        protocol = "m3u8_native",
                        formatNote = "${version.str("label") ?: "unknown"} [$shortLabel]",
                        languagePreference = languagePreference.toDouble(),
                    )
                    if (shortLabel.startsWith("cc") || shortLabel.startsWith("OGsub")) {
                        secondaryFormats += format
                    } else {
                        formats += format
                    }
                }

                protocol == "HTTPS" || protocol == "RTMP" -> formats += MediaFormat(
                    formatId = "$protocol-$versionCode",
                    url = streamUrl,
                    formatNote = "${version.str("label") ?: "unknown"} [$shortLabel]",
                    languagePreference = languagePreference.toDouble(),
                )

                else -> Unit
            }
        }
        formats += secondaryFormats
        val metadata = attributes.obj("metadata")
            ?: throw ExtractionError.Malformed("The Arte config had no metadata.")
        val chapters = attributes.obj("chapters")?.array("elements").orEmpty().mapNotNull { element ->
            val chapter = element as? JsonObject ?: return@mapNotNull null
            Chapter(
                startTime = chapter.number("startTime"),
                title = chapter.str("title"),
            )
        }
        return InfoDict(
            id = metadata.str("providerId"),
            title = metadata.str("subtitle") ?: metadata.str("title"),
            description = metadata.str("description"),
            duration = metadata.obj("duration")?.number("seconds"),
            uploadDate = ExtractorUtils.unifiedStrdate(attributes.obj("rights")?.str("begin")),
            isLive = attributes.bool("live") == true,
            chapters = chapters,
            thumbnails = metadata.array("images").orEmpty().mapNotNull { element ->
                val image = element as? JsonObject ?: return@mapNotNull null
                image.str("url")?.let { Thumbnail(id = image.str("caption"), url = it) }
            },
            formats = formats,
            subtitles = subtitles,
            webpageUrl = metadata.obj("link")?.str("url") ?: url,
            extractor = "arte.tv",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ArteTV"

        val VALID_URL: Regex = Regex(
            "(?:https?://(?:(?:www\\.)?arte\\.tv/(?<lang>$LANGUAGES)/videos" +
                "|api\\.arte\\.tv/api/player/v\\d+/config/(?<lang2>$LANGUAGES))" +
                "|arte://program)/(?<id>\\d{6}-\\d{3}-[AF]|LIVE)",
        )
    }
}

/** Upstream `ArteTVEmbedIE`: the player embed URLs. */
class ArteTVEmbedIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val jsonUrl = queryParam(url, "json_url")
            ?: throw ExtractionError.Malformed("The embed URL had no json_url.")
        return InfoDict(
            id = Regex("([\\w-]+)$").find(jsonUrl)?.groupValues?.get(1) ?: jsonUrl,
            redirectUrl = jsonUrl,
            webpageUrl = url,
            extractor = "arte.tv:embed",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ArteTVEmbed"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?arte\\.tv/player/v\\d+/index\\.php\\?.*?\\bjson_url=.+",
        )
    }
}

/** Upstream `ArteTVPlaylistIE`: the playlist API (bearer-token wall). */
class ArteTVPlaylistIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(
        "The Arte playlist API needs an embedded bearer token the port does not carry.",
    )

    companion object {
        const val IE_KEY: String = "ArteTVPlaylist"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?arte\\.tv/(?<lang>$LANGUAGES)/videos/(?<id>RC-\\d{6})")
    }
}

/** Upstream `ArteTVCategoryIE`: the category page scans. */
class ArteTVCategoryIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override fun suitable(url: String): Boolean =
        super.suitable(url) && !ArteTVIE.VALID_URL.containsMatchIn(url) &&
            !ArteTVPlaylistIE.VALID_URL.containsMatchIn(url)

    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val lang = match.groups["lang"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val entries = mutableListOf<InfoEntry>()
        for (videoMatch in Regex(
            "<a\\b[^>]*?href\\s*=\\s*(?:\"|'|\\b)(https?://www\\.arte\\.tv/$lang/videos/[\\w/-]+)",
        ).findAll(webpage)) {
            val videoUrl = videoMatch.groupValues[1]
            if (videoUrl == url) continue
            if (ArteTVIE.VALID_URL.containsMatchIn(videoUrl) ||
                ArteTVPlaylistIE.VALID_URL.containsMatchIn(videoUrl)
            ) {
                entries += InfoEntry(url = videoUrl)
            }
        }
        val title = ExtractorUtils.searchRegex(
            "(?s)<title[^>]*>([^<]+)</title>",
            webpage,
            default = null,
        )?.split('|')?.firstOrNull()?.trim()
        return InfoDict(
            id = playlistId,
            title = title,
            description = ExtractorUtils.htmlSearchMeta(webpage, "og:description"),
            entries = entries,
            webpageUrl = url,
            extractor = "arte.tv:category",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "ArteTVCategory"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?arte\\.tv/(?<lang>$LANGUAGES)/videos/(?<id>[\\w-]+(?:/[\\w-]+)*)/?\\s*$",
        )
    }
}

// ------------------------------------------------------------------ helpers

private val VERSION_CODE = Regex(
    "V(?<originalVoice>O?)(?<vlang>[FA]|E\\[[A-Z]+\\]|EU)?(?<audioDesc>AUD|)" +
        "(?:(?<hasSub>-ST)(?<sdhSub>M?)(?<subLang>[FA]|E\\[[A-Z]+\\]|EU))?",
)

private fun versionLanguagePreference(versionCode: String, languageCode: String?): Int {
    val match = VERSION_CODE.matchAt(versionCode, 0) ?: return -1
    val bits = listOf(
        match.groups["vlang"]?.value == languageCode,
        match.groups["audioDesc"]?.value.isNullOrEmpty(),
        match.groups["originalVoice"]?.value == "O",
        match.groups["subLang"]?.value == languageCode,
        match.groups["hasSub"]?.value == null,
        match.groups["sdhSub"]?.value.isNullOrEmpty(),
    )
    return bits.joinToString("") { if (it) "1" else "0" }.toIntOrNull(2) ?: -1
}

private fun queryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    return query.split('&').firstOrNull { it.substringBefore('=') == name }?.substringAfter('=', "")
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.let {
        when (it.content) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
