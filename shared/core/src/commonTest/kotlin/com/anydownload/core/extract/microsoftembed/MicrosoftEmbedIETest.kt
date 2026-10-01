package com.anydownload.core.extract.microsoftembed

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
 * Fixture cases for the Microsoft embed subset. Ids, titles, and media paths
 * are synthesized; media lives on `media.example`, and no token appears.
 */
class MicrosoftEmbedIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "RWL07e"

    private val embedJson = """
        {"streams": {
           "apple_HTTP_Live_Streaming": {"url": "https://media.example/hls/master.m3u8"},
           "smooth_Streaming": {"url": "https://media.example/ism/manifest"},
           "mp4_720p": {"url": "https://media.example/video/720.mp4",
                        "heightPixels": 720, "widthPixels": 1280}},
         "captions": {"en-US": {"url": "https://media.example/cc/en.vtt"}},
         "snippet": {"title": "Fixture Microsoft Video", "activeStartDate": "2021-09-14T00:00:00Z",
                     "thumbnails": [{"url": "https://media.example/thumb.jpg",
                                     "width": 100, "height": 50}]}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            MicrosoftEmbedIE(http(transfer())) to "https://www.microsoft.com/en-us/videoplayer/embed/$videoId",
            MicrosoftMediusIE(http(transfer())) to "https://medius.microsoft.com/Embed/video-nc/9640d86c-f513-4889-959e-5dace86e7d2b",
            MicrosoftLearnPlaylistIE(http(transfer())) to "https://learn.microsoft.com/en-us/shows/bash-for-beginners",
            MicrosoftLearnEpisodeIE(http(transfer())) to "https://learn.microsoft.com/en-us/shows/bash-for-beginners/fixture-episode",
            MicrosoftLearnSessionIE(http(transfer())) to "https://learn.microsoft.com/en-us/events/build-2022/fixture-session",
            MicrosoftBuildIE(http(transfer())) to "https://build.microsoft.com/en-US/sessions/b49feb31-afcd-4217-a538-d3ca1d171198",
            MicrosoftBuildIE(http(transfer())) to "https://build.microsoft.com/en-US/sessions",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(MicrosoftLearnPlaylistIE(http(transfer())).suitable("https://learn.microsoft.com/en-us/shows/x/y"))
    }

    // ------------------------------------------------------------------ embed

    @Test
    fun embedApiYieldsFormatsCaptionsAndMetadata() = runTest {
        val url = "https://www.microsoft.com/en-us/videoplayer/embed/$videoId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://prod-video-cms-rt-microsoft-com.akamaized.net/vhs/api/videos/$videoId",
                contentType = "application/json",
                body = embedJson,
            ),
        )
        val info = MicrosoftEmbedIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Microsoft Video", info.title)
        assertEquals("20210914", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("en-US", info.subtitles.single().language)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    // ------------------------------------------------------------- learn

    @Test
    fun learnEpisodeYieldsHlsAndHttpFormats() = runTest {
        val url = "https://learn.microsoft.com/en-us/shows/bash-for-beginners/fixture-episode"
        val page = """
            <html><head><meta name="entryId" content="entry-1">
            <meta property="og:title" content="Fixture Learn Episode">
            <meta property="og:description" content="Fixture description"></head></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://learn.microsoft.com/api/video/public/v1/entries/entry-1",
                contentType = "application/json",
                body = """
                    {"createTime": "2023-02-14T00:00:00Z", "publicVideo": {
                      "adaptiveVideoHLSUrl": "https://media.example/hls/master.m3u8",
                      "highQualityVideoUrl": "https://media.example/video_1280x720_high.mp4",
                      "audioUrl": "https://media.example/audio/128k.m4a",
                      "captions": [{"language": "en-US", "url": "https://media.example/cc/en.vtt"}],
                      "thumbnailOtherSizes": [{"url": "https://media.example/thumb.png"}]}}
                """.trimIndent(),
            ),
        )
        val info = MicrosoftLearnEpisodeIE(http(transfer)).extract(url)
        assertEquals("entry-1", info.id)
        assertEquals("Fixture Learn Episode", info.title)
        assertEquals("20230214", info.uploadDate)
        assertEquals(3, info.formats.size)
        assertEquals("hls", info.formats[0].formatId)
        assertEquals(1280L, info.formats[1].width)
        assertEquals("none", info.formats[2].vcodec)
    }

    @Test
    fun learnSessionRedirectsToTheMediusVideo() = runTest {
        val url = "https://learn.microsoft.com/en-us/events/build-2022/fixture-session"
        val page = """
            <html><head>
            <meta name="externalVideoUrl" content="https://medius.microsoft.com/Embed/video-nc/9640d86c-f513-4889-959e-5dace86e7d2b">
            </head></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = MicrosoftLearnSessionIE(http(transfer)).extract(url)
        assertEquals(
            "https://medius.microsoft.com/Embed/video-nc/9640d86c-f513-4889-959e-5dace86e7d2b",
            info.redirectUrl,
        )
    }

    @Test
    fun mediusIsmFailsTyped() = runTest {
        val url = "https://medius.microsoft.com/Embed/video-nc/9640d86c-f513-4889-959e-5dace86e7d2b"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://medius.microsoft.com/Embed/video-nc/9640d86c-f513-4889-959e-5dace86e7d2b",
                contentType = "text/html",
                body = """<html><script>StreamUrl = "https://media.example/ism/manifest";</script></html>""",
            ),
        )
        assertFailsWith<ExtractionError.NoFormats> {
            MicrosoftMediusIE(http(transfer)).extract(url)
        }
    }

    @Test
    fun buildListingYieldsSessionEntries() = runTest {
        val url = "https://build.microsoft.com/en-US/sessions"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api-v2.build.microsoft.com/api/session/all/en-US",
                contentType = "application/json",
                body = """
                    [{"sessionId": "aee55fb5-fcf9-4b38-b764-a3527cb57554", "title": "Fixture Keynote",
                      "onDemand": "https://medius.microsoft.com/Embed/video-nc/9640d86c-f513-4889-959e-5dace86e7d2b"}]
                """.trimIndent(),
            ),
        )
        val info = MicrosoftBuildIE(http(transfer)).extract(url)
        assertEquals("sessions", info.id)
        assertEquals(1, info.entries.size)
        assertEquals(
            "https://medius.microsoft.com/Embed/video-nc/9640d86c-f513-4889-959e-5dace86e7d2b",
            info.entries.single().url,
        )
    }

    // --------------------------------------------------------------- harness

    @Test
    fun embedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.microsoft.com/en-us/videoplayer/embed/$videoId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Microsoft Video"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://prod-video-cms-rt-microsoft-com.akamaized.net/vhs/api/videos/$videoId",
                    contentType = "application/json",
                    body = embedJson,
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MicrosoftEmbedIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
