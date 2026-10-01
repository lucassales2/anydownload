package com.anydownload.core.extract.prx

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the PRX subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class PrxIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val storyId = "399200"

    private fun storyJson(pieces: Int) = """
        {"id": $storyId, "title": "Fixture Story", "description": "<p>Fixture description</p>",
         "releasedAt": "2021-12-23T05:00:00Z", "duration": 1004,
         "_embedded": {
           "prx:image": {"id": 1, "width": 100, "height": 50,
                         "_links": {"enclosure": {"href": "https://media.example/story.jpg"}}},
           "prx:account": {"id": 220986, "name": "Fixture Account"},
           "prx:series": {"id": 38057, "title": "Fixture Series"},
           "prx:audio": {"_embedded": {"prx:items": [
             ${(1..pieces).joinToString(",") { n ->
                 """{"id": $n, "position": $n, "label": "Part $n",
                    "contentType": "audio/mpeg", "bitRate": 128, "frequency": 44100,
                    "duration": 530, "size": 1000,
                    "_links": {"enclosure": {"href": "https://media.example/audio/part$n.mp3"}}}"""
             }}
           ]}}}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            PRXStoryIE(http(transfer())) to "https://beta.prx.org/stories/$storyId",
            PRXStoryIE(http(transfer())) to "https://listen.prx.org/stories/$storyId",
            PRXSeriesIE(http(transfer())) to "https://beta.prx.org/series/36252",
            PRXAccountIE(http(transfer())) to "https://beta.prx.org/accounts/206",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(PRXSeriesIE(http(transfer())).suitable("https://beta.prx.org/accounts/206"))
    }

    // ------------------------------------------------------------------ story

    @Test
    fun singlePieceStoryYieldsFormats() = runTest {
        val url = "https://beta.prx.org/stories/$storyId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://cms.prx.org/api/v1/stories/$storyId",
                contentType = "application/json",
                body = storyJson(pieces = 1),
            ),
        )
        val info = PRXStoryIE(http(transfer)).extract(url)
        assertEquals(storyId, info.id)
        assertEquals("Fixture Story", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("Fixture Account", info.channel)
        assertEquals(1004.0, info.duration)
        assertEquals("20211223", info.uploadDate)
        assertEquals("https://media.example/story.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("none", info.formats.single().vcodec)
        assertEquals("https://media.example/audio/part1.mp3", info.formats.single().url)
    }

    @Test
    fun multiPieceStoryYieldsMediaItems() = runTest {
        val url = "https://beta.prx.org/stories/$storyId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://cms.prx.org/api/v1/stories/$storyId",
                contentType = "application/json",
                body = storyJson(pieces = 2),
            ),
        )
        val info = PRXStoryIE(http(transfer)).extract(url)
        assertEquals(2, info.media.size)
        assertEquals("${storyId}_part1", info.media[0].mediaId)
        assertEquals(530.0, info.media[0].duration)
        assertEquals("https://media.example/audio/part2.mp3", info.media[1].formats.single().url)
    }

    // --------------------------------------------------------------- listings

    @Test
    fun seriesListingYieldsStoryEntries() = runTest {
        val url = "https://beta.prx.org/series/36252"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://cms.prx.org/api/v1/series/36252",
                contentType = "application/json",
                body = """
                    {"id": 36252, "title": "Fixture Series",
                     "_embedded": {"prx:account": {"id": 206, "name": "Fixture Station"}}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://cms.prx.org/api/v1/series/36252/stories?page=1&per=100",
                contentType = "application/json",
                body = """
                    {"count": 1, "total": 1,
                     "_embedded": {"prx:items": [{"id": 326414, "title": "Fixture Story"}]}}
                """.trimIndent(),
            ),
        )
        val info = PRXSeriesIE(http(transfer)).extract(url)
        assertEquals("Fixture Series", info.title)
        assertEquals("Fixture Station", info.channel)
        assertEquals(1, info.entries.size)
        assertEquals("https://beta.prx.org/stories/326414", info.entries.single().url)
    }

    @Test
    fun accountListingChainsSeriesAndStories() = runTest {
        val url = "https://beta.prx.org/accounts/206"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://cms.prx.org/api/v1/accounts/206",
                contentType = "application/json",
                body = """{"id": 206, "name": "Fixture Station"}""",
            ),
            FixtureRoute(
                urlPattern = "https://cms.prx.org/api/v1/accounts/206/series?page=1&per=100",
                contentType = "application/json",
                body = """{"count": 1, "total": 1, "_embedded": {"prx:items": [{"id": 36252}]}}""",
            ),
            FixtureRoute(
                urlPattern = "https://cms.prx.org/api/v1/accounts/206/stories?page=1&per=100",
                contentType = "application/json",
                body = """{"count": 1, "total": 1, "_embedded": {"prx:items": [{"id": 326414}]}}""",
            ),
        )
        val info = PRXAccountIE(http(transfer)).extract(url)
        assertEquals("Fixture Station", info.channel)
        assertEquals(2, info.entries.size)
        assertEquals("https://beta.prx.org/series/36252", info.entries[0].url)
        assertEquals("https://beta.prx.org/stories/326414", info.entries[1].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun storyIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://beta.prx.org/stories/$storyId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(storyId),
                "title" to Expect.Value("Fixture Story"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://cms.prx.org/api/v1/stories/$storyId",
                    contentType = "application/json",
                    body = storyJson(pieces = 1),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> PRXStoryIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}

