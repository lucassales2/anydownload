package com.anydownload.core.extract.loom

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
 * Fixture cases for the Loom subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class LoomIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "43d05f362f734614a2e81b4694a3a523"
    private val graphql = "https://www.loom.com/graphql"

    private val metadataRoute = FixtureRoute(
        urlPattern = graphql,
        method = "POST",
        requestBodyContains = "GetVideoSSR",
        contentType = "application/json",
        body = """
            {"data": {"getVideo": {"__typename": "RegularUserVideo", "id": "$videoId",
              "name": "Fixture Loom", "description": "Fixture description",
              "createdAt": "2022-03-28T10:00:00Z",
              "owner": {"display_name": "Fixture Owner"},
              "video_properties": {"duration": 27, "width": 1280, "height": 720}}}}
        """.trimIndent(),
    )

    private val sourceRoute = FixtureRoute(
        urlPattern = graphql,
        method = "POST",
        requestBodyContains = "GetVideoSource",
        contentType = "application/json",
        body = """
            {"data": {"getVideo": {"nullableRawCdnUrl": {"url": "https://media.example/cdn/master.m3u8"}}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val extractor = LoomIE(http(transfer()))
        assertTrue(extractor.suitable("https://www.loom.com/share/$videoId"))
        assertTrue(extractor.suitable("https://www.loom.com/embed/$videoId"))
        assertFalse(extractor.suitable("https://www.loom.com/share/not-a-hex-id"))
        assertTrue(
            LoomFolderIE(http(transfer())).suitable("https://www.loom.com/share/folder/$videoId"),
        )
    }

    // ----------------------------------------------------------------- video

    @Test
    fun videoGraphqlYieldsFormatsSubtitlesAndChapters() = runTest {
        val transfer = transfer(
            metadataRoute,
            sourceRoute,
            FixtureRoute(
                urlPattern = "https://www.loom.com/api/campaigns/sessions/$videoId/raw-url",
                method = "POST",
                contentType = "application/json",
                body = """{"url": "https://media.example/raw/master-split.m3u8"}""",
            ),
            FixtureRoute(
                urlPattern = "https://www.loom.com/api/campaigns/sessions/$videoId/transcoded-url",
                method = "POST",
                contentType = "application/json",
                body = """{"url": "https://media.example/transcoded.mp4"}""",
            ),
            FixtureRoute(
                urlPattern = graphql,
                method = "POST",
                requestBodyContains = "FetchVideoTranscript",
                contentType = "application/json",
                body = """{"data": {"fetchVideoTranscript": {"source_url": "https://media.example/sub/en.vtt"}}}""",
            ),
            FixtureRoute(
                urlPattern = graphql,
                method = "POST",
                requestBodyContains = "FetchChapters",
                contentType = "application/json",
                body = """{"data": {"fetchVideoChapters": {"content": "00:00 Intro\n00:10 Body"}}}""",
            ),
        )
        val info = LoomIE(http(transfer)).extract("https://www.loom.com/share/$videoId")
        assertEquals(videoId, info.id)
        assertEquals("Fixture Loom", info.title)
        assertEquals("Fixture Owner", info.uploader)
        assertEquals("20220328", info.uploadDate)
        assertEquals(27.0, info.duration)
        assertEquals(3, info.formats.size)
        assertEquals("https://media.example/raw/master.m3u8", info.formats.first { it.formatId == "hls-raw" }.url)
        assertEquals("en", info.subtitles.single().language)
        assertEquals(2, info.chapters.size)
        assertEquals(10.0, info.chapters[1].startTime)
    }

    @Test
    fun passwordProtectedVideoFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = graphql,
                method = "POST",
                requestBodyContains = "GetVideoSSR",
                contentType = "application/json",
                body = """{"data": {"getVideo": {"__typename": "VideoPasswordMissingOrIncorrect"}}}""",
            ),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            LoomIE(http(transfer)).extract("https://www.loom.com/share/$videoId")
        }
        assertTrue(error.message!!.contains("password"))
    }

    // ---------------------------------------------------------------- folder

    @Test
    fun folderApiListsVideosAndSubfolders() = runTest {
        val folderId = "997db4db046f43e5912f10dc5f817b5c"
        val subfolderId = "9a8a87f6b6f546d9a400c8e7575ff7f2"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.loom.com/v1/folders/$folderId?limit=10000",
                contentType = "application/json",
                body = """
                    {"folder": {"name": "Fixture Folder"},
                     "videos": [{"id": "$videoId", "name": "Fixture Video"}],
                     "folders": [{"id": "$subfolderId"}]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://www.loom.com/v1/folders/$subfolderId?limit=10000",
                contentType = "application/json",
                body = """{"folder": {"name": "Sub"}, "videos": [{"id": "c43a642f815f4378b6f80a889bb73d8d"}]}""",
            ),
        )
        val info = LoomFolderIE(http(transfer)).extract("https://www.loom.com/share/folder/$folderId")
        assertEquals(folderId, info.id)
        assertEquals("Fixture Folder", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.loom.com/share/$videoId", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://www.loom.com/share/$videoId",
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Loom"),
                "duration" to Expect.Value(27.0),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                metadataRoute,
                sourceRoute,
                FixtureRoute(
                    urlPattern = "https://www.loom.com/api/campaigns/sessions/$videoId/raw-url",
                    method = "POST",
                    contentType = "application/json",
                    body = """{"url": "https://media.example/raw/master.m3u8"}""",
                ),
                FixtureRoute(
                    urlPattern = "https://www.loom.com/api/campaigns/sessions/$videoId/transcoded-url",
                    method = "POST",
                    contentType = "application/json",
                    body = """{"url": "https://media.example/cdn/master.m3u8"}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> LoomIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
