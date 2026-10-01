package com.anydownload.core.extract.boosty

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
 * Fixture cases for the Boosty subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class BoostyIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val okVideoId = "d7473824-352e-48e2-ae53-d4aa39459968"
    private val postUrl = "https://boosty.to/kuplinov/posts/e55d050c-e3bb-4873-a7db-ac7a49b40c38"
    private val externalUrl = "https://boosty.to/futuremusicproduction/posts/32a8cae2-3252-49da-b285-0e014bc6e565"
    private val apiRoute = "https://api.boosty.to/v1/blog/kuplinov/post/e55d050c-e3bb-4873-a7db-ac7a49b40c38"

    private val okVideoJson = """
        {"title": "Fixture Boosty Post", "hasAccess": true,
         "user": {"name": "Kuplinov", "id": 7958701},
         "createdAt": 1655031975, "publishTime": 1655049000, "updatedAt": 1743328648,
         "tags": [{"title": "tag1"}], "count": {"likes": 10},
         "data": [{"type": "ok_video", "id": "$okVideoId", "title": "Fixture Video",
           "duration": 105, "viewsCounter": 42, "preview": "https://media.example/preview.jpg",
           "playerUrls": [
             {"type": "full_hd", "url": "https://media.example/full_hd.mp4"},
             {"type": "hls", "url": "https://media.example/master.m3u8"}
           ]}]}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val extractor = BoostyIE(http(transfer()))
        assertTrue(extractor.suitable(postUrl))
        assertTrue(extractor.suitable("https://www.boosty.to/user/posts/123"))
        assertFalse(extractor.suitable("https://www.example.com/user/posts/123"))
    }

    // --------------------------------------------------------------- ok video

    @Test
    fun singleOkVideoYieldsTheFormatRows() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = apiRoute, contentType = "application/json", body = okVideoJson))
        val info = BoostyIE(http(transfer)).extract(postUrl)
        assertEquals(okVideoId, info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Kuplinov", info.channel)
        assertEquals("7958701", info.channelId)
        assertEquals("20220612", info.uploadDate)
        assertEquals(105.0, info.duration)
        assertEquals(42L, info.viewCount)
        assertEquals("https://media.example/preview.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("full_hd", info.formats[0].formatId)
        assertEquals(5, info.formats[0].preference)
        assertEquals("m3u8_native", info.formats[1].protocol)
    }

    @Test
    fun multipleOkVideosBecomeEntries() = runTest {
        val post = """
            {"title": "Fixture Post", "hasAccess": true, "user": {"name": "Fixture", "id": 1},
             "data": [
               {"type": "ok_video", "id": "v1", "title": "One", "playerUrls": [
                 {"type": "high", "url": "https://media.example/one_high.mp4"},
                 {"type": "full_hd", "url": "https://media.example/one_full.mp4"}]},
               {"type": "ok_video", "id": "v2", "title": "Two", "playerUrls": [
                 {"type": "low", "url": "https://media.example/two_low.mp4"}]}
             ]}
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = apiRoute, contentType = "application/json", body = post))
        val info = BoostyIE(http(transfer)).extract(postUrl)
        assertEquals(2, info.entries.size)
        assertEquals("v1", info.entries[0].id)
        assertEquals("One", info.entries[0].title)
        assertEquals("https://media.example/one_full.mp4", info.entries[0].url)
        assertEquals("https://media.example/two_low.mp4", info.entries[1].url)
    }

    // -------------------------------------------------------------- external

    @Test
    fun singleExternalVideoBecomesARedirect() = runTest {
        val post = """
            {"title": "Fixture Post", "hasAccess": true, "user": {"name": "Fixture", "id": 1},
             "data": [{"type": "video", "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"}]}
        """.trimIndent()
        val externalApi = "https://api.boosty.to/v1/blog/futuremusicproduction/post/32a8cae2-3252-49da-b285-0e014bc6e565"
        val transfer = transfer(FixtureRoute(urlPattern = externalApi, contentType = "application/json", body = post))
        val info = BoostyIE(http(transfer)).extract(externalUrl)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", info.redirectUrl)
    }

    // ------------------------------------------------------------------ walls

    @Test
    fun subscriptionPostFailsTypedAsLoginRequired() = runTest {
        val post = """{"title": "Fixture", "hasAccess": false, "data": []}"""
        val transfer = transfer(FixtureRoute(urlPattern = apiRoute, contentType = "application/json", body = post))
        assertFailsWith<ExtractionError.LoginRequired> {
            BoostyIE(http(transfer)).extract(postUrl)
        }
    }

    @Test
    fun postWithoutVideosFailsTyped() = runTest {
        val post = """{"title": "Fixture", "hasAccess": true, "data": []}"""
        val transfer = transfer(FixtureRoute(urlPattern = apiRoute, contentType = "application/json", body = post))
        assertFailsWith<ExtractionError.Unavailable> {
            BoostyIE(http(transfer)).extract(postUrl)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun okVideoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = postUrl,
            infoDict = mapOf(
                "id" to Expect.Value(okVideoId),
                "title" to Expect.Value("Fixture Video"),
                "upload_date" to Expect.Value("20220612"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(FixtureRoute(urlPattern = apiRoute, contentType = "application/json", body = okVideoJson)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> BoostyIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun externalVideoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val post = """
            {"title": "Fixture Post", "hasAccess": true, "user": {"name": "Fixture", "id": 1},
             "data": [{"type": "video", "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"}]}
        """.trimIndent()
        val externalApi = "https://api.boosty.to/v1/blog/futuremusicproduction/post/32a8cae2-3252-49da-b285-0e014bc6e565"
        val case = ExtractorCase(
            url = externalUrl,
            infoDict = mapOf("id" to Expect.Value("32a8cae2-3252-49da-b285-0e014bc6e565")),
            routes = listOf(FixtureRoute(urlPattern = externalApi, contentType = "application/json", body = post)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> BoostyIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
