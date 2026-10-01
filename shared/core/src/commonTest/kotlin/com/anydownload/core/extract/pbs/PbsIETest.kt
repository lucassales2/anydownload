package com.anydownload.core.extract.pbs

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
 * Fixture cases for the PBS subset. Every id, host, and media address is
 * synthesized (`*.example`); no cookie, token, or signed URL appears.
 */
class PbsIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "2365006249"
    private val slugUrl = "https://www.pbs.org/tpt/constitution-usa-peter-sagal/watch/a-more-perfect-union/"

    private val slugPage = """
        <html><head>
        <meta property="og:description" content="Fixture description">
        </head><body>
        <input type="hidden" id="air_date_1" value="2013-05-14">
        <span class="coveplayerid">2365006249</span>
        </body></html>
    """.trimIndent()

    private fun videoData(): String = """
        {"id": "2365006249",
         "title": "A More Perfect Union",
         "program": {"title": "Constitution USA with Peter Sagal"},
         "description": "Fixture description",
         "image_url": "https://img.example/thumb.jpg",
         "duration": 3190,
         "rating": "TV-14",
         "recommended_encoding": {"url": "https://redirect.example/1", "eeid": "1"},
         "chapters": [{"start_time": 0, "duration": 10000, "title": "Chapter 1"}],
         "cc": {"en": "https://media.example/captions.vtt"}}
    """.trimIndent()

    private fun playerRoutes(
        redirectStatus: String = "ok",
        httpCode: Int? = null,
        message: String? = null,
    ): List<FixtureRoute> = listOf(
        FixtureRoute(
            urlPattern = "http://player.pbs.org/widget/partnerplayer/$videoId",
            contentType = "text/html",
            body = "<html><script>PBS.videoData = ${videoData()};</script></html>",
        ),
        FixtureRoute(
            urlPattern = "http://player.pbs.org/portalplayer/$videoId",
            contentType = "text/html",
            body = "<html><script>PBS.videoData = ${videoData()};</script></html>",
        ),
        FixtureRoute(
            urlPattern = "https://redirect.example/1?format=json",
            contentType = "application/json",
            body = if (redirectStatus == "ok") {
                """{"status": "ok", "url": "https://media.example/hls/master.m3u8"}"""
            } else {
                """{"status": "error", "http_code": $httpCode, "message": "${message ?: "Fixture error"}"}"""
            },
        ),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            PBSIE(http(transfer())) to slugUrl,
            PBSIE(http(transfer())) to "http://video.pbs.org/video/2365006249",
            PBSIE(http(transfer())) to "https://video.pbs.org/widget/partnerplayer/2365006249",
            PBSIE(http(transfer())) to "https://player.pbs.org/partnerplayer/2365006249",
            PBSIE(http(transfer())) to "https://video.kqed.org/video/1234567890",
            PBSIE(http(transfer())) to "https://www.thirteen.org/programs/fixture-show/fixture-episode-tioglz/",
            PBSKidsIE(http(transfer())) to "https://pbskids.org/video/molly-of-denali/3030407927",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(PBSIE(http(transfer())).suitable("https://example.com/watch/123"))
        assertFalse(PBSKidsIE(http(transfer())).suitable("https://pbskids.org/video/molly-of-denali"))
    }

    // ----------------------------------------------------------------- PBSIE

    @Test
    fun slugPageMapsPlayerDataRedirectsAndChapters() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = slugUrl, contentType = "text/html", body = slugPage),
            *playerRoutes().toTypedArray(),
        )
        val info = PBSIE(http(transfer)).extract(slugUrl)

        assertEquals("2365006249", info.id)
        assertEquals("Constitution USA with Peter Sagal - A More Perfect Union", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(3190.0, info.duration)
        assertEquals("20130514", info.uploadDate)
        assertEquals(14, info.ageLimit)
        assertEquals("https://img.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/hls/master.m3u8", info.formats.single().url)
        assertEquals("https://media.example/captions.vtt", info.subtitles.single().formats.single().url)
        assertEquals(1, info.chapters.size)
        assertEquals(10.0, info.chapters[0].endTime)
        assertTrue(transfer.requests.any { it.url == "https://redirect.example/1?format=json" })
    }

    @Test
    fun playerIdPageReadsTheVideoDiv() = runTest {
        val playerUrl = "https://video.pbs.org/widget/partnerplayer/$videoId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = playerUrl,
                contentType = "text/html",
                body = """<html><div id="video_$videoId"></div></html>""",
            ),
            *playerRoutes().toTypedArray(),
        )
        val info = PBSIE(http(transfer)).extract(playerUrl)
        assertEquals("2365006249", info.id)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun redirectGeoAndExpiredErrorsFailTyped() = runTest {
        val geo = transfer(
            FixtureRoute(urlPattern = slugUrl, contentType = "text/html", body = slugPage),
            *playerRoutes(redirectStatus = "error", httpCode = 403).toTypedArray(),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            PBSIE(http(geo)).extract(slugUrl)
        }

        val expired = transfer(
            FixtureRoute(urlPattern = slugUrl, contentType = "text/html", body = slugPage),
            *playerRoutes(redirectStatus = "error", httpCode = 410).toTypedArray(),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            PBSIE(http(expired)).extract(slugUrl)
        }
        assertTrue(error.message!!.contains("expired"))
    }

    @Test
    fun multiPartTabsBecomeChildEntries() = runTest {
        val page = """
            <html><head><meta property="og:description" content="Fixture"></head><body>
            <div class="videotab" vid="1111111111"></div>
            <div class="videotab" vid="2222222222"></div>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = slugUrl, contentType = "text/html", body = page))
        val info = PBSIE(http(transfer)).extract(slugUrl)
        assertEquals(2, info.entries.size)
        assertEquals("http://video.pbs.org/video/1111111111", info.entries[0].url)
    }

    // -------------------------------------------------------------- PBSKidsIE

    @Test
    fun pbsKidsReadsTheDeeplinkState() = runTest {
        val url = "https://pbskids.org/video/molly-of-denali/3030407927"
        val page = """
            <html><script>
            window._PBS_KIDS_DEEPLINK = {"show_slug": "molly-of-denali", "video_obj": {
              "URI": "https://media.example/kids/master.m3u8",
              "title": "Bird in the Hand/Bye-Bye Birdie",
              "description": "Fixture description",
              "duration": 1540,
              "program_title": "Molly of Denali",
              "video_type": "Episode",
              "air_date": "2019-07-18"
            }};
            </script></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = PBSKidsIE(http(transfer)).extract(url)

        assertEquals("3030407927", info.id)
        assertEquals("Bird in the Hand/Bye-Bye Birdie", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(1540.0, info.duration)
        assertEquals("molly-of-denali", info.channel)
        assertEquals("20190718", info.uploadDate)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun pbsIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = slugUrl,
            infoDict = mapOf(
                "id" to Expect.Value("2365006249"),
                "title" to Expect.Value("Constitution USA with Peter Sagal - A More Perfect Union"),
                "duration" to Expect.Value(3190.0),
                "upload_date" to Expect.Value("20130514"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(FixtureRoute(urlPattern = slugUrl, contentType = "text/html", body = slugPage)) +
                playerRoutes(),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> PBSIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun pbsKidsIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://pbskids.org/video/molly-of-denali/3030407927"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("3030407927"),
                "title" to Expect.Value("Bird in the Hand/Bye-Bye Birdie"),
                "channel" to Expect.Value("molly-of-denali"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><script>
                        window._PBS_KIDS_DEEPLINK = {"show_slug": "molly-of-denali", "video_obj": {
                          "URI": "https://media.example/kids/master.m3u8",
                          "title": "Bird in the Hand/Bye-Bye Birdie",
                          "duration": 1540
                        }};
                        </script></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> PBSKidsIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
