/*
 * Mediaset extractors — AnyDownload
 *
 * Kotlin translation of the public ThePlatform subset of `mediaset.py` from
 * `yt_dlp/extractor/mediaset.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `mediaset.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the ThePlatform eu metadata and SMIL calls (MPEG4/MPEG-DASH/M3U
 * formats via the shared SmilManifest parser), the all-programs feed
 * metadata, and the show page/sub-brand listings. DRM (`_sampleaes/`)
 * manifests are skipped and a geo or release error fails typed. The port
 * does not carry series/season/episode fields or chapters from the feed, so
 * they are dropped. No cookie, token, or private URL is stored here.
 */
package com.anydownload.core.extract.mediaset

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.theplatform.SmilManifest
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Thumbnail
import com.anydownload.core.extract.theplatform.ThePlatformBaseIE
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val TP_PATH_PREFIX = "PR1GhC/media/guid/2702976343/"
private const val FEED_BASE =
    "https://feed.entertainment.tv.theplatform.eu/f/PR1GhC/mediaset-prod-all-programs-v2"
private const val PAGE_SIZE = 25
private const val MAX_PAGES = 5
private const val ASSET_TYPES =
    "geoNo:HD,browser,geoIT|geoNo:HD,geoIT|geoNo:SD,browser,geoIT|geoNo:SD,geoIT|geoNo|HD|SD"

