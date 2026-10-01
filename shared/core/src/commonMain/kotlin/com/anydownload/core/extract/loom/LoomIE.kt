/*
 * Loom extractors — AnyDownload
 *
 * Kotlin translation of the public GraphQL/page subset of `loom.py` from
 * `yt_dlp/extractor/loom.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `loom.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the public GraphQL calls (GetVideoSSR, GetVideoSource,
 * FetchVideoTranscript, FetchChapters), the raw/transcoded URL endpoints,
 * m3u8/mpd/plain formats, VTT subtitles, and the chapter text parser. The
 * folder listing walks the public folders API with a depth cap. Password-
 * protected videos fail typed (the port has no video-password option). No
 * cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.loom

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorUtils
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.InfoEntry
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.MediaFormat
import com.anydownload.core.extract.SubtitleFormat
import com.anydownload.core.extract.SubtitleTrack
import com.anydownload.core.extract.Chapter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Upstream `LoomIE`: a shared video. */
class LoomIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val metadataResponse = graphql(http, "GetVideoSSR", videoId, GET_VIDEO_SSR)
        val metadata = (metadataResponse?.obj("data")?.obj("getVideo"))
            ?: throw ExtractionError.Malformed("The Loom metadata call returned no video.")
        if (metadata.str("__typename") == "VideoPasswordMissingOrIncorrect") {
            throw ExtractionError.Unavailable(
                "This Loom video is password-protected and the port has no video-password option.",
            )
        }
        val videoData = graphql(http, "GetVideoSource", videoId, GET_VIDEO_SOURCE)
        val chapterData = graphql(http, "FetchChapters", videoId, FETCH_CHAPTERS)
        val duration = metadata.obj("video_properties")?.number("duration")?.toLong()
        val formats = mutableListOf<MediaFormat>()
        val rawUrl = urlApi(http, videoId, "raw-url")
        addFormats(formats, rawUrl, "raw", 1)
        val transcodedUrl = urlApi(http, videoId, "transcoded-url")
        addFormats(formats, transcodedUrl, "transcoded", -1)
        val cdnUrl = videoData?.obj("data")?.obj("getVideo")?.obj("nullableRawCdnUrl")?.str("url")
        if (cdnUrl != null && cdnUrl != rawUrl && cdnUrl != transcodedUrl) {
            addFormats(formats, cdnUrl, "cdn", 0)
        }
        if (formats.isEmpty()) {
            throw ExtractionError.NoFormats("The Loom API returned no playable format.")
        }
        val subtitles = mutableListOf<SubtitleTrack>()
        val transcript = graphql(http, "FetchVideoTranscript", videoId, FETCH_TRANSCRIPT)
        val transcriptNode = transcript?.obj("data")?.obj("fetchVideoTranscript")
        val subUrl = transcriptNode?.str("source_url") ?: transcriptNode?.str("captions_source_url")
        if (subUrl != null) {
            subtitles += SubtitleTrack(language = "en", formats = listOf(SubtitleFormat(ext = "vtt", url = subUrl)))
        }
        val chapterText = chapterData?.obj("data")?.obj("fetchVideoChapters")?.str("content")
        return InfoDict(
            id = videoId,
            title = metadata.str("name"),
            description = metadata.str("description"),
            duration = duration?.toDouble(),
            uploader = metadata.obj("owner")?.str("display_name"),
            uploadDate = dateFromIso(metadata.str("createdAt")),
            chapters = parseChapters(chapterText, duration),
            formats = formats,
            subtitles = subtitles,
            webpageUrl = url,
            extractor = "loom",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "Loom"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?loom\\.com/(?:share|embed)/(?<id>[\\da-f]{32})")
    }
}

/** Upstream `LoomFolderIE`: the folder listings. */
class LoomFolderIE(
    http: ExtractorHttp,
) : InfoExtractor(ieKey = IE_KEY, http = http, validUrl = VALID_URL) {
    override suspend fun extract(url: String): InfoDict {
        val folderId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val folderData = folderJson(http, folderId)
            ?: throw ExtractionError.Malformed("The Loom folder API returned no data.")
        val entries = mutableListOf<InfoEntry>()
        collectEntries(http, folderId, folderData, entries, depth = 0)
        return InfoDict(
            id = folderId,
            title = folderData.obj("folder")?.str("name")?.trim(),
            entries = entries,
            webpageUrl = url,
            extractor = "loom:folder",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "LoomFolder"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?loom\\.com/share/folder/(?<id>[\\da-f]{32})")
    }
}

// ------------------------------------------------------------------ helpers

private const val GRAPHQL_VERSION = "45a5bd4"

private const val GET_VIDEO_SSR = """
    query GetVideoSSR(${'$'}videoId: ID!) {
      getVideo(id: ${'$'}videoId) {
        __typename
        id
        name
        description
        createdAt
        owner { display_name }
        video_properties { duration width height microphone_enabled }
      }
    }
"""

private const val GET_VIDEO_SOURCE = """
    query GetVideoSource(${'$'}videoId: ID!) {
      getVideo(id: ${'$'}videoId) {
        ... on RegularUserVideo {
          id
          nullableRawCdnUrl { url }
        }
      }
    }
"""

private const val FETCH_TRANSCRIPT = """
    query FetchVideoTranscript(${'$'}videoId: ID!) {
      fetchVideoTranscript(videoId: ${'$'}videoId) {
        ... on VideoTranscriptDetails { source_url captions_source_url }
      }
    }
"""

private const val FETCH_CHAPTERS = """
    query FetchChapters(${'$'}videoId: ID!) {
      fetchVideoChapters(videoId: ${'$'}videoId) {
        ... on VideoChapters { content }
        ... on EmptyChaptersPayload { content }
      }
    }
"""

private suspend fun graphql(
    http: ExtractorHttp,
    operation: String,
    videoId: String,
    query: String,
): JsonObject? {
    val body = """{"operationName":"$operation","variables":{"videoId":"$videoId"},"query":${jsonQuote(query)}}"""
    return try {
        http.downloadJson(
            "https://www.loom.com/graphql",
            method = "POST",
            headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "x-loom-request-source" to "loom_web_$GRAPHQL_VERSION",
                "apollographql-client-name" to "web",
                "apollographql-client-version" to GRAPHQL_VERSION,
                "graphql-operation-name" to operation,
                "Origin" to "https://www.loom.com",
            ),
            body = body.encodeToByteArray(),
        ) as? JsonObject
    } catch (error: ExtractionError) {
        null
    } catch (error: IllegalStateException) {
        null
    }
}

