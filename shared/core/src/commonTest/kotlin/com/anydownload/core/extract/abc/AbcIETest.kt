package com.anydownload.core.extract.abc

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
 * Fixture cases for the ABC subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class AbcIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ABCIE(http(transfer())) to "https://www.abc.net.au/news/2023-06-25/fixture/102520540",
            ABCIE(http(transfer())) to "https://www.abc.net.au/btn/classroom/fixture/10527914",
            ABCIViewIE(http(transfer())) to "https://iview.abc.net.au/show/utopia/series/1/video/CO1211V001S00",
            ABCIViewShowSeriesIE(http(transfer())) to "https://iview.abc.net.au/show/upper-middle-bogan",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ABCIE(http(transfer())).suitable("https://iview.abc.net.au/show/utopia"))
    }

    // ------------------------------------------------------------- news page

    @Test
    fun directAudioLinkYieldsTheMp3() = runTest {
        val url = "https://www.abc.net.au/listen/programs/fixture/123456"
        val page = """
            <html><head>
            <meta property="og:title" content="Fixture Listen">
            <meta property="og:description" content="Fixture description">
            <meta property="og:image" content="https://media.example/thumb.jpg">
            </head><body>
            <a href="https://media.example/audio/fixture.mp3" data-duration="300" title="Download audio directly">Audio</a>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = ABCIE(http(transfer)).extract(url)
        assertEquals("123456", info.id)
        assertEquals("Fixture Listen", info.title)
        assertEquals("https://media.example/audio/fixture.mp3", info.formats.single().url)
        assertEquals("mp3", info.formats.single().ext)
    }

    @Test
    fun sourcesJsonYieldsTheVideoFormats() = runTest {
        val url = "https://www.abc.net.au/news/2023-06-25/fixture/102520540"
        val page = """
            <html><head><meta property="og:title" content="Fixture News"></head><body>
            <script>window.inlineVideoData = {"sources": [
              {"url": "https://media.example/video/720.mp4", "height": 720, "width": 1280, "codec": "avc1"},
              {"url": "https://media.example/video/_1500k.mp4", "label": "1500", "codec": "avc1"}
            ]};</script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = ABCIE(http(transfer)).extract(url)
        assertEquals(2, info.formats.size)
        assertEquals(720L, info.formats[0].height)
        assertEquals("1500", info.formats[1].formatId)
        assertEquals(1500.0, info.formats[1].tbr)
    }

    @Test
    fun youtubeEmbedBecomesAChildEntry() = runTest {
        val url = "https://www.abc.net.au/news/2023-06-25/fixture/102520540"
        val page = """
            <html><body>
            <iframe width="100%" src="//www.youtube-nocookie.com/embed/fake_value"></iframe>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = ABCIE(http(transfer)).extract(url)
        assertEquals("https://www.youtube-nocookie.com/embed/fake_value", info.entries.single().url)
    }

    // ------------------------------------------------------------- iview

    @Test
    fun iviewFailsTypedOnTheHmacToken() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            ABCIViewIE(http(transfer())).extract(
                "https://iview.abc.net.au/show/utopia/series/1/video/CO1211V001S00",
            )
        }
        assertTrue(error.message!!.contains("HMAC"))
    }

    @Test
    fun showSeriesPageListsEpisodes() = runTest {
        val url = "https://iview.abc.net.au/show/upper-middle-bogan"
        val state = """
            {"route": {"pageData": {"_embedded": {
              "highlightVideo": {"shareUrl": "https://iview.abc.net.au/video/CO1108V001S00"},
              "selectedSeries": {"id": "124870-1", "title": "Series 1", "showTitle": "Upper Middle Bogan",
                                 "description": "Fixture series",
                                 "_embedded": {"videoEpisodes": [
                                   {"shareUrl": "https://iview.abc.net.au/video/CO1108V001S00"},
                                   {"shareUrl": "https://iview.abc.net.au/video/CO1108V002S00"}]}}}}}}
        """.trimIndent()
        val page = "<html><body><script>window.__INITIAL_STATE__ = '${state.replace("'", "\\'")}';</script></body></html>"
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = ABCIViewShowSeriesIE(http(transfer)).extract(url)
        assertEquals("https://iview.abc.net.au/video/CO1108V001S00", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun newsAudioIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.abc.net.au/listen/programs/fixture/123456"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("123456"),
                "title" to Expect.Value("Fixture Listen"),
                "formats.0.ext" to Expect.Value("mp3"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><head><meta property="og:title" content="Fixture Listen"></head><body>
                        <a href="https://media.example/audio/fixture.mp3" data-duration="300" title="Download audio directly">Audio</a>
                        </body></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ABCIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
