/*
 * NFL extractors — AnyDownload
 *
 * Kotlin translation of the public page subset of `nfl.py` from
 * `yt_dlp/extractor/nfl.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nfl.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public `video-config-<uuid>` page scan and the direct item
 * URLs (m3u8, audio, plain) it carries. A config whose first playlist item
 * has an `mcpID`, and every NFL+ replay/episode URL, matches and fails typed
 * because the account token API is not translated. No client secret, API
 * key, cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.nfl

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

private const val HOST = "(?:(?:nfl|buffalobills|miamidolphins|patriots|newyorkjets|baltimoreravens|bengals|" +
    "clevelandbrowns|steelers|houstontexans|colts|jaguars|(?:titansonline|tennesseetitans)|denverbroncos|" +
    "(?:kc)?chiefs|raiders|chargers|dallascowboys|giants|philadelphiaeagles|(?:redskins|washingtonfootball)|" +
    "chicagobears|detroitlions|packers|vikings|atlantafalcons|panthers|neworleanssaints|buccaneers|azcardinals|" +
    "(?:stlouis|the)rams|49ers|seahawks)\\.com|.+?\\.clubs\\.nfl\\.com)"
private const val URL_BASE = "https?://(?<host>(?:www\\.)?(?:$HOST))/"

private const val VIDEO_CONFIG_REGEX =
    "(?s)<script[^>]+id=\"[^\"]*video-config-[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}[^\"]*\"[^>]*>\\s*(\\{.+?\\});?\\s*</script>"

/** Shared upstream `NFLBaseIE` behaviour. */
abstract class NflBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    /** Upstream `_parse_video_config` for the public (non-mcpID) items. */
    protected fun parseVideoConfig(configText: String, displayId: String): InfoDict {
        val config = ExtractorUtils.parseJson(configText) as? JsonObject
            ?: throw ExtractionError.Malformed("The NFL video config was not JSON.")
        val isLive = (config["live"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
        val item = config.array("playlist")?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Malformed("The NFL video config had no playlist item.")
        if (item.str("mcpID") != null) {
            throw ExtractionError.LoginRequired(
                "The NFL mcpID playback path needs the account token API, which the port does not carry.",
            )
        }
        val id = item.str("id") ?: item.str("entityId")
            ?: throw ExtractionError.Malformed("The NFL video config had no item id.")
        val itemUrl = item.str("url")
            ?: throw ExtractionError.Malformed("The NFL video config had no item URL.")
        val ext = ExtractorUtils.determineExt(itemUrl)
        val formats = mutableListOf<MediaFormat>()
        if (ext == "m3u8") {
            formats += MediaFormat(
                formatId = "hls",
                url = itemUrl,
                ext = "mp4",
                protocol = "m3u8_native",
            )
        } else {
            formats += MediaFormat(
                url = itemUrl,
                ext = ext,
                vcodec = if (item.boolean("audio") == true) MediaFormat.CODEC_NONE else null,
            )
        }
        val imageUrl = item.str("imageSrc") ?: item.str("posterImage")
        return InfoDict(
            id = id,
            title = item.str("title"),
            description = cleanHtml(item.str("description")),
            isLive = isLive,
            thumbnails = listOfNotNull(imageUrl?.let { Thumbnail(url = it) }),
            formats = formats,
            webpageUrl = null,
            extractor = "nfl.com",
            extractorKey = ieKey,
        )
    }
}

/** Upstream `NFLIE`: an nfl.com (or club) video/audio page. */
class NFLIE(
    http: ExtractorHttp,
) : NflBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val config = Regex(VIDEO_CONFIG_REGEX).find(webpage)?.groupValues?.get(1)
            ?: throw ExtractionError.Malformed("The NFL page had no video config.")
        return parseVideoConfig(config, displayId).copy(
            webpageUrl = url,
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NFL"

        val VALID_URL: Regex = Regex(URL_BASE + "(?:videos?|listen|audio)/(?<id>[^/#?\u0026]+)")
    }
}

/** Upstream `NFLArticleIE`: an nfl.com (or club) news article. */
class NFLArticleIE(
    http: ExtractorHttp,
) : NflBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val entries = mutableListOf<InfoEntry>()
        for (match in Regex(VIDEO_CONFIG_REGEX).findAll(webpage)) {
            val info = runCatching { parseVideoConfig(match.groupValues[1], displayId) }.getOrNull()
                ?: continue
            entries += InfoEntry(id = info.id, title = info.title, url = url)
        }
        val title = cleanHtml(elementByClass(webpage, "nfl-c-article__title"))
            ?: metaContent(webpage, "og:title")
            ?: metaContent(webpage, "twitter:title")
        return InfoDict(
            id = displayId,
            title = title,
            entries = entries,
            webpageUrl = url,
            extractor = "nfl.com:article",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NFLArticle"

        val VALID_URL: Regex = Regex(URL_BASE + "news/(?<id>[^/#?\u0026]+)")
    }
}

/** Upstream `NFLPlusReplayIE`: an NFL+ game replay. */
class NFLPlusReplayIE(
    http: ExtractorHttp,
) : NflBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "The NFL+ replay listing needs the account cookie and token API, which the port does not carry.",
        )
    }

    companion object {
        const val IE_KEY: String = "NFLPlusReplay"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?nfl\\.com/plus/games/(?<slug>[\\w-]+)(?:/(?<id>\\d+))?")
    }
}

/** Upstream `NFLPlusEpisodeIE`: an NFL+ episode. */
class NFLPlusEpisodeIE(
    http: ExtractorHttp,
) : NflBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        throw ExtractionError.LoginRequired(
            "The NFL+ episode API needs the account cookie and token API, which the port does not carry.",
        )
    }

    companion object {
        const val IE_KEY: String = "NFLPlusEpisode"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?nfl\\.com/plus/episodes/(?<id>[\\w-]+)")
    }
}

// ------------------------------------------------------------------ helpers

private fun elementByClass(webpage: String, className: String): String? {
    val match = Regex(
        "(?s)<(\\w+)\\b[^>]+class\\s*=\\s*[\"'](?:[\\w-]+\\s+)*?" +
            Regex.escape(className) + "(?:\\s+[\\w-]+)*[\"'][^>]*>(.*?)</\\1>",
    ).find(webpage) ?: return null
    return match.groupValues[2]
}

private fun metaContent(webpage: String, property: String): String? {
    val name = Regex.escape(property)
    val patterns = listOf(
        "<meta[^>]+(?:property|name)\\s*=\\s*[\"']$name[\"'][^>]+content\\s*=\\s*[\"']([^\"']*)[\"']",
        "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]+(?:property|name)\\s*=\\s*[\"']$name[\"']",
    )
    for (pattern in patterns) {
        Regex(pattern).find(webpage)?.let { return it.groupValues[1] }
    }
    return null
}

private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), ""))
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
