package com.anydownload.core.extract.ndr

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
 * Fixture cases for the NDR subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class NdrIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val ppjsonUrl = "http://www.ndr.de/soundcheck3366-ppjson.json"
    private val ppjson = """
        {"playlist": {
           "hls": {"src": "https://media.example/hls/master.m3u8"},
           "http_mp3": {"src": "https://media.example/audio/128.mp3", "quality": "m", "type": "audio/mpeg"},
           "config": {"title": "Fixture NDR", "duration": 132, "streamType": "httpVideo",
                      "poster": {"big": {"src": "https://media.example/thumb.jpg", "quality": "l"}},
                      "tracks": [{"src": "https://media.example/sub/de.ttml", "srclang": "de"}]}},
         "config": {"branding": "ndrtv", "publicationDate": "20150907T00:00:00"}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NDRIE(http(transfer())) to "https://www.ndr.de/fernsehen/fixture,hafengeburtstag988.html",
            NJoyIE(http(transfer())) to "https://www.n-joy.de/musik/fixture,felixjaehn168.html",
            NDREmbedBaseIE(http(transfer())) to "ndr:soundcheck3366",
            NDREmbedBaseIE(http(transfer())) to ppjsonUrl,
            NDREmbedIE(http(transfer())) to "https://www.ndr.de/fernsehen/soundcheck3366-player.html",
            NDREmbedIE(http(transfer())) to "https://www.ndr.de/fernsehen/visite11010-externalPlayer.html",
            NJoyEmbedIE(http(transfer())) to "https://www.n-joy.de/events/doku948-player_image-fixture_theme-n-joy.html",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NDREmbedBaseIE(http(transfer())).suitable("https://www.ndr.de/fixture.html"))
    }

    // ---------------------------------------------------------------- ppjson

    @Test
    fun ppjsonYieldsFormatsThumbnailsAndTracks() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = ppjsonUrl, contentType = "application/json", body = ppjson),
        )
        val info = NDREmbedBaseIE(http(transfer)).extract("ndr:soundcheck3366")
        assertEquals("soundcheck3366", info.id)
        assertEquals("Fixture NDR", info.title)
        assertEquals("ndrtv", info.uploader)
        assertEquals("20150907", info.uploadDate)
        assertEquals(132.0, info.duration)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats.first { it.formatId == "hls" }.protocol)
        assertEquals("mp3", info.formats.first { it.formatId == "m" }.ext)
        assertEquals(1, info.thumbnails.size)
        assertEquals("de", info.subtitles.single().language)
    }

    // ------------------------------------------------------------- page scans

    @Test
    fun ndrPageRedirectsToTheEmbedUrl() = runTest {
        val url = "https://www.ndr.de/fernsehen/fixture,hafengeburtstag988.html"
        val page = """
            <html><head><meta name="embedURL" content="ndr:hafengeburtstag988"></head></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = NDRIE(http(transfer)).extract(url)
        assertEquals("ndr:hafengeburtstag988", info.redirectUrl)
    }

    @Test
    fun njoyPageRedirectsToTheNdrId() = runTest {
        val url = "https://www.n-joy.de/musik/fixture,felixjaehn168.html"
        val page = """
            <html><head><meta name="description" content="Fixture description"></head><body>
            <iframe id="pp_felixjaehn168"></iframe>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = NJoyIE(http(transfer)).extract(url)
        assertEquals("ndr:felixjaehn168", info.redirectUrl)
        assertEquals("Fixture description", info.description)
    }

    @Test
    fun embedPageFetchesItsOwnPpjson() = runTest {
        val url = "https://www.ndr.de/fernsehen/soundcheck3366-player.html"
        val transfer = transfer(
            FixtureRoute(urlPattern = ppjsonUrl, contentType = "application/json", body = ppjson),
        )
        val info = NDREmbedIE(http(transfer)).extract(url)
        assertEquals("soundcheck3366", info.id)
        assertEquals("Fixture NDR", info.title)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun ppjsonIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "ndr:soundcheck3366",
            infoDict = mapOf(
                "id" to Expect.Value("soundcheck3366"),
                "title" to Expect.Value("Fixture NDR"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = ppjsonUrl, contentType = "application/json", body = ppjson),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NDREmbedBaseIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
