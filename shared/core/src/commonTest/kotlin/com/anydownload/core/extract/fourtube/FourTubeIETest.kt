package com.anydownload.core.extract.fourtube

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
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the FourTube subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class FourTubeIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val fourTubeUrl = "http://www.4tube.com/videos/209733/fixture-video"
    private val pornTubeUrl = "https://www.porntube.com/videos/fixture_7089759"

    private val fourTubePage = """
        <html><head>
        <meta name="name" content="Fixture FourTube Title">
        <meta name="uploadDate" content="2013-10-31T00:00:00Z">
        <meta name="thumbnailUrl" content="https://media.example/thumb.jpg">
        <meta name="duration" content="583">
        <meta itemprop="interactionCount" content="UserPlays:1,234">
        </head><body>
        <a class="item-to-subscribe" href="https://www.4tube.com/channels/wcp-club" title="Go to WCP Club page">WCP Club</a>
        <button data-id="209733" data-quality="720"></button>
        <button data-quality="1080"></button>
        </body></html>
    """.trimIndent()

    private val tokenRoute = FixtureRoute(
        urlPattern = "https://token.4tube.com/209733/desktop/720+1080",
        method = "POST",
        contentType = "application/json",
        body = """
            {"720": {"token": "https://media.example/720.mp4"},
             "1080": {"token": "https://media.example/1080.mp4"}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            FourTubeIE(http(transfer())) to fourTubeUrl,
            FourTubeIE(http(transfer())) to "http://www.4tube.com/embed/209733",
            FuxIE(http(transfer())) to "https://www.fux.com/video/195359/fixture",
            PornTubeIE(http(transfer())) to pornTubeUrl,
            PornerBrosIE(http(transfer())) to "https://www.pornerbros.com/videos/fixture_181369",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(FourTubeIE(http(transfer())).suitable("https://www.example.com/videos/209733/fixture"))
        assertFalse(FuxIE(http(transfer())).suitable(fourTubeUrl))
    }

    // --------------------------------------------------------------- fourtube

    @Test
    fun fourTubePageYieldsTheTokenRows() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$fourTubeUrl*", contentType = "text/html", body = fourTubePage),
            tokenRoute,
        )
        val info = FourTubeIE(http(transfer)).extract(fourTubeUrl)
        assertEquals("209733", info.id)
        assertEquals("Fixture FourTube Title", info.title)
        assertEquals("WCP Club", info.uploader)
        assertEquals("wcp-club", info.channelId)
        assertEquals("20131031", info.uploadDate)
        assertEquals(583.0, info.duration)
        assertEquals(1234L, info.viewCount)
        assertEquals(18, info.ageLimit)
        assertEquals(2, info.formats.size)
        assertEquals("720p", info.formats[0].formatId)
        assertEquals(720L, info.formats[0].height)
        assertEquals("https://media.example/1080.mp4", info.formats[1].url)
    }

    // --------------------------------------------------------------- porntube

    @Test
    fun pornTubeInitialStateYieldsTheRows() = runTest {
        val videoJson = """
            {"page": {"video": {"title": "Fixture PornTube", "mediaId": "555",
              "encodings": [{"height": 720}], "masterThumb": "https://media.example/pt.jpg",
              "user": {"username": "Alexy", "id": 1}, "channel": {"name": "Chan", "id": 2},
              "likes": 5, "playsQty": 10, "durationInSeconds": 100,
              "publishedAt": "2020-01-01T00:00:00Z"}}}
        """.trimIndent()
        val encoded = Base64.Default.encode(videoJson.encodeToByteArray())
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$pornTubeUrl*",
                contentType = "text/html",
                body = "<html><body><script>INITIALSTATE = \"$encoded\";</script></body></html>",
            ),
            FixtureRoute(
                urlPattern = "https://tkn.porntube.com/555/desktop/720",
                method = "POST",
                contentType = "application/json",
                body = """{"720": {"token": "https://media.example/720.mp4"}}""",
            ),
        )
        val info = PornTubeIE(http(transfer)).extract(pornTubeUrl)
        assertEquals("7089759", info.id)
        assertEquals("Fixture PornTube", info.title)
        assertEquals("Alexy", info.uploader)
        assertEquals("Chan", info.channel)
        assertEquals("2", info.channelId)
        assertEquals("20200101", info.uploadDate)
        assertEquals(100.0, info.duration)
        assertEquals(10L, info.viewCount)
        assertEquals(18, info.ageLimit)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/720.mp4", info.formats[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun fourTubeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = fourTubeUrl,
            infoDict = mapOf(
                "id" to Expect.Value("209733"),
                "title" to Expect.Value("Fixture FourTube Title"),
                "upload_date" to Expect.Value("20131031"),
                "age_limit" to Expect.Value(18),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "$fourTubeUrl*", contentType = "text/html", body = fourTubePage),
                tokenRoute,
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> FourTubeIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun pornTubeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val videoJson = """
            {"page": {"video": {"title": "Fixture PornTube", "mediaId": "555",
              "encodings": [{"height": 720}], "masterThumb": "https://media.example/pt.jpg",
              "user": {"username": "Alexy", "id": 1}, "channel": {"name": "Chan", "id": 2},
              "likes": 5, "playsQty": 10, "durationInSeconds": 100,
              "publishedAt": "2020-01-01T00:00:00Z"}}}
        """.trimIndent()
        val encoded = Base64.Default.encode(videoJson.encodeToByteArray())
        val case = ExtractorCase(
            url = pornTubeUrl,
            infoDict = mapOf(
                "id" to Expect.Value("7089759"),
                "title" to Expect.Value("Fixture PornTube"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "$pornTubeUrl*",
                    contentType = "text/html",
                    body = "<html><body><script>INITIALSTATE = \"$encoded\";</script></body></html>",
                ),
                FixtureRoute(
                    urlPattern = "https://tkn.porntube.com/555/desktop/720",
                    method = "POST",
                    contentType = "application/json",
                    body = """{"720": {"token": "https://media.example/720.mp4"}}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> PornTubeIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
