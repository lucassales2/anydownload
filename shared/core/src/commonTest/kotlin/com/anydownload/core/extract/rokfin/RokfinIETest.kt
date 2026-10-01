package com.anydownload.core.extract.rokfin

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
 * Fixture cases for the Rokfin subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class RokfinIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val postUrl = "https://www.rokfin.com/post/57548/fixture"
    private val postApi = "https://prod-api-v2.production.rokfin.com/api/v2/public/post/57548"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            RokfinIE(http(transfer())) to postUrl,
            RokfinIE(http(transfer())) to "https://rokfin.com/stream/10543/fixture",
            RokfinStackIE(http(transfer())) to "https://www.rokfin.com/stack/271/fixture",
            RokfinChannelIE(http(transfer())) to "https://rokfin.com/TheConvoCouch",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(RokfinChannelIE(http(transfer())).suitable("https://rokfin.com/feed"))
    }

    // ------------------------------------------------------------------- post

    @Test
    fun postApiYieldsTheM3u8FormatAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = postApi,
                contentType = "application/json",
                body = """
                    {"title": "Fixture Post", "description": "Fixture description",
                     "url": "https://media.example/hls/master.m3u8",
                     "thumbnail": "https://img.example/thumb.jpg",
                     "postedAtMilli": 1634998029000,
                     "likeCount": 12, "dislikeCount": 1,
                     "createdBy": {"username": "fixtureuser", "name": "Fixture Channel", "id": 65429},
                     "content": [{"duration": 213.0}]}
                """.trimIndent(),
            ),
        )
        val info = RokfinIE(http(transfer)).extract(postUrl)
        assertEquals("post/57548", info.id)
        assertEquals("Fixture Post", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(213.0, info.duration)
        assertEquals("Fixture Channel", info.channel)
        assertEquals("65429", info.channelId)
        assertEquals("20211023", info.uploadDate)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun storyboardFallbackBuildsTheStreamUrl() = runTest {
        val url = "https://rokfin.com/stream/10543/fixture"
        val api = "https://prod-api-v2.production.rokfin.com/api/v2/public/stream/10543"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = api,
                contentType = "application/json",
                body = """
                    {"title": "Fixture Stream",
                     "timelineUrl": "https://image.v.rokfin.com/fixture-capture/storyboard.vtt",
                     "creationDateTime": "2021-11-02T12:00:00Z"}
                """.trimIndent(),
            ),
        )
        val info = RokfinIE(http(transfer)).extract(url)
        assertEquals("https://stream.v.rokfin.com/fixture-capture.m3u8", info.formats.single().url)
        assertEquals("20211102", info.uploadDate)
    }

    @Test
    fun premiumPostWithoutUrlFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = postApi,
                contentType = "application/json",
                body = """{"title": "Premium", "premiumPlan": {"name": "premium"}}""",
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            RokfinIE(http(transfer)).extract(postUrl)
        }
    }

    // ----------------------------------------------------------------- stacks

    @Test
    fun stackApiYieldsChildEntries() = runTest {
        val url = "https://www.rokfin.com/stack/271/fixture"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://prod-api-v2.production.rokfin.com/api/v2/public/stack/271",
                contentType = "application/json",
                body = """
                    {"title": "Fixture Stack", "content": [
                      {"mediaType": "video", "id": 57548,
                       "content": {"contentTitle": "Fixture Video"}},
                      {"mediaType": "stream", "mediaId": 10543}]}
                """.trimIndent(),
            ),
        )
        val info = RokfinStackIE(http(transfer)).extract(url)
        assertEquals("271", info.id)
        assertEquals("Fixture Stack", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://rokfin.com/post/57548", info.entries[0].url)
        assertEquals("https://rokfin.com/stream/10543", info.entries[1].url)
    }

    // --------------------------------------------------------------- channels

    @Test
    fun channelPagesThePostsEndpoint() = runTest {
        val url = "https://rokfin.com/TheConvoCouch"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://prod-api-v2.production.rokfin.com/api/v2/public/user/TheConvoCouch",
                contentType = "application/json",
                body = """{"id": 12071, "description": "Fixture channel"}""",
            ),
            FixtureRoute(
                urlPattern = "https://prod-api-v2.production.rokfin.com/api/v2/public/user/TheConvoCouch/posts?page=0&size=50",
                contentType = "application/json",
                body = """
                    {"content": [{"mediaType": "video", "id": 1}], "last": true}
                """.trimIndent(),
            ),
        )
        val info = RokfinChannelIE(http(transfer)).extract(url)
        assertEquals("12071-new", info.id)
        assertEquals("TheConvoCouch - New", info.title)
        assertEquals(1, info.entries.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun postApiIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = postUrl,
            infoDict = mapOf(
                "id" to Expect.Value("post/57548"),
                "title" to Expect.Value("Fixture Post"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = postApi,
                    contentType = "application/json",
                    body = """{"title": "Fixture Post", "url": "https://media.example/hls/master.m3u8"}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RokfinIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun stackApiIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.rokfin.com/stack/271/fixture"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("271"),
                "title" to Expect.Value("Fixture Stack"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://prod-api-v2.production.rokfin.com/api/v2/public/stack/271",
                    contentType = "application/json",
                    body = """{"title": "Fixture Stack", "content": [{"mediaType": "video", "id": 1}]}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RokfinStackIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
