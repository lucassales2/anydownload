package com.anydownlod.core.extract.youtube

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.harness.CaseResult
import com.anydownlod.core.extract.harness.Expect
import com.anydownlod.core.extract.harness.ExtractorCase
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import com.anydownlod.core.extract.harness.runCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fixture cases for the JS-less `visionos` extractor. Every URL, host, and
 * token in the fixtures is synthesized; no real player response, signed
 * `googlevideo` URL, visitor id, or cookie appears here.
 */
class YoutubeIETest {

    private val videoId = "YE7VzlLtp-4"

    private fun http(body: String = playerJson()): ExtractorHttp = ExtractorHttp(
        FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://www.youtube.com/youtubei/v1/player*",
                    method = "POST",
                    contentType = "application/json",
                    body = body,
                ),
            ),
        ),
    )

    private suspend fun extract(url: String, body: String = playerJson()): InfoDict =
        YoutubeIE(http(body)).extract(url)

    // -------------------------------------------------------------- happy path

    @Test
    fun watchUrlMapsMetadataAndFormats() = runTest {
        val info = extract("https://www.youtube.com/watch?v=$videoId")

        assertEquals(videoId, info.id)
        assertEquals("Big Buck Bunny", info.title)
        assertEquals("Fixture Channel", info.channel)
        assertEquals("Fixture Channel", info.uploader)
        assertEquals("UCfixturechannel000000000", info.channelId)
        assertEquals(596.0, info.duration)
        assertEquals(123_456L, info.viewCount)
        assertEquals("A fixture description.", info.description)
        assertEquals("20080529", info.uploadDate)
        assertEquals(0, info.ageLimit)
        assertEquals(false, info.isLive)
        assertEquals("https://www.youtube.com/watch?v=$videoId", info.webpageUrl)
        assertEquals(YoutubeIE.IE_KEY, info.extractorKey)

        // Thumbnails sorted by area: 120x90, 480x360, 1280x720.
        assertEquals(3, info.thumbnails.size)
        assertEquals(120L, info.thumbnails.first().width)
        assertEquals(1280L, info.thumbnails.last().width)

        // itag 18 progressive plus 137 video-only and 140 audio-only; the
        // signatureCipher and n-challenged formats are dropped and counted.
        assertEquals(3, info.formats.size)
        assertEquals(2, info.formatsNeedingJs)

        val progressive = info.formats.first { it.formatId == "18" }
        assertEquals("avc1.42001E", progressive.vcodec)
        assertEquals("mp4a.40.2", progressive.acodec)
        assertEquals("mp4", progressive.ext)
        assertEquals("https", progressive.protocol)
        assertEquals(360L, progressive.height)
        assertEquals(640L, progressive.width)
        assertEquals(30.0, progressive.fps)
        assertEquals(580.0, progressive.tbr)
        assertEquals(12_345L, progressive.filesize)
        assertEquals(null, progressive.container)
        assertEquals("360p", progressive.formatNote)
        assertEquals(YoutubeIE.HTTP_CHUNK_SIZE, progressive.downloaderOptions?.httpChunkSize)
        assertEquals(false, progressive.hasDrm)

        val videoOnly = info.formats.first { it.formatId == "137" }
        assertEquals("none", videoOnly.acodec)
        assertEquals("mp4_dash", videoOnly.container)
        assertEquals(1080L, videoOnly.height)

        val audioOnly = info.formats.first { it.formatId == "140" }
        assertEquals("none", audioOnly.vcodec)
        assertEquals("m4a_dash", audioOnly.container)
        assertEquals(44_100L, audioOnly.asr)
    }

    @Test
    fun everySupportedUrlFormMatchesAndExtracts() = runTest {
        val urls = listOf(
            "https://www.youtube.com/watch?v=$videoId",
            "https://youtube.com/watch?v=$videoId&t=10s",
            "https://youtu.be/$videoId",
            "https://youtu.be/$videoId?t=30",
            "https://www.youtube.com/shorts/$videoId",
            "https://www.youtube.com/embed/$videoId",
            "https://www.youtube.com/live/$videoId",
            "https://www.youtube-nocookie.com/embed/$videoId",
            "https://music.youtube.com/watch?v=$videoId",
            "https://m.youtube.com/watch?v=$videoId",
        )
        for (url in urls) {
            val info = extract(url)
            assertEquals(videoId, info.id, "URL form failed: $url")
        }
    }

    @Test
    fun unsupportedYoutubeUrlsDoNotMatch() {
        val ie = YoutubeIE(http())
        val unsupported = listOf(
            "https://www.youtube.com/playlist?list=PLfixture",
            "https://www.youtube.com/channel/UCfixture",
            "https://www.youtube.com/@fixture",
            "https://www.youtube.com/results?search_query=fixture",
            "https://www.youtube.com/watch?v=tooshort",
            "https://www.youtube.com/feed/subscriptions",
        )
        for (url in unsupported) {
            assertFalse(ie.suitable(url), "URL must stay unsupported in D4: $url")
        }
        assertTrue(ie.suitable("https://www.youtube.com/watch?v=$videoId"))
    }

    // ------------------------------------------------------------ playability

    @Test
    fun loginRequiredFailsTyped() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            extract(
                "https://www.youtube.com/watch?v=$videoId",
                playerJson(status = "LOGIN_REQUIRED", reason = "Sign in to confirm you are not a bot"),
            )
        }
    }

    @Test
    fun ageGatedFailsTyped() = runTest {
        assertFailsWith<ExtractionError.AgeRestricted> {
            extract(
                "https://www.youtube.com/watch?v=$videoId",
                playerJson(status = "UNPLAYABLE", reason = "Sign in to confirm your age"),
            )
        }
        assertFailsWith<ExtractionError.AgeRestricted> {
            extract(
                "https://www.youtube.com/watch?v=$videoId",
                playerJson(status = "AGE_VERIFICATION_REQUIRED"),
            )
        }
    }

    @Test
    fun privateRemovedAndOfflineVideosFailTyped() = runTest {
        assertFailsWith<ExtractionError.Unavailable> {
            extract(
                "https://www.youtube.com/watch?v=$videoId",
                playerJson(status = "UNPLAYABLE", reason = "Private video"),
            )
        }
        assertFailsWith<ExtractionError.Unavailable> {
            extract(
                "https://www.youtube.com/watch?v=$videoId",
                playerJson(status = "UNPLAYABLE", reason = "This video has been removed"),
            )
        }
        assertFailsWith<ExtractionError.Unavailable> {
            extract(
                "https://www.youtube.com/watch?v=$videoId",
                playerJson(status = "LIVE_STREAM_OFFLINE", reason = "This live event has ended"),
            )
        }
        assertFailsWith<ExtractionError.Unavailable> {
            extract(
                "https://www.youtube.com/watch?v=$videoId",
                playerJson(status = "ERROR", reason = "Something went wrong"),
            )
        }
    }

    @Test
    fun liveStreamsFailTypedInD4() = runTest {
        assertFailsWith<ExtractionError.Unavailable> {
            extract(
                "https://www.youtube.com/live/$videoId",
                playerJson(live = true),
            )
        }
    }

    @Test
    fun onlyCipheredFormatsFailsTypedAndCountsThem() = runTest {
        val error = assertFailsWith<ExtractionError.NoFormats> {
            extract(
                "https://www.youtube.com/watch?v=$videoId",
                playerJson(formats = "", adaptive = cipheredOnlyFormats),
            )
        }
        assertTrue(error.message?.contains("JavaScript runtime") == true, "${error.message}")
    }

    // --------------------------------------------------------------- harness

    @Test
    fun upstreamShapeCasePassesThroughTheHarness() = runTest {
        val case = ExtractorCase(
            url = "https://www.youtube.com/watch?v=$videoId",
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Big Buck Bunny"),
                "channel" to Expect.Value("Fixture Channel"),
                "channel_id" to Expect.Value("UCfixturechannel000000000"),
                "duration" to Expect.FloatType,
                "view_count" to Expect.Value(123_456L),
                "upload_date" to Expect.Value("20080529"),
                "age_limit" to Expect.Value(0),
                "formats" to Expect.MinCount(1),
                "formats.0.format_id" to Expect.Value("18"),
                "formats.0.vcodec" to Expect.Value("avc1.42001E"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://www.youtube.com/youtubei/v1/player*",
                    method = "POST",
                    contentType = "application/json",
                    body = playerJson(),
                ),
            ),
        )
        val result = runCase(case) { http -> YoutubeIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun drcAndSuperResolutionFormatsKeepTheirUpstreamIds() = runTest {
        val drc = """
            {
              "itag": 140,
              "url": "https://cdn.fixtures.example.net/videoplayback?itag=140",
              "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
              "isDrc": true,
              "quality": "tiny",
              "audioQuality": "AUDIO_QUALITY_MEDIUM"
            }
        """.trimIndent()
        val superResolution = """
            {
              "itag": 137,
              "url": "https://cdn.fixtures.example.net/videoplayback?itag=137&xtags=sr%3D1",
              "mimeType": "video/mp4; codecs=\"avc1.640028\"",
              "quality": "hd1080",
              "qualityLabel": "1080p"
            }
        """.trimIndent()
        val info = extract(
            "https://www.youtube.com/watch?v=$videoId",
            playerJson(formats = drc, adaptive = superResolution),
        )
        assertEquals(
            setOf("140-drc", "137-sr"),
            info.formats.mapNotNull { it.formatId }.toSet(),
        )
        assertEquals("tiny, DRC", info.formats.first { it.formatId == "140-drc" }.formatNote)
        assertEquals(
            "1080p, AI-upscaled",
            info.formats.first { it.formatId == "137-sr" }.formatNote,
        )
    }

    @Test
    fun watchPageMetaFillsUploadDateAndAgeLimitWhenThePlayerLacksThem() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://www.youtube.com/watch*",
                    contentType = "text/html",
                    body = """
                        <html><head>
                        <meta itemprop="datePublished" content="2008-05-29T04:24:26-07:00">
                        <meta itemprop="uploadDate" content="2008-05-29T04:24:26-07:00">
                        <meta itemprop="isFamilyFriendly" content="true">
                        </head><body></body></html>
                    """.trimIndent(),
                ),
                FixtureRoute(
                    urlPattern = "https://www.youtube.com/youtubei/v1/player*",
                    method = "POST",
                    contentType = "application/json",
                    body = playerJson(includeMicroformat = false),
                ),
            ),
        )
        val info = YoutubeIE(ExtractorHttp(transfer)).extract("https://www.youtube.com/watch?v=$videoId")
        assertEquals("20080529", info.uploadDate)
        assertEquals(0, info.ageLimit)
    }

    @Test
    fun fixturesContainNoRealMediaHostsOrTokens() {
        val all = playerJson() + progressiveFormat + videoOnlyFormat + audioOnlyFormat +
            cipheredFormat + nChallengedFormat
        assertFalse(all.contains("googlevideo.com"), "no real signed media host in fixtures")
        assertFalse(all.contains("ytimg.com"), "no real thumbnail host in fixtures")
        assertFalse(all.contains("visitorData\":\"Cg"), "no real visitor data in fixtures")
        assertTrue(all.contains("cdn.fixtures.example.net"), "media fixtures use the synthetic host")
        assertTrue(all.contains("i9.ytimg.example"), "thumbnail fixtures use the synthetic host")
    }

    @Test
    fun visitorDataFromTheWatchPageIsSentToThePlayer() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://www.youtube.com/watch*",
                    contentType = "text/html",
                    body = "<html><script>ytcfg.set({\"visitorData\":\"FIXTURE_VISITOR\"});</script></html>",
                ),
                FixtureRoute(
                    urlPattern = "https://www.youtube.com/youtubei/v1/player*",
                    method = "POST",
                    contentType = "application/json",
                    body = playerJson(),
                ),
            ),
        )
        YoutubeIE(ExtractorHttp(transfer)).extract("https://www.youtube.com/watch?v=$videoId")

        val playerRequest = transfer.requests.first { it.method == "POST" }
        assertEquals("FIXTURE_VISITOR", playerRequest.headers["x-goog-visitor-id"])
    }

    // -------------------------------------------------------------- fixtures

    private fun playerJson(
        status: String = "OK",
        reason: String? = null,
        live: Boolean = false,
        formats: String = progressiveFormat,
        adaptive: String = adaptiveFormats,
        includeMicroformat: Boolean = true,
    ): String {
        val reasonField = if (reason != null) ", \"reason\": \"$reason\"" else ""
        val microformat = if (includeMicroformat) {
            """,
              "microformat": {"playerMicroformatRenderer": {
                "publishDate": "2008-05-29",
                "isFamilySafe": true,
                "availableCountries": ["US", "BR"]
              }}
            """
        } else {
            ""
        }
        return """
            {
              "playabilityStatus": {"status": "$status"$reasonField},
              "videoDetails": {
                "videoId": "$videoId",
                "title": "Big Buck Bunny",
                "author": "Fixture Channel",
                "channelId": "UCfixturechannel000000000",
                "lengthSeconds": "596",
                "viewCount": "123456",
                "shortDescription": "A fixture description.",
                "isLiveContent": $live,
                "isLive": $live,
                "thumbnail": {"thumbnails": [
                  {"url": "https://i9.ytimg.example/vi/fixture/hqdefault.jpg", "width": 480, "height": 360},
                  {"url": "https://i9.ytimg.example/vi/fixture/maxresdefault.jpg", "width": 1280, "height": 720},
                  {"url": "https://i9.ytimg.example/vi/fixture/default.jpg", "width": 120, "height": 90}
                ]}
              }$microformat,
              "streamingData": {"formats": [$formats], "adaptiveFormats": [$adaptive]}
            }
        """.trimIndent()
    }

    private val progressiveFormat = """
        {
          "itag": 18,
          "url": "https://cdn.fixtures.example.net/videoplayback?itag=18",
          "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"",
          "bitrate": 600000,
          "averageBitrate": 580000,
          "width": 640,
          "height": 360,
          "fps": 30,
          "quality": "medium",
          "qualityLabel": "360p",
          "contentLength": "12345",
          "audioChannels": 2,
          "audioSampleRate": "44100",
          "audioQuality": "AUDIO_QUALITY_LOW"
        }
    """.trimIndent()

    private val videoOnlyFormat = """
        {
          "itag": 137,
          "url": "https://cdn.fixtures.example.net/videoplayback?itag=137",
          "mimeType": "video/mp4; codecs=\"avc1.640028\"",
          "bitrate": 4500000,
          "width": 1920,
          "height": 1080,
          "fps": 30,
          "quality": "hd1080",
          "qualityLabel": "1080p",
          "contentLength": "100000000"
        }
    """.trimIndent()

    private val audioOnlyFormat = """
        {
          "itag": 140,
          "url": "https://cdn.fixtures.example.net/videoplayback?itag=140",
          "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
          "bitrate": 128000,
          "averageBitrate": 128000,
          "quality": "tiny",
          "audioQuality": "AUDIO_QUALITY_MEDIUM",
          "audioSampleRate": "44100",
          "audioChannels": 2,
          "contentLength": "9000000"
        }
    """.trimIndent()

    private val cipheredFormat = """
        {
          "itag": 135,
          "signatureCipher": "s=REDACTED&sp=sig&url=https%3A%2F%2Fcdn.fixtures.example.net%2Fvideoplayback%3Fitag%3D135",
          "mimeType": "video/mp4; codecs=\"avc1.4d401f\""
        }
    """.trimIndent()

    private val nChallengedFormat = """
        {
          "itag": 136,
          "url": "https://cdn.fixtures.example.net/videoplayback?itag=136&n=REDACTED",
          "mimeType": "video/mp4; codecs=\"avc1.4d401f\""
        }
    """.trimIndent()

    private val adaptiveFormats = "$videoOnlyFormat, $audioOnlyFormat, $cipheredFormat, $nChallengedFormat"

    private val cipheredOnlyFormats = "$cipheredFormat, $nChallengedFormat"

    @Test
    fun aMissingPlayerFixtureFailsWithAClearMessage() = runTest {
        val result = runCase(
            ExtractorCase(
                url = "https://www.youtube.com/watch?v=$videoId",
                infoDict = mapOf("title" to Expect.StringType),
            ),
        ) { http -> YoutubeIE(http) }
        val failed = assertIs<CaseResult.Failed>(result)
        assertNotNull(failed.reason)
        assertTrue(failed.reason.contains("No fixture"), failed.reason)
    }
}
