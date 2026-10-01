package com.anydownload.core.extract.pluralsight

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
 * The Pluralsight URL surface is a typed wall: the player payload needs an
 * authenticated subscription session, so no fixture can pass.
 */
class PluralsightIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            PluralsightIE(http) to "https://app.pluralsight.com/player?course=fixture&author=fixture&name=fixture",
            PluralsightCourseIE(http) to "https://www.pluralsight.com/courses/fixture-course",
            PluralsightCourseIE(http) to "https://app.pluralsight.com/library/courses/fixture-course",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(PluralsightIE(http).suitable("https://www.pluralsight.com/courses/fixture"))
    }

    @Test
    fun everyClassFailsTypedOnTheSessionWall() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            PluralsightIE(http) to "https://app.pluralsight.com/player?course=fixture",
            PluralsightCourseIE(http) to "https://www.pluralsight.com/courses/fixture-course",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.LoginRequired> { extractor.extract(url) }
            assertTrue(error.message!!.contains("session"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
