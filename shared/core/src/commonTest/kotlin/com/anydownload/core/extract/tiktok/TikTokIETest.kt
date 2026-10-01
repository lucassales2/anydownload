package com.anydownload.core.extract.tiktok

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.ExtractorRegistry
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
 * Fixture cases for the TikTok webpage subset. Every id, host, and address is
 * synthesized (`*.example`); no cookie, msToken, bearer token, or signed media
 * URL appears.
 */
class TikTokIETest {

    private val videoPageUrl = "https://www.tiktok.com/@fixtureuser/video/7170520270497680683"

    private val itemStruct = """
        {
          "id": "7170520270497680683",
          "desc": "Synthetic TikTok fixture description",
          "createTime": 1669516858,
          "author": {"uniqueId": "fixtureuser", "nickname": "Fixture Creator",
                     "authorId": "6687535061741700102", "secUid": "SECFIXTURE"},
          "music": {"title": "Fixture sound", "playUrl": "https://media.example/music.mp3"},
          "video": {
            "duration": 15, "width": 576, "height": 1024,
            "playAddr": "https://media.example/play.mp4",
            "downloadAddr": "https://media.example/download.mp4",
            "thumbnail": "https://media.example/thumb.jpg",
            "cover": "https://media.example/cover.webp",
            "bitrateInfo": [
              {"PlayAddr": {"UrlKey": "v1200_h264_720p_1500000",
                            "UrlList": ["https://media.example/h264-720.mp4"], "DataSize": 123456}},
              {"PlayAddr": {"UrlKey": "v1200_bytevc1_1080p_2500000",
                            "UrlList": ["https://media.example/h265-1080.mp4"], "DataSize": 234567}}
            ],
            "cla_info": {"caption_infos": [
              {"url": "https://media.example/captions.vtt", "lang": "en", "Format": "webvtt"}
            ]}
          },
          "stats": {"playCount": 42}
        }
    """.trimIndent()

    private fun universalPage(status: Long, item: String? = itemStruct): String {
        val detail = if (item == null) {
            """{"statusCode":$status}"""
        } else {
            """{"statusCode":$status,"itemInfo":{"itemStruct":$item}}"""
        }
        val json = """{"__DEFAULT_SCOPE__":{"webapp.video-detail":$detail}}"""
        return """
            <html><body><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">$json</script></body></html>
        """.trimIndent()
    }

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): TikTokIE =
        TikTokIE(ExtractorHttp(transfer))

    // ------------------------------------------------------------ URL matching

    @Test
    fun videoAndShortLinkFormsMatch() {
        val ie = extractor(transfer())
        val urls = listOf(
            "https://www.tiktok.com/@fixtureuser/video/7170520270497680683",
            "https://www.tiktok.com/embed/7170520270497680683",
            "https://www.tiktok.com/share/video/7170520270497680683",
            "https://www.tiktokv.com/@/video/7170520270497680683",
        )
        for (url in urls) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://www.tiktok.com/@fixtureuser"))
        assertFalse(ie.suitable("https://www.tiktok.com/music/fixture-123"))

        val vm = TikTokVMIE(ExtractorHttp(transfer()))
        for (url in listOf(
            "https://vm.tiktok.com/ZTRC5xgJp",
            "https://vt.tiktok.com/ZSe4FqkKd",
            "https://www.tiktok.com/t/ZTRC5xgJp",
        )) {
            assertTrue(vm.suitable(url), "short link must match: $url")
        }
        assertFalse(vm.suitable("https://www.tiktok.com/@fixtureuser"))
    }

    // -------------------------------------------------------------- extraction

    @Test
    fun webDataMapsFormatsAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = videoPageUrl, contentType = "text/html", body = universalPage(0)),
        )
        val info = extractor(transfer).extract(videoPageUrl)

        assertEquals("7170520270497680683", info.id)
        assertEquals("Synthetic TikTok fixture description", info.title)
        assertEquals(15.0, info.duration)
        assertEquals("fixtureuser", info.uploader)
        assertEquals("Fixture Creator", info.channel)
        assertEquals("SECFIXTURE", info.channelId)
        assertEquals("20221127", info.uploadDate)
        assertEquals(42L, info.viewCount)
        assertEquals(2, info.thumbnails.size)
        assertTrue(info.thumbnails.any { it.url == "https://media.example/thumb.jpg" })

        assertEquals(5, info.formats.size)
        val h264 = info.formats[0]
        assertEquals("h264_720p_1500000", h264.formatId)
        assertEquals(1500.0, h264.tbr)
        assertEquals(720L, h264.width)
        assertEquals(1280L, h264.height)
        assertEquals("h264", h264.vcodec)
        assertEquals("aac", h264.acodec)
        assertEquals(123456L, h264.filesize)
        assertEquals(videoPageUrl, h264.httpHeaders?.get("referer"))

        val h265 = info.formats[1]
        assertEquals("bytevc1_1080p_2500000", h265.formatId)
        assertEquals("h265", h265.vcodec)
        assertEquals(1080L, h265.width)
        assertEquals(1920L, h265.height)

        assertEquals("play", info.formats[2].formatId)
        assertEquals("download", info.formats[3].formatId)
        assertEquals("watermarked", info.formats[3].formatNote)
        assertEquals(-2, info.formats[3].preference)

        val audio = info.formats.single { it.formatId == "audio" }
        assertEquals("none", audio.vcodec)
        assertEquals("mp3", audio.acodec)

        val subtitle = info.subtitles.single()
        assertEquals("en", subtitle.language)
        assertEquals("vtt", subtitle.formats.single().ext)
        assertEquals("https://media.example/captions.vtt", subtitle.formats.single().url)
    }

    @Test
    fun privateStatusFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = videoPageUrl, body = universalPage(10216, item = null)),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            extractor(transfer).extract(videoPageUrl)
        }
    }

    @Test
    fun blockedNetworkFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = videoPageUrl, body = universalPage(10204, item = null)),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            extractor(transfer).extract(videoPageUrl)
        }
    }

    // -------------------------------------------------------------- shortener

    @Test
    fun shortLinkRedirectsIntoTheRegistry() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://vm.tiktok.com/ZTRC5xgJp",
                redirectTo = videoPageUrl,
            ),
            FixtureRoute(urlPattern = videoPageUrl, contentType = "text/html", body = universalPage(0)),
        )
        val http = ExtractorHttp(transfer)
        val registry = ExtractorRegistry(listOf(TikTokIE(http), TikTokVMIE(http)))
        val info = registry.extract("https://vm.tiktok.com/ZTRC5xgJp")

        assertEquals("7170520270497680683", info.id)
        assertEquals(5, info.formats.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun tikTokIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoPageUrl,
            infoDict = mapOf(
                "id" to Expect.Value("7170520270497680683"),
                "title" to Expect.Value("Synthetic TikTok fixture description"),
                "uploader" to Expect.Value("fixtureuser"),
                "channel" to Expect.Value("Fixture Creator"),
                "upload_date" to Expect.Value("20221127"),
                "duration" to Expect.Value(15L),
                "view_count" to Expect.Value(42L),
                "formats" to Expect.Count(5),
                "formats.0.format_id" to Expect.Value("h264_720p_1500000"),
                "formats.0.vcodec" to Expect.Value("h264"),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = videoPageUrl, contentType = "text/html", body = universalPage(0)),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> TikTokIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
