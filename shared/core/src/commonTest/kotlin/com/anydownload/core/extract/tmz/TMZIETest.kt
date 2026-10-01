package com.anydownload.core.extract.tmz

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
 * Fixture cases for the TMZ subset. Ids and media paths are synthesized on
 * `media.example`; the page URLs are `*.example`-free tmz.com forms with fake
 * ids. No cookie, token, or signed URL appears.
 */
class TMZIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl = "http://www.tmz.com/videos/0-cegprt2p/"

    private val jsonLdPage = FixtureRoute(
        urlPattern = "http://www.tmz.com/videos/*",
        contentType = "text/html",
        body = """
            <html><head><script type="application/ld+json">{
              "@context": "https://schema.org", "@type": "VideoObject",
              "name": "Fixture TMZ", "description": "Fixture description",
              "thumbnailUrl": "https://media.example/thumb.jpg",
              "uploadDate": "2016-07-06T12:00:00Z", "duration": "PT12M52S",
              "contentUrl": "https://media.example/video.mp4",
              "url": "http://www.tmz.com/videos/0-cegprt2p/",
              "author": {"@type": "Organization", "name": "TMZ Staff"}}</script></head>
            <body></body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val ie = TMZIE(http(transfer()))
        assertTrue(ie.suitable(videoUrl))
        assertTrue(ie.suitable("https://www.tmz.com/2015/04/19/bobby-brown-bobbi-kristina-awake-video-concert"))
        assertTrue(ie.suitable("http://www.tmz.com/2016/01/28/adam-silver-sting-drake-blake-griffin/"))
        assertFalse(ie.suitable("https://www.example.com/videos/0-cegprt2p/"))
    }

    // -------------------------------------------------------------- json-ld

    @Test
    fun jsonLdYieldsTheVideoObjectFields() = runTest {
        val info = TMZIE(http(transfer(jsonLdPage))).extract(videoUrl)
        assertEquals(videoUrl, info.id)
        assertEquals("Fixture TMZ", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("20160706", info.uploadDate)
        assertEquals(772.0, info.duration)
        assertEquals("TMZ Staff", info.uploader)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/video.mp4", info.formats[0].url)
    }

    @Test
    fun jsonLdIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value(videoUrl),
                "title" to Expect.Value("Fixture TMZ"),
                "duration" to Expect.Value(772.0),
                "upload_date" to Expect.Value("20160706"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(jsonLdPage),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TMZIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun hlsContentUrlBecomesOneHlsRow() = runTest {
        val page = FixtureRoute(
            urlPattern = "http://www.tmz.com/videos/*",
            contentType = "text/html",
            body = """
                <html><head><script type="application/ld+json">{
                  "@context": "https://schema.org", "@type": "VideoObject",
                  "name": "Fixture HLS", "contentUrl": "https://media.example/master.m3u8",
                  "url": "http://www.tmz.com/videos/0-cegprt2p/"}</script></head></html>
            """.trimIndent(),
        )
        val info = TMZIE(http(transfer(page))).extract(videoUrl)
        assertEquals("hls", info.formats.single().formatId)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // ------------------------------------------------------------ fallbacks

    @Test
    fun youtubeFallbackYieldsOneChildEntry() = runTest {
        val page = FixtureRoute(
            urlPattern = "http://www.tmz.com/videos/*",
            contentType = "text/html",
            body = """<html><body><script>player.cueVideoById('Dddb6IGe-ws');</script></body></html>""",
        )
        val info = TMZIE(http(transfer(page))).extract(videoUrl)
        assertEquals("Dddb6IGe-ws", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.youtube.com/watch?v=Dddb6IGe-ws", info.entries[0].url)
    }

    @Test
    fun twitterFallbackYieldsOneChildEntry() = runTest {
        val page = FixtureRoute(
            urlPattern = "http://www.tmz.com/videos/*",
            contentType = "text/html",
            body = """
                <html><body><blockquote class="twitter-tweet">
                <a href="https://twitter.com/TheMacLife/status/1329450007125225473"></a>
                </blockquote></body></html>
            """.trimIndent(),
        )
        val info = TMZIE(http(transfer(page))).extract(videoUrl)
        assertEquals(1, info.entries.size)
        assertEquals("https://twitter.com/TheMacLife/status/1329450007125225473", info.entries[0].url)
    }

    @Test
    fun pageWithoutVideoFailsTyped() = runTest {
        val page = FixtureRoute(
            urlPattern = "http://www.tmz.com/videos/*",
            contentType = "text/html",
            body = """<html><body><p>No media here.</p></body></html>""",
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            TMZIE(http(transfer(page))).extract(videoUrl)
        }
        assertTrue(error.message!!.contains("No video found"), error.message)
    }
}
