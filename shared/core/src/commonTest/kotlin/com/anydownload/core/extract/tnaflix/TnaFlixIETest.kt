package com.anydownload.core.extract.tnaflix

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
 * Fixture cases for the TNAFlix network subset. Ids, titles, and media
 * paths are synthesized; media lives on `media.example`, and the vkey/nkey
 * values are fake.
 */
class TnaFlixIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "6538"

    private fun page() = """
        <html><head><title>Fixture TNAFlix - TNAFlix Porn Videos</title>
        <meta property="og:description" content="Fixture description">
        <meta name="duration" content="2:44"></head>
        <body><input type="hidden" name="config" value="//media.example/config/6538.xml">
        <span>by <a href="/profile/fixture">fixtureuser</a></span></body></html>
    """.trimIndent()

    private fun configXml() = """
        <config><videoConfig><type>mp4</type></videoConfig>
        <videoLink>https://media.example/video/single.mp4</videoLink>
        <quality><item><res>720p</res><videoLink>https://media.example/video/720.mp4</videoLink></item></quality>
        <startThumb>https://media.example/start.jpg</startThumb>
        <timeline><imagePattern>https://media.example/thumb/#.jpg</imagePattern>
        <imageFirst>1</imageFirst><imageLast>3</imageLast>
        <imageWidth>100</imageWidth><imageHeight>50</imageHeight></timeline></config>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            TNAFlixNetworkEmbedIE(http(transfer())) to "https://player.tnaflix.com/video/$videoId",
            TNAFlixIE(http(transfer())) to
                "https://www.tnaflix.com/teen-porn/Educational-xxx-video/video$videoId",
            EMPFlixIE(http(transfer())) to "http://www.empflix.com/amateur-porn/Fixture/video33051",
            EMPFlixIE(http(transfer())) to "http://www.empflix.com/videos/Fixture-33051.html",
            MovieFapIE(http(transfer())) to
                "http://www.moviefap.com/videos/be9867c9416c19f54a4a/fixture.html",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(TNAFlixIE(http(transfer())).suitable("https://player.tnaflix.com/video/$videoId"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun xmlConfigYieldsFormatsAndThumbnails() = runTest {
        val url = "https://www.tnaflix.com/teen-porn/Educational-xxx-video/video$videoId"
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page()),
            FixtureRoute(
                urlPattern = "http://media.example/config/6538.xml",
                contentType = "text/xml",
                body = configXml(),
            ),
        )
        val info = TNAFlixIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture TNAFlix", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("fixtureuser", info.uploader)
        assertEquals(164.0, info.duration)
        assertEquals(18, info.ageLimit)
        assertEquals(2, info.formats.size)
        assertEquals("mp4", info.formats[0].ext)
        assertEquals(720L, info.formats[1].height)
        assertEquals(4, info.thumbnails.size)
    }

    // ------------------------------------------------------------------- embed

    @Test
    fun embedRedirectsToTheWatchPage() = runTest {
        val url = "https://player.tnaflix.com/video/$videoId"
        val info = TNAFlixNetworkEmbedIE(http(transfer())).extract(url)
        assertEquals("http://www.tnaflix.com/category/$videoId/video$videoId", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.tnaflix.com/teen-porn/Educational-xxx-video/video$videoId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture TNAFlix"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = url, contentType = "text/html", body = page()),
                FixtureRoute(
                    urlPattern = "http://media.example/config/6538.xml",
                    contentType = "text/xml",
                    body = configXml(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TNAFlixIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