/** Upstream `MediasetIE`: a Mediaset video. */
open class MediasetIE(
    http: ExtractorHttp,
    ieKey: String = IE_KEY,
    validUrl: Regex = VALID_URL,
) : ThePlatformBaseIE(ieKey = ieKey, http = http, validUrl = validUrl) {
    override suspend fun extract(url: String): InfoDict {
        val guid = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val tpPath = TP_PATH_PREFIX + guid
        val metadata = try {
            http.downloadJson("https://link.theplatform.eu/s/$tpPath?format=preview") as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        val parsed = parseTheplatformMetadata(metadata)
        val formats = mutableListOf<MediaFormat>()
        val subtitles = mutableListOf<SubtitleTrack>()
        var firstError: ExtractionError? = null
        var geoError: ExtractionError? = null
        for (format in listOf("MPEG4", "MPEG-DASH", "M3U")) {
            val xml = try {
                downloadSmil(
                    "http://link.theplatform.eu/s/$tpPath",
                    mapOf("mbr" to "true", "formats" to format, "assetTypes" to ASSET_TYPES),
                )
            } catch (error: Exception) {
                if (error is ExtractionError && geoError == null && error is ExtractionError.GeoRestricted) {
                    geoError = error
                }
                if (error is ExtractionError && firstError == null) firstError = error
                continue
            }
            if (SmilManifest.exceptionValue(xml) == "GeoLocationBlocked") {
                if (geoError == null) geoError = ExtractionError.GeoRestricted()
                continue
            }
            if (SmilManifest.exceptionValue(xml) != null) {
                if (firstError == null) {
                    firstError = ExtractionError.Unavailable(
                        SmilManifest.refAbstract(xml) ?: "The Mediaset video is unavailable.",
                    )
                }
                continue
            }
            for (video in SmilManifest.videos(xml)) {
                if ("_sampleaes/" in video.src) continue
                val ext = ExtractorUtils.determineExt(video.src)
                formats += MediaFormat(
                    url = video.src,
                    ext = if (ext == "m3u8") "mp4" else ext,
                    protocol = if (ext == "m3u8") "m3u8_native" else null,
                    width = video.width,
                    height = video.height,
                )
            }
            for ((src, lang, type) in SmilManifest.textStreams(xml)) {
                subtitles += SubtitleTrack(
                    language = lang ?: "en",
                    formats = listOf(
                        SubtitleFormat(ext = ExtractorUtils.mimetype2ext(type) ?: "vtt", url = src),
                    ),
                )
            }
        }
        if (formats.isEmpty() && (geoError != null || firstError != null)) {
            throw geoError ?: firstError!!
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The Mediaset SMIL manifests returned no playable format.")
        }
        var title = parsed.title
        var description = parsed.description
        var uploader = parsed.uploader
        var thumbnail = parsed.thumbnailUrl
        var viewCount: Long? = null
        val feed = try {
            http.downloadJson("$FEED_BASE/guid/-/$guid") as? JsonObject
        } catch (error: ExtractionError) {
            null
        }
        if (feed != null) {
            val publishInfo = feed.obj("mediasetprogram\$publishInfo")
            description = description ?: feed.str("description") ?: feed.str("longDescription")
            uploader = publishInfo?.str("description") ?: uploader
            viewCount = feed.str("mediasetprogram\$numberOfViews")?.toLongOrNull()
            for ((key, value) in feed.obj("thumbnails").orEmpty()) {
                if (!key.startsWith("image_keyframe_poster-")) continue
                thumbnail = (value as? JsonObject)?.str("url") ?: thumbnail
                break
            }
        }
        return InfoDict(
            id = guid,
            title = title,
            description = description,
            duration = parsed.durationSeconds,
            uploadDate = parsed.uploadDate,
            uploader = uploader,
            viewCount = viewCount,
            ageLimit = parsed.ageLimit,
            chapters = parsed.chapters,
            thumbnails = listOfNotNull(thumbnail?.let { Thumbnail(url = it) }),
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "mediaset",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Mediaset"

        val VALID_URL: Regex = Regex(
            "(?:mediaset:|https?://(?:\\w+\\.)+mediaset\\.it/" +
                "(?:(?:video|on-demand|movie)/(?:[^/]+/)+[^/]+_|" +
                "player/(?:v\\d+/)?index\\.html\\?\\S*?\\bprogramGuid=))" +
                "(?<id>F[0-9A-Z]{15})",
        )
    }
}

/** Upstream `MediasetShowIE`: a show page or sub-brand listing. */
class MediasetShowIE(
    http: ExtractorHttp,
) : MediasetIE(http, ieKey = "MediasetShow", validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val seasonId = match.groups["st"]?.value
        val subBrand = match.groups["sb"]?.value
        if (subBrand == null) {
            val page = http.downloadWebpage(url)
            val entries = mutableListOf<InfoEntry>()
            for (href in Regex("href=\"([^<>=]+SE\\d{12},ST\\d{12},sb\\d{9})\">[^<]+<").findAll(page)) {
                entries += InfoEntry(url = urlJoin("https://mediasetinfinity.mediaset.it", href.groupValues[1]))
            }
            val title = Regex("(?s)<title[^>]*>(.*?)</title>").find(page)?.groupValues?.get(1)
                ?.split('|')?.firstOrNull()?.trim()
            return InfoDict(
                id = seasonId ?: playlistId,
                title = title,
                entries = entries,
                webpageUrl = url,
                extractor = "mediaset:show",
                extractorKey = IE_KEY,
            )
        }
        val entries = mutableListOf<InfoEntry>()
        var title: String? = null
        var page = 0
        while (page < MAX_PAGES) {
            val lower = page * PAGE_SIZE + 1
            val upper = lower + PAGE_SIZE - 1
            val content = try {
                http.downloadJson(
                    "$FEED_BASE?byCustomValue=%7BsubBrandId%7D%7B$subBrand%7D" +
                        "&sort=:publishInfo_lastPublished%7Cdesc,tvSeasonEpisodeNumber%7Cdesc" +
                        "&range=$lower-$upper",
                ) as? JsonObject
            } catch (error: Exception) {
                break
            } ?: break
            val chunk = content.array("entries").orEmpty()
            if (chunk.isEmpty()) break
            for (element in chunk) {
                val entry = element as? JsonObject ?: continue
                val guid = entry.str("guid") ?: continue
                if (title == null) title = entry.str("mediasetprogram\$subBrandDescription")
                entries += InfoEntry(id = guid, url = "mediaset:$guid")
            }
            page++
        }
        return InfoDict(
            id = subBrand,
            title = title,
            entries = entries,
            webpageUrl = url,
            extractor = "mediaset:show",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "MediasetShow"

        val VALID_URL: Regex = Regex(
            "https?://(\\w+\\.)+mediaset\\.it/" +
                "(?:(?:fiction|programmi-tv|serie-tv|kids)/(?:.+?/)?(?:[a-z-]+)_SE(?<id>\\d{12})" +
                "(?:,ST(?<st>\\d{12}))?(?:,sb(?<sb>\\d{9}))?)$",
        )
    }
}

// ------------------------------------------------------------------ helpers

private fun urlJoin(base: String, href: String): String {
    if (href.startsWith("http://") || href.startsWith("https://")) return href
    return if (href.startsWith("/")) base + href else "$base/$href"
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
