package com.anydownload.core.extract.viu

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
 * Fixture cases for the Viu subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class ViuIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ViuIE(http(transfer())) to "viu:1116705532",
            ViuIE(http(transfer())) to "https://www.viu.com/en/media/1116705532",
            ViuPlaylistIE(http(transfer())) to "https://www.viu.com/en/listing/playlist-22461380",
            ViuOTTIE(http(transfer())) to "http://www.viu.com/ott/sg/en-us/vod/3421/fixture",
            ViuOTTIndonesiaIE(http(transfer())) to
                "https://www.viu.com/ott/id/id/all/video-japanese-drama-fixture-1165863142",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ViuPlaylistIE(http(transfer())).suitable("https://www.viu.com/en/media/1"))
    }

    // ---------------------------------------------------------------- desktop

    @Test
    fun clipApiYieldsTheM3u8AndSubtitles() = runTest {
        val url = "https://www.viu.com/en/media/1116705532"
        val api = """
            {"response": {"status": "success", "item": [{
              "title": "Fixture Clip", "description": "Fixture description", "duration": 1234,
              "urlpathd": "https://media.example/hls", "tdirforwhole": "fixture",
              "jwhlsfile": "master.m3u8",
              "subtitle_en_vtt": "https://media.example/sub/en.vtt",
              "subtitle_zh-hk_srt": "https://media.example/sub/zh.srt"
            }]}}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.viu.com/api/clip/load?appid=viu_desktop&fmt=json&id=1116705532",
                contentType = "application/json",
                body = api,
            ),
        )
        val info = ViuIE(http(transfer)).extract(url)
        assertEquals("1116705532", info.id)
        assertEquals("Fixture Clip", info.title)
        assertEquals(1234.0, info.duration)
        assertEquals("https://media.example/hls/fixture/master.m3u8", info.formats.single().url)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals(2, info.subtitles.size)
        assertEquals("vtt", info.subtitles.first { it.language == "en" }.formats.single().ext)
    }

    @Test
    fun clipApiErrorStatusFailsTyped() = runTest {
        val url = "https://www.viu.com/en/media/1116705532"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.viu.com/api/clip/load?appid=viu_desktop&fmt=json&id=1116705532",
                contentType = "application/json",
                body = """{"response": {"status": "fail", "message": "Geo-restricted"}}""",
            ),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            ViuIE(http(transfer)).extract(url)
        }
        assertTrue(error.message!!.contains("Geo-restricted"))
    }

    @Test
    fun containerApiYieldsTheViuChildEntries() = runTest {
        val url = "https://www.viu.com/en/listing/playlist-22461380"
        val api = """
            {"response": {"status": "success", "container": {
              "title": "Fixture Playlist",
              "item": [{"id": 1116705532}, {"id": "1130599965"}]}}}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.viu.com/api/container/load?appid=viu_desktop&fmt=json&id=playlist-22461380",
                contentType = "application/json",
                body = api,
            ),
        )
        val info = ViuPlaylistIE(http(transfer)).extract(url)
        assertEquals("22461380", info.id)
        assertEquals("Fixture Playlist", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("viu:1116705532", info.entries[0].url)
    }

    // -------------------------------------------------------------------- OTT

    @Test
    fun ottClassesFailTypedOnTheTokenFlows() = runTest {
        val ott = assertFailsWith<ExtractionError.Unavailable> {
            ViuOTTIE(http(transfer())).extract("http://www.viu.com/ott/sg/en-us/vod/3421/fixture")
        }
        assertTrue(ott.message!!.contains("bearer token"))

        val indonesia = assertFailsWith<ExtractionError.Unavailable> {
            ViuOTTIndonesiaIE(http(transfer())).extract(
                "https://www.viu.com/ott/id/id/all/video-japanese-drama-fixture-1165863142",
            )
        }
        assertTrue(indonesia.message!!.contains("DRM"))
    }

    // --------------------------------------------------------------- harness

    @Test
    fun clipApiIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.viu.com/en/media/1116705532"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("1116705532"),
                "title" to Expect.Value("Fixture Clip"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://www.viu.com/api/clip/load?appid=viu_desktop&fmt=json&id=1116705532",
                    contentType = "application/json",
                    body = """
                        {"response": {"status": "success", "item": [{
                          "title": "Fixture Clip", "href": "https://media.example/hls/master.m3u8"}]}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ViuIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun containerApiIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.viu.com/en/listing/playlist-22461380"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("22461380"),
                "title" to Expect.Value("Fixture Playlist"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://www.viu.com/api/container/load?appid=viu_desktop&fmt=json&id=playlist-22461380",
                    contentType = "application/json",
                    body = """{"response": {"status": "success", "container": {"title": "Fixture Playlist", "item": [{"id": 1}]}}}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ViuPlaylistIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
