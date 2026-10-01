package com.anydownload.core.extract.kick

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
 * Fixture cases for the Kick subset. Ids and media paths are synthesized on
 * `media.example`; the requests are anonymous, so no cookie or token appears.
 */
class KickIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val liveUrl = "https://kick.com/buddha"
    private val vodUrl = "https://kick.com/xqc/videos/5c697a87-afce-4256-b01f-3c8fe71ef5cb"
    private val clipUrl = "https://kick.com/mxddy?clip=clip_01GYXVB5Y8PWAPWCWMSBCFB05X"

    private val liveRoute = FixtureRoute(
        urlPattern = "https://kick.com/api/v2/channels/buddha",
        contentType = "application/json",
        body = """
            {"id": 32807, "name": "Buddha", "user_id": 33057,
             "user": {"username": "buddha", "bio": "Fixture bio", "id": 33057},
             "livestream": {
               "slug": "92722911-nopixel-40", "session_title": "Fixture Stream",
               "channel_id": 32807, "created_at": "2023-04-26T04:57:33.000000Z",
               "viewer_count": 1234, "is_mature": true,
               "playback_url": "https://media.example/live/master.m3u8",
               "thumbnail": {"url": "https://media.example/live.jpg"}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val live = KickIE(http(transfer()))
        assertTrue(live.suitable(liveUrl))
        assertTrue(live.suitable("https://www.kick.com/xqc"))
        assertFalse(live.suitable(vodUrl), "the live class must yield VOD URLs")
        assertFalse(live.suitable(clipUrl), "the live class must yield clip URLs")
        assertFalse(live.suitable("https://kick.com/categories/games"))
        assertFalse(live.suitable("https://kick.com/video/5c697a87-afce-4256-b01f-3c8fe71ef5cb"))

        val vod = KickVODIE(http(transfer()))
        assertTrue(vod.suitable(vodUrl))
        assertFalse(vod.suitable(liveUrl))

        val clip = KickClipIE(http(transfer()))
        assertTrue(clip.suitable(clipUrl))
        assertTrue(clip.suitable("https://kick.com/spreen/clips/clip_01J8RGZRKHXHXXKJEHGRM932A5"))
        assertFalse(clip.suitable(liveUrl))
    }

    // ------------------------------------------------------------- live

    @Test
    fun liveChannelYieldsTheHlsRowAndMetadata() = runTest {
        val info = KickIE(http(transfer(liveRoute))).extract(liveUrl)
        assertEquals("92722911-nopixel-40", info.id)
        assertEquals("Fixture Stream", info.title)
        assertEquals("Fixture bio", info.description)
        assertEquals("buddha", info.channel)
        assertEquals("32807", info.channelId)
        assertEquals("Buddha", info.uploader)
        assertEquals("20230426", info.uploadDate)
        assertEquals(true, info.isLive)
        assertEquals(18, info.ageLimit)
        assertEquals("https://media.example/live.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/live/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
    }

    @Test
    fun liveChannelIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = liveUrl,
            infoDict = mapOf(
                "id" to Expect.Value("92722911-nopixel-40"),
                "title" to Expect.Value("Fixture Stream"),
                "channel" to Expect.Value("buddha"),
                "upload_date" to Expect.Value("20230426"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(liveRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> KickIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun offlineChannelFailsTyped() = runTest {
        val offlineRoute = FixtureRoute(
            urlPattern = "https://kick.com/api/v2/channels/buddha",
            contentType = "application/json",
            body = """{"id": 32807, "name": "Buddha"}""",
        )
        val error = assertFailsWith<ExtractionError.NotYetAvailable> {
            KickIE(http(transfer(offlineRoute))).extract(liveUrl)
        }
        assertTrue(error.message!!.contains("not live"), error.message)
    }

    // -------------------------------------------------------------- VOD

    @Test
    fun vodYieldsTheHlsRowAndMetadata() = runTest {
        val vodRoute = FixtureRoute(
            urlPattern = "https://kick.com/api/v1/video/5c697a87-afce-4256-b01f-3c8fe71ef5cb",
            contentType = "application/json",
            body = """
                {"source": "https://media.example/vod/master.m3u8", "views": 100,
                 "created_at": "2025-08-25T00:00:43.000000Z",
                 "livestream": {"session_title": "Fixture VOD", "duration": 22278000,
                   "thumbnail": "https://media.example/vod.jpg", "is_mature": false, "is_live": false,
                   "channel": {"slug": "xqc", "id": 668,
                     "user": {"username": "xQc", "bio": "Fixture bio", "id": 676}}}}
            """.trimIndent(),
        )
        val info = KickVODIE(http(transfer(vodRoute))).extract(vodUrl)
        assertEquals("5c697a87-afce-4256-b01f-3c8fe71ef5cb", info.id)
        assertEquals("Fixture VOD", info.title)
        assertEquals(22278.0, info.duration)
        assertEquals(100L, info.viewCount)
        assertEquals("20250825", info.uploadDate)
        assertEquals(false, info.isLive)
        assertEquals("xqc", info.channel)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/vod/master.m3u8", info.formats[0].url)
    }

    // -------------------------------------------------------------- clips

    @Test
    fun clipYieldsTheHlsRowAndMetadata() = runTest {
        val clipRoute = FixtureRoute(
            urlPattern = "https://kick.com/api/v2/clips/clip_01GYXVB5Y8PWAPWCWMSBCFB05X/play",
            contentType = "application/json",
            body = """
                {"clip": {"id": "clip_01GYXVB5Y8PWAPWCWMSBCFB05X", "title": "Fixture Clip",
                  "clip_url": "https://media.example/clip/master.m3u8",
                  "thumbnail_url": "https://media.example/clip.jpg",
                  "duration": 35, "views": 10, "likes": 2, "is_mature": false,
                  "created_at": "2023-04-26T04:57:33.000000Z",
                  "channel": {"slug": "mxddy", "id": 133789},
                  "creator": {"username": "AbdCreates", "id": 3309077}}}
            """.trimIndent(),
        )
        val info = KickClipIE(http(transfer(clipRoute))).extract(clipUrl)
        assertEquals("clip_01GYXVB5Y8PWAPWCWMSBCFB05X", info.id)
        assertEquals("Fixture Clip", info.title)
        assertEquals("mxddy", info.channel)
        assertEquals("20230426", info.uploadDate)
        assertEquals(35.0, info.duration)
        assertEquals(10L, info.viewCount)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/clip/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
    }

    @Test
    fun directClipKeepsAPlainRow() = runTest {
        val clipRoute = FixtureRoute(
            urlPattern = "https://kick.com/api/v2/clips/clip_01GYXVB5Y8PWAPWCWMSBCFB05X/play",
            contentType = "application/json",
            body = """
                {"clip": {"id": "clip_01GYXVB5Y8PWAPWCWMSBCFB05X", "title": "Fixture Clip",
                  "clip_url": "https://media.example/clip.mp4"}}
            """.trimIndent(),
        )
        val info = KickClipIE(http(transfer(clipRoute))).extract(clipUrl)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/clip.mp4", info.formats[0].url)
        assertEquals(null, info.formats[0].protocol)
    }
}
