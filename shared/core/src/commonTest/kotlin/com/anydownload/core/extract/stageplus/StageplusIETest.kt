package com.anydownload.core.extract.stageplus

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the STAGE+ subset. The concert query and streams need the
 * account token, so the extractor matches and fails typed; no cookie, token,
 * or signed URL appears anywhere.
 */
class StageplusIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val extractor = StagePlusVODConcertIE(http())
        val cases = listOf(
            "https://www.stage-plus.com/video/vod_concert_APNM8GRFDPHMASJKBSPJACG",
            "https://stage-plus.com/video/vod_concert_fixture",
        )
        for (url in cases) {
            assertTrue(extractor.suitable(url), "StagePlus must match: $url")
        }
        assertFalse(extractor.suitable("https://www.example.com/video/vod_concert_fixture"))
        assertFalse(extractor.suitable("https://www.stage-plus.com/video/other_fixture"))
    }

    @Test
    fun concertFailsTypedAsLoginRequired() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            StagePlusVODConcertIE(http()).extract(
                "https://www.stage-plus.com/video/vod_concert_APNM8GRFDPHMASJKBSPJACG",
            )
        }
    }
}
