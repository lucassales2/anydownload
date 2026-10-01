package com.anydownload.core.extract.tvp

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
 * Fixture cases for the TVP subset. Every id, host, and media address is
 * synthesized (`*.example`); no cookie, token, or signed URL appears.
 */
class TvpIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            TVPIE(http(transfer())) to "https://www.tvp.info/52880236/09042021-0800",
            TVPIE(http(transfer())) to "https://swipeto.pl/64095316/fixture",
            TVPIE(http(transfer())) to "https://tvpworld.com/48583640/fixture",
            TVPStreamIE(http(transfer())) to "https://stream.tvp.pl/?channel_id=56969941",
            TVPStreamIE(http(transfer())) to "tvpstream:39821455",
            TVPEmbedIE(http(transfer())) to "tvp:194536",
            TVPEmbedIE(http(transfer())) to "https://tvp.info/sess/TVPlayer2/embed.php?ID=50595757",
            TVPVODVideoIE(http(transfer())) to
                "https://vod.tvp.pl/filmy-dokumentalne,163/ukrainski-sluga-narodu,339667",
            TVPVODSeriesIE(http(transfer())) to "https://vod.tvp.pl/seriale,18/ranczo-odcinki,316445",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(TVPEmbedIE(http(transfer())).suitable("https://www.tvp.info/52880236/x"))
        assertFalse(TVPStreamIE(http(transfer())).suitable("https://vod.tvp.pl/seriale,18/x-odcinki,1"))
    }

    // ------------------------------------------------------------- TVPEmbedIE

    private fun configBody(content: String): String =
        "tvp_embed_callback($content);"

    private val embedRoute = FixtureRoute(
        urlPattern = "https://www.tvp.pl/sess/TVPlayer2/api.php?id=194536*",
        contentType = "application/javascript",
        body = configBody(
            """
                {"content": {
                  "info": {"title": "Fixture TVP Title", "subtitle": "Fixture subtitle",
                           "description": "Fixture description", "isLive": false,
                           "isGeoBlocked": false, "duration": 2652, "ageGroup": {"minAge": 12}},
                  "files": [
                    {"url": "https://media.example/tvp/master.m3u8", "type": "hls",
                     "quality": {"fps": 25, "bitrate": 1500000, "width": 1280, "height": 720}},
                    {"url": "https://media.example/tvp/video.mp4", "type": "mp4",
                     "quality": {"fps": 25, "bitrate": 1500000, "width": 1280, "height": 720}}
                  ],
                  "posters": [{"src": "https://img.example/poster.jpg", "width": 1280, "height": 720}],
                  "subtitles": [{"url": "https://media.example/tvp/sub.vtt", "lang": "pl", "type": "vtt"}]
                }}
            """.trimIndent(),
        ),
    )

    @Test
    fun embedMapsTheJsonpConfig() = runTest {
        val transfer = transfer(embedRoute)
        val info = TVPEmbedIE(http(transfer)).extract("tvp:194536")

        assertEquals("194536", info.id)
        assertEquals("Fixture subtitle", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(2652.0, info.duration)
        assertEquals(12, info.ageLimit)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("direct", info.formats[1].formatId)
        assertEquals(720L, info.formats[1].height)
        assertEquals(1500.0, info.formats[1].tbr)
        assertEquals("https://img.example/poster.jpg", info.thumbnails.single().url)
        assertEquals("pl", info.subtitles.single().language)
    }

    @Test
    fun embedPaymentErrorFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.tvp.pl/sess/TVPlayer2/api.php?id=194536*",
                contentType = "application/javascript",
                body = configBody("""null,[{"desc": "Obiekt wymaga płatności"}]"""),
            ),
        )
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            TVPEmbedIE(http(transfer)).extract("tvp:194536")
        }
        assertTrue(error.message!!.contains("payment"))
    }

    // ----------------------------------------------------------- TVPStreamIE

    @Test
    fun streamReadsTheChannelList() = runTest {
        val url = "https://stream.tvp.pl/?channel_id=56969941"
        val page = """
            <html><script>window.__channels = [{"id": 56969941, "title": "TVP Info",
              "items": [{"is_live": true, "video_id": 123456, "title": "Fixture Live"}]}];</script></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = TVPStreamIE(http(transfer)).extract(url)
        assertEquals("56969941", info.id)
        assertEquals("Fixture Live", info.title)
        assertEquals("tvp:123456", info.redirectUrl)
        assertEquals(true, info.isLive)
    }

    // ------------------------------------------------------------------ TVPIE

    @Test
    fun classicPageHandsOffThroughTheIframeId() = runTest {
        val url = "https://www.tvp.pl/polska-press-video-uploader/wideo/62042351"
        val page = """
            <html><body>
            <iframe src="https://www.tvp.pl/sess/tvplayer.php?object_id=51247504&amp;autoplay=false"></iframe>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = TVPIE(http(transfer)).extract(url)
        assertEquals("51247504", info.id)
        assertEquals("tvp:51247504", info.redirectUrl)
    }

    @Test
    fun vueVideoDataHandsOffThroughTvpEmbed() = runTest {
        val url = "https://www.tvp.info/52880236/09042021-0800"
        val page = """
            <html><script>window.__videoData = {"_id": 52880236, "title": "Fixture News"};</script></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = TVPIE(http(transfer)).extract(url)
        assertEquals("52880236", info.id)
        assertEquals("Fixture News", info.title)
        assertEquals("tvp:52880236", info.redirectUrl)
    }

    // ---------------------------------------------------------- TVPVODVideoIE

    @Test
    fun vodVideoMapsThePlaylistFormats() = runTest {
        val videoId = "339667"
        val url = "https://vod.tvp.pl/filmy-dokumentalne,163/ukrainski-sluga-narodu,$videoId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://vod.tvp.pl/api/products/vods/$videoId?lang=pl&platform=BROWSER",
                contentType = "application/json",
                body = """
                    {"id": 339667, "title": "Fixture VOD Title", "lead": "Fixture description",
                     "rating": 12, "duration": 3051,
                     "images": [[{"url": "https://img.example/vod.jpg"}]]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://vod.tvp.pl/api/products/$videoId/videos/playlist" +
                    "?lang=pl&platform=BROWSER&videoType=MOVIE",
                contentType = "application/json",
                body = """
                    {"sources": {"HLS": [{"src": "https://media.example/vod/master.m3u8"}],
                                 "DASH": [{"src": "https://media.example/vod/manifest.mpd"}]},
                     "subtitles": [{"url": "https://media.example/vod/sub.ttml", "language": "pl"}]}
                """.trimIndent(),
            ),
        )
        val info = TVPVODVideoIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture VOD Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(3051.0, info.duration)
        assertEquals(12, info.ageLimit)
        assertEquals("https://img.example/vod.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("http_dash_segments", info.formats[1].protocol)
        assertEquals("ttml", info.subtitles.single().formats.single().ext)
    }

    // --------------------------------------------------------- TVPVODSeriesIE

    @Test
    fun vodSeriesListsTheSeasonEpisodes() = runTest {
        val playlistId = "316445"
        val url = "https://vod.tvp.pl/seriale,18/ranczo-odcinki,$playlistId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://vod.tvp.pl/api/products/vods/serials/$playlistId" +
                    "?lang=pl&platform=BROWSER",
                contentType = "application/json",
                body = """
                    {"title": "Fixture Series", "rating": 12, "description": {"lead": "Fixture lead"}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://vod.tvp.pl/api/products/vods/serials/$playlistId/seasons" +
                    "?lang=pl&platform=BROWSER",
                contentType = "application/json",
                body = """{"items": [{"id": 1, "title": "Season 1"}]}""",
            ),
            FixtureRoute(
                urlPattern = "https://vod.tvp.pl/api/products/vods/serials/$playlistId/seasons/1/episodes" +
                    "?lang=pl&platform=BROWSER",
                contentType = "application/json",
                body = """
                    {"items": [{"id": 311357, "title": "Episode 1",
                                "webUrl": "https://vod.tvp.pl/x,1/y,311357"}]}
                """.trimIndent(),
            ),
        )
        val info = TVPVODSeriesIE(http(transfer)).extract(url)
        assertEquals(playlistId, info.id)
        assertEquals("Fixture Series", info.title)
        assertEquals("Fixture lead", info.description)
        assertEquals(1, info.entries.size)
        assertEquals("311357", info.entries[0].id)
        assertEquals("https://vod.tvp.pl/x,1/y,311357", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun embedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "tvp:194536",
            infoDict = mapOf(
                "id" to Expect.Value("194536"),
                "title" to Expect.Value("Fixture subtitle"),
                "duration" to Expect.Value(2652.0),
                "age_limit" to Expect.Value(12),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(embedRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TVPEmbedIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun vodVideoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val videoId = "339667"
        val url = "https://vod.tvp.pl/filmy-dokumentalne,163/ukrainski-sluga-narodu,$videoId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture VOD Title"),
                "duration" to Expect.Value(3051.0),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://vod.tvp.pl/api/products/vods/$videoId?lang=pl&platform=BROWSER",
                    contentType = "application/json",
                    body = """{"id": 339667, "title": "Fixture VOD Title", "duration": 3051}""",
                ),
                FixtureRoute(
                    urlPattern = "https://vod.tvp.pl/api/products/$videoId/videos/playlist" +
                        "?lang=pl&platform=BROWSER&videoType=MOVIE",
                    contentType = "application/json",
                    body = """{"sources": {"HLS": [{"src": "https://media.example/vod/master.m3u8"}]}}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TVPVODVideoIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
