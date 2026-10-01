package com.anydownload.core.extract.itv

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
 * Fixture cases for the ITV subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class ItvIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val episodeUrl = "https://www.itv.com/hub/plebs/2a1873a0002"
    private val btccUrl = "https://www.itv.com/btcc/articles/btcc-2019-brands-hatch-gp-race-action"

    private val episodePage = """
        <html><head>
        <meta property="og:title" content="Fixture ITV Title">
        <meta property="og:image" content="https://media.example/og.jpg">
        </head><body>
        <div id="video" data-video-id="https://media.example/api/playlist" data-video-hmac="abc123"
         data-video-posterframe="https://media.example/poster/{width}x{height}q{quality}b{blur}.jpg"
         data-video-variants='{"platformTag": [["aes","hls","outband-webvtt"], ["aes","hls"]]}'></div>
        <div class="episode-info__synopsis">Fixture synopsis</div>
        </body></html>
    """.trimIndent()

    private val playlistRoute = FixtureRoute(
        urlPattern = "https://media.example/api/playlist",
        method = "POST",
        contentType = "application/json",
        body = """
            {"Playlist": {"Video": {
              "Base": "https://media.example/base/", "Duration": "00:10:30",
              "MediaFiles": [{"Href": "master.m3u8"}, {"Href": "video.mp4"}],
              "Subtitles": [{"Href": "https://media.example/subs.vtt"}]
            }}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val itv = ITVIE(http(transfer()))
        assertTrue(itv.suitable(episodeUrl))
        assertTrue(itv.suitable("https://www.itv.com/hub/the-jonathan-ross-show/2a1166a0209"))
        assertFalse(itv.suitable(btccUrl))

        val btcc = ITVBTCCIE(http(transfer()))
        assertTrue(btcc.suitable(btccUrl))
        assertTrue(btcc.suitable("https://www.itv.com/news/2021-10-27/fixture"))
        assertFalse(btcc.suitable(episodeUrl))
    }

    // ---------------------------------------------------------------- episode

    @Test
    fun episodeYieldsTheHlsAndDirectRows() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$episodeUrl*", contentType = "text/html", body = episodePage),
            playlistRoute,
        )
        val info = ITVIE(http(transfer)).extract(episodeUrl)
        assertEquals("2a1873a0002", info.id)
        assertEquals("Fixture ITV Title", info.title)
        assertEquals("Fixture synopsis", info.description)
        assertEquals(630.0, info.duration)
        assertEquals(2, info.formats.size)
        assertEquals("https://media.example/base/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("https://media.example/base/video.mp4", info.formats[1].url)
        assertEquals(1, info.subtitles.size)
        assertEquals("en", info.subtitles[0].language)
        assertEquals("https://media.example/subs.vtt", info.subtitles[0].formats.single().url)
        assertEquals("https://media.example/poster/1920x1080q100b0.jpg", info.thumbnails[0].url)
        assertEquals("https://media.example/og.jpg", info.thumbnails.last().url)
    }

    @Test
    fun noDownloadFeaturesetFailsTyped() = runTest {
        val page = """
            <html><head><meta property="og:title" content="Fixture"></head>
            <body><div id="video" data-video-id="https://media.example/api/playlist" data-video-hmac="abc"
             data-video-variants='{"platformTag": [["aes","dash"]]}'></div></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = "$episodeUrl*", contentType = "text/html", body = page))
        assertFailsWith<ExtractionError.Unavailable> {
            ITVIE(http(transfer)).extract(episodeUrl)
        }
    }

    // ------------------------------------------------------------------- btcc

    @Test
    fun btccArticleYieldsTheBrightcoveEntries() = runTest {
        val page = """
            <html><head><meta property="og:title" content="Fixture BTCC"></head>
            <body><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"article":
              {"body":{"content":[{"data":{"name":"Brightcove","id":123,"accountId":"999","playerId":"P1"}},
              {"data":{"name":"Other"}}]}}}}}</script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = "$btccUrl*", contentType = "text/html", body = page))
        val info = ITVBTCCIE(http(transfer)).extract(btccUrl)
        assertEquals("btcc-2019-brands-hatch-gp-race-action", info.id)
        assertEquals("Fixture BTCC", info.title)
        assertEquals(1, info.entries.size)
        assertEquals(
            "http://players.brightcove.net/999/P1_default/index.html?videoId=123",
            info.entries[0].url,
        )
    }

    // --------------------------------------------------------------- harness

    @Test
    fun episodeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = episodeUrl,
            infoDict = mapOf(
                "id" to Expect.Value("2a1873a0002"),
                "title" to Expect.Value("Fixture ITV Title"),
                "duration" to Expect.Value(630.0),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "$episodeUrl*", contentType = "text/html", body = episodePage),
                playlistRoute,
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ITVIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun btccIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val page = """
            <html><head><meta property="og:title" content="Fixture BTCC"></head>
            <body><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"article":
              {"body":{"content":[{"data":{"name":"Brightcove","id":123,"accountId":"999","playerId":"P1"}}]}}}}}</script>
            </body></html>
        """.trimIndent()
        val case = ExtractorCase(
            url = btccUrl,
            infoDict = mapOf(
                "id" to Expect.Value("btcc-2019-brands-hatch-gp-race-action"),
                "title" to Expect.Value("Fixture BTCC"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(FixtureRoute(urlPattern = "$btccUrl*", contentType = "text/html", body = page)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ITVBTCCIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
