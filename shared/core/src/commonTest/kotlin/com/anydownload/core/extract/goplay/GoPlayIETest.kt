package com.anydownload.core.extract.goplay

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The PLAY URL surface is a typed wall: the long-form API needs an AWS
 * Cognito login bearer, so no fixture can pass.
 */
class GoPlayIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatch() {
        val extractor = GoPlayIE(http)
        assertTrue(
            extractor.suitable(
                "https://www.play.tv/video/de-slimste-mens-ter-wereld/de-slimste-mens-ter-wereld-s22/" +
                    "de-slimste-mens-ter-wereld-s22-aflevering-1",
            ),
        )
        assertTrue(extractor.suitable("https://www.play.tv/video/1917"))
        assertFalse(extractor.suitable("https://www.play.tv/"))
    }

    @Test
    fun apiFailsTypedOnTheLoginWall() = runTest {
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            GoPlayIE(http).extract("https://www.play.tv/video/1917")
        }
        assertTrue(error.message!!.contains("Cognito"))
    }
}
