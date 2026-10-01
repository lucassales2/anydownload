/*
 * Omny Studio extractors — AnyDownload
 *
 * Kotlin translation of the public page/API subset of `omnyfm.py` from
 * `yt_dlp/extractor/omnyfm.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-10-01.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `omnyfm.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the Next.js clip/program/playlist props, the mp3 row with vcodec
 * none, chapters, and the paged clips APIs for the playlist and show
 * listings (at most five pages eagerly). The `section_start` field,
 * categories, tags, episode/season numbers, and the file size are not
 * carried; an embed URL strips its query. No cookie, token, or signed media
 * URL is stored here.
 */
package com.anydownload.core.extract.omnyfm

import com.anydownload.core.extract.Chapter
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

private const val API_BASE = "https://api.omny.fm"
private const val BASE_URL = "https://omny.fm/shows"
private const val PAGE_SIZE = 100
private const val MAX_PAGES = 5

/** Upstream `OmnyfmIE`: one Omny clip page. */
class OmnyfmIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val uploaderId = match.groups["uploaderid"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val audioId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val clip = nextJsData(webpage)?.obj("props")?.obj("pageProps")?.obj("clip")
            ?: throw ExtractionError.Malformed("The Omny page had no clip data.")
        val audioUrl = clip.str("AudioUrl")
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: throw ExtractionError.NoFormats("The Omny clip had no audio URL.")
        val chapters = mutableListOf<Chapter>()
        for (element in clip.array("Chapters").orEmpty()) {
            val chapter = element as? JsonObject ?: continue
            val start = chapter.str("Position")?.let(ExtractorUtils::parseDuration) ?: continue
            chapters += Chapter(startTime = start, title = cleanHtml(chapter.str("Name")))
        }
        val publishedUrl = clip.str("PublishedUrl")
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        return InfoDict(
            id = audioId,
            title = cleanHtml(clip.str("Title")),
            description = cleanHtml(clip.str("Description")),
            duration = clip.number("DurationSeconds"),
            uploadDate = clip.str("PublishedUtc")?.let(ExtractorUtils::unifiedStrdate),
            uploader = cleanHtml(clip.obj("Program")?.str("Name")),
            channelId = uploaderId,
            thumbnails = listOfNotNull(
                clip.str("ImageUrl")?.substringBefore('?')?.let { Thumbnail(url = it) },
            ),
            chapters = chapters,
            formats = listOf(
                MediaFormat(
                    url = audioUrl,
                    ext = ExtractorUtils.determineExt(audioUrl, "mp3"),
                    vcodec = MediaFormat.CODEC_NONE,
                ),
            ),
            webpageUrl = publishedUrl ?: url,
            extractor = "omnyfm",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Omnyfm"

        val VALID_URL: Regex = Regex(
            "https?://omny\\.fm/shows/(?<uploaderid>[\\w-]+)/" +
                "(?<id>(?!playlists(?:[/?#\"']|$))[\\w-]+)(?:/embed)?(?=[?#\"']|$)",
        )
    }
}

/** Upstream `OmnyfmPlaylistBaseIE`: the shared clip-entry walk. */
abstract class OmnyfmPlaylistBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : InfoExtractor(ieKey = ieKey, http = http, validUrl = validUrl) {
    protected fun clipEntries(clips: JsonObject?, uploaderId: String): List<InfoEntry> =
        clips?.array("Clips").orEmpty().mapNotNull { element ->
            val slug = (element as? JsonObject)?.str("Slug") ?: return@mapNotNull null
            InfoEntry(url = "$BASE_URL/$uploaderId/$slug")
        }
}

