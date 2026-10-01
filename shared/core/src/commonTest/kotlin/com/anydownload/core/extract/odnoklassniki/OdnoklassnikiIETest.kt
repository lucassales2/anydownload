package com.anydownload.core.extract.odnoklassniki

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
 * Fixture cases for the Odnoklassniki subset. Ids, titles, and media paths
 * are synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class OdnoklassnikiIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "62036049272859"
    private val pageUrl = "https://ok.ru/video/$videoId"

    private fun desktopPage(player: String) = """
        <html><head>
        <meta name="ya:ovs:upload_date" content="2022-08-01">
        <meta name="ya:ovs:adult" content="false">
        </head><body>
        <div data-options='$player'></div>
        </body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val extractor = OdnoklassnikiIE(http(transfer()))
        assertTrue(extractor.suitable(pageUrl))
        assertTrue(extractor.suitable("https://ok.ru/videoembed/$videoId"))
        assertFalse(extractor.suitable("https://ok.ru/group/1234"))
    }

    // -------------------------------------------------------------- desktop

    @Test
    fun desktopPageYieldsFormatsAndMetadata() = runTest {
        val player = """
            {"videoId": "$videoId", "isExternalPlayer": false, "flashvars": {
              "metadata": "{\"provider\": \"UPLOADED_ODKL\", \"movie\": {\"title\": \"Fixture OK\", \"poster\": \"https://media.example/thumb.jpg\", \"duration\": 76, \"subtitleTracks\": [{\"language\": \"en\", \"url\": \"https://media.example/sub/en.vtt\"}]}, \"author\": {\"id\": 1, \"name\": \"Fixture Author\"}, \"videos\": [{\"name\": \"mobile\", \"url\": \"https://media.example/video/type=4/fixture.mp4\"}], \"hlsManifestUrl\": \"https://media.example/hls/master.m3u8\"}"}}
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = pageUrl, contentType = "text/html", body = desktopPage(player)))
        val info = OdnoklassnikiIE(http(transfer)).extract(pageUrl)
        assertEquals(videoId, info.id)
        assertEquals("Fixture OK", info.title)
        assertEquals("Fixture Author", info.uploader)
        assertEquals(76.0, info.duration)
        assertEquals("20220801", info.uploadDate)
        assertEquals(0, info.ageLimit)
        assertEquals(2, info.formats.size)
        assertEquals(1, info.formats.first { it.formatId == "mobile" }.preference)
        assertEquals("m3u8_native", info.formats.first { it.protocol == "m3u8_native" }.protocol)
        assertEquals("en", info.subtitles.single().language)
    }

    @Test
    fun externalPlayerBecomesARedirect() = runTest {
        val player = """
            {"videoId": "$videoId", "isExternalPlayer": true, "url": "https://media.example/player/fixture"}
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = pageUrl, contentType = "text/html", body = desktopPage(player)))
        val info = OdnoklassnikiIE(http(transfer)).extract(pageUrl)
        assertEquals("https://media.example/player/fixture", info.redirectUrl)
    }

    // --------------------------------------------------------------- mobile

    @Test
    fun mobilePageIsTheFallback() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = pageUrl,
                contentType = "text/html",
                body = "<html><body><div class=\"vp_video_stub_txt\">Fixture unavailable</div></body></html>",
            ),
            FixtureRoute(
                urlPattern = "https://m.ok.ru/video/$videoId",
                contentType = "text/html",
                body = """
                    <html><body>
                    <div data-video="{&quot;videoSrc&quot;: &quot;https://media.example/mobile.mp4&quot;,
                                      &quot;videoName&quot;: &quot;Fixture Mobile&quot;,
                                      &quot;videoDuration&quot;: 5000}"></div>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val info = OdnoklassnikiIE(http(transfer)).extract(pageUrl)
        assertEquals("Fixture Mobile", info.title)
        assertEquals(5.0, info.duration)
        assertEquals("https://media.example/mobile.mp4", info.formats.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun desktopPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val player = """
            {"videoId": "$videoId", "isExternalPlayer": false, "flashvars": {
              "metadata": "{\"provider\": \"UPLOADED_ODKL\", \"movie\": {\"title\": \"Fixture OK\"},
               \"videos\": [{\"name\": \"mobile\", \"url\": \"https://media.example/video/fixture.mp4\"}]}"}}
        """.trimIndent()
        val case = ExtractorCase(
            url = pageUrl,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture OK"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = pageUrl, contentType = "text/html", body = desktopPage(player)),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> OdnoklassnikiIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
