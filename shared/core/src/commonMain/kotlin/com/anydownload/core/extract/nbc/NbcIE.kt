/*
 * NBC extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `nbc.py` from
 * `yt_dlp/extractor/nbc.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `nbc.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: `NBCIE`'s GraphQL metadata and PRELOAD fallback, the NBCU TVE/deck
 * page walk (`_extract_nbcu_video`), the SMIL-to-HLS selection
 * (`_download_nbcu_smil_and_extract_m3u8_url`), the `NBCNewsIE` Next.js
 * videoAssets mapping, `NBCStationsIE`'s `data-videos`/`data-meta` and SMIL
 * path, and `NBCOlympicsIE`'s embed hand-off. Adobe Pass paths stop at the
 * typed login wall from `AdobePassIE`; the four upstream `_WORKING = False`
 * classes (NBCSports vplayer/site/stream and the Olympics stream) are
 * planned, not translated. Upstream fields the port's InfoDict does not
 * model (`media_type`, `categories`, `episode`, `season_number`,
 * `episode_number`, `series`, `creators`, `tags`, `location`,
 * `_old_archive_ids`) are dropped. Manifests are parsed at download time;
 * subtitle tracks come from the ThePlatform captions, not the HLS master.
 * No cookie, bearer token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.nbc

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.extract.theplatform.SmilManifest
import com.anydownload.core.extract.theplatform.ThePlatformBaseIE
import com.anydownload.core.extract.theplatform.encodeQueryValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Upstream `NBCUniversalBaseIE`: the NBCU page walk and the SMIL-to-HLS
 * selection shared by `NBCIE`, `BravoTVIE`, and `SyfyIE`.
 */
abstract class NBCUniversalBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : ThePlatformBaseIE(ieKey = ieKey, http = http, validUrl = validUrl) {

    /** Upstream `_download_nbcu_smil_and_extract_m3u8_url`. */
    private suspend fun smilM3u8Url(
        tpPath: String,
        query: Map<String, String>,
    ): String {
        val manifestQuery = query + mapOf(
            "format" to "SMIL",
            "manifest" to "m3u",
            "switch" to "HLSServiceSecure",
        )
        val xml = downloadSmil("https://link.theplatform.com/s/$tpPath", manifestQuery)
        SmilManifest.videoSources(xml)
            .firstOrNull { ExtractorUtils.determineExt(it, defaultExt = "") == "m3u8" }
            ?.let { return it }
        when (val exception = SmilManifest.exceptionValue(xml)) {
            "GeoLocationBlocked" -> throw ExtractionError.GeoRestricted(listOf("US"))
            null -> {}
            else -> throw ExtractionError.Unavailable(
                SmilManifest.refAbstract(xml) ?: "The ThePlatform manifest failed.",
            )
        }
        throw ExtractionError.NoFormats("No playable HLS stream was found in the SMIL manifest.")
    }

    /**
     * Upstream `_extract_nbcu_formats_and_subtitles`: the two-step `m3u+none`
     * and `mpeg4` SMIL calls, the `{folders}` template fill, and the DRM
     * check. The m3u8 master is recorded for the engine's download-time parse.
     */
    protected suspend fun nbcuHlsFormat(tpPath: String, videoId: String, auth: String?): MediaFormat {
        val query = linkedMapOf<String, String>()
        if (auth != null) query["auth"] = auth
        query["formats"] = "m3u+none,mpeg4"
        val original = smilM3u8Url(tpPath, query)
        var m3u8Url = original
        val match = NBCU_M3U8.matchEntire(original)
        if (match != null) {
            query["formats"] = "mpeg4"
            val template = smilM3u8Url(tpPath, query)
            m3u8Url = template.replace("{folders}", match.groups["folders"]?.value.orEmpty())
        }
        if (m3u8Url.contains("/mpeg_cenc") || m3u8Url.contains("/mpeg_cbcs")) {
            throw ExtractionError.NoFormats("This video is DRM protected.")
        }
        return MediaFormat(
            formatId = "hls",
            url = m3u8Url,
            ext = "mp4",
            protocol = "m3u8_native",
        )
    }

