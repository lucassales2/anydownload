package com.anydownload.core.extract.condenast

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Condé Nast subset. Ids and media paths are synthesized
 * on `media.example`; the player ids are fake 24-hex values. No cookie, token,
 * or signed URL appears.
 */
class CondeNastIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val embedUrl = "https://player.cnevids.com/inline/video/59138decb57ac36b83000005.js?target=js-cne-player"
    private val watchUrl = "http://video.wired.com/watch/3d-printed-speakers-lit-with-led"
    private val seriesUrl = "http://video.gq.com/series/the-closer"

    private val playerRoute = FixtureRoute(
        urlPattern = "http://player.cnevids.com/embed-api.json?*",
        contentType = "application/json",
        body = """
            {"video": {"title": "Fixture Video", "brand": "wired", "duration": 120,
              "premiere_date": "2013-03-14T00:00:00+00:00",
              "poster_frame": "https://media.example/poster.jpg",
              "sources": [
                {"src": "https://media.example/master.m3u8", "type": "application/x-mpegURL"},
                {"src": "https://media.example/video.mp4", "type": "video/mp4", "quality": "high"}],
              "captions": {"vtt": {"src": "https://media.example/captions.vtt"},
                           "mp4": {"src": "https://media.example/ignored.mp4"}}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val ie = CondeNastIE(http(transfer()))
        val urls = listOf(
            watchUrl,
            "http://player.cnevids.com/embedjs/55f9cf8b61646d1acf00000c/5511d76261646d5566020000.js",
            embedUrl,
            "http://player-backend.cnevids.com/script/video/59138decb57ac36b83000005.js",
            "https://www.vanityfair.com/video/watch/vf-quiz-show-squid-game-s3",
            seriesUrl,
        )
        for (url in urls) {
            assertTrue(ie.suitable(url), "CondeNast must match: $url")
        }
        assertFalse(ie.suitable("https://www.example.com/watch/foo"))
    }

    // --------------------------------------------------------------- embed

    @Test
    fun embedYieldsTheSourceRowsAndCaptions() = runTest {
        val info = CondeNastIE(http(transfer(playerRoute))).extract(embedUrl)
        assertEquals("59138decb57ac36b83000005", info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("wired", info.uploader)
        assertEquals(120.0, info.duration)
        assertEquals("20130314", info.uploadDate)
        assertEquals("https://media.example/poster.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("hls", info.formats[0].formatId)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mp4-high", info.formats[1].formatId)
        assertEquals("1", info.formats[1].quality)
        assertEquals(1, info.subtitles.size)
        assertEquals("en", info.subtitles[0].language)
        assertEquals("https://media.example/captions.vtt", info.subtitles[0].formats.single().url)
    }

    @Test
    fun embedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = embedUrl,
            infoDict = mapOf(
                "id" to Expect.Value("59138decb57ac36b83000005"),
                "title" to Expect.Value("Fixture Video"),
                "duration" to Expect.Value(120.0),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(playerRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> CondeNastIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    // --------------------------------------------------------------- watch

    @Test
    fun watchUsesThePreloadedStateDescription() = runTest {
        val watchPage = FixtureRoute(
            urlPattern = "http://video.wired.com/watch/*",
            contentType = "text/html",
            body = """
                <html><body><script>
                __PRELOADED_STATE__ = {"transformed":{"video":{"id":"5171b343c2b4c00dd0c1ccb3",
                  "description":"Fixture description"}}};
                </script></body></html>
            """.trimIndent(),
        )
        val info = CondeNastIE(http(transfer(watchPage, playerRoute))).extract(watchUrl)
        assertEquals("5171b343c2b4c00dd0c1ccb3", info.id)
        assertEquals("Fixture description", info.description)
        assertEquals(2, info.formats.size)
    }

    @Test
    fun watchFallsBackToTheParamsJson() = runTest {
        val watchPage = FixtureRoute(
            urlPattern = "http://video.wired.com/watch/*",
            contentType = "text/html",
            body = """
                <html><body>
                <script>var params = {"videoId": "placeholder"};</script>
                <div data-video-id="5171b343c2b4c00dd0c1ccb3"></div>
                </body></html>
            """.trimIndent(),
        )
        val info = CondeNastIE(http(transfer(watchPage, playerRoute))).extract(watchUrl)
        assertEquals("5171b343c2b4c00dd0c1ccb3", info.id)
        assertEquals(2, info.formats.size)
    }

    @Test
    fun watchFallsBackToThePlayerAttributes() = runTest {
        val watchPage = FixtureRoute(
            urlPattern = "http://video.wired.com/watch/*",
            contentType = "text/html",
            body = """
                <html><body>
                <div data-js="video-player" data-video="55f9cf8b61646d1acf00000c"
                     data-player="5511d76261646d5566020000" id="embedplayer"></div>
                </body></html>
            """.trimIndent(),
        )
        val info = CondeNastIE(http(transfer(watchPage, playerRoute))).extract(watchUrl)
        assertEquals("55f9cf8b61646d1acf00000c", info.id)
        assertEquals(2, info.formats.size)
    }

    // -------------------------------------------------------------- series

    @Test
    fun seriesYieldsTheThumbTitleEntries() = runTest {
        val seriesPage = FixtureRoute(
            urlPattern = "http://video.gq.com/series/*",
            contentType = "text/html",
            body = """
                <html><body>
                <div class="cne-series-info"><h1>Fixture Series</h1></div>
                <p class="cne-thumb-title"><a href="/watch/fixture-one?c=series">One</a></p>
                <p class="cne-thumb-title"><a href="/watch/fixture-two">Two</a></p>
                <p class="cne-thumb-title"><a href="/watch/fixture-one?c=series">One again</a></p>
                </body></html>
            """.trimIndent(),
        )
        val info = CondeNastIE(http(transfer(seriesPage))).extract(seriesUrl)
        assertEquals("the-closer", info.id)
        assertEquals("Fixture Series", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("http://video.gq.com/watch/fixture-one", info.entries[0].url)
        assertEquals("http://video.gq.com/watch/fixture-two", info.entries[1].url)
    }
}
