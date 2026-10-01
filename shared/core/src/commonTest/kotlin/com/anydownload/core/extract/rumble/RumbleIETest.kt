package com.anydownload.core.extract.rumble

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
 * Fixture cases for the Rumble subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class RumbleIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "v5pv5f"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            RumbleEmbedIE(http(transfer())) to "https://rumble.com/embed/$videoId",
            RumbleEmbedIE(http(transfer())) to "https://rumble.com/embed/ufe9n.$videoId",
            RumbleIE(http(transfer())) to "https://rumble.com/$videoId-fixture-video.html",
            RumbleChannelIE(http(transfer())) to "https://rumble.com/c/Styxhexenhammer666",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(RumbleIE(http(transfer())).suitable("https://rumble.com/videos"))
    }

    // ---------------------------------------------------------------- embed

    @Test
    fun playerApiYieldsFormatsCaptionsAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://rumble.com/embedJS/u3/?request=video&ver=2&v=$videoId",
                contentType = "application/json",
                body = """
                    {"title": "Fixture Rumble", "live": 0, "duration": 234, "fps": 30,
                     "pubDate": "2019-10-20T00:00:00Z", "i": "https://media.example/thumb.jpg",
                     "author": {"name": "Fixture Channel", "url": "https://rumble.com/c/Fixture"},
                     "ua": {
                       "hls": {"auto": {"url": "https://media.example/hls/master.m3u8",
                                        "meta": {"h": 720, "w": 1280, "bitrate": 2000}}},
                       "mp4": {"720": {"url": "https://media.example/video/720.mp4",
                                       "meta": {"h": 720, "size": 1000}},
                               "audio": {"url": "https://media.example/audio/128.mp4"}}},
                     "cc": {"en": {"path": "https://media.example/sub/en.vtt", "language": "English"}}}
                """.trimIndent(),
            ),
        )
        val info = RumbleEmbedIE(http(transfer)).extract("https://rumble.com/embed/$videoId")
        assertEquals(videoId, info.id)
        assertEquals("Fixture Rumble", info.title)
        assertEquals(234.0, info.duration)
        assertEquals("20191020", info.uploadDate)
        assertEquals("Fixture Channel", info.channel)
        assertEquals(false, info.isLive)
        assertEquals(3, info.formats.size)
        assertEquals("m3u8_native", info.formats.first { it.formatId == "hls" }.protocol)
        assertEquals(720L, info.formats.first { it.formatId?.startsWith("mp4-720") == true }.height)
        assertEquals("en", info.subtitles.single().language)
    }

    // ---------------------------------------------------------------- page

    @Test
    fun pageRedirectsToTheEmbed() = runTest {
        val url = "https://rumble.com/$videoId-fixture-video.html"
        val page = """
            <html><body>
            <script src="https://rumble.com/embed/$videoId"></script>
            <div class="media-description"><p>Fixture description</p></div>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = RumbleIE(http(transfer)).extract(url)
        assertEquals("https://rumble.com/embed/$videoId", info.redirectUrl)
        assertEquals("Fixture description", info.description)
    }

    // -------------------------------------------------------------- channel

    @Test
    fun channelPagesYieldEntries() = runTest {
        val url = "https://rumble.com/c/Fixture"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://rumble.com/c/Fixture?page=1",
                contentType = "text/html",
                body = """
                    <html><body><a class="videostream__link" href="/$videoId-fixture.html">One</a></body></html>
                """.trimIndent(),
            ),
        )
        val info = RumbleChannelIE(http(transfer)).extract(url)
        assertEquals("Fixture", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://rumble.com/$videoId-fixture.html", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun embedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://rumble.com/embed/$videoId",
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Rumble"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://rumble.com/embedJS/u3/?request=video&ver=2&v=$videoId",
                    contentType = "application/json",
                    body = """
                        {"title": "Fixture Rumble", "live": 0,
                         "ua": {"hls": {"auto": {"url": "https://media.example/hls/master.m3u8",
                                                 "meta": {"h": 720}}}}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RumbleEmbedIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
