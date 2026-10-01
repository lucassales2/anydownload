package com.anydownload.core.extract.cda

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
 * The CDA URL surface is a typed wall: the app API needs an OAuth bearer and
 * the web player file fields are encrypted, so no fixture can pass.
 */
class CDAIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            CDAIE(http) to "http://www.cda.pl/video/5749950c",
            CDAIE(http) to "http://ebd.cda.pl/0x0/5749950c",
            CDAIE(http) to "https://m.cda.pl/video/617297677",
            CDAFolderIE(http) to "https://www.cda.pl/fixture-channel/folder/12345",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(CDAFolderIE(http).suitable("http://www.cda.pl/video/5749950c"))
    }

    @Test
    fun everyClassFailsTypedOnTheTokenAndCryptoWalls() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            CDAIE(http) to "http://www.cda.pl/video/5749950c",
            CDAFolderIE(http) to "https://www.cda.pl/fixture-channel/folder/12345",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.LoginRequired> { extractor.extract(url) }
            assertTrue(error.message!!.contains("OAuth"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
