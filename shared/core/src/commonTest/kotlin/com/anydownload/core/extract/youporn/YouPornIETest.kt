package com.anydownload.core.extract.youporn

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
 * Fixture cases for the YouPorn subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or signed URL
 * appears.
 */
class YouPornIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val watchUrl = "https://www.youporn.com/watch/505835/fixture-video/"
    private val watchPage = """
        <html><head>
        <meta property="og:title" content="Fixture Video">
        <meta property="og:description" content="Fixture description">
        </head><body>
        <div id="watch-container"></div>
        <div class="watchVideoTitle">Fixture Video Title</div>
        <div id="description">Fixture description body</div>
        <script>playervars: {"duration": 210, "mediaDefinitions": [
          {"format": "hls", "videoUrl": "https://media.example/hls-info"},
          {"format": "mp4", "videoUrl": "https://media.example/mp4-info"}
        ]}</script>
        <div class="submitByLink">Fixture Uploader</div>
        <label>Uploaded</label> <span>2020-11-23</span>
        <div data-value="1,234"><label>Views:</label></div>
        RTA-5042-1996-1400-1577-RTA
        </body></html>
    """.trimIndent()

    private val hlsInfoRoute = FixtureRoute(
        urlPattern = "https://media.example/hls-info",
        contentType = "application/json",
        body = """
            [{"format": "hls", "defaultQuality": "auto",
              "videoUrl": "https://media.example/hls/master.m3u8"}]
        """.trimIndent(),
    )

    private val mp4InfoRoute = FixtureRoute(
        urlPattern = "https://media.example/mp4-info",
        contentType = "application/json",
        body = """
            [{"format": "mp4", "videoUrl": "https://media.example/mp4/720p_1500k_505835.mp4",
              "quality": 720, "videoSize": 12345}]
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            YouPornIE(http(transfer())) to "https://www.youporn.com/watch/505835/fixture-video/",
            YouPornIE(http(transfer())) to "https://www.youporn.com/embed/505835/fixture-video/",
            YouPornCategoryIE(http(transfer())) to "https://www.youporn.com/category/fixture-category/popular/",
            YouPornChannelIE(http(transfer())) to "https://www.youporn.com/channel/fixture-channel/",
            YouPornCollectionIE(http(transfer())) to "https://www.youporn.com/collections/videos/33044251/",
            YouPornTagIE(http(transfer())) to "https://www.youporn.com/porntags/fixture-tag",
            YouPornStarIE(http(transfer())) to "https://www.youporn.com/pornstar/fixture-star/",
            YouPornVideosIE(http(transfer())) to "https://www.youporn.com/browse/time",
            YouPornVideosIE(http(transfer())) to "https://www.youporn.com/recommended",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(YouPornIE(http(transfer())).suitable("https://www.youporn.com/category/x/"))
    }

    // ---------------------------------------------------------------- video

    @Test
    fun playerVarsYieldTheMp4AndMasterHlsFormats() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "https://www.youporn.com/watch/505835", contentType = "text/html", body = watchPage),
            hlsInfoRoute,
            mp4InfoRoute,
        )
        val info = YouPornIE(http(transfer)).extract(watchUrl)
        assertEquals("505835", info.id)
        assertEquals("Fixture Video Title", info.title)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals(210.0, info.duration)
        assertEquals("20201123", info.uploadDate)
        assertEquals(1234L, info.viewCount)
        assertEquals(18, info.ageLimit)
        assertEquals(2, info.formats.size, info.formats.joinToString { "${it.formatId}|${it.protocol}|${it.url}" })
        val mp4 = info.formats.first { it.ext == "mp4" && it.protocol == null }
        assertEquals("720p-1500k", mp4.formatId)
        assertEquals(720L, mp4.height)
        assertEquals("m3u8_native", info.formats.first { it.protocol == "m3u8_native" }.protocol)
    }

    @Test
    fun missingWatchContainerFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.youporn.com/watch/505835",
                contentType = "text/html",
                body = "<html><body>Video unavailable</body></html>",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            YouPornIE(http(transfer)).extract(watchUrl)
        }
    }

    // --------------------------------------------------------------- listings

    @Test
    fun categoryPageListsTheVideoTitleLinks() = runTest {
        val url = "https://www.youporn.com/category/fixture-category/popular/"
        val page = """
            <html><body>
            <a class="video-title" href="/watch/1/fixture-one/">One</a>
            <a class="video-title" href="https://www.youporn.com/watch/2/fixture-two/">Two</a>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = YouPornCategoryIE(http(transfer)).extract(url)
        assertEquals("fixture-category/popular", info.id)
        assertEquals("Category fixture category videos by popular", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.youporn.com/watch/2/fixture-two/", info.entries[1].url)
    }

    @Test
    fun collectionAndStarOverrideTitleAndDescription() = runTest {
        val collectionUrl = "https://www.youporn.com/collections/videos/33044251/"
        val collectionPage = """
            <html><body>
            <div class="collection-infos">Collection: Fixture 5 VIDEOS 10 VIEWS 3 days LAST UPDATED From: fixture_user</div>
            <a class="video-title" href="/watch/1/fixture-one/">One</a>
            </body></html>
        """.trimIndent()
        val collection = YouPornCollectionIE(
            http(transfer(FixtureRoute(urlPattern = collectionUrl, contentType = "text/html", body = collectionPage))),
        ).extract(collectionUrl)
        assertEquals("Collection Fixture videos", collection.title)
        assertEquals("fixture_user", collection.uploader)

        val starUrl = "https://www.youporn.com/pornstar/fixture-star/"
        val starPage = """
            <html><body>
            <div class="pornstar-info-wrapper">Fixture Star Rank 1 Videos 10 Views 100 Subscribers 5</div></div></div></div></div></div>
            </body></html>
        """.trimIndent()
        val star = YouPornStarIE(
            http(transfer(FixtureRoute(urlPattern = starUrl, contentType = "text/html", body = starPage))),
        ).extract(starUrl)
        assertEquals("Pornstar Fixture-star videos", star.title)
        assertTrue(star.description!!.contains("Fixture Star Rank 1"))
    }

    // --------------------------------------------------------------- harness

    @Test
    fun watchPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = watchUrl,
            infoDict = mapOf(
                "id" to Expect.Value("505835"),
                "title" to Expect.Value("Fixture Video Title"),
                "age_limit" to Expect.Value(18),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "https://www.youporn.com/watch/505835", contentType = "text/html", body = watchPage),
                hlsInfoRoute,
                mp4InfoRoute,
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> YouPornIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun categoryPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.youporn.com/category/fixture-category/popular/"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("fixture-category/popular"),
                "title" to Expect.Value("Category fixture category videos by popular"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """<html><body><a class="video-title" href="/watch/1/fixture-one/">One</a></body></html>""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> YouPornCategoryIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
