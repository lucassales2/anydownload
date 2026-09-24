/*
 * YouTube extractor, stage 1 (JS-less `visionos` client) — AnyDownload
 *
 * Kotlin translation of a subset of `yt_dlp/extractor/youtube/_base.py`
 * (the `visionos` entry of `INNERTUBE_CLIENTS`, `_extract_context`,
 * `generate_api_headers`, `_call_api`) and `yt_dlp/extractor/youtube/_video.py`
 * (`YoutubeIE._VALID_URL` subset, `_generate_player_context`,
 * `_get_checkok_params`, `_extract_player_response`, the `streamingData` to
 * format mapping in `process_format_stream`, and the `_real_extract` metadata
 * and playability handling), read at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf) on 2026-09-24.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. Not translated: playlists, channels,
 * live streams, comments, subtitles, cookies, PO token providers, the
 * JavaScript challenge pipeline (`yt-dlp-ejs`), storyboards, and every other
 * innertube client. `_base.py` and `_video.py` are not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 */
package com.anydownlod.core.extract.youtube

import com.anydownlod.core.extract.DownloaderOptions
import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorUtils
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.Thumbnail
import com.anydownlod.core.jsc.JsChallengeOutcome
import com.anydownlod.core.jsc.JsChallengeProvider
import com.anydownlod.core.jsc.JsChallengeRequest
import com.anydownlod.core.jsc.JsChallengeType
import com.anydownlod.core.jsc.JsRuntime
import com.anydownlod.core.jsc.NoJsRuntime
import com.anydownlod.core.platform.HttpMethods
import com.anydownlod.core.platform.HttpRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Single-video YouTube extraction with the JS-less `visionos` innertube
 * client. Every format that would need a signature or `n` transform is
 * dropped and counted in [InfoDict.formatsNeedingJs]; live streams, playlists,
 * channels, and search URLs fail typed.
 */
