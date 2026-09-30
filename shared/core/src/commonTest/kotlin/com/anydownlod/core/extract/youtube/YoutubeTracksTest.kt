package com.anydownlod.core.extract.youtube

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T-124: subtitle tracks and chapter metadata sit on the info dict, and a
 * configured PO-token provider feeds `serviceIntegrityDimensions`. Every URL
 * and token is synthetic.
 */
class YoutubeTracksTest {

    private val videoId = "YE7VzlLtp-4"

    private fun playerJson() = """
        {
          "playabilityStatus": {"status": "OK"},
          "videoDetails": {
            "videoId": "$videoId", "title": "Big Buck Bunny", "author": "Fixture Channel",
            "channelId": "UCfixturechannel000000000", "lengthSeconds": "596", "viewCount": "1",
            "isLiveContent": false
          },
          "captions": {
            "playerCaptionsTracklistRenderer": {
              "captionTracks": [
                {
                  "baseUrl": "https://www.youtube.com/api/timedtext?v=fixture&lang=en",
                  "name": {"simpleText": "English"},
                  "languageCode": "en",
                  "kind": "asr"
                },
                {
                  "baseUrl": "https://www.youtube.com/api/timedtext?v=fixture&lang=pt&exp=xpe",
                  "name": {"runs": [{"text": "Portug"}]},
                  "languageCode": "pt"
                }
              ]
            }
          },
          "playerOverlays": {
            "playerOverlayRenderer": {
              "decoratedPlayerBarRenderer": {
                "decoratedPlayerBarRenderer": {
                  "playerBar": {
                    "chapteredPlayerBarRenderer": {
                      "chapters": [
                        {"chapterRenderer": {"timeRangeStartMillis": 30000, "title": {"simpleText": "Two"}}},
                        {"chapterRenderer": {"timeRangeStartMillis": 0, "title": {"simpleText": "One"}}}
                      ]
                    }
                  }
                }
              }
            }
          },
          "streamingData": {
            "formats": [
              {"itag": 18, "url": "https://cdn.fixtures.example.net/plain.mp4",
               "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "height": 360}
            ],
            "adaptiveFormats": []
          }
        }
    """.trimIndent()

    private fun ie(): YoutubeIE = YoutubeIE(
        ExtractorHttp(
            FixtureHttpTransfer(
                listOf(
                    FixtureRoute(
                        urlPattern = "https://www.youtube.com/youtubei/v1/player*",
                        method = "POST",
                        contentType = "application/json",
                        body = playerJson(),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun manualAndAutomaticTracksAndChaptersSitOnTheInfoDict() = runTest {
        val info = ie().extract("https://www.youtube.com/watch?v=$videoId")

        assertEquals(1, info.subtitles.size)
        val manual = info.subtitles.single()
        assertEquals("pt", manual.language)
        assertEquals("Portug", manual.name)
        assertFalse(manual.automatic)
        assertTrue(manual.needsPoToken, "the fixture's exp=xpe marks the track")
        assertEquals(YoutubeIE.SUBTITLE_FORMATS, manual.formats.map { it.ext })
        assertTrue(manual.formats.all { it.url.contains("fmt=") && it.url.contains("lang=pt") })

        val automatic = info.automaticCaptions.single()
        assertEquals("en", automatic.language)
        assertEquals("English", automatic.name)
        assertTrue(automatic.automatic)
        assertFalse(automatic.needsPoToken)

        assertEquals(2, info.chapters.size)
        assertEquals("One", info.chapters[0].title)
        assertEquals(0.0, info.chapters[0].startTime)
        assertEquals(30.0, info.chapters[0].endTime)
        assertEquals("Two", info.chapters[1].title)
        assertEquals(30.0, info.chapters[1].startTime)
        assertEquals(596.0, info.chapters[1].endTime)
    }

    private fun livePlayerJson() = """
        {
          "playabilityStatus": {"status": "OK"},
          "videoDetails": {
            "videoId": "$videoId", "title": "Live Fixture", "author": "Fixture Channel",
            "channelId": "UCfixturechannel000000000", "lengthSeconds": "0", "viewCount": "1",
            "isLiveContent": true, "isLive": true
          },
          "streamingData": {
            "hlsManifestUrl": "https://manifest.example/live.m3u8",
            "formats": [],
            "adaptiveFormats": []
          }
        }
    """.trimIndent()

    @Test
    fun aLiveVideoExposesTheHlsManifestAndIsLive() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://www.youtube.com/youtubei/v1/player*",
                    method = "POST",
                    contentType = "application/json",
                    body = livePlayerJson(),
                ),
            ),
        )

        val info = YoutubeIE(ExtractorHttp(transfer)).extract("https://www.youtube.com/watch?v=$videoId")

        assertEquals(true, info.isLive)
        val format = info.formats.single()
        assertEquals("https://manifest.example/live.m3u8", format.url)
        assertEquals("m3u8_native", format.protocol)
        assertEquals("live HLS", format.formatNote)
    }

    @Test
    fun aWasLiveVideoKeepsItsRecordedFormats() = runTest {
        val wasLive = playerJson().replace(
            "\"isLiveContent\": false",
            "\"isLiveContent\": true, \"isLive\": false",
        )
        val transfer = FixtureHttpTransfer(
            listOf(
                FixtureRoute(
                    urlPattern = "https://www.youtube.com/youtubei/v1/player*",
                    method = "POST",
                    contentType = "application/json",
                    body = wasLive,
                ),
            ),
        )

        val info = YoutubeIE(ExtractorHttp(transfer)).extract("https://www.youtube.com/watch?v=$videoId")

        assertEquals(false, info.isLive)
        assertEquals(listOf("18"), info.formats.mapNotNull { it.formatId })
    }

    @Test
    fun aPlayerProviderTokenRidesTheServiceIntegrityDimensions() = runTest {
        val requests = mutableListOf<HttpRequest>()
        val transfer = object : HttpTransfer {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                requests += request
                val bytes = playerJson().encodeToByteArray()
                return HttpResponse.Final(200, "application/json", bytes.size.toLong(), ByteArrayHttpBody(bytes))
            }
        }
        val provider = PoTokenProvider { request ->
            if (request.context == PoTokenContext.PLAYER) "FIXTURE_PLAYER_TOKEN" else null
        }

        YoutubeIE(ExtractorHttp(transfer), poTokenProvider = provider)
            .extract("https://www.youtube.com/watch?v=$videoId")

        val playerRequest = requests.first { it.method == "POST" }
        val body = playerRequest.body!!.decodeToString()
        assertTrue(body.contains("serviceIntegrityDimensions"))
        assertTrue(body.contains("FIXTURE_PLAYER_TOKEN"))
    }
}
