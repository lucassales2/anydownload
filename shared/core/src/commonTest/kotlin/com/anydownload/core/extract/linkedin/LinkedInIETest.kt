package com.anydownload.core.extract.linkedin

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
 * Fixture cases for the LinkedIn subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class LinkedInIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val postId = "6850898786781339649"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            LinkedInIE(http(transfer())) to
                "https://www.linkedin.com/posts/mishalkhawaja_fixture-ugcPost-$postId-mM20",
            LinkedInIE(http(transfer())) to "https://www.linkedin.com/feed/update/urn:li:activity:7016901149999955968",
            LinkedInLearningIE(http(transfer())) to "https://www.linkedin.com/learning/fixture-course/fixture-lesson",
            LinkedInLearningCourseIE(http(transfer())) to "https://www.linkedin.com/learning/fixture-course",
            LinkedInEventsIE(http(transfer())) to "https://www.linkedin.com/events/7084656651378536448/comments/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(LinkedInIE(http(transfer())).suitable("https://www.linkedin.com/learning/fixture-course"))
    }

    // ------------------------------------------------------------------- post

    @Test
    fun postPageYieldsFormatsAndCaptions() = runTest {
        val url = "https://www.linkedin.com/posts/mishalkhawaja_fixture-ugcPost-$postId-mM20"
        val page = """
            <html><head>
            <meta property="og:title" content="Fixture LinkedIn Post">
            <meta property="og:description" content="Fixture description">
            <meta property="og:image" content="https://media.example/thumb.jpg">
            <script type="application/ld+json">
            {"@type": "SocialMediaPosting", "author": {"name": "Fixture Author"}}
            </script></head>
            <body><video data-sources='[{"src": "https://media.example/video/720.mp4",
              "type": "video/mp4", "data-bitrate": "2000"}]'
              data-captions-url="https://media.example/cc/en.vtt"></video></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = LinkedInIE(http(transfer)).extract(url)
        assertEquals(postId, info.id)
        assertEquals("Fixture LinkedIn Post", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("Fixture Author", info.channel)
        assertEquals(1, info.formats.size)
        assertEquals(2_000_000.0, info.formats.single().tbr)
        assertEquals("https://media.example/cc/en.vtt", info.subtitles.single().formats.single().url)
    }

    // ------------------------------------------------------------- typed walls

    @Test
    fun cookieClassesFailTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            LinkedInLearningIE(http(transfer())).extract(
                "https://www.linkedin.com/learning/fixture-course/fixture-lesson",
            )
        }
        assertFailsWith<ExtractionError.LoginRequired> {
            LinkedInLearningCourseIE(http(transfer())).extract("https://www.linkedin.com/learning/fixture-course")
        }
        assertFailsWith<ExtractionError.LoginRequired> {
            LinkedInEventsIE(http(transfer())).extract("https://www.linkedin.com/events/7084656651378536448/comments/")
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun postIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.linkedin.com/posts/mishalkhawaja_fixture-ugcPost-$postId-mM20"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(postId),
                "title" to Expect.Value("Fixture LinkedIn Post"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><head><meta property="og:title" content="Fixture LinkedIn Post"></head>
                        <body><video data-sources='[{"src": "https://media.example/video/720.mp4",
                          "type": "video/mp4", "data-bitrate": "2000"}]'></video></body></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> LinkedInIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
