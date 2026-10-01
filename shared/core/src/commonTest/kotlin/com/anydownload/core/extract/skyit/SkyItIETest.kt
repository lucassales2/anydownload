package com.anydownload.core.extract.skyit

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
 * Fixture cases for the Sky Italia subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no user token appears
 * (the default caller token is the public constant upstream carries).
 */
class SkyItIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "631227"

    private fun videoRoute() = FixtureRoute(
        urlPattern = "https://apid.sky.it/vdp/v1/getVideoData?caller=sky&id=$videoId&token=*",
        contentType = "application/json",
        body = """
            {"id": "$videoId", "type": "video", "title": "Fixture Sky",
             "hls_url": "https://media.example/hls/master.m3u8", "geob": false,
             "short_desc": "Fixture description", "duration_sec": 26,
             "create_date": "2020-11-22T00:00:00Z",
             "video_still": "https://media.example/still.jpg"}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            SkyItPlayerIE(http(transfer())) to "https://player.sky.it/player/external.html?id=$videoId&domain=sky",
            SkyItVideoIE(http(transfer())) to "https://video.sky.it/news/mondo/video/fixture-$videoId",
            SkyItVideoLiveIE(http(transfer())) to "https://video.sky.it/diretta/tg24",
            SkyItIE(http(transfer())) to "https://sport.sky.it/calcio/serie-a/2022/11/03/fixture-news",
            SkyItArteIE(http(transfer())) to "https://arte.sky.it/video/fixture-$videoId",
            CieloTVItIE(http(transfer())) to "https://www.cielotv.it/video/Fixture.html",
            TV8ItIE(http(transfer())) to "https://www.tv8.it/video/fixture-$videoId",
            TV8ItLiveIE(http(transfer())) to "https://tv8.it/streaming",
            TV8ItPlaylistIE(http(transfer())) to "https://tv8.it/intrattenimento/fixture-playlist",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(TV8ItPlaylistIE(http(transfer())).suitable("https://www.tv8.it/video/fixture-$videoId"))
    }

    // ----------------------------------------------------------------- player

    @Test
    fun playerApiYieldsHlsAndMetadata() = runTest {
        val url = "https://player.sky.it/player/external.html?id=$videoId&domain=sky"
        val transfer = transfer(videoRoute())
        val info = SkyItPlayerIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Sky", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(26.0, info.duration)
        assertEquals("20201122", info.uploadDate)
        assertEquals(false, info.isLive)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/still.jpg", info.thumbnails.single().url)
    }

    @Test
    fun geoblockedVideoFailsTyped() = runTest {
        val url = "https://player.sky.it/player/external.html?id=$videoId&domain=sky"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://apid.sky.it/vdp/v1/getVideoData?caller=sky&id=$videoId&token=*",
                contentType = "application/json",
                body = """{"id": "$videoId", "title": "Fixture", "geob": true}""",
            ),
        )
        assertFailsWithGeoblock { SkyItPlayerIE(http(transfer)).extract(url) }
    }

    // ---------------------------------------------------------------- article

    @Test
    fun articleScansTheVideoId() = runTest {
        val url = "https://sport.sky.it/calcio/serie-a/2022/11/03/fixture-news"
        val page = """<html><body><div data-videoid="$videoId"></div></body></html>"""
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = SkyItIE(http(transfer)).extract(url)
        assertEquals(
            "https://player.sky.it/player/external.html?id=$videoId&domain=sky",
            info.redirectUrl,
        )
    }

    // ------------------------------------------------------------------ live

    @Test
    fun tv8LiveYieldsHls() = runTest {
        val url = "https://tv8.it/streaming"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://apid.sky.it/vdp/v1/getLivestream?id=7",
                contentType = "application/json",
                body = """
                    {"id": "tv8", "type": "live", "title": "TV8 Live",
                     "streaming_url": "https://media.example/hls/tv8.m3u8", "geoblock": false}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://tv8.it/api/getStreaming",
                contentType = "application/json",
                body = """{"info": {"title": {"text": "TV8 Fixture"}, "description": {"html": "<p>Fixture</p>"}}}""",
            ),
        )
        val info = TV8ItLiveIE(http(transfer)).extract(url)
        assertEquals("tv8", info.id)
        assertEquals("TV8 Fixture", info.title)
        assertEquals(true, info.isLive)
        assertEquals(1, info.formats.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun playerIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://player.sky.it/player/external.html?id=$videoId&domain=sky"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Sky"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(videoRoute()),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SkyItPlayerIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}

private fun assertFailsWithGeoblock(block: suspend () -> Unit) {
    kotlinx.coroutines.test.runTest {
        kotlin.test.assertFailsWith<com.anydownload.core.extract.ExtractionError.GeoRestricted> {
            block()
        }
    }
}
