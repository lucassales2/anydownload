package com.anydownlod.core.extract.soundcloud

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
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
import kotlin.test.assertTrue

/**
 * Fixture cases for the SoundCloud track subset. Every id, host, address, and
 * client id is synthesized (`*.example`); no real client id, OAuth token,
 * cookie, or signed media URL appears.
 */
class SoundcloudIETest {

    private val trackInfo = """
        {
          "id": 123456789,
          "title": "Synthetic SoundCloud track",
          "description": "A synthetic track description",
          "duration": 143206,
          "created_at": "2026-08-19T10:00:00Z",
          "playback_count": 42,
          "permalink_url": "https://soundcloud.com/fixtureuser/fixture-track",
          "user": {"id": 7, "username": "Fixture Uploader",
                   "permalink_url": "https://soundcloud.com/fixtureuser",
                   "avatar_url": "https://i.example/avatars/user-7-large.jpg"},
          "artwork_url": "https://i.example/artworks/track-123-large.jpg",
          "media": {"transcodings": [
            {"url": "https://api-v2.soundcloud.com/media/soundcloud:tracks:123456789/1/http_aac",
             "preset": "aac_160k",
             "format": {"protocol": "http", "mime_type": "audio/mp4; codecs=\"mp4a.40.2\""}},
            {"url": "https://api-v2.soundcloud.com/media/soundcloud:tracks:123456789/2/hls_aac",
             "preset": "aac_160k",
             "format": {"protocol": "hls", "mime_type": "audio/mp4; codecs=\"mp4a.40.2\""}},
            {"url": "https://api-v2.soundcloud.com/media/soundcloud:tracks:123456789/3/http_opus",
             "preset": "opus_0_0",
             "format": {"protocol": "progressive", "mime_type": "audio/ogg; codecs=\"opus\""}},
            {"url": "https://api-v2.soundcloud.com/media/soundcloud:tracks:123456789/4/ctr_aac",
             "preset": "aac_160k",
             "format": {"protocol": "ctr-aes", "mime_type": "audio/mp4"}}
          ]}
        }
    """.trimIndent()

    private val mainPage = """<html><head><script src="https://a.example/scripts/app.js"></script></head></html>"""

    private val appJs = """window.__sc = { client_id: "FAKECLIENTID0123456789abcdefghij" };"""

