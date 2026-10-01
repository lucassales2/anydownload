package com.anydownload.core.extract.rtp

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the RTP subset. The site host is a real host name in
 * the URL surface; no auth hash, token, or media URL appears anywhere.
 */
class RtpIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchTheClass() {
        val cases = listOf(
            "http://www.rtp.pt/play/p405/e174042/paixoes-cruzadas",
            "https://www.rtp.pt/play/zigzag/p13166/e757904/25-curiosidades-25-de-abril",
            "https://www.rtp.pt/play/p14335/e877072/a-nossa-tarde/1364744",
        )
        for (url in cases) {
            assertTrue(RTPIE(http()).suitable(url), "RTP must match: $url")
        }
        assertFalse(RTPIE(http()).suitable("https://www.rtp.pt/play/p405"))
    }

    // ------------------------------------------------------------- typed wall

    @Test
    fun everyUrlFormFailsTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            RTPIE(http()).extract("http://www.rtp.pt/play/p405/e174042/paixoes-cruzadas")
        }
        assertFailsWith<ExtractionError.LoginRequired> {
            RTPIE(http()).extract("https://www.rtp.pt/play/p14335/e877072/a-nossa-tarde/1364744")
        }
    }
}
