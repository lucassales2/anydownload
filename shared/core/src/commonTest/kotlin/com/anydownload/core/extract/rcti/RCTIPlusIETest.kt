package com.anydownload.core.extract.rcti

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The RCTI+ URL surface is a typed wall: the API needs a visitor access
 * token, so no fixture can pass.
 */
class RCTIPlusIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            RCTIPlusIE(http) to "https://www.rctiplus.com/programs/1259/kiko-untuk-lola/episode/22124/untuk-lola",
            RCTIPlusIE(http) to "https://www.rctiplus.com/live-event/123/fixture",
            RCTIPlusSeriesIE(http) to "https://www.rctiplus.com/programs/1259/kiko-untuk-lola",
            RCTIPlusSeriesIE(http) to "https://www.rctiplus.com/programs/1259/kiko-untuk-lola/episodes",
            RCTIPlusTVIE(http) to "https://www.rctiplus.com/tv/rcti",
            RCTIPlusTVIE(http) to "https://www.rctiplus.com/live-event",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(RCTIPlusTVIE(http).suitable("https://www.rctiplus.com/programs/1/x"))
    }

    @Test
    fun everyClassFailsTypedOnTheVisitorTokenWall() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            RCTIPlusIE(http) to "https://www.rctiplus.com/programs/1259/kiko-untuk-lola/episode/22124/untuk-lola",
            RCTIPlusSeriesIE(http) to "https://www.rctiplus.com/programs/1259/kiko-untuk-lola",
            RCTIPlusTVIE(http) to "https://www.rctiplus.com/tv/rcti",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.LoginRequired> { extractor.extract(url) }
            assertTrue(error.message!!.contains("visitor"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