    private fun routes(info: String = trackInfo): List<FixtureRoute> = listOf(
        FixtureRoute(urlPattern = "https://soundcloud.com/", contentType = "text/html", body = mainPage),
        FixtureRoute(urlPattern = "https://a.example/scripts/app.js", body = appJs),
        FixtureRoute(urlPattern = "https://api-v2.soundcloud.com/resolve?*", body = info),
        FixtureRoute(urlPattern = "https://api-v2.soundcloud.com/tracks/123456789?*", body = info),
        FixtureRoute(
            urlPattern = "https://api-v2.soundcloud.com/media/*/1/http_aac*",
            body = """{"url":"https://media.example/stream/123456789/160.mp3?x=1"}""",
        ),
        FixtureRoute(
            urlPattern = "https://api-v2.soundcloud.com/media/*/2/hls_aac*",
            body = """{"url":"https://media.example/stream/123456789/hls/160.m3u8?x=1"}""",
        ),
        FixtureRoute(
            urlPattern = "https://api-v2.soundcloud.com/media/*/3/http_opus*",
            body = """{"url":"https://media.example/stream/123456789/opus.opus?x=1"}""",
        ),
    )

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): SoundcloudIE =
        SoundcloudIE(ExtractorHttp(transfer))

    // ------------------------------------------------------------ URL matching

    @Test
    fun trackFormsMatch() {
        val ie = extractor(transfer())
        val urls = listOf(
            "https://soundcloud.com/fixtureuser/fixture-track",
            "https://m.soundcloud.com/fixtureuser/fixture-track",
            "https://soundcloud.com/fixtureuser/fixture-track/s-abcdef",
            "https://api.soundcloud.com/tracks/123456789",
            "https://api.soundcloud.com/tracks/123456789?secret_token=s-abcdef",
            "https://api-v2.soundcloud.com/tracks/123456789",
        )
        for (url in urls) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://soundcloud.com/fixtureuser/sets/fixture-set"))
        assertFalse(ie.suitable("https://soundcloud.com/fixtureuser/tracks"))
        assertFalse(ie.suitable("https://soundcloud.com/stations/track/fixture"))
        assertFalse(ie.suitable("https://soundcloud.com/fixtureuser/likes"))

        val embed = SoundcloudEmbedIE(ExtractorHttp(transfer()))
        assertTrue(
            embed.suitable("https://w.soundcloud.com/player/?url=https%3A%2F%2Fapi.soundcloud.com%2Ftracks%2F1"),
        )
    }

    // -------------------------------------------------------------- extraction

    @Test
    fun trackExtractionMapsFormatsAndMetadata() = runTest {
        val transfer = transfer(*routes().toTypedArray())
        val info = extractor(transfer).extract("https://soundcloud.com/fixtureuser/fixture-track")

        assertEquals("123456789", info.id)
        assertEquals("Synthetic SoundCloud track", info.title)
        assertEquals("A synthetic track description", info.description)
        assertEquals(143.206, info.duration)
        assertEquals("20260819", info.uploadDate)
        assertEquals(42L, info.viewCount)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals("7", info.channelId)

        assertEquals(10, info.thumbnails.size)
        val large = info.thumbnails.single { it.id == "t500x500" }
        assertEquals("https://i.example/artworks/track-123-t500x500.jpg", large.url)
        assertEquals(500L, large.width)
        val original = info.thumbnails.single { it.id == "original" }
        assertEquals("https://i.example/artworks/track-123-original.jpg", original.url)
        assertEquals(10, original.preference)

        assertEquals(3, info.formats.size)
        val httpAac = info.formats[0]
        assertEquals("http_aac_160k", httpAac.formatId)
        assertEquals("http", httpAac.protocol)
        assertEquals("m4a", httpAac.ext)
        assertEquals("mp4a.40.2", httpAac.acodec)
        assertEquals(160.0, httpAac.abr)
        assertEquals("m4a_dash", httpAac.container)

        val hlsAac = info.formats[1]
        assertEquals("hls_aac_160k", hlsAac.formatId)
        assertEquals("m3u8_native", hlsAac.protocol)

        val opus = info.formats[2]
        assertEquals("http_opus_0_0", opus.formatId)
        assertEquals("opus", opus.ext)
        assertEquals("opus", opus.acodec)
        assertEquals("none", opus.vcodec)
    }

    @Test
    fun apiTrackUrlExtracts() = runTest {
        val transfer = transfer(*routes().toTypedArray())
        val info = extractor(transfer).extract("https://api.soundcloud.com/tracks/123456789")
        assertEquals("123456789", info.id)
        assertEquals(3, info.formats.size)
        assertEquals("https://soundcloud.com/fixtureuser/fixture-track", info.webpageUrl)
    }

    @Test
    fun drmOnlyTrackFailsTyped() = runTest {
        val drm = """
            {
              "id": 123456789,
              "title": "DRM only",
              "media": {"transcodings": [
                {"url": "https://api-v2.soundcloud.com/media/soundcloud:tracks:123456789/4/ctr_aac",
                 "preset": "aac_160k",
                 "format": {"protocol": "cbc-aes", "mime_type": "audio/mp4"}}
              ]}
            }
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "https://soundcloud.com/", body = mainPage),
            FixtureRoute(urlPattern = "https://a.example/scripts/app.js", body = appJs),
            FixtureRoute(urlPattern = "https://api-v2.soundcloud.com/resolve?*", body = drm),
        )
        assertFailsWith<ExtractionError.NoFormats> {
            extractor(transfer).extract("https://soundcloud.com/fixtureuser/fixture-track")
        }
    }

    // ------------------------------------------------------------------ embed

    @Test
    fun embedRedirectsIntoTheRegistry() = runTest {
        val embedUrl =
            "https://w.soundcloud.com/player/?url=https%3A%2F%2Fapi.soundcloud.com%2Ftracks%2F123456789" +
                "&secret_token=s-fixture"
        val transfer = transfer(*routes().toTypedArray())
        val http = ExtractorHttp(transfer)
        val registry = ExtractorRegistry(listOf(SoundcloudIE(http), SoundcloudEmbedIE(http)))
        val info = registry.extract(embedUrl)

        assertEquals("123456789", info.id)
        assertEquals(3, info.formats.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun soundcloudIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://soundcloud.com/fixtureuser/fixture-track",
            infoDict = mapOf(
                "id" to Expect.Value("123456789"),
                "title" to Expect.Value("Synthetic SoundCloud track"),
                "uploader" to Expect.Value("Fixture Uploader"),
                "upload_date" to Expect.Value("20260819"),
                "duration" to Expect.Value(143.206),
                "view_count" to Expect.Value(42L),
                "formats" to Expect.Count(3),
                "formats.0.format_id" to Expect.Value("http_aac_160k"),
                "formats.1.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = routes(),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> SoundcloudIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
