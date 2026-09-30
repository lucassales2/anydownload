package com.anydownlod.core.extract.vimeo

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixture cases for the Vimeo subset. Every id, host, and address is
 * synthesized (`*.example`); no Vimeo API token, OAuth secret, cookie, or
 * signed media URL appears.
 */
class VimeoIETest {

    private val configJson = """
        {
          "video": {
            "id": 76979871,
            "title": "Synthetic Vimeo fixture",
            "duration": 62,
            "thumbs": {"640": "https://i.example/thumb-640.jpg", "1280": "https://i.example/thumb-1280.jpg"},
            "owner": {"name": "Fixture Owner", "url": "https://vimeo.example/fixtureowner"},
            "files": {
              "progressive": [
                {"url": "https://media.example/prog-720.mp4", "quality": "720p", "width": 1280, "height": 720,
                 "fps": 30, "bitrate": 2000000}
              ],
              "hls": {"cdns": {"akamai": {"url": "https://media.example/hls/master.json?base64_init=1"}}},
              "dash": {"cdns": {
                "akamai": {"url": "https://media.example/dash/master.json?base64_init=1"},
                "json-api": {"url": "https://media.example/dash-json?json=1"}
              }}
            }
          },
          "request": {
            "text_tracks": [{"lang": "en", "label": "English", "url": "/texttrack/abc123.vtt"}]
          },
          "embed": {
            "chapters": [{"title": "Main", "timecode": 12.5}, {"title": "Intro", "timecode": 0}]
          }
        }
    """.trimIndent()

    private val dashJsonApi = """{"url":"https://media.example/another/master.json?query_string=1"}"""

    private val configUrl = "https://player.example/video/76979871/config"

    private fun watchPage(
        configUrl: String = this.configUrl,
        includeClip: Boolean = true,
    ): String {
        val clip = if (includeClip) {
            ""","clip":{"description":"Synthetic <b>description</b>","uploaded_on":"2026-08-19"}"""
        } else {
            ""
        }
        return """
            <!doctype html><html><head>
            <meta property="og:description" content="Meta description fallback">
            <link rel="license" href="https://creativecommons.org/licenses/by/4.0/">
            </head><body>
            <time datetime="2026-08-19T12:00:00Z">Aug 19</time>
            <script>UserPlays:7; UserLikes:3; UserComments:1;</script>
            <script>
              vimeo.clip_page_config = {"player":{"config_url":"$configUrl"}$clip};
            </script>
            </body></html>
        """.trimIndent()
    }

    private fun playerPage(config: String = configJson): String = """
        <html><body><script>window.playerConfig = $config;</script></body></html>
    """.trimIndent()

