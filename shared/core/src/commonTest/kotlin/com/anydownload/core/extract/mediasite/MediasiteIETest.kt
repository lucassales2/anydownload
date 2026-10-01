package com.anydownload.core.extract.mediasite

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
 * Fixture cases for the Mediasite subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class MediasiteIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val resourceId = "2db6c271681e4f199af3c60d1f82869b1d"
    private val playUrl = "https://mediasite.example/Mediasite/Play/$resourceId"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            MediasiteIE(http(transfer())) to playUrl,
            MediasiteIE(http(transfer())) to
                "https://mediasite.example/Mediasite/Showcase/livebroadcast/Presentation/$resourceId",
            MediasiteCatalogIE(http(transfer())) to
                "http://events7.example/Mediasite/Catalog/Full/$resourceId",
            MediasiteNamedCatalogIE(http(transfer())) to
                "https://msite.example/Mediasite/Catalog/catalogs/fixture-catalog",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(MediasiteIE(http(transfer())).suitable("https://mediasite.example/Mediasite/Catalog/Full/x"))
    }

    // --------------------------------------------------------------- player

    @Test
    fun playerOptionsYieldFormatsAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = playUrl,
                contentType = "text/html",
                body = "<html><body><div id=\"ServicePath\">/Mediasite/PlayerService/PlayerService.svc/json</div></body></html>",
            ),
            FixtureRoute(
                urlPattern = "/Mediasite/PlayerService/PlayerService.svc/json/GetPlayerOptions",
                method = "POST",
                contentType = "application/json",
                body = """
                    {"d": {"Presentation": {
                      "Title": "Fixture Lecture", "Description": "Fixture description",
                      "Duration": 7713088, "UnixTime": 1413309600000,
                      "Streams": [
                        {"StreamType": 0, "ThumbnailUrl": "/thumbs/main.jpg",
                         "VideoUrls": [{"Location": "https://media.example/hls/master.m3u8",
                                        "MimeType": "application/x-mpegURL"}]},
                        {"StreamType": 2,
                         "VideoUrls": [{"Location": "https://media.example/video/slide.mp4",
                                        "MimeType": "video/mp4"}]}]}}}
                """.trimIndent(),
            ),
        )
        val info = MediasiteIE(http(transfer)).extract(playUrl)
        assertEquals(resourceId, info.id)
        assertEquals("Fixture Lecture", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(7713.088, info.duration)
        assertEquals("20141014", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats.first { it.formatId == "video1-0.0" }.protocol)
        assertEquals(-10, info.formats.first { it.formatId == "slide-1.0" }.preference)
        assertEquals("https://mediasite.example/thumbs/main.jpg", info.thumbnails.first().url)
    }

    // -------------------------------------------------------------- catalog

    @Test
    fun catalogPostYieldsEntries() = runTest {
        val url = "http://events7.example/Mediasite/Catalog/Full/$resourceId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = "<html><body><script>AntiForgeryToken: 'fake_token'</script></body></html>",
            ),
            FixtureRoute(
                urlPattern = "http://events7.example/Mediasite/Catalog/Data/GetPresentationsForFolder",
                method = "POST",
                requestBodyContains = "\"CatalogId\": \"$resourceId\"",
                contentType = "application/json",
                body = """
                    {"CurrentFolder": {"Name": "Fixture Folder"},
                     "PresentationDetailsList": [{"Id": "aaaabbbbccccddddeeeeffff00001111"}]}
                """.trimIndent(),
            ),
        )
        val info = MediasiteCatalogIE(http(transfer)).extract(url)
        assertEquals(resourceId, info.id)
        assertEquals("Fixture Folder", info.title)
        assertEquals(1, info.entries.size)
        assertEquals(
            "http://events7.example/Mediasite/Play/aaaabbbbccccddddeeeeffff00001111",
            info.entries.single().url,
        )
    }

    @Test
    fun namedCatalogRedirects() = runTest {
        val url = "https://msite.example/Mediasite/Catalog/catalogs/fixture-catalog"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = "<html><body><script>CatalogId: '9518c4a6c5cf4993b21cbd53e828a925'</script></body></html>",
            ),
        )
        val info = MediasiteNamedCatalogIE(http(transfer)).extract(url)
        assertEquals(
            "https://msite.example/Mediasite/Catalog/Full/9518c4a6c5cf4993b21cbd53e828a925",
            info.redirectUrl,
        )
    }

    // --------------------------------------------------------------- harness

    @Test
    fun playerOptionsIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = playUrl,
            infoDict = mapOf(
                "id" to Expect.Value(resourceId),
                "title" to Expect.Value("Fixture Lecture"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = playUrl,
                    contentType = "text/html",
                    body = "<html><body><div id=\"ServicePath\">/Mediasite/PlayerService/PlayerService.svc/json</div></body></html>",
                ),
                FixtureRoute(
                    urlPattern = "/Mediasite/PlayerService/PlayerService.svc/json/GetPlayerOptions",
                    method = "POST",
                    contentType = "application/json",
                    body = """
                        {"d": {"Presentation": {"Title": "Fixture Lecture",
                          "Streams": [{"StreamType": 0,
                            "VideoUrls": [{"Location": "https://media.example/hls/master.m3u8",
                                           "MimeType": "application/x-mpegURL"}]}]}}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MediasiteIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
