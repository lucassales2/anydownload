package com.anydownload.core.extract.vidio

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
 * Fixture cases for the Vidio subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and the API key is a fake
 * value served by the fixture (the port fetches it at runtime and never
 * stores it).
 */
class VidioIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "165683"

    private fun authRoute() = FixtureRoute(
        urlPattern = "https://www.vidio.com/auth",
        method = "POST",
        contentType = "application/json",
        body = """{"api_key": "fake_value"}""",
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            VidioIE(http(transfer())) to "http://www.vidio.com/watch/$videoId-dj_ambred-booyah-live-2015",
            VidioIE(http(transfer())) to "https://www.vidio.com/embed/7115874-fakta-temuan-suspek-cacar-monyet",
            VidioPremierIE(http(transfer())) to "https://www.vidio.com/premier/2885/badai-pasti-berlalu",
            VidioLiveIE(http(transfer())) to "https://www.vidio.com/live/204-sctv",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(VidioIE(http(transfer())).suitable("https://www.vidio.com/premier/2885/badai-pasti-berlalu"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun watchApiYieldsHlsAndMetadata() = runTest {
        val url = "http://www.vidio.com/watch/$videoId-dj_ambred-booyah-live-2015"
        val transfer = transfer(
            authRoute(),
            FixtureRoute(
                urlPattern = "https://api.vidio.com/videos/$videoId",
                contentType = "application/json",
                body = """
                    {"videos": [{"title": "Fixture Vidio", "description": "Fixture description",
                      "duration": 149, "created_at": "2015-10-15T00:00:00Z",
                      "image_url_medium": "https://media.example/thumb.jpg",
                      "total_view_count": 10, "is_premium": false}],
                     "clips": [{"hls_url": "https://media.example/hls/master.m3u8"}],
                     "channels": [{"id": 280236, "name": "Fixture Channel"}],
                     "users": [{"name": "Fixture User", "username": "fixtureuser"}]}
                """.trimIndent(),
            ),
        )
        val info = VidioIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Vidio", info.title)
        assertEquals(149.0, info.duration)
        assertEquals("20151015", info.uploadDate)
        assertEquals("Fixture Channel", info.channel)
        assertEquals("280236", info.channelId)
        assertEquals("Fixture User", info.uploader)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun premiumVideoWithoutSourceFailsTyped() = runTest {
        val url = "http://www.vidio.com/watch/1550718-stand-by-me-doraemon"
        val transfer = transfer(
            authRoute(),
            FixtureRoute(
                urlPattern = "https://api.vidio.com/videos/1550718",
                contentType = "application/json",
                body = """{"videos": [{"title": "Fixture Premium", "is_premium": true}], "clips": []}""",
            ),
            FixtureRoute(
                urlPattern = "https://www.vidio.com/interactions_stream.json?video_id=1550718&type=videos",
                contentType = "application/json",
                body = """{}""",
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            VidioIE(http(transfer)).extract(url)
        }
    }

    // --------------------------------------------------------------- premier

    @Test
    fun premierListingYieldsWatchEntries() = runTest {
        val url = "https://www.vidio.com/premier/2885/badai-pasti-berlalu"
        val transfer = transfer(
            authRoute(),
            FixtureRoute(
                urlPattern = "https://api.vidio.com/content_profiles/2885/playlists",
                contentType = "application/json",
                body = """
                    {"data": [{"id": "playlist-1", "attributes": {"name": "Fixture Playlist"},
                      "links": {"watchpage": "https://www.vidio.com/watch/1550718-fixture"}}]}
                """.trimIndent(),
            ),
        )
        val info = VidioPremierIE(http(transfer)).extract(url)
        assertEquals("2885", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.vidio.com/watch/1550718-fixture", info.entries.single().url)
    }

    // -------------------------------------------------------------------- live

    @Test
    fun liveApiYieldsHlsAndMetadata() = runTest {
        val url = "https://www.vidio.com/live/204-sctv"
        val transfer = transfer(
            authRoute(),
            FixtureRoute(
                urlPattern = "https://www.vidio.com/api/livestreamings/204/detail",
                contentType = "application/json",
                body = """
                    {"livestreamings": [{"title": "Fixture Live", "image": "https://media.example/live.jpg",
                      "is_drm": false, "is_premium": false,
                      "stream_url": "https://media.example/hls/live.m3u8"}],
                     "users": [{"name": "Fixture Broadcaster"}]}
                """.trimIndent(),
            ),
        )
        val info = VidioLiveIE(http(transfer)).extract(url)
        assertEquals("204", info.id)
        assertEquals("Fixture Live", info.title)
        assertEquals(true, info.isLive)
        assertEquals("Fixture Broadcaster", info.uploader)
        assertEquals("https://media.example/hls/live.m3u8", info.formats.single().url)
    }

    @Test
    fun drmLivestreamFailsTyped() = runTest {
        val url = "https://www.vidio.com/live/6299-bein-1"
        val transfer = transfer(
            authRoute(),
            FixtureRoute(
                urlPattern = "https://www.vidio.com/api/livestreamings/6299/detail",
                contentType = "application/json",
                body = """{"livestreamings": [{"title": "Fixture DRM", "is_drm": true}], "users": []}""",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            VidioLiveIE(http(transfer)).extract(url)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun watchIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "http://www.vidio.com/watch/$videoId-dj_ambred-booyah-live-2015"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Vidio"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                authRoute(),
                FixtureRoute(
                    urlPattern = "https://api.vidio.com/videos/$videoId",
                    contentType = "application/json",
                    body = """
                        {"videos": [{"title": "Fixture Vidio", "is_premium": false}],
                         "clips": [{"hls_url": "https://media.example/hls/master.m3u8"}]}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> VidioIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
