package com.anydownload.core.extract.udemy

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
 * The Udemy URL surface is a typed wall: lecture media and subscriber
 * curriculum items need an authenticated session, so no fixture can pass.
 */
class UdemyIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            UdemyIE(http) to "https://www.udemy.com/java-tutorial/#/lecture/172757",
            UdemyIE(http) to "https://www.udemy.com/lecture/view/?lectureId=160614",
            UdemyIE(http) to "https://www.udemy.com/java-tutorial/learn/v4/t/lecture/160614",
            UdemyCourseIE(http) to "https://www.udemy.com/java-tutorial/",
            UdemyCourseIE(http) to "https://wipro.udemy.com/java-tutorial/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(UdemyCourseIE(http).suitable("https://www.udemy.com/java-tutorial/#/lecture/172757"))
    }

    @Test
    fun everyClassFailsTypedOnTheSessionWall() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            UdemyIE(http) to "https://www.udemy.com/java-tutorial/#/lecture/172757",
            UdemyCourseIE(http) to "https://www.udemy.com/java-tutorial/",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.LoginRequired> { extractor.extract(url) }
            assertTrue(error.message!!.contains("authenticated session"), "${extractor.ieKey}")
        }
    }
}
