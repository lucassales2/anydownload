package com.anydownload.core.extract.iqiyi

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
 * Fixture cases for the iQIYI subset. Every id, host, and media address is
 * synthesized (`*.example`); the MD5 signature is computed at runtime from
 * the fake ids, and no cookie or signed URL appears.
 */
class IqiyiIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl = "http://www.iqiyi.com/v_19rrojlavg.html"
    private val tvid = "123456789"
    private val videoId = "9c1fb1b99d192b21c559e5a1a2cb3c73"

    private val videoPage = """
        <html><body>
        <div data-player-tvid="$tvid" data-player-videoid="$videoId"></div>
        <span data-videochanged-title="word">Fixture iQIYI Title</span>
        </body></html>
    """.trimIndent()

    private val tmtsRoute = FixtureRoute(
        urlPattern = "http://cache.m.iqiyi.com/jp/tmts/$tvid/$videoId/*",
        contentType = "application/javascript",
        body = """
            var tvInfoJs={"code": "A00000", "data": {"vidl": [
              {"vd": 4, "m3utx": "https://media.example/iqiyi/master.m3u8"}
            ]}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            IqiyiIE(http(transfer())) to videoUrl,
            IqiyiIE(http(transfer())) to "http://www.pps.tv/w_19rrbav0ph.html",
            IqIE(http(transfer())) to "https://www.iq.com/play/fixture-1bk9icvr331",
            IqAlbumIE(http(transfer())) to "https://www.iq.com/album/fixture-1bk9icvr331",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(IqiyiIE(http(transfer())).suitable("https://www.iq.com/play/x"))
        assertFalse(IqAlbumIE(http(transfer())).suitable("https://www.iq.com/play/x"))
    }

    // --------------------------------------------------------------- IqiyiIE

    @Test
    fun signedTmtsApiMapsTheM3u8Formats() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = videoUrl, contentType = "text/html", body = videoPage),
            tmtsRoute,
        )
        val info = IqiyiIE(http(transfer)).extract(videoUrl)

        assertEquals(videoId, info.id)
        assertEquals("Fixture iQIYI Title", info.title)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("4", info.formats.single().formatId)
        assertEquals("5", info.formats.single().quality)
        val tmtsRequest = transfer.requests.first { it.url.contains("/jp/tmts/") }
        assertTrue(
            Regex("sc=[0-9a-f]{32}").containsMatchIn(tmtsRequest.url),
            "the tmts request must carry a 32-hex MD5 signature",
        )
    }

    @Test
    fun geoErrorCodeFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = videoUrl, contentType = "text/html", body = videoPage),
            FixtureRoute(
                urlPattern = "http://cache.m.iqiyi.com/jp/tmts/$tvid/$videoId/*",
                contentType = "application/javascript",
                body = """var tvInfoJs={"code": "A00111"}""",
            ),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            IqiyiIE(http(transfer)).extract(videoUrl)
        }
    }

    @Test
    fun albumPageListsThePlaylistEntries() = runTest {
        val albumUrl = "http://www.iqiyi.com/a_19rrhb8ce1.html"
        val albumPage = """
            <html><body>
            <a class="site-piclist_pic_link" href="http://www.iqiyi.com/v_1.html">One</a>
            <script>albumId: 202918101,</script>
            <div data-share-title="Fixture Album"></div>
            </body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = albumUrl, contentType = "text/html", body = albumPage),
            FixtureRoute(
                urlPattern = "http://cache.video.qiyi.com/jp/avlist/202918101/2/50/",
                contentType = "application/javascript",
                body = """var tvInfoJs={"data": {"vlist": [{"vurl": "http://www.iqiyi.com/v_2.html"}]}}""",
            ),
        )
        val info = IqiyiIE(http(transfer)).extract(albumUrl)
        assertEquals("202918101", info.id)
        assertEquals("Fixture Album", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("http://www.iqiyi.com/v_2.html", info.entries[1].url)
    }

    // ------------------------------------------------------------------- IqIE

    @Test
    fun intlPlayerFailsTypedOnTheJsSignature() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            IqIE(http(transfer())).extract("https://www.iq.com/play/fixture-1bk9icvr331")
        }
        assertTrue(error.message!!.contains("cmd5x"))
    }

    // -------------------------------------------------------------- IqAlbumIE

    @Test
    fun albumListsTheEpisodesAndSingleVideoRedirects() = runTest {
        val albumUrl = "https://www.iq.com/album/fixture-1bk9icvr331"
        val albumPage = """
            <html><head><script id="__NEXT_DATA__" type="application/json">
            {"props": {"initialState": {"album": {"videoAlbumInfo": {
              "albumId": "1bk9icvr331", "name": "Fixture Album", "description": "Fixture description",
              "totalPageRange": [{"from": 1, "to": 2}]
            }}}, "initialProps": {"pageProps": {"modeCode": "intl", "langCode": "en_us"}}}}
            </script></head></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = albumUrl, contentType = "text/html", body = albumPage),
            FixtureRoute(
                urlPattern = "https://pcw-api.iq.com/api/episodeListSource/1bk9icvr331" +
                    "?platformId=3&modeCode=intl&langCode=en_us&endOrder=2&startOrder=1",
                contentType = "application/json",
                body = """
                    {"data": {"epg": [{"qipuIdStr": "v1", "playLocSuffix": "fixture-ep1",
                                       "name": "Episode 1"}]}}
                """.trimIndent(),
            ),
        )
        val info = IqAlbumIE(http(transfer)).extract(albumUrl)
        assertEquals("1bk9icvr331", info.id)
        assertEquals("Fixture Album", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.iq.com/play/fixture-ep1", info.entries[0].url)

        val singleUrl = "https://www.iq.com/album/fixture-22yjnij099k"
        val singlePage = """
            <html><head><script id="__NEXT_DATA__" type="application/json">
            {"props": {"initialState": {"album": {"videoAlbumInfo": {
              "videoType": "singleVideo"
            }}}}}
            </script></head></html>
        """.trimIndent()
        val single = IqAlbumIE(
            http(transfer(FixtureRoute(urlPattern = singleUrl, contentType = "text/html", body = singlePage))),
        ).extract(singleUrl)
        assertEquals("https://www.iq.com/play/22yjnij099k", single.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun iqiyiIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture iQIYI Title"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = videoUrl, contentType = "text/html", body = videoPage),
                tmtsRoute,
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> IqiyiIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun iqAlbumIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val albumUrl = "https://www.iq.com/album/fixture-1bk9icvr331"
        val case = ExtractorCase(
            url = albumUrl,
            infoDict = mapOf(
                "id" to Expect.Value("1bk9icvr331"),
                "title" to Expect.Value("Fixture Album"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = albumUrl,
                    contentType = "text/html",
                    body = """
                        <html><head><script id="__NEXT_DATA__" type="application/json">
                        {"props": {"initialState": {"album": {"videoAlbumInfo": {
                          "albumId": "1bk9icvr331", "name": "Fixture Album",
                          "totalPageRange": [{"from": 1, "to": 2}]
                        }}}}}
                        </script></head></html>
                    """.trimIndent(),
                ),
                FixtureRoute(
                    urlPattern = "https://pcw-api.iq.com/api/episodeListSource/1bk9icvr331" +
                        "?platformId=3&modeCode=intl&langCode=en_us&endOrder=2&startOrder=1",
                    contentType = "application/json",
                    body = """{"data": {"epg": [{"qipuIdStr": "v1", "name": "Episode 1"}]}}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> IqAlbumIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
