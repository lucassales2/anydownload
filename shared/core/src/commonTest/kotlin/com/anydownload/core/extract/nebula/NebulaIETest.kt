package com.anydownload.core.extract.nebula

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
 * The Nebula URL surface is a typed wall: the content API needs a guest
 * token, so no fixture can pass. The cases prove matching plus the failure.
 */
class NebulaIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NebulaIE(http) to "https://nebula.tv/videos/fixture-video",
            NebulaIE(http) to "https://watchnebula.com/videos/fixture-video",
            NebulaIE(http) to "https://beta.nebula.tv/videos/fixture-video",
            NebulaClassIE(http) to "https://nebula.tv/fixture-class/fixture-episode",
            NebulaSubscriptionsIE(http) to "https://nebula.tv/myshows",
            NebulaSubscriptionsIE(http) to "https://nebula.tv/library/latest-videos",
            NebulaChannelIE(http) to "https://nebula.tv/fixture-channel",
            NebulaSeasonIE(http) to "https://nebula.tv/fixture-series/season/2",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NebulaSubscriptionsIE(http).suitable("https://nebula.tv/videos/fixture-video"))
        assertFalse(NebulaIE(http).suitable("https://nebula.tv/fixture-channel"))
    }

    @Test
    fun everyClassFailsTypedOnTheTokenFlow() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NebulaIE(http) to "https://nebula.tv/videos/fixture-video",
            NebulaClassIE(http) to "https://nebula.tv/fixture-class/fixture-episode",
            NebulaSubscriptionsIE(http) to "https://nebula.tv/myshows",
            NebulaChannelIE(http) to "https://nebula.tv/fixture-channel",
            NebulaSeasonIE(http) to "https://nebula.tv/fixture-series/season/2",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.Unavailable> { extractor.extract(url) }
            assertTrue(error.message!!.contains("token"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