    /** Upstream `_extract_nbcu_video`. */
    protected suspend fun extractNbcuVideo(url: String, displayId: String): InfoDict {
        val webpage = http.downloadWebpage(url)
        val settings = extractJsonElement(
            webpage,
            Regex("<script[^>]*data-drupal-selector=\"drupal-settings-json\"[^>]*>"),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The NBC page had no drupal settings.")

        val accountPid: String
        val accountId: String
        val videoId: String
        val metadata: JsonObject?
        val tve = htmlStartTagByClass(webpage, "tve-video-deck-app")?.let(::tagAttributes)
        if (tve != null && tve.isNotEmpty()) {
            accountPid = tve["data-mpx-media-account-pid"] ?: tve["data-mpx-account-pid"]
                ?: throw ExtractionError.Malformed("The NBC TVE deck had no account pid.")
            accountId = tve["data-mpx-media-account-id"]
                ?: throw ExtractionError.Malformed("The NBC TVE deck had no account id.")
            metadata = tve["data-normalized-video"]?.let { raw ->
                ExtractorUtils.parseJson(ExtractorUtils.unescapeHtml(raw) ?: raw) as? JsonObject
            }
            videoId = tve["data-guid"] ?: metadata?.str("guid")
                ?: throw ExtractionError.Malformed("The NBC TVE deck had no video id.")
            if (tve["data-entitlement"] == "auth") {
                // Upstream builds the v-chip resource and then runs the Adobe
                // Pass credential flow; the port stops at the typed login wall.
                mvpdAuthRequired()
            }
        } else {
            val playlist = findLsPlaylist(settings)
                ?: throw ExtractionError.Malformed("The NBC page had no LS playlist.")
            videoId = playlist.str("defaultGuid")
                ?: throw ExtractionError.Malformed("The NBC LS playlist had no default guid.")
            accountPid = playlist.str("mpxMediaAccountPid") ?: playlist.str("mpxAccountPid")
                ?: throw ExtractionError.Malformed("The NBC LS playlist had no account pid.")
            accountId = playlist.str("mpxMediaAccountId")
                ?: throw ExtractionError.Malformed("The NBC LS playlist had no account id.")
            metadata = playlist.array("videos").orEmpty()
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.str("guid") == videoId }
        }

        val tpPath = "$accountPid/media/guid/$accountId/$videoId"
        val parsed = parseTheplatformMetadata(downloadTheplatformMetadata(tpPath, videoId, fatal = false))
        return InfoDict(
            id = videoId,
            title = metadata?.str("title") ?: parsed.title,
            description = metadata?.str("description") ?: parsed.description,
            duration = metadata?.number("durationInSeconds") ?: parsed.durationSeconds,
            uploadDate = metadata?.str("airDate")?.let(ExtractorUtils::unifiedStrdate) ?: parsed.uploadDate,
            uploader = parsed.uploader,
            ageLimit = parsed.ageLimit,
            thumbnails = thumbnailList(metadata?.str("thumbnailUrl") ?: parsed.thumbnailUrl),
            chapters = parsed.chapters,
            subtitles = parsed.subtitles,
            formats = listOf(nbcuHlsFormat(tpPath, videoId, null)),
            webpageUrl = url,
            extractor = "nbc",
            extractorKey = ieKey,
        )
    }

    private fun findLsPlaylist(settings: JsonObject): JsonObject? {
        val direct = settings.obj("ls_playlist")
        if (direct != null && direct.str("defaultGuid") != null) return direct
        return settings.array("ls_playlist").orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.str("defaultGuid") != null }
    }

    companion object {
        /** Upstream `_M3U8_RE`; the `folders` group is the template path. */
        private val NBCU_M3U8 = Regex(
            "https?://[^/?#]+/prod/[\\w-]+/(?<folders>[^?#]+/)cmaf/mpeg_(?:cbcs|cenc)\\w*/master_cmaf\\w*\\.m3u8",
        )
    }
}

