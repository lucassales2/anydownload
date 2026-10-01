package com.anydownload.core.extract.pornhub

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
 * Fixture cases for the PornHub subset. Every id, host, and media address is
 * synthesized (`*.example`); `fake_value` stands in for the playlist token,
 * and no cookie or signed URL appears.
 */
class PornHubIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl = "https://www.pornhub.com/view_video.php?viewkey=648719015"

    private val videoPage = """
        <html><head><meta name="twitter:title" content="Fixture PH Title"></head><body>
        <script>var flashvars_123 = {
          "image_url": "https://img.example/thumb.jpg",
          "video_duration": "361",
          "closedCaptionsFile": "https://media.example/captions.srt",
          "mediaDefinitions": [
            {"videoUrl": "https://media.example/201306/28/video_720p_2000k.mp4", "quality": "720"},
            {"videoUrl": "https://media.example/hls/master.m3u8", "quality": "1080"},
            {"videoUrl": "https://media.example/dash/manifest.mpd", "quality": "1080"}
          ]
        };</script>
        <span class="count">1,234</span> Views
        All Comments <span>(56)</span>
        <script>var MODEL_PROFILE = {"username": "Fixture Model",
                                    "modelProfileLink": "/model/fixture-model"};</script>
        </body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            PornHubIE(http(transfer())) to videoUrl,
            PornHubIE(http(transfer())) to "https://www.pornhub.com/embed/648719015",
            PornHubIE(http(transfer())) to "https://www.thumbzilla.com/video/648719015/fixture",
            PornHubUserIE(http(transfer())) to "https://www.pornhub.com/model/zoe_ph",
            PornHubUserIE(http(transfer())) to "https://www.pornhub.com/pornstar/liz-vicious",
            PornHubPagedVideoListIE(http(transfer())) to "https://www.pornhub.com/model/zoe_ph/videos",
            PornHubPagedVideoListIE(http(transfer())) to "https://www.pornhub.com/video?page=3",
            PornHubUserVideosUploadIE(http(transfer())) to
                "https://www.pornhub.com/pornstar/jenny-blighe/videos/upload",
            PornHubPlaylistIE(http(transfer())) to "https://www.pornhub.com/playlist/44121572",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(PornHubPagedVideoListIE(http(transfer())).suitable(videoUrl))
        assertFalse(PornHubUserIE(http(transfer())).suitable("https://www.pornhub.com/model/zoe_ph/videos"))
    }

    // ------------------------------------------------------------- PornHubIE

    @Test
    fun videoPageMapsFlashvarsFormatsAndCounts() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = videoUrl, contentType = "text/html", body = videoPage))
        val info = PornHubIE(http(transfer)).extract(videoUrl)

        assertEquals("648719015", info.id)
        assertEquals("Fixture PH Title", info.title)
        assertEquals("Fixture Model", info.uploader)
        assertEquals("20130628", info.uploadDate)
        assertEquals(361.0, info.duration)
        assertEquals(1234L, info.viewCount)
        assertEquals(18, info.ageLimit)
        assertEquals("https://img.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals("https://media.example/captions.srt", info.subtitles.single().formats.single().url)
        assertEquals(3, info.formats.size)
        assertEquals("720p", info.formats[0].formatId)
        assertEquals(720L, info.formats[0].height)
        assertEquals("m3u8_native", info.formats[1].protocol)
        assertEquals("http_dash_segments", info.formats[2].protocol)
        assertEquals("https://www.pornhub.com/", info.formats[0].httpHeaders?.get("referer"))
    }

    @Test
    fun jsGatePageFailsTypedAsALoginWall() = runTest {
        val gated = "<html><body><script>document.location.reload(true)</script></body></html>"
        val transfer = transfer(FixtureRoute(urlPattern = videoUrl, contentType = "text/html", body = gated))
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            PornHubIE(http(transfer)).extract(videoUrl)
        }
        assertTrue(error.message!!.contains("age verification"))
    }

    @Test
    fun userPageRedirectsToTheVideosList() = runTest {
        val url = "https://www.pornhub.com/model/zoe_ph"
        val info = PornHubUserIE(http(transfer())).extract(url)
        assertEquals("zoe_ph", info.id)
        assertEquals("https://www.pornhub.com/model/zoe_ph/videos", info.redirectUrl)
    }

    @Test
    fun pagedVideoListScansTheContainer() = runTest {
        val url = "https://www.pornhub.com/model/zoe_ph/videos"
        val page = """
            <html><body><div class="container">
            <a href="/view_video.php?viewkey=abc123" title="Fixture Video One"></a>
            <a href="/view_video.php?viewkey=def456" title="Fixture Video Two"></a>
            </div></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "$url?page=1", contentType = "text/html", body = page),
        )
        val info = PornHubPagedVideoListIE(http(transfer)).extract(url)
        assertEquals("model/zoe_ph/videos", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.pornhub.com/view_video.php?viewkey=abc123", info.entries[0].url)
        assertEquals("Fixture Video One", info.entries[0].title)
    }

    @Test
    fun playlistReadsTheChunkedPages() = runTest {
        val url = "https://www.pornhub.com/playlist/44121572"
        val page1 = """
            <html><body>
            <script>var playlistId = "44121572"; var itemsCount = 76 || 0; var token = "fake_value";</script>
            <div class="container"><a href="/view_video.php?viewkey=abc123" title="One"></a></div>
            </body></html>
        """.trimIndent()
        val page2 = """
            <html><body><div class="container">
            <a href="/view_video.php?viewkey=def456" title="Two"></a>
            </div></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page1),
            FixtureRoute(
                urlPattern = "https://www.pornhub.com/playlist/viewChunked?id=44121572&page=2&token=fake_value",
                contentType = "text/html",
                body = page2,
            ),
        )
        val info = PornHubPlaylistIE(http(transfer)).extract(url)
        assertEquals("44121572", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.pornhub.com/view_video.php?viewkey=def456", info.entries[1].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun pornHubIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("648719015"),
                "title" to Expect.Value("Fixture PH Title"),
                "duration" to Expect.Value(361.0),
                "age_limit" to Expect.Value(18),
                "formats.1.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(FixtureRoute(urlPattern = videoUrl, contentType = "text/html", body = videoPage)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> PornHubIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
