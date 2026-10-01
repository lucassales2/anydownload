package com.anydownload.core.extract.hotstar

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
 * The Hotstar URL surface is a typed wall: the API needs user-token cookies
 * and device ids and requests Widevine DRM parameters, so no fixture can
 * pass.
 */
class HotStarIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            HotStarIE(http) to "https://www.hotstar.com/in/movies/nuvvu-naaku-nachav/1260009879",
            HotStarPrefixIE(http) to "hotstar:1000076273",
            HotStarPrefixIE(http) to "hotstar:movies:1260009879",
            HotStarSeriesIE(http) to "https://www.hotstar.com/in/tv/radhakrishn/1260000646",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(HotStarPrefixIE(http).suitable("hotstar:not-a-number"))
    }

    @Test
    fun everyClassFailsTypedOnTheTokenAndDrmWalls() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            HotStarIE(http) to "https://www.hotstar.com/in/movies/nuvvu-naaku-nachav/1260009879",
            HotStarPrefixIE(http) to "hotstar:movies:1260009879",
            HotStarSeriesIE(http) to "https://www.hotstar.com/in/tv/radhakrishn/1260000646",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.LoginRequired> { extractor.extract(url) }
            assertTrue(error.message!!.contains("DRM"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
