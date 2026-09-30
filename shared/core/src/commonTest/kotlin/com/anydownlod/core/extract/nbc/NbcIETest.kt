package com.anydownlod.core.extract.nbc

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.InfoExtractor
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
 * Fixture cases for the NBC subset. Every id, host, and media address is
 * synthesized (`*.example`); `fake_value` stands in for any token, and no
 * cookie, Adobe Pass software statement, or signed URL appears.
 */
class NbcIETest {

    private val nbcUrl = "https://www.nbc.com/the-tonight-show/video/fixture-episode/2848237"
    private val nbcTpPath = "NnzsPC/media/guid/1234567890/2848237"
    private val bravoTpPath = "NnzsPC/media/guid/1234567890/3923059"

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val smilXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <smil xmlns="http://www.w3.org/2005/SMIL21/Language">
          <body><switch>
            <video src="https://media.example/hls/master.m3u8" dur="236504ms"
                   type="application/x-mpegURL" width="1280" height="720"/>
          </switch></body>
        </smil>
    """.trimIndent()

    private val tpMetadataJson = """
        {
          "title": "Fixture TP Episode",
          "description": "Fixture TP description",
          "defaultThumbnailUrl": "https://img.example/tp.jpg",
          "duration": 236504,
          "pubDate": 1424246400000,
          "billingCode": "NBCU-COM",
          "ratings": [{"rating": "TV-14"}],
          "captions": [{"src": "https://media.example/captions.vtt", "lang": "en", "type": "text/vtt"}],
          "chapters": [{"startTime": 0, "endTime": 10000}, {"startTime": 10000, "endTime": 20000}]
        }
    """.trimIndent()

    private fun nbcuRoutes(tpPath: String): List<FixtureRoute> = listOf(
        FixtureRoute(
            urlPattern = "https://link.theplatform.com/s/$tpPath?formats=*",
            contentType = "application/xml",
            body = smilXml,
        ),
        FixtureRoute(
            urlPattern = "https://link.theplatform.com/s/$tpPath?format=preview",
            contentType = "application/json",
            body = tpMetadataJson,
        ),
    )

    private fun graphqlJson(locked: Boolean = false): String = """
        {"data": {"bonanzaPage": {"metadata": {
          "description": "GraphQL description",
          "episodeNumber": 86,
          "keywords": ["fixture"],
          "locked": $locked,
          "mpxAccountId": "1234567890",
          "mpxGuid": "2848237",
          "rating": "TV-14",
          "resourceId": "12345",
          "seasonNumber": 2,
          "secondaryTitle": "GraphQL subtitle",
          "seriesShortTitle": "Fixture Show"
        }}}}
    """.trimIndent()

    private fun preloadPage(): String = """
        <html><body><script>
        PRELOAD = {"pages":{"/the-tonight-show/video/fixture-episode/2848237":{"base":{"metadata":{
          "description": "Preload description",
          "mpxAccountId": "1234567890",
          "mpxGuid": "2848237",
          "secondaryTitle": "Preload subtitle"
        }}}}}
        </script></body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NBCIE(http(transfer())) to nbcUrl,
            NBCIE(http(transfer())) to
                "https://www.nbc.com/classic-tv/charles-in-charge/video/charles-in-charge-pilot/n3310",
            NBCNewsIE(http(transfer())) to
                "https://www.nbcnews.com/watch/nbcnews-com/how-twitter-reacted-to-the-snowden-interview-269389891880",
            NBCNewsIE(http(transfer())) to
                "https://www.today.com/video/see-the-aurora-borealis-from-space-669831235788",
            NBCOlympicsIE(http(transfer())) to
                "https://www.nbcolympics.com/videos/fixture-olympics-video",
            NBCStationsIE(http(transfer())) to
                "https://www.nbcboston.com/weather/video-weather/highs-near-freezing/2961135/",
            NBCStationsIE(http(transfer())) to
                "https://www.telemundoarizona.com/responde/fixture/2247002/",
            BravoTVIE(http(transfer())) to
                "https://www.bravotv.com/top-chef/season-16/episode-15/videos/the-top-chef-season-16-winner-is",
            BravoTVIE(http(transfer())) to "https://www.oxygen.com/in-ice-cold-blood/season-1/closing-night",
            SyfyIE(http(transfer())) to "https://www.syfy.com/face-off/season-13/episode-10/videos/keyed-up",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NBCIE(http(transfer())).suitable("https://www.nbc.com/the-tonight-show"))
        assertFalse(NBCStationsIE(http(transfer())).suitable("https://www.example.com/watch/2961135/"))
        assertFalse(NBCNewsIE(http(transfer())).suitable("https://www.nbcnews.example/watch/x"))
    }

    // ------------------------------------------------------------------- NBCIE

    @Test
    fun nbcGraphQlMapsMetadataAndFormats() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://friendship.nbc.co/v2/graphql?*",
                contentType = "application/json",
                body = graphqlJson(),
            ),
            *nbcuRoutes(nbcTpPath).toTypedArray(),
        )
        val info = NBCIE(http(transfer)).extract(nbcUrl)

        assertEquals("2848237", info.id)
        assertEquals("Fixture TP Episode", info.title)
        assertEquals("GraphQL description", info.description)
        assertEquals(236.504, info.duration)
        assertEquals("20150218", info.uploadDate)
        assertEquals("NBCU-COM", info.uploader)
        assertEquals(14, info.ageLimit)
        assertEquals(2, info.chapters.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/hls/master.m3u8", info.formats.single().url)
        assertEquals(1, info.subtitles.size)
        assertTrue(transfer.requests.any { it.url.startsWith("https://link.theplatform.com/s/$nbcTpPath?formats=") })
    }

    @Test
    fun nbcLockedVideoFailsAsALoginWall() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://friendship.nbc.co/v2/graphql?*",
                contentType = "application/json",
                body = graphqlJson(locked = true),
            ),
            *nbcuRoutes(nbcTpPath).toTypedArray(),
        )
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            NBCIE(http(transfer)).extract(nbcUrl)
        }
        assertTrue(error.message!!.contains("TV provider"))
    }

    @Test
    fun nbcFallsBackToThePreloadPage() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://friendship.nbc.co/v2/graphql?*",
                contentType = "application/json",
                body = """{"data": {"bonanzaPage": {"metadata": null}}}""",
            ),
            FixtureRoute(urlPattern = nbcUrl, contentType = "text/html", body = preloadPage()),
            *nbcuRoutes(nbcTpPath).toTypedArray(),
        )
        val info = NBCIE(http(transfer)).extract(nbcUrl)
        assertEquals("2848237", info.id)
        assertEquals("Fixture TP Episode", info.title)
        assertEquals("Preload description", info.description)
    }

    // ---------------------------------------------------- NBCU deck and LS pages

    private fun bravoPage(entitlement: String): String = """
        <html><body>
        <script data-drupal-selector="drupal-settings-json" type="application/json">
        {"tve_adobe_auth": {"adobePassResourceId": "fixture", "adobePassRequestorId": "fixture"}}
        </script>
        <div class="video-deck tve-video-deck-app"
             data-mpx-media-account-pid="NnzsPC"
             data-mpx-media-account-id="1234567890"
             data-guid="3923059"
             data-title="Fixture Raw Title"
             data-entitlement="$entitlement"
             data-rating="TV-PG"
             data-normalized-video="{&quot;title&quot;:&quot;Fixture Episode&quot;,&quot;durationInSeconds&quot;:190,&quot;airDate&quot;:&quot;2019-03-15T00:00:00Z&quot;,&quot;thumbnailUrl&quot;:&quot;https://img.example/deck.jpg&quot;,&quot;seasonNumber&quot;:16,&quot;episodeNumber&quot;:15,&quot;episodeTitle&quot;:&quot;Finale&quot;,&quot;show&quot;:&quot;Fixture Show&quot;}">
        </div></body></html>
    """.trimIndent()

    @Test
    fun bravoDeckUsesTheDrupalSettingsAndMetadata() = runTest {
        val url = "https://www.bravotv.com/top-chef/season-16/episode-15/videos/the-top-chef-season-16-winner-is"
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = bravoPage("free")),
            *nbcuRoutes(bravoTpPath).toTypedArray(),
        )
        val info = BravoTVIE(http(transfer)).extract(url)
        assertEquals("3923059", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals(190.0, info.duration)
        assertEquals("20190315", info.uploadDate)
        assertEquals("https://img.example/deck.jpg", info.thumbnails.single().url)
        assertEquals(14, info.ageLimit)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun deckAuthEntitlementFailsAsALoginWall() = runTest {
        val url = "https://www.bravotv.com/top-chef/season-20/episode-1/london-calling"
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = bravoPage("auth")),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            BravoTVIE(http(transfer)).extract(url)
        }
    }

    @Test
    fun syfyReadsTheLsPlaylistVariant() = runTest {
        val url = "https://www.syfy.com/face-off/season-13/episode-10/videos/keyed-up"
        val page = """
            <html><body>
            <script data-drupal-selector="drupal-settings-json" type="application/json">
            {"ls_playlist": [{"defaultGuid": "3774403", "mpxMediaAccountPid": "NnzsPC",
              "mpxMediaAccountId": "1234567890",
              "videos": [{"guid": "3774403", "title": "Keyed Up", "description": "Fixture",
                          "durationInSeconds": 169, "airDate": "2018-08-08T00:00:00Z",
                          "thumbnailUrl": "https://img.example/syfy.jpg"}]}]}
            </script></body></html>
        """.trimIndent()
        val tpPath = "NnzsPC/media/guid/1234567890/3774403"
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            *nbcuRoutes(tpPath).toTypedArray(),
        )
        val info = SyfyIE(http(transfer)).extract(url)
        assertEquals("3774403", info.id)
        assertEquals("Keyed Up", info.title)
        assertEquals(169.0, info.duration)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // --------------------------------------------------------------- NBC News

    @Test
    fun nbcNewsMapsVideoAssets() = runTest {
        val url = "https://www.nbcnews.com/watch/nbcnews-com/how-twitter-reacted-269389891880"
        val page = """
            <html><head>
            <script id="__NEXT_DATA__" type="application/json">
            {"props": {"initialState": {"video": {"current": {
              "headline": {"primary": "Fixture News Title"},
              "description": {"primary": "Fixture news description"},
              "duration": "PT46S",
              "datePublished": "2014-05-29T00:00:00Z",
              "primaryImage": {"url": {"primary": "https://img.example/news.jpg"}},
              "videoAssets": [
                {"publicUrl": "https://media.example/news.mp4", "format": "MP4",
                 "bitrate": 2000, "width": 1280, "height": 720},
                {"publicUrl": "https://link.theplatform.com/s/path/to/asset.m3u8", "format": "M3U"}
              ],
              "closedCaptioning": {"en": "https://media.example/news.vtt"}
            }}}}}
            </script></head></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = NBCNewsIE(http(transfer)).extract(url)

        assertEquals("269389891880", info.id)
        assertEquals("Fixture News Title", info.title)
        assertEquals("Fixture news description", info.description)
        assertEquals(46.0, info.duration)
        assertEquals("20140529", info.uploadDate)
        assertEquals("https://img.example/news.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("MP4-2", info.formats[0].formatId)
        assertEquals(1280L, info.formats[0].width)
        assertEquals("m3u8_native", info.formats[1].protocol)
        assertTrue(
            info.formats[1].url?.endsWith("?format=redirect") == true,
            "the ThePlatform asset must ask for a redirect",
        )
        assertEquals("https://media.example/news.vtt", info.subtitles.single().formats.single().url)
    }

    // -------------------------------------------------------------- Olympics

    @Test
    fun nbcOlympicsHandsOffToThePlatform() = runTest {
        val url = "https://www.nbcolympics.com/videos/fixture-olympics-video"
        val page = """
            <html><body><script>
            jQuery.extend(Drupal.settings, {"vod":{"iframe_url":"https://vplayer.nbcolympics.com/p/BxmELC/nbcolympics/select/SAwGfPlQ1q01"}});
            </script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = NBCOlympicsIE(http(transfer)).extract(url)
        assertEquals("fixture-olympics-video", info.id)
        assertEquals(
            "https://player.theplatform.com/p/BxmELC/nbcolympics/select/SAwGfPlQ1q01",
            info.redirectUrl,
        )
    }

    @Test
    fun nbcOlympicsFallsBackToTheEmbedUrlPattern() = runTest {
        val url = "https://www.nbcolympics.com/video/fixture-olympics-embed"
        val page = """
            <html><body><script>
            window.__DATA__ = {"embedUrl": "https://player.theplatform.com/p/abc/nbc/select/fixture-id"};
            </script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = NBCOlympicsIE(http(transfer)).extract(url)
        assertEquals(
            "https://player.theplatform.com/p/abc/nbc/select/fixture-id",
            info.redirectUrl,
        )
    }

    // -------------------------------------------------------------- Stations

    @Test
    fun nbcStationsMapsTheSmilVideos() = runTest {
        val url = "https://www.nbcboston.com/weather/video-weather/highs-near-freezing/2961135/"
        val page = """
            <html><body>
            <script>var nbc = {"pdkAcct":"Yh1nAC","callLetters":"WBTS","on_air_name":"NBC 10 Boston",
              "video":{"fwSSID":"12345","fwNetworkID":"382114"}};</script>
            <div data-videos="[{"pid_streaming_web_high":"2961135","title":"Fixture Stations Title",
              "summary":"Fixture stations summary"}]"
              data-meta="{"date_string":"<time datetime=\"2023-02-01T12:00:00Z\"></time>"}">
            </div></body></html>
        """.trimIndent()
        val smil = """
            <smil xmlns="http://www.w3.org/2005/SMIL21/Language">
              <body>
                <video src="https://media.example/stations/master.m3u8" dur="235669ms"
                       type="application/x-mpegURL" width="1920" height="1080"/>
                <textstream src="https://media.example/stations.vtt" lang="en" type="text/vtt"/>
              </body>
            </smil>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://link.theplatform.com/s/Yh1nAC/2961135?*",
                contentType = "application/xml",
                body = smil,
            ),
        )
        val info = NBCStationsIE(http(transfer)).extract(url)
        assertEquals("2961135", info.id)
        assertEquals("Fixture Stations Title", info.title)
        assertEquals("Fixture stations summary", info.description)
        assertEquals("nbcboston", info.channel)
        assertEquals("WBTS", info.channelId)
        assertEquals("NBC 10 Boston", info.uploader)
        assertEquals(235.669, info.duration)
        assertEquals("20230201", info.uploadDate)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/stations.vtt", info.subtitles.single().formats.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun nbcIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = nbcUrl,
            infoDict = mapOf(
                "id" to Expect.Value("2848237"),
                "title" to Expect.Value("Fixture TP Episode"),
                "duration" to Expect.Value(236.504),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://friendship.nbc.co/v2/graphql?*",
                    contentType = "application/json",
                    body = graphqlJson(),
                ),
            ) + nbcuRoutes(nbcTpPath),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NBCIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun bravoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.bravotv.com/top-chef/season-16/episode-15/videos/the-top-chef-season-16-winner-is"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("3923059"),
                "title" to Expect.Value("Fixture Episode"),
                "duration" to Expect.Value(190.0),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = url, contentType = "text/html", body = bravoPage("free")),
            ) + nbcuRoutes(bravoTpPath),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> BravoTVIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
