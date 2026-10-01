package com.anydownload.core.extract.vice

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
 * Fixture cases for the Vice subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no MVPD credential,
 * cookie, token, or signed URL appears.
 */
class ViceIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "58c69e38a55424f1227dc3f7"
    private val secondId = "5816510690b70e6c5fd39a56"
    private val videoUrl = "https://video.vice.com/en_us/video/fixture-video/$videoId"
    private val showUrl = "https://video.vice.com/en_us/show/fixture-show"
    private val articleUrl = "https://www.vice.com/en_us/article/fixture-article"

    private fun videoGraphqlRoute(locked: Boolean = false) = FixtureRoute(
        urlPattern = "https://video.vice.com/api/v1/graphql*",
        contentType = "application/json",
        body = """
            {"data": {"videos": [{
              "title": "Fixture Vice Video",
              "body": "<p>Fixture <b>description</b></p>",
              "locked": $locked,
              "rating": "14",
              "thumbnail_url": "https://media.example/thumb.jpg"
            }]}}
        """.trimIndent(),
    )

    private fun preplayRoute() = FixtureRoute(
        urlPattern = "https://vms.vice.com/en_us/video/preplay/$videoId*",
        contentType = "application/json",
        body = """
            {"playURL": "https://media.example/master.m3u8",
             "video": {
               "video_duration": 120,
               "created_at": 1489664942000,
               "video_rating": "14",
               "show": {"base": {"display_title": "Fixture Show"}},
               "channel": {"name": "vice", "id": "57a204088cb727dec794c67b"},
               "episode": {"episode_number": 2, "id": "ep1"},
               "season": {"season_number": 1, "id": "se1"}
             },
             "subtitleURLs": [
               {"url": "https://media.example/subs.vtt", "languages": [{"language_code": "en"}]}
             ]}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ViceIE(http(transfer())) to videoUrl,
            ViceIE(http(transfer())) to "https://video.vice.com/en_us/embed/57f41d3556a0a80f54726060",
            ViceIE(http(transfer())) to "https://vms.vice.com/en_us/video/preplay/$videoId",
            ViceIE(http(transfer())) to "https://www.viceland.com/en_us/video/fixture/5a8f2d7ff1cdb332dd446ec1",
            ViceShowIE(http(transfer())) to showUrl,
            ViceArticleIE(http(transfer())) to articleUrl,
            ViceArticleIE(http(transfer())) to "https://www.vice.com/ru/article/fixture-slug-229",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ViceIE(http(transfer())).suitable("https://www.example.com/en_us/video/fixture/$videoId"))
        assertFalse(ViceShowIE(http(transfer())).suitable(videoUrl))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun videoPreplayYieldsFormatsAndMetadata() = runTest {
        val transfer = transfer(videoGraphqlRoute(), preplayRoute())
        val info = ViceIE(http(transfer)).extract(videoUrl)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Vice Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(120.0, info.duration)
        assertEquals("20170316", info.uploadDate)
        assertEquals(14, info.ageLimit)
        assertEquals("vice", info.uploader)
        assertEquals("57a204088cb727dec794c67b", info.channelId)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
        assertEquals("en", info.subtitles.single().language)
        assertEquals("https://media.example/subs.vtt", info.subtitles.single().formats.single().url)
    }

    @Test
    fun lockedVideoFailsTypedAsLoginRequired() = runTest {
        val transfer = transfer(videoGraphqlRoute(locked = true))
        assertFailsWith<ExtractionError.LoginRequired> {
            ViceIE(http(transfer)).extract(videoUrl)
        }
    }

    // ------------------------------------------------------------------- show

    @Test
    fun showListsThePagedVideos() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://video.vice.com/api/v1/graphql*shows*",
                contentType = "application/json",
                body = """
                    {"data": {"shows": [{
                      "dek": "Fixture dek",
                      "id": "show1",
                      "title": "Fixture Show"
                    }]}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://video.vice.com/api/v1/graphql*videos*",
                contentType = "application/json",
                body = """
                    {"data": {"videos": [
                      {"body": "one", "id": "vid1", "url": "$videoUrl"},
                      {"body": "two", "id": "vid2", "url": "https://video.vice.com/en_us/video/second/$secondId"}
                    ]}}
                """.trimIndent(),
            ),
        )
        val info = ViceShowIE(http(transfer)).extract(showUrl)
        assertEquals("show1", info.id)
        assertEquals("Fixture Show", info.title)
        assertEquals("Fixture dek", info.description)
        assertEquals(2, info.entries.size)
        assertEquals("vid1", info.entries[0].id)
        assertEquals(videoUrl, info.entries[0].url)
        assertEquals("vid2", info.entries[1].id)
    }

    // ---------------------------------------------------------------- article

    @Test
    fun articleViceEmbedBecomesARedirect() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://video.vice.com/api/v1/graphql*articles*",
                contentType = "application/json",
                body = """
                    {"data": {"articles": [{
                      "body": "<iframe src=\"//video.vice.com/en_us/embed/57f41d3556a0a80f54726060\"></iframe>",
                      "embed_code": ""
                    }]}}
                """.trimIndent(),
            ),
        )
        val info = ViceArticleIE(http(transfer)).extract(articleUrl)
        assertEquals("https://video.vice.com/en_us/embed/57f41d3556a0a80f54726060", info.redirectUrl)
    }

    @Test
    fun articleYoutubeEmbedBecomesARedirect() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://video.vice.com/api/v1/graphql*articles*",
                contentType = "application/json",
                body = """
                    {"data": {"articles": [{
                      "body": "<iframe src=\"https://www.youtube.com/embed/dQw4w9WgXcQ\"></iframe>",
                      "embed_code": ""
                    }]}}
                """.trimIndent(),
            ),
        )
        val info = ViceArticleIE(http(transfer)).extract(articleUrl)
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", info.redirectUrl)
    }

    @Test
    fun articleDataVideoUrlIsTheFallback() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://video.vice.com/api/v1/graphql*articles*",
                contentType = "application/json",
                body = """
                    {"data": {"articles": [{
                      "body": "<p>No embed here</p>",
                      "embed_code": "<div data-video-url=\"$videoUrl\"></div>"
                    }]}}
                """.trimIndent(),
            ),
        )
        val info = ViceArticleIE(http(transfer)).extract(articleUrl)
        assertEquals(videoUrl, info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Vice Video"),
                "age_limit" to Expect.Value(14),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(videoGraphqlRoute(), preplayRoute()),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ViceIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun showIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = showUrl,
            infoDict = mapOf(
                "id" to Expect.Value("show1"),
                "title" to Expect.Value("Fixture Show"),
                "entries" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://video.vice.com/api/v1/graphql*shows*",
                    contentType = "application/json",
                    body = """{"data": {"shows": [{"dek": "Fixture dek", "id": "show1", "title": "Fixture Show"}]}}""",
                ),
                FixtureRoute(
                    urlPattern = "https://video.vice.com/api/v1/graphql*videos*",
                    contentType = "application/json",
                    body = """
                        {"data": {"videos": [
                          {"body": "one", "id": "vid1", "url": "$videoUrl"},
                          {"body": "two", "id": "vid2", "url": "https://video.vice.com/en_us/video/second/$secondId"}
                        ]}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ViceShowIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
