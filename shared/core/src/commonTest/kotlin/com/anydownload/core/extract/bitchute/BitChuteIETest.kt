package com.anydownload.core.extract.bitchute

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.harness.CaseResult
import com.anydownload.core.extract.harness.Expect
import com.anydownload.core.extract.harness.ExtractorCase
import com.anydownload.core.extract.harness.ExtractorTestRun
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import com.anydownload.core.extract.harness.FixtureRoute
import com.anydownload.core.extract.harness.runCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the BitChute subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class BitChuteIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "UGlrF9o9b-Q"

    private fun mediaRoute() = FixtureRoute(
        urlPattern = "https://api.bitchute.com/api/beta/video/media",
        method = "POST",
        contentType = "application/json",
        body = """{"media_url": "https://media.example/hls/master.m3u8"}""",
    )

    private fun videoRoute() = FixtureRoute(
        urlPattern = "https://api.bitchute.com/api/beta/video",
        method = "POST",
        contentType = "application/json",
        body = """
            {"video_name": "Fixture BitChute", "description": "Fixture description",
             "thumbnail_url": "https://media.example/thumb.jpg", "duration": "00:16",
             "date_published": "2017-01-03T00:00:00Z", "view_count": 10,
             "state_id": "video",
             "channel": {"channel_id": "1VBwRfyNcKdX", "channel_name": "Fixture Channel"}}
        """.trimIndent(),
    )

    private fun channelRoute() = FixtureRoute(
        urlPattern = "https://api.bitchute.com/api/beta/channel",
        method = "POST",
        contentType = "application/json",
        body = """
            {"channel_name": "Fixture Channel", "channel_id": "1VBwRfyNcKdX",
             "profile_name": "Fixture Uploader", "profile_id": "I5NgtHZn9vPj"}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            BitChuteIE(http(transfer())) to "https://www.bitchute.com/video/$videoId/",
            BitChuteIE(http(transfer())) to "https://www.bitchute.com/embed/lbb5G1hjPhw/",
            BitChuteIE(http(transfer())) to "https://old.bitchute.com/video/$videoId/",
            BitChuteChannelIE(http(transfer())) to "https://www.bitchute.com/channel/bitchute/",
            BitChuteChannelIE(http(transfer())) to "https://www.bitchute.com/playlist/wV9Imujxasw9/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(BitChuteIE(http(transfer())).suitable("https://www.bitchute.com/channel/bitchute/"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun mediaApiYieldsHlsAndMetadata() = runTest {
        val url = "https://www.bitchute.com/video/$videoId/"
        val transfer = transfer(mediaRoute(), videoRoute(), channelRoute())
        val info = BitChuteIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture BitChute", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(16.0, info.duration)
        assertEquals(10L, info.viewCount)
        assertEquals("20170103", info.uploadDate)
        assertEquals("Fixture Channel", info.channel)
        assertEquals("1VBwRfyNcKdX", info.channelId)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    @Test
    fun channelListingFailsTyped() = runTest {
        assertFailsWith<ExtractionError.Unavailable> {
            BitChuteChannelIE(http(transfer())).extract("https://www.bitchute.com/channel/bitchute/")
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.bitchute.com/video/$videoId/"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture BitChute"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(mediaRoute(), videoRoute(), channelRoute()),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> BitChuteIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
