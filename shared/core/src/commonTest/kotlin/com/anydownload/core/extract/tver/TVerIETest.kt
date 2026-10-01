package com.anydownload.core.extract.tver

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
 * The TVer URL surface is a typed wall: the platform API needs a browser
 * session, so no fixture can pass.
 */
class TVerIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            TVerIE(http) to "https://tver.jp/episodes/epc1hdugbk",
            TVerIE(http) to "https://tver.jp/series/srtxft431v",
            TVerIE(http) to "https://tver.jp/corner/f0103888",
            TVerOlympicIE(http) to "https://tver.jp/olympic/milanocortina2026/video/play/fixture123",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(TVerIE(http).suitable("https://tver.jp/"))
    }

    @Test
    fun everyClassFailsTypedOnTheSessionWall() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            TVerIE(http) to "https://tver.jp/episodes/epc1hdugbk",
            TVerOlympicIE(http) to "https://tver.jp/olympic/milanocortina2026/video/play/fixture123",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.LoginRequired> { extractor.extract(url) }
            assertTrue(error.message!!.contains("browser session"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
