package com.anydownload.core.extract.mediastream

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
 * Fixture cases for the MediaStream subset. Ids and media paths are
 * synthesized on `media.example`; the MDSTRM window values are fake. No
 * cookie, token, or signed URL appears.
 */
class MediaStreamIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val embedUrl = "https://mdstrm.com/embed/6318e3f1d1d316083ae48831?access_token=fake_value"
    private val winUrl =
        "https://www.winsports.co/videos/siempre-castellanos-gran-atajada-del-portero-cardenal-para-evitar-la-caida-de-su-arco-60536"

    private val embedPage = FixtureRoute(
        urlPattern = "https://mdstrm.com/embed/6318e3f1d1d316083ae48831*",
        contentType = "text/html",
        body = """
            <html><head>
            <meta property="og:title" content="Fixture Video">
            <meta property="og:description" content="Fixture description">
            <meta property="og:image" content="https://media.example/thumb.jpg">
            </head><body><script>
            window.MDSTRMUID = "fake_uid";
            window.MDSTRMSID = "fake_sid";
            window.MDSTRMPID = "fake_pid";
            window.VERSION = "fake_version";
            window.MDSTRM.OPTIONS = {"src": {"hls": "https://media.example/master.m3u8",
              "mpd": "https://media.example/manifest.mpd", "mp4": "https://media.example/video.mp4"},
              "type": "live", "title": "Fixture Player"};
            </script></body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val embed = MediaStreamIE(http(transfer()))
        assertTrue(embed.suitable("https://mdstrm.com/embed/6318e3f1d1d316083ae48831"))
        assertTrue(embed.suitable("https://mdstrm.com/live-stream/5a7b1e63a8da282c34d65445"))
        assertFalse(embed.suitable(winUrl))
        assertFalse(embed.suitable("https://www.example.com/embed/abc"))

        val win = WinSportsVideoIE(http(transfer()))
        assertTrue(win.suitable(winUrl))
        assertFalse(win.suitable("https://mdstrm.com/embed/6318e3f1d1d316083ae48831"))
    }

    // --------------------------------------------------------------- embed

    @Test
    fun embedYieldsTheHlsMpdAndDirectRows() = runTest {
        val info = MediaStreamIE(http(transfer(embedPage))).extract(embedUrl)
        assertEquals("6318e3f1d1d316083ae48831", info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(true, info.isLive)
        assertEquals(3, info.formats.size)
        assertEquals("hls", info.formats[0].formatId)
        assertEquals(
            "https://media.example/master.m3u8?at=web-app&access_token=fake_value" +
                "&uid=fake_uid&sid=fake_sid&pid=fake_pid&av=fake_version",
            info.formats[0].url,
        )
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("dash", info.formats[1].formatId)
        assertEquals("https://media.example/manifest.mpd", info.formats[1].url)
        assertEquals("mpd", info.formats[1].protocol)
        assertEquals("https://media.example/video.mp4", info.formats[2].url)
    }

    @Test
    fun embedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = embedUrl,
            infoDict = mapOf(
                "id" to Expect.Value("6318e3f1d1d316083ae48831"),
                "title" to Expect.Value("Fixture Video"),
                "formats" to Expect.Count(3),
            ),
            routes = listOf(embedPage),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MediaStreamIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun geoMessageFailsTyped() = runTest {
        val geoPage = FixtureRoute(
            urlPattern = "https://mdstrm.com/embed/6318e3f1d1d316083ae48831*",
            contentType = "text/html",
            body = "<html><body>Este contenido no está disponible en tu zona geográfica.</body></html>",
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            MediaStreamIE(http(transfer(geoPage))).extract(embedUrl)
        }
    }

    // ------------------------------------------------------------ WinSports

    @Test
    fun winSportsUsesTheDrupalSettingsEmbed() = runTest {
        val winPage = FixtureRoute(
            urlPattern = "https://www.winsports.co/videos/*",
            contentType = "text/html",
            body = """
                <html><head><meta property="og:title" content="Fixture Win Title | Win Sports"></head>
                <body><script data-drupal-selector="drupal-settings-json">
                {"settings": {"mediastream_formatter": {"default": {
                  "mediastream_id": {"url": "63731bab8ec9b308a2c9ed28"}}}}}
                </script></body></html>
            """.trimIndent(),
        )
        val embed = FixtureRoute(
            urlPattern = "https://mdstrm.com/embed/63731bab8ec9b308a2c9ed28*",
            contentType = "text/html",
            body = """
                <html><head><meta property="og:title" content="Fixture Win Player"></head>
                <body><script>
                window.MDSTRM.OPTIONS = {"src": {"hls": "https://media.example/win.m3u8"}, "type": "video"};
                </script></body></html>
            """.trimIndent(),
        )
        val info = WinSportsVideoIE(http(transfer(winPage, embed))).extract(winUrl)
        assertEquals("63731bab8ec9b308a2c9ed28", info.id)
        assertEquals("Fixture Win Title", info.title)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/win.m3u8?at=web-app", info.formats[0].url)
        assertEquals(winUrl, info.webpageUrl)
    }
}
