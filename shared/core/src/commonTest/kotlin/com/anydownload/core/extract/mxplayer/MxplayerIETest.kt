package com.anydownload.core.extract.mxplayer

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
 * Fixture cases for the MX Player subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class MxplayerIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "9d2013d31d5835bb8400e3b3c5e7bb72"

    private fun mxsPage(entity: String, extra: String = "") = """
        <html><body><script>
        window.__mxs__ = {"config": {"videoCdnBaseUrl": "https://cdn.example/video",
                                     "imageBaseUrl": "https://img.example"},
                          "entities": {"$videoId": $entity}$extra};
        </script></body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            MxplayerIE(http(transfer())) to "https://www.mxplayer.in/movie/fixture-movie-$videoId",
            MxplayerIE(http(transfer())) to
                "https://www.mxplayer.in/show/watch-fixture/season-1/episode-1-$videoId",
            MxplayerSeasonIE(http(transfer())) to
                "https://www.mxplayer.in/show/watch-fixture/seasons/season-1-$videoId",
            MxplayerShowIE(http(transfer())) to "https://www.mxplayer.in/show/watch-fixture-$videoId",
            MxplayerRedirectIE(http(transfer())) to "https://www.mxplayer.in/detail/episode/$videoId",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(MxplayerShowIE(http(transfer())).suitable("https://www.mxplayer.in/movie/x-$videoId"))
    }

    // ----------------------------------------------------------------- video

    @Test
    fun pageMxsYieldsThirdPartyAndMxplayFormats() = runTest {
        val url = "https://www.mxplayer.in/movie/fixture-movie-$videoId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = mxsPage(
                    """
                    {"title": "Fixture Movie", "description": "<p>Fixture description</p>",
                     "duration": 7894, "rating": 13, "viewCount": 100,
                     "publishTime": "2024-11-24T00:00:00Z",
                     "imageInfo": {"hd": {"url": "thumb.jpg", "width": 1280, "height": 720}},
                     "stream": {"thirdParty": ["https://media.example/third/master.m3u8"],
                                "mxplay": {"hls": {"high": "mxplay/master.m3u8"},
                                           "dash": {"high": "mxplay/master.mpd"}}}}
                    """.trimIndent(),
                ),
            ),
        )
        val info = MxplayerIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Movie", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(13, info.ageLimit)
        assertEquals("20241124", info.uploadDate)
        assertEquals(3, info.formats.size)
        assertEquals("https://cdn.example/video/mxplay/master.m3u8", info.formats.first { it.url?.contains("cdn.example") == true }.url)
        assertEquals("https://img.example/thumb.jpg", info.thumbnails.single().url)
    }

    @Test
    fun drmStreamFailsTyped() = runTest {
        val url = "https://www.mxplayer.in/movie/fixture-movie-$videoId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = mxsPage("""{"title": "DRM", "stream": {"drmProtect": true}}"""),
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            MxplayerIE(http(transfer)).extract(url)
        }
    }

    // -------------------------------------------------------------- seasons

    @Test
    fun seasonApiYieldsEntries() = runTest {
        val url = "https://www.mxplayer.in/show/watch-fixture/seasons/season-1-$videoId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = mxsPage(
                    """
                    {"title": "Season 1", "container": {"title": "Fixture Show"},
                     "tabs": [{"api": "season/episodes"}]}
                    """.trimIndent(),
                ),
            ),
            FixtureRoute(
                urlPattern = "https://api.mxplayer.in/v1/web/season/episodes",
                contentType = "application/json",
                body = """
                    {"items": [{"shareUrl": "/detail/episode/3eda0b3baf27f2892d3fca2fd650fb95"}]}
                """.trimIndent(),
            ),
        )
        val info = MxplayerSeasonIE(http(transfer)).extract(url)
        assertEquals("Fixture Show - Season 1", info.title)
        assertEquals(1, info.entries.size)
        assertEquals(
            "https://www.mxplayer.in/detail/episode/3eda0b3baf27f2892d3fca2fd650fb95",
            info.entries.single().url,
        )
    }

    // -------------------------------------------------------------- redirect

    @Test
    fun redirectResolverReturnsTheMappedUrl() = runTest {
        val url = "https://www.mxplayer.in/detail/episode/$videoId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://seo.mxplayer.in/v1/api/seo/get-url-details?url=/detail/episode/$videoId",
                contentType = "application/json",
                body = """{"data": {"redirect": "/movie/fixture-movie-$videoId"}}""",
            ),
        )
        val info = MxplayerRedirectIE(http(transfer)).extract(url)
        assertEquals("https://www.mxplayer.in/movie/fixture-movie-$videoId", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun movieIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.mxplayer.in/movie/fixture-movie-$videoId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Movie"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = mxsPage(
                        """{"title": "Fixture Movie",
                            "stream": {"thirdParty": ["https://media.example/third/master.m3u8"]}}""",
                    ),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MxplayerIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
