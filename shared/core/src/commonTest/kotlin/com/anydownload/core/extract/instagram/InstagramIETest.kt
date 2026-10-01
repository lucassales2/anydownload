package com.anydownload.core.extract.instagram

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
 * Fixture cases for the Instagram logged-out post subset. Every id, host, and
 * address is synthesized (`*.example`); no cookie or signed media URL
 * appears.
 */
class InstagramIETest {

    private val postUrl = "https://www.instagram.com/p/aye83DjauH/"

    private val product = """
        {
          "pk": "1234567890",
          "video_duration": 12.5,
          "video_codec": "avc1.64001f",
          "has_audio": true,
          "video_versions": [
            {"id": "v1", "type": 101, "url": "https://media.example/video-101.mp4", "width": 640, "height": 1136},
            {"id": "v2", "type": 102, "url": "https://media.example/video-102.mp4", "width": 720, "height": 1280}
          ],
          "image_versions2": {"candidates": [
            {"url": "https://media.example/thumb-1080.jpg", "width": 1080, "height": 1920},
            {"url": "https://media.example/thumb-640.jpg", "width": 640, "height": 1136}
          ]},
          "caption": {"text": "Synthetic Instagram caption"},
          "user": {"pk": "555", "username": "fixtureuser", "full_name": "Fixture User"},
          "taken_at": 1669516858,
          "view_count": 123
        }
    """.trimIndent()

    private fun sjsPage(productJson: String): String = """
        <html><body><script type="application/json" data-sjs>
        {"require":[["RelayPrefetchedStreamCache",["__bbox",{"result":{"data":{"xig_polaris_media":{
          "if_not_gated_logged_out":$productJson
        }}}}]]]}
        </script></body></html>
    """.trimIndent()

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): InstagramIE =
        InstagramIE(ExtractorHttp(transfer))

    // ------------------------------------------------------------ URL matching

    @Test
    fun postFormsMatch() {
        val ie = extractor(transfer())
        for (url in listOf(
            "https://www.instagram.com/p/aye83DjauH/",
            "https://instagram.com/tv/BkfuX9UB-eK/",
            "https://www.instagram.com/reel/Chunk8-jurw/",
            "https://www.instagram.com/reels/Cop84x6u7CP/",
            "https://www.instagram.com/marvelskies.fc/reel/CWqAgUZgCku/",
            "https://instagram.com/p/9o6LshA7zy/embed/",
        )) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://www.instagram.com/share/reel/xyz"))
        assertFalse(ie.suitable("https://www.instagram.com/explore/tags/cats/"))
        assertFalse(ie.suitable("https://www.instagram.com/stories/fixtureuser/123/"))
    }

    // -------------------------------------------------------------- extraction

    @Test
    fun loggedOutProductMapsFormatsAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = postUrl, contentType = "text/html", body = sjsPage(product)),
        )
        val info = extractor(transfer).extract(postUrl)

        assertEquals("BJlgLS", info.id)
        assertEquals("Video by fixtureuser", info.title)
        assertEquals("Synthetic Instagram caption", info.description)
        assertEquals(12.5, info.duration)
        assertEquals("Fixture User", info.uploader)
        assertEquals("fixtureuser", info.channel)
        assertEquals("555", info.channelId)
        assertEquals("20221127", info.uploadDate)
        assertEquals(123L, info.viewCount)

        assertEquals(2, info.thumbnails.size)
        assertEquals("https://media.example/thumb-640.jpg", info.thumbnails[0].url)
        assertEquals("https://media.example/thumb-1080.jpg", info.thumbnails[1].url)

        assertEquals(2, info.formats.size)
        val first = info.formats[0]
        assertEquals("v1", first.formatId)
        assertEquals("https://media.example/video-101.mp4", first.url)
        assertEquals(640L, first.width)
        assertEquals(1136L, first.height)
        assertEquals("avc1.64001f", first.vcodec)
        assertEquals("https://www.instagram.com/", first.httpHeaders?.get("referer"))
    }

    @Test
    fun carouselBecomesBoundedMediaItems() = runTest {
        val carousel = """
            {
              "pk": "1234567890",
              "caption": {"text": "Synthetic carousel"},
              "user": {"pk": "555", "username": "fixtureuser", "full_name": "Fixture User"},
              "taken_at": 1669516858,
              "carousel_media": [
                {"pk": "987654321", "video_duration": 5,
                 "video_versions": [{"id": "v1", "url": "https://media.example/one.mp4", "width": 640, "height": 1136}]},
                {"pk": "111111111", "video_duration": 7,
                 "video_versions": [{"id": "v2", "url": "https://media.example/two.mp4", "width": 720, "height": 1280}]}
              ]
            }
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = postUrl, contentType = "text/html", body = sjsPage(carousel)),
        )
        val info = extractor(transfer).extract(postUrl)

        assertEquals("Post by fixtureuser", info.title)
        assertEquals(2, info.media.size)
        assertEquals("63mix", info.media[0].mediaId)
        assertEquals("https://media.example/one.mp4", info.media[0].formats.single().url)
        assertEquals("Gn2vH", info.media[1].mediaId)
        assertEquals(7.0, info.media[1].duration)
    }

    @Test
    fun openGraphFallbackStillFindsTheVideo() = runTest {
        val page = """
            <html><head>
            <meta property="og:title" content="Fixture post">
            <meta property="og:description" content="A fixture description">
            <meta property="og:video" content="https://media.example/og-video.mp4">
            <meta property="og:image" content="https://media.example/og-thumb.jpg">
            </head><body>no data-sjs here</body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = postUrl, contentType = "text/html", body = page))
        val info = extractor(transfer).extract(postUrl)

        assertEquals("aye83DjauH", info.id)
        assertEquals("Fixture post", info.title)
        assertEquals("https://media.example/og-video.mp4", info.formats.single().url)
        assertEquals("https://media.example/og-thumb.jpg", info.thumbnails.single().url)
    }

    @Test
    fun gatedPostFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = postUrl, body = "<html><body>Login required</body></html>"),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            extractor(transfer).extract(postUrl)
        }
    }

    @Test
    fun iosSchemeRedirects() = runTest {
        val ios = InstagramIOSIE(ExtractorHttp(transfer()))
        val info = ios.extract("instagram://media?id=1234567890")
        assertEquals("BJlgLS", info.id)
        assertEquals("https://instagram.com/p/BJlgLS", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun instagramIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = postUrl,
            infoDict = mapOf(
                "id" to Expect.Value("BJlgLS"),
                "title" to Expect.Value("Video by fixtureuser"),
                "channel" to Expect.Value("fixtureuser"),
                "upload_date" to Expect.Value("20221127"),
                "duration" to Expect.Value(12.5),
                "view_count" to Expect.Value(123L),
                "formats" to Expect.Count(2),
                "formats.0.format_id" to Expect.Value("v1"),
            ),
            routes = listOf(FixtureRoute(urlPattern = postUrl, contentType = "text/html", body = sjsPage(product))),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> InstagramIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
