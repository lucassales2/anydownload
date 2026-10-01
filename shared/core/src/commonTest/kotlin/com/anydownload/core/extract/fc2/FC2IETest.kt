package com.anydownload.core.extract.fc2

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
 * Fixture cases for the FC2 subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class FC2IETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val contentUrl = "https://video.fc2.com/content/20121209FP73fxDx"
    private val embedUrl = "http://video.fc2.com/flv2.swf?t=201404182936758512407645" +
        "&i=20130316kwishtfitaknmcgd76kjd864hso93htfjcnaogz629mcgfs6rbfk0hsycma7shkf85937cbchfygd74" +
        "&i=201403223kCqB3Ez&d=2625&sj=11&lang=ja&rel=1&from=11&cmt=1&tk=fake_value&tl=Fixture%20Embed"
    private val liveUrl = "https://live.fc2.com/57892267/"

    private val contentPage = """
        <html><head>
        <meta property="og:image" content="https://media.example/thumb.jpg">
        <meta property="og:description" content="Fixture FC2 description">
        </head><body><h2 class="videoCnt_title">Fixture FC2 Title</h2></body></html>
    """.trimIndent()

    private fun apiRoute(id: String, type: Int, url: String) = FixtureRoute(
        urlPattern = "https://video.fc2.com/api/v3/videoplaylist/$id*",
        contentType = "application/json",
        body = """{"type": $type, "playlist": {"nq": "$url"}}""",
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val fc2 = FC2IE(http(transfer()))
        val fc2Cases = listOf(
            contentUrl,
            "http://video.fc2.com/en/content/20121103kUan1KHs",
            "http://video.fc2.com/en/a/content/20130926eZpARwsF",
            "fc2:201403223kCqB3Ez",
        )
        for (url in fc2Cases) {
            assertTrue(fc2.suitable(url), "FC2 must match: $url")
        }
        assertFalse(fc2.suitable("https://www.example.com/content/20121209FP73fxDx"))

        assertTrue(FC2EmbedIE(http(transfer())).suitable(embedUrl))
        assertTrue(FC2LiveIE(http(transfer())).suitable(liveUrl))
        assertFalse(FC2LiveIE(http(transfer())).suitable(contentUrl))
    }

    // ---------------------------------------------------------------- content

    @Test
    fun contentPageYieldsTheHlsRow() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$contentUrl*", contentType = "text/html", body = contentPage),
            apiRoute("20121209FP73fxDx", 2, "https://media.example/master.m3u8"),
        )
        val info = FC2IE(http(transfer)).extract(contentUrl)
        assertEquals("20121209FP73fxDx", info.id)
        assertEquals("Fixture FC2 Title", info.title)
        assertEquals("Fixture FC2 description", info.description)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
    }

    @Test
    fun directRowJoinsTheRelativeUrl() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$contentUrl*", contentType = "text/html", body = contentPage),
            apiRoute("20121209FP73fxDx", 1, "/rel/video.mp4"),
        )
        val info = FC2IE(http(transfer)).extract(contentUrl)
        assertEquals(1, info.formats.size)
        assertEquals("https://video.fc2.com/rel/video.mp4", info.formats[0].url)
        assertEquals(null, info.formats[0].protocol)
    }

    // ------------------------------------------------------------------ embed

    @Test
    fun embedDelegatesWithTheComputedThumbnail() = runTest {
        val transfer = transfer(
            apiRoute("201403223kCqB3Ez", 2, "https://media.example/master.m3u8"),
        )
        val info = FC2EmbedIE(http(transfer)).extract(embedUrl)
        assertEquals("201403223kCqB3Ez", info.id)
        assertEquals("Fixture Embed", info.title)
        assertEquals(
            "http://video11-thumbnail.fc2.com/up/pic/201403/22/E/z/201403223kCqB3Ez.jpg",
            info.thumbnails.single().url,
        )
        assertEquals(1, info.formats.size)
    }

    // ------------------------------------------------------------------- live

    @Test
    fun livePageFailsTypedAtTheWebSocketWall() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$liveUrl*", contentType = "text/html", body = "<html><body>live</body></html>"),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            FC2LiveIE(http(transfer)).extract(liveUrl)
        }
        assertTrue(error.message!!.contains("WebSocket"), error.message)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun contentIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = contentUrl,
            infoDict = mapOf(
                "id" to Expect.Value("20121209FP73fxDx"),
                "title" to Expect.Value("Fixture FC2 Title"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "$contentUrl*", contentType = "text/html", body = contentPage),
                apiRoute("20121209FP73fxDx", 2, "https://media.example/master.m3u8"),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> FC2IE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun embedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = embedUrl,
            infoDict = mapOf(
                "id" to Expect.Value("201403223kCqB3Ez"),
                "title" to Expect.Value("Fixture Embed"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(apiRoute("201403223kCqB3Ez", 2, "https://media.example/master.m3u8")),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> FC2EmbedIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
