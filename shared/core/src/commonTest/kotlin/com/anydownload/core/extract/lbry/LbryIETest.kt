package com.anydownload.core.extract.lbry

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the LBRY subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class LbryIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val claimId = "17f983b61f53091fb8ea58a9c56804e4ff8cff4d"
    private val proxy = "https://api.lbry.tv/api/v1/proxy"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            LBRYIE(http(transfer())) to "https://lbry.tv/@Mantega:1/First-day-LBRY:1",
            LBRYIE(http(transfer())) to "lbry://@lbry#3f/odysee#7",
            LBRYChannelIE(http(transfer())) to "https://lbry.tv/@LBRYFoundation:0",
            LBRYPlaylistIE(http(transfer())) to "https://odysee.com/\$/playlist/ffef782f27486f0ac138bde8777f72ebdd0548c2",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(LBRYChannelIE(http(transfer())).suitable("https://odysee.com/\$/playlist/ffef782f"))
    }

    // ---------------------------------------------------------------- stream

    @Test
    fun streamResolveAndGetYieldTheFormat() = runTest {
        val url = "https://lbry.tv/@Mantega:1/First-day-LBRY:1"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = proxy,
                method = "POST",
                requestBodyContains = "\"resolve\"",
                contentType = "application/json",
                body = """
                    {"result": {"lbry://@Mantega#1/First-day-LBRY#1": {
                      "claim_id": "$claimId", "value_type": "stream",
                      "value": {"stream_type": "video", "title": "Fixture Stream",
                                "description": "Fixture description",
                                "thumbnail": {"url": "https://media.example/thumb.png"},
                                "video": {"width": 1280, "height": 720},
                                "source": {"size": 1000},
                                "video_duration": 346},
                      "signing_channel": {"claim_id": "chan-1",
                        "value": {"title": "Fixture Channel"}}}}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = proxy,
                method = "POST",
                requestBodyContains = "\"get\"",
                contentType = "application/json",
                body = """{"result": {"streaming_url": "https://media.example/hls/master.m3u8"}}""",
            ),
        )
        val info = LBRYIE(http(transfer)).extract(url)
        assertEquals(claimId, info.id)
        assertEquals("Fixture Stream", info.title)
        assertEquals("Fixture Channel", info.channel)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // --------------------------------------------------------------- channel

    @Test
    fun channelSearchYieldsEntries() = runTest {
        val url = "https://lbry.tv/@LBRYFoundation:0"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = proxy,
                method = "POST",
                requestBodyContains = "\"resolve\"",
                contentType = "application/json",
                body = """
                    {"result": {"lbry://@LBRYFoundation#0": {
                      "claim_id": "0ed629d2b9c601300cacf7eabe9da0be79010212", "value_type": "channel",
                      "value": {"title": "The LBRY Foundation"}}}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = proxy,
                method = "POST",
                requestBodyContains = "\"claim_search\"",
                contentType = "application/json",
                body = """
                    {"result": {"items": [{"name": "fixture-claim", "claim_id": "abc123",
                      "value": {"title": "Fixture Video"}}]}}
                """.trimIndent(),
            ),
        )
        val info = LBRYChannelIE(http(transfer)).extract(url)
        assertEquals("0ed629d2b9c601300cacf7eabe9da0be79010212", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://lbry.tv/fixture-claim:abc123", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun streamIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://lbry.tv/@Mantega:1/First-day-LBRY:1"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(claimId),
                "title" to Expect.Value("Fixture Stream"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = proxy,
                    method = "POST",
                    requestBodyContains = "\"resolve\"",
                    contentType = "application/json",
                    body = """
                        {"result": {"lbry://@Mantega#1/First-day-LBRY#1": {
                          "claim_id": "$claimId", "value_type": "stream",
                          "value": {"stream_type": "video", "title": "Fixture Stream"}}}}
                    """.trimIndent(),
                ),
                FixtureRoute(
                    urlPattern = proxy,
                    method = "POST",
                    requestBodyContains = "\"get\"",
                    contentType = "application/json",
                    body = """{"result": {"streaming_url": "https://media.example/hls/master.m3u8"}}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> LBRYIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