/** Upstream `NBCIE`: nbc.com video pages through the friendship GraphQL API. */
class NBCIE(
    http: ExtractorHttp,
) : NBCUniversalBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val permalink = "http" + percentDecode(match.groups["permalink"]?.value.orEmpty())
        val fallbackId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()

        var videoData = graphqlMetadata(permalink)
        if (videoData == null || videoData.isEmpty()) {
            val webpage = http.downloadWebpage(url)
            val preload = extractJsonElement(webpage, Regex("<script>\\s*PRELOAD\\s*=")) as? JsonObject
            videoData = preload?.obj("pages")?.obj(urlPath(url))?.obj("base")?.obj("metadata")
        }
        val metadata = videoData?.takeIf { it.isNotEmpty() }
            ?: throw ExtractionError.Unavailable("The NBC video is not available through the page or API.")

        val videoId = metadata.str("mpxGuid") ?: fallbackId
        val accountId = metadata.str("mpxAccountId")
            ?: throw ExtractionError.Malformed("The NBC video had no account id.")
        val tpPath = "NnzsPC/media/guid/$accountId/$videoId"
        val parsed = parseTheplatformMetadata(downloadTheplatformMetadata(tpPath, videoId, fatal = false))
        if (metadata.bool("locked") == true) {
            mvpdAuthRequired()
        }
        return InfoDict(
            id = videoId,
            title = parsed.title ?: metadata.str("secondaryTitle"),
            description = metadata.str("description") ?: parsed.description,
            duration = parsed.durationSeconds,
            uploadDate = parsed.uploadDate,
            uploader = parsed.uploader,
            ageLimit = parsed.ageLimit ?: ExtractorUtils.parseAgeLimit(metadata.str("rating")),
            chapters = parsed.chapters,
            subtitles = parsed.subtitles,
            formats = listOf(nbcuHlsFormat(tpPath, videoId, null)),
            webpageUrl = url,
            extractor = "nbc",
            extractorKey = IE_KEY,
        )
    }

    private suspend fun graphqlMetadata(permalink: String): JsonObject? {
        val variables = buildJsonObject {
            put("name", permalink)
            put("oneApp", true)
            put("userId", "0")
        }.toString()
        val url = "https://friendship.nbc.co/v2/graphql?query=" + encodeQueryValue(GRAPHQL_QUERY) +
            "&variables=" + encodeQueryValue(variables)
        val json = http.downloadJson(url) as? JsonObject ?: return null
        return json.obj("data")?.obj("bonanzaPage")?.obj("metadata")
    }

    companion object {
        const val IE_KEY: String = "NBC"

        /** Upstream `_VALID_URL`; the `permalink` group is the GraphQL name. */
        val VALID_URL: Regex = Regex(
            "https?(?<permalink>://(?:www\\.)?nbc\\.com/(?:classic-tv/)?[^/?#]+/video/[^/?#]+/(?<id>\\w+))",
        )

        /** Upstream's `bonanzaPage` query, with inline values filled by variables. */
        private val GRAPHQL_QUERY = """
            query bonanzaPage(
              ${'$'}app: NBCUBrands! = nbc
              ${'$'}name: String!
              ${'$'}oneApp: Boolean
              ${'$'}platform: SupportedPlatforms! = web
              ${'$'}type: EntityPageType! = VIDEO
              ${'$'}userId: String!
            ) {
              bonanzaPage(
                app: ${'$'}app
                name: ${'$'}name
                oneApp: ${'$'}oneApp
                platform: ${'$'}platform
                type: ${'$'}type
                userId: ${'$'}userId
              ) {
                metadata {
                  ... on VideoPageData {
                    description
                    episodeNumber
                    keywords
                    locked
                    mpxAccountId
                    mpxGuid
                    rating
                    resourceId
                    seasonNumber
                    secondaryTitle
                    seriesShortTitle
                  }
                }
              }
            }
        """.trimIndent()
    }
}

