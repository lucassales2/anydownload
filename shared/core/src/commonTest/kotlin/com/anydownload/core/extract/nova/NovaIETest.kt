package com.anydownload.core.extract.nova

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
 * Fixture cases for the Nova subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class NovaIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val embedUrl = "https://media.cms.nova.cz/embed/8o0n0r?autoplay=1"
    private val novaPlusUrl = "https://novaplus.nova.cz/porad/ulice/epizoda/18760-21-05-2015"
    private val articleUrl = "http://tn.nova.cz/clanek/fixture-article.html"

    private val newPathPage = """
        <html><head>
        <meta property="og:title" content="Fixture Nova Title">
        <meta property="og:image" content="https://media.example/poster.jpg">
        </head><body>
        <script>
        player: {
          "lib": {"source": {"sources": [
            {"src": "https://media.example/master.m3u8", "type": "application/x-mpegURL"},
            {"src": "https://media.example/video.mp4", "type": "video/mp4"}
          ]}},
          "sourceInfo": {"duration": 114}
        };
        </script>
        </body></html>
    """.trimIndent()

    private val drmPage = """
        <html><body>
        <script>
        player: {
          "lib": {"source": {"sources": [
            {"src": "https://media.example/drm.mpd", "type": "application/dash+xml",
             "drm": {"keySystem": "widevine"}}
          ]}},
          "sourceInfo": {"duration": 100}
        };
        </script>
        </body></html>
    """.trimIndent()

    private val articlePage = """
        <html><head><meta property="og:description" content="Fixture article description"></head>
        <body>
        <div id="player_13260"></div>
        <script src="https://api.nova.cz/bin/player/videojs/config.php?site=30&media=13260"></script>
        </body></html>
    """.trimIndent()

    private fun configRoute(source: String, title: String = "Fixture Config Title") = FixtureRoute(
        urlPattern = "https://api.nova.cz/bin/player/videojs/config.php*",
        contentType = "application/json",
        body = """
            {"mediafile": {"src": "$source", "meta": {"title": "$title"}},
             "poster": "https://media.example/poster.jpg"}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val embed = NovaEmbedIE(http(transfer()))
        assertTrue(embed.suitable(embedUrl))
        assertTrue(embed.suitable("https://mediatn.cms.nova.cz/embed/EU5ELEsmOHt?autoplay=1"))

        val nova = NovaIE(http(transfer()))
        val cases = listOf(
            articleUrl,
            "https://novaplus.nova.cz/porad/ulice/epizoda/18760-2180-dil",
            "http://fanda.nova.cz/clanek/fun-and-games/fixture.html",
            "http://sport.tn.nova.cz/clanek/sport/hokej/fixture.html",
        )
        for (url in cases) {
            assertTrue(nova.suitable(url), "Nova must match: $url")
        }
        assertFalse(nova.suitable("https://www.example.com/clanek/fixture.html"))
    }

    // ---------------------------------------------------------------- embed

    @Test
    fun embedPlayerYieldsTheHlsAndMp4Rows() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = "$embedUrl", contentType = "text/html", body = newPathPage))
        val info = NovaEmbedIE(http(transfer)).extract(embedUrl)
        assertEquals("8o0n0r", info.id)
        assertEquals("Fixture Nova Title", info.title)
        assertEquals("https://media.example/poster.jpg", info.thumbnails.single().url)
        assertEquals(114.0, info.duration)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mp4", info.formats[1].ext)
    }

    @Test
    fun drmOnlyPlayerFailsTyped() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = "$embedUrl", contentType = "text/html", body = drmPage))
        assertFailsWith<ExtractionError.Unavailable> {
            NovaEmbedIE(http(transfer)).extract(embedUrl)
        }
    }

    // ---------------------------------------------------------------- article

    @Test
    fun novaPlusPageDelegatesToTheEmbedAndKeepsMetadata() = runTest {
        val page = """
            <html><head><meta property="og:description" content="Outer description"></head>
            <body><iframe src="//media.cms.nova.cz/embed/8o0n0r?autoplay=1"></iframe></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "$novaPlusUrl*", contentType = "text/html", body = page),
            FixtureRoute(urlPattern = "https://media.cms.nova.cz/embed/8o0n0r", contentType = "text/html", body = newPathPage),
        )
        val info = NovaIE(http(transfer)).extract(novaPlusUrl)
        assertEquals("8o0n0r", info.id)
        assertEquals("Fixture Nova Title", info.title)
        assertEquals("Outer description", info.description)
        assertEquals("20150521", info.uploadDate)
        assertEquals(novaPlusUrl, info.webpageUrl)
    }

    @Test
    fun articleConfigYieldsTheDirectMediafile() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = articleUrl, contentType = "text/html", body = articlePage),
            configRoute("https://media.example/video.mp4"),
        )
        val info = NovaIE(http(transfer)).extract(articleUrl)
        assertEquals("13260", info.id)
        assertEquals("Fixture Config Title", info.title)
        assertEquals("Fixture article description", info.description)
        assertEquals("https://media.example/poster.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/video.mp4", info.formats[0].url)
        assertEquals("mp4", info.formats[0].ext)
    }

    @Test
    fun rtmpMediafileFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = articleUrl, contentType = "text/html", body = articlePage),
            configRoute("rtmpe://example.com/app/playpath"),
        )
        assertFailsWith<ExtractionError.NoFormats> {
            NovaIE(http(transfer)).extract(articleUrl)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun embedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = embedUrl,
            infoDict = mapOf(
                "id" to Expect.Value("8o0n0r"),
                "title" to Expect.Value("Fixture Nova Title"),
                "duration" to Expect.Value(114.0),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(FixtureRoute(urlPattern = "$embedUrl", contentType = "text/html", body = newPathPage)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NovaEmbedIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun articleIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = articleUrl,
            infoDict = mapOf(
                "id" to Expect.Value("13260"),
                "title" to Expect.Value("Fixture Config Title"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = articleUrl, contentType = "text/html", body = articlePage),
                configRoute("https://media.example/video.mp4"),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NovaIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
