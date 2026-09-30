package com.anydownlod.core.extract.bilibili

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.harness.CaseResult
import com.anydownlod.core.extract.harness.Expect
import com.anydownlod.core.extract.harness.ExtractorCase
import com.anydownlod.core.extract.harness.ExtractorTestRun
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import com.anydownlod.core.extract.harness.runCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Bilibili subset. Every id, host, and address is
 * synthesized (`*.example`); no cookie, token, real video, or `hdslb.com`
 * media URL appears.
 */
class BilibiliIETest {

    private val fixedNow = 1700000000L
    private val expectedWbiKey = "bbaabaababaababbababbabaaaababba"
    private val expectedWRid = "b12de615bf927ebe9c4e4912efd02d40"

    private val navJson = """
        {"data":{"wbi_img":{
          "img_url":"https://i.example/bfs/wbi/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png",
          "sub_url":"https://i.example/bfs/wbi/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.png"}}}
    """.trimIndent()

    private fun page(body: String): String =
        "<html><body><script>window.__INITIAL_STATE__=$body;</script></body></html>"

    private val singleState = """
        {"videoData":{"bvid":"BVfixture","aid":111,"cid":123456,"title":"Fixture video",
        "desc":"A synthetic fixture video","pic":"https://i.example/pic.jpg","pubdate":1700000000,
        "viewCount":42,"pages":[{"cid":123456,"page":1,"part":"Part one"}],"stat":{"view":42}},
        "upData":{"name":"Fixture UP","mid":333},"tags":[{"tag_name":"fixture"}]}
    """.trimIndent()

    private val playInfoJson = """
        {"data":{"quality":80,"timelength":125000,
        "support_formats":[{"quality":80,"new_description":"1080P 高清"}],
        "dash":{
          "audio":[{"id":30280,"baseUrl":"https://media.example/audio.m4s","mimeType":"audio/mp4",
                    "codecs":"mp4a.40.2","bandwidth":128000,"size":1000}],
          "video":[{"id":80,"baseUrl":"https://media.example/video-80.m4s?x=1","mimeType":"video/mp4",
                    "codecs":"avc1.640032","width":1920,"height":1080,"frameRate":60,
                    "bandwidth":2000000,"size":5000}]}}}
    """.trimIndent()

