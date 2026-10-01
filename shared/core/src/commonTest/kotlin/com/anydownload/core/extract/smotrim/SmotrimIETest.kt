package com.anydownload.core.extract.smotrim

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
 * Fixture cases for the Smotrim subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class SmotrimIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "1539617"

    private fun playerJson() = """
        {"data": {"template": {"share_url": "https://smotrim.ru/video/$videoId"},
          "playlist": {"medialist": [{"id": "$videoId", "title": "Fixture Video",
            "channelId": "76", "anons": "<p>Fixture description</p>",
            "sources": {"m3u8": {"auto": "https://media.example/hls/master.m3u8"}}}]}}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            SmotrimIE(http(transfer())) to "https://smotrim.ru/video/$videoId",
            SmotrimIE(http(transfer())) to "https://player.smotrim.ru/iframe/video/id/2988590",
            SmotrimAudioIE(http(transfer())) to "https://smotrim.ru/audio/id/100",
            SmotrimLiveIE(http(transfer())) to "https://smotrim.ru/channel/76",
            SmotrimLiveIE(http(transfer())) to "https://player.smotrim.ru/iframe/live/uid/381308c7-a066-4c4f-9656-83e2e792a7b4",
            SmotrimLiveIE(http(transfer())) to "https://testplayer.vgtrk.com/iframe/live/id/19201",
            SmotrimPlaylistIE(http(transfer())) to "https://smotrim.ru/brand/64356",
            SmotrimPlaylistIE(http(transfer())) to "https://smotrim.ru/podcast/8021",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(SmotrimAudioIE(http(transfer())).suitable("https://smotrim.ru/video/$videoId"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun videoApiYieldsHlsAndMetadata() = runTest {
        val url = "https://smotrim.ru/video/$videoId"
        val page = """
            <html><head><meta property="og:image" content="https://media.example/thumb.jpg"></head></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://player.smotrim.ru/iframe/datavideo/id/$videoId/sid/smotrim",
                contentType = "application/json",
                body = playerJson(),
            ),
            FixtureRoute(urlPattern = "https://smotrim.ru/video/$videoId", contentType = "text/html", body = page),
        )
        val info = SmotrimIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("76", info.channelId)
        assertEquals(false, info.isLive)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    @Test
    fun lockedItemFailsTyped() = runTest {
        val url = "https://smotrim.ru/video/$videoId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://player.smotrim.ru/iframe/datavideo/id/$videoId/sid/smotrim",
                contentType = "application/json",
                body = """
                    {"data": {"playlist": {"medialist": [{"id": "$videoId", "locked": true}]}}}
                """.trimIndent(),
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            SmotrimIE(http(transfer)).extract(url)
        }
    }

    // ---------------------------------------------------------------- channel

    @Test
    fun channelPageScansThePlayerFrame() = runTest {
        val url = "https://smotrim.ru/channel/76"
        val page = """
            <html><body><div class="main-player__frame" src="//player.smotrim.ru/iframe/live/id/19201"></div></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "https://smotrim.ru/channel/76", contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://smotrim.ru/channel/4",
                contentType = "text/html",
                body = "<html></html>",
            ),
            FixtureRoute(
                urlPattern = "https://player.smotrim.ru/iframe/datalive/uid/19201/sid/smotrim",
                contentType = "application/json",
                body = """
                    {"data": {"template": {"share_url": "https://smotrim.ru/channel/4"},
                      "playlist": {"medialist": [{"id": "19201", "title": "Fixture Live",
                        "sources": {"m3u8": {"auto": "https://media.example/hls/live.m3u8"}}}]}}}
                """.trimIndent(),
            ),
        )
        val info = SmotrimLiveIE(http(transfer)).extract(url)
        assertEquals("19201", info.id)
        assertEquals("Fixture Live", info.title)
        assertEquals(true, info.isLive)
    }

    // --------------------------------------------------------------- playlist

    @Test
    fun brandListingYieldsEntries() = runTest {
        val url = "https://smotrim.ru/brand/64356"
        val page = """
            <html><head><meta property="og:title" content="Fixture Brand"></head>
            <body><div class="brand-main-item__videos">Videos</div></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://smotrim.ru/api/videos*page=1",
                contentType = "application/json",
                body = """
                    {"contents": [{"list": [{"link": "/video/$videoId"}, {"link": "/video/2988590"}]}]}
                """.trimIndent(),
            ),
        )
        val info = SmotrimPlaylistIE(http(transfer)).extract(url)
        assertEquals("64356", info.id)
        assertEquals("Fixture Brand", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://smotrim.ru/video/$videoId", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://smotrim.ru/video/$videoId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Video"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://player.smotrim.ru/iframe/datavideo/id/$videoId/sid/smotrim",
                    contentType = "application/json",
                    body = playerJson(),
                ),
                FixtureRoute(
                    urlPattern = "https://smotrim.ru/video/$videoId",
                    contentType = "text/html",
                    body = "<html></html>",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SmotrimIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
