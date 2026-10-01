package com.anydownload.core.extract.go

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Go (TV Everywhere) subset. The site hosts are real
 * host names in the URL surface; no credential, software statement, or
 * media URL appears anywhere.
 */
class GoIETest {

    private fun transfer(): FixtureHttpTransfer = FixtureHttpTransfer(emptyList())

    private fun http(): ExtractorHttp = ExtractorHttp(transfer())

    private val episodeId = "4192c0e6-26e5-47a8-817b-ce8272b9e440"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf(
            "https://abc.com/episode/$episodeId/playlist/PL551127435",
            "https://www.freeform.com/episode/bda0eaf7-761a-4838-aa44-96f794000844/playlist/PL553044961",
            "https://disneynow.com/episode/21029660-ba06-4406-adb0-a9a78f6e265e/playlist/PL553044961",
            "https://fxnow.fxnetworks.com/episode/09f4fa6f-c293-469e-aebe-32c9ca5842a7/playlist/PL554408064",
            "https://www.nationalgeographic.com/tv/episode/ca694661-1186-41ae-8089-82f64d69b16d/playlist/PL554408064",
        )
        for (url in cases) {
            assertTrue(GoIE(http()).suitable(url), "Go must match: $url")
        }
        assertFalse(GoIE(http()).suitable("https://abc.com/video/not-a-uuid/playlist/1"))
        assertFalse(GoIE(http()).suitable("https://www.example.com/episode/$episodeId"))
    }

    // ------------------------------------------------------------- typed wall

    @Test
    fun everyUrlFormFailsTyped() = runTest {
        val url = "https://abc.com/episode/$episodeId/playlist/PL551127435"
        assertFailsWith<ExtractionError.LoginRequired> {
            GoIE(http()).extract(url)
        }
    }

}