/** Upstream `NBCNewsIE`: the Next.js `videoAssets` state of nbcnews.com. */
class NBCNewsIE(
    http: ExtractorHttp,
) : ThePlatformBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val next = extractJsonElement(webpage, Regex("<script[^>]*id=\"__NEXT_DATA__\"[^>]*>")) as? JsonObject
        val state = next?.obj("props")?.obj("initialState")
            ?: throw ExtractionError.Malformed("The NBC News page had no Next.js state.")
        val videoData = state.obj("video")?.obj("current")
            ?: state.obj("article")?.array("content")?.firstNotNullOfOrNull { content ->
                (content as? JsonObject)?.obj("primaryMedia")?.obj("video")
            }
            ?: throw ExtractionError.Unavailable("The NBC News page had no video data.")

        val title = videoData.obj("headline")?.str("primary") ?: videoId
        val formats = mutableListOf<MediaFormat>()
        for (element in videoData.array("videoAssets").orEmpty()) {
            val asset = element as? JsonObject ?: continue
            var publicUrl = ExtractorUtils.urlOrNone(asset.str("publicUrl")) ?: continue
            if (publicUrl.contains("://link.theplatform.com/")) {
                publicUrl = updateUrlQuery(publicUrl, "format", "redirect")
            }
            val formatId = asset.str("format")
            if (formatId == "M3U") {
                formats += MediaFormat(
                    formatId = "hls",
                    url = publicUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )
                continue
            }
            val tbr = asset.number("bitrate")?.div(1000.0)
            formats += MediaFormat(
                formatId = listOfNotNull(formatId, tbr?.toLong()?.toString()).joinToString("-"),
                url = publicUrl,
                ext = "mp4",
                width = asset.number("width")?.toLong(),
                height = asset.number("height")?.toLong(),
                tbr = tbr,
            )
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        for ((language, value) in videoData.obj("closedCaptioning").orEmpty()) {
            val url = ExtractorUtils.urlOrNone((value as? JsonPrimitive)?.content) ?: continue
            subtitles += SubtitleTrack(
                language = language.ifEmpty { "en" },
                formats = listOf(SubtitleFormat(ext = "vtt", url = url)),
            )
        }

        return InfoDict(
            id = videoId,
            title = title,
            description = videoData.obj("description")?.str("primary"),
            duration = parseDurationSeconds(videoData.str("duration")),
            uploadDate = videoData.str("datePublished")?.let(ExtractorUtils::unifiedStrdate),
            thumbnails = thumbnailList(videoData.obj("primaryImage")?.obj("url")?.str("primary")),
            subtitles = subtitles,
            formats = formats,
            webpageUrl = url,
            extractor = "nbc",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NBCNews"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:nbcnews|today|msnbc)\\.com/(?:[^/]+/)*(?:.*-)?(?<id>[^/?]+)",
        )
    }
}

/** Upstream `NBCOlympicsIE`: the page hands off to the ThePlatform player. */
class NBCOlympicsIE(
    http: ExtractorHttp,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val drupal = extractJsonElement(webpage, Regex("jQuery\\.extend\\(Drupal\\.settings\\s*,\\s*"))
            as? JsonObject
        var playerUrl = drupal?.obj("vod")?.str("iframe_url")
        if (playerUrl == null) {
            playerUrl = Regex("([\"'])embedUrl\\1\\s*:\\s*([\"'])(?<embedUrl>.+?)\\2")
                .find(webpage)?.groups?.get("embedUrl")?.value
        }
        val resolved = ExtractorUtils.urlOrNone(
            playerUrl?.replace("vplayer.nbcolympics.com", "player.theplatform.com"),
        ) ?: throw ExtractionError.Malformed("The NBC Olympics page had no embed URL.")
        return InfoDict(
            id = displayId,
            webpageUrl = url,
            redirectUrl = resolved,
            extractor = "nbc",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "NBCOlympics"

        val VALID_URL: Regex = Regex("https?://www\\.nbcolympics\\.com/videos?/(?<id>[0-9a-z-]+)")
    }
}

