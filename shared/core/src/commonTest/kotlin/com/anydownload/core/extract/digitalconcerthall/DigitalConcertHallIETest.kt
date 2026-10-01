package com.anydownload.core.extract.digitalconcerthall

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Digital Concert Hall subset. The site host is a real
 * host name in the URL surface; no client secret, token, or media URL
 * appears anywhere.
 */
class DigitalConcertHallIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchTheClass() {
        val cases = listOf(
            "https://www.digitalconcerthall.com/en/concert/53201",
            "https://www.digitalconcerthall.com/en/concert/53785",
            "https://www.digitalconcerthall.com/en/film/388",
            "https://www.digitalconcerthall.com/en/work/53785-1",
        )
        for (url in cases) {
            assertTrue(DigitalConcertHallIE(http()).suitable(url), "DCH must match: $url")
        }
        assertFalse(DigitalConcertHallIE(http()).suitable("https://www.digitalconcerthall.com/en/other/1"))
    }

    // ------------------------------------------------------------- typed wall

    @Test
    fun everyUrlFormFailsTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            DigitalConcertHallIE(http()).extract("https://www.digitalconcerthall.com/en/concert/53201")
        }
        assertFailsWith<ExtractionError.LoginRequired> {
            DigitalConcertHallIE(http()).extract("https://www.digitalconcerthall.com/en/film/388")
        }
    }
}
