package com.anydownload.core.extract.ivi

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
 * Fixture cases for the ivi.ru subset. Ids and media paths are synthesized on
 * `media.example`; the API call is the unsigned site-183 fallback. No cookie,
 * token, or signed URL appears.
 */
class IviIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val movieUrl = "http://www.ivi.ru/watch/53141"
    private val compilationUrl = "http://www.ivi.ru/watch/dvoe_iz_lartsa"

    private val apiRoute = FixtureRoute(
        urlPattern = "https://api.ivi.ru/light/",
        method = "POST",
        contentType = "application/json",
        body = """
            {"result": {"title": "Fixture Movie", "duration": 5498,
              "files": [
                {"url": "https://media.example/720.mp4", "content_format": "MP4-HD720",
                 "size_in_bytes": 1234},
                {"url": "https://media.example/drm.mp4", "content_format": "MP4-MDRM-HD"},
                {"url": "https://media.example/1080.mp4", "content_format": "MP4-HD1080"}],
              "preview": [{"url": "https://media.example/thumb.jpg", "content_format": "jpg"}]}}
        """.trimIndent(),
    )

    private val moviePage = FixtureRoute(
        urlPattern = "http://www.ivi.ru/watch/53141",
        contentType = "text/html",
        body = """
            <html><head><meta property="og:description" content="Fixture description">
            </head><body></body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val movie = IviIE(http(transfer()))
        assertTrue(movie.suitable(movieUrl))
        assertTrue(movie.suitable("http://www.ivi.ru/watch/dvoe_iz_lartsa/9549"))
        assertTrue(movie.suitable("https://www.ivi.tv/watch/33560/"))
        assertTrue(movie.suitable("http://www.ivi.ru/video/player?videoId=53141"))
        assertFalse(movie.suitable(compilationUrl))

        val compilation = IviCompilationIE(http(transfer()))
        assertTrue(compilation.suitable(compilationUrl))
        assertTrue(compilation.suitable("http://www.ivi.ru/watch/dvoe_iz_lartsa/season1"))
        assertFalse(compilation.suitable(movieUrl))
        assertFalse(compilation.suitable("https://www.example.com/watch/dvoe_iz_lartsa"))
    }

    // ----------------------------------------------------------------- movie

    @Test
    fun movieYieldsTheFileRowsAndMetadata() = runTest {
        val info = IviIE(http(transfer(apiRoute, moviePage))).extract(movieUrl)
        assertEquals("53141", info.id)
        assertEquals("Fixture Movie", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(5498.0, info.duration)
        assertEquals(2, info.formats.size)
        assertEquals("MP4-HD720", info.formats[0].formatId)
        assertEquals("https://media.example/720.mp4", info.formats[0].url)
        assertEquals("7", info.formats[0].quality)
        assertEquals(1234L, info.formats[0].filesize)
        assertEquals("MP4-HD1080", info.formats[1].formatId)
        assertEquals("8", info.formats[1].quality)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    @Test
    fun movieIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = movieUrl,
            infoDict = mapOf(
                "id" to Expect.Value("53141"),
                "title" to Expect.Value("Fixture Movie"),
                "duration" to Expect.Value(5498.0),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(apiRoute, moviePage),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> IviIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun geoErrorFailsTyped() = runTest {
        val geoRoute = FixtureRoute(
            urlPattern = "https://api.ivi.ru/light/",
            method = "POST",
            contentType = "application/json",
            body = """
                {"error": {"origin": "NotAllowedForLocation", "message": "Geo blocked"}}
            """.trimIndent(),
        )
        val error = assertFailsWith<ExtractionError.GeoRestricted> {
            IviIE(http(transfer(geoRoute))).extract(movieUrl)
        }
        assertEquals(listOf("RU"), error.countries)
    }

    // ------------------------------------------------------------ compilation

    @Test
    fun compilationYieldsTheSeasonEntries() = runTest {
        val compilationPage = FixtureRoute(
            urlPattern = "http://www.ivi.ru/watch/dvoe_iz_lartsa",
            contentType = "text/html",
            body = """
                <html><head><meta name="title" content="Fixture Compilation">
                </head><body>
                <a href="/watch/dvoe_iz_lartsa/season1">Season 1</a>
                </body></html>
            """.trimIndent(),
        )
        val seasonPage = FixtureRoute(
            urlPattern = "http://www.ivi.ru/watch/dvoe_iz_lartsa/season1",
            contentType = "text/html",
            body = """
                <html><body>
                <a href="/watch/dvoe_iz_lartsa/123">Episode 1</a>
                <a href="/watch/dvoe_iz_lartsa/456">Episode 2</a>
                </body></html>
            """.trimIndent(),
        )
        val info = IviCompilationIE(http(transfer(compilationPage, seasonPage))).extract(compilationUrl)
        assertEquals("dvoe_iz_lartsa", info.id)
        assertEquals("Fixture Compilation", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("http://www.ivi.ru/watch/dvoe_iz_lartsa/123", info.entries[0].url)
        assertEquals("http://www.ivi.ru/watch/dvoe_iz_lartsa/456", info.entries[1].url)
    }

    @Test
    fun seasonUrlNamesThePlaylist() = runTest {
        val seasonPage = FixtureRoute(
            urlPattern = "http://www.ivi.ru/watch/dvoe_iz_lartsa/season1",
            contentType = "text/html",
            body = """
                <html><head><meta name="title" content="Fixture Compilation">
                </head><body><a href="/watch/dvoe_iz_lartsa/123">Episode 1</a></body></html>
            """.trimIndent(),
        )
        val info = IviCompilationIE(http(transfer(seasonPage))).extract(
            "http://www.ivi.ru/watch/dvoe_iz_lartsa/season1",
        )
        assertEquals("dvoe_iz_lartsa/season1", info.id)
        assertEquals("Fixture Compilation", info.title)
        assertEquals(1, info.entries.size)
    }
}
