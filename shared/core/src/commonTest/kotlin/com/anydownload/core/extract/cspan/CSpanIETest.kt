package com.anydownload.core.extract.cspan

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
 * Fixture cases for the C-SPAN subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class CSpanIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val clipUrl = "http://www.c-span.org/video/?c4486943/cspan-international-health-care-models"
    private val congressUrl = "https://www.c-span.org/congress/?chamber=house&date=2017-12-13&t=1513208380"

    private val jwsetupPage = """
        <html><head>
        <meta property="og:title" content="CSPAN - International Health Care Models">
        </head><body>
        <h1 class="video-page-title">CSPAN - International Health Care Models</h1>
        <div itemprop="description">Fixture description</div>
        <span itemprop="thumbnailUrl">https://media.example/thumb.jpg</span>
        <span itemprop="uploadDate">2015-02-04T10:00:00Z</span>
        <span class="views">1,234 Views</span>
        <script>
        jwsetup = {
          "sources": [
            {"file": "https://media.example/master.m3u8", "type": "hls", "label": "Auto"},
            {"file": "/mp4/720p.mp4", "type": "video/mp4", "label": "720p", "height": 720}
          ],
          "tracks": [{"kind": "captions", "file": "/caps.vtt", "label": "English"}]
        };
        jwsetup.seclength = 3600;
        </script>
        </body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cspan = CSpanIE(http(transfer()))
        val cases = listOf(
            clipUrl,
            "http://www.c-span.org/video/?318608-1/gm-ignition-switch-recall",
            "https://www.c-span.org/video/?437336-1/judiciary-antitrust-competition-policy-consumer-rights",
        )
        for (url in cases) {
            assertTrue(cspan.suitable(url), "CSpan must match: $url")
        }
        assertFalse(cspan.suitable("https://www.example.com/video/?c4486943/x"))
        assertTrue(CSpanCongressIE(http(transfer())).suitable(congressUrl))
        assertFalse(CSpanCongressIE(http(transfer())).suitable(clipUrl))
    }

    // ---------------------------------------------------------------- jwsetup

    @Test
    fun jwsetupYieldsSourcesTracksAndMetadata() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = "$clipUrl*", contentType = "text/html", body = jwsetupPage))
        val info = CSpanIE(http(transfer)).extract(clipUrl)
        assertEquals("c4486943", info.id)
        assertEquals("CSPAN - International Health Care Models", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals("20150204", info.uploadDate)
        assertEquals(3600.0, info.duration)
        assertEquals(1234L, info.viewCount)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("http://www.c-span.org/mp4/720p.mp4", info.formats[1].url)
        assertEquals("mp4", info.formats[1].ext)
        assertEquals("http://www.c-span.org/video/?c4486943/cspan-international-health-care-models", info.formats[0].httpHeaders?.get("Referer"))
        assertEquals("English", info.subtitles.single().language)
        assertEquals("vtt", info.subtitles.single().formats.single().ext)
        assertEquals("http://www.c-span.org/caps.vtt", info.subtitles.single().formats.single().url)
    }

    // -------------------------------------------------------------- redirects

    @Test
    fun ustreamEmbedBecomesARedirect() = runTest {
        val page = "<html><body><iframe src=\"https://www.ustream.tv/embed/58428542\"></iframe></body></html>"
        val transfer = transfer(FixtureRoute(urlPattern = "$clipUrl*", contentType = "text/html", body = page))
        val info = CSpanIE(http(transfer)).extract(clipUrl)
        assertEquals("https://www.ustream.tv/embed/58428542", info.redirectUrl)
    }

    @Test
    fun brightcoveEmbedBecomesARedirect() = runTest {
        val page = """
            <html><body>
            <div id='brightcove-player-embed' data-bcid='12345' data-bcaccountid='999'
                 data-noprebcplayerid='P1' data-newbcplayerid='default'></div>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = "$clipUrl*", contentType = "text/html", body = page))
        val info = CSpanIE(http(transfer)).extract(clipUrl)
        assertEquals("http://players.brightcove.net/999/P1_default/index.html?videoId=12345", info.redirectUrl)
    }

    @Test
    fun senateIframeBecomesARedirect() = runTest {
        val page = """
            <html><body>
            <iframe src="http://www.senate.gov/isvp/?comm=judiciary&filename=judiciary031715"></iframe>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = "$clipUrl*", contentType = "text/html", body = page))
        val info = CSpanIE(http(transfer)).extract(clipUrl)
        assertEquals("http://www.senate.gov/isvp/?comm=judiciary&filename=judiciary031715", info.redirectUrl)
    }

    @Test
    fun vlplayerErrorMessageFailsTyped() = runTest {
        val page = "<html><body><div class=\"VLplayer-error-message\">Video not available</div></body></html>"
        val transfer = transfer(FixtureRoute(urlPattern = "$clipUrl*", contentType = "text/html", body = page))
        assertFailsWith<ExtractionError.Unavailable> {
            CSpanIE(http(transfer)).extract(clipUrl)
        }
    }

    // --------------------------------------------------------------- congress

    @Test
    fun congressPageYieldsTheJwsetupSources() = runTest {
        val page = """
            <html><head><meta property="og:title" content="Congressional Chronicle | C-SPAN"></head>
            <body>
            <script>jwsetup = {"sources": [{"file": "https://media.example/master.m3u8", "type": "hls"}], "tracks": []};</script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = "$congressUrl*", contentType = "text/html", body = page))
        val info = CSpanCongressIE(http(transfer)).extract(congressUrl)
        assertEquals("house_2017-12-13", info.id)
        assertEquals("Congressional Chronicle", info.title)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun clipIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = clipUrl,
            infoDict = mapOf(
                "id" to Expect.Value("c4486943"),
                "title" to Expect.Value("CSPAN - International Health Care Models"),
                "upload_date" to Expect.Value("20150204"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(FixtureRoute(urlPattern = "$clipUrl*", contentType = "text/html", body = jwsetupPage)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> CSpanIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun congressIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = congressUrl,
            infoDict = mapOf(
                "id" to Expect.Value("house_2017-12-13"),
                "title" to Expect.Value("Congressional Chronicle"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "$congressUrl*",
                    contentType = "text/html",
                    body = "<html><head><meta property=\"og:title\" content=\"Congressional Chronicle | C-SPAN\"></head>" +
                        "<body><script>jwsetup = {\"sources\": [{\"file\": \"https://media.example/master.m3u8\", \"type\": \"hls\"}]};</script></body></html>",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> CSpanCongressIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
