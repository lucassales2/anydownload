package com.anydownload.core.extract.sohu

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
 * Fixture cases for the Sohu subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and the CDN keys are fake.
 */
class SohuIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val mytvUrl = "http://my.tv.sohu.com/us/232799889/78693464.shtml"
    private val multiUrl = "http://my.tv.sohu.com/pl/8384802/78910339.shtml"
    private val vUrl = "https://tv.sohu.com/v/dXMvMjMyNzk5ODg5Lzc4NjkzNDY0LnNodG1s.html"

    private fun page(vid: String, title: String = "Fixture Sohu Title - 搜狐视频") = """
        <html><head><meta property="og:title" content="$title"></head>
        <body><script>var vid = "$vid"; publishTime: "2015-03-05 15:00";</script></body></html>
    """.trimIndent()

    private val singleJson = """
        {"play": 1, "allot": "cdn.example", "wm_data": {"wm_username": "Fixture Uploader"},
         "tv_application_time": "2015-03-05 15:00",
         "data": {"totalBlocks": 1, "norVid": 78693464,
           "clipsURL": ["https://media.example/clip.mp4"], "su": ["fake_su"],
           "clipsBytes": [123], "clipsDuration": [213.0], "width": 1280, "height": 720,
           "fps": 25, "coverImg": "https://media.example/cover.jpg", "tag": "a,b"}}
    """.trimIndent()

    private val cdnRoute = FixtureRoute(
        urlPattern = "http://cdn.example/*",
        contentType = "application/json",
        body = """{"url": "https://media.example/video.mp4", "nid": "1"}""",
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val sohu = SohuIE(http(transfer()))
        assertTrue(sohu.suitable("http://tv.sohu.com/20130724/n382479172.shtml"))
        assertTrue(sohu.suitable(mytvUrl))
        assertFalse(sohu.suitable(vUrl))

        val sohuV = SohuVIE(http(transfer()))
        assertTrue(sohuV.suitable(vUrl))
        assertFalse(sohuV.suitable(mytvUrl))
        assertFalse(sohuV.suitable("https://www.example.com/v/abc.html"))
    }

    // ---------------------------------------------------------------- single

    @Test
    fun singlePartYieldsTheFormatRow() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$mytvUrl*", contentType = "text/html", body = page("78693464")),
            FixtureRoute(
                urlPattern = "http://my.tv.sohu.com/play/videonew.do?vid=78693464",
                contentType = "application/json",
                body = singleJson,
            ),
            cdnRoute,
        )
        val info = SohuIE(http(transfer)).extract(mytvUrl)
        assertEquals("78693464", info.id)
        assertEquals("Fixture Sohu Title", info.title)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals("20150305", info.uploadDate)
        assertEquals("https://media.example/cover.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("nor", info.formats[0].formatId)
        assertEquals("https://media.example/video.mp4", info.formats[0].url)
        assertEquals(123L, info.formats[0].filesize)
        assertEquals(1280L, info.formats[0].width)
        assertEquals(720L, info.formats[0].height)
        assertEquals(25.0, info.formats[0].fps)
    }

    @Test
    fun multipartVideoBecomesSelectableMedia() = runTest {
        val multiJson = """
            {"play": 1, "allot": "cdn.example",
             "data": {"totalBlocks": 2, "norVid": 78910339,
               "clipsURL": ["https://media.example/c1.mp4", "https://media.example/c2.mp4"],
               "su": ["s1", "s2"], "clipsBytes": [1, 2], "clipsDuration": [294.0, 300.0]}}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "$multiUrl*", contentType = "text/html", body = page("78910339")),
            FixtureRoute(
                urlPattern = "http://my.tv.sohu.com/play/videonew.do?vid=78910339",
                contentType = "application/json",
                body = multiJson,
            ),
            cdnRoute,
        )
        val info = SohuIE(http(transfer)).extract(multiUrl)
        assertEquals("78910339", info.id)
        assertEquals(2, info.media.size)
        assertEquals("78910339_part1", info.media[0].mediaId)
        assertEquals(294.0, info.media[0].duration)
        assertEquals(1, info.media[0].formats.size)
        assertEquals("78910339_part2", info.media[1].mediaId)
    }

    @Test
    fun geoBlockedVideoFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$mytvUrl*", contentType = "text/html", body = page("78693464")),
            FixtureRoute(
                urlPattern = "http://my.tv.sohu.com/play/videonew.do?vid=78693464",
                contentType = "application/json",
                body = """{"play": 0}""",
            ),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            SohuIE(http(transfer)).extract(mytvUrl)
        }
    }

    @Test
    fun statusTwelveFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$mytvUrl*", contentType = "text/html", body = page("78693464")),
            FixtureRoute(
                urlPattern = "http://my.tv.sohu.com/play/videonew.do?vid=78693464",
                contentType = "application/json",
                body = """{"play": 0, "status": 12}""",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            SohuIE(http(transfer)).extract(mytvUrl)
        }
    }

    // --------------------------------------------------------------------- v

    @Test
    fun vFormRedirectsToTheDecodedPage() = runTest {
        val transfer = transfer()
        val info = SohuVIE(http(transfer)).extract(vUrl)
        assertEquals("http://my.tv.sohu.com/us/232799889/78693464.shtml", info.redirectUrl)

        val tvInfo = SohuVIE(http(transfer)).extract(
            "https://tv.sohu.com/v/MjAyMzA2MTQvbjYwMTMxNTE5Mi5zaHRtbA==.html",
        )
        assertEquals("http://tv.sohu.com/20230614/n601315192.shtml", tvInfo.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun singlePartIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = mytvUrl,
            infoDict = mapOf(
                "id" to Expect.Value("78693464"),
                "title" to Expect.Value("Fixture Sohu Title"),
                "upload_date" to Expect.Value("20150305"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "$mytvUrl*", contentType = "text/html", body = page("78693464")),
                FixtureRoute(
                    urlPattern = "http://my.tv.sohu.com/play/videonew.do?vid=78693464",
                    contentType = "application/json",
                    body = singleJson,
                ),
                cdnRoute,
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SohuIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun vFormIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = vUrl,
            infoDict = mapOf("id" to Expect.Value("dXMvMjMyNzk5ODg5Lzc4NjkzNDY0LnNodG1s")),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SohuVIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
