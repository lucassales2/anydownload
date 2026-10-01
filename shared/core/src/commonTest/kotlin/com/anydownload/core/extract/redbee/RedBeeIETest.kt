package com.anydownload.core.extract.redbee

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Red Bee subset. The site hosts are real host names
 * in the URL surface; no device id, token, or media URL appears anywhere.
 */
class RedBeeIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        assertTrue(
            ParliamentLiveUKIE(http()).suitable(
                "http://parliamentlive.tv/Event/Index/c1e9d44d-fd6c-4263-b50f-97ed26cc998b",
            ),
        )
        assertTrue(
            ParliamentLiveUKIE(http()).suitable(
                "https://parliamentlive.tv/event/index/3f24936f-130f-40bf-9a5d-b3d6479da6a4",
            ),
        )
        assertTrue(
            RTBFIE(http()).suitable("https://www.rtbf.be/video/detail_fixture?id=1921274"),
        )
        assertTrue(
            RTBFIE(http()).suitable(
                "http://www.rtbf.be/ouftivi/heros/detail_fixture?id=1097&videoId=2057442",
            ),
        )
        assertTrue(
            RTBFIE(http()).suitable("https://www.rtbf.be/auvio/detail_fixture?id=1921274"),
        )
        assertFalse(ParliamentLiveUKIE(http()).suitable("https://www.rtbf.be/video/detail_fixture?id=1921274"))
        assertFalse(RTBFIE(http()).suitable("https://www.rtbf.be/video/detail_fixture"))
    }

    // ------------------------------------------------------------- typed wall

    @Test
    fun everyUrlFormFailsTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            ParliamentLiveUKIE(http()).extract(
                "http://parliamentlive.tv/Event/Index/c1e9d44d-fd6c-4263-b50f-97ed26cc998b",
            )
        }
        assertFailsWith<ExtractionError.LoginRequired> {
            RTBFIE(http()).extract("https://www.rtbf.be/video/detail_fixture?id=1921274")
        }
    }
}
