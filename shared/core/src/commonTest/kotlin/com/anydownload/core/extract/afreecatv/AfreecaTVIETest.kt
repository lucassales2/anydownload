package com.anydownload.core.extract.afreecatv

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
 * Fixture cases for the SoopLive subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class AfreecaTVIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            AfreecaTVIE(http(transfer())) to "https://vod.sooplive.com/player/192805325",
            AfreecaTVIE(http(transfer())) to "https://vod.sooplive.com/PLAYER/STATION/20515605",
            AfreecaTVCatchStoryIE(http(transfer())) to "https://vod.sooplive.com/player/103247/catchstory",
            AfreecaTVLiveIE(http(transfer())) to "https://play.sooplive.com/pyh3646/237852185",
            AfreecaTVLiveIE(http(transfer())) to "https://play.sooplive.com/pyh3646",
            AfreecaTVUserIE(http(transfer())) to "https://www.sooplive.com/station/devil0108/vod/review",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(AfreecaTVIE(http(transfer())).suitable("https://play.sooplive.com/x"))
    }

    // ------------------------------------------------------------------ VOD

    @Test
    fun vodApiYieldsASinglePartWithFormats() = runTest {
        val url = "https://vod.sooplive.com/player/192805325"
        val api = "https://api.m.sooplive.com/station/video/a/view"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = api,
                method = "POST",
                requestBodyContains = "nTitleNo=192805325",
                contentType = "application/json",
                body = """
                    {"data": {"title": "Fixture VOD", "writer_nick": "Fixture Streamer",
                              "bj_id": "fixturebj", "total_file_duration": 10869000,
                              "thumb": "https://media.example/thumb.php",
                              "files": [{"file_info_key": "20260414_1B44E53B_293230967_1",
                                         "file": "https://media.example/hls/master.m3u8",
                                         "duration": 10869000}]}}
                """.trimIndent(),
            ),
        )
        val info = AfreecaTVIE(http(transfer)).extract(url)
        assertEquals("20260414_1B44E53B_293230967_1", info.id)
        assertEquals("Fixture VOD", info.title)
        assertEquals("Fixture Streamer", info.uploader)
        assertEquals(10869.0, info.duration)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun vodApiMultiPartBecomesMediaItems() = runTest {
        val url = "https://vod.sooplive.com/player/192805325"
        val api = "https://api.m.sooplive.com/station/video/a/view"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = api,
                method = "POST",
                contentType = "application/json",
                body = """
                    {"data": {"title": "Fixture VOD",
                              "files": [{"file_info_key": "part-1", "file": "https://media.example/part1.mp4"},
                                        {"file_info_key": "part-2", "file": "https://media.example/part2.mp4"}]}}
                """.trimIndent(),
            ),
        )
        val info = AfreecaTVIE(http(transfer)).extract(url)
        assertEquals(2, info.media.size)
        assertEquals("Fixture VOD (part 1)", info.media[0].title)
        assertEquals("https://media.example/part2.mp4", info.media[1].formats.single().url)
    }

    @Test
    fun privateAndSubscriberVodsFailTyped() = runTest {
        val url = "https://vod.sooplive.com/player/192805325"
        val api = "https://api.m.sooplive.com/station/video/a/view"
        val privateTransfer = transfer(
            FixtureRoute(urlPattern = api, method = "POST", contentType = "application/json", body = """{"data": {"code": -6205}}"""),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            AfreecaTVIE(http(privateTransfer)).extract(url)
        }

        val subscriberTransfer = transfer(
            FixtureRoute(
                urlPattern = api,
                method = "POST",
                contentType = "application/json",
                body = """{"data": {"title": "Sub", "sub_upload_type": "sub", "files": []}}""",
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            AfreecaTVIE(http(subscriberTransfer)).extract(url)
        }
    }

    // ------------------------------------------------------------ catchstory

    @Test
    fun catchStoryApiYieldsMediaItems() = runTest {
        val url = "https://vod.sooplive.com/player/103247/catchstory"
        val api = "https://api.m.sooplive.com/catchstory/a/view?aStoryListIdx=&nStoryIdx=103247"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = api,
                contentType = "application/json",
                body = """
                    {"data": [{"story_type": "catch", "catch_list": [
                       {"title": "Fixture Catch", "writer_nick": "Fixture",
                        "files": [{"file_info_key": "catch-1", "file": "https://media.example/catch.mp4",
                                   "duration": 30000}]}]}]}
                """.trimIndent(),
            ),
        )
        val info = AfreecaTVCatchStoryIE(http(transfer)).extract(url)
        assertEquals(1, info.media.size)
        assertEquals("Fixture Catch", info.media.single().title)
        assertEquals(30.0, info.media.single().duration)
    }

    // ----------------------------------------------------------------- live

    @Test
    fun liveApiYieldsTheAssignedStream() = runTest {
        val url = "https://play.sooplive.com/pyh3646/237852185"
        val liveApi = "https://live.sooplive.com/afreeca/player_live_api.php"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = liveApi,
                method = "POST",
                requestBodyContains = "bid=pyh3646",
                contentType = "application/json",
                body = """
                    {"CHANNEL": {"BJID": "pyh3646", "BNO": "237852185", "TITLE": "Fixture Live",
                                 "BJNICK": "Fixture Streamer", "CDN": ["gcp_cdn"]}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = liveApi,
                method = "POST",
                requestBodyContains = "bno=237852185",
                contentType = "application/json",
                body = """{"CHANNEL": {"AID": "fake_aid"}}""",
            ),
            FixtureRoute(
                urlPattern = "https://livestream-manager.sooplive.com/broad_stream_assign.html*",
                contentType = "application/json",
                body = """{"view_url": "https://media.example/live/master.m3u8"}""",
            ),
        )
        val info = AfreecaTVLiveIE(http(transfer)).extract(url)
        assertEquals("237852185", info.id)
        assertEquals("Fixture Live", info.title)
        assertEquals(true, info.isLive)
        assertEquals("https://media.example/live/master.m3u8?aid=fake_aid", info.formats.single().url)
    }

    @Test
    fun endedLiveFailsTyped() = runTest {
        val url = "https://play.sooplive.com/pyh3646"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://live.sooplive.com/afreeca/player_live_api.php",
                method = "POST",
                contentType = "application/json",
                body = """{"CHANNEL": {"RESULT": 0}}""",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            AfreecaTVLiveIE(http(transfer)).extract(url)
        }
    }

    // ------------------------------------------------------------------ user

    @Test
    fun stationPagesListVodEntries() = runTest {
        val url = "https://www.sooplive.com/station/devil0108/vod/review"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://chapi.sooplive.com/api/devil0108/vods/review?page=1&per_page=60&orderby=reg_date",
                contentType = "application/json",
                body = """{"data": [{"title_no": 1, "title_name": "Fixture One"}]}""",
            ),
            FixtureRoute(
                urlPattern = "https://chapi.sooplive.com/api/devil0108/vods/review?page=2&per_page=60&orderby=reg_date",
                contentType = "application/json",
                body = """{"data": []}""",
            ),
        )
        val info = AfreecaTVUserIE(http(transfer)).extract(url)
        assertEquals("devil0108", info.id)
        assertEquals("devil0108 - review", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://vod.sooplive.com/player/1/", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun vodApiIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://vod.sooplive.com/player/192805325"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("part-1"),
                "title" to Expect.Value("Fixture VOD"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api.m.sooplive.com/station/video/a/view",
                    method = "POST",
                    contentType = "application/json",
                    body = """
                        {"data": {"title": "Fixture VOD",
                                  "files": [{"file_info_key": "part-1",
                                             "file": "https://media.example/hls/master.m3u8"}]}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> AfreecaTVIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
