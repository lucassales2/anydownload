/*
 * Polskie Radio extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `polskieradio.py` from
 * `yt_dlp/extractor/polskieradio.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `polskieradio.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the legacy article/category HTML (og tags, `this-article` body, the
 * `data-media` players, the `source:` audition record), the Next.js article
 * data with its Audio attachments, the player bundle channel list plus the
 * public stations API, and the podcast list/track APIs. The LP3 list API
 * needs an `x-api-key` header the port does not embed, so `PolskieRadioAuditionIE`
 * matches and fails typed. The legacy billennium-tab and ASP.NET postback
 * pagination are not translated (first listing page only); the podcast list
 * walks at most ten pages eagerly; ISM/f4m live streams are skipped. No
 * cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.polskieradio

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

/** Upstream `PolskieRadioLegacyIE`: the legacy article pages. */
class PolskieRadioLegacyIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val finalUrl = http.followRedirects(url)
        if (PolskieRadioIE.VALID_URL.containsMatchIn(finalUrl)) {
            return InfoDict(
                id = playlistId,
                webpageUrl = url,
                redirectUrl = finalUrl,
                extractor = "polskieradio:legacy",
                extractorKey = IE_KEY,
            )
        }
        val webpage = http.downloadWebpage(url)
        val content = ExtractorUtils.searchRegex(
            "(?s)<div[^>]+class=\"\\s*this-article\\s*\"[^>]*>(.+?)<div[^>]+class=\"tags\"[^>]*>",
            webpage,
            default = null,
        )
        val uploadDate = ExtractorUtils.unifiedStrdate(
            ExtractorUtils.searchRegex(
                "(?s)<span[^>]+id=\"datetime2\"[^>]*>(.+?)</span>",
                webpage,
                default = null,
            ),
        )
        val thumbnail = ExtractorUtils.htmlSearchMeta(webpage, "og:image")
        val title = ExtractorUtils.htmlSearchMeta(webpage, "og:title")?.trim()
        val description = ExtractorUtils.htmlSearchMeta(webpage, "og:description")
            ?.replace('\u00a0', ' ')
            ?.trim()
        if (content == null) {
            val recordUrl = ExtractorUtils.searchRegex(
                "source:\\s*'(//static\\.prsa\\.pl/[^']+)'",
                webpage,
                default = null,
            ) ?: throw ExtractionError.Malformed("The legacy article had no audio source.")
            return InfoDict(
                id = playlistId,
                title = title,
                description = description,
                url = protoRelative(recordUrl),
                ext = "mp3",
                uploadDate = uploadDate,
                thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
                extractor = "polskieradio:legacy",
                extractorKey = IE_KEY,
            )
        }
        return InfoDict(
            id = playlistId,
            title = title,
            description = description,
            entries = extractWebpagePlayerEntries(content, playlistId, title).toList(),
            webpageUrl = url,
            extractor = "polskieradio:legacy",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PolskieRadioLegacy"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?polskieradio(?:24)?\\.pl/\\d+/\\d+/[Aa]rtykul/(?<id>\\d+)",
        )
    }
}

/** Upstream `PolskieRadioIE`: the new Next.js article sites. */
class PolskieRadioIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val playlistId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val pageProps = nextJsPageProps(webpage)
            ?: throw ExtractionError.Malformed("The article page had no Next.js data.")
        val articleData = pageProps.obj("data")?.obj("articleData")
            ?: pageProps.obj("post")?.obj("data")
            ?: throw ExtractionError.Malformed("The article page had no article data.")
        val title = stripOrNone(articleData.str("title"))
        val description = stripOrNone(articleData.str("lead"))
        val entries = articleData.array("attachments").orEmpty().mapNotNull { element ->
            val attachment = element as? JsonObject ?: return@mapNotNull null
            if (attachment.str("fileType") != "Audio") return@mapNotNull null
            val file = attachment.str("file") ?: return@mapNotNull null
            InfoEntry(
                id = ENTRY_ID.find(file)?.groupValues?.get(1),
                url = file,
                title = stripOrNone(attachment.str("description")) ?: title,
            )
        }
        if (entries.isEmpty()) {
            return InfoDict(
                id = playlistId,
                title = title,
                description = description,
                entries = extractWebpagePlayerEntries(
                    articleData.str("content").orEmpty(),
                    playlistId,
                    title,
                ).toList(),
                webpageUrl = url,
                extractor = "polskieradio",
                extractorKey = IE_KEY,
            )
        }
        return InfoDict(
            id = playlistId,
            title = title,
            description = description,
            entries = entries,
            webpageUrl = url,
            extractor = "polskieradio",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PolskieRadio"

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/]+\\.)?(?:polskieradio(?:24)?|radiokierowcow)\\.pl/artykul/(?<id>\\d+)",
        )
    }
}

