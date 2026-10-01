package com.anydownload.core.extract.wdr

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
 * Fixture cases for the WDR subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class WdrIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "1557833"

    private fun assetJson() = """
        {"mediaType": "vod",
         "trackerData": {"trackerClipId": "mdb-$videoId", "trackerClipTitle": "Fixture WDR",
                         "trackerClipAirTime": "2018-01-12"},
         "mediaResource": {
           "dflt": {"videoURL": "https://media.example/hls/master.m3u8",
                    "audioURL": "https://media.example/audio/item.mp3"},
           "captionsHash": {"vtt": "https://media.example/cc/de.vtt"},
           "captionURL": "https://media.example/cc/de.ttml"}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            WDRIE(http(transfer())) to "http://deviceids-medp.wdr.de/ondemand/155/$videoId.js",
            WDRPageIE(http(transfer())) to
                "http://www1.wdr.de/mediathek/video/sendungen/doku-am-freitag/video-fixture-100.html",
            WDRPageIE(http(transfer())) to "http://www.wdrmaus.de/aktuelle-sendung/index.php5",
            WDRElefantIE(http(transfer())) to "http://www.wdrmaus.de/elefantenseite/#elefantenkino_wippe",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(WDRElefantIE(http(transfer())).suitable("http://www1.wdr.de/mediathek/video/x-100.html"))
    }

    // ------------------------------------------------------------------ asset

    @Test
    fun assetYieldsHlsAndSubtitles() = runTest {
        val url = "http://deviceids-medp.wdr.de/ondemand/155/$videoId.js"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                contentType = "text/javascript",
                body = "wdrJsonp(${assetJson()});",
            ),
        )
        val info = WDRIE(http(transfer)).extract(url)
        assertEquals("mdb-$videoId", info.id)
        assertEquals("Fixture WDR", info.title)
        assertEquals("20180112", info.uploadDate)
        assertEquals(false, info.isLive)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mp3", info.formats[1].ext)
        assertEquals("de", info.subtitles.single().language)
        assertEquals(2, info.subtitles.single().formats.size)
    }

    // ------------------------------------------------------------------- page

    @Test
    fun pageScansDataExtensionEntries() = runTest {
        val url = "http://www1.wdr.de/mediathek/video/sendungen/doku-am-freitag/video-fixture-100.html"
        val page = """
            <html><body>
            <a class="mediaLink" data-extension-ard='{"mediaObj": {"url": "https://deviceids-medp.wdr.de/ondemand/155/$videoId.js"}}'>x</a>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = WDRPageIE(http(transfer)).extract(url)
        assertEquals("video-fixture-100", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://deviceids-medp.wdr.de/ondemand/155/$videoId.js", info.entries.single().url)
    }

    // ---------------------------------------------------------------- elefant

    @Test
    fun elefantRedirectsToTheZmdbUrl() = runTest {
        val url = "http://www.wdrmaus.de/elefantenseite/#elefantenkino_wippe"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.wdrmaus.de/elefantenseite/data/tableOfContentsJS.php5",
                contentType = "application/json",
                body = """{"elefantenkino_wippe": {"xmlPath": "data/wippe.xml"}}""",
            ),
            FixtureRoute(
                urlPattern = "https://www.wdrmaus.de/elefantenseite/data/wippe.xml",
                contentType = "text/xml",
                body = "<movie><zmdb_url>https://deviceids-medp.wdr.de/ondemand/119/1198320.js</zmdb_url></movie>",
            ),
        )
        val info = WDRElefantIE(http(transfer)).extract(url)
        assertEquals(
            "https://deviceids-medp.wdr.de/ondemand/119/1198320.js",
            info.redirectUrl,
        )
    }

    // --------------------------------------------------------------- harness

    @Test
    fun assetIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "http://deviceids-medp.wdr.de/ondemand/155/$videoId.js"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("mdb-$videoId"),
                "title" to Expect.Value("Fixture WDR"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/javascript",
                    body = "wdrJsonp(${assetJson()});",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> WDRIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
