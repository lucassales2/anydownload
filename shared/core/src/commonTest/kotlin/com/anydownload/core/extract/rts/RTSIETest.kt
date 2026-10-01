package com.anydownload.core.extract.rts

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the RTS subset. Ids and media paths are synthesized on
 * `media.example`; the akahd authparams is a fake value. No cookie, token, or
 * signed URL appears.
 */
class RTSIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl = "http://www.rts.ch/archives/tv/divers/3449373-les-enfants-terribles.html"
    private val articleUrl = "http://www.rts.ch/sport/hockey/6693917-hockey-davos-decroche-son-titre.html"

    private val tokenRoute = FixtureRoute(
        urlPattern = "http://tp.srgssr.ch/akahd/token?acl=*",
        contentType = "application/json",
        body = """{"token": {"authparams": "hdnts=fake_value"}}""",
    )

    private val videoRoute = FixtureRoute(
        urlPattern = "http://www.rts.ch/a/3449373.html?f=json/article",
        contentType = "application/json",
        body = """
            {"video": {"JSONinfo": {"title": "Fixture Video", "intro": "Fixture description",
              "duration": 1488, "plays": 10, "programName": "Divers",
              "broadcast_date": "1968-09-21T00:00:00Z",
              "preview_image_url": "https://media.example/thumb.image",
              "streams": {"hls": "https://media.example/master.m3u8",
                          "hls_sd": "https://media.example/sd.m3u8",
                          "f4m": "https://media.example/manifest.f4m",
                          "http": "https://media.example/720-1200k.mp4"},
              "media": [{"url": "clip.mp4", "rate": 800}]}}}
        """.trimIndent(),
    )

    private val compositionRoute = FixtureRoute(
        urlPattern = "https://il.srgssr.ch/integrationlayer/2.0/rts/mediaComposition/video/3449373.json?*",
        contentType = "application/json",
        body = """{"chapterList": [{"id": "3449373", "title": "Fixture Video"}]}""",
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val ie = RTSIE(http(transfer()))
        assertTrue(ie.suitable(videoUrl))
        assertTrue(ie.suitable("rts:3449373"))
        assertTrue(ie.suitable("http://pages.rts.ch/emissions/passe-moi-les-jumelles/5624065-entre-ciel-et-mer.html"))
        assertFalse(ie.suitable("https://www.example.com/archives/tv/divers/3449373-x.html"))
    }

    // ----------------------------------------------------------------- video

    @Test
    fun videoYieldsTheStreamRows() = runTest {
        val info = RTSIE(http(transfer(videoRoute, compositionRoute, tokenRoute))).extract(videoUrl)
        assertEquals("3449373", info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(1488.0, info.duration)
        assertEquals(10L, info.viewCount)
        assertEquals("Divers", info.uploader)
        assertEquals("19680921", info.uploadDate)
        assertEquals("https://media.example/thumb.image", info.thumbnails.single().url)
        assertEquals(3, info.formats.size)
        assertEquals("hls", info.formats[0].formatId)
        assertEquals("https://media.example/master.m3u8?hdnts=fake_value", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("http", info.formats[1].formatId)
        assertEquals(1200.0, info.formats[1].tbr)
        assertEquals("mp4-800k", info.formats[2].formatId)
        assertEquals("http://rtsww-d.rts.ch/clip.mp4", info.formats[2].url)
        assertEquals(800.0, info.formats[2].tbr)
    }

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("3449373"),
                "title" to Expect.Value("Fixture Video"),
                "duration" to Expect.Value(1488.0),
                "upload_date" to Expect.Value("19680921"),
                "formats" to Expect.Count(3),
            ),
            routes = listOf(videoRoute, compositionRoute, tokenRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RTSIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun audioUsesTheAudioDownloadBase() = runTest {
        val audioRoute = FixtureRoute(
            urlPattern = "http://www.rts.ch/a/5706148.html?f=json/article",
            contentType = "application/json",
            body = """
                {"audio": {"title": "Fixture Audio", "duration": "2:03",
                  "streams": {"hls": "https://media.example/audio.m3u8"},
                  "media": [{"url": "clip.mp3"}]}}
            """.trimIndent(),
        )
        val composition = FixtureRoute(
            urlPattern = "https://il.srgssr.ch/integrationlayer/2.0/rts/mediaComposition/audio/5706148.json",
            contentType = "application/json",
            body = """{"chapterList": [{"id": "5706148"}]}""",
        )
        val info = RTSIE(http(transfer(audioRoute, composition, tokenRoute))).extract(
            "http://www.rts.ch/audio/couleur3/programmes/5706148-urban-hippie.html",
        )
        assertEquals("5706148", info.id)
        assertEquals("Fixture Audio", info.title)
        assertEquals(123.0, info.duration)
        assertEquals("http://rtsww-a-d.rts.ch/clip.mp3", info.formats[1].url)
    }

    // -------------------------------------------------------------- articles

    @Test
    fun articleWithItemsYieldsChildEntries() = runTest {
        val route = FixtureRoute(
            urlPattern = "http://www.rts.ch/a/6693917.html?f=json/article",
            contentType = "application/json",
            body = """
                {"title": "Fixture Article",
                 "items": [{"url": "rts:3449373"}, {"url": "rts:3449374"}]}
            """.trimIndent(),
        )
        val info = RTSIE(http(transfer(route))).extract(articleUrl)
        assertEquals("6693917", info.id)
        assertEquals("Fixture Article", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("rts:3449373", info.entries[0].url)
    }

    @Test
    fun articleWithVideoUrnsYieldsSrgssrEntries() = runTest {
        val route = FixtureRoute(
            urlPattern = "http://www.rts.ch/a/6693917.html?f=json/article",
            contentType = "application/json",
            body = """{"title": "Fixture Article"}""",
        )
        val page = FixtureRoute(
            urlPattern = "http://www.rts.ch/sport/hockey/6693917-*",
            contentType = "text/html",
            body = """
                <html><body>
                <article class="content-item"><a data-video-urn="urn:rts:video:12345"></a></article>
                </body></html>
            """.trimIndent(),
        )
        val info = RTSIE(http(transfer(route, page))).extract(articleUrl)
        assertEquals("6693917", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("srgssr:rts:video:12345", info.entries[0].url)
    }
}