private suspend fun urlApi(http: ExtractorHttp, videoId: String, endpoint: String): String? {
    val body = """{"anonID":"fixture-anon","deviceID":null,"force_original":false,"password":null}"""
    return try {
        val response = http.downloadJson(
            "https://www.loom.com/api/campaigns/sessions/$videoId/$endpoint",
            method = "POST",
            headers = mapOf("Accept" to "application/json", "Content-Type" to "application/json"),
            body = body.encodeToByteArray(),
        ) as? JsonObject
        response?.str("url")
    } catch (error: ExtractionError) {
        null
    } catch (error: IllegalStateException) {
        null
    }
}

private fun addFormats(formats: MutableList<MediaFormat>, url: String?, formatId: String, quality: Int) {
    if (url == null) return
    val ext = ExtractorUtils.determineExt(url)
    when (ext) {
        "m3u8" -> formats += MediaFormat(
            formatId = "hls-$formatId",
            url = url.replace("-split.m3u8", ".m3u8"),
            ext = "mp4",
            protocol = "m3u8_native",
            preference = quality,
        )

        "mpd" -> formats += MediaFormat(
            formatId = "dash-$formatId",
            url = url,
            ext = "mp4",
            protocol = "mpd",
            preference = quality,
        )

        else -> formats += MediaFormat(
            formatId = "http-$formatId",
            url = url,
            ext = ext,
            preference = quality,
        )
    }
}

private fun parseChapters(content: String?, duration: Long?): List<Chapter> {
    val text = content ?: return emptyList()
    val starts = mutableListOf<Pair<Double, String>>()
    for (line in text.lineSequence()) {
        val match = Regex("^\\s*(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})\\s+(.+?)\\s*$").find(line) ?: continue
        val hours = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val minutes = match.groupValues[2].toDoubleOrNull() ?: continue
        val seconds = match.groupValues[3].toDoubleOrNull() ?: continue
        starts += Pair(hours * 3600 + minutes * 60 + seconds, match.groupValues[4])
    }
    return starts.mapIndexed { index, (start, title) ->
        Chapter(
            title = title,
            startTime = start,
            endTime = starts.getOrNull(index + 1)?.first ?: duration?.toDouble(),
        )
    }
}

private suspend fun folderJson(http: ExtractorHttp, folderId: String): JsonObject? = try {
    http.downloadJson("https://www.loom.com/v1/folders/$folderId?limit=10000") as? JsonObject
} catch (error: ExtractionError) {
    null
}

private suspend fun collectEntries(
    http: ExtractorHttp,
    rootFolderId: String,
    folderData: JsonObject,
    entries: MutableList<InfoEntry>,
    depth: Int,
) {
    for (element in folderData.array("videos").orEmpty()) {
        val video = element as? JsonObject ?: continue
        val videoId = video.primitiveText("id") ?: continue
        entries += InfoEntry(
            id = videoId,
            title = video.str("name"),
            url = "https://www.loom.com/share/$videoId",
        )
    }
    if (depth >= MAX_FOLDER_DEPTH) return
    for (element in folderData.array("folders").orEmpty()) {
        val folder = element as? JsonObject ?: continue
        val subfolderId = folder.primitiveText("id") ?: continue
        if (subfolderId == rootFolderId) continue
        val subData = folderJson(http, subfolderId) ?: continue
        collectEntries(http, rootFolderId, subData, entries, depth + 1)
    }
}

private const val MAX_FOLDER_DEPTH = 3

private fun jsonQuote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

private fun dateFromIso(value: String?): String? {
    val match = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(value ?: return null) ?: return null
    return match.groupValues[1] + match.groupValues[2] + match.groupValues[3]
}

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.primitiveText(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
