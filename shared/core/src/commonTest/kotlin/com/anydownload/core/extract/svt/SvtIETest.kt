package com.anydownload.core.extract.svt

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
 * Fixture cases for the SVT subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class SvtIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val svtId = "ePBvGRq"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            SVTPlayIE(http(transfer())) to "svt:$svtId",
            SVTPlayIE(http(transfer())) to "https://www.svtplay.se/video/30479064/fixture/fixture?modalId=$svtId",
            SVTSeriesIE(http(transfer())) to "https://www.svtplay.se/rederiet?tab=season-2-jpmQYgn",
            SVTPageIE(http(transfer())) to "https://www.svt.se/vader/manadskronikor/maj2018",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(SVTSeriesIE(http(transfer())).suitable("https://www.svtplay.se/video/1/x/y"))
    }

    // ----------------------------------------------------------------- play

    @Test
    fun playApiYieldsFormatsAndSubtitles() = runTest {
        val url = "svt:$svtId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.svt.se/videoplayer-api/video/$svtId",
                contentType = "application/json",
                body = """
                    {"title": "Fixture Episode", "programTitle": "Fixture Series",
                     "materialLength": 3585, "rights": {"validFrom": "2025-05-11T00:00:00Z"},
                     "blockedForChildren": false,
                     "videoReferences": [
                       {"playerType": "hls", "url": "https://media.example/hls/master.m3u8"},
                       {"playerType": "dash", "url": "https://media.example/dash/master.mpd"}],
                     "subtitleReferences": [
                       {"language": "sv", "url": "https://media.example/sub/sv.vtt"},
                       {"language": "sv", "url": "https://media.example/sub/text-open.vtt"}]}
                """.trimIndent(),
            ),
        )
        val info = SVTPlayIE(http(transfer)).extract(url)
        assertEquals(svtId, info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals(3585.0, info.duration)
        assertEquals("20250511", info.uploadDate)
        assertEquals(0, info.ageLimit)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mpd", info.formats[1].protocol)
        assertEquals(2, info.subtitles.size)
        assertTrue(info.subtitles.any { it.language == "sv-forced" })
    }

    @Test
    fun geoBlockedSwedenFailsTyped() = runTest {
        val url = "svt:$svtId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.svt.se/videoplayer-api/video/$svtId",
                contentType = "application/json",
                body = """
                    {"title": "Geo", "rights": {"geoBlockedSweden": true}, "videoReferences": []}
                """.trimIndent(),
            ),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            SVTPlayIE(http(transfer)).extract(url)
        }
    }

    // --------------------------------------------------------------- series

    @Test
    fun seriesGraphqlYieldsEntries() = runTest {
        val url = "https://www.svtplay.se/rederiet?tab=season-2-jpmQYgn"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.svt.se/contento/graphql?query=*",
                contentType = "application/json",
                body = """
                    {"data": {"listablesBySlug": [{
                      "id": "series-1", "name": "Fixture Series", "longDescription": "Fixture description",
                      "associatedContent": [
                        {"id": "season-2-jpmQYgn", "name": "Season 2",
                         "items": [{"item": {"videoSvtId": "$svtId"}}]}]}]}}
                """.trimIndent(),
            ),
        )
        val info = SVTSeriesIE(http(transfer)).extract(url)
        assertEquals("season-2-jpmQYgn", info.id)
        assertEquals("Fixture Series - Season 2", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("svt:$svtId", info.entries.single().url)
    }

    // ----------------------------------------------------------------- page

    @Test
    fun svtPageYieldsMedia() = runTest {
        val url = "https://www.svt.se/vader/manadskronikor/maj2018"
        val page = """
            <html><head><meta property="og:title" content="Fixture Page"></head><body>
            <script>urqlState = {"key": {"data": "{\"page\": {\"topMedia\": {\"svtId\": \"$svtId\"},
              \"body\": []}}"}};</script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://api.svt.se/video/$svtId",
                contentType = "application/json",
                body = """
                    {"title": "Fixture Media",
                     "videoReferences": [{"playerType": "hls",
                                          "url": "https://media.example/hls/master.m3u8"}]}
                """.trimIndent(),
            ),
        )
        val info = SVTPageIE(http(transfer)).extract(url)
        assertEquals("maj2018", info.id)
        assertEquals("Fixture Page", info.title)
        assertEquals(1, info.media.size)
        assertEquals("m3u8_native", info.media.single().formats.single().protocol)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun playIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "svt:$svtId",
            infoDict = mapOf(
                "id" to Expect.Value(svtId),
                "title" to Expect.Value("Fixture Episode"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api.svt.se/videoplayer-api/video/$svtId",
                    contentType = "application/json",
                    body = """
                        {"title": "Fixture Episode",
                         "videoReferences": [{"playerType": "hls",
                                              "url": "https://media.example/hls/master.m3u8"}]}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SVTPlayIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
