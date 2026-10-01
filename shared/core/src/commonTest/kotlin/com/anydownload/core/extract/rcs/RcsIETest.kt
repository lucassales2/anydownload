package com.anydownload.core.extract.rcs

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
 * Fixture cases for the RCS subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class RcsIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "b727632a-f9d0-11ea-91b0-38d50a849abb"

    private fun videoJson(id: String = videoId) = """
        <html><body>##start-video##
        {"id": "$id", "title": "Fixture RCS", "description": "<p>Fixture description</p>",
         "provider": "Fixture Provider",
         "mediaProfile": {"mediaFile": [
           {"mimeType": "application/x-mpegURL", "value": "https://vod.rcsobjects.it/hls/master.csmil/master.m3u8", "bitrate": 2000},
           {"mimeType": "audio/mpeg", "value": "https://media.example/audio/song.mp3", "bitrate": 128}]}}
        ##end-video##</body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            RCSEmbedsIE(http(transfer())) to "https://video.rcs.it/video-embed/iodonna-0001585037",
            RCSEmbedsIE(http(transfer())) to "https://video.gazzanet.gazzetta.it/video-embed/gazzanet-mo05-0000260789",
            RCSIE(http(transfer())) to "https://video.corriere.it/sport/formula-1/fixture/$videoId",
            RCSIE(http(transfer())) to "https://viaggi.corriere.it/video/fixture-video/",
            RCSVariousIE(http(transfer())) to "https://www.leitv.it/benessere/mal-di-testa/",
            RCSVariousIE(http(transfer())) to "https://www.youreporter.it/fiume-sesia-3-ottobre-2020/",
            RCSVariousIE(http(transfer())) to "https://www.amica.it/video-post/fixture-post/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(RCSVariousIE(http(transfer())).suitable("https://www.example.com/x/"))
    }

    // ------------------------------------------------------------- json by id

    @Test
    fun videoJsonByIdYieldsFormats() = runTest {
        val url = "https://video.corriere.it/sport/formula-1/fixture/$videoId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://video.corriere.it/video-json/$videoId",
                contentType = "application/json",
                body = videoJson(),
            ),
        )
        val info = RCSIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture RCS", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("Fixture Provider", info.channel)
        assertEquals(2, info.formats.size)
        assertEquals("https://vod.rcsobjects.it/hls/master.urlset/master.m3u8", info.formats[0].url)
        assertEquals("mp3", info.formats[1].ext)
        assertEquals("https://media.example/audio/song.mp3", info.formats[1].url)
    }

    // ------------------------------------------------------ data-config scan

    @Test
    fun dataConfigScanResolvesTheVideoId() = runTest {
        val url = "https://www.leitv.it/benessere/mal-di-testa/"
        val page = """
            <html><body>
            <div id="divVideoPlayer" data-config="{&quot;newspaper&quot;: &quot;leitv&quot;, &quot;uuid&quot;: &quot;$videoId&quot;}"></div>
            </body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "https://www.leitv.it/benessere/mal-di-testa/", contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://video.leitv.it/video-json/$videoId",
                contentType = "application/json",
                body = videoJson(),
            ),
        )
        val info = RCSVariousIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture RCS", info.title)
    }

    // ---------------------------------------------------- embed redirect scan

    @Test
    fun pageWithoutVideoDataRedirectsToTheEmbed() = runTest {
        val url = "https://www.amica.it/video-post/fixture-post/"
        val page = """
            <html><body>
            <iframe src="//video.rcs.it/video-embed/amica-0001225365"></iframe>
            </body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "https://www.amica.it/video-post/fixture-post/", contentType = "text/html", body = page),
        )
        val info = RCSVariousIE(http(transfer)).extract(url)
        assertEquals("https://video.rcs.it/video-embed/amica-0001225365", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun embedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://video.rcs.it/video-embed/iodonna-0001585037",
            infoDict = mapOf(
                "id" to Expect.Value("iodonna-0001585037"),
                "title" to Expect.Value("Fixture RCS"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://video.rcs.it/video-json/iodonna-0001585037",
                    contentType = "application/json",
                    body = videoJson("iodonna-0001585037"),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RCSEmbedsIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
