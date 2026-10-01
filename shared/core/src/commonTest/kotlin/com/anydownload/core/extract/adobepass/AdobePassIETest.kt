package com.anydownload.core.extract.adobepass

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit cases for the translated `AdobePassIE` base. The class has no upstream
 * `_VALID_URL`, so there is no URL-form harness case; these cases cover the
 * two translated members with fake values only. No provider, credential,
 * MSO id, or endpoint is real.
 */
class AdobePassIETest {

    /** A concrete subclass so the abstract base's members can be called. */
    private class FixtureAdobePassIE(http: ExtractorHttp) : AdobePassIE(
        ieKey = "fixtureadobepass",
        http = http,
        validUrl = Regex("https://fixture\\.example/watch/(?<id>[a-z0-9]+)"),
    ) {
        override suspend fun extract(url: String): InfoDict = mvpdAuthRequired()
    }

    private fun base(): FixtureAdobePassIE =
        FixtureAdobePassIE(ExtractorHttp(FixtureHttpTransfer(emptyList())))

    @Test
    fun mvpdResourceMatchesTheUpstreamElementTreeShape() {
        assertEquals(
            "<rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss/\">" +
                "<channel><title>fixturemso</title><item><title>Fixture &amp; &lt;Title&gt;</title>" +
                "<guid>fixture-guid</guid>" +
                "<media:rating scheme=\"urn:v-chip\">TV-PG</media:rating>" +
                "</item></channel></rss>",
            base().mvpdResource("fixturemso", "Fixture & <Title>", "fixture-guid", "TV-PG"),
        )
    }

    @Test
    fun mvpdResourceSelfClosesAnEmptyRating() {
        val expectedRating = "<media:rating scheme=\"urn:v-chip\" />"
        val nullRating = base().mvpdResource("fixturemso", "Fixture Title", "fixture-guid", null)
        val emptyRating = base().mvpdResource("fixturemso", "Fixture Title", "fixture-guid", "")
        assertTrue(nullRating.contains(expectedRating), "null rating must self-close: $nullRating")
        assertTrue(emptyRating.contains(expectedRating), "empty rating must self-close: $emptyRating")
    }

    @Test
    fun mvpdAuthIsATypedLoginWallWithNoSecretRequest() {
        val error = assertFailsWith<ExtractionError.LoginRequired> { base().mvpdAuthRequired() }
        assertTrue(
            error.message!!.contains("TV provider"),
            "message must name the TV provider wall: ${error.message}",
        )
    }

    @Test
    fun theBaseItselfDeclaresNoPublicUrlForm() {
        // The fixture subclass matches only its own fixture form; the base
        // class adds no URL pattern of its own, mirroring upstream.
        assertTrue(base().suitable("https://fixture.example/watch/fixture1"))
        assertFalse(base().suitable("https://auth.example/adobe-services/authenticate"))
        assertFalse(base().suitable("https://api.example/api/v1/authenticate"))
    }
}
