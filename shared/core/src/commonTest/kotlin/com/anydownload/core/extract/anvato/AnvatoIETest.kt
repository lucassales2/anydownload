package com.anydownload.core.extract.anvato

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Anvato URL surface is a typed wall: no fixture can pass the AES
 * auth header, so the case proves matching plus the typed failure only.
 */
class AnvatoIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val extractor = AnvatoIE(http)
        assertTrue(extractor.suitable("anvato:fake_access_key:12345"))
        assertTrue(extractor.suitable("anvato:fake_mcp:987654"))
        assertFalse(extractor.suitable("https://anvato.example/watch/12345"))
        assertFalse(extractor.suitable("anvato:fake_key:not-a-number"))
    }

    @Test
    fun apiFailsTypedOnTheAesAuthHeader() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            AnvatoIE(http).extract("anvato:fake_access_key:12345")
        }
        assertTrue(error.message!!.contains("AES-encrypted"))
    }
}
