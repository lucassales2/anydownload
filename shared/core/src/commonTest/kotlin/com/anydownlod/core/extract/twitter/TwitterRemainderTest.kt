package com.anydownlod.core.extract.twitter

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
 * Fixture cases for the T-125 Twitter remainder. Every id, host, and address
 * is synthesized (`*.example`); no token, cookie, real status, or `twimg.com`
 * media URL appears.
 */
class TwitterRemainderTest {

    private val statusId = "1111111111111111111"
    private val amplifyId = "0ba0c3c7-0af3-4c0a-bed5-7efd1ffa2951"

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val amplifyPage = """
        <html><head>
        <meta property="twitter:amplify:vmap" content="https://vmap.example/vmap.xml">
        <meta property="twitter:image:src" content="https://img.example/thumb.jpg">
        <meta property="twitter:image:width" content="640">
        <meta property="twitter:image:height" content="360">
        <meta property="twitter:player:width" content="1280">
        <meta property="twitter:player:height" content="720">
        </head><body></body></html>
    """.trimIndent()

    private val vmapXml = """
        <vmap:VMAP xmlns:vmap="http://twitter.com/schema/videoVMapV2.xsd">
          <videoVariant url="https%3A%2F%2Fmedia.example%2Fv%2F720x1280%2Fclip.mp4" bitrate="256000"/>
          <videoVariant url="https://media.example/hls/playlist.m3u8"/>
          <MediaFile>https://media.example/fallback.mp4</MediaFile>
        </vmap:VMAP>
    """.trimIndent()

    private fun amplifyRoutes(): List<FixtureRoute> = listOf(
        FixtureRoute(
            urlPattern = "https://amp.twimg.com/v/$amplifyId",
            contentType = "text/html",
            body = amplifyPage,
        ),
        FixtureRoute(
            urlPattern = "https://vmap.example/vmap.xml",
            contentType = "application/xml",
            body = vmapXml,
        ),
    )

