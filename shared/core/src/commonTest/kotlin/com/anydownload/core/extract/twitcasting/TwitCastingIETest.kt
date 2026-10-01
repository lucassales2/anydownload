package com.anydownload.core.extract.twitcasting

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
 * Fixture cases for the TwitCasting subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class TwitCastingIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "2357609"
    private val uploaderId = "ivetesangalo"

    private fun moviePage() = """
        <html><head><meta property="og:title" content="Fixture TwitCasting">
        <meta property="og:image" content="https://media.example/thumb.jpg"></head>
        <body><span id="movietitle">Fixture TwitCasting</span>
        <span id="authorcomment">Fixture description</span>
        <span class="tw-player-duration-time">0:32</span>
        <div data-toggle="true" datetime="2011-08-22T00:00:00Z"></div>
        <span>Total : Views 42</span>
        <div data-movie-playlist='{"2": [{"thumbnailUrl": "https://media.example/thumb.jpg",
          "duration": 32000, "source": {"url": "https://media.example/hls/master.m3u8"}}]}'></div>
        </body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            TwitCastingIE(http(transfer())) to "https://twitcasting.tv/$uploaderId/movie/$videoId",
            TwitCastingIE(http(transfer())) to "https://twitcasting.tv/$uploaderId/twplayer/$videoId",
            TwitCastingLiveIE(http(transfer())) to "https://twitcasting.tv/$uploaderId",
            TwitCastingUserIE(http(transfer())) to "https://twitcasting.tv/$uploaderId/show",
            TwitCastingUserIE(http(transfer())) to "https://twitcasting.tv/$uploaderId/archive/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(TwitCastingIE(http(transfer())).suitable("https://twitcasting.tv/$uploaderId/show"))
    }

    // ------------------------------------------------------------------ movie

    @Test
    fun moviePageYieldsHlsAndMetadata() = runTest {
        val url = "https://twitcasting.tv/$uploaderId/movie/$videoId"
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = moviePage()))
        val info = TwitCastingIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture TwitCasting", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(32.0, info.duration)
        assertEquals(42L, info.viewCount)
        assertEquals("20110822", info.uploadDate)
        assertEquals(false, info.isLive)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://twitcasting.tv/", info.formats.single().httpHeaders?.get("Referer"))
    }

    @Test
    fun passwordProtectedMovieFailsTyped() = runTest {
        val url = "https://twitcasting.tv/$uploaderId/movie/$videoId"
        val page = """<html><body><form method="POST"><input type="password" name="password"></form></body></html>"""
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        assertFailsWith<ExtractionError.LoginRequired> {
            TwitCastingIE(http(transfer)).extract(url)
        }
    }

    // ------------------------------------------------------------------- live

    @Test
    fun liveCheckRedirectsToTheCurrentMovie() = runTest {
        val url = "https://twitcasting.tv/$uploaderId"
        val page = """
            <html><body><a class="tw-movie-thumbnail2" href="/$uploaderId/movie/$videoId"></a></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://frontendapi.twitcasting.tv/watch/user/$uploaderId",
                method = "POST",
                contentType = "application/json",
                body = """{"is_live": true}""",
            ),
            FixtureRoute(
                urlPattern = "https://twitcasting.tv/$uploaderId/show/",
                contentType = "text/html",
                body = page,
            ),
        )
        val info = TwitCastingLiveIE(http(transfer)).extract(url)
        assertEquals("https://twitcasting.tv/$uploaderId/movie/$videoId", info.redirectUrl)
    }

    // ------------------------------------------------------------------- user

    @Test
    fun userHistoryYieldsEntries() = runTest {
        val url = "https://twitcasting.tv/$uploaderId/show"
        val page = """
            <html><body><a class="tw-movie-thumbnail2" href="/$uploaderId/movie/$videoId"></a></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://twitcasting.tv/$uploaderId/show?filter=watchable",
                contentType = "text/html",
                body = page,
            ),
        )
        val info = TwitCastingUserIE(http(transfer)).extract(url)
        assertEquals(uploaderId, info.id)
        assertEquals("$uploaderId - Live History", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://twitcasting.tv/$uploaderId/movie/$videoId", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun movieIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://twitcasting.tv/$uploaderId/movie/$videoId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture TwitCasting"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(FixtureRoute(urlPattern = url, contentType = "text/html", body = moviePage())),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TwitCastingIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
