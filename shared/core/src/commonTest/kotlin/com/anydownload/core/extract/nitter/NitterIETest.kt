package com.anydownload.core.extract.nitter

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Nitter subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class NitterIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val statusId = "1314279897502629888"
    private val instance = "nitter.example"

    private fun statusPage() = """
        <html><head>
        <meta property="og:description" content="Fixture tweet text">
        <meta property="og:image" content="https://media.example/thumb.jpg"></head>
        <body><div class="main-tweet">
        <a class="username" title="@fixtureuser"></a>
        <a class="fullname" title="Fixture User"></a>
        <div class="tweet-content">Fixture tweet text</div>
        <span class="tweet-date"><a title="Oct 8, 2020 · 6:02 PM UTC"></a></span>
        <span class="icon-play"></span>10</div>
        <video data-url="https://media.example/hls/master.m3u8" poster="/pic/thumb.jpg"></video>
        </div></body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchTheClass() {
        assertTrue(NitterIE(http(transfer())).suitable("https://$instance/firefox/status/$statusId#m"))
        assertTrue(NitterIE(http(transfer())).suitable("https://nitter.lacontrevoie.fr/Le___Doc/status/1299715685392756737"))
        assertTrue(NitterIE(http(transfer())).suitable("https://tweet.lambda.dance/user/status/123"))
        assertFalse(NitterIE(http(transfer())).suitable("https://www.example.com/user/status/123"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun statusPageYieldsHlsAndMetadata() = runTest {
        val url = "https://$instance/firefox/status/$statusId#m"
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = statusPage()))
        val info = NitterIE(http(transfer)).extract(url)
        assertEquals(statusId, info.id)
        assertEquals("Fixture User - Fixture tweet text", info.title)
        assertEquals("Fixture tweet text", info.description)
        assertEquals("Fixture User", info.channel)
        assertEquals(10L, info.viewCount)
        assertEquals("20201008", info.uploadDate)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun statusIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://$instance/firefox/status/$statusId#m"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(statusId),
                "title" to Expect.Value("Fixture User - Fixture tweet text"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(FixtureRoute(urlPattern = url, contentType = "text/html", body = statusPage())),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NitterIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
