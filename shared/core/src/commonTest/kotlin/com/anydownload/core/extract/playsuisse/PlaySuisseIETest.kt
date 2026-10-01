package com.anydownload.core.extract.playsuisse

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Play Suisse subset. The asset query and streams need
 * the OAuth password token, so the extractor matches and fails typed; no
 * cookie, token, or signed URL appears anywhere.
 */
class PlaySuisseIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val extractor = PlaySuisseIE(http())
        val cases = listOf(
            "https://www.playsuisse.ch/watch/763211/0",
            "https://www.playsuisse.ch/watch/763182?episodeId=763211",
            "https://www.playsuisse.ch/detail/2573198",
        )
        for (url in cases) {
            assertTrue(extractor.suitable(url), "PlaySuisse must match: $url")
        }
        assertFalse(extractor.suitable("https://www.example.com/watch/763211/0"))
    }

    @Test
    fun watchPageFailsTypedAsLoginRequired() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            PlaySuisseIE(http()).extract("https://www.playsuisse.ch/watch/763182?episodeId=763211")
        }
    }
}
