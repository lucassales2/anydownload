package com.anydownload.core.extract.espn

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
 * Fixture cases for the ESPN subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no key or token appears.
 */
class EspnIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "10365079"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ESPNIE(http(transfer())) to "http://espn.go.com/video/clip?id=$videoId",
            ESPNIE(http(transfer())) to "https://cdn.espn.go.com/video/clip/_/id/19771774",
            ESPNArticleIE(http(transfer())) to "http://espn.go.com/nba/recap?gameId=400793786",
            FiveThirtyEightIE(http(transfer())) to "https://fivethirtyeight.com/features/fixture-feature",
            ESPNCricInfoIE(http(transfer())) to "https://www.espncricinfo.com/video/fixture-1289135",
            WatchESPNIE(http(transfer())) to
                "https://www.espn.com/watch/player/_/id/90a2c85d-75e0-4b1e-a878-8e428a3cb2f3",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ESPNArticleIE(http(transfer())).suitable("http://espn.go.com/video/clip?id=$videoId"))
    }

    // ---------------------------------------------------------------- clip

    @Test
    fun clipApiYieldsTheSourceFormats() = runTest {
        val url = "http://espn.go.com/video/clip?id=$videoId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "http://api-app.espn.com/v1/video/clips/$videoId",
                contentType = "application/json",
                body = """
                    {"videos": [{"headline": "Fixture Clip", "caption": "Fixture caption",
                      "duration": 1302, "originalPublishDate": "2014-01-28T00:00:00Z",
                      "thumbnail": "https://media.example/thumb.jpg",
                      "links": {"source": {
                        "hls": {"href": "https://media.example/hls/master.m3u8"},
                        "mp4": {"href": "https://media.example/video/720p30_2000k.mp4"}}}}]}
                """.trimIndent(),
            ),
        )
        val info = ESPNIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Clip", info.title)
        assertEquals(1302.0, info.duration)
        assertEquals("20140128", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats.first { it.protocol == "m3u8_native" }.protocol)
        assertEquals(720L, info.formats.first { it.height == 720L }.height)
    }

    // -------------------------------------------------------------- article

    @Test
    fun articlePageRedirectsToTheClip() = runTest {
        val url = "http://espn.go.com/nba/recap?gameId=400793786"
        val page = """
            <html><body><a class="video-play-button" data-id="19771774"></a></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = ESPNArticleIE(http(transfer)).extract(url)
        assertEquals("http://espn.go.com/video/clip?id=19771774", info.redirectUrl)
    }

    // ------------------------------------------------------------- cricinfo

    @Test
    fun cricInfoApiYieldsTheHlsFormats() = runTest {
        val url = "https://www.espncricinfo.com/video/fixture-1289135"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://hs-consumer-api.espncricinfo.com/v1/pages/video/video-details?videoId=1289135",
                contentType = "application/json",
                body = """
                    {"video": {"title": "Fixture Cricket", "summary": "Fixture summary",
                      "duration": 96, "publishedAt": "2021-11-13T00:00:00Z",
                      "playbacks": [{"type": "HLS", "url": "https://media.example/hls/master.m3u8"},
                                    {"type": "AUDIO", "url": "https://media.example/audio/128.mp3"}]}}
                """.trimIndent(),
            ),
        )
        val info = ESPNCricInfoIE(http(transfer)).extract(url)
        assertEquals("1289135", info.id)
        assertEquals("Fixture Cricket", info.title)
        assertEquals("20211113", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("none", info.formats[1].vcodec)
    }

    // ----------------------------------------------------------------- wall

    @Test
    fun watchEspnFailsTypedOnTheTokenAndMvpdWalls() = runTest {
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            WatchESPNIE(http(transfer())).extract(
                "https://www.espn.com/watch/player/_/id/90a2c85d-75e0-4b1e-a878-8e428a3cb2f3",
            )
        }
        assertTrue(error.message!!.contains("MVPD"))
    }

    // --------------------------------------------------------------- harness

    @Test
    fun clipIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "http://espn.go.com/video/clip?id=$videoId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Clip"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "http://api-app.espn.com/v1/video/clips/$videoId",
                    contentType = "application/json",
                    body = """
                        {"videos": [{"headline": "Fixture Clip",
                          "links": {"source": {"hls": {"href": "https://media.example/hls/master.m3u8"}}}}]}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ESPNIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