/** Upstream `PolskieRadioAuditionIE`: the LP3 audition listings (key wall). */
class PolskieRadioAuditionIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict = throw ExtractionError.Unavailable(
        "The LP3 audition list API needs an x-api-key header that the port does not embed.",
    )

    companion object {
        const val IE_KEY: String = "PolskieRadioAudition"

        val VALID_URL: Regex = Regex(
            "https?://(?:[^/]+\\.)?polskieradio\\.pl/audycj[ae]/(?<id>\\d+)",
        )
    }
}

/** Upstream `PolskieRadioCategoryIE`: the legacy category listings. */
class PolskieRadioCategoryIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val categoryId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val finalUrl = http.followRedirects(url)
        if (PolskieRadioAuditionIE.VALID_URL.containsMatchIn(finalUrl)) {
            return InfoDict(
                id = categoryId,
                webpageUrl = url,
                redirectUrl = finalUrl,
                extractor = "polskieradio:category",
                extractorKey = IE_KEY,
            )
        }
        val webpage = http.downloadWebpage(url)
        val title = ExtractorUtils.searchRegex(
            "<title>([^<]+)(?: - [^<]+ - [^<]+| w [Pp]olskie[Rr]adio\\.pl\\s*)</title>",
            webpage,
            default = null,
        )?.trim()
        val entries = mutableListOf<InfoEntry>()
        for (match in ARTICLE_LINK.findAll(webpage)) {
            val attributes = parseTagAttributes(match.groupValues[1])
            val href = attributes["href"] ?: continue
            entries += InfoEntry(
                id = match.groupValues[2],
                url = urlJoin(url, href),
                title = attributes["title"],
            )
        }
        for (match in SPAN_MEDIA.findAll(webpage)) {
            val media = ExtractorUtils.parseJson(ExtractorUtils.unescapeHtml(match.groupValues[1]))
                as? JsonObject ?: continue
            entries += InfoEntry(
                id = media.str("uid"),
                url = media.str("file"),
                title = percentDecode(media.str("title")),
            )
        }
        return InfoDict(
            id = categoryId,
            title = title,
            entries = entries,
            webpageUrl = url,
            extractor = "polskieradio:category",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PolskieRadioCategory"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?polskieradio\\.pl/(?:\\d+(?:,[^/]+)?/|[^/]+/Tag)(?<id>\\d+)",
        )

        private val ARTICLE_LINK = Regex(
            "(?s)<article[^>]+>.*?(<a[^>]+href=[\"'](?:(?:https?)?://[^/]+)?/\\d+/\\d+/Artykul/(\\d+)[^>]+>).*?</article>",
        )
        private val SPAN_MEDIA = Regex("<span data-media=(\\{[^ ]+\\})")
    }
}

