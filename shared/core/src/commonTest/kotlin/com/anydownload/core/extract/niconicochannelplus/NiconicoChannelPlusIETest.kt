package com.anydownload.core.extract.niconicochannelplus

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
 * Fixture cases for the Niconico Channel Plus subset. Ids, titles, and media
 * paths are synthesized; media lives on `media.example`, and no cookie or
 * token appears.
 */
class NiconicoChannelPlusIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val contentCode = "smsDd8EdFLcVZk9yyAhD6H7H"

    private val channelsRoute = FixtureRoute(
        urlPattern = "https://nfc-api.nicochannel.jp/fc/content_providers/channels",
        contentType = "application/json",
        body = """
            {"data": {"content_providers": [
               {"id": "site-1", "domain": "https://nicochannel.jp/kaorin"},
               {"id": "site-1", "domain": "https://nicochannel.jp/testman"}]}}
        """.trimIndent(),
    )

    private val videoPageRoute = FixtureRoute(
        urlPattern = "https://nfc-api.nicochannel.jp/fc/video_pages/$contentCode",
        contentType = "application/json",
        body = """
            {"data": {"video_page": {
              "type": "vod", "title": "Fixture Video", "description": "Fixture description",
              "released_at": "2022-01-05T00:00:00Z", "thumbnail_url": "https://media.example/thumb.jpg",
              "video_stream": {"authenticated_url": "https://media.example/hls/{session_id}/master.m3u8"},
              "active_video_filename": {"length": 4097},
              "video_aggregate_info": {"total_views": 100}}}}
        """.trimIndent(),
    )

    private val sessionRoute = FixtureRoute(
        urlPattern = "https://nfc-api.nicochannel.jp/fc/video_pages/$contentCode/session_ids",
        method = "POST",
        contentType = "application/json",
        body = """{"data": {"session_id": "fake_session"}}""",
    )

    private val baseInfoRoute = FixtureRoute(
        urlPattern = "https://nfc-api.nicochannel.jp/fc/fanclub_sites/site-1/page_base_info",
        contentType = "application/json",
        body = """{"data": {"fanclub_site": {"fanclub_site_name": "Fixture Channel"}}}""",
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NiconicoChannelPlusIE(http(transfer())) to "https://nicochannel.jp/kaorin/video/$contentCode",
            NiconicoChannelPlusIE(http(transfer())) to "https://nicochannel.jp/kaorin/live/$contentCode",
            NiconicoChannelPlusChannelVideosIE(http(transfer())) to "https://nicochannel.jp/testman/videos",
            NiconicoChannelPlusChannelLivesIE(http(transfer())) to "https://nicochannel.jp/testman/lives",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NiconicoChannelPlusChannelLivesIE(http(transfer())).suitable("https://nicochannel.jp/testman/videos"))
    }

    // ----------------------------------------------------------------- video

    @Test
    fun vodPageYieldsTheSessionSignedHls() = runTest {
        val url = "https://nicochannel.jp/kaorin/video/$contentCode"
        val transfer = transfer(channelsRoute, videoPageRoute, sessionRoute, baseInfoRoute)
        val info = NiconicoChannelPlusIE(http(transfer)).extract(url)
        assertEquals(contentCode, info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Fixture Channel", info.channel)
        assertEquals("kaorin", info.channelId)
        assertEquals(100L, info.viewCount)
        assertEquals("https://media.example/hls/fake_session/master.m3u8", info.formats.single().url)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun upcomingLiveFailsTyped() = runTest {
        val url = "https://nicochannel.jp/kaorin/live/$contentCode"
        val upcomingRoute = FixtureRoute(
            urlPattern = "https://nfc-api.nicochannel.jp/fc/video_pages/$contentCode",
            contentType = "application/json",
            body = """
                {"data": {"video_page": {"type": "live", "title": "Upcoming",
                  "live_scheduled_start_at": "2022-01-05T00:00:00Z"}}}
            """.trimIndent(),
        )
        val transfer = transfer(channelsRoute, upcomingRoute, sessionRoute, baseInfoRoute)
        assertFailsWith<ExtractionError.NotYetAvailable> {
            NiconicoChannelPlusIE(http(transfer)).extract(url)
        }
    }

    // --------------------------------------------------------------- channel

    @Test
    fun channelVideosListYieldsEntries() = runTest {
        val url = "https://nicochannel.jp/testman/videos"
        val transfer = transfer(
            channelsRoute,
            baseInfoRoute,
            FixtureRoute(
                urlPattern = "https://nfc-api.nicochannel.jp/fc/fanclub_sites/site-1/video_pages?page=1&per_page=12",
                contentType = "application/json",
                body = """
                    {"data": {"video_pages": {"list": [
                       {"content_code": "$contentCode", "title": "Fixture Video"}]}}}
                """.trimIndent(),
            ),
        )
        val info = NiconicoChannelPlusChannelVideosIE(http(transfer)).extract(url)
        assertEquals("testman-videos", info.id)
        assertEquals("Fixture Channel-videos", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://nicochannel.jp/testman/video/$contentCode", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun vodIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://nicochannel.jp/kaorin/video/$contentCode"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(contentCode),
                "title" to Expect.Value("Fixture Video"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(channelsRoute, videoPageRoute, sessionRoute, baseInfoRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NiconicoChannelPlusIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
