package com.anydownlod.core.postprocess

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * T-016 SponsorBlock client: synthetic segment JSON only. No real request is
 * made in the default tests; the live service is never contacted.
 */
class SponsorBlockClientTest {

    private fun client(body: String, statusCode: Int = 200): SponsorBlockClient = SponsorBlockClient(
        ExtractorHttp(
            FixtureHttpTransfer(
                listOf(
                    FixtureRoute(
                        urlPattern = "https://sponsor.ajay.app/api/skipSegments*",
                        statusCode = statusCode,
                        contentType = "application/json",
                        body = body,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun skipSegmentsParseAndOtherActionsAreIgnored() = runTest {
        val body = """
            [
              {"category": "sponsor", "actionType": "skip", "segment": [10.0, 20.5], "UUID": "fixture-1"},
              {"category": "intro", "actionType": "mute", "segment": [30.0, 35.0], "UUID": "fixture-2"},
              {"category": "outro", "actionType": "skip", "segment": [40.0, 50.0], "UUID": "fixture-3"}
            ]
        """.trimIndent()

        val result = assertIs<SponsorBlockResult.Segments>(client(body).fetch("abcdefghijk"))

        assertEquals(2, result.segments.size)
        assertEquals(SponsorSegment("sponsor", 10_000, 20_500), result.segments[0])
        assertEquals(SponsorSegment("outro", 40_000, 50_000), result.segments[1])
    }

    @Test
    fun anEmptyListMeansNoSegments() = runTest {
        assertIs<SponsorBlockResult.NoSegments>(client("[]").fetch("abcdefghijk"))
    }

    @Test
    fun aBlankVideoIdIsNotApplicable() = runTest {
        assertIs<SponsorBlockResult.NotApplicable>(client("[]").fetch(""))
    }

    @Test
    fun aServiceFailureIsUnavailableAndKeepsNoReasonSecrets() = runTest {
        val result = assertIs<SponsorBlockResult.Unavailable>(client("nope", statusCode = 404).fetch("abcdefghijk"))
        assertTrue(result.reason.isNotBlank())
    }

    @Test
    fun aMalformedBodyIsUnavailable() = runTest {
        assertIs<SponsorBlockResult.Unavailable>(client("not json").fetch("abcdefghijk"))
    }

    @Test
    fun theRequestCarriesTheReviewedCategories() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://sponsor.ajay.app/api/skipSegments*",
                    contentType = "application/json",
                    body = "[]",
                ),
            ),
        )
        SponsorBlockClient(ExtractorHttp(transfer)).fetch("abcdefghijk")

        val url = transfer.requests.single().url
        assertTrue(url.contains("videoID=abcdefghijk"))
        assertTrue(url.contains("sponsor"))
        assertTrue(url.contains("music_offtopic"))
    }
}
