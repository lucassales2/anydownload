package com.anydownload.core.extract.aenetworks

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
 * The A&E URL surface is a typed wall: the ThePlatform SMIL URLs need an
 * HMAC signature with embedded secrets, so no fixture can pass.
 */
class AENetworksIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            AENetworksIE(http) to "https://www.history.com/shows/fixture/season-1/episode-1",
            AENetworksIE(http) to "https://www.aetv.com/movies/fixture-movie",
            AENetworksIE(http) to "https://www.mylifetime.com/shows/fixture/videos/fixture-clip",
            AENetworksCollectionIE(http) to "https://play.aetv.com/list/fixture-list",
            AENetworksShowIE(http) to "https://www.history.com/shows/fixture-show",
            HistoryTopicIE(http) to "https://www.history.com/topics/fixture/fixture-video",
            HistoryPlayerIE(http) to "https://www.history.com/player/12345",
            BiographyIE(http) to "https://www.biography.com/video/fixture-video",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(HistoryPlayerIE(http).suitable("https://www.history.com/topics/x/y-video"))
    }

    @Test
    fun everyClassFailsTypedOnTheSigningWall() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            AENetworksIE(http) to "https://www.history.com/shows/fixture/season-1/episode-1",
            AENetworksCollectionIE(http) to "https://play.aetv.com/list/fixture-list",
            AENetworksShowIE(http) to "https://www.history.com/shows/fixture-show",
            HistoryTopicIE(http) to "https://www.history.com/topics/fixture/fixture-video",
            HistoryPlayerIE(http) to "https://www.history.com/player/12345",
            BiographyIE(http) to "https://www.biography.com/video/fixture-video",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.Unavailable> { extractor.extract(url) }
            assertTrue(error.message!!.contains("HMAC"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
