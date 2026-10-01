package com.anydownload.core.extract.yahoo

import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoExtractor
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Yahoo subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no user credential
 * appears (the public app id is the one the public API always receives).
 */
class YahooIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "f885cf7f-43d4-3450-9fac-46ac30ece521"

    private fun articleJson() = """
        {"items": [{"data": {"partnerData": {"type": "video", "uuid": "$videoId",
          "title": "Fixture Story"}}}]}
    """.trimIndent()

    private fun metaJson() = """
        {"query": {"results": {"mediaObj": [{"meta": {
          "title": "Fixture Yahoo Video", "description": "Fixture description",
          "thumbnail": "http://media.example/thumb.jpg", "publish_time": "2013-11-29T10:00:00Z",
          "duration": 128, "view_count": 42}}]}}}
    """.trimIndent()

    private fun streamsJson() = """
        {"query": {"results": {"mediaObj": [{"status": {"msg": "ok"},
          "streams": [{"host": "https://media.example", "path": "/video/720.mp4",
                       "format": "mp4", "bitrate": 2000, "width": 1280, "height": 720,
                       "framerate": 30}],
          "closedcaptions": [{"url": "https://media.example/cc/en.vtt", "lang": "en-US",
                              "content_type": "text/vtt"}]}]}}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            YahooIE(http(transfer())) to "https://news.yahoo.com/video/china-moses-crazy-blues-104538833.html",
            YahooIE(http(transfer())) to "https://www.yahoo.com/movies/v/true-story-trailer-173000497.html",
            YahooIE(http(transfer())) to "https://tw.news.yahoo.com/-100120367.html",
            YahooJapanNewsIE(http(transfer())) to "https://news.yahoo.co.jp/articles/fixturearticle",
            YahooJapanNewsIE(http(transfer())) to "https://news.yahoo.co.jp/feature/1356",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(YahooJapanNewsIE(http(transfer())).suitable("https://www.yahoo.com/video/x-104538833.html"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun videoArticleYieldsFormatsAndCaptions() = runTest {
        val url = "https://news.yahoo.com/video/china-moses-crazy-blues-104538833.html"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://us.yahoo.com/caas/content/article?url=*",
                contentType = "application/json",
                body = articleJson(),
            ),
            FixtureRoute(
                urlPattern = "https://video-api.yql.yahoo.com/v1/video/sapi/streams/$videoId",
                contentType = "application/json",
                body = metaJson(),
            ),
            FixtureRoute(
                urlPattern = "https://video-api.yql.yahoo.com/v1/video/sapi/streams/$videoId?format=webm&region=US",
                contentType = "application/json",
                body = streamsJson(),
            ),
            FixtureRoute(
                urlPattern = "https://video-api.yql.yahoo.com/v1/video/sapi/streams/$videoId?format=mp4&region=US",
                contentType = "application/json",
                body = streamsJson(),
            ),
        )
        val info = YahooIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Yahoo Video", info.title)
        assertEquals(128.0, info.duration)
        assertEquals(42L, info.viewCount)
        assertEquals("20131129", info.uploadDate)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("https://media.example/video/720.mp4", info.formats[0].url)
        assertEquals("en-US", info.subtitles.single().language)
        assertEquals("https://media.example/cc/en.vtt", info.subtitles.single().formats.single().url)
    }

    // --------------------------------------------------------------- playlist

    @Test
    fun storyPageYieldsEntries() = runTest {
        val url = "https://www.yahoo.com/entertainment/gwen-stefani-reveals-033045672.html"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://us.yahoo.com/caas/content/article?url=*",
                contentType = "application/json",
                body = """
                    {"items": [{"data": {"partnerData": {"type": "story", "uuid": "story-1",
                      "title": "Fixture Story", "summary": "Fixture summary",
                      "cover": {"type": "yvideo", "url": "https://www.yahoo.com/video/cover-033459311.html",
                                "uuid": "cover-uuid"},
                      "body": [{"type": "videoIframe", "url": "https://www.youtube.com/embed/fixture"}]}}}]}
                """.trimIndent(),
            ),
        )
        val info = YahooIE(http(transfer)).extract(url)
        assertEquals("story-1", info.id)
        assertEquals("Fixture Story", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.yahoo.com/video/cover-033459311.html", info.entries[0].url)
    }

    // ------------------------------------------------------------- japan news

    @Test
    fun japanNewsYieldsFormats() = runTest {
        val url = "https://news.yahoo.co.jp/articles/fixturearticle"
        val page = """
            <html><head><meta property="og:title" content="Fixture Japan Title">
            <meta property="og:image" content="https://media.example/jp.jpg"></head>
            <body><script>window.__PRELOADED_STATE__ = {"articleDetail": {"headline": "Fixture Japan Title",
              "paragraphs": [{"objectItems": [{"video": {"vid": 12345}}]}]},
              "pageData": {"spaceId": "fixture-space", "description": "Fixture Japan description"}};</script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://feapi-yvpub.yahooapis.jp/v1/content/12345?appid=*",
                contentType = "application/json",
                body = """
                    {"ResultSet": {"Result": [{"VideoUrlSet": {"VideoUrl": [
                      {"delivery": "hls", "Url": "https://media.example/hls/master.m3u8"},
                      {"delivery": "http", "Url": "https://media.example/video/720.mp4",
                       "bitrate": 2000, "width": 1280, "height": 720}]}}]}}
                """.trimIndent(),
            ),
        )
        val info = YahooJapanNewsIE(http(transfer)).extract(url)
        assertEquals("fixturearticle", info.id)
        assertEquals("Fixture Japan Title", info.title)
        assertEquals("Fixture Japan description", info.description)
        assertEquals(2, info.formats.size)
        assertEquals("https://media.example/hls/master.m3u8", info.formats[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoArticleIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://news.yahoo.com/video/china-moses-crazy-blues-104538833.html"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Yahoo Video"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://us.yahoo.com/caas/content/article?url=*",
                    contentType = "application/json",
                    body = articleJson(),
                ),
                FixtureRoute(
                    urlPattern = "https://video-api.yql.yahoo.com/v1/video/sapi/streams/$videoId",
                    contentType = "application/json",
                    body = metaJson(),
                ),
                FixtureRoute(
                    urlPattern = "https://video-api.yql.yahoo.com/v1/video/sapi/streams/$videoId?format=*",
                    contentType = "application/json",
                    body = streamsJson(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> YahooIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
