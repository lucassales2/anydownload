package com.anydownload.core.extract.openrec

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
 * Fixture cases for the OpenRec/mellow-fan subset. Every id, host, and media
 * address is synthesized (`*.example`); no cookie, token, or signed URL
 * appears.
 */
class OpenRecIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val captureEncoded = "%7B%22capture%22%3A%7B%22source%22%3A%22https%3A//media.example/capture/master.m3u8%22" +
        "%2C%22title%22%3A%22Fixture%20Capture%22%2C%22startTime%22%3A100%2C%22endTime%22%3A189" +
        "%2C%22thumbnailUrl%22%3A%22https%3A//img.example/capture.jpg%22" +
        "%2C%22publishedAt%22%3A%222021-11-22T10%3A00%3A00Z%22%7D" +
        "%2C%22movie%22%3A%7B%22channel%22%3A%7B%22name%22%3A%22Fixture%20Channel%22" +
        "%2C%22id%22%3A%22fixture-channel%22%7D%7D%7D"

    private fun moviePage(status: String = "UPLOADED"): String = """
        <html><script>window.pageStore = {"v8": {"movie": {
          "title": "Fixture Movie", "introduction": "Fixture description",
          "playTime": {"value": 771000},
          "lThumbnailUrl": "https://img.example/movie.jpg",
          "startedAt": {"time": 1637589871000}, "totalViews": 1234,
          "onAirStatus": "$status", "targetMembers": [], "publicType": "public",
          "channel": {"user": {"name": "Fixture Channel", "id": "fixture-channel"}}
        }}};</script></html>
    """.trimIndent()

    private fun detailRoute(videoId: String): FixtureRoute = FixtureRoute(
        urlPattern = "https://apiv5.mellow-fan.com/api/v5/movies/$videoId/detail",
        contentType = "application/json",
        body = """
            {"data": {"items": [{"media": {"url": "https://media.example/movie/master.m3u8"}}]}}
        """.trimIndent(),
    )

    private val meRoute = FixtureRoute(
        urlPattern = "https://apiv5.mellow-fan.com/api/v5/users/me",
        statusCode = 401,
        contentType = "application/json",
        body = """{"status": 401}""",
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            OpenRecIE(http(transfer())) to "https://www.mellow-fan.com/live/e2zwj0mp6ro",
            OpenRecIE(http(transfer())) to "https://www.openrec.tv/m/live/abc123",
            OpenRecCaptureIE(http(transfer())) to "https://www.mellow-fan.com/capture/l2q00vxl8q8",
            OpenRecMovieIE(http(transfer())) to "https://www.mellow-fan.com/movie/e5rk9k4o6zv",
            OpenRecPlaylistIE(http(transfer())) to
                "https://www.mellow-fan.com/user/DbD_BPF/playlist/j59svruhtua2z8t",
            OpenRecChannelIE(http(transfer())) to "https://www.mellow-fan.com/user/OPENRECPARK",
            OpenRecChannelSearchIE(http(transfer())) to
                "https://www.mellow-fan.com/user/indegnasen/search?search_query=fixture",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(OpenRecChannelIE(http(transfer())).suitable("https://www.mellow-fan.com/user/x/search"))
        assertFalse(OpenRecMovieIE(http(transfer())).suitable("https://www.mellow-fan.com/live/x"))
    }

    // ------------------------------------------------------- OpenRecCaptureIE

    @Test
    fun captureReadsTheEncodedPageStore() = runTest {
        val url = "https://www.mellow-fan.com/capture/l2q00vxl8q8"
        val page = """
            <html><script>window.pageStore = JSON.parse(decodeURIComponent("$captureEncoded"))</script></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = OpenRecCaptureIE(http(transfer)).extract(url)

        assertEquals("l2q00vxl8q8", info.id)
        assertEquals("Fixture Capture", info.title)
        assertEquals(89.0, info.duration)
        assertEquals("Fixture Channel", info.channel)
        assertEquals("fixture-channel", info.channelId)
        assertEquals("https://img.example/capture.jpg", info.thumbnails.single().url)
        assertEquals("20211122", info.uploadDate)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // --------------------------------------------------------- OpenRecMovieIE

    @Test
    fun movieMapsTheDetailMediaAndMetadata() = runTest {
        val url = "https://www.mellow-fan.com/movie/e5rk9k4o6zv"
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = moviePage()),
            meRoute,
            detailRoute("e5rk9k4o6zv"),
        )
        val info = OpenRecMovieIE(http(transfer)).extract(url)

        assertEquals("e5rk9k4o6zv", info.id)
        assertEquals("Fixture Movie", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(771.0, info.duration)
        assertEquals("20211122", info.uploadDate)
        assertEquals(1234L, info.viewCount)
        assertEquals("Fixture Channel", info.channel)
        assertEquals("public", info.availability)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://www.mellow-fan.com/", info.formats.single().httpHeaders?.get("referer"))
    }

    @Test
    fun upcomingLiveFailsTyped() = runTest {
        val url = "https://www.mellow-fan.com/live/e2zwj0mp6ro"
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = moviePage(status = "COMING_UP")),
            meRoute,
            detailRoute("e2zwj0mp6ro"),
        )
        assertFailsWith<ExtractionError.NotYetAvailable> {
            OpenRecIE(http(transfer)).extract(url)
        }
    }

    @Test
    fun subscriptionContentFailsAsALoginWall() = runTest {
        val url = "https://www.mellow-fan.com/movie/e5rk9k4o6zv"
        val page = moviePage().replace("\"targetMembers\": []", "\"targetMembers\": [{\"type\": \"subscription\"}]")
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            meRoute,
            detailRoute("e5rk9k4o6zv"),
        )
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            OpenRecMovieIE(http(transfer)).extract(url)
        }
        assertTrue(error.message!!.contains("channel subscription"))
    }

    // ------------------------------------------------------ OpenRecChannelIE

    @Test
    fun channelPagesThroughThePublicSearch() = runTest {
        val url = "https://www.mellow-fan.com/user/OPENRECPARK"
        val page = """
            <html><script>window.pageStore = {"state": {"_channel": {"movieCount": 1,
              "user": {"name": "Fixture Channel"}}}};</script></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://public.mellow-fan.com/external/api/v5/search-movies" +
                    "?channel_ids=OPENRECPARK&include_live=true&include_upload=true" +
                    "&onair_status=2&include_deleted=true&sort=published_at&page=1",
                contentType = "application/json",
                body = """[{"id": "e5rk9k4o6zv", "movie_type": "2"}]""",
            ),
            FixtureRoute(
                urlPattern = "https://public.mellow-fan.com/external/api/v5/search-movies" +
                    "?channel_ids=OPENRECPARK&include_live=true&include_upload=true" +
                    "&onair_status=2&include_deleted=true&sort=published_at&page=2",
                contentType = "application/json",
                body = "[]",
            ),
        )
        val info = OpenRecChannelIE(http(transfer)).extract(url)
        assertEquals("OPENRECPARK", info.id)
        assertEquals("Fixture Channel", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.mellow-fan.com/movie/e5rk9k4o6zv", info.entries[0].url)
    }

    // ------------------------------------------------ OpenRecChannelSearchIE

    @Test
    fun channelSearchSplitsTheTypeAndPagesThePublicApi() = runTest {
        val bare = "https://www.mellow-fan.com/user/indegnasen/search?search_query=fixture"
        val bareInfo = OpenRecChannelSearchIE(http(transfer())).extract(bare)
        assertEquals(2, bareInfo.entries.size)
        assertTrue(bareInfo.entries[0].url!!.contains("/search/capture"))

        val typed = "https://www.mellow-fan.com/user/indegnasen/search/movie?search_query=fixture"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://public.mellow-fan.com/external/api/v5/search-movies" +
                    "?channel_ids=indegnasen&page=1&search_query=fixture",
                contentType = "application/json",
                body = """[{"id": "e5rk9k4o6zv"}]""",
            ),
            FixtureRoute(
                urlPattern = "https://public.mellow-fan.com/external/api/v5/search-movies" +
                    "?channel_ids=indegnasen&page=2&search_query=fixture",
                contentType = "application/json",
                body = "[]",
            ),
        )
        val info = OpenRecChannelSearchIE(http(transfer)).extract(typed)
        assertEquals("indegnasen", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.mellow-fan.com/movie/e5rk9k4o6zv", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun captureIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.mellow-fan.com/capture/l2q00vxl8q8"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("l2q00vxl8q8"),
                "title" to Expect.Value("Fixture Capture"),
                "duration" to Expect.Value(89.0),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><script>window.pageStore = JSON.parse(decodeURIComponent("$captureEncoded"))</script></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> OpenRecCaptureIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun movieIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.mellow-fan.com/movie/e5rk9k4o6zv"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("e5rk9k4o6zv"),
                "title" to Expect.Value("Fixture Movie"),
                "duration" to Expect.Value(771.0),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = url, contentType = "text/html", body = moviePage()),
                meRoute,
                detailRoute("e5rk9k4o6zv"),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> OpenRecMovieIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
