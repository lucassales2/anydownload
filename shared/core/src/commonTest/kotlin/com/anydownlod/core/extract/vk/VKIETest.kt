package com.anydownlod.core.extract.vk

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
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
 * Fixture cases for the VK embed and public-AJAX subset. Every id, host, and
 * address is synthesized (`*.example`); no cookie, bearer token, or signed
 * media URL appears.
 */
class VKIETest {

    private val embedUrl = "https://vk.com/video_ext.php?oid=-77521&id=162222515&hash=abc123"
    private val watchUrl = "https://vk.com/video205387401_165548505"

    private val embedPage = """
        <html><body>
        <span class="mv_views_count">12,345</span>
        <script>var playerParams = ({"params":[{
          "md_title":"Fixture VK video",
          "md_author":"Fixture Author",
          "duration":195,
          "date":1329049880,
          "jpg":"https://img.example/thumb.jpg",
          "url240":"https://media.example/240.mp4",
          "url360":"//media.example/360.mp4",
          "hls":"https://media.example/master.m3u8",
          "dash_sep":"https://media.example/manifest.mpd",
          "rtmp":"rtmp://media.example/video",
          "live":0,
          "subs":[{"lang":"en","title":"en.srt","url":"https://media.example/en.srt"}]
        }],"thumb":"ignored"});</script>
        </body></html>
    """.trimIndent()

    private val ajaxResponse = """
        {"payload":["0","<html><span class=\"mv_views_count\">777</span></html>",{
          "mvData":{"title":"AJAX title","desc":"AJAX desc","duration":9},
          "player":{"params":[{
            "md_title":"Watch title","md_author":"Watch Author","duration":9,
            "date":1374364108,"jpg":"https://img.example/w.jpg",
            "url480":"https://media.example/480.mp4","live":0
          }]}
        }]}
    """.trimIndent()

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): VKIE =
        VKIE(ExtractorHttp(transfer))

    // ------------------------------------------------------------ URL matching

    @Test
    fun videoFormsMatch() {
        val ie = extractor(transfer())
        for (url in listOf(
            embedUrl,
            "https://vk.com/video-77521_162222515",
            "https://m.vk.com/video205387401_165548505",
            "https://vk.com/videos-77521?z=video-77521_162222515%2Fclub77521",
            "https://vk.com/clip-77521_162222515",
            "https://www.daxab.com/embed/-77521_162222515",
        )) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://vk.com/videos-77521"))
        assertFalse(ie.suitable("https://vk.com/fixtureuser"))
    }

    // -------------------------------------------------------------- extraction

    @Test
    fun embedPageMapsFormatsAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = embedPage),
        )
        val info = extractor(transfer).extract(embedUrl)

        assertEquals("-77521_162222515", info.id)
        assertEquals("Fixture VK video", info.title)
        assertEquals("Fixture Author", info.uploader)
        assertEquals(195.0, info.duration)
        assertEquals("20120212", info.uploadDate)
        assertEquals(12345L, info.viewCount)
        assertEquals(false, info.isLive)
        assertEquals("https://img.example/thumb.jpg", info.thumbnails.single().url)

        assertEquals(5, info.formats.size)
        val sd = info.formats.single { it.formatId == "url240" }
        assertEquals("https://media.example/240.mp4", sd.url)
        assertEquals(240L, sd.height)
        assertEquals(1, sd.sourcePreference)

        val medium = info.formats.single { it.formatId == "url360" }
        assertEquals("https://media.example/360.mp4", medium.url)

        assertEquals("m3u8_native", info.formats.single { it.formatId == "hls" }.protocol)
        assertEquals("http_dash_segments", info.formats.single { it.formatId == "dash_sep" }.protocol)
        assertEquals("flv", info.formats.single { it.formatId == "rtmp" }.ext)

        val subtitle = info.subtitles.single()
        assertEquals("en", subtitle.language)
        assertEquals("srt", subtitle.formats.single().ext)
        assertEquals("https://media.example/en.srt", subtitle.formats.single().url)
    }

    @Test
    fun watchPageUsesThePublicAjaxEndpoint() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://vk.com/al_video.php",
                method = "POST",
                body = ajaxResponse,
            ),
        )
        val info = extractor(transfer).extract(watchUrl)

        assertEquals("205387401_165548505", info.id)
        assertEquals("Watch title", info.title)
        assertEquals("Watch Author", info.uploader)
        assertEquals(9.0, info.duration)
        assertEquals("20130720", info.uploadDate)
        assertEquals(777L, info.viewCount)
        assertEquals("https://media.example/480.mp4", info.formats.single().url)
    }

    @Test
    fun loginRequiredCodeFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://vk.com/al_video.php",
                method = "POST",
                body = """{"payload":["3","",""]}""",
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            extractor(transfer).extract(watchUrl)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun vkIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = embedUrl,
            infoDict = mapOf(
                "id" to Expect.Value("-77521_162222515"),
                "title" to Expect.Value("Fixture VK video"),
                "uploader" to Expect.Value("Fixture Author"),
                "upload_date" to Expect.Value("20120212"),
                "duration" to Expect.Value(195L),
                "view_count" to Expect.Value(12345L),
                "formats" to Expect.Count(5),
                "formats.0.format_id" to Expect.Value("url240"),
            ),
            routes = listOf(FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = embedPage)),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> VKIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