class YoutubeIE(
    http: ExtractorHttp,
    private val jsRuntime: JsRuntime = NoJsRuntime,
) : InfoExtractor(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    private val challengeProvider = JsChallengeProvider(jsRuntime)

    override val displayName: String = "YouTube"

    override suspend fun extract(url: String): InfoDict {
        val videoId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val page = fetchWatchPage(videoId)
        val player = requestPlayer(videoId, page.visitorData)

        checkPlayability(player)
        val videoDetails = player.objectOrNull("videoDetails")
        val microformat = player.path("microformat", "playerMicroformatRenderer") as? JsonObject
        checkNotLive(videoDetails)

        val streamingData = player["streamingData"] as? JsonObject
        val stage1 = mapFormats(streamingData)
        val stage2 = if (jsRuntime.available) runCatching { resolveStage2(videoId, page, streamingData) }.getOrNull() else null
        val mapped = if (stage2 != null) {
            MappedFormats(
                formats = mergeFormats(stage2.first.formats, stage2.second.formats),
                droppedNeedingRuntime = stage2.first.droppedNeedingRuntime + stage2.second.droppedNeedingRuntime,
            )
        } else {
            stage1
        }
        if (mapped.formats.isEmpty()) {
            throw if (mapped.droppedNeedingRuntime > 0) {
                ExtractionError.NoFormats("No downloadable format is available without the JavaScript runtime.")
            } else {
                ExtractionError.NoFormats()
            }
        }

        return InfoDict(
            id = videoId,
            title = videoDetails?.stringOrNull("title"),
            formats = mapped.formats,
            thumbnails = thumbnails(videoDetails),
            duration = videoDetails?.numberOrNull("lengthSeconds"),
            uploader = videoDetails?.stringOrNull("author"),
            channel = videoDetails?.stringOrNull("author"),
            channelId = videoDetails?.stringOrNull("channelId")
                ?: microformat?.stringOrNull("externalChannelId"),
            uploadDate = ExtractorUtils.unifiedStrdate(
                microformat?.stringOrNull("publishDate")
                    ?: microformat?.stringOrNull("uploadDate")
                    ?: page.uploadDate,
            ),
            viewCount = videoDetails?.numberOrNull("viewCount")?.toLong(),
            description = videoDetails?.stringOrNull("shortDescription"),
            webpageUrl = "https://www.youtube.com/watch?v=$videoId",
            extractor = "youtube",
            extractorKey = ieKey,
            ageLimit = when {
                microformat?.booleanOrNull("isFamilySafe") == false -> 18
                page.familyFriendly == false -> 18
                page.ageRestricted -> 18
                else -> 0
            },
            isLive = false,
            formatsNeedingJs = mapped.droppedNeedingRuntime,
        )
    }

    // ------------------------------------------------------------- innertube

    /**
     * Upstream `_initial_extract` obtains the session's visitor id from the
     * watch page's ytcfg and sends it as `X-Goog-Visitor-Id`; the same page
     * provides the `uploadDate`/`isFamilyFriendly` fallbacks upstream reads
     * with `search_meta`. The values are used for this extraction only — never
     * stored, logged, or put in an [InfoDict]. A page the fixture store cannot
     * serve simply yields an empty result and the player request goes out
     * without the visitor header.
     */
    private data class WatchPageData(
        val visitorData: String? = null,
        val uploadDate: String? = null,
        val familyFriendly: Boolean? = null,
        val ageRestricted: Boolean = false,
        /** The `web` player JS URL from the page's player config, if present. */
        val playerUrl: String? = null,
        /** Upstream `STS`: the signature timestamp the page declares. */
        val signatureTimestamp: Long? = null,
        /** The page's `INNERTUBE_CLIENT_VERSION` for the `web` client. */
        val webClientVersion: String? = null,
    )

    private suspend fun fetchWatchPage(videoId: String): WatchPageData = runCatching {
        val html = http.downloadWebpage(
            url = "https://www.youtube.com/watch?v=$videoId",
            headers = mapOf("user-agent" to USER_AGENT),
            maxBytes = VISITOR_PAGE_MAX_BYTES,
        )
        WatchPageData(
            visitorData = ExtractorUtils.searchRegex("\"visitorData\"\\s*:\\s*\"([^\"]+)\"", html)
                ?: ExtractorUtils.searchRegex("\"VISITOR_DATA\"\\s*:\\s*\"([^\"]+)\"", html),
            uploadDate = ExtractorUtils.htmlSearchMeta(html, "uploadDate", "datePublished"),
            familyFriendly = ExtractorUtils.htmlSearchMeta(html, "isFamilyFriendly")
                ?.equals("true", ignoreCase = true),
            ageRestricted = ExtractorUtils.htmlSearchMeta(html, "og:restrictions:age")
                ?.contains("18") == true,
            playerUrl = ExtractorUtils.searchRegex("\"jsUrl\"\\s*:\\s*\"([^\"]+)\"", html)?.let(::absolutePlayerUrl),
            signatureTimestamp = ExtractorUtils.searchRegex("\"STS\"\\s*:\\s*(\\d+)", html)?.toLongOrNull(),
            webClientVersion = ExtractorUtils.searchRegex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"", html),
        )
    }.getOrDefault(WatchPageData())

    private suspend fun requestPlayer(
        videoId: String,
        visitorData: String?,
        client: PlayerClient = PlayerClient.VISIONOS,
        signatureTimestamp: Long? = null,
    ): JsonObject {
        val body = playerRequestBody(videoId, client, signatureTimestamp)
        val headers = buildMap {
            put("content-type", "application/json")
            put("x-youtube-client-name", client.headerName)
            put("x-youtube-client-version", client.headerVersion)
            put("origin", "https://www.youtube.com")
            put("user-agent", client.userAgent)
            // Session-only; never persisted (upstream `generate_api_headers`).
            visitorData?.takeIf { it.isNotBlank() }?.let { put("x-goog-visitor-id", it) }
        }
        val response = http.downloadJson(
            url = PLAYER_URL,
            method = HttpMethods.POST,
            headers = headers,
            body = body,
        )
        return response as? JsonObject
            ?: throw ExtractionError.Malformed("The player response was not an object.")
    }

    private enum class PlayerClient(
        val clientName: String,
        val clientVersion: String,
        val headerName: String,
        val headerVersion: String,
        val userAgent: String,
        val visionosFields: Boolean,
    ) {
        VISIONOS(
            clientName = "VISIONOS",
            clientVersion = INNERTUBE_CLIENT_VERSION,
            headerName = INNERTUBE_CLIENT_NAME,
            headerVersion = INNERTUBE_CLIENT_VERSION,
            userAgent = USER_AGENT,
            visionosFields = true,
        ),
        WEB(
            clientName = "WEB",
            clientVersion = WEB_CLIENT_VERSION,
            headerName = WEB_CLIENT_NAME,
            headerVersion = WEB_CLIENT_VERSION,
            userAgent = USER_AGENT,
            visionosFields = false,
        ),
    }

    private fun playerRequestBody(videoId: String, client: PlayerClient, sts: Long?): ByteArray = buildJsonObject {
        put(
            "context",
            buildJsonObject {
                put(
                    "client",
                    buildJsonObject {
                        put("clientName", client.clientName)
                        put("clientVersion", client.clientVersion)
                        if (client.visionosFields) {
                            put("deviceMake", "Apple")
                            put("deviceModel", "RealityDevice17,1")
                            put("userAgent", USER_AGENT)
                            put("osName", "visionOS")
                            put("osVersion", "26.5.23O471")
                        }
                        // Upstream `_extract_context` enforces language and time zone.
                        put("hl", "en")
                        put("timeZone", "UTC")
                        put("utcOffsetMinutes", 0)
                    },
                )
            },
        )
        put("videoId", videoId)
        put(
            "playbackContext",
            buildJsonObject {
                put(
                    "contentPlaybackContext",
                    buildJsonObject {
                        put("html5Preference", "HTML5_PREF_WANTS")
                        sts?.let { put("signatureTimestamp", it) }
                    },
                )
            },
        )
        put("contentCheckOk", true)
        put("racyCheckOk", true)
    }.toString().encodeToByteArray()

    // --------------------------------------------------------- stage 2 (EJS)

    /**
     * Fetches the web client's player response and resolves every ciphered or
     * `n`-challenged format from both clients with the bundled solver. Returns
     * the resolved visionos set and the resolved web set; a missing player URL
     * or a solver failure keeps stage 1.
     */
    private suspend fun resolveStage2(
        videoId: String,
        page: WatchPageData,
        visionosStreaming: JsonObject?,
    ): Pair<MappedFormats, MappedFormats>? {
        val playerUrl = page.playerUrl ?: return null
        val playerJs = fetchPlayerJs(playerUrl)
        val webPlayer = requestPlayer(
            videoId = videoId,
            visitorData = page.visitorData,
            client = PlayerClient.WEB,
            signatureTimestamp = page.signatureTimestamp,
        )
        checkPlayability(webPlayer)
        val webStreaming = webPlayer["streamingData"] as? JsonObject
        return resolveFormats(visionosStreaming, playerJs) to resolveFormats(webStreaming, playerJs)
    }

    private suspend fun fetchPlayerJs(playerUrl: String): String {
        playerJsCacheMutex.withLock { playerJsCache[playerUrl] }?.let { return it }
        val bytes = http.downloadBytes(
            request = HttpRequest(url = playerUrl, headers = mapOf("user-agent" to USER_AGENT)),
            maxBytes = PLAYER_JS_MAX_BYTES,
        )
        val text = bytes.decodeToString()
        playerJsCacheMutex.withLock { playerJsCache[playerUrl] = text }
        return text
    }

    /**
     * Resolves the streams of one player response. Plain URLs pass through;
     * `signatureCipher` and `n` values go to the solver in one batch. Streams
     * the solver cannot resolve are dropped and counted.
     */
    private suspend fun resolveFormats(streamingData: JsonObject?, playerJs: String): MappedFormats {
        if (streamingData == null) return MappedFormats(emptyList(), 0)
        data class Pending(
            val stream: JsonObject,
            val url: String,
            val signature: String?,
            val signatureParam: String?,
            val nChallenge: String?,
        )
        val pending = mutableListOf<Pending>()
        for (key in listOf("formats", "adaptiveFormats")) {
            val list = streamingData[key] as? JsonArray ?: continue
            for (element in list) {
                val stream = element as? JsonObject ?: continue
                val url = stream.stringOrNull("url")
                if (url != null) {
                    pending += Pending(stream, url, null, null, nChallengeOf(url))
                } else {
                    val cipher = stream.stringOrNull("signatureCipher") ?: stream.stringOrNull("cipher") ?: continue
                    val params = parseQuery(cipher)
                    val cipherUrl = params["url"] ?: continue
                    pending += Pending(
                        stream = stream,
                        url = cipherUrl,
                        signature = params["s"],
                        signatureParam = params["sp"] ?: "signature",
                        nChallenge = nChallengeOf(cipherUrl),
                    )
                }
            }
        }
        if (pending.isEmpty()) return MappedFormats(emptyList(), 0)

        val sigChallenges = pending.mapNotNull { it.signature?.length }
            .distinct()
            .map { length -> CharArray(length) { index -> index.toChar() }.concatToString() }
        val nChallenges = pending.mapNotNull { it.nChallenge }.distinct()
        val requests = buildList {
            if (sigChallenges.isNotEmpty()) add(JsChallengeRequest(JsChallengeType.SIG, sigChallenges))
            if (nChallenges.isNotEmpty()) add(JsChallengeRequest(JsChallengeType.N, nChallenges))
        }
        val results = when (val outcome = challengeProvider.solve(playerJs, requests)) {
            is JsChallengeOutcome.Solved -> outcome.results
            is JsChallengeOutcome.Failed -> emptyMap()
        }
        val sigResults = results[JsChallengeType.SIG].orEmpty()
        val nResults = results[JsChallengeType.N].orEmpty()

        val formats = mutableListOf<MediaFormat>()
        var dropped = 0
        for (item in pending) {
            var resolvedUrl = item.url
            val signature = item.signature
            if (signature != null) {
                val challenge = CharArray(signature.length) { index -> index.toChar() }.concatToString()
                val spec = sigResults[challenge] ?: run { dropped++; continue }
                val solved = buildString {
                    for (character in spec) {
                        val index = character.code
                        if (index in signature.indices) append(signature[index])
                    }
                }
                resolvedUrl = appendQuery(resolvedUrl, item.signatureParam ?: "signature", solved)
            }
            val nChallenge = item.nChallenge
            if (nChallenge != null) {
                val solved = nResults[nChallenge] ?: run { dropped++; continue }
                resolvedUrl = updateQueryParam(resolvedUrl, "n", solved)
            }
            formats += mapFormat(item.stream, resolvedUrl)
        }
        return MappedFormats(formats, dropped)
    }

    /** Visionos formats first, then web-only formats, deduped by format id. */
    private fun mergeFormats(first: List<MediaFormat>, second: List<MediaFormat>): List<MediaFormat> {
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<MediaFormat>()
        for (format in first + second) {
            val id = format.formatId ?: continue
            if (seen.add(id)) merged += format
        }
        return merged
    }

    private fun absolutePlayerUrl(raw: String): String {
        val unescaped = raw.replace("\\/", "/")
        return when {
            unescaped.startsWith("//") -> "https:$unescaped"
            unescaped.startsWith("/") -> "https://www.youtube.com$unescaped"
            else -> unescaped
        }
    }

    private fun nChallengeOf(url: String): String? =
        parseQuery(url.substringAfter('?', "").substringBefore('#'))["n"]?.takeIf { it.isNotEmpty() }

    private fun parseQuery(value: String): Map<String, String> =
        value.split('&').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null
            else percentDecode(part.substring(0, separator)) to percentDecode(part.substring(separator + 1))
        }.toMap()

    private fun appendQuery(url: String, name: String, value: String): String =
        url + (if (url.contains('?')) "&" else "?") + name + "=" + value

    private fun updateQueryParam(url: String, name: String, value: String): String {
        val fragment = url.substringAfter('#', "")
        val base = url.substringBefore('#')
        val queryStart = base.indexOf('?')
        val prefix = if (queryStart < 0) base else base.substring(0, queryStart)
        val parts = if (queryStart < 0) {
            mutableListOf()
        } else {
            base.substring(queryStart + 1).split('&').toMutableList()
        }
        var replaced = false
        val updated = parts.map { part ->
            if (part.substringBefore('=') == name) {
                replaced = true
                "$name=$value"
            } else {
                part
            }
        }.toMutableList()
        if (!replaced) updated += "$name=$value"
        return prefix + "?" + updated.joinToString("&") + if (fragment.isNotEmpty()) "#$fragment" else ""
    }

    // ------------------------------------------------------------ playability

    private fun checkPlayability(player: JsonObject) {
        val playability = player["playabilityStatus"] as? JsonObject
            ?: throw ExtractionError.Malformed("The player response had no playability status.")
        val status = playability.stringOrNull("status") ?: ""
        val reason = playability.stringOrNull("reason")?.lowercase() ?: ""

        val ageGated = status in AGE_STATUSES ||
            AGE_REASONS.any { reason.contains(it) }
        if (ageGated) throw ExtractionError.AgeRestricted()

        val loginRequired = status == "LOGIN_REQUIRED" ||
            reason.contains("sign in") || reason.contains("login")
        if (loginRequired) throw ExtractionError.LoginRequired()

        if (reason.contains("not available in your country")) {
            throw ExtractionError.GeoRestricted()
        }
        if (reason.contains("private video") || reason.contains("video is private")) {
            throw ExtractionError.Unavailable("This video is private.")
        }
        if (reason.contains("removed") || reason.contains("deleted") || reason.contains("no longer available")) {
            throw ExtractionError.Unavailable("This video is unavailable or was removed.")
        }
        if (status == "LIVE_STREAM_OFFLINE") {
            throw ExtractionError.Unavailable("This live stream is offline.")
        }
        if (status == "UNPLAYABLE") {
            throw ExtractionError.Unavailable("This video is unavailable.")
        }
        if (status == "ERROR") {
            throw ExtractionError.Unavailable("YouTube returned an error for this video.")
        }
        if (status != "OK") {
            throw ExtractionError.Unavailable("This video cannot be played.")
        }
    }

    private fun checkNotLive(videoDetails: JsonObject?) {
        val isLive = videoDetails?.booleanOrNull("isLiveContent") == true ||
            videoDetails?.booleanOrNull("isLive") == true
        if (isLive) throw ExtractionError.Unavailable("Live streams are not supported in this phase.")
    }

    // --------------------------------------------------------------- formats

    private data class MappedFormats(val formats: List<MediaFormat>, val droppedNeedingRuntime: Int)

    private fun mapFormats(streamingData: JsonObject?): MappedFormats {
        if (streamingData == null) return MappedFormats(emptyList(), 0)
        val formats = mutableListOf<MediaFormat>()
        var dropped = 0
        for (key in listOf("formats", "adaptiveFormats")) {
            val list = streamingData[key] as? JsonArray ?: continue
            for (element in list) {
                val stream = element as? JsonObject ?: continue
                val url = stream.stringOrNull("url")
                if (url == null) {
                    // A signatureCipher has no plain URL; without the JS
                    // runtime the format cannot be downloaded.
                    if (stream["signatureCipher"] != null || stream["cipher"] != null) dropped++
                    continue
                }
                if (hasNChallenge(url)) {
                    dropped++
                    continue
                }
                formats += mapFormat(stream, url)
            }
        }
        return MappedFormats(formats, dropped)
    }

    private fun mapFormat(stream: JsonObject, url: String): MediaFormat {
        val mimeType = stream.stringOrNull("mimeType")
        val codecs = ExtractorUtils.parseCodecs(mimeType)
        val declaresCodecs = mimeType?.contains("codecs=") == true
        val vcodec = codecs.vcodec ?: if (declaresCodecs) MediaFormat.CODEC_NONE else null
        val acodec = codecs.acodec ?: if (declaresCodecs) MediaFormat.CODEC_NONE else null
        val ext = ExtractorUtils.mimetype2ext(mimeType?.substringBefore(';'))
        val itag = stream.numberOrNull("itag")?.toLong()?.toString()
        val quality = stream.stringOrNull("quality")
        val qualityLabel = stream.stringOrNull("qualityLabel")
        val singleStream = (vcodec == MediaFormat.CODEC_NONE) != (acodec == MediaFormat.CODEC_NONE)
        val fps = stream.numberOrNull("fps")?.takeIf { it > 1.0 }
        val tbr = (stream.numberOrNull("averageBitrate") ?: stream.numberOrNull("bitrate"))?.div(1000.0)
        val isDrc = stream.booleanOrNull("isDrc") == true
        val superResolution = isSuperResolution(url)
        val note = qualityLabel ?: quality?.replace("audio_quality_", "")

        return MediaFormat(
            formatId = when {
                isDrc -> "$itag-drc"
                superResolution -> "$itag-sr"
                else -> itag
            },
            url = url,
            ext = ext,
            protocol = "https",
            vcodec = vcodec,
            acodec = acodec,
            width = stream.numberOrNull("width")?.toLong(),
            height = stream.numberOrNull("height")?.toLong(),
            fps = fps,
            tbr = tbr,
            asr = stream.numberOrNull("audioSampleRate")?.toLong(),
            filesize = stream.numberOrNull("contentLength")?.toLong(),
            audioChannels = stream.numberOrNull("audioChannels")?.toLong(),
            container = if (singleStream && ext != null) "${ext}_dash" else null,
            quality = quality,
            sourcePreference = if (itag == "22") -5 else -1,
            formatNote = buildList {
                note?.let(::add)
                if (isDrc) add("DRC")
                if (superResolution) add("AI-upscaled")
            }.joinToString(", ").ifEmpty { null },
            downloaderOptions = DownloaderOptions(httpChunkSize = HTTP_CHUNK_SIZE),
            hasDrm = stream["drmFamilies"] != null,
        )
    }

    private fun hasNChallenge(url: String): Boolean {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return false
        return query.split('&').any { part ->
            val name = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            name == "n" && value.isNotEmpty()
        }
    }

    /**
     * Upstream `is_super_resolution`: the URL's `xtags` value is itself a
     * query string, and `sr=1` marks an AI-upscaled format.
     */
    private fun isSuperResolution(url: String): Boolean {
        val query = url.substringAfter('?', "").substringBefore('#')
        val xtags = query.split('&')
            .firstOrNull { it.substringBefore('=') == "xtags" }
            ?.substringAfter('=', "")
            ?: return false
        val decoded = percentDecode(xtags)
        return decoded.split('&').any { part ->
            part.substringBefore('=') == "sr" && part.substringAfter('=', "") == "1"
        }
    }

    private fun percentDecode(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char == '%' && index + 2 < value.length -> {
                    val code = value.substring(index + 1, index + 3).toIntOrNull(16)
                    if (code == null) {
                        out.append(char)
                        index++
                    } else {
                        out.append(code.toChar())
                        index += 3
                    }
                }

                char == '+' -> {
                    out.append(' ')
                    index++
                }

                else -> {
                    out.append(char)
                    index++
                }
            }
        }
        return out.toString()
    }

    // ------------------------------------------------------------ metadata

    private fun thumbnails(videoDetails: JsonObject?): List<Thumbnail> {
        val list = videoDetails?.path("thumbnail", "thumbnails") as? JsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val thumb = element as? JsonObject ?: return@mapNotNull null
            val url = thumb.stringOrNull("url") ?: return@mapNotNull null
            Thumbnail(
                url = url,
                width = thumb.numberOrNull("width")?.toLong(),
                height = thumb.numberOrNull("height")?.toLong(),
            )
        }.sortedWith(
            compareBy(
                { (it.width ?: 0) * (it.height ?: 0) },
                { it.width ?: 0 },
            ),
        )
    }

    companion object {
        const val IE_KEY: String = "Youtube"

        /** Upstream `visionos` client values, read at the pin. */
        const val INNERTUBE_CLIENT_NAME: String = "101"
        const val INNERTUBE_CLIENT_VERSION: String = "1.02"
        const val USER_AGENT: String =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/26.0 Safari/605.1.15"

        /** Upstream `_call_api` target for the `player` endpoint. */
        const val PLAYER_URL: String = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

        /** Upstream `_base.py` `web` client context (client name 1). */
        const val WEB_CLIENT_NAME: String = "1"
        const val WEB_CLIENT_VERSION: String = "2.20260708.00.00"

        /**
         * The player JS is a few MB; upstream reads it whole and caches it per
         * player id. 12 MiB leaves headroom over the observed ~2-3 MB.
         */
        const val PLAYER_JS_MAX_BYTES: Int = 12 * 1024 * 1024

        private val playerJsCache: MutableMap<String, String> = mutableMapOf()
        private val playerJsCacheMutex = Mutex()

        /** Upstream `_extract_formats_and_subtitles` CHUNK_SIZE (10 << 20). */
        const val HTTP_CHUNK_SIZE: Long = 10L * 1024 * 1024

        /** The watch page needs more than the extractor default to hold ytcfg. */
        const val VISITOR_PAGE_MAX_BYTES: Int = 2 * 1024 * 1024

        private val AGE_STATUSES = setOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED")
        private val AGE_REASONS = listOf(
            "confirm your age", "age-restricted", "age restricted", "inappropriate",
        )

        /**
         * The URL forms the task lists; everything else (playlists, channels,
         * `@handle`, search) does not match and stays unsupported in D4.
         */
        val VALID_URL: Regex = Regex(
            "^(" +
                "https?://(?:www\\.|m\\.|music\\.)?youtube(?:-nocookie)?\\.com/" +
                "(?:(?:watch(?:\\?|\\#!?)(?:.*?[&;])??v=)|(?:v|embed|e|shorts|live)/(?!videoseries|live_stream))" +
                "|https?://youtu\\.be/" +
                ")(?<id>[0-9A-Za-z_-]{11})(?:[?&#].*)?$",
        )
    }
}

// ------------------------------------------------------------- JSON helpers

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.numberOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.booleanOrNull(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonElement.path(vararg keys: String): JsonElement? {
    var current: JsonElement? = this
    for (key in keys) {
        current = (current as? JsonObject)?.get(key) ?: return null
    }
    return current
}
