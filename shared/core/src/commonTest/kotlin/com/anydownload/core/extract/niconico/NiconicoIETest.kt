package com.anydownload.core.extract.niconico

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
 * Fixture cases for the Niconico subset. Every id, host, and media address is
 * synthesized (`*.example`); `fake_value` stands in for the per-video access
 * key, and no cookie, account token, or signed URL appears.
 */
class NiconicoIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val watchUrl = "https://www.nicovideo.jp/watch/sm9"

    private fun watchJson(
        premium: Boolean = false,
        withMedia: Boolean = true,
    ): String = """
        {"meta": {"status": 200}, "data": {
          "video": {
            "id": "sm9",
            "title": "Fixture Niconico Title",
            "description": "<b>Fixture</b> description",
            "duration": 320,
            "registeredAt": "2007-03-05T03:00:00+09:00",
            "count": {"view": 1000, "comment": 10, "like": 20},
            "thumbnail": {
              "url": "https://img.example/thumb_370x370.jpg",
              "largeUrl": "https://img.example/large_1920x1080.jpg"
            }
          },
          "channel": {"name": "Fixture Channel", "id": "4"},
          "genre": {"label": "未設定"},
          "tag": {"items": [{"name": "fixture"}]},
          "payment": {"video": {"isPremium": $premium, "isAdmission": false, "isPpv": false}},
          ${if (withMedia) """
          "media": {"domand": {
            "accessRightKey": "fake_value",
            "videos": [
              {"id": "video-h264-360p", "isAvailable": true},
              {"id": "video-h264-720p", "isAvailable": true}
            ],
            "audios": [{"id": "audio-aac-64k", "isAvailable": true, "bitRate": 64000}]
          }},
          "client": {"watchTrackId": "AAAAAAAAAA_1234567890"},
          "comment": {"nvComment": {"server": "https://nvcomment.example", "params": {},
                                     "threadKey": "fake_value"}},
          """ else ""}
          "unused": null
        }}
    """.trimIndent()

    private val accessRightsRoute = FixtureRoute(
        urlPattern = "https://nvapi.nicovideo.jp/v1/watch/sm9/access-rights/hls*",
        method = "POST",
        contentType = "application/json",
        body = """{"meta": {"status": 200}, "data": {"contentUrl": "https://media.example/hls/master.m3u8"}}""",
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NiconicoIE(http(transfer())) to "https://www.nicovideo.jp/watch/sm9",
            NiconicoIE(http(transfer())) to "https://www.nicovideo.jp/watch/1173108780",
            NiconicoIE(http(transfer())) to "https://embed.nicovideo.jp/watch/sm9",
            NiconicoIE(http(transfer())) to "https://sp.nicovideo.jp/watch/so38016254",
            NiconicoPlaylistIE(http(transfer())) to "https://www.nicovideo.jp/mylist/27411728",
            NiconicoPlaylistIE(http(transfer())) to "https://www.nicovideo.jp/user/805442/mylist/27411728",
            NiconicoPlaylistIE(http(transfer())) to "https://nico.ms/mylist/27411728",
            NiconicoSeriesIE(http(transfer())) to "https://www.nicovideo.jp/series/110226",
            NiconicoSeriesIE(http(transfer())) to "https://nico.ms/series/203559",
            NicovideoSearchURLIE(http(transfer())) to "https://www.nicovideo.jp/search/sm9",
            NicovideoTagURLIE(http(transfer())) to "https://www.nicovideo.jp/tag/fixture",
            NiconicoUserIE(http(transfer())) to "https://www.nicovideo.jp/user/419948",
            NiconicoUserIE(http(transfer())) to "https://www.nicovideo.jp/user/419948/video",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NiconicoIE(http(transfer())).suitable("https://www.nicovideo.jp/my/history"))
        assertFalse(NiconicoUserIE(http(transfer())).suitable("https://www.nicovideo.jp/user/419948/mylist/1"))
        assertFalse(NicovideoSearchURLIE(http(transfer())).suitable("https://www.nicovideo.jp/tag/fixture"))
    }

    // ------------------------------------------------------------- NiconicoIE

    @Test
    fun watchApiMapsMetadataAndHlsFormat() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.nicovideo.jp/api/watch/v3_guest/sm9*",
                contentType = "application/json",
                body = watchJson(),
            ),
            accessRightsRoute,
        )
        val info = NiconicoIE(http(transfer)).extract(watchUrl)

        assertEquals("sm9", info.id)
        assertEquals("Fixture Niconico Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(320.0, info.duration)
        assertEquals("20070305", info.uploadDate)
        assertEquals(1000L, info.viewCount)
        assertEquals("Fixture Channel", info.channel)
        assertEquals("4", info.channelId)
        assertEquals("public", info.availability)
        assertEquals(2, info.thumbnails.size)
        assertEquals(370L, info.thumbnails[0].width)
        assertEquals(370L, info.thumbnails[0].height)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/hls/master.m3u8", info.formats.single().url)
        assertTrue(transfer.requests.any { it.url.contains("/access-rights/hls?actionTrackId=AAAAAAAAAA_") })
    }

    @Test
    fun premiumOnlyVideoFailsAsALoginWall() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.nicovideo.jp/api/watch/v3_guest/sm9*",
                contentType = "application/json",
                body = watchJson(premium = true, withMedia = false),
            ),
        )
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            NiconicoIE(http(transfer)).extract(watchUrl)
        }
        assertTrue(error.message!!.contains("Premium"))
    }

    @Test
    fun watchApiErrorStatusFailsTypedThroughTheSeam() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.nicovideo.jp/api/watch/v3_guest/sm9*",
                statusCode = 404,
                contentType = "application/json",
                body = """{"meta": {"status": 404, "errorCode": "NOT_FOUND"}}""",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            NiconicoIE(http(transfer)).extract(watchUrl)
        }
    }

    // -------------------------------------------------------------- listings

    @Test
    fun playlistApiMapsOnePageOfItems() = runTest {
        val listId = "27411728"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://nvapi.nicovideo.jp/v2/mylists/$listId?page=1&pageSize=*",
                contentType = "application/json",
                body = """
                    {"data": {"mylist": {
                      "name": "Fixture Mylist",
                      "description": "Fixture description",
                      "items": [
                        {"id": "sm9", "title": "Episode 1", "duration": 320,
                         "thumbnail": {"largeUrl": "https://img.example/ep1.jpg"},
                         "owner": {"id": "4", "name": "Fixture Channel"}}
                      ]
                    }}}
                """.trimIndent(),
            ),
        )
        val info = NiconicoPlaylistIE(http(transfer)).extract("https://www.nicovideo.jp/mylist/$listId")

        assertEquals(listId, info.id)
        assertEquals("Fixture Mylist", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(1, info.entries.size)
        assertEquals("sm9", info.entries[0].id)
        assertEquals("Episode 1", info.entries[0].title)
        assertEquals("https://www.nicovideo.jp/watch/sm9", info.entries[0].url)
    }

    @Test
    fun seriesApiMapsTheDetailAndItems() = runTest {
        val listId = "110226"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://nvapi.nicovideo.jp/v2/series/$listId?page=1&pageSize=*",
                contentType = "application/json",
                body = """
                    {"data": {
                      "detail": {"title": "Fixture Series", "description": "Fixture series description"},
                      "items": [{"id": "sm9", "title": "Episode 1"}]
                    }}
                """.trimIndent(),
            ),
        )
        val info = NiconicoSeriesIE(http(transfer)).extract("https://www.nicovideo.jp/series/$listId")

        assertEquals(listId, info.id)
        assertEquals("Fixture Series", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.nicovideo.jp/watch/sm9", info.entries[0].url)
    }

    @Test
    fun searchPageScansVideoIdsUntilAnEmptyPage() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.nicovideo.jp/search/sm9?page=1",
                contentType = "text/html",
                body = """<html><body><div data-video-id="sm9">a</div><div data-video-id='sm8628149'>b</div></body></html>""",
            ),
            FixtureRoute(
                urlPattern = "https://www.nicovideo.jp/search/sm9?page=2",
                contentType = "text/html",
                body = "<html><body>no results</body></html>",
            ),
        )
        val info = NicovideoSearchURLIE(http(transfer)).extract("https://www.nicovideo.jp/search/sm9")

        assertEquals("sm9", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.nicovideo.jp/watch/sm8628149", info.entries[1].url)
    }

    @Test
    fun tagPageScansVideoIds() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.nicovideo.jp/tag/fixture?page=1",
                contentType = "text/html",
                body = """<html><body><div data-video-id="sm9">a</div></body></html>""",
            ),
            FixtureRoute(
                urlPattern = "https://www.nicovideo.jp/tag/fixture?page=2",
                contentType = "text/html",
                body = "<html><body></body></html>",
            ),
        )
        val info = NicovideoTagURLIE(http(transfer)).extract("https://www.nicovideo.jp/tag/fixture")

        assertEquals("fixture", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("sm9", info.entries[0].id)
    }

    @Test
    fun userApiPagesThroughTheVideoList() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://nvapi.nicovideo.jp/v2/users/419948/videos" +
                    "?sortKey=registeredAt&sortOrder=desc&pageSize=100&page=1",
                contentType = "application/json",
                body = """
                    {"data": {"totalCount": 1, "items": [{"essential": {"id": "sm9"}}]}}
                """.trimIndent(),
            ),
        )
        val info = NiconicoUserIE(http(transfer)).extract("https://www.nicovideo.jp/user/419948")

        assertEquals("419948", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.nicovideo.jp/watch/sm9", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun niconicoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = watchUrl,
            infoDict = mapOf(
                "id" to Expect.Value("sm9"),
                "title" to Expect.Value("Fixture Niconico Title"),
                "duration" to Expect.Value(320.0),
                "availability" to Expect.Value("public"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://www.nicovideo.jp/api/watch/v3_guest/sm9*",
                    contentType = "application/json",
                    body = watchJson(),
                ),
                accessRightsRoute,
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NiconicoIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
