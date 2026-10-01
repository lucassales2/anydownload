package com.anydownload.core.extract.weverse

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Weverse URL surface. Every URL form matches and
 * fails typed as the account wall; no request is made and no token or media
 * URL appears.
 */
class WeverseIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<String, String>>(
            WeverseIE.IE_KEY to "https://weverse.io/billlie/live/0-107323480",
            WeverseMediaIE.IE_KEY to "https://weverse.io/billlie/media/1-107323480",
            WeverseMomentIE.IE_KEY to "https://weverse.io/billlie/moment/0123abcdef/post/2-107323480",
            WeverseLiveTabIE.IE_KEY to "https://weverse.io/billlie/live",
            WeverseMediaTabIE.IE_KEY to "https://weverse.io/billlie/media/all",
            WeverseLiveIE.IE_KEY to "https://weverse.io/billlie",
        )
        val extractors = mapOf(
            WeverseIE.IE_KEY to WeverseIE(http()),
            WeverseMediaIE.IE_KEY to WeverseMediaIE(http()),
            WeverseMomentIE.IE_KEY to WeverseMomentIE(http()),
            WeverseLiveTabIE.IE_KEY to WeverseLiveTabIE(http()),
            WeverseMediaTabIE.IE_KEY to WeverseMediaTabIE(http()),
            WeverseLiveIE.IE_KEY to WeverseLiveIE(http()),
        )
        for ((key, url) in cases) {
            val extractor = extractors.getValue(key)
            assertTrue(extractor.suitable(url), "$key must match: $url")
        }
        assertFalse(WeverseMediaIE(http()).suitable("https://weverse.io/billlie/live/0-1"))
        assertFalse(WeverseIE(http()).suitable("https://example.com/billlie/live/0-1"))
    }

    @Test
    fun everyUrlFormFailsTypedAsTheAccountWall() = runTest {
        val urls = mapOf(
            WeverseIE.IE_KEY to "https://weverse.io/billlie/live/0-107323480",
            WeverseMediaIE.IE_KEY to "https://weverse.io/billlie/media/1-107323480",
            WeverseMomentIE.IE_KEY to "https://weverse.io/billlie/moment/0123abcdef/post/2-107323480",
            WeverseLiveTabIE.IE_KEY to "https://weverse.io/billlie/live",
            WeverseMediaTabIE.IE_KEY to "https://weverse.io/billlie/media",
            WeverseLiveIE.IE_KEY to "https://weverse.io/billlie",
        )
        val extractors = listOf(
            WeverseIE(http()),
            WeverseMediaIE(http()),
            WeverseMomentIE(http()),
            WeverseLiveTabIE(http()),
            WeverseMediaTabIE(http()),
            WeverseLiveIE(http()),
        )
        for (extractor in extractors) {
            val url = urls.getValue(extractor.ieKey)
            val error = assertFailsWith<ExtractionError.LoginRequired>("${extractor.ieKey}: $url") {
                extractor.extract(url)
            }
            assertTrue(
                error.message!!.contains("account token cookie"),
                "${extractor.ieKey}: the reason must name the account wall",
            )
        }
    }

    @Test
    fun theReasonIsOneSentence() {
        assertEquals(
            "The Weverse API needs a Weverse account token cookie (we2_access_token/" +
                "we2_refresh_token) and HMAC-signed requests; this account API is not translated.",
            WeverseBaseIE.ACCOUNT_WALL_MESSAGE,
        )
    }
}
