package com.anydownlod.core.extract.twitter

import com.anydownlod.core.FormatChoices
import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixture cases for the public X/Twitter status extractor. Every id, handle,
 * text, and media address here is synthesized: the media hosts are
 * `*.example`, and no token, cookie, real status, or `twimg.com` URL appears.
 */
class TwitterIETest {

    private val statusId = "1111111111111111111"

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private fun statusRoute(json: String, id: String = statusId): FixtureRoute =
        FixtureRoute(
            urlPattern = "https://cdn.syndication.twimg.com/tweet-result?id=$id*",
            contentType = "application/json",
            body = json,
        )

    private suspend fun extract(json: String, id: String = statusId): InfoDict {
        val transfer = transfer(statusRoute(json, id))
        return TwitterIE(http(transfer)).extract(statusUrl(id))
    }

    private fun statusUrl(id: String, host: String = "x.com"): String = "https://$host/fixture/status/$id"

    // ----------------------------------------------------------- URL matching

    @Test
    fun theFourHostFormsAndIwebMatch() {
        val ie = TwitterIE(http(transfer()))
        val urls = listOf(
            "https://x.com/fixture/status/$statusId",
            "https://twitter.com/fixture/status/$statusId",
            "https://mobile.x.com/fixture/status/$statusId",
            "https://mobile.twitter.com/fixture/status/$statusId",
            "https://www.x.com/fixture/status/$statusId",
            "https://m.twitter.com/fixture/status/$statusId",
            "https://x.com/i/web/status/$statusId",
            "https://twitter.com/statuses/$statusId",
        )
        for (url in urls) {
            assertTrue(ie.suitable(url), "URL must match: $url")
            assertEquals(statusId, ie.matchId(url), "match id failed: $url")
        }
    }

    @Test
    fun nonStatusUrlsDoNotMatch() {
        val ie = TwitterIE(http(transfer()))
        val unsupported = listOf(
            "https://x.com/fixture",
            "https://twitter.com/fixture/status/not-a-number",
            "https://x.com/i/spaces/1YqKDgLqOKqJV",
            "https://x.com/i/broadcasts/1dRJZpjLAaMGB",
            "https://t.co/abcdef",
            "https://x.com/fixture/status/$statusId/photo/1",
        )
        for (url in unsupported) {
            assertFalse(ie.suitable(url), "URL must stay unsupported in D7: $url")
        }
    }

    // ------------------------------------------------------------- happy path

    @Test
    fun singleVideoStatusMapsMetadataAndMedia() = runTest {
        val info = extract(SINGLE_VIDEO)

        assertEquals(statusId, info.id)
        assertEquals("Fixture Poster - Synthetic fixture post with one video", info.title)
        assertEquals("Synthetic fixture post with one video", info.description)
        assertEquals("Fixture Poster", info.uploader)
        assertEquals("Fixture Poster", info.channel)
        assertEquals("2222222222222222222", info.channelId)
        assertEquals("20260901", info.uploadDate)
        assertEquals(456L, info.viewCount)
        assertEquals(0, info.ageLimit)
        assertEquals(statusUrl(statusId), info.webpageUrl)
        assertEquals(TwitterIE.IE_KEY, info.extractorKey)
        assertEquals("twitter", info.extractor)
        assertTrue(info.formats.isEmpty())

        assertEquals(1, info.media.size)
        val media = info.media.single()
        assertEquals(statusId, media.mediaId)
        assertEquals(12.922, media.duration)
        assertEquals(3, media.formats.size)

        val direct = media.formats.first { it.formatId == "http-256" }
        assertEquals("https", direct.protocol)
        assertEquals("mp4", direct.ext)
        assertEquals(320L, direct.width)
        assertEquals(180L, direct.height)
        assertEquals(256.0, direct.tbr)

        val hls = media.formats.first { it.protocol == "m3u8_native" }
        assertTrue(hls.url!!.endsWith(".m3u8"))
        assertEquals("mp4", hls.ext)

        assertTrue(info.thumbnails.isNotEmpty())
        assertTrue(info.thumbnails.all { it.url.startsWith("https://pbs.example/media/single.jpg?name=") })
        assertEquals("orig", info.thumbnails.last().id)
    }

