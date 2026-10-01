package com.anydownload.core.extract.wrestleuniverse

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Wrestle Universe subset. The site host is a real
 * host name in the URL surface; no API key, device id, token, or media URL
 * appears anywhere.
 */
class WrestleUniverseIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<com.anydownload.core.extract.InfoExtractor, String>>(
            WrestleUniverseVODIE(http()) to "https://www.wrestle-universe.com/videos/fixturevideoid",
            WrestleUniverseVODIE(http()) to "https://www.wrestle-universe.com/en/videos/fixturevideoid",
            WrestleUniversePPVIE(http()) to "https://www.wrestle-universe.com/lives/fixtureliveid",
            WrestleUniversePPVIE(http()) to "https://www.wrestle-universe.com/ja/lives/fixtureliveid",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(WrestleUniverseVODIE(http()).suitable("https://www.wrestle-universe.com/lives/fixtureliveid"))
        assertFalse(WrestleUniversePPVIE(http()).suitable("https://www.wrestle-universe.com/videos/fixturevideoid"))
    }

    // ------------------------------------------------------------- typed wall

    @Test
    fun everyUrlFormFailsTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            WrestleUniverseVODIE(http()).extract("https://www.wrestle-universe.com/videos/fixturevideoid")
        }
        assertFailsWith<ExtractionError.LoginRequired> {
            WrestleUniversePPVIE(http()).extract("https://www.wrestle-universe.com/lives/fixtureliveid")
        }
    }
}
