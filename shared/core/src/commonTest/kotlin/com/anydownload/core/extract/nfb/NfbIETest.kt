package com.anydownload.core.extract.nfb

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
 * Fixture cases for the NFB/ONF subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class NfbIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val filmId = "trafficopter"

    private fun filmPage() = """
        <html><head>
        <script>window.PLAYER_OPTIONS["fixture"] = {"source": "https://media.example/hls/film.m3u8",
          "dvSource": "https://media.example/hls/film-dv.m3u8",
          "poster": "https://media.example/poster.jpg",
          "overlay": {"url": "https://www.nfb.ca/film/$filmId/overlay/"}};</script>
        </head><body>
        <div itemprops="director" itemtype="https://schema.org/Person"><span itemprops="name">Fixture Director</span></div>
        <script>"nfb_version_title": "Fixture Film",</script>
        <script>"nfb_version_year": "1972",</script>
        <div id="tabSynopsis"><p>Fixture description</p></div>
        </body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NFBIE(http(transfer())) to "https://www.nfb.ca/film/$filmId/",
            NFBIE(http(transfer())) to "https://www.onf.ca/film/fixture-french/",
            NFBIE(http(transfer())) to "https://www.nfb.ca/series/fixture-series/season1/episode9/",
            NFBSeriesIE(http(transfer())) to "https://www.nfb.ca/series/fixture-series/",
            NFBSeriesIE(http(transfer())) to "https://www.onf.ca/serie/fixture-serie/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NFBIE(http(transfer())).suitable("https://www.nfb.ca/series/fixture-series/"))
    }

    // ------------------------------------------------------------------- film

    @Test
    fun filmPageYieldsHlsFormats() = runTest {
        val url = "https://www.nfb.ca/film/$filmId/"
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = filmPage()))
        val info = NFBIE(http(transfer)).extract(url)
        assertEquals(filmId, info.id)
        assertEquals("Fixture Film", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("1972", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("described video", info.formats[1].formatNote)
        assertEquals("https://media.example/poster.jpg", info.thumbnails.single().url)
    }

    // ---------------------------------------------------------------- series

    @Test
    fun seriesListingYieldsEpisodeEntries() = runTest {
        val url = "https://www.nfb.ca/series/fixture-series/"
        val page = """
            <html><body><script>
            episodesData: [{"embed_url": "https://www.nfb.ca/series/fixture-series/season1/episode9/",
              "description": "Fixture episode", "thumbnail_url": "https://media.example/thumb.jpg",
              "data_layer": {"seriesTitle": "Fixture Series", "episodeTitle": "Finale",
                             "episodeYear": 2018}}];
            </script></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.nfb.ca/series/fixture-series/season1/episode1",
                contentType = "text/html",
                body = page,
            ),
        )
        val info = NFBSeriesIE(http(transfer)).extract(url)
        assertEquals("fixture-series", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.nfb.ca/series/fixture-series/season1/episode9/", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun filmIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.nfb.ca/film/$filmId/"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(filmId),
                "title" to Expect.Value("Fixture Film"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(FixtureRoute(urlPattern = url, contentType = "text/html", body = filmPage())),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NFBIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
