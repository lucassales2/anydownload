package com.anydownload.core.extract.npo

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
 * Fixture cases for the NPO subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class NpoIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NPOIE(http(transfer())) to "npo:VPWON_1220719",
            NPOIE(http(transfer())) to "https://npo.nl/KN_1698996",
            NPOLiveIE(http(transfer())) to "https://www.npo.nl/live/npo-1",
            NPORadioIE(http(transfer())) to "https://www.npo.nl/radio/radio-1",
            NPORadioFragmentIE(http(transfer())) to "https://www.npo.nl/radio/radio-5/fragment/174356",
            SchoolTVIE(http(transfer())) to "https://www.schooltv.nl/video/fixture-video/",
            HetKlokhuisIE(http(transfer())) to "https://hetklokhuis.nl/tv-uitzending/3471/Fixture",
            VPROIE(http(transfer())) to "https://www.vpro.nl/programmas/2doc/2015/fixture.html",
            WNLIE(http(transfer())) to "https://www.omroepwnl.nl/video/detail/fixture__060515",
            AndereTijdenIE(http(transfer())) to
                "https://anderetijden.nl/programma/1/Andere-Tijden/aflevering/676/Fixture",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NPORadioIE(http(transfer())).suitable("https://www.npo.nl/radio/radio-5/fragment/1"))
    }

    // -------------------------------------------------------------- token wall

    @Test
    fun playerFailsTypedOnTheTokenFlow() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            NPOIE(http(transfer())).extract("npo:VPWON_1220719")
        }
        assertTrue(error.message!!.contains("token"))
    }

    // ----------------------------------------------------------------- radio

    @Test
    fun radioPageYieldsTheLiveStream() = runTest {
        val url = "https://www.npo.nl/radio/radio-1"
        val page = """
            <html><body>
            <div data-channel='NPO Radio 1'
                 data-streams='{"url": "https://media.example/radio/live.mp3", "codec": "mp3"}'></div>
            </body></html>
        """.trimIndent()
        val info = NPORadioIE(http(transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))))
            .extract(url)
        assertEquals("radio-1", info.id)
        assertEquals("NPO Radio 1", info.title)
        assertEquals("https://media.example/radio/live.mp3", info.url)
        assertEquals("mp3", info.ext)
        assertEquals(true, info.isLive)
    }

    @Test
    fun radioFragmentYieldsTheAudioUrl() = runTest {
        val url = "https://www.npo.nl/radio/radio-5/fragment/174356"
        val page = """
            <html><body>
            <a href="/radio/radio-5/fragment/174356" title="Fixture Fragment">Fixture</a>
            <div data-streams='https://media.example/radio/fragment.mp3'></div>
            </body></html>
        """.trimIndent()
        val info = NPORadioFragmentIE(http(transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))))
            .extract(url)
        assertEquals("174356", info.id)
        assertEquals("Fixture Fragment", info.title)
        assertEquals("https://media.example/radio/fragment.mp3", info.url)
    }

    // ------------------------------------------------------------- redirects

    @Test
    fun liveAndDataMidPagesReDispatchToTheNpoScheme() = runTest {
        val liveUrl = "https://www.npo.nl/live/npo-1"
        val live = NPOLiveIE(
            http(
                transfer(
                    FixtureRoute(
                        urlPattern = liveUrl,
                        contentType = "text/html",
                        body = "<html><body><div media-id=\"LI_NL1_4188102\"></div></body></html>",
                    ),
                ),
            ),
        ).extract(liveUrl)
        assertEquals("LI_NL1_4188102", live.id)
        assertEquals("npo:LI_NL1_4188102", live.redirectUrl)

        val schoolUrl = "https://www.schooltv.nl/video/fixture-video/"
        val school = SchoolTVIE(
            http(
                transfer(
                    FixtureRoute(
                        urlPattern = schoolUrl,
                        contentType = "text/html",
                        body = "<html><body><div data-mid=\"WO_NTR_429477\"></div></body></html>",
                    ),
                ),
            ),
        ).extract(schoolUrl)
        assertEquals("npo:WO_NTR_429477", school.redirectUrl)
    }

    // ------------------------------------------------------------- playlists

    @Test
    fun vproPlaylistScansMediaIds() = runTest {
        val url = "https://www.vpro.nl/programmas/2doc/2015/fixture.html"
        val page = """
            <html><body>
            <h1 class="media-platform-title">Fixture Playlist</h1>
            <div data-media-id="VPWON_1169289"></div>
            <div data-media-id="https://www.youtube.com/watch?v=fake_value"></div>
            </body></html>
        """.trimIndent()
        val info = VPROIE(http(transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))))
            .extract(url)
        assertEquals("fixture", info.id)
        assertEquals("Fixture Playlist", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("npo:VPWON_1169289", info.entries[0].url)
        assertEquals("https://www.youtube.com/watch?v=fake_value", info.entries[1].url)
    }

    @Test
    fun wnlAndAndereTijdenPlaylistsScanTheirEntries() = runTest {
        val wnlUrl = "https://www.omroepwnl.nl/video/detail/fixture__060515"
        val wnlPage = """
            <html><body><h1 class="subject">Fixture Subject</h1>
            <a href="https://media.example/watch/1" class="js-mid">Deel 1</a>
            </body></html>
        """.trimIndent()
        val wnl = WNLIE(http(transfer(FixtureRoute(urlPattern = wnlUrl, contentType = "text/html", body = wnlPage))))
            .extract(wnlUrl)
        assertEquals("Fixture Subject", wnl.title)
        assertEquals(1, wnl.entries.size)

        val atUrl = "https://anderetijden.nl/programma/1/Andere-Tijden/aflevering/676/Fixture"
        val atPage = """
            <html><body>
            <h1 class="page-title">Fixture Page Title</h1>
            <figure class="episode-container episode-page" data-prid="POMS_AT_11736927"></figure>
            </body></html>
        """.trimIndent()
        val at = AndereTijdenIE(http(transfer(FixtureRoute(urlPattern = atUrl, contentType = "text/html", body = atPage))))
            .extract(atUrl)
        assertEquals("Fixture Page Title", at.title)
        assertEquals("npo:POMS_AT_11736927", at.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun radioPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.npo.nl/radio/radio-1"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("radio-1"),
                "title" to Expect.Value("NPO Radio 1"),
                "url" to Expect.Value("https://media.example/radio/live.mp3"),
                "is_live" to Expect.Value(true),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><body>
                        <div data-channel='NPO Radio 1'
                             data-streams='{"url": "https://media.example/radio/live.mp3", "codec": "mp3"}'></div>
                        </body></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NPORadioIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun vproPlaylistIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.vpro.nl/programmas/2doc/2015/fixture.html"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("fixture"),
                "title" to Expect.Value("Fixture Playlist"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><body><h1 class="media-platform-title">Fixture Playlist</h1>
                        <div data-media-id="VPWON_1169289"></div></body></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> VPROIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
