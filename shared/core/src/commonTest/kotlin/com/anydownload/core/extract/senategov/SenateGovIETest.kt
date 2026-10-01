package com.anydownload.core.extract.senategov

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixture cases for the Senate subset. Ids, titles, and media paths are
 * synthesized; media lives on the akamaized/`media.example` hosts, and no
 * cookie, token, or signed URL appears.
 */
class SenateGovIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val isvpUrl = "http://www.senate.gov/isvp/?comm=judiciary&type=live&stt=" +
        "&filename=judiciary031715&auto_play=false&wmode=transparent" +
        "&poster=http%3A%2F%2Fwww.judiciary.senate.gov%2Fthemes%2Fjudiciary%2Fimages%2Fvideo-poster-flash-fit.png"
    private val judiciaryStream =
        "https://www-senate-gov-media-srs.akamaized.net/hls/live/2036788/judiciary/judiciary031715/master.m3u8"
    private val hearingUrl = "https://www.help.senate.gov/hearings/fixture-hearing"

    private val isvpPage = FixtureRoute(
        urlPattern = "http://www.senate.gov/isvp*",
        contentType = "text/html",
        body = "<html><head><title>ISVP</title></head></html>",
    )

    private fun hearingPage(rta: Boolean = false) = """
        <html><head>
        <meta property="og:title" content="Fixture Hearing | Committee">
        <meta property="og:description" content="Fixture description">
        <meta property="og:image" content="https://media.example/hearing.jpg">
        ${if (rta) "<meta name=\"rating\" content=\"RTA-5042-1996-1400-1577-RTA\">" else ""}
        </head><body>
        <iframe src="http://www.senate.gov/isvp/?comm=help&filename=help090920&poster=https://www.help.senate.gov/assets/images/video-poster.png&stt=950&auto_play=false"></iframe>
        </body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val isvpCases = listOf(
            "http://www.senate.gov/isvp/?comm=judiciary&filename=judiciary031715",
            "http://www.senate.gov/isvp?type=live&comm=commerce&filename=commerce011514.mp4",
            "https://www.senate.gov/isvp/?auto_play=false&comm=help&filename=help090920",
        )
        for (url in isvpCases) {
            assertTrue(SenateISVPIE(http(transfer())).suitable(url), "ISVP must match: $url")
        }
        assertTrue(SenateGovIE(http(transfer())).suitable(hearingUrl))
        assertTrue(
            SenateGovIE(http(transfer()))
                .suitable("https://www.appropriations.senate.gov/hearings/watch?hearingid=FIXTURE"),
        )
        assertFalse(SenateGovIE(http(transfer())).suitable("https://www.example.com/hearings/fixture"))
        assertFalse(SenateGovIE(http(transfer())).suitable("https://www.senate.gov/hearings/fixture"))
    }

    // ------------------------------------------------------------------ ISVP

    @Test
    fun isvpUsesTheFirstAvailableStreamAndThePoster() = runTest {
        val transfer = transfer(
            isvpPage,
            FixtureRoute(urlPattern = judiciaryStream, contentType = "application/vnd.apple.mpegurl", body = "#EXTM3U"),
        )
        val info = SenateISVPIE(http(transfer)).extract(isvpUrl)
        assertEquals("judiciary031715", info.id)
        assertEquals("ISVP", info.title)
        assertEquals(
            "http://www.judiciary.senate.gov/themes/judiciary/images/video-poster-flash-fit.png",
            info.thumbnails.single().url,
        )
        assertEquals(1, info.formats.size)
        assertEquals(judiciaryStream, info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
    }

    @Test
    fun isvpFallsBackToTheArchiveStream() = runTest {
        val url = "http://www.senate.gov/isvp?type=live&comm=commerce&filename=commerce011514.mp4&auto_play=false"
        val archiveStream =
            "https://www-senate-gov-msl3archive.akamaized.net/commerce/commerce011514.mp4_1/master.m3u8"
        val transfer = transfer(
            isvpPage,
            FixtureRoute(
                urlPattern = "https://www-senate-gov-media-srs.akamaized.net/hls/live/2036779/commerce/*",
                statusCode = 404,
                body = "",
            ),
            FixtureRoute(urlPattern = archiveStream, contentType = "application/vnd.apple.mpegurl", body = "#EXTM3U"),
        )
        val info = SenateISVPIE(http(transfer)).extract(url)
        assertEquals("commerce011514", info.id)
        assertEquals(archiveStream, info.formats.single().url)
    }

    @Test
    fun isvpWithoutAFilenameFailsTyped() = runTest {
        assertFailsWith<ExtractionError.UnsupportedUrl> {
            SenateISVPIE(http(transfer())).extract("http://www.senate.gov/isvp/?comm=judiciary")
        }
    }

    @Test
    fun isvpUnknownCommitteeFailsTyped() = runTest {
        val transfer = transfer(isvpPage)
        assertFailsWith<ExtractionError.UnsupportedUrl> {
            SenateISVPIE(http(transfer)).extract("http://www.senate.gov/isvp/?comm=unknown&filename=fixture")
        }
    }

    @Test
    fun isvpWithNoReachableStreamReturnsNoFormats() = runTest {
        val transfer = transfer(
            isvpPage,
            FixtureRoute(urlPattern = "https://www-senate-gov-media-srs.akamaized.net/*", statusCode = 404, body = ""),
            FixtureRoute(urlPattern = "https://www-senate-gov-msl3archive.akamaized.net/*", statusCode = 404, body = ""),
            FixtureRoute(urlPattern = "https://judiciary-f.akamaihd.net/*", statusCode = 404, body = ""),
        )
        val info = SenateISVPIE(http(transfer)).extract(isvpUrl)
        assertTrue(info.formats.isEmpty())
    }

    // ------------------------------------------------------------- committee pages

    @Test
    fun committeePageDelegatesToIsvpAndKeepsItsMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = hearingUrl, contentType = "text/html", body = hearingPage()),
            isvpPage,
            FixtureRoute(
                urlPattern = "https://www-senate-gov-media-srs.akamaized.net/hls/live/2036793/help/help090920/master.m3u8",
                contentType = "application/vnd.apple.mpegurl",
                body = "#EXTM3U",
            ),
        )
        val info = SenateGovIE(http(transfer)).extract(hearingUrl)
        assertEquals("help090920", info.id)
        assertEquals("Fixture Hearing", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("https://media.example/hearing.jpg", info.thumbnails.single().url)
        assertNull(info.ageLimit)
        assertEquals("https://www.help.senate.gov/hearings/fixture-hearing", info.webpageUrl)
    }

    @Test
    fun committeePageAppliesTheRtaAgeLimit() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = hearingUrl, contentType = "text/html", body = hearingPage(rta = true)),
            isvpPage,
            FixtureRoute(
                urlPattern = "https://www-senate-gov-media-srs.akamaized.net/hls/live/2036793/help/help090920/master.m3u8",
                contentType = "application/vnd.apple.mpegurl",
                body = "#EXTM3U",
            ),
        )
        val info = SenateGovIE(http(transfer)).extract(hearingUrl)
        assertEquals(18, info.ageLimit)
    }

    @Test
    fun committeePageWithoutAnEmbedFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = hearingUrl,
                contentType = "text/html",
                body = "<html><head><title>No embed</title></head></html>",
            ),
        )
        assertFailsWith<ExtractionError.UnsupportedUrl> {
            SenateGovIE(http(transfer)).extract(hearingUrl)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun isvpIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = isvpUrl,
            infoDict = mapOf(
                "id" to Expect.Value("judiciary031715"),
                "title" to Expect.Value("ISVP"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                isvpPage,
                FixtureRoute(urlPattern = judiciaryStream, contentType = "application/vnd.apple.mpegurl", body = "#EXTM3U"),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SenateISVPIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun committeePageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = hearingUrl,
            infoDict = mapOf(
                "id" to Expect.Value("help090920"),
                "title" to Expect.Value("Fixture Hearing"),
                "age_limit" to Expect.Value(18),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = hearingUrl, contentType = "text/html", body = hearingPage(rta = true)),
                isvpPage,
                FixtureRoute(
                    urlPattern = "https://www-senate-gov-media-srs.akamaized.net/hls/live/2036793/help/help090920/master.m3u8",
                    contentType = "application/vnd.apple.mpegurl",
                    body = "#EXTM3U",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SenateGovIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
