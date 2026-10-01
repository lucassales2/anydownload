package com.anydownload.core.extract.ustream

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
 * Fixture cases for the Ustream subset. Ids and media paths are synthesized on
 * `media.example`; the connection-info host carries random digits and the
 * rsid/rpin values are random, so those routes match with a wildcard. No
 * cookie, token, or signed URL appears.
 */
class UstreamIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val recordedUrl = "http://www.ustream.tv/recorded/20274954"
    private val embedUrl = "http://www.ustream.tv/embed/10299409"
    private val embedRecordedUrl = "http://www.ustream.tv/embed/recorded/59307601"
    private val channelUrl = "http://www.ustream.tv/channel/channeljapan"

    private val directApiRoute = FixtureRoute(
        urlPattern = "https://api.ustream.tv/videos/20274954.json",
        contentType = "application/json",
        body = """
            {"video": {"title": "Fixture Video", "description": "Fixture description",
              "file_size": 1234567, "created_at": 1328577035, "length": 3600, "views": 10,
              "owner": {"username": "yaliberty", "id": 6780869},
              "thumbnail": {"small": "https://media.example/small.jpg"},
              "media_urls": {"flv": "https://media.example/video.flv"}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val video = UstreamIE(http(transfer()))
        assertTrue(video.suitable(recordedUrl))
        assertTrue(video.suitable(embedUrl))
        assertTrue(video.suitable(embedRecordedUrl))
        assertTrue(video.suitable("https://video.ibm.com/embed/recorded/128240221"))
        assertFalse(video.suitable(channelUrl))
        assertFalse(video.suitable("https://www.example.com/recorded/20274954"))

        val channel = UstreamChannelIE(http(transfer()))
        assertTrue(channel.suitable(channelUrl))
        assertFalse(channel.suitable(recordedUrl))
    }

    // ------------------------------------------------------------- embeds

    @Test
    fun embedRecordedYieldsOneChildEntry() = runTest {
        val info = UstreamIE(http(transfer())).extract(embedRecordedUrl)
        assertEquals("59307601", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("http://www.ustream.tv/recorded/59307601", info.entries[0].url)
    }

    @Test
    fun embedYieldsTheOffAirEntries() = runTest {
        val embedPage = FixtureRoute(
            urlPattern = "http://www.ustream.tv/embed/10299409",
            contentType = "text/html",
            body = """
                <html><script>ustream.vars.offAirContentVideoIds=["10299409","10299410"];</script></html>
            """.trimIndent(),
        )
        val info = UstreamIE(http(transfer(embedPage))).extract(embedUrl)
        assertEquals("10299409", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("http://www.ustream.tv/recorded/10299409", info.entries[0].url)
        assertEquals("http://www.ustream.tv/recorded/10299410", info.entries[1].url)
    }

    // ----------------------------------------------------------- recorded

    @Test
    fun recordedYieldsTheDirectRowAndMetadata() = runTest {
        val info = UstreamIE(http(transfer(directApiRoute))).extract(recordedUrl)
        assertEquals("20274954", info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("20120207", info.uploadDate)
        assertEquals(3600.0, info.duration)
        assertEquals(10L, info.viewCount)
        assertEquals("yaliberty", info.uploader)
        assertEquals("https://media.example/small.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("20274954", info.formats[0].formatId)
        assertEquals("https://media.example/video.flv", info.formats[0].url)
        assertEquals("flv", info.formats[0].ext)
        assertEquals(1234567L, info.formats[0].filesize)
    }

    @Test
    fun recordedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = recordedUrl,
            infoDict = mapOf(
                "id" to Expect.Value("20274954"),
                "title" to Expect.Value("Fixture Video"),
                "duration" to Expect.Value(3600.0),
                "upload_date" to Expect.Value("20120207"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(directApiRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> UstreamIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun recordedFallsBackToTheHlsStream() = runTest {
        val emptyApiRoute = FixtureRoute(
            urlPattern = "https://api.ustream.tv/videos/20274954.json",
            contentType = "application/json",
            body = """{"video": {"title": "Fixture Video", "media_urls": {}}}""",
        )
        val connInfoRoute = FixtureRoute(
            urlPattern = "http://r*-1-20274954-recorded-lp-live.ums.ustream.tv/1/ustream?*",
            contentType = "application/json",
            body = """[{"args": [{"host": "stream.example", "connectionId": "fake_value"}]}]""",
        )
        val streamInfoRoute = FixtureRoute(
            urlPattern = "http://stream.example/1/ustream?connectionId=fake_value",
            contentType = "application/json",
            body = """[{"args": [{"stream": {"url": "https://media.example/master.m3u8"}}]}]""",
        )
        val info = UstreamIE(http(transfer(emptyApiRoute, connInfoRoute, streamInfoRoute))).extract(recordedUrl)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
    }

    @Test
    fun apiErrorBecomesTypedUnavailable() = runTest {
        val errorRoute = FixtureRoute(
            urlPattern = "https://api.ustream.tv/videos/20274954.json",
            contentType = "application/json",
            body = """{"error": "Fixture error"}""",
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            UstreamIE(http(transfer(errorRoute))).extract(recordedUrl)
        }
        assertTrue(error.message!!.contains("Fixture error"), error.message)
    }

    // ------------------------------------------------------------ channel

    @Test
    fun channelPaginatesTheSocialstream() = runTest {
        val channelPage = FixtureRoute(
            urlPattern = "http://www.ustream.tv/channel/channeljapan",
            contentType = "text/html",
            body = """<html><head><meta name="ustream:channel_id" content="10874166"></head></html>""",
        )
        val firstPage = FixtureRoute(
            urlPattern = "http://www.ustream.tv/ajax/socialstream/videos/10874166/1.json",
            contentType = "application/json",
            body = """
                {"data": "<div data-content-id=\"111\"></div>\n<div data-content-id=\"222\"></div>",
                 "nextUrl": "/ajax/socialstream/videos/10874166/2.json"}
            """.trimIndent(),
        )
        val secondPage = FixtureRoute(
            urlPattern = "http://www.ustream.tv/ajax/socialstream/videos/10874166/2.json",
            contentType = "application/json",
            body = """{"data": "<div data-content-id=\"333\"></div>", "nextUrl": null}""",
        )
        val info = UstreamChannelIE(http(transfer(channelPage, firstPage, secondPage))).extract(channelUrl)
        assertEquals("10874166", info.id)
        assertEquals(3, info.entries.size)
        assertEquals("http://www.ustream.tv/recorded/111", info.entries[0].url)
        assertEquals("http://www.ustream.tv/recorded/333", info.entries[2].url)
    }
}