    @Test
    fun twoVideosWithPhotoAndQuoteGroupByStableMediaId() = runTest {
        val info = extract(TWO_VIDEOS, id = TWO_VIDEOS_STATUS_ID)

        assertEquals(2, info.media.size)
        assertEquals(
            listOf("3333333333333333333", "4444444444444444444"),
            info.media.map { it.mediaId },
        )
        assertTrue(info.media.all { media -> media.formats.isNotEmpty() })
        assertTrue(info.media.none { media -> media.mediaId == "6666666666666666666" })
        assertEquals("Fixture Poster - Two videos and a photo #1", info.media[0].title)
        assertEquals("Fixture Poster - Two videos and a photo #2", info.media[1].title)
    }

    @Test
    fun formatChoicesUnionTheListedMedia() = runTest {
        val info = extract(SINGLE_VIDEO)
        val choices = FormatChoices.from(info)
        assertEquals(listOf(360, 180), choices.videoHeights)
        assertTrue(choices.hasSingleFileVideo)
        assertTrue(choices.audioContainers.isEmpty())
    }

    // ------------------------------------------------------- typed failures

    @Test
    fun photoOnlyStatusFailsNoFormats() = runTest {
        assertFailsWith<ExtractionError.NoFormats> { extract(PHOTO_ONLY, id = "7777777777777777777") }
    }

    @Test
    fun protectedPayloadFailsLoginRequired() = runTest {
        assertFailsWith<ExtractionError.LoginRequired> {
            extract("""{"__typename":"TweetUnavailable","reason":"Protected"}""", id = "8888888888888888888")
        }
    }

