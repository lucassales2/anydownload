package com.anydownlod.core.extract.youtube

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Innertube search parsing over synthesized public result shapes. No live
 * call, no thumbnail, no visitor data, and no signed URL appears here.
 */
class YoutubeSearchTest {

    // ------------------------------------------------------------- fixtures

    private val ytmSongs = """
        {"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[
        {"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"lYBUbBu4W08"},"overlay":{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{"playNavigationEndpoint":{"watchEndpoint":{"videoId":"lYBUbBu4W08","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}}}}}}},"flexColumns":[
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Never Gonna Give You Up"}]}}},
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
        {"text":"Rick Astley","navigationEndpoint":{"browseEndpoint":{"browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},
        {"text":" • "},
        {"text":"Whenever You Need Somebody","navigationEndpoint":{"browseEndpoint":{"browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}},
        {"text":" • "},
        {"text":"3:34"}]}}}]}}
        ]}}]}}}}]}}}
    """.trimIndent()

    private val ytmVideos = """
        {"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicShelfRenderer":{"contents":[
        {"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"dQw4w9WgXcQ"},"overlay":{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{"playNavigationEndpoint":{"watchEndpoint":{"videoId":"dQw4w9WgXcQ","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_OMV"}}}}}}}},"flexColumns":[
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Rick Astley - Never Gonna Give You Up (Official Video)"}]}}},
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
        {"text":"Rick Astley","navigationEndpoint":{"browseEndpoint":{"browseId":"UCuAXFkgsw1L7xaCfnd5JJOw","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_USER_CHANNEL"}}}}},
        {"text":" • "},
        {"text":"1.8B views"},
        {"text":" • "},
        {"text":"3:34"}]}}}]}}
        ]}}]}}}}]}}}
    """.trimIndent()

    private val youtubeVideos = """
        {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[
        {"videoRenderer":{"videoId":"dQw4w9WgXcQ","title":{"runs":[{"text":"Rick Astley - Never Gonna Give You Up (Official Video)"}]},"ownerText":{"runs":[{"text":"Rick Astley"}]},"lengthText":{"simpleText":"3:34"},"viewCountText":{"simpleText":"1,819,912,851 views"}}}
        ]}}]}}}}}
    """.trimIndent()

    private fun search(body: String): Pair<YoutubeSearch, FixtureHttpTransfer> {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://*.youtube.com/youtubei/v1/search*",
                    method = "POST",
                    contentType = "application/json",
                    body = body,
                ),
            ),
        )
        return YoutubeSearch(ExtractorHttp(transfer)) to transfer
    }

    // ---------------------------------------------------------- music results

    @Test
    fun youtubeMusicSongsCarryArtistsAlbumDurationAndVerified() = runTest {
        val (search, _) = search(ytmSongs)
        val result = search.searchMusic("Rick Astley Never Gonna Give You Up", MusicSearchFilter.SONGS).single()
        assertEquals("lYBUbBu4W08", result.videoId)
        assertEquals("Never Gonna Give You Up", result.title)
        assertEquals(listOf("Rick Astley"), result.artists)
        assertEquals("Whenever You Need Somebody", result.album)
        assertEquals(214.0, result.durationSeconds)
        assertTrue(result.verified)
        assertEquals("https://music.youtube.com/watch?v=lYBUbBu4W08", result.url)
    }

    @Test
    fun youtubeMusicVideosCarryChannelViewsAndAreNotVerified() = runTest {
        val (search, _) = search(ytmVideos)
        val result = search.searchMusic("Rick Astley", MusicSearchFilter.VIDEOS).single()
        assertEquals("dQw4w9WgXcQ", result.videoId)
        assertEquals("Rick Astley", result.channel)
        assertEquals(1_800_000_000L, result.viewCount)
        assertEquals(214.0, result.durationSeconds)
        assertEquals(false, result.verified)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", result.url)
    }

    @Test
    fun regularYoutubeResultsCarryChannelDurationAndViews() = runTest {
        val (search, _) = search(youtubeVideos)
        val result = search.searchYoutube("Rick Astley").single()
        assertEquals("dQw4w9WgXcQ", result.videoId)
        assertEquals("Rick Astley - Never Gonna Give You Up (Official Video)", result.title)
        assertEquals("Rick Astley", result.channel)
        assertEquals(214.0, result.durationSeconds)
        assertEquals(1_819_912_851L, result.viewCount)
        assertEquals(false, result.verified)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", result.url)
    }

    // ------------------------------------------------------------- requests

    @Test
    fun theRequestBodyCarriesTheQueryAndClientHeaders() = runTest {
        val (search, transfer) = search(ytmSongs)
        search.searchMusic("fixture query", MusicSearchFilter.SONGS)
        val request = transfer.requests.single()
        assertTrue(request.url.startsWith(YoutubeSearch.MUSIC_SEARCH_URL))
        assertEquals("67", request.headers["x-youtube-client-name"])
        val body = request.body?.decodeToString().orEmpty()
        assertTrue(body.contains("\"query\":\"fixture query\""), body)
        assertTrue(body.contains("\"clientName\":\"WEB_REMIX\""), body)
        assertTrue(body.contains("EgWKAQIIAWoKEAoQAxAEEAkQBQ=="))
    }

    @Test
    fun theYoutubeRequestBodyUsesTheWebContext() = runTest {
        val (search, transfer) = search(youtubeVideos)
        search.searchYoutube("fixture query")
        val request = transfer.requests.single()
        assertEquals("1", request.headers["x-youtube-client-name"])
        val body = request.body?.decodeToString().orEmpty()
        assertTrue(body.contains("\"clientName\":\"WEB\""), body)
        assertTrue(body.contains("EgIQAfABAQ=="))
    }

    @Test
    fun aBlankQuerySendsNoRequest() = runTest {
        val (search, transfer) = search(ytmSongs)
        assertTrue(search.searchMusic("   ").isEmpty())
        assertTrue(search.searchYoutube("").isEmpty())
        assertTrue(transfer.requests.isEmpty())
    }

    @Test
    fun anEmptyDocumentReturnsNoResults() = runTest {
        val (search, _) = search("""{"contents":{}}""")
        assertTrue(search.searchMusic("nothing").isEmpty())
        assertTrue(search.searchYoutube("nothing").isEmpty())
    }

    @Test
    fun aNonObjectResponseFailsTyped() = runTest {
        val (search, _) = search("""[1,2,3]""")
        assertFailsWith<ExtractionError.Malformed> {
            search.searchMusic("fixture")
        }
    }

    // ------------------------------------------------------------- parsing

    @Test
    fun durationParsingHandlesMinutesAndHours() {
        assertEquals(214.0, parseDurationSeconds("3:34"))
        assertEquals(3723.0, parseDurationSeconds("1:02:03"))
        assertNull(parseDurationSeconds("no duration"))
        assertNull(parseDurationSeconds("3:aa"))
        assertNull(parseDurationSeconds(null))
    }

    @Test
    fun viewParsingHandlesSuffixesAndSeparators() {
        assertEquals(1_819_912_851L, parseViewCount("1,819,912,851 views"))
        assertEquals(1_800_000_000L, parseViewCount("1.8B views"))
        assertEquals(77_000L, parseViewCount("77K views"))
        assertEquals(2_000_000_000L, parseViewCount("2B plays"))
        assertNull(parseViewCount("no views"))
        assertNull(parseViewCount(null))
    }
}
