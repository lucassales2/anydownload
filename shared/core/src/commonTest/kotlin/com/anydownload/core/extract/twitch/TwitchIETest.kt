package com.anydownload.core.extract.twitch

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
 * Fixture cases for the Twitch VOD and live-stream subset. Every id, host,
 * value, and signature is synthesized (`*.example`); no real token, cookie,
 * or signed media URL appears.
 */
class TwitchIETest {

    private val gqlUrl = "https://gql.twitch.tv/gql"

    private val vodMetadata = """
        [{"data":{"video":{
          "id":"123456789","title":"Fixture VOD","description":"A fixture VOD",
          "lengthSeconds":3600,
          "previewThumbnailURL":"https://static.example/vod-320x180.jpg",
          "publishedAt":"2026-08-19T10:00:00Z","viewCount":42,
          "owner":{"displayName":"Fixture Streamer","login":"fixturechannel"}
        }}}]
    """.trimIndent()

    private val vodToken = """
        {"data":{"videoPlaybackAccessToken":{"value":"fixture-value","signature":"fixture-signature"}}}
    """.trimIndent()

    private val streamMetadata = """
        [
          {"data":{"user":{"stream":{"id":"987654321","createdAt":"2026-08-19T10:00:00Z","viewers":100,"type":"live"}}}},
          {"data":{"user":{"displayName":"Fixture Streamer","broadcastSettings":{"title":"Fixture stream description"}}}},
          {"data":{"user":{"stream":{"previewImageURL":"https://static.example/preview-640x360.jpg"}}}}
        ]
    """.trimIndent()

    private val streamToken = """
        {"data":{"streamPlaybackAccessToken":{"value":"fixture-value","signature":"fixture-signature"}}}
    """.trimIndent()

    private fun vodRoutes(): List<FixtureRoute> = listOf(
        FixtureRoute(urlPattern = gqlUrl, method = "POST", requestBodyContains = "VideoMetadata", body = vodMetadata),
        FixtureRoute(
            urlPattern = gqlUrl,
            method = "POST",
            requestBodyContains = "videoPlaybackAccessToken",
            body = vodToken,
        ),
    )

    private fun streamRoutes(metadata: String = streamMetadata): List<FixtureRoute> = listOf(
        FixtureRoute(urlPattern = gqlUrl, method = "POST", requestBodyContains = "StreamMetadata", body = metadata),
        FixtureRoute(
            urlPattern = gqlUrl,
            method = "POST",
            requestBodyContains = "streamPlaybackAccessToken",
            body = streamToken,
        ),
    )

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    // ------------------------------------------------------------ URL matching

    @Test
    fun vodAndStreamFormsMatch() {
        val vod = TwitchVodIE(ExtractorHttp(transfer()))
        for (url in listOf(
            "https://www.twitch.tv/fixturechannel/v/123456789",
            "http://www.twitch.tv/riotgames/video/6528877?t=5m10s",
            "https://www.twitch.tv/videos/635475444",
            "https://m.twitch.tv/fixturechannel/v/247478721",
            "https://player.twitch.tv/?video=v6528877",
            "https://www.twitch.tv/user/schedule?vodID=123456",
        )) {
            assertTrue(vod.suitable(url), "VOD URL must match: $url")
        }
        assertFalse(vod.suitable("https://www.twitch.tv/fixturechannel"))

        val stream = TwitchStreamIE(ExtractorHttp(transfer()))
        for (url in listOf(
            "https://www.twitch.tv/fixturechannel",
            "https://go.twitch.tv/fixturechannel",
            "https://m.twitch.tv/fixturechannel#profile-0",
            "https://player.twitch.tv/?channel=fixturechannel",
        )) {
            assertTrue(stream.suitable(url), "stream URL must match: $url")
        }
        assertFalse(stream.suitable("https://www.twitch.tv/fixturechannel/videos"))
        assertFalse(stream.suitable("https://www.twitch.tv/videos/123456"))
        assertFalse(stream.suitable("https://www.twitch.tv/fixturechannel/clip/FixtureClip"))
    }

    // -------------------------------------------------------------------- vod

    @Test
    fun vodExtractionMapsMetadataAndUsherUrl() = runTest {
        val transfer = transfer(*vodRoutes().toTypedArray())
        val info = TwitchVodIE(ExtractorHttp(transfer)).extract("https://www.twitch.tv/fixturechannel/v/123456789")

        assertEquals("v123456789", info.id)
        assertEquals("Fixture VOD", info.title)
        assertEquals("A fixture VOD", info.description)
        assertEquals(3600.0, info.duration)
        assertEquals("Fixture Streamer", info.uploader)
        assertEquals("fixturechannel", info.channel)
        assertEquals("20260819", info.uploadDate)
        assertEquals(42L, info.viewCount)
        assertEquals(false, info.isLive)

        assertEquals(2, info.thumbnails.size)
        assertEquals("https://static.example/vod-0x0.jpg", info.thumbnails[0].url)
        assertEquals(1, info.thumbnails[0].preference)

        val format = info.formats.single()
        assertEquals("m3u8_native", format.protocol)
        assertTrue(format.url?.startsWith("https://usher.ttvnw.net/vod/123456789.m3u8?") == true)
        assertTrue(format.url?.contains("sig=fixture-signature") == true)
        assertTrue(format.url?.contains("token=fixture-value") == true)
    }

    // ----------------------------------------------------------------- stream

    @Test
    fun streamExtractionMapsMetadataAndUsherUrl() = runTest {
        val transfer = transfer(*streamRoutes().toTypedArray())
        val info = TwitchStreamIE(ExtractorHttp(transfer)).extract("https://www.twitch.tv/fixturechannel")

        assertEquals("987654321", info.id)
        assertEquals("Fixture Streamer (live)", info.title)
        assertEquals("Fixture stream description", info.description)
        assertEquals("Fixture Streamer", info.uploader)
        assertEquals("fixturechannel", info.channelId)
        assertEquals("20260819", info.uploadDate)
        assertEquals(100L, info.viewCount)
        assertEquals(true, info.isLive)

        val format = info.formats.single()
        assertEquals("m3u8_native", format.protocol)
        assertTrue(format.url?.startsWith("https://usher.ttvnw.net/api/channel/hls/fixturechannel.m3u8?") == true)
    }

    @Test
    fun offlineChannelFailsTyped() = runTest {
        val offline = """
            [{"data":{"user":{"stream":null}}},
             {"data":{"user":null}},
             {"data":{"user":null}}]
        """.trimIndent()
        val transfer = transfer(*streamRoutes(metadata = offline).toTypedArray())
        assertFailsWith<ExtractionError.NotYetAvailable> {
            TwitchStreamIE(ExtractorHttp(transfer)).extract("https://www.twitch.tv/fixturechannel")
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun twitchVodIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://www.twitch.tv/fixturechannel/v/123456789",
            infoDict = mapOf(
                "id" to Expect.Value("v123456789"),
                "title" to Expect.Value("Fixture VOD"),
                "uploader" to Expect.Value("Fixture Streamer"),
                "upload_date" to Expect.Value("20260819"),
                "duration" to Expect.Value(3600L),
                "view_count" to Expect.Value(42L),
                "formats" to Expect.Count(1),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = vodRoutes(),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> TwitchVodIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
