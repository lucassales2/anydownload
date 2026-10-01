package com.anydownload.core.extract.videocampus_sachsen

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
 * Fixture cases for the ViMP subset. Ids, titles, and media paths are
 * synthesized; media lives on the `*.example` hosts and no cookie, token, or
 * signed URL appears.
 */
class VideocampusSachsenIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "fc99c527e4205b121cb7c74433469262"
    private val secondId = "09d4ed029002eb1bdda610f1103dd54c"
    private val tmpId = "e0d6c8ce6e394c18"
    private val displayId = "Fixture-Lecture"

    private fun videoPage() = """
        <html><head>
        <meta property="og:title" content="Fixture Lecture">
        <meta property="og:description" content="Fixture description">
        <meta property="og:image" content="https://media.example/poster.jpg">
        </head><body>
        <video-js data-piwik-title="Fixture Player Title"></video-js>
        </body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val mediaCases = listOf(
            "https://videocampus.sachsen.de/m/$tmpId",
            "https://videocampus.sachsen.de/video/$displayId/$videoId",
            "https://videocampus.sachsen.de/category/video/$displayId/$videoId",
            "https://videocampus.sachsen.de/media/embed?key=$videoId",
            "https://www.hsbi.de/medienportal/video/$displayId/$videoId",
        )
        for (url in mediaCases) {
            assertTrue(VideocampusSachsenIE(http(transfer())).suitable(url), "ViMP must match: $url")
        }
        val playlistCases = listOf(
            "https://www.hsbi.de/medienportal/album/view/aid/208",
            "https://vimp.oth-regensburg.de/channel/Designtheorie-1-SoSe-2020/3",
            "https://videocampus.sachsen.de/category/online-tutorials-onyx/91",
            "https://videocampus.sachsen.de/tag/26902",
        )
        for (url in playlistCases) {
            assertTrue(ViMPPlaylistIE(http(transfer())).suitable(url), "ViMPPlaylist must match: $url")
        }
        assertFalse(VideocampusSachsenIE(http(transfer())).suitable("https://example.com/video/$displayId/$videoId"))
        assertFalse(ViMPPlaylistIE(http(transfer())).suitable("https://example.com/tag/26902"))
        assertFalse(ViMPPlaylistIE(http(transfer())).suitable("https://videocampus.sachsen.de/video/$displayId/$videoId"))
        assertFalse(VideocampusSachsenIE(http(transfer())).suitable("https://videocampus.sachsen.de/tag/26902"))
    }

    // ------------------------------------------------------------------- media

    @Test
    fun videoPageYieldsTheHlsAndHttpFormats() = runTest {
        val url = "https://videocampus.sachsen.de/video/$displayId/$videoId"
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = videoPage()))
        val info = VideocampusSachsenIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Lecture", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("https://media.example/poster.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        val hls = info.formats.first { it.protocol == "m3u8_native" }
        assertEquals(
            "https://videocampus.sachsen.de/media/hlsMedium/key/$videoId/format/auto/ext/mp4/learning/0/path/m3u8",
            hls.url,
        )
        assertEquals("https://videocampus.sachsen.de/getMedium/$videoId.mp4", info.formats[1].url)
    }

    @Test
    fun tmpIdPageScansTheEmbedSrcAndUsesMetaMetadata() = runTest {
        val url = "https://videocampus.sachsen.de/m/$tmpId"
        val page = """
            <html><head><meta property="og:title" content="Fixture Tmp Lecture"></head>
            <body><iframe src="https://videocampus.sachsen.de/media/embed?key=$videoId"></iframe></body></html>
        """.trimIndent()
        val info = VideocampusSachsenIE(http(transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))))
            .extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Tmp Lecture", info.title)
    }

    @Test
    fun embedFormUsesTheVideoJsTitle() = runTest {
        val url = "https://videocampus.sachsen.de/media/embed?key=$videoId"
        val info = VideocampusSachsenIE(http(transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = videoPage()))))
            .extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Player Title", info.title)
        assertEquals(null, info.description)
        assertTrue(info.thumbnails.isEmpty())
    }

    @Test
    fun tmpPageWithoutAnEmbedKeyFailsTyped() = runTest {
        val url = "https://videocampus.sachsen.de/m/$tmpId"
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = "<html><body>no player</body></html>"),
        )
        assertFailsWith<ExtractionError.Malformed> {
            VideocampusSachsenIE(http(transfer)).extract(url)
        }
    }

    // --------------------------------------------------------------- listings

    @Test
    fun albumListingPostsTheVarsBodyAndListsTheVideoLinks() = runTest {
        val url = "https://www.hsbi.de/medienportal/album/view/aid/208"
        val page = "<html><head><title>Fixture Album - Playlists - HSBI-Medienportal</title></head><body></body></html>"
        val boxList = """
            <div class="boxList">
            <a href="/video/Fixture-One/$videoId" data-title="One">One</a>
            <a href="/video/Fixture-Two/$secondId" data-title="Two">Two</a>
            </div>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://www.hsbi.de/medienportal/media/ajax/component/boxList/aid/208*",
                method = "POST",
                contentType = "text/html",
                body = boxList,
                requestBodyContains = "vars%5Balbum%5D=208",
            ),
        )
        val info = ViMPPlaylistIE(http(transfer)).extract(url)
        assertEquals("album-208", info.id)
        assertEquals("Fixture Album - Playlists - HSBI-Medienportal", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.hsbi.de/medienportal/video/Fixture-Two/$secondId", info.entries[1].url)
        val post = transfer.requests.single { it.method == "POST" }
        val body = post.body?.decodeToString().orEmpty()
        assertTrue(body.contains("vars%5Bmode%5D=album"), body)
        assertTrue(body.contains("vars%5Balbum%5D=208"), body)
        assertTrue(body.contains("vars%5Bper_page%5D%5Bthumb%5D=10"), body)
        assertTrue(post.url.contains("page=0&page_only=1"), post.url)
    }

    @Test
    fun listingStopsOnAShortPage() = runTest {
        val url = "https://videocampus.sachsen.de/category/online-tutorials-onyx/91"
        val links = (0 until 10).joinToString("\n") { index ->
            val id = index.toString(16).padStart(32, '0')
            "<a href=\"/video/Fixture-$index/$id\">$index</a>"
        }
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = "<html><head><title>Fixture</title></head></html>"),
            FixtureRoute(
                urlPattern = "https://videocampus.sachsen.de/media/ajax/component/boxList/category/online-tutorials-onyx/category_id/91?page=0&page_only=1",
                method = "POST",
                contentType = "text/html",
                body = links,
                requestBodyContains = "vars%5Bcategory%5D=91",
            ),
            FixtureRoute(
                urlPattern = "https://videocampus.sachsen.de/media/ajax/component/boxList/category/online-tutorials-onyx/category_id/91?page=1&page_only=1",
                method = "POST",
                contentType = "text/html",
                body = "<div>empty</div>",
            ),
        )
        val info = ViMPPlaylistIE(http(transfer)).extract(url)
        assertEquals("category-91", info.id)
        assertEquals(10, info.entries.size)
        val posts = transfer.requests.filter { it.method == "POST" }
        assertEquals(2, posts.size)
        assertTrue(posts[0].url.contains("page=0"), posts[0].url)
        assertTrue(posts[1].url.contains("page=1"), posts[1].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://videocampus.sachsen.de/video/$displayId/$videoId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Lecture"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(FixtureRoute(urlPattern = url, contentType = "text/html", body = videoPage())),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> VideocampusSachsenIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun albumListingIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.hsbi.de/medienportal/album/view/aid/208"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("album-208"),
                "entries" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = "<html><head><title>Fixture Album</title></head></html>",
                ),
                FixtureRoute(
                    urlPattern = "https://www.hsbi.de/medienportal/media/ajax/component/boxList/aid/208*",
                    method = "POST",
                    contentType = "text/html",
                    body = "<a href=\"/video/Fixture-One/$videoId\">One</a>" +
                        "<a href=\"/video/Fixture-Two/$secondId\">Two</a>",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ViMPPlaylistIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
