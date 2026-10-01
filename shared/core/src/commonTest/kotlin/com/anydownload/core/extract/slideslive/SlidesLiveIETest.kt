package com.anydownload.core.extract.slideslive

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
 * Fixture cases for the SlidesLive subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears (the player token is a fake fixture value).
 */
class SlidesLiveIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "38902413"
    private val embedUrl = "https://slideslive.com/embed/presentation/$videoId"
    private val playerUrl = "https://slideslive.com/player/$videoId?player_token=fake_player_token"

    private val embedPage = """
        <html><body><div data-player-token="fake_player_token"></div></body></html>
    """.trimIndent()

    private val playerData = """
        #EXTM3U
        #EXT-SL-PRESENTATION-TITLE:GCC IA16 backend
        #EXT-SL-PRESENTATION-UPDATED-AT:2023-10-20T10:00:00Z
        #EXT-SL-PRESENTATION-THUMBNAIL:https://media.example/cover.jpg
        #EXT-SL-PLAYLIST-TYPE:vod
        #EXT-SL-VOD-VIDEO-SERVICE-NAME:yoda
        #EXT-SL-VOD-VIDEO-ID:fixture-path
        #EXT-SL-VOD-VIDEO-SERVERS:["cdn.example"]
        #EXT-SL-VOD-SUBTITLES:[{"language": "en", "webvtt_url": "https://media.example/sub/en.vtt"}]
        #EXT-SL-VOD-SLIDES-JSON-URL:https://media.example/slides.json
    """.trimIndent()

    private val slidesJson = """
        {"slide_qualities": ["big"], "slides": [
          {"image": {"name": "slide-001", "extname": ".jpg"}, "time": 1000},
          {"image": {"name": "slide-002"}, "time": 2000}
        ]}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val extractor = SlidesLiveIE(http(transfer()))
        assertTrue(extractor.suitable("https://slideslive.com/$videoId/fixture"))
        assertTrue(extractor.suitable("https://slideslive.com/embed/presentation/$videoId"))
        assertTrue(extractor.suitable("https://slideslive.com/embed/$videoId"))
        assertFalse(extractor.suitable("https://slideslive.com/embed/presentation/not-a-number"))
    }

    // ------------------------------------------------------------------- yoda

    @Test
    fun yodaPresentationYieldsFormatsChaptersAndSubtitles() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = embedPage),
            FixtureRoute(urlPattern = playerUrl, contentType = "application/vnd.apple.mpegurl", body = playerData),
            FixtureRoute(urlPattern = "https://media.example/slides.json", contentType = "application/json", body = slidesJson),
        )
        val info = SlidesLiveIE(http(transfer)).extract("https://slideslive.com/$videoId/fixture")
        assertEquals(videoId, info.id)
        assertEquals("GCC IA16 backend", info.title)
        assertEquals("20231020", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats.first { it.formatId == "hls" }.protocol)
        assertEquals("https://cdn.example/fixture-path/master.m3u8", info.formats.first { it.formatId == "hls" }.url)
        assertEquals(2, info.chapters.size)
        assertEquals(3, info.thumbnails.size)
        assertEquals("https://cdn.slideslive.com/data/presentations/$videoId/slides/big/slide-001.jpg", info.thumbnails[1].url)
        assertEquals("en", info.subtitles.single().language)
        assertEquals(false, info.isLive)
    }

    @Test
    fun slidesXmlPathBuildsChapters() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = embedPage),
            FixtureRoute(
                urlPattern = playerUrl,
                contentType = "application/vnd.apple.mpegurl",
                body = """
                    #EXTM3U
                    #EXT-SL-PRESENTATION-TITLE:Fixture XML
                    #EXT-SL-PLAYLIST-TYPE:vod
                    #EXT-SL-VOD-VIDEO-SERVICE-NAME:yoda
                    #EXT-SL-VOD-VIDEO-ID:fixture-path
                    #EXT-SL-VOD-VIDEO-SERVERS:["cdn.example"]
                    #EXT-SL-VOD-SLIDES-XML-URL:https://media.example/slides.xml
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://media.example/slides.xml",
                contentType = "application/xml",
                body = "<slides><slide><slideName>one</slideName><timeSec>1.5</timeSec></slide>" +
                    "<slide><slideName>two</slideName><timeSec>3</timeSec></slide></slides>",
            ),
        )
        val info = SlidesLiveIE(http(transfer)).extract("https://slideslive.com/$videoId")
        assertEquals("Fixture XML", info.title)
        assertEquals(2, info.chapters.size)
        assertEquals(1.5, info.chapters[0].startTime)
        assertEquals("https://cdn.slideslive.com/data/presentations/$videoId/slides/big/one.jpg", info.thumbnails[0].url)
    }

    @Test
    fun urlAndVimeoServicesBecomeRedirects() = runTest {
        fun player(service: String, id: String) = """
            #EXTM3U
            #EXT-SL-PRESENTATION-TITLE:Fixture
            #EXT-SL-PLAYLIST-TYPE:vod
            #EXT-SL-VOD-VIDEO-SERVICE-NAME:$service
            #EXT-SL-VOD-VIDEO-ID:$id
        """.trimIndent()
        val urlTransfer = transfer(
            FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = embedPage),
            FixtureRoute(urlPattern = playerUrl, contentType = "text/plain", body = player("url", "https://media.example/direct.mp4")),
        )
        val direct = SlidesLiveIE(http(urlTransfer)).extract("https://slideslive.com/$videoId")
        assertEquals("https://media.example/direct.mp4", direct.redirectUrl)

        val vimeoTransfer = transfer(
            FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = embedPage),
            FixtureRoute(urlPattern = playerUrl, contentType = "text/plain", body = player("vimeo", "12345")),
        )
        val vimeo = SlidesLiveIE(http(vimeoTransfer)).extract("https://slideslive.com/$videoId")
        assertEquals("https://player.vimeo.com/video/12345", vimeo.redirectUrl)
    }

    @Test
    fun unknownServiceFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = embedPage),
            FixtureRoute(
                urlPattern = playerUrl,
                contentType = "text/plain",
                body = """
                    #EXTM3U
                    #EXT-SL-VOD-VIDEO-SERVICE-NAME:fixture-service
                    #EXT-SL-VOD-VIDEO-ID:1
                """.trimIndent(),
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            SlidesLiveIE(http(transfer)).extract("https://slideslive.com/$videoId")
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun yodaPresentationIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://slideslive.com/$videoId/fixture",
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("GCC IA16 backend"),
                "formats" to Expect.Count(2),
                "thumbnails" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = embedPage),
                FixtureRoute(
                    urlPattern = playerUrl,
                    contentType = "application/vnd.apple.mpegurl",
                    body = """
                        #EXTM3U
                        #EXT-SL-PRESENTATION-TITLE:GCC IA16 backend
                        #EXT-SL-PLAYLIST-TYPE:vod
                        #EXT-SL-VOD-VIDEO-SERVICE-NAME:yoda
                        #EXT-SL-VOD-VIDEO-ID:fixture-path
                        #EXT-SL-VOD-VIDEO-SERVERS:["cdn.example"]
                        #EXT-SL-VOD-SLIDES-JSON-URL:https://media.example/slides.json
                    """.trimIndent(),
                ),
                FixtureRoute(
                    urlPattern = "https://media.example/slides.json",
                    contentType = "application/json",
                    body = """{"slides": [{"image": {"name": "slide-001"}, "time": 1000}]}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SlidesLiveIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
