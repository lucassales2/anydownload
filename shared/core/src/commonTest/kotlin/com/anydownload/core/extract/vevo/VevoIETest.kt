package com.anydownload.core.extract.vevo

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
 * Fixture cases for the Vevo subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and the legacy token is a
 * fake value served by the fixture (the port fetches it at runtime and
 * never stores it).
 */
class VevoIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "GB1101300280"

    private fun tokenRoute() = FixtureRoute(
        urlPattern = "https://accounts.vevo.com/token",
        method = "POST",
        contentType = "application/json",
        body = """{"legacy_token": "fake_value"}""",
    )

    private fun infoRoute() = FixtureRoute(
        urlPattern = "https://apiv2.vevo.com/video/$videoId?token=*",
        contentType = "application/json",
        body = """
            {"title": "Somebody to Die For", "duration": 229,
             "releaseDate": "2013-06-24T00:00:00Z", "imageUrl": "https://media.example/thumb.jpg",
             "views": {"total": 42}, "isExplicit": false,
             "artists": [{"name": "Hurts", "role": "Main"},
                         {"name": "Fixture Guest", "role": "Featured"}]}
        """.trimIndent(),
    )

    private fun streamsRoute() = FixtureRoute(
        urlPattern = "https://apiv2.vevo.com/video/$videoId/streams?token=*",
        contentType = "application/json",
        body = """
            [{"version": 2, "url": "https://media.example/hls/master.m3u8"},
             {"version": 3, "url": "https://media.example/video_high_1280x720_avc1_2000_aac_128.mp4"}]
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            VevoIE(http(transfer())) to "http://www.vevo.com/watch/hurts/somebody-to-die-for/$videoId",
            VevoIE(http(transfer())) to "https://videoplayer.vevo.com/embed/embedded?videoId=$videoId",
            VevoIE(http(transfer())) to "vevo:$videoId",
            VevoPlaylistIE(http(transfer())) to "http://www.vevo.com/watch/genre/rock",
            VevoPlaylistIE(http(transfer())) to "http://www.vevo.com/watch/playlist/fixture-playlist",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(VevoIE(http(transfer())).suitable("http://www.vevo.com/watch/genre/rock"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun videoApiYieldsFormatsAndMetadata() = runTest {
        val url = "http://www.vevo.com/watch/hurts/somebody-to-die-for/$videoId"
        val transfer = transfer(tokenRoute(), infoRoute(), streamsRoute())
        val info = VevoIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Hurts ft. Fixture Guest - Somebody to Die For", info.title)
        assertEquals(229.0, info.duration)
        assertEquals("20130624", info.uploadDate)
        assertEquals(42L, info.viewCount)
        assertEquals(0, info.ageLimit)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals(720L, info.formats[1].height)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    // --------------------------------------------------------------- playlist

    @Test
    fun playlistPageYieldsIsrcEntries() = runTest {
        val url = "http://www.vevo.com/watch/genre/rock"
        val page = """
            <html><body><script>window.__INITIAL_STORE__ = {"default": {"genres": {
              "rock": {"playlistId": "rock", "name": "Rock", "description": "Fixture genre",
                       "isrcs": ["$videoId", "USUV71302923"]}}}};</script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = VevoPlaylistIE(http(transfer)).extract(url)
        assertEquals("rock", info.id)
        assertEquals("Rock", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("vevo:$videoId", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "http://www.vevo.com/watch/hurts/somebody-to-die-for/$videoId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Hurts ft. Fixture Guest - Somebody to Die For"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(tokenRoute(), infoRoute(), streamsRoute()),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> VevoIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