    private fun routes(
        pageUrlPattern: String = "https://www.bilibili.com/video/BVfixture*",
        state: String = singleState,
        pages: String = """{"data":[{"cid":123456,"page":1,"part":"Part one"}]}""",
    ): List<FixtureRoute> = listOf(
        FixtureRoute(urlPattern = pageUrlPattern, contentType = "text/html", body = page(state)),
        FixtureRoute(urlPattern = "https://api.bilibili.com/x/web-interface/nav", body = navJson),
        FixtureRoute(urlPattern = "https://api.bilibili.com/x/player/pagelist*", body = pages),
        FixtureRoute(
            urlPattern = "https://api.bilibili.com/x/player/wbi/playurl*",
            body = playInfoJson,
        ),
    )

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): BiliBiliIE =
        BiliBiliIE(ExtractorHttp(transfer), nowSeconds = { fixedNow })

    // ------------------------------------------------------------ URL matching

    @Test
    fun videoAndFestivalFormsMatch() {
        val ie = extractor(transfer())
        val urls = listOf(
            "https://www.bilibili.com/video/BVfixture",
            "http://www.bilibili.com/video/av1074402/",
            "https://www.bilibili.com/festival/2023fixture?bvid=BVfixture",
        )
        for (url in urls) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://www.bilibili.com/bangumi/play/ep123"))
        assertFalse(ie.suitable("https://space.bilibili.com/123/video"))
    }

    // -------------------------------------------------------------- WBI signing

    @Test
    fun wbiKeyAndSignatureMatchTheUpstreamAlgorithm() {
        val lookup = "a".repeat(32) + "b".repeat(32)
        assertEquals(expectedWbiKey, BiliBiliIE.wbiKey(lookup))

        val signed = BiliBiliIE.wbiSign(
            mapOf(
                "bvid" to "BVfixture",
                "cid" to "123456",
                "fnval" to "4048",
                "try_look" to "1",
            ),
            expectedWbiKey,
            fixedNow,
        )
        assertEquals(expectedWRid, signed["w_rid"])
        assertEquals("1700000000", signed["wts"])
    }

    // ------------------------------------------------------------- single video

    @Test
    fun singleVideoMapsMetadataAndDashFormats() = runTest {
        val transfer = transfer(*routes().toTypedArray())
        val info = extractor(transfer).extract("https://www.bilibili.com/video/BVfixture")

        assertEquals("BVfixture", info.id)
        assertEquals("Fixture video", info.title)
        assertEquals("A synthetic fixture video", info.description)
        assertEquals("Fixture UP", info.uploader)
        assertEquals("333", info.channelId)
        assertEquals(42L, info.viewCount)
        assertEquals("20231114", info.uploadDate)
        assertEquals("https://i.example/pic.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)

        val audio = info.formats[0]
        assertEquals("30280", audio.formatId)
        assertEquals("https://media.example/audio.m4s", audio.url)
        assertEquals("none", audio.vcodec)
        assertEquals("mp4a.40.2", audio.acodec)
        assertEquals(128.0, audio.tbr)
        assertEquals("https://www.bilibili.com/video/BVfixture", audio.httpHeaders?.get("referer"))

        val video = info.formats[1]
        assertEquals("80", video.formatId)
        assertEquals(1920L, video.width)
        assertEquals(1080L, video.height)
        assertEquals(60.0, video.fps)
        assertEquals(2000.0, video.tbr)
        assertEquals("none", video.acodec)
        assertEquals("1080P 高清", video.formatNote)

        val playurlRequest = transfer.requests.last { it.url.contains("playurl") }
        assertTrue(
            playurlRequest.url.contains("w_rid=$expectedWRid"),
            "the playurl request must carry the WBI signature",
        )
    }

    @Test
    fun bilibiliIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://www.bilibili.com/video/BVfixture",
            infoDict = mapOf(
                "id" to Expect.Value("BVfixture"),
                "title" to Expect.Value("Fixture video"),
                "uploader" to Expect.Value("Fixture UP"),
                "upload_date" to Expect.Value("20231114"),
                "formats" to Expect.Count(2),
                "formats.0.format_id" to Expect.Value("30280"),
                "formats.1.format_id" to Expect.Value("80"),
                "formats.1.height" to Expect.Value(1080L),
            ),
            routes = routes(),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> BiliBiliIE(http, nowSeconds = { fixedNow }) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    // --------------------------------------------------------------- anthology

    @Test
    fun anthologyWithoutPartBecomesRegistryEntries() = runTest {
        val pages = """
            {"data":[{"cid":1,"page":1,"part":"One"},{"cid":2,"page":2,"part":"Two"}]}
        """.trimIndent()
        val transfer = transfer(*routes(pages = pages).toTypedArray())
        val info = extractor(transfer).extract("https://www.bilibili.com/video/BVfixture")
        assertEquals(2, info.entries.size, "requests: " + transfer.requests.map { it.url })
        assertEquals("https://www.bilibili.com/video/BVfixture?p=1", info.entries[0].url)
        assertEquals("https://www.bilibili.com/video/BVfixture?p=2", info.entries[1].url)
        assertTrue(info.formats.isEmpty())
    }

    @Test
    fun anExplicitPartExtractsThatPartOnly() = runTest {
        val pages = """
            {"data":[{"cid":1,"page":1,"part":"One"},{"cid":2,"page":2,"part":"Two"}]}
        """.trimIndent()
        val transfer = transfer(*routes(pages = pages).toTypedArray())
        val info = extractor(transfer).extract("https://www.bilibili.com/video/BVfixture?p=2")

        assertEquals("BVfixture_p2", info.id)
        assertEquals("Fixture video p02 Two", info.title)
        val playurlRequest = transfer.requests.last { it.url.contains("playurl") }
        assertTrue(playurlRequest.url.contains("cid=2&"), "part 2 must request its own cid")
    }

    // ---------------------------------------------------------------- errors

    @Test
    fun protectedAndDeletedStatesFailTyped() = runTest {
        val protectedState = """
            {"error":{"trueCode":-403},"videoData":{}}
        """.trimIndent()
        val protectedTransfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.bilibili.com/video/BVfixture*",
                body = page(protectedState),
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            extractor(protectedTransfer).extract("https://www.bilibili.com/video/BVfixture")
        }

        val deletedState = """
            {"error":{"trueCode":-404},"videoData":{}}
        """.trimIndent()
        val deletedTransfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.bilibili.com/video/BVfixture*",
                body = page(deletedState),
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            extractor(deletedTransfer).extract("https://www.bilibili.com/video/BVfixture")
        }
    }

    // ----------------------------------------------------------------- player

    @Test
    fun playerIframeDelegatesThroughTheRegistry() = runTest {
        val playerUrl = "http://player.bilibili.com/player.html?aid=92494333&cid=157926707&page=1"
        val player = BiliBiliPlayerIE(ExtractorHttp(transfer()))
        assertTrue(player.suitable(playerUrl))
        assertEquals("92494333", player.matchId(playerUrl))
        assertEquals(
            "https://www.bilibili.com/video/av92494333",
            player.extract(playerUrl).redirectUrl,
        )

        val avState = singleState.replace("BVfixture", "BVplayer")
        val avRoutes = routes(pageUrlPattern = "https://www.bilibili.com/video/av92494333*", state = avState)
        val transfer = transfer(*avRoutes.toTypedArray())
        val registry = ExtractorRegistry(
            listOf(extractor(transfer), BiliBiliPlayerIE(ExtractorHttp(transfer))),
        )
        val info = registry.extract(playerUrl)
        assertEquals("BVplayer", info.id)
        assertEquals(2, info.formats.size)
    }
}
