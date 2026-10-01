package com.anydownload.core.extract.rutube

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
 * Fixture cases for the Rutube subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class RutubeIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "3eac3b4561676c17df9132a9a1e62e3e"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            RutubeIE(http(transfer())) to "https://rutube.ru/video/$videoId/",
            RutubeIE(http(transfer())) to "https://rutube.ru/play/embed/$videoId",
            RutubeEmbedIE(http(transfer())) to "https://rutube.ru/video/embed/6722881",
            RutubeTagsIE(http(transfer())) to "https://rutube.ru/tags/video/1800/",
            RutubeMovieIE(http(transfer())) to "https://rutube.ru/metainfo/tv/4567",
            RutubePersonIE(http(transfer())) to "https://rutube.ru/video/person/313878/",
            RutubePlaylistIE(http(transfer())) to "https://rutube.ru/plst/308547/",
            RutubeChannelIE(http(transfer())) to "https://rutube.ru/channel/639184/videos/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(RutubeTagsIE(http(transfer())).suitable("https://rutube.ru/video/person/1/"))
    }

    // ----------------------------------------------------------------- video

    @Test
    fun videoApisYieldFormatsAndMetadata() = runTest {
        val url = "https://rutube.ru/video/$videoId/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://rutube.ru/api/video/$videoId/?format=json",
                contentType = "application/json",
                body = """
                    {"id": "$videoId", "title": "Fixture Rutube", "description": "Fixture description",
                     "duration": 81, "created_ts": "2013-10-16T00:00:00Z", "hits": 100,
                     "is_adult": false, "is_livestream": false,
                     "thumbnail_url": "https://media.example/thumb.jpg",
                     "author": {"id": 29790, "name": "Fixture Author"}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://rutube.ru/api/play/options/$videoId/?format=json",
                contentType = "application/json",
                body = """
                    {"video_balancer": {"hls": "https://media.example/hls/master.m3u8",
                                        "http": "https://media.example/video/720.mp4"},
                     "captions": [{"code": "ru", "file": "https://media.example/sub/ru.vtt",
                                   "langTitle": "Russian"}]}
                """.trimIndent(),
            ),
        )
        val info = RutubeIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Rutube", info.title)
        assertEquals("Fixture Author", info.uploader)
        assertEquals("20131016", info.uploadDate)
        assertEquals(100L, info.viewCount)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats.first { it.formatId == "hls" }.protocol)
        assertEquals("ru", info.subtitles.single().language)
    }

    // ----------------------------------------------------------------- embed

    @Test
    fun embedOptionsResolveTheEffectiveVideo() = runTest {
        val embedId = "6722881"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://rutube.ru/api/play/options/$embedId/?format=json",
                contentType = "application/json",
                body = """
                    {"effective_video": "$videoId",
                     "video_balancer": {"hls": "https://media.example/hls/master.m3u8"}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://rutube.ru/api/video/$videoId/?format=json",
                contentType = "application/json",
                body = """{"id": "$videoId", "title": "Fixture Embed"}""",
            ),
        )
        val info = RutubeEmbedIE(http(transfer)).extract("https://rutube.ru/video/embed/$embedId")
        assertEquals(videoId, info.id)
        assertEquals("Fixture Embed", info.title)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // -------------------------------------------------------------- playlist

    @Test
    fun tagListYieldsEntries() = runTest {
        val url = "https://rutube.ru/tags/video/1800/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://rutube.ru/api/tags/video/1800/?page=1&format=json",
                contentType = "application/json",
                body = """
                    {"results": [{"id": "$videoId", "title": "Fixture Video",
                                  "video_url": "https://rutube.ru/video/$videoId/"}],
                     "has_next": false}
                """.trimIndent(),
            ),
        )
        val info = RutubeTagsIE(http(transfer)).extract(url)
        assertEquals("1800", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://rutube.ru/video/$videoId/", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://rutube.ru/video/$videoId/"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Rutube"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://rutube.ru/api/video/$videoId/?format=json",
                    contentType = "application/json",
                    body = """{"id": "$videoId", "title": "Fixture Rutube"}""",
                ),
                FixtureRoute(
                    urlPattern = "https://rutube.ru/api/play/options/$videoId/?format=json",
                    contentType = "application/json",
                    body = """{"video_balancer": {"hls": "https://media.example/hls/master.m3u8"}}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RutubeIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