/** Upstream `PolskieRadioPlayerIE`: the live antenna streams. */
class PolskieRadioPlayerIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val channelUrl = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val playerCode = http.downloadWebpage(PLAYER_URL, maxBytes = 4 * 1024 * 1024)
        val channelListRaw = ExtractorUtils.searchRegex(
            ";var r=\"anteny\",a=(\\[.+?\\])},",
            playerCode,
            default = null,
        ) ?: throw ExtractionError.Malformed("The player bundle had no channel list.")
        val channelList = ExtractorUtils.parseJson(ExtractorUtils.jsToJson(channelListRaw)) as? JsonArray
            ?: throw ExtractionError.Malformed("The player channel list was not JSON.")
        val channel = channelList.mapNotNull { it as? JsonObject }
            .firstOrNull { it.str("url") == channelUrl }
            ?: throw ExtractionError.Unavailable("Channel not found.")
        val stationList = http.downloadJson(
            STATIONS_API_URL,
            headers = mapOf(
                "Accept" to "application/json",
                "Referer" to url,
                "Origin" to BASE_URL,
            ),
        ) as? JsonArray ?: throw ExtractionError.Malformed("The stations API was not a list.")
        val wantedName = channel.str("streamName") ?: channel.str("name")
        val station = stationList.mapNotNull { it as? JsonObject }
            .firstOrNull { it.str("Name") == wantedName }
            ?: throw ExtractionError.Unavailable("Station not found even though the channel was extracted.")
        val formats = mutableListOf<MediaFormat>()
        for (element in station.array("Streams").orEmpty()) {
            val raw = (element as? JsonPrimitive)?.content ?: continue
            val streamUrl = protoRelative(raw)
            when {
                streamUrl.endsWith("/playlist.m3u8") -> formats += MediaFormat(
                    formatId = "hls",
                    url = streamUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                streamUrl.endsWith("/manifest.f4m") || streamUrl.endsWith("/Manifest") -> Unit
                else -> formats += MediaFormat(url = streamUrl)
            }
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The station carried no playable stream.")
        }
        return InfoDict(
            id = channel.number("id")?.toLong()?.toString() ?: channelUrl,
            title = channel.str("name") ?: channel.str("streamName"),
            formats = formats,
            thumbnails = listOf(Thumbnail(url = "$BASE_URL/images/$channelUrl-color-logo.png")),
            isLive = true,
            webpageUrl = url,
            extractor = "polskieradio:player",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "PolskieRadioPlayer"

        val VALID_URL: Regex = Regex("https?://player\\.polskieradio\\.pl/anteny/(?<id>[^/]+)")

        private const val BASE_URL = "https://player.polskieradio.pl"
        private const val PLAYER_URL = "https://player.polskieradio.pl/main.bundle.js"
        private const val STATIONS_API_URL = "https://apipr.polskieradio.pl/api/stacje"
    }
}

/** Upstream `PolskieRadioPodcastListIE`: the podcast episode lists. */
class PolskieRadioPodcastListIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val podcastId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = callApi(podcastId, 1)
            ?: throw ExtractionError.Unavailable("The podcast API returned no data.")
        val itemCount = data.number("itemCount")?.toLong() ?: 0
        val totalPages = minOf(((itemCount + PAGE_SIZE - 1) / PAGE_SIZE).toInt(), MAX_PAGES)
        val entries = mutableListOf<InfoEntry>()
        for (page in 1..maxOf(totalPages, 1)) {
            val pageData = if (page == 1) data else callApi(podcastId, page) ?: break
            for (element in pageData.array("items").orEmpty()) {
                val episode = element as? JsonObject ?: continue
                val parsed = parsePodcastEpisode(episode) ?: continue
                entries += InfoEntry(id = parsed.id, url = parsed.url, title = parsed.title)
            }
        }
        return InfoDict(
            id = data.str("id") ?: podcastId,
            title = data.str("title"),
            description = data.str("description"),
            uploader = data.str("announcer"),
            entries = entries,
            webpageUrl = url,
            extractor = "polskieradio:podcast:list",
            extractorKey = IE_KEY,
        )
    }

    private suspend fun callApi(podcastId: String, page: Int): JsonObject? {
        val body = http.downloadJson(
            "$API_BASE/Podcasts/$podcastId/?pageSize=$PAGE_SIZE&page=$page",
        ) as? JsonObject
        return body
    }

    companion object {
        const val IE_KEY: String = "PolskieRadioPodcastList"

        val VALID_URL: Regex = Regex("https?://podcasty\\.polskieradio\\.pl/podcast/(?<id>\\d+)")
        private const val PAGE_SIZE = 10
        private const val MAX_PAGES = 10
    }
}

