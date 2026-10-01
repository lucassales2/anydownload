package com.anydownload.core.extract.txxx

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
 * Fixture cases for the TXXX network subset. Ids, titles, and media paths
 * are synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class TxxxIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "16574965"
    private val txxxUrl = "https://txxx.com/videos/$videoId/fixture-video/"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            TxxxIE(http(transfer())) to txxxUrl,
            TxxxIE(http(transfer())) to "https://vxxx.com/video-68925/",
            PornTopIE(http(transfer())) to "https://porntop.com/video/101569/fixture/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(PornTopIE(http(transfer())).suitable(txxxUrl))
    }

    // ----------------------------------------------------------------- txxx

    @Test
    fun txxxApisYieldFormatsAndMetadata() = runTest {
        val encoded = kotlin.io.encoding.Base64.Default.encode("/contents/videos/1080.mp4".encodeToByteArray())
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://txxx.com/api/videofile.php?video_id=$videoId&lifetime=8640000",
                contentType = "application/json",
                body = """
                    [{"video_url": "$encoded", "format": "_1080p"}]
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://txxx.com/api/json/video/86400/16000000/16574000/$videoId.json",
                contentType = "application/json",
                body = """
                    {"video": {"title": "Fixture Txxx", "duration": "00:11:34",
                      "thumbsrc": "https://media.example/thumb.jpg",
                      "user": {"username": "Fixture Uploader"},
                      "statistics": {"viewed": 100}}}
                """.trimIndent(),
            ),
        )
        val info = TxxxIE(http(transfer)).extract(txxxUrl)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Txxx", info.title)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals(694.0, info.duration)
        assertEquals(100L, info.viewCount)
        assertEquals(18, info.ageLimit)
        assertEquals("https://txxx.com//contents/videos/1080.mp4", info.formats.single().url)
        assertEquals("1080p", info.formats.single().formatId)
    }

    // -------------------------------------------------------------- porntop

    @Test
    fun pornTopPlayerJsonYieldsFormats() = runTest {
        val url = "https://porntop.com/video/101569/fixture/"
        val encoded = kotlin.io.encoding.Base64.Default.encode("/media/720.mp4".encodeToByteArray())
        val playerJson = """[{"video_url": "$encoded", "format": "720p"}]"""
        val playerB64 = kotlin.io.encoding.Base64.Default.encode(playerJson.encodeToByteArray())
        val page = """
            <html><body>
            <script>schemaJson = {"@type": "VideoObject", "name": "Fixture PornTop",
                                  "description": "Fixture description",
                                  "uploadDate": "2020-12-31", "thumbnailUrl": "https://media.example/thumb.jpg"};</script>
            <script>window.initPlayer({a: {b: {}}}, '$playerB64')</script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = PornTopIE(http(transfer)).extract(url)
        assertEquals("101569", info.id)
        assertEquals("Fixture PornTop", info.title)
        assertEquals("20201231", info.uploadDate)
        assertEquals(18, info.ageLimit)
        assertEquals("https://porntop.com//media/720.mp4", info.formats.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun txxxIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val encoded = kotlin.io.encoding.Base64.Default.encode("/contents/videos/720.mp4".encodeToByteArray())
        val case = ExtractorCase(
            url = txxxUrl,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Txxx"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://txxx.com/api/videofile.php?video_id=$videoId&lifetime=8640000",
                    contentType = "application/json",
                    body = """[{"video_url": "$encoded", "format": "720p"}]""",
                ),
                FixtureRoute(
                    urlPattern = "https://txxx.com/api/json/video/86400/16000000/16574000/$videoId.json",
                    contentType = "application/json",
                    body = """{"video": {"title": "Fixture Txxx"}}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TxxxIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