    @Test
    fun deletedStatusFailsUnavailable() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://cdn.syndication.twimg.com/tweet-result?id=9999999999999999999*",
                statusCode = 404,
                body = "",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            TwitterIE(http(transfer)).extract(statusUrl("9999999999999999999"))
        }
    }

    // ---------------------------------------------------- request discipline

    @Test
    fun extractionOnlyRequestsTheGuestLookup() = runTest {
        val transfer = transfer(statusRoute(SINGLE_VIDEO))
        TwitterIE(http(transfer)).extract(statusUrl(statusId))

        assertEquals(1, transfer.requests.size)
        val request = transfer.requests.single()
        assertEquals("GET", request.method)
        assertTrue(request.url.startsWith("https://cdn.syndication.twimg.com/tweet-result?id=$statusId&token="))
        assertNull(request.authorization)
        assertFalse(request.headers.keys.any { it.equals("cookie", true) })
        assertEquals("Googlebot", request.headers["user-agent"])

        val token = request.url.substringAfter("token=")
        assertTrue(token.isNotEmpty())
        assertFalse(token.contains('0'))
        assertFalse(token.contains('.'))
        assertTrue(token.all { it in "123456789abcdefghijklmnopqrstuvwxyz" })
    }

    @Test
    fun syndicationTokenIsDerivedDeterministically() {
        val first = TwitterIE.syndicationToken("1234567890123456789")
        assertNotNull(first)
        assertEquals(first, TwitterIE.syndicationToken("1234567890123456789"))
        assertFalse(first!!.contains('0'))
        assertFalse(first.contains('.'))
        assertTrue(first.all { it in "123456789abcdefghijklmnopqrstuvwxyz" })
        assertFalse(first == TwitterIE.syndicationToken("2234567890123456789"))
        assertNull(TwitterIE.syndicationToken("not-a-number"))
    }

    // -------------------------------------------------------------- fixtures

    private companion object {
        val SINGLE_VIDEO = """
            {
              "__typename": "Tweet",
              "id_str": "1111111111111111111",
              "text": "Synthetic fixture post with one video",
              "created_at": "2026-09-01T12:00:00.000Z",
              "possibly_sensitive": false,
              "view_count": 456,
              "favorite_count": 12,
              "retweet_count": 3,
              "reply_count": 1,
              "user": {
                "id_str": "2222222222222222222",
                "name": "Fixture Poster",
                "screen_name": "fixture_poster",
                "protected": false
              },
              "mediaDetails": [
                {
                  "type": "video",
                  "media_url_https": "https://pbs.example/media/single.jpg",
                  "sizes": {
                    "small": {"w": 680, "h": 383},
                    "orig": {"w": 1280, "h": 720}
                  },
                  "original_info": {"width": 1280, "height": 720},
                  "video_info": {
                    "duration_millis": 12922,
                    "variants": [
                      {
                        "bitrate": 256000,
                        "content_type": "video/mp4",
                        "url": "https://video.example/ext_tw_video/1111111111111111111/pu/vid/320x180/aaa.mp4"
                      },
                      {
                        "bitrate": 832000,
                        "content_type": "video/mp4",
                        "url": "https://video.example/ext_tw_video/1111111111111111111/pu/vid/640x360/bbb.mp4"
                      },
                      {
                        "content_type": "application/x-mpegURL",
                        "url": "https://video.example/ext_tw_video/1111111111111111111/pu/pl/ccc.m3u8"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        const val TWO_VIDEOS_STATUS_ID = "9999999999999999999"

        val TWO_VIDEOS = """
            {
              "__typename": "Tweet",
              "id_str": "9999999999999999999",
              "text": "Two videos and a photo",
              "created_at": "2026-09-02T10:00:00.000Z",
              "possibly_sensitive": true,
              "view_count": 1000,
              "user": {
                "id_str": "2222222222222222222",
                "name": "Fixture Poster",
                "screen_name": "fixture_poster",
                "protected": false
              },
              "mediaDetails": [
                {
                  "type": "video",
                  "media_url_https": "https://pbs.example/media/first.jpg",
                  "sizes": {"small": {"w": 680, "h": 383}},
                  "video_info": {
                    "duration_millis": 5000,
                    "variants": [
                      {
                        "bitrate": 832000,
                        "content_type": "video/mp4",
                        "url": "https://video.example/ext_tw_video/3333333333333333333/pu/vid/640x360/first.mp4"
                      }
                    ]
                  }
                },
                {
                  "type": "photo",
                  "media_url_https": "https://pbs.example/media/photo.jpg"
                },
                {
                  "type": "video",
                  "media_url_https": "https://pbs.example/media/second.jpg",
                  "sizes": {"small": {"w": 680, "h": 383}},
                  "video_info": {
                    "duration_millis": 7000,
                    "variants": [
                      {
                        "bitrate": 256000,
                        "content_type": "video/mp4",
                        "url": "https://video.example/amplify_video/4444444444444444444/pu/vid/320x180/second.mp4"
                      }
                    ]
                  }
                }
              ],
              "quoted_tweet": {
                "id_str": "5555555555555555555",
                "mediaDetails": [
                  {
                    "type": "video",
                    "video_info": {
                      "variants": [
                        {
                          "bitrate": 832000,
                          "content_type": "video/mp4",
                          "url": "https://video.example/ext_tw_video/6666666666666666666/pu/vid/640x360/quoted.mp4"
                        }
                      ]
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val PHOTO_ONLY = """
            {
              "__typename": "Tweet",
              "id_str": "7777777777777777777",
              "text": "Photo only",
              "created_at": "2026-09-03T09:00:00.000Z",
              "user": {
                "id_str": "2222222222222222222",
                "name": "Fixture Poster",
                "screen_name": "fixture_poster",
                "protected": false
              },
              "mediaDetails": [
                {
                  "type": "photo",
                  "media_url_https": "https://pbs.example/media/photo.jpg"
                }
              ]
            }
        """.trimIndent()
    }
}