/** Upstream `NBCStationsIE`: the local-station pages and their SMIL video list. */
class NBCStationsIE(
    http: ExtractorHttp,
) : ThePlatformBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val channel = match.groups["site"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val videoId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)

        val nbcData = extractJsonElement(
            webpage,
            Regex("<script>\\s*var\\s+nbc\\s*=|Object\\.assign\\(nbc,"),
        ) as? JsonObject
        val pdkAcct = nbcData?.str("pdkAcct") ?: "Yh1nAC"
        val fwSsid = nbcData?.obj("video")?.str("fwSSID")

        val videosJson = extractJsonElement(webpage, Regex("data-videos=\"\\[")) as? JsonObject
        val metaJson = extractJsonElement(webpage, Regex("data-meta=\"")) as? JsonObject
        val videoData = JsonObject((videosJson ?: JsonObject(emptyMap())) + (metaJson ?: JsonObject(emptyMap())))
        if (videoData.isEmpty()) {
            throw ExtractionError.Unavailable("No video metadata was found in the page.")
        }

        val isLive = videoData.number("mpx_is_livestream")?.toInt() == 1
        val videoMeta = videoData.obj("video")?.obj("meta")
        var playerId: String?
        val info = linkedMapOf<String, Any?>()
        val formats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()
        var duration: Double? = null

        if (isLive) {
            playerId = firstOf(videoData, videoMeta, listOf("mpx_m3upid", "mpx_pid", "pid_streaming_web_medium"))
            info["title"] = "$channel livestream"
        } else {
            playerId = firstOf(videoData, videoMeta, listOf("pid_streaming_web_high", "mpx_pid"))
            val dateRaw = videoData.str("date_string") ?: videoData.str("date_gmt")
            val dateString = if (dateRaw != null) {
                ExtractorUtils.searchRegex("datetime=\"([^\"]+)\"", dateRaw, default = null)
            } else {
                nbcData?.obj("dataLayer")?.obj("adobe")?.let { adobe ->
                    adobe.str("prop70") ?: adobe.str("eVar70") ?: adobe.str("eVar59")
                }
            }
            val videoUrl = firstOf(videoData, videoMeta, listOf("mp4_url"))
            if (videoUrl != null) {
                val ext = ExtractorUtils.determineExt(videoUrl, defaultExt = "mp4")
                val height = Regex("\\d+-(\\d+)p").find(videoUrl.substringAfterLast('/'))
                    ?.groups?.get(1)?.value
                formats += MediaFormat(
                    formatId = "http-$ext",
                    url = videoUrl,
                    ext = ext,
                    width = RESOLUTIONS[height]?.toLong(),
                    height = height?.toLong(),
                )
            }
            info["title"] = videoData.str("title")
                ?: nbcData?.obj("dataLayer")?.obj("adobe")?.let { adobe ->
                    adobe.str("contenttitle") ?: adobe.str("title") ?: adobe.str("prop22")
                }
            info["description"] = videoData.str("summary") ?: videoData.str("excerpt")
                ?: videoData.str("video_hero_text")
                ?: cleanHtml(nbcData?.obj("dataLayer")?.str("summary"))
            info["uploadDate"] = dateString?.let(ExtractorUtils::unifiedStrdate)
        }

        if (playerId != null && fwSsid != null) {
            val query = mapOf(
                "formats" to "MPEG-DASH none,M3U none,MPEG-DASH none,MPEG4,MP3",
                "format" to "SMIL",
                "fwsitesection" to fwSsid,
                "fwNetworkID" to (nbcData?.obj("video")?.str("fwNetworkID") ?: "382114"),
                "pprofile" to "ots_desktop_html",
                "sensitive" to "false",
                "w" to "1920",
                "h" to "1080",
                "mode" to if (isLive) "LIVE" else "on-demand",
                "vpaid" to "script",
                "schema" to "2.0",
                "sdk" to "PDK 6.1.3",
            )
            val smil = runCatching { downloadSmil("https://link.theplatform.com/s/$pdkAcct/$playerId", query) }
                .getOrNull()
            if (smil != null) {
                val videos = SmilManifest.videos(smil)
                duration = videos.firstOrNull()?.durationMs?.div(1000.0)
                for (video in videos) {
                    val ext = ExtractorUtils.mimetype2ext(video.type)
                        ?: ExtractorUtils.determineExt(video.src, defaultExt = "")
                    if (ext == "m3u8") {
                        formats += MediaFormat(
                            formatId = "hls",
                            url = video.src,
                            ext = "mp4",
                            protocol = "m3u8_native",
                            width = video.width,
                            height = video.height,
                        )
                    } else {
                        formats += MediaFormat(
                            formatId = "https-$ext",
                            url = video.src,
                            ext = ext,
                            width = video.width,
                            height = video.height,
                        )
                    }
                }
                for ((src, language, type) in SmilManifest.textStreams(smil)) {
                    subtitles += SubtitleTrack(
                        language = language ?: "en",
                        formats = listOf(SubtitleFormat(ExtractorUtils.mimetype2ext(type) ?: "vtt", src)),
                    )
                }
            }
        }

        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("No video content was found in the page.")
        }
        return InfoDict(
            id = videoId,
            title = info["title"] as? String,
            description = info["description"] as? String,
            duration = duration,
            uploadDate = info["uploadDate"] as? String,
            channel = channel,
            channelId = nbcData?.str("callLetters"),
            uploader = nbcData?.str("on_air_name"),
            formats = formats,
            subtitles = subtitles,
            isLive = isLive,
            webpageUrl = url,
            extractor = "nbc",
            extractorKey = IE_KEY,
        )
    }

    /** Upstream `((None, ('video', 'meta')), keys...)` traversal. */
    private fun firstOf(video: JsonObject, meta: JsonObject?, keys: List<String>): String? {
        for (key in keys) video.str(key)?.let { return it }
        for (key in keys) meta?.str(key)?.let { return it }
        return null
    }

    companion object {
        const val IE_KEY: String = "NBCStations"

        private val RESOLUTIONS = mapOf(
            "1080" to 1920,
            "720" to 1280,
            "540" to 960,
            "360" to 640,
            "234" to 416,
        )

        private const val DOMAINS =
            "nbcbayarea|nbcboston|nbcchicago|nbcconnecticut|nbcdfw|nbclosangeles|nbcmiami|nbcnewyork|" +
                "nbcphiladelphia|nbcsandiego|nbcwashington|necn|telemundo52|telemundoarizona|" +
                "telemundochicago|telemundonuevainglaterra"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?<site>$DOMAINS)\\.com/(?:[^/?#]+/)*(?<id>[^/?#]+)/?(?:$|[#?])",
        )
    }
}

