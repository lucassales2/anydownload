package com.anydownload.core.extract.mlb

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
 * Fixture cases for the MLB subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class MlbIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            MLBIE(http(transfer())) to "https://www.mlb.com/mariners/video/ackleys-catch/c-34698933",
            MLBIE(http(transfer())) to "http://mlb.mlb.com/shared/video/embed/embed.html?content_id=36599553",
            MLBVideoIE(http(transfer())) to "https://www.mlb.com/mariners/video/ackley-s-spectacular-catch-c34698933",
            MLBTVIE(http(transfer())) to "https://www.mlb.com/tv/g661581/fixture",
            MLBArticleIE(http(transfer())) to "https://www.mlb.com/news/fixture-article",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(MLBVideoIE(http(transfer())).suitable("https://www.mlb.com/video/x/c-1"))
    }

    // -------------------------------------------------------------- legacy id

    @Test
    fun legacyDetailsApiYieldsFormatsThumbnailsAndCaptions() = runTest {
        val url = "https://www.mlb.com/mariners/video/ackleys-catch/c-34698933"
        val api = "http://content.mlb.com/mlb/item/id/v1/34698933/details/web-v1.json"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = api,
                contentType = "application/json",
                body = """
                    {"id": "34698933", "title": "Fixture Catch", "description": "Fixture description",
                     "date": "2014-07-22T00:00:00Z", "language": "EN", "duration": "00:01:06",
                     "image": {"cuts": [{"src": "https://media.example/thumb.jpg", "width": 640, "height": 360}]},
                     "keywordsAll": [{"type": "closed_captions_location_en",
                                      "value": "https://media.example/cc/en.vtt"}],
                     "playbacks": [
                       {"name": "hls-4000", "url": "https://media.example/hls/master.m3u8"},
                       {"name": "mp4_4000K_1280X720", "url": "https://media.example/mp4/1280x720_30_4000K.mp4"}
                     ]}
                """.trimIndent(),
            ),
        )
        val info = MLBIE(http(transfer)).extract(url)
        assertEquals("34698933", info.id)
        assertEquals("Fixture Catch", info.title)
        assertEquals("20140722", info.uploadDate)
        assertEquals(66.0, info.duration)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals(720L, info.formats[1].height)
        assertEquals(4000.0, info.formats[1].tbr)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals("https://media.example/cc/en.vtt", info.subtitles.single().formats.single().url)
    }

    // ------------------------------------------------------------- graphql

    @Test
    fun videoGraphqlYieldsTheFirstFeed() = runTest {
        val url = "https://www.mlb.com/mariners/video/ackley-s-spectacular-catch-c34698933"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://fastball-gateway.mlb.com/graphql*",
                contentType = "application/json",
                body = """
                    {"data": {"mediaPlayback": [{
                      "id": "c04a8863-f569-42e6-9f87-992393657614", "title": "Fixture GraphQL",
                      "timestamp": "2014-07-22T00:00:00Z",
                      "feeds": [{"duration": "00:00:46",
                                 "playbacks": [{"name": "hls", "url": "https://media.example/hls/master.m3u8"}]}]
                    }]}}
                """.trimIndent(),
            ),
        )
        val info = MLBVideoIE(http(transfer)).extract(url)
        assertEquals("c04a8863-f569-42e6-9f87-992393657614", info.id)
        assertEquals("Fixture GraphQL", info.title)
        assertEquals(46.0, info.duration)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // ----------------------------------------------------------------- tv wall

    @Test
    fun mlbTvFailsTypedOnTheSessionTokens() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            MLBTVIE(http(transfer())).extract("https://www.mlb.com/tv/g661581/fixture")
        }
        assertTrue(error.message!!.contains("tokens"))
    }

    // ---------------------------------------------------------------- article

    @Test
    fun articlePageListsVideoParts() = runTest {
        val url = "https://www.mlb.com/news/fixture-article"
        val page = """
            <html><head><meta property="og:title" content="Fixture Article"></head><body>
            <script>window.initState = {"apolloCache": {"ROOT_QUERY": {
              "getArticle({})": {"translationId": "36db7394-343c-4ea3-b8ca-ead2e61bca9a",
                                 "summary": "Fixture summary",
                                 "parts": [{"__typename": "Video", "slug": "fixture-video-one"},
                                           {"__typename": "Text", "slug": "fixture-text"}]}
            }}};</script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = MLBArticleIE(http(transfer)).extract(url)
        assertEquals("36db7394-343c-4ea3-b8ca-ead2e61bca9a", info.id)
        assertEquals("Fixture Article", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.mlb.com/video/fixture-video-one", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun legacyDetailsIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.mlb.com/mariners/video/ackleys-catch/c-34698933"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("34698933"),
                "title" to Expect.Value("Fixture Catch"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "http://content.mlb.com/mlb/item/id/v1/34698933/details/web-v1.json",
                    contentType = "application/json",
                    body = """
                        {"id": "34698933", "title": "Fixture Catch",
                         "playbacks": [{"name": "hls", "url": "https://media.example/hls/master.m3u8"}]}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MLBIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun articleIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.mlb.com/news/fixture-article"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("36db7394-343c-4ea3-b8ca-ead2e61bca9a"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><body><script>window.initState = {"apolloCache": {"ROOT_QUERY": {
                          "getArticle({})": {"translationId": "36db7394-343c-4ea3-b8ca-ead2e61bca9a",
                                             "parts": [{"__typename": "Video", "slug": "one"}]}
                        }}};</script></body></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MLBArticleIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
