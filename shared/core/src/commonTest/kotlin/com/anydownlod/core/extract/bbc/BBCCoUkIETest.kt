package com.anydownlod.core.extract.bbc

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.CaseResult
import com.anydownlod.core.extract.harness.Expect
import com.anydownlod.core.extract.harness.ExtractorCase
import com.anydownlod.core.extract.harness.ExtractorTestRun
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import com.anydownlod.core.extract.harness.runCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the BBC programme/iPlayer subset. Every id, host, and
 * address is synthesized (`*.example`); no cookie, account, or signed media
 * URL appears.
 */
class BBCCoUkIETest {

    private val mediaJson = """
        {
          "media": [
            {"kind": "video", "bitrate": 1500000, "encoding": "h264", "width": 1280, "height": 720,
             "media_file_size": 1000,
             "connection": [
               {"href": "https://vs.example/hls/master.m3u8", "kind": "video", "protocol": "https",
                "transferFormat": "hls", "supplier": "akamai"},
               {"href": "https://vs.example/dash/manifest.mpd", "kind": "video", "protocol": "https",
                "transferFormat": "dash", "supplier": "dashsupplier"},
               {"href": "https://vs.example/video-720.mp4", "kind": "video", "protocol": "https", "supplier": "http"}
             ]},
            {"kind": "audio", "bitrate": 128000, "encoding": "aac",
             "connection": [{"href": "https://vs.example/audio.mp4", "kind": "audio", "protocol": "https",
                             "supplier": "http"}]},
            {"kind": "captions",
             "connection": [{"href": "https://vs.example/captions.ttml", "kind": "captions", "protocol": "https"}]}
          ]
        }
    """.trimIndent()

    private fun pageWithMediator(vpid: String): String = """
        <html><head>
        <meta property="og:title" content="Synthetic BBC programme">
        <meta name="description" content="A synthetic BBC description.">
        <meta property="og:image" content="https://ichef.example/thumb.jpg">
        </head><body>
        <script>mediator.bind({"player":{"vpid":"$vpid","duration":300}}, document.getElementById('media_player'));</script>
        </body></html>
    """.trimIndent()

    private fun routes(
        pageUrl: String = "https://www.bbc.co.uk/programmes/p0000001",
        page: String = pageWithMediator("p0000002"),
        vpid: String = "p0000002",
        iptvAll: String = mediaJson,
        pc: String = "{}",
    ): List<FixtureRoute> = listOf(
        FixtureRoute(urlPattern = pageUrl, contentType = "text/html", body = page),
        FixtureRoute(urlPattern = mediaSelector("iptv-all", vpid), body = iptvAll),
        FixtureRoute(urlPattern = mediaSelector("pc", vpid), body = pc),
    )

