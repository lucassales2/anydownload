package com.anydownload.core.extract.err

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
 * Fixture cases for the ERR subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class ErrIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val jupiterUrl = "https://jupiter.err.ee/1211107/siin-me-oleme"
    private val episodeUrl = "https://jupiter.err.ee/1609145945/impulss"
    private val arhiivUrl = "https://arhiiv.err.ee/video/kontsertpalad"

    private fun jupiterRoute(
        urlPattern: String,
        body: String,
    ) = FixtureRoute(urlPattern = urlPattern, contentType = "application/json", body = body)

    private val jupiterApi = "https://services.err.ee/api/v2/vodContent/getContentPageData?contentId=1211107"
    private val episodeApi = "https://services.err.ee/api/v2/vodContent/getContentPageData?contentId=1609145945"

    private val movieJson = """
        {"data": {"mainContent": {"heading": "Siin me oleme!", "subHeading": "",
          "lead": "<p>Fixture lead</p>", "created": 1608210000, "year": 1978, "type": "movie",
          "medias": [{"src": {"hls": "https://media.example/master.m3u8",
            "dash": "https://media.example/manifest.mpd", "file": "https://media.example/video.mp4"},
            "restrictions": {"drm": false}}]}}}
    """.trimIndent()

    private val episodeJson = """
        {"data": {"mainContent": {"heading": "Impulss", "subHeading": "Loteriipilet hooldekodusse",
          "body": "Fixture body", "created": 1698327601, "type": "episode", "rootContentId": "1609108187",
          "medias": [{"src": {"hls": "https://media.example/master.m3u8"}, "restrictions": {"drm": false}}]}}}
    """.trimIndent()

    private val arhiivJson = """
        {"media": {"src": {"hls": "https://media.example/master.m3u8",
          "dash": "https://media.example/manifest.mpd"}},
         "info": {"title": "Fixture Archive", "seriesTitle": "Kontsertpalad", "seriesId": "s1",
          "episode": "255", "synopsis": "Fixture synopsis", "uploadDate": "2022-10-19T00:00:00Z",
          "dateModified": "2024-06-17T00:00:00Z", "date": "2021-01-25T00:00:00Z", "year": 1970}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val jupiter = ERRJupiterIE(http(transfer()))
        val jupiterCases = listOf(
            jupiterUrl,
            "https://jupiterpluss.err.ee/1609180445/bolee-zelenyj-tallinn",
            "https://lasteekraan.err.ee/1092243/patu",
        )
        for (url in jupiterCases) {
            assertTrue(jupiter.suitable(url), "ERRJupiter must match: $url")
        }
        assertFalse(jupiter.suitable("https://www.example.com/1211107/x"))

        val arhiiv = ERRArhiivIE(http(transfer()))
        assertTrue(arhiiv.suitable(arhiivUrl))
        assertTrue(arhiiv.suitable("https://arhiiv.err.ee/video/vaata/koalitsioonileppe-allkirjastamine"))
        assertFalse(arhiiv.suitable(jupiterUrl))
    }

    // --------------------------------------------------------------- jupiter

    @Test
    fun jupiterMovieYieldsTheStreamRows() = runTest {
        val transfer = transfer(jupiterRoute(jupiterApi, movieJson))
        val info = ERRJupiterIE(http(transfer)).extract(jupiterUrl)
        assertEquals("1211107", info.id)
        assertEquals("Siin me oleme!", info.title)
        assertEquals("Fixture lead", info.description)
        assertEquals("20201217", info.uploadDate)
        assertEquals(3, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mpd", info.formats[1].protocol)
        assertEquals("http", info.formats[2].formatId)
        assertEquals("https://media.example/video.mp4", info.formats[2].url)
    }

    @Test
    fun jupiterEpisodeFillsTheChannelFields() = runTest {
        val transfer = transfer(jupiterRoute(episodeApi, episodeJson))
        val info = ERRJupiterIE(http(transfer)).extract(episodeUrl)
        assertEquals("1609145945", info.id)
        assertEquals("Impulss", info.title)
        assertEquals("Impulss", info.channel)
        assertEquals("1609108187", info.channelId)
        assertEquals("20231026", info.uploadDate)
        assertEquals(1, info.formats.size)
    }

    @Test
    fun drmRestrictedMediaFailsTyped() = runTest {
        val drmJson = """
            {"data": {"mainContent": {"heading": "Fixture", "type": "movie",
              "medias": [{"src": {"hls": "https://media.example/master.m3u8"},
                "restrictions": {"drm": true}}]}}}
        """.trimIndent()
        val transfer = transfer(jupiterRoute(jupiterApi, drmJson))
        assertFailsWith<ExtractionError.Unavailable> {
            ERRJupiterIE(http(transfer)).extract(jupiterUrl)
        }
    }

    // ---------------------------------------------------------------- arhiiv

    @Test
    fun arhiivPageYieldsTheStreamRowsAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://arhiiv.err.ee/api/v1/content/video/kontsertpalad",
                contentType = "application/json",
                body = arhiivJson,
            ),
        )
        val info = ERRArhiivIE(http(transfer)).extract(arhiivUrl)
        assertEquals("kontsertpalad", info.id)
        assertEquals("Fixture Archive", info.title)
        assertEquals("Fixture synopsis", info.description)
        assertEquals("20221019", info.uploadDate)
        assertEquals("Kontsertpalad", info.channel)
        assertEquals("s1", info.channelId)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mpd", info.formats[1].protocol)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun jupiterIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = jupiterUrl,
            infoDict = mapOf(
                "id" to Expect.Value("1211107"),
                "title" to Expect.Value("Siin me oleme!"),
                "upload_date" to Expect.Value("20201217"),
                "formats" to Expect.Count(3),
            ),
            routes = listOf(jupiterRoute(jupiterApi, movieJson)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ERRJupiterIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun arhiivIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = arhiivUrl,
            infoDict = mapOf(
                "id" to Expect.Value("kontsertpalad"),
                "title" to Expect.Value("Fixture Archive"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://arhiiv.err.ee/api/v1/content/video/kontsertpalad",
                    contentType = "application/json",
                    body = arhiivJson,
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ERRArhiivIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
