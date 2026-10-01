package com.anydownload.core.extract.tumblr

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
 * Fixture cases for the Tumblr subset. Every id, host, and media address is
 * synthesized (`*.example`); no cookie, token, or signed URL appears.
 */
class TumblrIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val postUrl = "https://fixture-blog.tumblr.com/post/123456789"
    private val fetchUrl = "http://fixture-blog.tumblr.com/post/123456789"
    private val iframeUrl = "https://www.tumblr.com/video/fixture-blog/123456789/500"

    private val page = """
        <html><head>
        <title>Fixture Tumblr Post | Tumblr</title>
        <meta property="og:description" content="Fixture description">
        <meta property="og:video" content="https://media.example/tumblr/fallback.mp4">
        <meta property="og:video:width" content="640">
        <meta property="og:video:height" content="360">
        <meta property="og:image" content="https://img.example/tumblr.jpg">
        </head><body>
        <iframe src='$iframeUrl'></iframe>
        </body></html>
    """.trimIndent()

    private val iframe = """
        <html><body><video data-crt-options='{"duration": 42, "hdUrl": "https://media.example/tumblr/video_720.mp4"}'>
        <source src="https://media.example/tumblr/video_360.mp4"></video></body></html>
    """.trimIndent()

    private fun videoRoutes(): List<FixtureRoute> = listOf(
        FixtureRoute(urlPattern = fetchUrl, contentType = "text/html", body = page),
        FixtureRoute(urlPattern = iframeUrl, contentType = "text/html", body = iframe),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            TumblrIE(http(transfer())) to postUrl,
            TumblrIE(http(transfer())) to "https://fixture-blog.tumblr.com/video/123456789",
            TumblrIE(http(transfer())) to "https://www.tumblr.com/video/123456789",
            TumblrIE(http(transfer())) to "https://fixture-blog.tumblr.com/post/123456789?foo=1",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(TumblrIE(http(transfer())).suitable("https://fixture-blog.tumblr.com/tagged/fixture"))
    }

    // ------------------------------------------------------------- extraction

    @Test
    fun postMapsTheIframeFormats() = runTest {
        val transfer = transfer(*videoRoutes().toTypedArray())
        val info = TumblrIE(http(transfer)).extract(postUrl)

        assertEquals("123456789", info.id)
        assertEquals("Fixture Tumblr Post", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(42.0, info.duration)
        assertEquals("https://img.example/tumblr.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("sd", info.formats[0].formatId)
        assertEquals(360L, info.formats[0].height)
        assertEquals(0, info.formats[0].preference)
        assertEquals("hd", info.formats[1].formatId)
        assertEquals(720L, info.formats[1].height)
        assertEquals(1, info.formats[1].preference)
        assertTrue(
            transfer.requests.filter { it.url == fetchUrl }
                .all { it.headers["user-agent"] == "WhatsApp/2.0" },
        )
    }

    @Test
    fun postFallsBackToTheOgVideo() = runTest {
        val pageWithoutIframe = """
            <html><head>
            <title>Fixture Tumblr Post | Tumblr</title>
            <meta property="og:video" content="https://media.example/tumblr/fallback.mp4">
            <meta property="og:video:width" content="640">
            <meta property="og:video:height" content="360">
            </head></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = fetchUrl, contentType = "text/html", body = pageWithoutIframe),
        )
        val info = TumblrIE(http(transfer)).extract(postUrl)
        assertEquals("https://media.example/tumblr/fallback.mp4", info.formats.single().url)
        assertEquals(640L, info.formats.single().width)
        assertEquals(360L, info.formats.single().height)
    }

    @Test
    fun dashboardOnlyRedirectFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = fetchUrl,
                redirectTo = "https://www.tumblr.com/safe-mode",
            ),
            FixtureRoute(urlPattern = "https://www.tumblr.com/safe-mode", contentType = "text/html", body = "ok"),
        )
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            TumblrIE(http(transfer)).extract(postUrl)
        }
        assertTrue(error.message!!.contains("dashboard-only"))
    }

    // --------------------------------------------------------------- harness

    @Test
    fun tumblrIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = postUrl,
            infoDict = mapOf(
                "id" to Expect.Value("123456789"),
                "title" to Expect.Value("Fixture Tumblr Post"),
                "duration" to Expect.Value(42.0),
                "formats.1.format_id" to Expect.Value("hd"),
                "formats.1.height" to Expect.Value(720L),
            ),
            routes = videoRoutes(),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TumblrIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
