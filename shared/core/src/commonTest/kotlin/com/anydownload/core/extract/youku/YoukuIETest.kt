package com.anydownload.core.extract.youku

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
 * Fixture cases for the Youku subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class YoukuIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl = "https://v.youku.com/v_show/id_XNTE1MzczOTg4MA==.html"
    private val showUrl = "http://list.youku.com/show/id_zc7c670be07ff11e48b3f.html"

    private val upsJson = """
        {"data": {"video": {"title": "Fixture Youku Title", "seconds": 362.97,
          "logo": "https://media.example/logo.jpg", "username": "Fixture Uploader",
          "userid": 123, "tags": ["a"]},
          "uploader": {"homepage": "https://www.youku.com/profile"},
          "stream": [
            {"stream_type": "mp4hd2", "m3u8_url": "https://media.example/master.m3u8",
             "size": 12345, "width": 1280, "height": 720},
            {"stream_type": "flv", "m3u8_url": "https://media.example/tail.m3u8", "channel_type": "tail"}
          ]}}
    """.trimIndent()

    private val upsRoute = FixtureRoute(
        urlPattern = "https://ups.youku.com/ups/get.json*",
        contentType = "application/json",
        body = upsJson,
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val youku = YoukuIE(http(transfer()))
        val youkuCases = listOf(
            videoUrl,
            "http://v.youku.com/v_show/id_XNjA1NzA2Njgw.html",
            "http://player.youku.com/player.php/sid/XNDgyMDQ2NTQw/v.swf",
            "https://play.tudou.com/v_show/id_XNjAxNjI2OTU3Ng==.html?",
            "youku:XNjAxNjI2OTU3Ng",
        )
        for (url in youkuCases) {
            assertTrue(youku.suitable(url), "Youku must match: $url")
        }
        assertFalse(youku.suitable(showUrl))

        val show = YoukuShowIE(http(transfer()))
        assertTrue(show.suitable(showUrl))
        assertFalse(show.suitable(videoUrl))
        assertFalse(show.suitable("https://www.example.com/show/id_z1.html"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun videoYieldsTheM3u8RowsAndMetadata() = runTest {
        val transfer = transfer(upsRoute)
        val info = YoukuIE(http(transfer)).extract(videoUrl)
        assertEquals("XNTE1MzczOTg4MA", info.id)
        assertEquals("Fixture Youku Title", info.title)
        assertEquals(362.97, info.duration)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals("123", info.channelId)
        assertEquals("https://media.example/logo.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("h4", info.formats[0].formatId)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals(12345L, info.formats[0].filesize)
        assertEquals(1280L, info.formats[0].width)
        assertEquals(720L, info.formats[0].height)
    }

    @Test
    fun geoBlockedErrorFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://ups.youku.com/ups/get.json*",
                contentType = "application/json",
                body = """{"data": {"error": {"note": "因版权原因无法观看此视频"}}}""",
            ),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            YoukuIE(http(transfer)).extract(videoUrl)
        }
    }

    @Test
    fun privateErrorFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://ups.youku.com/ups/get.json*",
                contentType = "application/json",
                body = """{"data": {"error": {"note": "该视频被设为私密"}}}""",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            YoukuIE(http(transfer)).extract(videoUrl)
        }
    }

    @Test
    fun genericErrorFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://ups.youku.com/ups/get.json*",
                contentType = "application/json",
                body = """{"data": {"error": {"code": -1, "note": "boom"}}}""",
            ),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            YoukuIE(http(transfer)).extract(videoUrl)
        }
        assertTrue(error.message!!.contains("-1"), error.message)
    }

    // ------------------------------------------------------------------- show

    @Test
    fun showYieldsTheModuleAndEpisodeEntries() = runTest {
        val page = """
            <html><head><meta name="description" content="Fixture Show, extra"></head>
            <body>
            <script>var PageConfig = {"showid": "zshow1"};</script>
            <div class="p-intro"><div class="intro-more">Fixture description</div></div>
            </body></html>
        """.trimIndent()
        val module = """
            cb({"html": "<div class='p-drama-grid'>
              <a href=\"//v.youku.com/v_show/id_X1.html\">1</a>
              <a href=\"//v.youku.com/v_show/id_X2.html\">2</a></div>
              <div id=\"reload_1\"></div><li data-id=\"r2\">x</li>"});
        """.trimIndent()
        val episode = """
            cb({"html": "<div class='p-drama-grid'><a href=\"//v.youku.com/v_show/id_X3.html\">3</a></div>"});
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "$showUrl*", contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "http://list.youku.com/show/module?id=zshow1&tab=showInfo&callback=cb",
                contentType = "text/javascript",
                body = module,
            ),
            FixtureRoute(
                urlPattern = "http://list.youku.com/show/episode?id=zshow1&stage=r2&callback=cb",
                contentType = "text/javascript",
                body = episode,
            ),
        )
        val info = YoukuShowIE(http(transfer)).extract(showUrl)
        assertEquals("zc7c670be07ff11e48b3f", info.id)
        assertEquals("Fixture Show", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(3, info.entries.size)
        assertEquals("http://v.youku.com/v_show/id_X1.html", info.entries[0].url)
        assertEquals("http://v.youku.com/v_show/id_X3.html", info.entries[2].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("XNTE1MzczOTg4MA"),
                "title" to Expect.Value("Fixture Youku Title"),
                "duration" to Expect.Value(362.97),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(upsRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> YoukuIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun showIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val page = """
            <html><head><meta name="description" content="Fixture Show, extra"></head>
            <body>
            <script>var PageConfig = {"showid": "zshow1"};</script>
            <div class="p-intro"><div class="intro-more">Fixture description</div></div>
            </body></html>
        """.trimIndent()
        val case = ExtractorCase(
            url = showUrl,
            infoDict = mapOf(
                "id" to Expect.Value("zc7c670be07ff11e48b3f"),
                "title" to Expect.Value("Fixture Show"),
                "entries" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "$showUrl*", contentType = "text/html", body = page),
                FixtureRoute(
                    urlPattern = "http://list.youku.com/show/module?id=zshow1&tab=showInfo&callback=cb",
                    contentType = "text/javascript",
                    body = """
                        cb({"html": "<div class='p-drama-grid'>
                          <a href=\"//v.youku.com/v_show/id_X1.html\">1</a>
                          <a href=\"//v.youku.com/v_show/id_X2.html\">2</a></div>
                          <div id='reload_1'></div>"});
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> YoukuShowIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
