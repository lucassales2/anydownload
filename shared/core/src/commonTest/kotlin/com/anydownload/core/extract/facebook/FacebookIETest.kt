package com.anydownload.core.extract.facebook

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorRegistry
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
 * Fixture cases for the Facebook classic server-JS subset. Every id, host,
 * and address is synthesized (`*.example`); no cookie or signed media URL
 * appears.
 */
class FacebookIETest {

    private val videoUrl = "https://www.facebook.com/fixturepage/videos/1234567890123456/"

    private val page = """
        <html><head>
        <meta property="og:title" content="Synthetic Facebook video | Facebook">
        <meta property="og:description" content="A synthetic Facebook description">
        <meta property="og:image" content="https://scontent.example/thumb.jpg">
        </head><body>
        <abbr data-utime="1669516858">Aug 19</abbr>
        <script>var x = {ownerName:"Fixture Uploader", viewCount:"1,234"};</script>
        <script>handleServerJS({"instances":[[1,["VideoConfig"],[{
          "video_id": "1234567890123456",
          "videoData": [{
            "stream_type": "progressive",
            "sd_src": "https://video.example/sd.mp4",
            "hd_src": "https://video.example/hd.mp4",
            "sd_src_no_ratelimit": "https://video.example/sd-nr.mp4",
            "hd_src_no_ratelimit": "https://video.example/hd-nr.mp4",
            "subtitles_src": "https://video.example/subs.vtt"
          }]
        }]]]});</script>
        </body></html>
    """.trimIndent()

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): FacebookIE =
        FacebookIE(ExtractorHttp(transfer))

    // ------------------------------------------------------------ URL matching

    @Test
    fun videoFormsMatch() {
        val ie = extractor(transfer())
        val urls = listOf(
            "https://www.facebook.com/fixturepage/videos/1234567890123456/",
            "https://www.facebook.com/video.php?v=1234567890123456",
            "https://www.facebook.com/video/embed?v=1234567890123456",
            "https://www.facebook.com/watch/?v=1234567890123456",
            "https://m.facebook.com/fixturepage/posts/1234567890123456",
            "facebook:1234567890123456",
        )
        for (url in urls) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://www.facebook.com/fixturepage"))
        assertFalse(ie.suitable("https://www.facebook.com/reel/1234567890123456"))

        val reel = FacebookReelIE(ExtractorHttp(transfer()))
        assertTrue(reel.suitable("https://www.facebook.com/reel/1234567890123456"))
        assertFalse(reel.suitable(videoUrl))

        val plugins = FacebookPluginsVideoIE(ExtractorHttp(transfer()))
        assertTrue(plugins.suitable("https://www.facebook.com/plugins/video.php?href=https%3A%2F%2Fexample.com"))

        val safety = FacebookRedirectURLIE(ExtractorHttp(transfer()))
        assertTrue(safety.suitable("https://www.facebook.com/flx/warn/?u=https%3A%2F%2Fexample.com"))
    }

    // -------------------------------------------------------------- extraction

    @Test
    fun classicVideoConfigMapsFormatsAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = videoUrl, contentType = "text/html", body = page),
        )
        val info = extractor(transfer).extract(videoUrl)

        assertEquals("1234567890123456", info.id)
        assertEquals("Synthetic Facebook video", info.title)
        assertEquals("A synthetic Facebook description", info.description)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals("20221127", info.uploadDate)
        assertEquals(1234L, info.viewCount)
        assertEquals("https://scontent.example/thumb.jpg", info.thumbnails.single().url)

        assertEquals(4, info.formats.size)
        val sd = info.formats.single { it.formatId == "progressive_sd_src" }
        assertEquals("https://video.example/sd.mp4", sd.url)
        assertEquals("facebookexternalhit/1.1", sd.httpHeaders?.get("user-agent"))
        assertEquals(250L shl 20, sd.downloaderOptions?.httpChunkSize)

        val hd = info.formats.single { it.formatId == "progressive_hd_src" }
        assertEquals(720L, hd.height)

        val subtitle = info.subtitles.single()
        assertEquals("en", subtitle.language)
        assertEquals("vtt", subtitle.formats.single().ext)
        assertEquals("https://video.example/subs.vtt", subtitle.formats.single().url)
    }

    @Test
    fun loginWallFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = videoUrl,
                body = "<html><body><div id=\"login_form\">You must log in</div></body></html>",
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            extractor(transfer).extract(videoUrl)
        }
    }

    @Test
    fun noPlayableDataFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = videoUrl, body = "<html><body>Nothing here.</body></html>"),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            extractor(transfer).extract(videoUrl)
        }
    }

    // -------------------------------------------------------------- redirects

    @Test
    fun reelRedirectsIntoTheRegistry() = runTest {
        val reelUrl = "https://www.facebook.com/reel/9876543210987654"
        val watchUrl = "https://m.facebook.com/watch/?v=9876543210987654&_rdr"
        val transfer = transfer(
            FixtureRoute(urlPattern = watchUrl, contentType = "text/html", body = page.replace("1234567890123456", "9876543210987654")),
        )
        val http = ExtractorHttp(transfer)
        val registry = ExtractorRegistry(listOf(FacebookIE(http), FacebookReelIE(http)))
        val info = registry.extract(reelUrl)
        assertEquals("9876543210987654", info.id)
        assertEquals(4, info.formats.size)
    }

    @Test
    fun pluginsRedirectIntoTheRegistry() = runTest {
        val pluginUrl =
            "https://www.facebook.com/plugins/video.php?" +
                "href=https%3A%2F%2Fwww.facebook.com%2Ffixturepage%2Fvideos%2F1234567890123456%2F"
        val transfer = transfer(
            FixtureRoute(urlPattern = videoUrl, contentType = "text/html", body = page),
        )
        val http = ExtractorHttp(transfer)
        val registry = ExtractorRegistry(listOf(FacebookIE(http), FacebookPluginsVideoIE(http)))
        val info = registry.extract(pluginUrl)
        assertEquals("1234567890123456", info.id)
    }

    @Test
    fun safetyRedirectDecodesTheTarget() = runTest {
        val safety = FacebookRedirectURLIE(ExtractorHttp(transfer()))
        val info = safety.extract(
            "https://www.facebook.com/flx/warn/?u=https%3A%2F%2Fwww.example.com%2Fwatch%3Fv%3D1&s=1",
        )
        assertEquals("https://www.example.com/watch?v=1", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun facebookIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("1234567890123456"),
                "title" to Expect.Value("Synthetic Facebook video"),
                "uploader" to Expect.Value("Fixture Uploader"),
                "upload_date" to Expect.Value("20221127"),
                "view_count" to Expect.Value(1234L),
                "formats" to Expect.Count(4),
                "formats.0.format_id" to Expect.Value("progressive_sd_src"),
            ),
            routes = listOf(FixtureRoute(urlPattern = videoUrl, contentType = "text/html", body = page)),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> FacebookIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