    private val statusJson = """
        {
          "__typename": "Tweet",
          "id_str": "$statusId",
          "text": "Card fixture post with one video",
          "created_at": "2026-09-10T10:00:00.000Z",
          "user": {
            "id_str": "2222222222222222222",
            "name": "Fixture Poster",
            "screen_name": "fixture_poster",
            "protected": false
          },
          "mediaDetails": [
            {
              "type": "video",
              "media_url_https": "https://pbs.example/media/card.jpg",
              "sizes": {"small": {"w": 680, "h": 383}},
              "video_info": {
                "duration_millis": 9000,
                "variants": [
                  {
                    "bitrate": 256000,
                    "content_type": "video/mp4",
                    "url": "https://video.example/ext_tw_video/3333333333333333333/pu/vid/320x180/card.mp4"
                  }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    // ------------------------------------------------------------------ card

    @Test
    fun cardUrlsMatchAllUpstreamForms() {
        val ie = TwitterCardIE(http(transfer()))
        val urls = listOf(
            "https://twitter.com/i/cards/tfw/v1/560070183650213889",
            "https://x.com/i/cards/tfw/v1/560070183650213889",
            "https://twitter.com/i/videos/tweet/705235433198714880",
            "https://x.com/i/videos/752274308186120192",
        )
        for (url in urls) {
            assertTrue(ie.suitable(url), "URL must match: $url")
            assertEquals(url.substringAfterLast('/'), ie.matchId(url), "match id failed: $url")
        }
        assertFalse(ie.suitable("https://x.com/i/spaces/1OwxWwQOPlNxQ"))
        assertFalse(ie.suitable("https://x.com/fixture/status/$statusId"))
    }

    @Test
    fun cardDelegatesToTheStatusExtractorThroughTheRegistry() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://cdn.syndication.twimg.com/tweet-result?id=$statusId*",
                contentType = "application/json",
                body = statusJson,
            ),
        )
        val registry = ExtractorRegistry(listOf(TwitterIE(http(transfer)), TwitterCardIE(http(transfer))))

        val info = registry.extract("https://twitter.com/i/cards/tfw/v1/$statusId")
        assertEquals(statusId, info.id)
        assertEquals("Fixture Poster - Card fixture post with one video", info.title)
        assertEquals(1, info.media.size)
        assertEquals(TwitterIE.IE_KEY, info.extractorKey)
    }

    // --------------------------------------------------------------- amplify

    @Test
    fun amplifyParsesVmapVariantsAndFallback() = runTest {
        val info = TwitterAmplifyIE(http(transfer(*amplifyRoutes().toTypedArray()))).extract(
            "https://amp.twimg.com/v/$amplifyId",
        )

        assertEquals(amplifyId, info.id)
        assertEquals("Twitter Video", info.title)
        assertEquals(3, info.formats.size)

        val direct = info.formats[0]
        assertEquals("http-256", direct.formatId)
        assertEquals("https://media.example/v/720x1280/clip.mp4", direct.url)
        assertEquals("https", direct.protocol)
        assertEquals(256.0, direct.tbr)
        // The player meta dimensions win on the first format.
        assertEquals(1280L, direct.width)
        assertEquals(720L, direct.height)

        val hls = info.formats[1]
        assertEquals("hls", hls.formatId)
        assertEquals("m3u8_native", hls.protocol)

        val fallback = info.formats[2]
        assertEquals("https://media.example/fallback.mp4", fallback.url)
        assertEquals("http", fallback.formatId)

        val thumbnail = info.thumbnails.single()
        assertEquals("https://img.example/thumb.jpg", thumbnail.url)
        assertEquals(640L, thumbnail.width)
        assertEquals(360L, thumbnail.height)
    }

    @Test
    fun amplifyIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://amp.twimg.com/v/$amplifyId",
            infoDict = mapOf(
                "id" to Expect.Value(amplifyId),
                "title" to Expect.Value("Twitter Video"),
                "formats" to Expect.Count(3),
                "formats.0.format_id" to Expect.Value("http-256"),
                "formats.0.width" to Expect.Value(1280L),
                "formats.1.protocol" to Expect.Value("m3u8_native"),
                "formats.2.url" to Expect.Value("https://media.example/fallback.mp4"),
                "thumbnails.0.url" to Expect.Value("https://img.example/thumb.jpg"),
            ),
            routes = amplifyRoutes(),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TwitterAmplifyIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    // ------------------------------------------------------------- shortener

    @Test
    fun shortenerResolvesAndRedispatchesToTheTargetExtractor() = runTest {
        val routes = listOf(
            FixtureRoute(
                urlPattern = "https://t.co/fixture1",
                redirectTo = "https://amp.twimg.com/v/$amplifyId",
            ),
        ) + amplifyRoutes()
        val transfer = transfer(*routes.toTypedArray())
        val registry = ExtractorRegistry(
            listOf(TwitterAmplifyIE(http(transfer)), TwitterShortenerIE(http(transfer))),
        )

        val info = registry.extract("https://t.co/fixture1")
        assertEquals(amplifyId, info.id)
        assertEquals(3, info.formats.size)
        assertEquals(TwitterAmplifyIE.IE_KEY, info.extractorKey)
    }

    @Test
    fun shortenerAcceptsTheTcoKeywordForm() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://t.co/fixture2",
                redirectTo = "https://amp.twimg.com/v/$amplifyId",
            ),
            *amplifyRoutes().toTypedArray(),
        )
        val ie = TwitterShortenerIE(http(transfer))
        assertTrue(ie.suitable("tco:fixture2"))
        assertEquals("fixture2", ie.matchId("tco:fixture2"))
        val info = ie.extract("tco:fixture2")
        assertEquals("https://amp.twimg.com/v/$amplifyId", info.redirectUrl)
    }

    @Test
    fun shortenerStripsTheUnsafeLinkPrefix() = runTest {
        val unsafeLink = "https://twitter.com/safety/unsafe_link_warning" +
            "?unsafe_link=https://amp.twimg.com/v/$amplifyId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://t.co/fixture3",
                redirectTo = unsafeLink,
            ),
            FixtureRoute(urlPattern = unsafeLink, body = "<html></html>"),
        )
        val info = TwitterShortenerIE(http(transfer)).extract("https://t.co/fixture3")
        assertEquals("https://amp.twimg.com/v/$amplifyId", info.redirectUrl)
    }

    // ------------------------------------------------- broadcasts and Spaces

    @Test
    fun broadcastAndEventsUrlsMatchAndFailTyped() = runTest {
        val ie = TwitterBroadcastIE(http(transfer()))
        val urls = listOf(
            "https://twitter.com/i/broadcasts/1yNGaQLWpejGj",
            "https://x.com/i/broadcasts/1ZkKzeyrPbaxv",
            "https://x.com/i/events/1910629646300762112",
        )
        for (url in urls) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        val error = assertFailsWith<ExtractionError.Unavailable> {
            ie.extract("https://x.com/i/broadcasts/1yNGaQLWpejGj")
        }
        assertTrue(error.message!!.contains("Twitter API token"), "message must name the token gap")
    }

    @Test
    fun spacesUrlsMatchAndFailTyped() = runTest {
        val ie = TwitterSpacesIE(http(transfer()))
        assertTrue(ie.suitable("https://twitter.com/i/spaces/1OwxWwQOPlNxQ"))
        assertTrue(ie.suitable("https://x.com/i/spaces/1vAxRAVQWONJl"))
        assertFalse(ie.suitable("https://x.com/i/spaces/too-short"))
        val error = assertFailsWith<ExtractionError.Unavailable> {
            ie.extract("https://x.com/i/spaces/1OwxWwQOPlNxQ")
        }
        assertTrue(error.message!!.contains("Twitter API token"), "message must name the token gap")
    }
}
