package com.anydownload.core.extract.viewlift

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
 * Fixture cases for the ViewLift subset. The site hosts are real host names
 * in the URL surface; no cookie, token, or media URL appears anywhere.
 */
class ViewLiftIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    private val filmId = "74849a00-85a9-11e1-9660-123139220831"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ViewLiftEmbedIE(http()) to "http://embed.snagfilms.com/embed/player?filmId=$filmId&w=500",
            ViewLiftEmbedIE(http()) to "http://www.snagfilms.com/embed/player?filmId=$filmId",
            ViewLiftIE(http()) to "http://www.snagfilms.com/films/title/lost_for_life",
            ViewLiftIE(http()) to "https://www.hoichoi.tv/show/fixture-show",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ViewLiftEmbedIE(http()).suitable("https://www.snagfilms.com/films/title/lost_for_life"))
    }

    // ------------------------------------------------------------- typed wall

    @Test
    fun everyUrlFormFailsTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            ViewLiftEmbedIE(http()).extract("http://embed.snagfilms.com/embed/player?filmId=$filmId&w=500")
        }
        assertFailsWith<ExtractionError.LoginRequired> {
            ViewLiftIE(http()).extract("http://www.snagfilms.com/films/title/lost_for_life")
        }
    }
}
