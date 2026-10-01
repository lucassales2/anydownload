package com.anydownload.core.extract.sonyliv

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
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
 * Fixture cases for the SonyLIV subset. Ids and media paths are synthesized on
 * `media.example`; the anonymous token is a fake value. No cookie, real token,
 * or signed URL appears.
 */
class SonyLivIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val episodeUrl =
        "https://www.sonyliv.com/shows/bachelors-delight-1700000113/achaari-cheese-toast-1000022678?watch=true"
    private val seriesUrl = "https://www.sonyliv.com/shows/adaalat-1700000091"

    private val tokenRoute = FixtureRoute(
        urlPattern = "https://apiv2.sonyliv.com/AGL/1.4/A/ENG/WEB/ALL/GETTOKEN",
        contentType = "application/json",
        body = """{"resultObj": "fake_value"}""",
    )

    private val contentRoute = FixtureRoute(
        urlPattern = "https://apiv2.sonyliv.com/AGL/1.5/A/ENG/WEB/IN/CONTENT/VIDEOURL/VOD/1000022678",
        contentType = "application/json",
        body = """
            {"resultObj": {
              "videoURL": "https://media.example/DASH/manifest.mpd",
              "posterURL": "https://media.example/poster.jpg",
              "isEncrypted": false,
              "subtitle": [
                {"subtitleUrl": "https://media.example/sub.vtt", "subtitleLanguageName": "English"}]}}
        """.trimIndent(),
    )

    private val detailRoute = FixtureRoute(
        urlPattern = "https://apiv2.sonyliv.com/AGL/1.6/A/ENG/WEB/IN/DETAIL/1000022678",
        contentType = "application/json",
        body = """
            {"resultObj": {"containers": [{"metadata": {
              "episodeTitle": "Fixture Episode",
              "longDescription": "Fixture description",
              "duration": 185, "creationDate": 1586632091000,
              "season": 1, "title": "Fixture Series", "episodeNumber": 1, "year": 2016}}]}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val episode = SonyLivIE(http(transfer()))
        val episodeCases = listOf(
            episodeUrl,
            "sonyliv:1000022678",
            "https://www.sonyliv.com/movies/tahalka-1000050121?watch=true",
            "https://www.sonyliv.com/clip/jigarbaaz-1000098925",
            "https://www.sonyliv.com/trailer/sandwiched-forever-1000100286?watch=true",
            "https://www.sonyliv.com/sports/india-tour-1700000286/cricket-day-3-1000100959?watch=true",
            "https://www.sonyliv.com/music-videos/yeh-un-dinon-ki-baat-hai-1000018779",
        )
        for (url in episodeCases) {
            assertTrue(episode.suitable(url), "SonyLIV must match: $url")
        }
        assertFalse(episode.suitable(seriesUrl))
        assertFalse(episode.suitable("https://www.example.com/shows/x-1000022678"))

        val series = SonyLivSeriesIE(http(transfer()))
        assertTrue(series.suitable(seriesUrl))
        assertTrue(series.suitable("https://www.sonyliv.com/shows/beyhadh-1700000007/"))
        assertFalse(series.suitable(episodeUrl))
    }

    // ---------------------------------------------------------------- episode

    @Test
    fun episodeYieldsDashAndHlsRowsWithMetadata() = runTest {
        val info = SonyLivIE(http(transfer(tokenRoute, contentRoute, detailRoute))).extract(episodeUrl)
        assertEquals("1000022678", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(185.0, info.duration)
        assertEquals(2, info.formats.size)
        assertEquals("dash", info.formats[0].formatId)
        assertEquals("https://media.example/DASH/manifest.mpd", info.formats[0].url)
        assertEquals("mpd", info.formats[0].protocol)
        assertEquals("hls", info.formats[1].formatId)
        assertEquals("https://media.example/HLS/manifest.m3u8", info.formats[1].url)
        assertEquals("m3u8_native", info.formats[1].protocol)
        assertEquals("https://media.example/poster.jpg", info.thumbnails.single().url)
        assertEquals(1, info.subtitles.size)
        assertEquals("English", info.subtitles[0].language)
        assertEquals("https://media.example/sub.vtt", info.subtitles[0].formats.single().url)
    }

    @Test
    fun episodeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = episodeUrl,
            infoDict = mapOf(
                "id" to Expect.Value("1000022678"),
                "title" to Expect.Value("Fixture Episode"),
                "duration" to Expect.Value(185.0),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(tokenRoute, contentRoute, detailRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SonyLivIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun encryptedAssetFailsTypedDrm() = runTest {
        val drmRoute = FixtureRoute(
            urlPattern = "https://apiv2.sonyliv.com/AGL/1.5/A/ENG/WEB/IN/CONTENT/VIDEOURL/VOD/1000022678",
            contentType = "application/json",
            body = """
                {"resultObj": {"videoURL": "https://media.example/DASH/manifest.mpd",
                  "isEncrypted": true}}
            """.trimIndent(),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            SonyLivIE(http(transfer(tokenRoute, drmRoute))).extract(episodeUrl)
        }
        assertTrue(error.message!!.contains("DRM"), error.message)
    }

    // ----------------------------------------------------------------- series

    @Test
    fun seriesPaginatesTheSeasonEpisodes() = runTest {
        val seriesDetailRoute = FixtureRoute(
            urlPattern = "https://apiv2.sonyliv.com/AGL/1.9/R/ENG/WEB/IN/DL/DETAIL/1700000091?*",
            contentType = "application/json",
            body = """
                {"resultObj": {"containers": [{"containers": [
                  {"id": 111, "metadata": {"title": "Season 1"}}]}]}}
            """.trimIndent(),
        )
        val firstPageRoute = FixtureRoute(
            urlPattern = "https://apiv2.sonyliv.com/AGL/1.4/R/ENG/WEB/IN/CONTENT/DETAIL/BUNDLE/111?from=0&to=99*",
            contentType = "application/json",
            body = """
                {"resultObj": {"containers": [{"containers": [{"id": 1001}, {"id": 1002}]}]}}
            """.trimIndent(),
        )
        val secondPageRoute = FixtureRoute(
            urlPattern = "https://apiv2.sonyliv.com/AGL/1.4/R/ENG/WEB/IN/CONTENT/DETAIL/BUNDLE/111?from=100&to=199*",
            contentType = "application/json",
            body = """{"resultObj": {"containers": [{"containers": []}]}}""",
        )
        val info = SonyLivSeriesIE(
            http(transfer(tokenRoute, seriesDetailRoute, firstPageRoute, secondPageRoute)),
        ).extract(seriesUrl)
        assertEquals("1700000091", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("sonyliv:1001", info.entries[0].url)
        assertEquals("sonyliv:1002", info.entries[1].url)
    }
}