/** Upstream `PolskieRadioPodcastIE`: a single podcast track. */
class PolskieRadioPodcastIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val podcastId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val data = http.downloadJson(
            "$API_BASE/audio",
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"guids":["$podcastId"]}""".encodeToByteArray(),
        ) as? JsonArray
        val episode = data?.firstOrNull() as? JsonObject
            ?: throw ExtractionError.Unavailable("The podcast track was not found.")
        return parsePodcastEpisode(episode)
            ?: throw ExtractionError.Malformed("The podcast track had no audio URL.")
    }

    companion object {
        const val IE_KEY: String = "PolskieRadioPodcast"

        val VALID_URL: Regex = Regex(
            "https?://podcasty\\.polskieradio\\.pl/track/(?<id>[a-f\\d]{8}(?:-[a-f\\d]{4}){4}[a-f\\d]{8})",
        )
    }
}

// ------------------------------------------------------------------ helpers

private const val API_BASE = "https://apipodcasts.polskieradio.pl/api"
private val ENTRY_ID = Regex("([a-f\\d]{8}-(?:[a-f\\d]{4}-){3}[a-f\\d]{12})")

/** Upstream `PolskieRadioBaseIE._extract_webpage_player_entries`. */
private fun extractWebpagePlayerEntries(
    webpage: String,
    playlistId: String,
    title: String?,
): Sequence<InfoEntry> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<InfoEntry>()
    for (match in Regex("<[^>]+data-media=[\"']?(\\{[^>]+\\})[\"']?").findAll(webpage)) {
        val media = ExtractorUtils.parseJson(ExtractorUtils.unescapeHtml(match.groupValues[1])) as? JsonObject
            ?: continue
        val file = media.str("file") ?: continue
        if (media.str("desc") == null) continue
        val mediaUrl = protoRelative(file)
        if (!seen.add(mediaUrl)) continue
        out += InfoEntry(
            id = media.number("id")?.toLong()?.toString() ?: playlistId,
            url = mediaUrl,
            title = percentDecode(media.str("desc")) ?: title,
        )
    }
    return out.asSequence()
}

/** Upstream `PolskieRadioPodcastBaseIE._parse_episode`. */
private fun parsePodcastEpisode(data: JsonObject): InfoDict? {
    val url = data.str("url") ?: return null
    return InfoDict(
        id = data.str("guid"),
        title = data.str("title"),
        description = data.str("description"),
        duration = data.number("length"),
        uploadDate = ExtractorUtils.unifiedStrdate(data.str("publishDate")),
        formats = listOf(
            MediaFormat(
                url = url,
                ext = ExtractorUtils.determineExt(url),
                filesize = data.number("fileSize")?.toLong(),
            ),
        ),
        thumbnails = listOfNotNull(data.str("image")?.let { Thumbnail(url = it) }),
        extractor = "polskieradio:podcast",
    )
}

private val NEXT_DATA = Regex(
    "<script[^>]+id=\"__NEXT_DATA__\"[^>]*>(.+?)</script>",
    RegexOption.DOT_MATCHES_ALL,
)

private fun nextJsPageProps(html: String): JsonObject? {
    val raw = NEXT_DATA.find(html)?.groupValues?.get(1) ?: return null
    val root = ExtractorUtils.parseJson(raw) as? JsonObject ?: return null
    return root.obj("props")?.obj("pageProps")
}

private val TAG_ATTRIBUTE = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

private fun parseTagAttributes(tag: String): Map<String, String> {
    val out = linkedMapOf<String, String>()
    for (match in TAG_ATTRIBUTE.findAll(tag)) {
        out[match.groupValues[1].lowercase()] = match.groupValues[2].ifEmpty { match.groupValues[3] }
    }
    return out
}

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    val origin = Regex("^(https?://[^/]+)").find(base)?.groupValues?.get(1) ?: return href
    return if (href.startsWith("/")) origin + href else "$base/$href"
}

private fun protoRelative(url: String): String =
    if (url.startsWith("//")) "https:$url" else url

private fun percentDecode(value: String?): String? {
    val text = value ?: return null
    return runCatching {
        buildString {
            var i = 0
            while (i < text.length) {
                if (text[i] == '%' && i + 2 < text.length) {
                    val code = text.substring(i + 1, i + 3).toIntOrNull(16)
                    if (code != null) {
                        append(code.toByte().toInt().toChar())
                        i += 3
                        continue
                    }
                }
                append(text[i])
                i++
            }
        }
    }.getOrDefault(text)
}

private fun stripOrNone(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
