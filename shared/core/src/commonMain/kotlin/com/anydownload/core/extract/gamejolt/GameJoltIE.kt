/*
 * Game Jolt extractors — AnyDownload
 *
 * Kotlin translation of the public API subset of `gamejolt.py` from
 * `yt_dlp/extractor/gamejolt.py` at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-30.
 *
 * yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * This translation keeps that license. `gamejolt.py` is not vendored; see
 * shared/core/NOTICE.md and port/manifest.json.
 *
 * Scope: the site-api posts view, the post content/video/GIF mapping, the
 * user/game/community/search post lists with their scroll pagination, and the
 * soundtrack overview. HLS/DASH masters are recorded for the download-time
 * parse. The comments side extractor, `display_id`, `uploader_url`,
 * `categories`, `tags`, like/comment counts, `release_timestamp`, and the
 * `embeds` re-dispatch metadata are not modeled on the port's InfoDict; a
 * soundtrack entry is a direct media URL whose download depends on the host's
 * direct-file path. No cookie, token, or signed media URL is stored here.
 */
package com.anydownload.core.extract.gamejolt

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Upstream `GameJoltBaseIE`: the site-api and post parser. */
abstract class GameJoltBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex? = null,
) : InfoExtractor(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_call_api`. */
    protected suspend fun callApi(endpoint: String, itemId: String, data: String? = null): JsonObject {
        val json = http.downloadJson(
            "https://gamejolt.com/site-api/$endpoint",
            method = if (data != null) {
                com.anydownload.core.platform.HttpMethods.POST
            } else {
                com.anydownload.core.platform.HttpMethods.GET
            },
            headers = mapOf("accept" to "image/webp,*/*", "content-type" to "application/json"),
            body = data?.encodeToByteArray(),
        ) as? JsonObject ?: throw ExtractionError.Malformed("The Game Jolt API returned no object.")
        return json.obj("payload")
            ?: throw ExtractionError.Malformed("The Game Jolt API returned no payload.")
    }

    /** Upstream `_parse_content_as_text`. */
    protected fun parseContentAsText(content: JsonObject?): String {
        val outerContents = content?.array("content").orEmpty()
        val joined = mutableListOf<String>()
        for (element in outerContents) {
            val outer = element as? JsonObject ?: continue
            if (outer.str("type") != "paragraph") {
                joined += parseContentAsText(outer)
                continue
            }
            var text = ""
            for (innerElement in outer.array("content").orEmpty()) {
                val inner = innerElement as? JsonObject ?: continue
                when {
                    inner.str("text") != null -> text += inner.str("text")
                    inner.str("type") == "hardBreak" -> text += "\n"
                }
            }
            joined += text
        }
        return joined.joinToString("\n")
    }

    /** Upstream `_parse_post`; comments are not modeled. */
    protected suspend fun parsePost(postData: JsonObject): InfoDict {
        val postId = postData.str("hash") ?: throw ExtractionError.Malformed("The Game Jolt post had no hash.")
        val leadContent = ExtractorUtils.parseJson(postData.str("lead_content") ?: "{}") as? JsonObject
        var description = postData.str("leadStr")
            ?: parseContentAsText(ExtractorUtils.parseJson(postData.str("lead_content")) as? JsonObject)
        var fullDescription: String? = null
        if (postData.bool("has_article") == true) {
            val articleContent = postData.str("article_content")
                ?: (try {
                    callApi(
                        "web/posts/article/${postData.number("id")?.toLong() ?: postId}",
                        postId,
                    ).obj("article")?.toString()
                } catch (error: ExtractionError) {
                    null
                })
            if (articleContent != null) {
                fullDescription = parseContentAsText(ExtractorUtils.parseJson(articleContent) as? JsonObject)
            }
        }
        val userData = postData.obj("user") ?: JsonObject(emptyMap())

        val videoData = postData.obj("videos") ?: JsonObject(emptyMap())
        val formats = mutableListOf<MediaFormat>()
        val thumbnails = mutableListOf<Thumbnail>()
        for (element in videoData.array("media").orEmpty()) {
            val media = element as? JsonObject ?: continue
            val mediaUrl = ExtractorUtils.urlOrNone(media.str("img_url")) ?: continue
            val mimetype = media.str("filetype") ?: ""
            val ext = ExtractorUtils.determineExt(mediaUrl, defaultExt = "")
            val mediaId = media.str("type")
            when {
                mimetype == "application/vnd.apple.mpegurl" || ext == "m3u8" -> formats += MediaFormat(
                    formatId = mediaId ?: "hls",
                    url = mediaUrl,
                    ext = "mp4",
                    protocol = "m3u8_native",
                )

                mimetype == "application/dash+xml" || ext == "mpd" -> formats += MediaFormat(
                    formatId = mediaId ?: "dash",
                    url = mediaUrl,
                    ext = "mp4",
                    protocol = "http_dash_segments",
                )

                "image" in mimetype -> thumbnails += Thumbnail(
                    id = mediaId,
                    url = mediaUrl,
                    width = media.number("width")?.toLong(),
                    height = media.number("height")?.toLong(),
                )

                else -> formats += MediaFormat(
                    formatId = mediaId,
                    url = mediaUrl,
                    width = media.number("width")?.toLong(),
                    height = media.number("height")?.toLong(),
                    filesize = media.number("filesize")?.toLong(),
                    acodec = if ("video-card" in mediaUrl) MediaFormat.CODEC_NONE else null,
                )
            }
        }

        val base = InfoDict(
            id = postId,
            title = description,
            description = fullDescription ?: description,
            uploader = userData.str("display_name") ?: userData.str("name"),
            channelId = userData.str("username"),
            viewCount = videoData.number("view_count")?.toLong(),
            uploadDate = postData.number("added_on")?.div(1000)?.toLong()
                ?.let(ExtractorUtils::epochSecondsToDate),
            webpageUrl = ExtractorUtils.urlOrNone(postData.str("url")) ?: "https://gamejolt.com/p/$postId",
            extractor = "gamejolt",
            extractorKey = ieKey,
        )
        if (formats.isNotEmpty()) {
            return base.copy(formats = formats, thumbnails = thumbnails)
        }

        val gifEntries = mutableListOf<InfoEntry>()
        for (element in postData.array("media").orEmpty()) {
            val media = element as? JsonObject ?: continue
            val mediaUrl = media.str("img_url") ?: continue
            if (ExtractorUtils.determineExt(mediaUrl, defaultExt = "") != "gif" ||
                "gif" !in (media.str("filetype") ?: "")
            ) {
                continue
            }
            gifEntries += InfoEntry(
                id = media.str("hash"),
                title = media.str("filename")?.substringBefore('.'),
                url = mediaUrl,
            )
        }
        if (gifEntries.isNotEmpty()) {
            return base.copy(entries = gifEntries)
        }
        val embedUrl = ExtractorUtils.urlOrNone(
            postData.array("embeds").orEmpty()
                .mapNotNull { (it as? JsonObject)?.str("url") }
                .firstOrNull(),
        )
        if (embedUrl != null) {
            return base.copy(redirectUrl = embedUrl)
        }
        return base
    }
}

/** Upstream `GameJoltIE`: the `/p/<slug>-<id>` post pages. */
class GameJoltIE(
    http: ExtractorHttp,
) : GameJoltBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val postId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val post = callApi("web/posts/view/$postId", postId).obj("post")
            ?: throw ExtractionError.Malformed("The Game Jolt post was not found.")
        return parsePost(post)
    }

    companion object {
        const val IE_KEY: String = "GameJolt"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?gamejolt\\.com/p/(?:[\\w-]*-)?(?<id>\\w{8})",
        )
    }
}

/** Upstream `GameJoltPostListBaseIE`: the scroll-paged post lists. */
abstract class GameJoltPostListBaseIE(
    ieKey: String,
    http: ExtractorHttp,
    validUrl: Regex,
) : GameJoltBaseIE(
    ieKey = ieKey,
    http = http,
    validUrl = validUrl,
) {
    /** Upstream `_entries`; the scroll cursor bounds the walk. */
    protected suspend fun listPosts(endpoint: String, listId: String): List<InfoEntry> {
        val entries = mutableListOf<InfoEntry>()
        var items = try {
            callApi(endpoint, listId).array("items").orEmpty()
        } catch (error: ExtractionError) {
            JsonArray(emptyList())
        }
        var page = 1
        while (items.isNotEmpty() && page <= MAX_PAGES) {
            for (element in items) {
                val item = element as? JsonObject ?: continue
                val post = item.obj("action_resource_model") ?: continue
                val info = parsePost(post)
                entries += InfoEntry(id = info.id, title = info.title, url = info.webpageUrl ?: info.redirectUrl)
            }
            val scrollId = (items.lastOrNull() as? JsonObject)?.str("scroll_id") ?: break
            page++
            items = try {
                callApi(
                    endpoint,
                    listId,
                    data = buildJsonObject {
                        put("scrollDirection", "from")
                        put("scrollId", scrollId)
                    }.toString(),
                ).array("items").orEmpty()
            } catch (error: ExtractionError) {
                break
            }
        }
        return entries
    }

    companion object {
        private const val MAX_PAGES = 100
    }
}

/** Upstream `GameJoltUserIE`: the `@user` profile post lists. */
class GameJoltUserIE(
    http: ExtractorHttp,
) : GameJoltPostListBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val userId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val userData = callApi("web/profile/@$userId", userId).obj("user")
            ?: throw ExtractionError.Malformed("The Game Jolt user was not found.")
        return InfoDict(
            id = userData.number("id")?.toLong()?.toString(),
            title = userData.str("display_name") ?: userData.str("name"),
            description = parseContentAsText(
                ExtractorUtils.parseJson(userData.str("bio_content") ?: "{}") as? JsonObject,
            ),
            entries = listPosts("web/posts/fetch/user/@$userId?tab=active", userId),
            webpageUrl = url,
            extractor = "gamejolt",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "GameJoltUser"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?gamejolt\\.com/@(?<id>[\\w-]+)")
    }
}

/** Upstream `GameJoltGameIE`: the game post lists. */
class GameJoltGameIE(
    http: ExtractorHttp,
) : GameJoltPostListBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val gameId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val gameData = callApi("web/discover/games/$gameId", gameId).obj("game")
            ?: throw ExtractionError.Malformed("The Game Jolt game was not found.")
        return InfoDict(
            id = gameId,
            title = gameData.str("title"),
            description = parseContentAsText(
                ExtractorUtils.parseJson(gameData.str("description_content") ?: "{}") as? JsonObject,
            ),
            entries = listPosts("web/posts/fetch/game/$gameId", gameId),
            webpageUrl = url,
            extractor = "gamejolt",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "GameJoltGame"

        val VALID_URL: Regex = Regex("https?://(?:www\\.)?gamejolt\\.com/games/[\\w-]+/(?<id>\\d+)")
    }
}

/** Upstream `GameJoltGameSoundtrackIE`: the soundtrack listings. */
class GameJoltGameSoundtrackIE(
    http: ExtractorHttp,
) : GameJoltBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val gameId = matchId(url) ?: throw ExtractionError.UnsupportedUrl()
        val overview = callApi("web/discover/games/overview/$gameId", gameId)
        val entries = overview.array("songs").orEmpty().mapNotNull { element ->
            val song = element as? JsonObject ?: return@mapNotNull null
            val songUrl = ExtractorUtils.urlOrNone(song.str("url")) ?: return@mapNotNull null
            InfoEntry(id = song.number("id")?.toLong()?.toString(), title = song.str("title"), url = songUrl)
        }
        val title = overview.obj("microdata")?.str("name")
            ?: overview.obj("twitter")?.str("title")
            ?: overview.obj("fb")?.str("title")
        return InfoDict(
            id = gameId,
            title = title,
            entries = entries,
            webpageUrl = url,
            extractor = "gamejolt",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "GameJoltGameSoundtrack"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?gamejolt\\.com/get/soundtrack(?:\\?|\\#!?)(?:.*?[&;])??game=(?<id>(?:\\d+)+)",
        )
    }
}

/** Upstream `GameJoltCommunityIE`: the community/channel post lists. */
class GameJoltCommunityIE(
    http: ExtractorHttp,
) : GameJoltPostListBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val displayId = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val communityId = match.groups["community"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val channelId = match.groups["channel"]?.value ?: "featured"
        val sortBy = match.groups["sort"]?.value ?: "new"
        val communityData = callApi("web/communities/view/$communityId", displayId).obj("community")
            ?: throw ExtractionError.Malformed("The Game Jolt community was not found.")
        val channelData = try {
            callApi("web/communities/view-channel/$communityId/$channelId", displayId).obj("channel")
        } catch (error: ExtractionError) {
            null
        }
        val title = (communityData.str("name") ?: communityId) + " - " +
            (channelData?.str("display_title") ?: channelId)
        return InfoDict(
            id = "$communityId/$channelId",
            title = title,
            description = parseContentAsText(
                ExtractorUtils.parseJson(communityData.str("description_content") ?: "{}") as? JsonObject,
            ),
            entries = listPosts(
                "web/posts/fetch/community/$communityId?channels[]=$sortBy&channels[]=$channelId",
                displayId,
            ),
            webpageUrl = url,
            extractor = "gamejolt",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "GameJoltCommunity"

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?gamejolt\\.com/c/(?<id>(?<community>[\\w-]+)" +
                "(?:/(?<channel>[\\w-]+))?)(?:(?:\\?|\\#!?)(?:.*?[&;])??sort=(?<sort>\\w+))?",
        )
    }
}

/** Upstream `GameJoltSearchIE`: the search result listings. */
class GameJoltSearchIE(
    http: ExtractorHttp,
) : GameJoltPostListBaseIE(
    ieKey = IE_KEY,
    http = http,
    validUrl = VALID_URL,
) {
    override suspend fun extract(url: String): InfoDict {
        val match = VALID_URL.find(url) ?: throw ExtractionError.UnsupportedUrl()
        val filter = match.groups["filter"]?.value
        val query = match.groups["id"]?.value ?: throw ExtractionError.UnsupportedUrl()
        val displayQuery = percentDecode(query)
        if (filter != null) {
            val entries = mutableListOf<InfoEntry>()
            val initial = callApi("web/search/$filter?q=$query", displayQuery)
            val count = initial.number("count")?.toInt()
                ?: initial.number("${filter}Count")?.toInt()
                ?: 0
            val perPage = initial.number("perPage")?.toInt() ?: 0
            if (count > 0 && perPage > 0) {
                for (page in 1..(count + perPage - 1) / perPage) {
                    val results = callApi("web/search/$filter?q=$query&page=$page", displayQuery)
                    for (element in results.array(filter).orEmpty()) {
                        val result = element as? JsonObject ?: continue
                        val resultUrl = when (filter) {
                            "users" -> result.str("username")?.let { "https://gamejolt.com/@$it" }
                            "communities" -> result.str("path")?.let { "https://gamejolt.com/c/$it" }
                            "games" -> result.str("slug")?.let { slug ->
                                result.number("id")?.toLong()?.let { "https://gamejolt.com/games/$slug/$it" }
                            }

                            else -> null
                        }
                        resultUrl?.let { entries += InfoEntry(url = it) }
                    }
                }
            }
            return InfoDict(
                id = displayQuery,
                title = displayQuery,
                entries = entries,
                webpageUrl = url,
                extractor = "gamejolt",
                extractorKey = IE_KEY,
            )
        }
        val initialPosts = callApi("web/search?q=$query", displayQuery).array("posts").orEmpty()
        val entries = mutableListOf<InfoEntry>()
        var items = initialPosts
        var page = 1
        while (items.isNotEmpty() && page <= MAX_PAGES) {
            for (element in items) {
                val item = element as? JsonObject ?: continue
                val info = parsePost(item)
                entries += InfoEntry(id = info.id, title = info.title, url = info.webpageUrl ?: info.redirectUrl)
            }
            val scrollId = (items.lastOrNull() as? JsonObject)?.str("scroll_id") ?: break
            page++
            items = try {
                callApi(
                    "web/posts/fetch/search/$query",
                    displayQuery,
                    data = buildJsonObject {
                        put("scrollDirection", "from")
                        put("scrollId", scrollId)
                    }.toString(),
                ).array("items").orEmpty()
            } catch (error: ExtractionError) {
                break
            }
        }
        return InfoDict(
            id = displayQuery,
            title = displayQuery,
            entries = entries,
            webpageUrl = url,
            extractor = "gamejolt",
            extractorKey = IE_KEY,
        )
    }

    companion object {
        const val IE_KEY: String = "GameJoltSearch"
        private const val MAX_PAGES = 100

        val VALID_URL: Regex = Regex(
            "https?://(?:www\\.)?gamejolt\\.com/search(?:/(?<filter>communities|users|games))?" +
                "(?:\\?|\\#!?)(?:.*?[&;])??q=(?<id>(?:[^&#]+)+)",
        )
    }
}

// ------------------------------------------------------------------ helpers

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

private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.str(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.number(name: String): Double? =
    (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.bool(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
