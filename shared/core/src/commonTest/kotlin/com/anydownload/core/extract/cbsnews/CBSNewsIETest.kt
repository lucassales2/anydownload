package com.anydownload.core.extract.cbsnews

import com.anydownload.core.extract.ExtractionError
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the CBS News subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class CBSNewsIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "SNJBOYzXiWBOvaLsdzwH8fmtP1SCd91Y"

    private fun payloadPage(item: String) = """
        <html><head><meta property="og:title" content="Fixture CBS"></head><body>
        <script>CBSNEWS.defaultPayload = {"items": [$item]};</script>
        </body></html>
    """.trimIndent()

    private val mp4Item = """
        {"mpxRefId": "$videoId", "title": "Fixture Video", "fulltitle": "Fixture Video Full",
         "dek": "Fixture description", "duration": 205, "timestamp": 1396650660000,
         "format": "video/mp4", "video": "https://media.example/video/clip.mp4",
         "images": {"hd": "https://media.example/thumb.jpg"}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            CBSNewsIE(http(transfer())) to "https://www.cbsnews.com/video/fixture-video/",
            CBSNewsIE(http(transfer())) to "https://www.cbsnews.com/news/fixture-article/",
            CBSNewsEmbedIE(http(transfer())) to "https://www.cbsnews.com/embed/video/?v=1#fixture-payload",
            CBSLocalIE(http(transfer())) to "https://www.cbsnews.com/newyork/video/fixture-video/",
            CBSLocalArticleIE(http(transfer())) to "https://www.cbsnews.com/newyork/news/fixture-article/",
            CBSLocalLiveIE(http(transfer())) to "https://www.cbsnews.com/losangeles/live/",
            CBSNewsLiveIE(http(transfer())) to "https://www.cbsnews.com/live/",
            CBSNewsLiveVideoIE(http(transfer())) to "https://www.cbsnews.com/live/video/fixture-story/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(CBSNewsLiveIE(http(transfer())).suitable("https://www.cbsnews.com/video/x/"))
    }

    // ---------------------------------------------------------------- video

    @Test
    fun newsPageItemYieldsTheDirectMp4() = runTest {
        val url = "https://www.cbsnews.com/video/fixture-video/"
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = payloadPage(mp4Item)))
        val info = CBSNewsIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Video Full", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(205.0, info.duration)
        assertEquals("20140404", info.uploadDate)
        assertEquals("https://media.example/video/clip.mp4", info.formats.single().url)
    }

    @Test
    fun manifestWithAnvatoIdBecomesARedirect() = runTest {
        val url = "https://www.cbsnews.com/video/fixture-video/"
        val item = """
            {"mpxRefId": "$videoId", "title": "Fixture Video",
             "format": "application/x-mpegURL", "video": "https://media.example/manifest.m3u8"}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = payloadPage(item)),
            FixtureRoute(
                urlPattern = "https://media.example/manifest.m3u8",
                contentType = "application/vnd.apple.mpegurl",
                body = "#EXTM3U\n# anvato-12345\n",
            ),
        )
        val info = CBSNewsIE(http(transfer)).extract(url)
        assertEquals("anvato:5VD6Eyd6djewbCmNwBFnsJj17YAvGRwl:12345", info.redirectUrl)
    }

    @Test
    fun embedPayloadFailsTyped() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            CBSNewsEmbedIE(http(transfer())).extract("https://www.cbsnews.com/embed/video/?v=1#fixture")
        }
        assertTrue(error.message!!.contains("zlib"))
    }

    // ----------------------------------------------------------------- live

    @Test
    fun liveRundownYieldsTheM3u8() = runTest {
        val url = "https://www.cbsnews.com/live/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://feeds-cbsn.cbsnews.com/2.0/rundown/?partner=cbsnsite&edition=CBSN-US&type=live",
                contentType = "application/json",
                body = """
                    {"navigation": {"data": [{"headline": "Fixture Live", "rundown_slug": "fixture rundown",
                      "videoUrlDAI": "https://media.example/live/master.m3u8",
                      "images": {"thumbnail_url_hd": "https://media.example/live.jpg"}}]}}
                """.trimIndent(),
            ),
        )
        val info = CBSNewsLiveIE(http(transfer)).extract(url)
        assertEquals("CBSN-US", info.id)
        assertEquals("Fixture Live", info.title)
        assertEquals(true, info.isLive)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun liveVideoStoryYieldsTheM3u8() = runTest {
        val url = "https://www.cbsnews.com/live/video/fixture-story/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "http://feeds.cbsn.cbsnews.com/rundown/story?device=desktop&dvr_slug=fixture-story",
                contentType = "application/json",
                body = """
                    {"headline": "Fixture Story", "url": "https://media.example/story/master.m3u8",
                     "thumbnail_url_hd": "https://media.example/story.jpg", "segmentDur": "00:05:34"}
                """.trimIndent(),
            ),
        )
        val info = CBSNewsLiveVideoIE(http(transfer)).extract(url)
        assertEquals("fixture-story", info.id)
        assertEquals(334.0, info.duration)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun newsPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.cbsnews.com/video/fixture-video/"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Video Full"),
                "formats.0.url" to Expect.Value("https://media.example/video/clip.mp4"),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = url, contentType = "text/html", body = payloadPage(mp4Item)),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> CBSNewsIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
