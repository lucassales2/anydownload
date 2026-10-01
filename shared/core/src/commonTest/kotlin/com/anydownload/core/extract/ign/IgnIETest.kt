package com.anydownload.core.extract.ign

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
 * Fixture cases for the IGN subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class IgnIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoSlug = "the-last-of-us-review"
    private val videoId = "8f862beef863986b2785559b9e1aa599"

    private val videoJson = """
        {"videoId": "$videoId",
         "refs": {"m3u8Url": "https://media.example/hls/master.m3u8"},
         "assets": [{"url": "https://media.example/video/720.mp4", "bitrate": 2000000,
                     "frame_rate": 30, "height": 720, "width": 1280}],
         "system": {"mezzanineUrl": "https://media.example/mezzanine/source.mp4"},
         "thumbnails": [{"url": "https://media.example/thumb.jpg"}],
         "metadata": {"longTitle": "Fixture IGN Video", "description": "Fixture description",
                      "publishDate": "2013-06-05T10:00:00Z", "duration": 440}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            IGNIE(http(transfer())) to "https://www.ign.com/videos/2013/06/05/$videoSlug",
            IGNIE(http(transfer())) to "https://www.pcmag.com/videos/2015/01/06/fixture-video",
            IGNVideoIE(http(transfer())) to "https://me.ign.com/en/videos/112203/video/fixture-video",
            IGNArticleIE(http(transfer())) to "https://me.ign.com/en/feature/15775/fixture-article",
            IGNArticleIE(http(transfer())) to "https://www.ign.com/articles/2014/08/15/fixture-article",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(IGNArticleIE(http(transfer())).suitable("https://www.ign.com/videos/2013/06/05/$videoSlug"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun slugApiYieldsFormatsAndMetadata() = runTest {
        val url = "https://www.ign.com/videos/2013/06/05/$videoSlug"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://apis.ign.com/video/v3/videos/slug/$videoSlug",
                contentType = "application/json",
                body = videoJson,
            ),
        )
        val info = IGNIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture IGN Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(440.0, info.duration)
        assertEquals("20130605", info.uploadDate)
        assertEquals(3, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mezzanine", info.formats[2].formatId)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    // --------------------------------------------------------------- playlist

    @Test
    fun playlistScansTheContentFeedGrid() = runTest {
        val url = "https://www.ign.com/videos?filter=all"
        val page = """
            <html><body>
            <section class="content-feed-grid"><a href="/videos/2013/06/05/$videoSlug">One</a></section>
            <section class="content-feed-grid"><a href="/videos/other-video">Two</a></section>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = "https://www.ign.com/videos?filter=all", contentType = "text/html", body = page))
        val info = IGNIE(http(transfer)).extract(url)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.ign.com/videos/2013/06/05/$videoSlug", info.entries.single().url)
    }

    // ------------------------------------------------------------------ embed

    @Test
    fun embedPageYieldsVideoData() = runTest {
        val url = "https://me.ign.com/en/videos/112203/video/fixture-video"
        val embedUrl = "https://me.ign.com/en/videos/112203/video/embed"
        val escaped = videoJson.replace("\"", "&quot;")
        val page = """
            <html><body>
            <div class="video-player" data-video-id="$videoId"
                 data-settings="{&quot;video&quot;: $escaped}"></div>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = page))
        val info = IGNVideoIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture IGN Video", info.title)
    }

    // ---------------------------------------------------------------- article

    @Test
    fun articleApiYieldsEntries() = runTest {
        val url = "https://me.ign.com/en/feature/15775/fixture-article"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://apis.ign.com/article/v3/articles/slug/fixture-article",
                contentType = "application/json",
                body = """
                    {"articleId": "72113", "metadata": {"headline": "Fixture Article"},
                     "mediaRelations": [{"media": {"metadata": {"url": "https://www.ign.com/videos/2013/06/05/$videoSlug"}}}],
                     "content": ["[ignvideo url=\"https://www.ign.com/videos/other-video\"]"]}
                """.trimIndent(),
            ),
        )
        val info = IGNArticleIE(http(transfer)).extract(url)
        assertEquals("72113", info.id)
        assertEquals("Fixture Article", info.title)
        assertEquals(2, info.entries.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun slugVideoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.ign.com/videos/2013/06/05/$videoSlug"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture IGN Video"),
                "formats" to Expect.Count(3),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://apis.ign.com/video/v3/videos/slug/$videoSlug",
                    contentType = "application/json",
                    body = videoJson,
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> IGNIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
