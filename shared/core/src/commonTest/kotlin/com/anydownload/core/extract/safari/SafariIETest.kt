package com.anydownload.core.extract.safari

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Safari subset. Ids and media paths are synthesized on
 * `media.example`; the Kaltura dispatch reuses the port's Kaltura fixture
 * shape. No cookie, token, or signed URL appears.
 */
class SafariIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videosUrl =
        "https://www.safaribooksonline.com/videos/python-programming-language/9780134217314/9780134217314-PYMC_13_00"
    private val libraryUrl =
        "https://www.safaribooksonline.com/library/view/hadoop-fundamentals-livelessons/9780133392838/part00.html"
    private val apiUrl =
        "https://www.safaribooksonline.com/api/v1/book/9780133392838/chapter/part00.html"
    private val courseUrl =
        "https://www.safaribooksonline.com/library/view/hadoop-fundamentals-livelessons/9780133392838/"

    private val mwEmbedRoute = FixtureRoute(
        urlPattern = "https://cdnapisec.kaltura.com/html5/html5lib/v2.37.1/mwEmbedFrame.php?*",
        contentType = "text/html",
        body = """
            <html><script>window.kalturaIframePackageData = {"entryResult": {
              "meta": {"id": "0_qbqx90ic", "name": "Fixture Safari",
                "description": "Fixture description",
                "dataUrl": "https://cdnapisec.kaltura.com/p/1926081/sp/192608100/playManifest/entryId/0_qbqx90ic/format/url/protocol/http",
                "duration": 100, "createdAt": 1437758058, "plays": 10,
                "thumbnailUrl": "https://img.example/safari.jpg", "userId": "stork"},
              "contextData": {"flavorAssets": [{"id": "flavor1", "status": 2, "fileExt": "mp4",
                "bitrate": 1500, "frameRate": 25, "size": 1048576, "containerFormat": "isom",
                "height": 720, "width": 1280, "videoCodecId": "avc1"}]}}}</script></html>
        """.trimIndent(),
    )

    private val libraryPage = FixtureRoute(
        urlPattern = "https://www.safaribooksonline.com/library/view/hadoop-fundamentals-livelessons/*",
        contentType = "text/html",
        body = """
            <html><body>
            <div data-reference-id="0_qbqx90ic" data-partner-id="1926081" data-ui-id="29375172"></div>
            </body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val safari = SafariIE(http(transfer()))
        assertTrue(safari.suitable(libraryUrl))
        assertTrue(safari.suitable(videosUrl))
        assertTrue(safari.suitable(
            "https://learning.oreilly.com/videos/hadoop-fundamentals-livelessons/9780133392838/9780133392838-00_SeriesIntro",
        ))
        assertFalse(safari.suitable(courseUrl))

        val api = SafariApiIE(http(transfer()))
        assertTrue(api.suitable(apiUrl))
        assertFalse(api.suitable(libraryUrl))

        val course = SafariCourseIE(http(transfer()))
        assertTrue(course.suitable(courseUrl))
        assertTrue(course.suitable("https://www.safaribooksonline.com/api/v1/book/9781449396459/?override_format=json"))
        assertTrue(course.suitable("http://techbus.safaribooksonline.com/9780134426365"))
        assertFalse(course.suitable(libraryUrl), "the course class must yield part URLs")
        assertFalse(course.suitable(apiUrl), "the course class must yield chapter API URLs")
        assertFalse(course.suitable("https://www.example.com/library/view/x/1/"))
    }

    // ------------------------------------------------------------- safari

    @Test
    fun videosUrlDispatchesToKaltura() = runTest {
        val info = SafariIE(http(transfer(mwEmbedRoute))).extract(videosUrl)
        assertEquals("0_qbqx90ic", info.id)
        assertEquals("Fixture Safari", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("stork", info.uploader)
        assertEquals("https://img.example/safari.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("mp4-1500", info.formats[0].formatId)
        assertEquals("hls", info.formats[1].formatId)
        assertEquals(videosUrl, info.webpageUrl)
    }

    @Test
    fun videosUrlIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videosUrl,
            infoDict = mapOf(
                "id" to Expect.Value("0_qbqx90ic"),
                "title" to Expect.Value("Fixture Safari"),
                "duration" to Expect.Value(100.0),
            ),
            routes = listOf(mwEmbedRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SafariIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun libraryPageReadsTheDataAttributes() = runTest {
        val info = SafariIE(http(transfer(libraryPage, mwEmbedRoute))).extract(libraryUrl)
        assertEquals("0_qbqx90ic", info.id)
        assertEquals("Fixture Safari", info.title)
        assertEquals(2, info.formats.size)
    }

    // -------------------------------------------------------------- api

    @Test
    fun chapterApiRewritesTheWebUrl() = runTest {
        val apiRoute = FixtureRoute(
            urlPattern = "https://www.safaribooksonline.com/api/v1/book/9780133392838/chapter/part00.html",
            contentType = "application/json",
            body = """
                {"web_url": "https://www.safaribooksonline.com/library/view/hadoop-fundamentals-livelessons/9780133392838/part00.html",
                 "natural_key": ["9780133392838", "00_SeriesIntro.html"]}
            """.trimIndent(),
        )
        val info = SafariApiIE(http(transfer(apiRoute, mwEmbedRoute))).extract(apiUrl)
        assertEquals("0_qbqx90ic", info.id)
        assertEquals("Fixture Safari", info.title)
        assertEquals(apiUrl, info.webpageUrl)
    }

    // ------------------------------------------------------------- course

    @Test
    fun courseYieldsTheChapterEntries() = runTest {
        val courseRoute = FixtureRoute(
            urlPattern = "https://learning.oreilly.com/api/v1/book/9780133392838/?*",
            contentType = "application/json",
            body = """
                {"title": "Fixture Course", "chapters": [
                  "https://www.safaribooksonline.com/api/v1/book/9780133392838/chapter/part00.html",
                  "https://www.safaribooksonline.com/api/v1/book/9780133392838/chapter/part01.html"]}
            """.trimIndent(),
        )
        val info = SafariCourseIE(http(transfer(courseRoute))).extract(courseUrl)
        assertEquals("9780133392838", info.id)
        assertEquals("Fixture Course", info.title)
        assertEquals(2, info.entries.size)
        assertEquals(
            "https://www.safaribooksonline.com/api/v1/book/9780133392838/chapter/part00.html",
            info.entries[0].url,
        )
    }

    @Test
    fun courseIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val courseRoute = FixtureRoute(
            urlPattern = "https://learning.oreilly.com/api/v1/book/9780133392838/?*",
            contentType = "application/json",
            body = """{"title": "Fixture Course", "chapters": []}""",
        )
        val case = ExtractorCase(
            url = courseUrl,
            infoDict = mapOf(
                "id" to Expect.Value("9780133392838"),
                "title" to Expect.Value("Fixture Course"),
            ),
            routes = listOf(courseRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SafariCourseIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
