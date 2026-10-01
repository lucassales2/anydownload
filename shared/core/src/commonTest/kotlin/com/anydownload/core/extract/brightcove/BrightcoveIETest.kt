package com.anydownload.core.extract.brightcove

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
 * Fixture cases for the Brightcove subset. Every account id, host, and media
 * address is synthesized (`*.example`); `fake_value` stands in for the policy
 * key, and no cookie, bearer token, or signed URL appears.
 */
class BrightcoveIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val newUrl = "https://players.brightcove.net/929656772001/" +
        "e41d32dc-ec74-459e-a845-6c69f7b724ea_default/index.html?videoId=4463358922001"
    private val apiUrl = "https://edge.api.brightcove.com/playback/v1/accounts/" +
        "929656772001/videos/4463358922001"

    private val configRoute = FixtureRoute(
        urlPattern = "https://players.brightcove.net/929656772001/" +
            "e41d32dc-ec74-459e-a845-6c69f7b724ea_default/config.json",
        contentType = "application/json",
        body = """{"video_cloud": {"policy_key": "fake_value"}}""",
    )

    private fun videoJson(
        extraSources: String = "",
        errors: String = "",
    ): String = """
        {
          "id": "4463358922001",
          "name": "Fixture Brightcove",
          "description": "Fixture description",
          "account_id": "929656772001",
          "published_at": "2015-05-25T15:31:23Z",
          "duration": 60000,
          "poster": "https://img.example/poster-1280x720.jpg",
          "sources": [
            {"type": "application/x-mpegURL", "src": "https://media.example/hls/master.m3u8"},
            {"type": "video/mp4", "src": "https://media.example/video-720.mp4", "container": "MP4",
             "avg_bitrate": 2500000, "height": 720, "width": 1280, "codec": "H264", "size": 123456},
            {"type": "application/dash+xml", "src": "https://media.example/dash/manifest.mpd"}
            $extraSources
          ],
          "text_tracks": [
            {"kind": "captions", "src": "https://media.example/captions.vtt", "srclang": "EN",
             "label": "English"}
          ],
          "errors": [$errors]
        }
    """.trimIndent()

    private fun videoRoutes(body: String): List<FixtureRoute> = listOf(
        configRoute,
        FixtureRoute(urlPattern = apiUrl, contentType = "application/json", body = body),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            BrightcoveNewIE(http(transfer())) to newUrl,
            BrightcoveNewIE(http(transfer())) to
                "https://players.brightcove.net/123/abc_default/index.html?playlistId=456",
            BrightcoveLegacyIE(http(transfer())) to
                "https://c.brightcove.com/services/viewer/htmlFederated?videoId=1&playerID=2",
            BrightcoveLegacyIE(http(transfer())) to "brightcove:?@videoPlayer=1&playerKey=AQ~~,x~,y",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(BrightcoveNewIE(http(transfer())).suitable("https://players.brightcove.net/1/2_3/x.html"))
        assertFalse(BrightcoveLegacyIE(http(transfer())).suitable("https://example.com/watch/1"))
    }

    // --------------------------------------------------------- BrightcoveNewIE

    @Test
    fun playbackApiMapsSourcesSubtitlesAndThumbnails() = runTest {
        val transfer = transfer(*videoRoutes(videoJson()).toTypedArray())
        val info = BrightcoveNewIE(http(transfer)).extract(newUrl)

        assertEquals("4463358922001", info.id)
        assertEquals("Fixture Brightcove", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(60.0, info.duration)
        assertEquals("20150525", info.uploadDate)
        assertEquals("929656772001", info.channelId)
        assertEquals(3, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("http-2500k-720p", info.formats[1].formatId)
        assertEquals(2500.0, info.formats[1].tbr)
        assertEquals(720L, info.formats[1].height)
        assertEquals("http_dash_segments", info.formats[2].protocol)
        assertEquals(9, info.thumbnails.size)
        assertEquals("https://img.example/poster-1280x720.jpg", info.thumbnails[6].url)
        assertEquals("en", info.subtitles.single().language)
        assertTrue(transfer.requests.any { it.url == apiUrl })
    }

    @Test
    fun policyKeyFallsBackToThePlayerJavaScript() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://players.brightcove.net/929656772001/" +
                    "e41d32dc-ec74-459e-a845-6c69f7b724ea_default/config.json",
                statusCode = 404,
                contentType = "application/json",
                body = """{"error_code": "NOT_FOUND"}""",
            ),
            FixtureRoute(
                urlPattern = "https://players.brightcove.net/929656772001/" +
                    "e41d32dc-ec74-459e-a845-6c69f7b724ea_default/index.min.js",
                contentType = "application/javascript",
                body = "var player = { policyKey: 'fake_value' };",
            ),
            FixtureRoute(urlPattern = apiUrl, contentType = "application/json", body = videoJson()),
        )
        val info = BrightcoveNewIE(http(transfer)).extract(newUrl)
        assertEquals("4463358922001", info.id)
        assertEquals(3, info.formats.size)
    }

    @Test
    fun drmSourcesAreFlagged() = runTest {
        val extra = """,{"type": "video/mp4", "src": "https://media.example/drm.wvm", "container": "WVM"}"""
        val transfer = transfer(*videoRoutes(videoJson(extraSources = extra)).toTypedArray())
        val info = BrightcoveNewIE(http(transfer)).extract(newUrl)
        assertEquals(true, info.formats[3].hasDrm)
        assertEquals(null, info.formats[1].hasDrm)
    }

    @Test
    fun playlistMapsChildVideoUrls() = runTest {
        val playlistUrl = "https://players.brightcove.net/929656772001/" +
            "e41d32dc-ec74-459e-a845-6c69f7b724ea_default/index.html?playlistId=987"
        val transfer = transfer(
            configRoute,
            FixtureRoute(
                urlPattern = "https://edge.api.brightcove.com/playback/v1/accounts/" +
                    "929656772001/playlists/987",
                contentType = "application/json",
                body = """
                    {"id": "987", "name": "Fixture Playlist", "description": "Fixture",
                     "videos": [{"id": "1", "name": "One"}, {"id": "2", "name": "Two"}]}
                """.trimIndent(),
            ),
        )
        val info = BrightcoveNewIE(http(transfer)).extract(playlistUrl)
        assertEquals("987", info.id)
        assertEquals(2, info.entries.size)
        assertEquals(
            "https://players.brightcove.net/929656772001/" +
                "e41d32dc-ec74-459e-a845-6c69f7b724ea_default/index.html?videoId=1",
            info.entries[0].url,
        )
    }

    @Test
    fun tveErrorsFailAsALoginWallAndNoSourcesFailsTyped() = runTest {
        val tve = transfer(
            *videoRoutes(videoJson(errors = """{"error_subcode": "TVE_AUTH"}""")).toTypedArray(),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            BrightcoveNewIE(http(tve)).extract(newUrl)
        }

        val noSources = transfer(
            *videoRoutes(
                """{"id": "4463358922001", "sources": [], "errors": [{"error_code": "ACCESS_DENIED"}]}""",
            ).toTypedArray(),
        )
        val error = assertFailsWith<ExtractionError.NoFormats> {
            BrightcoveNewIE(http(noSources)).extract(newUrl)
        }
        assertTrue(error.message!!.contains("ACCESS_DENIED"))
    }

    // ------------------------------------------------------ BrightcoveLegacyIE

    @Test
    fun legacyPublisherIdResolvesToTheNewPlayerUrl() = runTest {
        val url = "https://c.brightcove.com/services/viewer/htmlFederated" +
            "?playerID=123&publisherId=929656772001&videoId=4463358922001"
        val info = BrightcoveLegacyIE(http(transfer())).extract(url)
        assertEquals("4463358922001", info.id)
        assertEquals(
            "https://players.brightcove.net/929656772001/default_default/index.html?videoId=4463358922001",
            info.redirectUrl,
        )
    }

    @Test
    fun legacyPlayerKeyIsDecodedToThePublisherId() = runTest {
        val url = "brightcove:?@videoPlayer=4463358922001&playerKey=AQ~~,AAAA2HPclaE~,xyz"
        val info = BrightcoveLegacyIE(http(transfer())).extract(url)
        assertEquals("4463358922001", info.id)
        assertEquals(
            "https://players.brightcove.net/929656772001/default_default/index.html?videoId=4463358922001",
            info.redirectUrl,
        )
    }

    @Test
    fun legacyWithoutAPublisherFailsTyped() = runTest {
        val url = "brightcove:?@videoPlayer=4463358922001"
        assertFailsWith<ExtractionError.UnsupportedUrl> {
            BrightcoveLegacyIE(http(transfer())).extract(url)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun brightcoveNewIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = newUrl,
            infoDict = mapOf(
                "id" to Expect.Value("4463358922001"),
                "title" to Expect.Value("Fixture Brightcove"),
                "duration" to Expect.Value(60.0),
                "formats" to Expect.Count(3),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
                "thumbnails" to Expect.Count(9),
            ),
            routes = videoRoutes(videoJson()),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> BrightcoveNewIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