/** Upstream `OmnyfmPlaylistIE`: a program's playlists or one playlist. */
class OmnyfmPlaylistIE(
    http: ExtractorHttp,
) : OmnyfmPlaylistBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val uploaderId = match.groups["uploaderid"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val playlistId = match.groups["id"]?.value
        val webpage = http.downloadWebpage(url)
        val pageProps = nextJsData(webpage)?.obj("props")?.obj("pageProps")
            ?: throw ExtractionError.Malformed("The Omny page had no page props.")

        if (playlistId == null) {
            val entries = pageProps.array("playlistsWithClips").orEmpty().mapNotNull { element ->
                val slug = (element as? JsonObject)?.obj("playlist")?.str("Slug")
                    ?: return@mapNotNull null
                InfoEntry(url = "$BASE_URL/$uploaderId/playlists/$slug")
            }
            return InfoDict(
                id = uploaderId,
                entries = entries,
                webpageUrl = url,
                extractor = "omnyfm:playlist",
                extractorKey = IE_KEY,
            )
        }

        val entries = mutableListOf<InfoEntry>()
        var clipId: String? = null
        var page = 1
        while (page <= MAX_PAGES) {
            val query = buildString {
                append("?direction=AfterExclusive&pageSize=$PAGE_SIZE")
                if (clipId != null) append("&clipId=$clipId")
            }
            val clips = http.downloadJson(
                "$API_BASE/programs/$uploaderId/playlists/$playlistId/clips$query",
            ) as? JsonObject ?: throw ExtractionError.Malformed("The Omny clips API was not an object.")
            entries += clipEntries(clips, uploaderId)
            if (clips.bool("NextClipsAvailable") != true) break
            clipId = (clips.array("Clips")?.lastOrNull() as? JsonObject)?.str("Id")
            if (clipId == null) break
            page++
        }
        val playlist = pageProps.obj("playlist")
        return InfoDict(
            id = playlistId,
            title = cleanHtml(playlist?.str("Title")),
            description = cleanHtml(playlist?.str("Description")),
            thumbnails = listOfNotNull(
                playlist?.str("ArtworkUrl")?.substringBefore('?')?.let { Thumbnail(url = it) },
            ),
            entries = entries,
            webpageUrl = playlist?.str("EmbedUrl")?.removeSuffix("/embed") ?: url,
            extractor = "omnyfm:playlist",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "OmnyfmPlaylist"

        val VALID_URL: Regex = Regex(
            "https?://omny\\.fm/shows/(?<uploaderid>[\\w-]+)/playlists" +
                "(?:/(?<id>[\\w-]+))?(?:/embed)?/?(?=[?#\"']|$)",
        )
    }
}

/** Upstream `OmnyfmShowIE`: a program's clip listing. */
class OmnyfmShowIE(
    http: ExtractorHttp,
) : OmnyfmPlaylistBaseIE(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val uploaderId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val webpage = http.downloadWebpage(url)
        val program = nextJsData(webpage)?.obj("props")?.obj("pageProps")?.obj("program")
            ?: throw ExtractionError.Malformed("The Omny page had no program data.")
        val organizationId = program.str("OrganizationId")
            ?: throw ExtractionError.Malformed("The Omny program had no organization id.")
        val programId = program.str("Id")
            ?: throw ExtractionError.Malformed("The Omny program had no id.")

        val entries = mutableListOf<InfoEntry>()
        var page = 0
        while (page < MAX_PAGES) {
            val clips = http.downloadJson(
                "$API_BASE/orgs/$organizationId/programs/$programId/clips?cursor=$page&pageSize=$PAGE_SIZE",
            ) as? JsonObject ?: throw ExtractionError.Malformed("The Omny clips API was not an object.")
            val pageEntries = clipEntries(clips, uploaderId)
            if (pageEntries.isEmpty()) break
            entries += pageEntries
            if (pageEntries.size < PAGE_SIZE) break
            page++
        }
        return InfoDict(
            id = uploaderId,
            title = cleanHtml(program.str("Name")),
            description = cleanHtml(program.str("Description")),
            thumbnails = listOfNotNull(
                program.str("ArtworkUrl")?.substringBefore('?')?.let { Thumbnail(url = it) },
            ),
            entries = entries,
            webpageUrl = url,
            extractor = "omnyfm:show",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "OmnyfmShow"

        val VALID_URL: Regex = Regex(
            "https?://omny\\.fm/shows/(?<id>[\\w-]+)/?(?:[?#]|$)",
        )
    }
}

// ------------------------------------------------------------------ helpers

/** Upstream `_search_nextjs_data`. */
private fun nextJsData(webpage: String): JsonObject? {
    val body = Regex("(?s)<script[^>]+id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>")
        .find(webpage)?.groupValues?.get(1) ?: return null
    return ExtractorUtils.parseJson(body) as? JsonObject
}

/** Upstream `clean_html`. */
private fun cleanHtml(value: String?): String? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return ExtractorUtils.unescapeHtml(text.replace(Regex("<[^>]*>"), " "))
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