    private fun routes(
        pageUrlPattern: String = "https://vimeo.com/76979871*",
        pageHtml: String = watchPage(),
    ): List<FixtureRoute> = listOf(
        FixtureRoute(urlPattern = pageUrlPattern, contentType = "text/html", body = pageHtml),
        FixtureRoute(urlPattern = configUrl, body = configJson),
        FixtureRoute(urlPattern = "https://media.example/dash-json?json=1", body = dashJsonApi),
    )

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): VimeoIE =
        VimeoIE(ExtractorHttp(transfer))

    // ------------------------------------------------------------ URL matching

    @Test
    fun watchPlayerAndChannelFormsMatch() {
        val ie = extractor(transfer())
        val urls = listOf(
            "https://vimeo.com/76979871",
            "http://vimeo.com/56015672#at=0",
            "https://www.vimeo.com/76979871?share=copy",
            "https://vimeo.com/76979871/abcdef0123",
            "https://vimeo.com/channels/staffpicks/75629013",
            "https://player.vimeo.com/video/54469442",
            "https://player.vimeo.com/video/54469442?h=abcdef0123",
        )
        for (url in urls) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://vimeo.com/album/12345"))
        assertFalse(ie.suitable("https://vimeo.com/ondemand/fixture"))
        assertFalse(ie.suitable("https://vimeo.com/watchlater"))
        assertFalse(ie.suitable("https://vimeo.com/channels/staffpicks"))
    }

    // ------------------------------------------------------------------ watch

    @Test
    fun watchPageConfigMapsFormatsAndMetadata() = runTest {
        val transfer = transfer(*routes().toTypedArray())
        val info = extractor(transfer).extract("https://vimeo.com/76979871")

        assertEquals("76979871", info.id)
        assertEquals("Synthetic Vimeo fixture", info.title)
        assertEquals(62.0, info.duration)
        assertEquals("Fixture Owner", info.uploader)
        assertEquals("20260819", info.uploadDate)
        assertEquals("Synthetic description", info.description)
        assertEquals(7L, info.viewCount)
        assertNull(info.channelId)
        assertNull(info.isLive)

        assertEquals(2, info.thumbnails.size)
        assertTrue(info.thumbnails.any { it.url == "https://i.example/thumb-640.jpg" && it.width == 640L })

        val subtitle = info.subtitles.single()
        assertEquals("en", subtitle.language)
        assertEquals("English", subtitle.name)
        assertEquals("https://player.vimeo.com/texttrack/abc123.vtt", subtitle.formats.single().url)

        assertEquals(2, info.chapters.size)
        assertEquals("Intro", info.chapters[0].title)
        assertEquals(0.0, info.chapters[0].startTime)
        assertEquals("Main", info.chapters[1].title)
        assertEquals(12.5, info.chapters[1].startTime)

        assertEquals(4, info.formats.size)
        val progressive = info.formats[0]
        assertEquals("http-720p", progressive.formatId)
        assertEquals("https://media.example/prog-720.mp4", progressive.url)
        assertEquals(1280L, progressive.width)
        assertEquals(720L, progressive.height)
        assertEquals(30.0, progressive.fps)
        assertEquals(2000000.0, progressive.tbr)
        assertEquals(10, progressive.sourcePreference)

        val hls = info.formats.single { it.formatId == "hls-akamai" }
        assertEquals("m3u8_native", hls.protocol)
        assertEquals("https://media.example/hls/master.json?base64_init=1", hls.url)

        val dash = info.formats.single { it.formatId == "dash-akamai" }
        assertEquals("http_dash_segments", dash.protocol)
        assertEquals("https://media.example/dash/master.mpd?base64_init=1", dash.url)

        val dashJson = info.formats.single { it.formatId == "dash-json-api" }
        assertEquals("https://media.example/another/master.mpd?query_string=1", dashJson.url)
    }

    @Test
    fun pageWithoutClipUsesTheMetaDescription() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://vimeo.com/76979871*",
                body = watchPage(includeClip = false),
            ),
            FixtureRoute(urlPattern = configUrl, body = configJson),
            FixtureRoute(urlPattern = "https://media.example/dash-json?json=1", body = dashJsonApi),
        )
        val info = extractor(transfer).extract("https://vimeo.com/76979871")
        assertEquals("Meta description fallback", info.description)
    }

    // ----------------------------------------------------------------- player

    @Test
    fun playerPageReadsTheInlineConfig() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://player.vimeo.com/video/54469442*",
                contentType = "text/html",
                body = playerPage(),
            ),
            FixtureRoute(urlPattern = "https://media.example/dash-json?json=1", body = dashJsonApi),
        )
        val info = extractor(transfer).extract("https://player.vimeo.com/video/54469442")
        assertEquals("76979871", info.id)
        assertEquals("Synthetic Vimeo fixture", info.title)
        assertEquals(4, info.formats.size)
        assertEquals("https://player.vimeo.com/video/54469442", info.webpageUrl)
        assertTrue(transfer.requests.none { it.url == configUrl }, "the player page must not fetch a config URL")
    }

    // ---------------------------------------------------------------- channel

    @Test
    fun channelFormUsesTheDataConfigUrl() = runTest {
        val channelUrl = "https://vimeo.com/channels/staffpicks/75629013"
        val page = """
            <html><body><div id="player" data-config-url="$configUrl"></div></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "https://vimeo.com/channels/staffpicks/75629013*", body = page),
            FixtureRoute(urlPattern = configUrl, body = configJson),
            FixtureRoute(urlPattern = "https://media.example/dash-json?json=1", body = dashJsonApi),
        )
        val info = extractor(transfer).extract(channelUrl)
        assertEquals("staffpicks", info.channelId)
        assertEquals(4, info.formats.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun vimeoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://vimeo.com/76979871",
            infoDict = mapOf(
                "id" to Expect.Value("76979871"),
                "title" to Expect.Value("Synthetic Vimeo fixture"),
                "uploader" to Expect.Value("Fixture Owner"),
                "upload_date" to Expect.Value("20260819"),
                "duration" to Expect.Value(62L),
                "formats" to Expect.Count(4),
                "formats.0.format_id" to Expect.Value("http-720p"),
                "formats.0.height" to Expect.Value(720L),
                "formats.1.protocol" to Expect.Value("m3u8_native"),
                "formats.2.protocol" to Expect.Value("http_dash_segments"),
            ),
            routes = routes(),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> VimeoIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    // ---------------------------------------------------------------- errors

    @Test
    fun passwordProtectedFailsTyped() = runTest {
        val passwordConfig = """
            {"view":4,"video":{"id":76979871,"title":"Protected","files":{}}}
        """.trimIndent()
        val watchTransfer = transfer(
            FixtureRoute(urlPattern = "https://vimeo.com/76979871*", body = watchPage()),
            FixtureRoute(urlPattern = configUrl, body = passwordConfig),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            extractor(watchTransfer).extract("https://vimeo.com/76979871")
        }

        val playerTransfer = transfer(
            FixtureRoute(
                urlPattern = "https://player.vimeo.com/video/54469442*",
                body = playerPage(passwordConfig),
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            extractor(playerTransfer).extract("https://player.vimeo.com/video/54469442")
        }
    }

    @Test
    fun pageWithoutAConfigFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://vimeo.com/76979871*",
                body = "<html><body>No config here.</body></html>",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            extractor(transfer).extract("https://vimeo.com/76979871")
        }
    }

    @Test
    fun upcomingEventFailsTyped() = runTest {
        val upcoming = """
            {"video":{"id":76979871,"title":"Later","live_event":{"status":"pending"},"files":{}}}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "https://vimeo.com/76979871*", body = watchPage()),
            FixtureRoute(urlPattern = configUrl, body = upcoming),
        )
        assertFailsWith<ExtractionError.NotYetAvailable> {
            extractor(transfer).extract("https://vimeo.com/76979871")
        }
    }
}
