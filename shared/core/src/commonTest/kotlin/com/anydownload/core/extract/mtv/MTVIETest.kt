package com.anydownload.core.extract.mtv

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
 * Fixture cases for the MTV subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class MTVIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val clipUrl = "https://www.mtv.com/video-clips/syolsj"
    private val episodeUrl = "https://www.mtv.com/episodes/uzvigh"
    private val videoId = "213ea7f8-bac7-4a43-8cd5-8d8cb8c8160f"

    private fun videoDetail(
        mgid: String = "mgid:arc:video:mtv.com:$videoId",
        serviceUrl: String? = "https://media.example/service.json?some=query",
        authRequired: Boolean = false,
    ) = """
        {
          "mgid": "$mgid",
          ${if (serviceUrl != null) "\"videoServiceUrl\": \"$serviceUrl\"," else ""}
          "title": "Fixture MTV Video",
          "channel": {"name": "MTV"},
          "description": "Fixture description",
          "fullDescription": "Fixture full description",
          "images": [{"url": "https://images.example/thumb.jpg"}],
          "duration": {"milliseconds": 95000},
          "originalPublishDate": "2025-07-31T00:00:00+00:00",
          "authRequired": $authRequired
        }
    """.trimIndent()

    private fun pageJson(videoDetail: String) = """
        {"children": [
          {"type": "MainContainer", "children": [
            {"type": "AviaWrapper", "children": [
              {"type": "FlexWrapper", "children": [
                {"type": "Player", "props": {"videoDetail": $videoDetail}}
              ]}
            ]}
          ]}
        ]}
    """.trimIndent()

    private fun fallbackPageJson(videoDetail: String) =
        """{"children": [{"type": "handleTVEAuthRedirection", "videoDetail": $videoDetail}]}"""

    private fun serviceRoute(manifestType: String, source: String) = FixtureRoute(
        urlPattern = "https://media.example/service.json*",
        contentType = "application/json",
        body = """{"stitchedstream": {"manifesttype": "$manifestType", "source": "$source"}}""",
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val extractor = MTVIE(http(transfer()))
        assertTrue(extractor.suitable(clipUrl))
        assertTrue(extractor.suitable(episodeUrl))
        assertFalse(extractor.suitable("https://www.example.com/video-clips/syolsj"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun clipPageYieldsTheHlsRowAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$clipUrl*", contentType = "application/json", body = pageJson(videoDetail())),
            serviceRoute("hls", "https://media.example/master.m3u8"),
        )
        val info = MTVIE(http(transfer)).extract(clipUrl)
        assertEquals(videoId, info.id)
        assertEquals("Fixture MTV Video", info.title)
        assertEquals("Fixture full description", info.description)
        assertEquals("MTV", info.channel)
        assertEquals(95.0, info.duration)
        assertEquals("20250731", info.uploadDate)
        assertEquals("https://images.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
    }

    @Test
    fun fallbackNodeYieldsTheDashRow() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$episodeUrl*", contentType = "application/json", body = fallbackPageJson(videoDetail())),
            serviceRoute("dash", "https://media.example/manifest.mpd"),
        )
        val info = MTVIE(http(transfer)).extract(episodeUrl)
        assertEquals(videoId, info.id)
        assertEquals(1, info.formats.size)
        assertEquals("mpd", info.formats[0].protocol)
        assertEquals("https://media.example/manifest.mpd", info.formats[0].url)
    }

    @Test
    fun authRequiredVideoFailsTypedAsLoginRequired() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$clipUrl*",
                contentType = "application/json",
                body = pageJson(videoDetail(authRequired = true)),
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            MTVIE(http(transfer)).extract(clipUrl)
        }
    }

    @Test
    fun unsupportedManifestTypeFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$clipUrl*", contentType = "application/json", body = pageJson(videoDetail())),
            serviceRoute("ssai", "https://media.example/stream"),
        )
        assertFailsWith<ExtractionError.NoFormats> {
            MTVIE(http(transfer)).extract(clipUrl)
        }
    }

    @Test
    fun missingVideoDetailFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$clipUrl*",
                contentType = "application/json",
                body = """{"children": [{"type": "MainContainer", "children": []}]}""",
            ),
        )
        assertFailsWith<ExtractionError.Malformed> {
            MTVIE(http(transfer)).extract(clipUrl)
        }
    }

    @Test
    fun missingServiceUrlFailsTypedAsUnavailable() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$clipUrl*",
                contentType = "application/json",
                body = pageJson(videoDetail(serviceUrl = null)),
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            MTVIE(http(transfer)).extract(clipUrl)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun clipIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = clipUrl,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture MTV Video"),
                "channel" to Expect.Value("MTV"),
                "duration" to Expect.Value(95.0),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "$clipUrl*", contentType = "application/json", body = pageJson(videoDetail())),
                serviceRoute("hls", "https://media.example/master.m3u8"),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MTVIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun episodeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = episodeUrl,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "upload_date" to Expect.Value("20250731"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "$episodeUrl*",
                    contentType = "application/json",
                    body = fallbackPageJson(videoDetail()),
                ),
                serviceRoute("dash", "https://media.example/manifest.mpd"),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MTVIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