    private fun mediaSelector(mediaSet: String, vpid: String) =
        "https://open.live.bbc.co.uk/mediaselector/6/select/version/2.0/mediaset/$mediaSet/vpid/$vpid"

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): BBCCoUkIE =
        BBCCoUkIE(ExtractorHttp(transfer))

    // ------------------------------------------------------------ URL matching

    @Test
    fun programmeAndIplayerFormsMatch() {
        val ie = extractor(transfer())
        val urls = listOf(
            "https://www.bbc.co.uk/programmes/p0000001",
            "http://bbc.co.uk/programmes/b039g8p7",
            "https://www.bbc.co.uk/iplayer/episode/p0000002/Synthetic_Title",
            "https://www.bbc.co.uk/iplayer/playlist/p0000003",
        )
        for (url in urls) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://www.bbc.co.uk/programmes/articles/abc123"))
        assertFalse(ie.suitable("https://www.bbc.co.uk/programmes/p0000001/episodes"))
        assertFalse(ie.suitable("https://www.bbc.com/news/world-europe-32668511"))
    }

    // -------------------------------------------------------------- extraction

    @Test
    fun mediaSelectorMapsFormatsAndMetadata() = runTest {
        val transfer = transfer(*routes().toTypedArray())
        val info = extractor(transfer).extract("https://www.bbc.co.uk/programmes/p0000001")

        assertEquals("p0000002", info.id)
        assertEquals("Synthetic BBC programme", info.title)
        assertEquals("A synthetic BBC description.", info.description)
        assertEquals(300.0, info.duration)
        assertEquals("https://ichef.example/thumb.jpg", info.thumbnails.single().url)

        val hls = info.formats.single { it.protocol == "m3u8_native" }
        assertEquals("https://vs.example/hls/master.m3u8", hls.url)
        val dash = info.formats.single { it.protocol == "http_dash_segments" }
        assertEquals("https://vs.example/dash/manifest.mpd", dash.url)

        val video = info.formats.single { it.url == "https://vs.example/video-720.mp4" }
        assertEquals(1280L, video.width)
        assertEquals(720L, video.height)
        assertEquals(1500000.0, video.tbr)
        assertEquals("h264", video.vcodec)
        assertEquals(1000L, video.filesize)

        val audio = info.formats.single { it.url == "https://vs.example/audio.mp4" }
        assertEquals("none", audio.vcodec)
        assertEquals("aac", audio.acodec)
        assertEquals(128000.0, audio.abr)

        val subtitle = info.subtitles.single()
        assertEquals("en", subtitle.language)
        assertEquals("ttml", subtitle.formats.single().ext)
        assertEquals("https://vs.example/captions.ttml", subtitle.formats.single().url)
    }

    @Test
    fun vpidFieldIsUsedWhenThereIsNoMediator() = runTest {
        val page = """
            <html><head><meta property="og:title" content="Fixture title"></head>
            <body><script>window.__DATA__ = {"vpid":"p0000003"};</script></body></html>
        """.trimIndent()
        val transfer = transfer(
            *routes(
                pageUrl = "https://www.bbc.co.uk/iplayer/episode/p0000003/Title",
                page = page,
                vpid = "p0000003",
            ).toTypedArray(),
        )
        val info = extractor(transfer).extract("https://www.bbc.co.uk/iplayer/episode/p0000003/Title")
        assertEquals("p0000003", info.id)
        assertEquals("Fixture title", info.title)
        assertEquals(4, info.formats.size)
    }

    @Test
    fun iplayerPlaylistUrlUsesThePageVpid() = runTest {
        val transfer = transfer(
            *routes(pageUrl = "https://www.bbc.co.uk/iplayer/playlist/p0000001").toTypedArray(),
        )
        val info = extractor(transfer).extract("https://www.bbc.co.uk/iplayer/playlist/p0000001")
        assertEquals("p0000002", info.id)
    }

    // ----------------------------------------------------------------- errors

    @Test
    fun geoErrorFallsBackToTheNextMediaSet() = runTest {
        val transfer = transfer(
            *routes(iptvAll = """{"result":"geolocation"}""", pc = mediaJson).toTypedArray(),
        )
        val info = extractor(transfer).extract("https://www.bbc.co.uk/programmes/p0000001")
        assertEquals(4, info.formats.size)
    }

    @Test
    fun allGeoRestrictedFailsTyped() = runTest {
        val transfer = transfer(
            *routes(
                iptvAll = """{"result":"geolocation"}""",
                pc = """{"result":"notukerror"}""",
            ).toTypedArray(),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            extractor(transfer).extract("https://www.bbc.co.uk/programmes/p0000001")
        }
    }

    @Test
    fun pageWithoutAVpidFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.bbc.co.uk/programmes/p0000001",
                contentType = "text/html",
                body = "<html><body>Nothing playable.</body></html>",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            extractor(transfer).extract("https://www.bbc.co.uk/programmes/p0000001")
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun bbcIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://www.bbc.co.uk/programmes/p0000001",
            infoDict = mapOf(
                "id" to Expect.Value("p0000002"),
                "title" to Expect.Value("Synthetic BBC programme"),
                "duration" to Expect.Value(300L),
                "formats" to Expect.Count(4),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = routes(),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> BBCCoUkIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
