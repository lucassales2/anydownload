package com.anydownload.core.extract.reddit

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.CaseResult
import com.anydownload.core.extract.harness.Expect
import com.anydownload.core.extract.harness.ExtractorCase
import com.anydownload.core.extract.harness.ExtractorTestRun
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import com.anydownload.core.extract.harness.FixtureRoute
import com.anydownload.core.extract.harness.runCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Reddit subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example` (plus the `v.redd.it` path
 * forms), and no cookie or token appears.
 */
class RedditIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val postUrl = "https://www.reddit.com/r/videos/comments/6rrwyj/fixture/"
    private val apiUrl = "https://www.reddit.com/r/videos/comments/6rrwyj/.json"

    private fun apiBody(post: String) = """
        [{"data": {"children": [{"data": $post}]}}, {"data": {"children": []}}]
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val extractor = RedditIE(http(transfer()))
        assertTrue(extractor.suitable(postUrl))
        assertTrue(extractor.suitable("https://old.reddit.com/r/x/comments/abc/fixture/"))
        assertTrue(extractor.suitable("https://www.redditmedia.com/r/x/comments/abc/fixture/"))
        assertFalse(extractor.suitable("https://www.reddit.com/r/videos/"))
    }

    // -------------------------------------------------------------- hosted

    @Test
    fun hostedVideoYieldsFallbackHlsAndDash() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = apiUrl,
                contentType = "application/json",
                body = apiBody(
                    """
                    {"title": "Fixture Post", "author": "fixtureuser", "subreddit": "videos",
                     "created_utc": 1501941939.0, "over_18": false,
                     "url": "https://v.redd.it/zv89llsvexdz",
                     "preview": {"images": [{"source": {"url": "https://media.example/thumb.jpg",
                                                        "width": 640, "height": 360}}]},
                     "secure_media": {"reddit_video": {
                        "fallback_url": "https://v.redd.it/zv89llsvexdz/DASH_720.mp4",
                        "hls_url": "https://v.redd.it/zv89llsvexdz/HLSPlaylist.m3u8",
                        "dash_url": "https://v.redd.it/zv89llsvexdz/DASHPlaylist.mpd",
                        "height": 720, "width": 1280, "bitrate_kbps": 2500, "duration": 12}}}
                    """.trimIndent(),
                ),
            ),
        )
        val info = RedditIE(http(transfer)).extract(postUrl)
        assertEquals("zv89llsvexdz", info.id)
        assertEquals("Fixture Post", info.title)
        assertEquals("fixtureuser", info.uploader)
        assertEquals("videos", info.channelId)
        assertEquals("20170805", info.uploadDate)
        assertEquals(12.0, info.duration)
        assertEquals(3, info.formats.size)
        assertEquals("https://v.redd.it/zv89llsvexdz/DASH_720.mp4", info.formats.first { it.formatId == "fallback" }.url)
        assertEquals("en", info.subtitles.single().language)
        assertEquals(1, info.thumbnails.size)
    }

    @Test
    fun textPostMediaMetadataBecomesMediaItems() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = apiUrl,
                contentType = "application/json",
                body = apiBody(
                    """
                    {"title": "Fixture Text Post", "author": "fixtureuser", "subreddit": "videos",
                     "over_18": false, "url": "https://www.reddit.com/r/videos/comments/6rrwyj/fixture/",
                     "media_metadata": {"media-1": {"id": "media-1", "e": "RedditVideo",
                        "hlsUrl": "https://v.redd.it/media-1/HLSPlaylist.m3u8",
                        "dashUrl": "https://v.redd.it/media-1/DASHPlaylist.mpd"}}}
                    """.trimIndent(),
                ),
            ),
        )
        val info = RedditIE(http(transfer)).extract(postUrl)
        assertEquals("6rrwyj", info.id)
        assertEquals(1, info.media.size)
        assertEquals("media-1", info.media.single().mediaId)
        assertEquals(2, info.media.single().formats.size)
    }

    @Test
    fun externalLinkBecomesARedirect() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = apiUrl,
                contentType = "application/json",
                body = apiBody(
                    """
                    {"title": "Fixture External", "author": "fixtureuser", "subreddit": "videos",
                     "over_18": false, "url": "https://media.example/player/fixture"}
                    """.trimIndent(),
                ),
            ),
        )
        val info = RedditIE(http(transfer)).extract(postUrl)
        assertEquals("https://media.example/player/fixture", info.redirectUrl)
    }

    @Test
    fun apiRefusalFailsTypedAsLoginRequired() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = apiUrl,
                statusCode = 403,
                contentType = "application/json",
                body = """{"reason": "quarantined"}""",
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            RedditIE(http(transfer)).extract(postUrl)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun hostedVideoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = postUrl,
            infoDict = mapOf(
                "id" to Expect.Value("zv89llsvexdz"),
                "title" to Expect.Value("Fixture Post"),
                "formats" to Expect.Count(3),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = apiUrl,
                    contentType = "application/json",
                    body = apiBody(
                        """
                        {"title": "Fixture Post", "url": "https://v.redd.it/zv89llsvexdz",
                         "secure_media": {"reddit_video": {
                            "fallback_url": "https://v.redd.it/zv89llsvexdz/DASH_720.mp4",
                            "hls_url": "https://v.redd.it/zv89llsvexdz/HLSPlaylist.m3u8",
                            "dash_url": "https://v.redd.it/zv89llsvexdz/DASHPlaylist.mpd"}}}
                        """.trimIndent(),
                    ),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RedditIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