/** Upstream `BravoTVIE`: bravotv.com and oxygen.com pages. */
class BravoTVIE(
    http: ExtractorHttp,
) : NBCUniversalBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return extractNbcuVideo(url, displayId)
    }

    companion object {
        const val IE_KEY: String = "BravoTV"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?(?:bravotv|oxygen)\\.com/(?:[^/?#]+/)+(?<id>[^/?#]+)",
        )
    }
}

/** Upstream `SyfyIE`: syfy.com episode and video pages. */
class SyfyIE(
    http: ExtractorHttp,
) : NBCUniversalBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val displayId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        return extractNbcuVideo(url, displayId)
    }

    companion object {
        const val IE_KEY: String = "Syfy"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?syfy\\.com/[^/?#]+/(?:season-\\d+/episode-\\d+/(?:videos/)?|videos/)" +
                "(?<id>[^/?#]+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/**
 * Upstream `_search_json` subset: the balanced JSON value after [marker].
 * The scanner tracks braces, brackets, and string escapes.
 */
private fun extractJsonElement(html: String, marker: Regex): JsonElement? {
    val match = marker.find(html) ?: return null
    var index = match.range.last + 1
    while (index < html.length && html[index].isWhitespace()) index++
    val opening = html.getOrNull(index) ?: return null
    if (opening != '{' && opening != '[') return null
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

            '{', '[' -> depth++
            '}', ']' -> {
                depth--
                if (depth == 0) {
                    return ExtractorUtils.parseJson(html.substring(index, position + 1))
                }
            }
        }
    }
    return null
}

/** Upstream `get_element_html_by_class`: the start tag carrying the class. */
private fun htmlStartTagByClass(html: String, className: String): String? {
    val classValue = Regex("\\bclass\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    for (tag in Regex("<[a-zA-Z][^>]*>").findAll(html)) {
        val value = tag.value
        val classes = classValue.find(value)?.let {
            it.groupValues[1].ifEmpty { it.groupValues[2] }
        } ?: continue
        if (classes.split(Regex("\\s+")).any { it == className }) return value
    }
    return null
}

/** Upstream `extract_attributes` for one start tag. */
private fun tagAttributes(tag: String): Map<String, String> = buildMap {
    val attribute = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")
    for (match in attribute.findAll(tag)) {
        val value = match.groupValues[2].ifEmpty {
            match.groupValues[3].ifEmpty { match.groupValues[4] }
        }
        put(match.groupValues[1].lowercase(), value)
    }
}

/** `urllib.parse.unquote` for the percent-escaped permalink. */
private fun percentDecode(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%' && index + 2 < value.length) {
            val code = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (code != null) {
                append(code.toChar())
                index += 3
                continue
            }
        }
        append(character)
        index++
    }
}

/** Upstream `update_url_query` for one key. */
private fun updateUrlQuery(url: String, key: String, value: String): String {
    val separator = if (url.contains('?')) '&' else '?'
    return "$url$separator${encodeQueryValue(key)}=${encodeQueryValue(value)}"
}

/** Upstream `urlparse(url).path`. */
private fun urlPath(url: String): String {
    val afterScheme = url.substringAfter("://", url)
    val clean = afterScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
    return "/$clean"
}

/** Upstream `parse_duration` for the ISO forms the Next.js state carries. */
private fun parseDurationSeconds(value: String?): Double? {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    ExtractorUtils.parseIso8601(text)?.let { return it.toDouble() }
    return text.toDoubleOrNull()
}

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    val withoutTags = text.replace(Regex("<[^>]*>"), " ")
    return ExtractorUtils.unescapeHtml(withoutTags)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
}

private fun thumbnailList(url: String?): List<Thumbnail> =
    ExtractorUtils.urlOrNone(url)?.let { listOf(Thumbnail(url = it)) }.orEmpty()

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
