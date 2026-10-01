package com.anydownload.core.extract.iprima

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
 * Fixture cases for the iPrima subset. Ids and media paths are synthesized on
 * `media.example`; the login token never appears because the class fails
 * typed before any request. No cookie, token, or signed URL appears.
 */
class IPrimaIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val cnnUrl =
        "https://cnn.iprima.cz/porady/strunc/24072020-koronaviru-mam-plne-zuby"
    private val showUrl = "https://prima.iprima.cz/particka/92-epizoda"

    private val cnnPage = FixtureRoute(
        urlPattern = "https://cnn.iprima.cz/*",
        contentType = "text/html",
        body = """
            <html><head>
            <meta property="og:title" content="Fixture CNN Title">
            <meta property="og:image" content="https://media.example/thumb.jpg">
            <meta property="og:description" content="Fixture description">
            </head><body><div data-product="p716177"></div></body></html>
        """.trimIndent(),
    )

    private val playerPage = FixtureRoute(
        urlPattern = "http://play.iprima.cz/prehravac/init?*",
        contentType = "text/html",
        body = """
            <html><script>
            TDIPlayerOptions = {"tracks":{"hls":[{"src":"https://media.example/master.m3u8","lang":"cs"}]}}; ]]
            </script></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val show = IPrimaIE(http(transfer()))
        val showCases = listOf(
            showUrl,
            "http://play.iprima.cz/particka/particka-92",
            "http://www.iprima.cz/filmy/desne-rande",
            "https://zoom.iprima.cz/porady/krasy-kanarskych-ostrovu/tenerife-v-risi-ohne",
            "https://cool.iprima.cz/derava-silnice-nevadi",
        )
        for (url in showCases) {
            assertTrue(show.suitable(url), "iPrima must match: $url")
        }
        assertFalse(show.suitable(cnnUrl), "the non-CNN class must yield cnn URLs")
        assertFalse(show.suitable("https://www.example.com/filmy/desne-rande"))

        val cnn = IPrimaCNNIE(http(transfer()))
        assertTrue(cnn.suitable(cnnUrl))
        assertFalse(cnn.suitable(showUrl))
    }

    // ------------------------------------------------------------------ wall

    @Test
    fun showFailsTypedAtTheLoginWall() = runTest {
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            IPrimaIE(http(transfer())).extract(showUrl)
        }
        assertTrue(error.message!!.contains("Login is required"), error.message)
    }

    // ------------------------------------------------------------------- CNN

    @Test
    fun cnnYieldsTheHlsRowAndMetadata() = runTest {
        val info = IPrimaCNNIE(http(transfer(cnnPage, playerPage))).extract(cnnUrl)
        assertEquals("p716177", info.id)
        assertEquals("Fixture CNN Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("hls", info.formats[0].formatId)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("cs", info.formats[0].language)
    }

    @Test
    fun cnnIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = cnnUrl,
            infoDict = mapOf(
                "id" to Expect.Value("p716177"),
                "title" to Expect.Value("Fixture CNN Title"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(cnnPage, playerPage),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> IPrimaCNNIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun cnnFailsTypedWhenGeoBlocked() = runTest {
        val geoPage = FixtureRoute(
            urlPattern = "http://play.iprima.cz/prehravac/init?*",
            contentType = "text/html",
            body = """<html><body>>GEO_IP_NOT_ALLOWED<</body></html>""",
        )
        val error = assertFailsWith<ExtractionError.GeoRestricted> {
            IPrimaCNNIE(http(transfer(cnnPage, geoPage))).extract(cnnUrl)
        }
        assertEquals(listOf("CZ"), error.countries)
    }
}
