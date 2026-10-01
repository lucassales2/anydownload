package com.anydownload.core.extract.lifenews

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
 * Fixture cases for the Life.ru subset. Ids and media paths are synthesized on
 * `media.example`; the embed id is a fake 32-hex value. No cookie, token, or
 * signed URL appears.
 */
class LifeNewsIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val articleUrl = "https://life.ru/t/новости/98736"
    private val multiUrl = "https://life.ru/t/новости/153461"
    private val embedId = "e50c2dec2867350528e2574c899b8291"
    private val embedUrl = "https://embed.life.ru/embed/$embedId"

    private val singlePage = FixtureRoute(
        urlPattern = "https://life.ru/t/*/98736",
        contentType = "text/html",
        body = """
            <html><head>
            <meta property="og:title" content="Fixture Title - Life.ru">
            <meta property="og:description" content="Fixture description">
            </head><body>
            <video controls><source src="/video/98736.mp4" type="video/mp4"></video>
            <div class="hits-count"> 1234 </div>
            <time datetime="2012-08-05T12:00:00+04:00"></time>
            </body></html>
        """.trimIndent(),
    )

    private val embedPage = FixtureRoute(
        urlPattern = "http://embed.life.ru/embed/$embedId",
        contentType = "text/html",
        body = """
            <html><body><script>
            options = {"playlist": {"master": "/master.m3u8",
              "original": "https://media.example/original.mp4",
              "image": "https://media.example/thumb.jpg"}};
            </script></body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val news = LifeNewsIE(http(transfer()))
        assertTrue(news.suitable(articleUrl))
        assertTrue(news.suitable("https://life.ru/t/%D0%BD%D0%BE%D0%B2%D0%BE%D1%81%D1%82%D0%B8/153461"))
        assertFalse(news.suitable(embedUrl))

        val embed = LifeEmbedIE(http(transfer()))
        assertTrue(embed.suitable(embedUrl))
        assertTrue(embed.suitable("https://embed.life.ru/video/$embedId"))
        assertFalse(embed.suitable(articleUrl))
        assertFalse(embed.suitable("https://www.example.com/embed/x"))
    }

    // ------------------------------------------------------------- article

    @Test
    fun singleVideoYieldsTheDirectRow() = runTest {
        val info = LifeNewsIE(http(transfer(singlePage))).extract(articleUrl)
        assertEquals("98736", info.id)
        assertEquals("Fixture Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(1234L, info.viewCount)
        assertEquals("20120805", info.uploadDate)
        assertEquals(1, info.formats.size)
        assertEquals("https://life.ru/video/98736.mp4", info.formats[0].url)
    }

    @Test
    fun singleVideoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = articleUrl,
            infoDict = mapOf(
                "id" to Expect.Value("98736"),
                "title" to Expect.Value("Fixture Title"),
                "view_count" to Expect.Value(1234L),
                "upload_date" to Expect.Value("20120805"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(singlePage),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> LifeNewsIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun singleIframeDispatchesToTheEmbed() = runTest {
        val page = FixtureRoute(
            urlPattern = "https://life.ru/t/*/152125",
            contentType = "text/html",
            body = """
                <html><head><meta property="og:title" content="Fixture Iframe - Life.ru"></head>
                <body><iframe src="//embed.life.ru/embed/$embedId"></iframe></body></html>
            """.trimIndent(),
        )
        val info = LifeNewsIE(http(transfer(page, embedPage))).extract("https://life.ru/t/новости/152125")
        assertEquals(embedId, info.id)
        assertEquals("Fixture Iframe", info.title)
        assertEquals(2, info.formats.size)
    }

    @Test
    fun twoVideosYieldMediaItems() = runTest {
        val page = FixtureRoute(
            urlPattern = "https://life.ru/t/*/153461",
            contentType = "text/html",
            body = """
                <html><head><meta property="og:title" content="Fixture Multi - Life.ru"></head>
                <body>
                <video controls><source src="/video/153461-1.mp4"></video>
                <video controls><source src="/video/153461-2.mp4"></video>
                </body></html>
            """.trimIndent(),
        )
        val info = LifeNewsIE(http(transfer(page))).extract(multiUrl)
        assertEquals("153461", info.id)
        assertEquals(2, info.media.size)
        assertEquals("153461-video1", info.media[0].mediaId)
        assertEquals("Fixture Multi (Видео 1)", info.media[0].title)
        assertEquals("https://life.ru/video/153461-1.mp4", info.media[0].formats.single().url)
        assertEquals("153461-video2", info.media[1].mediaId)
    }

    @Test
    fun pageWithoutMediaFailsTyped() = runTest {
        val page = FixtureRoute(
            urlPattern = "https://life.ru/t/*/98736",
            contentType = "text/html",
            body = """<html><body><p>No media here.</p></body></html>""",
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            LifeNewsIE(http(transfer(page))).extract(articleUrl)
        }
        assertTrue(error.message!!.contains("No media links available"), error.message)
    }

    // --------------------------------------------------------------- embed

    @Test
    fun embedPlaylistYieldsTheHlsAndOriginalRows() = runTest {
        val info = LifeEmbedIE(http(transfer(embedPage))).extract(embedUrl)
        assertEquals(embedId, info.id)
        assertEquals(embedId, info.title)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8", info.formats[0].formatId)
        assertEquals("https://embed.life.ru/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("https://media.example/original.mp4", info.formats[1].url)
        assertEquals("1", info.formats[1].quality)
    }

    @Test
    fun embedFallsBackToTheFileField() = runTest {
        val page = FixtureRoute(
            urlPattern = "https://embed.life.ru/embed/$embedId",
            contentType = "text/html",
            body = """
                <html><body><script>var player = {"file": "/fallback.m3u8"};</script></body></html>
            """.trimIndent(),
        )
        val info = LifeEmbedIE(http(transfer(page))).extract(embedUrl)
        assertEquals(1, info.formats.size)
        assertEquals("https://embed.life.ru/fallback.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
    }
}
