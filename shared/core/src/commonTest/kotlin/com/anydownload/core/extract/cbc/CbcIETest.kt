package com.anydownload.core.extract.cbc

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
 * Fixture cases for the CBC subset. Every id, host, and media address is
 * synthesized (`*.example`); no cookie, account token, or signed URL appears.
 */
class CbcIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            CBCIE(http(transfer())) to "https://www.cbc.ca/22minutes/videos/clips-season-23/don-cherry-play-offs",
            CBCPlayerIE(http(transfer())) to "https://www.cbc.ca/player/play/2683190193",
            CBCPlayerIE(http(transfer())) to "cbcplayer:1.7159484",
            CBCPlayerIE(http(transfer())) to "http://www.cbc.ca/i/caffeine/syndicate/?mediaId=2657631896",
            CBCPlayerPlaylistIE(http(transfer())) to "https://www.cbc.ca/player/news/Canada/North",
            CBCGemIE(http(transfer())) to "https://gem.cbc.ca/media/schitts-creek/s06e01",
            CBCGemIE(http(transfer())) to "https://gem.cbc.ca/nadiyas-family-favourites/s01e01",
            CBCGemPlaylistIE(http(transfer())) to "https://gem.cbc.ca/media/schitts-creek/s06",
            CBCGemContentIE(http(transfer())) to "https://gem.cbc.ca/the-tunnel",
            CBCGemOlympicsIE(http(transfer())) to
                "https://gem.cbc.ca/ski-jumping-nh-individual-womens-final-30086/s01e30086",
            CBCGemLiveIE(http(transfer())) to "https://gem.cbc.ca/live/44",
            CBCGemLiveIE(http(transfer())) to "https://gem.cbc.ca/live-event/10835",
            CBCListenIE(http(transfer())) to
                "https://www.cbc.ca/listen/cbc-podcasts/1353-the-naked-emperor/episode/16142603-introducing",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(CBCIE(http(transfer())).suitable("https://www.cbc.ca/player/play/2683190193"))
        assertFalse(CBCIE(http(transfer())).suitable("https://www.cbc.ca/listen/live-radio/1-64/clip/16170773-x"))
        assertFalse(CBCPlayerPlaylistIE(http(transfer())).suitable("https://www.cbc.ca/player/play/1"))
        assertFalse(CBCGemLiveIE(http(transfer())).suitable("https://gem.cbc.ca/live/not-a-number"))
    }

    // ------------------------------------------------------------------ CBCIE

    @Test
    fun cbcPageCollectsMediaIdsAsPlaylistEntries() = runTest {
        val url = "https://www.cbc.ca/22minutes/videos/clips-season-23/don-cherry-play-offs"
        val page = """
            <html><head>
            <meta property="og:title" content="Fixture CBC Page">
            <meta property="og:description" content="Fixture description">
            <script>window.__INITIAL_STATE__ = {"detail":{"content":{"body":[{"content":[
              {"type": "polopoly_media", "content": {"sourceId": "2682904050"}}]}]}},
              "app": {"contentId": "2680832926"}};</script>
            </head><body>
            <iframe src="https://www.cbc.ca/player/play?mediaId=2683190193"></iframe>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = CBCIE(http(transfer)).extract(url)

        assertEquals("don-cherry-play-offs", info.id)
        assertEquals("Fixture CBC Page", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(3, info.entries.size)
        assertTrue(info.entries.any { it.url == "https://www.cbc.ca/player/play/2682904050" })
        assertTrue(info.entries.any { it.url == "https://www.cbc.ca/player/play/2680832926" })
        assertTrue(info.entries.any { it.url == "https://www.cbc.ca/player/play/2683190193" })
    }

    // ------------------------------------------------------------ CBCPlayerIE

    private val playerUrl = "https://www.cbc.ca/player/play/1.7194274"

    private fun playerPage(assets: String = """[{"key": "https://asset.example/medianet.json", "type": "medianet"}]"""): String = """
        <html><body><script>
        window.__INITIAL_STATE__ = {"video": {"currentClip": {
          "title": "Fixture Player Title",
          "description": "Fixture player description",
          "publishedAt": 1714788791000,
          "image": {"url": "https://img.example/player.jpg?width=640"},
          "mediaId": "2683190193",
          "media": {
            "streamType": "VOD",
            "duration": 77.678,
            "clipType": "Excerpt",
            "region": "Canada",
            "assets": $assets,
            "textTracks": [
              {"src": "https://media.example/captions.vtt", "language": "en-US", "label": "English"}
            ],
            "chapters": [
              {"startTime": 0, "endTime": 5000, "name": "Chapter 1"},
              {"startTime": 5000, "endTime": 10000, "name": "Chapter 2"}
            ]
          }
        }}};
        </script></body></html>
    """.trimIndent()

    @Test
    fun cbcPlayerMapsMedianetAssetsAndChapters() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = playerUrl, contentType = "text/html", body = playerPage()),
            FixtureRoute(
                urlPattern = "https://asset.example/medianet.json",
                contentType = "application/json",
                body = """
                    {"url": "https://media.example/hls/master.m3u8", "params": [
                      {"name": "contentType", "value": "application/x-mpegURL"},
                      {"name": "mediaType", "value": "video"}
                    ]}
                """.trimIndent(),
            ),
        )
        val info = CBCPlayerIE(http(transfer)).extract(playerUrl)

        assertEquals("1.7194274", info.id)
        assertEquals("Fixture Player Title", info.title)
        assertEquals("Fixture player description", info.description)
        assertEquals(77.678, info.duration)
        assertEquals("20240504", info.uploadDate)
        assertEquals("https://img.example/player.jpg", info.thumbnails.single().url)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals(1, info.subtitles.size)
        assertEquals("en-US", info.subtitles.single().language)
        assertEquals(2, info.chapters.size)
        assertEquals("Chapter 1", info.chapters[0].title)
    }

    @Test
    fun cbcPlayerDeprecatedThePlatformPathFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = playerUrl, contentType = "text/html", body = playerPage(assets = "[]")),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            CBCPlayerIE(http(transfer)).extract(playerUrl)
        }
        assertTrue(error.message!!.contains("ThePlatform"))
    }

    @Test
    fun cbcPlayerPlaylistReadsClipsByCategory() = runTest {
        val url = "https://www.cbc.ca/player/news/Canada/North"
        val page = """
            <html><body><script>
            window.__INITIAL_STATE__ = {"video": {"clipsByCategory": {"news/canada/north": {"items": [
              {"id": "1.1111111"}, {"id": "1.2222222"}
            ]}}}};
            </script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = CBCPlayerPlaylistIE(http(transfer)).extract(url)

        assertEquals("news/canada/north", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.cbc.ca/player/play/1.1111111", info.entries[0].url)
    }

    // -------------------------------------------------------------- CBC Gem

    private fun gemShowJson(
        title: String = "Fixture Show",
        itemTitle: String = "6. Smoke Signals",
        itemId: String = "schitts-creek/s06e01",
        mediaId: String = "media-123",
        seasonNumber: Int = 6,
    ): String = """
        {"title": "$title",
         "structuredMetadata": {"partofSeason": {"seasonNumber": $seasonNumber}, "genre": ["Comedy"]},
         "content": [{"lineups": [{"seasonNumber": $seasonNumber, "items": [{
           "url": "$itemId",
           "idMedia": "$mediaId",
           "episodeNumber": 1,
           "title": "$itemTitle",
           "description": "Fixture episode description",
           "images": {"card": {"url": "https://img.example/card.jpg?width=600"}},
           "metadata": {"duration": 1324, "airDate": "2020-01-07T00:00:00Z",
                         "availabilityDate": "2021-06-18T00:00:00Z", "rating": "C14"}
         }]}]}]}
    """.trimIndent()

    @Test
    fun cbcGemMapsShowAndMediaApis() = runTest {
        val url = "https://gem.cbc.ca/media/schitts-creek/s06e01"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://services.radio-canada.ca/ott/catalog/v2/gem/show/schitts-creek/s06e01?device=web",
                contentType = "application/json",
                body = gemShowJson(),
            ),
            FixtureRoute(
                urlPattern = "https://services.radio-canada.ca/media/validation/v2/?appCode=gem*idMedia=media-123",
                contentType = "application/json",
                body = """{"errorCode": 0, "url": "https://media.example/gem/master.m3u8"}""",
            ),
        )
        val info = CBCGemIE(http(transfer)).extract(url)

        assertEquals("schitts-creek/s06e01", info.id)
        assertEquals("Smoke Signals", info.title)
        assertEquals("Fixture episode description", info.description)
        assertEquals(1324.0, info.duration)
        assertEquals("20210618", info.uploadDate)
        assertEquals(14, info.ageLimit)
        assertEquals("https://img.example/card.jpg", info.thumbnails.single().url)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertTrue(info.formats.single().url!!.contains("manifestType="))
    }

    @Test
    fun cbcGemMediaValidationErrorsFailTyped() = runTest {
        val url = "https://gem.cbc.ca/media/schitts-creek/s06e01"
        fun routes(body: String): Array<FixtureRoute> = arrayOf(
            FixtureRoute(
                urlPattern = "https://services.radio-canada.ca/ott/catalog/v2/gem/show/schitts-creek/s06e01?device=web",
                contentType = "application/json",
                body = gemShowJson(),
            ),
            FixtureRoute(
                urlPattern = "https://services.radio-canada.ca/media/validation/v2/?appCode=gem*idMedia=media-123",
                contentType = "application/json",
                body = body,
            ),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            CBCGemIE(http(transfer(*routes("""{"errorCode": 1}""")))).extract(url)
        }
        assertFailsWith<ExtractionError.LoginRequired> {
            CBCGemIE(http(transfer(*routes("""{"errorCode": 35}""")))).extract(url)
        }
        val error = assertFailsWith<ExtractionError.Unavailable> {
            CBCGemIE(http(transfer(*routes("""{"errorCode": 9, "message": "Fixture refusal"}""")))).extract(url)
        }
        assertTrue(error.message!!.contains("Fixture refusal"))
    }

    @Test
    fun cbcGemPlaylistListsTheSeasonItems() = runTest {
        val url = "https://gem.cbc.ca/media/schitts-creek/s06"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://services.radio-canada.ca/ott/catalog/v2/gem/show/schitts-creek?device=web",
                contentType = "application/json",
                body = gemShowJson(),
            ),
        )
        val info = CBCGemPlaylistIE(http(transfer)).extract(url)

        assertEquals("schitts-creek/s06", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://gem.cbc.ca/media/schitts-creek/s06e01", info.entries[0].url)
        assertEquals("Smoke Signals", info.entries[0].title)
    }

    @Test
    fun cbcGemContentRoutesSeriesAndStandalone() = runTest {
        val seriesUrl = "https://gem.cbc.ca/the-tunnel"
        val seriesPage = """
            <html><head><script id="__NEXT_DATA__" type="application/json">
            {"props": {"pageProps": {"data": {"contentType": "Season", "content": [
              {"lineups": [{"url": "https://gem.cbc.ca/the-tunnel/s01"}]}
            ]}}}}
            </script></head></html>
        """.trimIndent()
        val seriesInfo = CBCGemContentIE(
            http(transfer(FixtureRoute(urlPattern = seriesUrl, contentType = "text/html", body = seriesPage))),
        ).extract(seriesUrl)
        assertEquals("the-tunnel", seriesInfo.id)
        assertEquals("https://gem.cbc.ca/the-tunnel/s01", seriesInfo.entries.single().url)

        val standaloneUrl = "https://gem.cbc.ca/fixture-event-30086"
        val standalonePage = """
            <html><head><script id="__NEXT_DATA__" type="application/json">
            {"props": {"pageProps": {"data": {"contentType": "Standalone",
              "header": {"cta": {"media": {"url": "https://gem.cbc.ca/fixture-event-30086/s01e30086"}}}}}}}
            </script></head></html>
        """.trimIndent()
        val standaloneInfo = CBCGemContentIE(
            http(transfer(FixtureRoute(urlPattern = standaloneUrl, contentType = "text/html", body = standalonePage))),
        ).extract(standaloneUrl)
        assertEquals("https://gem.cbc.ca/fixture-event-30086/s01e30086", standaloneInfo.redirectUrl)
    }

    @Test
    fun cbcGemOlympicsReadsTheReplay() = runTest {
        val url = "https://gem.cbc.ca/ski-jumping-nh-individual-womens-final-30086/s01e30086"
        val show = """
            {"title": "Fixture Show", "content": [{"lineups": [{"seasonNumber": 1, "items": [{
              "formattedIdMedia": "30086", "type": "Replay",
              "title": "Ski Jumping: Final", "description": "Fixture description",
              "images": {"card": {"url": "https://img.example/olympics.jpg"}},
              "metadata": {"replay": {"airDate": "2026-02-07T12:00:00Z", "duration": 12793}}
            }]}]}]}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://services.radio-canada.ca/ott/catalog/v2/gem/show/" +
                    "ski-jumping-nh-individual-womens-final-30086?device=web",
                contentType = "application/json",
                body = show,
            ),
            FixtureRoute(
                urlPattern = "https://services.radio-canada.ca/media/validation/v2/" +
                    "?appCode=medianetlive*idMedia=30086",
                contentType = "application/json",
                body = """{"errorCode": 0, "url": "https://media.example/olympics/master.m3u8"}""",
            ),
        )
        val info = CBCGemOlympicsIE(http(transfer)).extract(url)

        assertEquals("ski-jumping-nh-individual-womens-final-30086", info.id)
        assertEquals("Ski Jumping: Final", info.title)
        assertEquals(12793.0, info.duration)
        assertEquals("20260207", info.uploadDate)
        assertEquals(false, info.isLive)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun cbcGemLiveReadsTheRootData() = runTest {
        val url = "https://gem.cbc.ca/live/44"
        val page = """
            <html><head><script id="__NEXT_DATA__" type="application/json">
            {"props": {"pageProps": {"data": {
              "formattedIdMedia": "44",
              "isVodEnabled": true,
              "title": "Ottawa",
              "description": "The live TV channel and local programming from Ottawa",
              "airDate": "2017-04-13T12:00:00Z",
              "images": {"card": {"url": "https://img.example/live.jpg"}}
            }}}}
            </script></head></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://services.radio-canada.ca/media/validation/v2/" +
                    "?appCode=medianetlive*idMedia=44",
                contentType = "application/json",
                body = """{"errorCode": 0, "url": "https://media.example/live/master.m3u8"}""",
            ),
        )
        val info = CBCGemLiveIE(http(transfer)).extract(url)

        assertEquals("44", info.id)
        assertEquals("Ottawa", info.title)
        assertEquals("20170413", info.uploadDate)
        assertEquals(false, info.isLive)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // --------------------------------------------------------------- Listen

    @Test
    fun cbcListenReadsTheClipsApi() = runTest {
        val url = "https://www.cbc.ca/listen/cbc-podcasts/1353-the-naked-emperor/episode/16142603-introducing"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.cbc.ca/listen/api/v1/clips/16142603",
                contentType = "application/json",
                body = """
                    {"data": {
                      "url": "https://media.example/audio.mp3",
                      "title": "Fixture Episode",
                      "description": "Fixture description",
                      "releasedAt": 1745812800000,
                      "airdate": 1745827200000,
                      "duration": 229
                    }}
                """.trimIndent(),
            ),
        )
        val info = CBCListenIE(http(transfer)).extract(url)

        assertEquals("16142603", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(229.0, info.duration)
        assertEquals("20250428", info.uploadDate)
        assertEquals("https://media.example/audio.mp3", info.url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun cbcPlayerIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = playerUrl,
            infoDict = mapOf(
                "id" to Expect.Value("1.7194274"),
                "title" to Expect.Value("Fixture Player Title"),
                "duration" to Expect.Value(77.678),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = playerUrl, contentType = "text/html", body = playerPage()),
                FixtureRoute(
                    urlPattern = "https://asset.example/medianet.json",
                    contentType = "application/json",
                    body = """
                        {"url": "https://media.example/hls/master.m3u8", "params": [
                          {"name": "contentType", "value": "application/x-mpegURL"}
                        ]}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> CBCPlayerIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun cbcGemIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://gem.cbc.ca/media/schitts-creek/s06e01"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("schitts-creek/s06e01"),
                "title" to Expect.Value("Smoke Signals"),
                "duration" to Expect.Value(1324.0),
                "age_limit" to Expect.Value(14),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://services.radio-canada.ca/ott/catalog/v2/gem/show/" +
                        "schitts-creek/s06e01?device=web",
                    contentType = "application/json",
                    body = gemShowJson(),
                ),
                FixtureRoute(
                    urlPattern = "https://services.radio-canada.ca/media/validation/v2/?appCode=gem*idMedia=media-123",
                    contentType = "application/json",
                    body = """{"errorCode": 0, "url": "https://media.example/gem/master.m3u8"}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> CBCGemIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
