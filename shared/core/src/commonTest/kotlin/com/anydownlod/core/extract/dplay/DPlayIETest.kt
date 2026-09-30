package com.anydownlod.core.extract.dplay

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixture cases for the DPlay / Discovery+ subset. Every host, id, and media
 * address is synthesized (`*.example`); the token is the literal
 * `fake_value`, and no cookie, signed URL, or provider credential appears.
 */
class DPlayIETest {

    private val displayId = "fixture-show/fixture-episode"
    private val videoId = "13628"

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private fun videoJson(id: String = videoId): String = """
        {
          "data": {
            "id": "$id",
            "attributes": {
              "name": "  Fixture Episode  ",
              "description": "Fixture description",
              "videoDuration": 1500000,
              "publishStart": "2024-09-10T00:15:00Z",
              "seasonNumber": 1,
              "episodeNumber": 2
            }
          },
          "included": [
            {"type": "channel", "attributes": {"name": "Fixture Channel"}},
            {"type": "image", "attributes": {"src": "https://img.example/thumb.jpg", "width": 1280, "height": 720}},
            {"type": "show", "attributes": {"name": "Fixture Show"}},
            {"type": "tag", "attributes": {"name": "fixture"}}
          ]
        }
    """.trimIndent()

    private val streamingObjectJson = """
        {"data": {"attributes": {"streaming": {
          "dash": {"url": "https://media.example/dash/manifest.mpd"},
          "hls": {"url": "https://media.example/hls/master.m3u8"},
          "progressive": {"url": "https://media.example/video.mp4"}
        }}}}
    """.trimIndent()

    private val streamingListJson = """
        {"data": {"attributes": {"streaming": [
          {"type": "hls", "url": "https://media.example/hls/master.m3u8"},
          {"type": "dash", "url": "https://media.example/dash/manifest.mpd"}
        ]}}}
    """.trimIndent()

    private fun videoRoutes(
        discoHost: String,
        display: String = displayId,
        id: String = videoId,
        playback: DiscoPlayback = DiscoPlayback.V1,
    ): List<FixtureRoute> {
        val routes = mutableListOf(
            FixtureRoute(
                urlPattern = "https://$discoHost/token*",
                contentType = "application/json",
                body = """{"data": {"attributes": {"token": "fake_value"}}}""",
            ),
            FixtureRoute(
                urlPattern = "https://$discoHost/content/videos/$display*",
                contentType = "application/json",
                body = videoJson(id),
            ),
        )
        routes += if (playback == DiscoPlayback.V1) {
            FixtureRoute(
                urlPattern = "https://$discoHost/playback/videoPlaybackInfo/$id",
                contentType = "application/json",
                body = streamingObjectJson,
            )
        } else {
            FixtureRoute(
                urlPattern = "https://$discoHost/playback/v3/videoPlaybackInfo",
                method = "POST",
                contentType = "application/json",
                body = streamingListJson,
            )
        }
        return routes
    }

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf(
            DPlayIE(http(transfer())) to "https://www.dplay.se/videos/fixture-show/fixture-episode",
            DPlayIE(http(transfer())) to "https://it.dplay.com/nove/fixture-show/fixture-episode/",
            DPlayIE(http(transfer())) to "https://www.discoveryplus.dk/videoer/fixture-show/fixture-episode",
            HGTVDeIE(http(transfer())) to "https://de.hgtv.com/sendungen/fixture-show/fixture-episode",
            GoDiscoveryIE(http(transfer())) to "https://go.discovery.com/video/fixture-show/fixture-episode",
            TravelChannelIE(http(transfer())) to "https://watch.travelchannel.com/video/fixture-show/fixture-episode",
            CookingChannelIE(http(transfer())) to "https://cookingchanneltv.com/video/fixture-show/fixture-episode",
            HGTVUsaIE(http(transfer())) to "https://watch.hgtv.com/video/fixture-show/fixture-episode",
            FoodNetworkIE(http(transfer())) to "https://foodnetwork.com/video/fixture-show/fixture-episode",
            DestinationAmericaIE(http(transfer())) to "https://destinationamerica.com/video/fixture-show/fixture-episode",
            InvestigationDiscoveryIE(http(transfer())) to
                "https://investigationdiscovery.com/video/fixture-show/fixture-episode",
            AmHistoryChannelIE(http(transfer())) to "https://ahctv.com/video/fixture-show/fixture-episode",
            ScienceChannelIE(http(transfer())) to "https://sciencechannel.com/video/fixture-show/fixture-episode",
            DiscoveryLifeIE(http(transfer())) to "https://discoverylife.com/video/fixture-show/fixture-episode",
            AnimalPlanetIE(http(transfer())) to "https://animalplanet.com/video/fixture-show/fixture-episode",
            TLCIE(http(transfer())) to "https://go.tlc.com/video/fixture-show/fixture-episode",
            DiscoveryPlusIE(http(transfer())) to "https://www.discoveryplus.com/video/fixture-show/fixture-episode",
            DiscoveryPlusIndiaIE(http(transfer())) to "https://www.discoveryplus.in/videos/fixture-show/fixture-episode",
            DiscoveryNetworksDeIE(http(transfer())) to "https://dmax.de/sendungen/fixture-show/fixture-episode",
            DiscoveryPlusItalyIE(http(transfer())) to
                "https://www.discoveryplus.com/it/video/fixture-show/fixture-episode",
            DiscoveryPlusItalyShowIE(http(transfer())) to "https://www.discoveryplus.it/programmi/fixture-show",
            DiscoveryPlusIndiaShowIE(http(transfer())) to "https://www.discoveryplus.in/show/fixture-show",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(DPlayIE(http(transfer())).suitable("https://www.dplay.com/video/fixture-show/fixture-episode"))
        assertFalse(TLCIE(http(transfer())).suitable("https://go.tlc.com/show/fixture-show"))
        assertFalse(
            DiscoveryPlusIE(http(transfer())).suitable("https://www.discoveryplus.com/it/video/fixture-show/x"),
            "the Italy form belongs to DiscoveryPlusItalyIE",
        )
        assertTrue(
            DiscoveryPlusItalyIE(http(transfer()))
                .suitable("https://www.discoveryplus.com/it/video/fixture-show/fixture-episode"),
        )
        assertFalse(DiscoveryPlusItalyShowIE(http(transfer())).suitable("https://www.discoveryplus.it/video/x"))
    }

    // ------------------------------------------------------------- DPlay v1

    @Test
    fun dplayV1MapsStreamingFormatsAndMetadata() = runTest {
        val transfer = transfer(
            *videoRoutes("disco-api.dplay.se").toTypedArray(),
        )
        val info = DPlayIE(http(transfer)).extract(
            "https://www.dplay.se/videos/fixture-show/fixture-episode",
        )

        assertEquals("13628", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(1500.0, info.duration)
        assertEquals("20240910", info.uploadDate)
        assertEquals("Fixture Channel", info.channel)
        assertEquals("https://img.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(1280L, info.thumbnails.single().width)
        assertEquals(720L, info.thumbnails.single().height)

        assertEquals(3, info.formats.size)
        assertEquals("dash", info.formats[0].formatId)
        assertEquals("http_dash_segments", info.formats[0].protocol)
        assertEquals("hls", info.formats[1].formatId)
        assertEquals("m3u8_native", info.formats[1].protocol)
        assertEquals("progressive", info.formats[2].formatId)
        assertEquals("mp4", info.formats[2].ext)
        assertNull(info.formats[2].protocol)
        assertEquals("dplay.se", info.formats[0].httpHeaders?.get("referer"))

        // The bearer token is minted from the anonymous endpoint and never
        // stored in the info dict.
        assertTrue(transfer.requests.any { it.url.startsWith("https://disco-api.dplay.se/token?realm=dplayse") })
    }

    // --------------------------------------------------- Discovery+ products

    private data class ProductSpec(
        val name: String,
        val make: (ExtractorHttp) -> InfoExtractor,
        val url: String,
        val discoHost: String,
        val realm: String,
    )

    private fun productSpecs(): List<ProductSpec> = listOf(
        ProductSpec(
            "HGTVDe", { HGTVDeIE(it) }, "https://de.hgtv.com/sendungen/$displayId",
            "eu1-prod.disco-api.com", "hgtv",
        ),
        ProductSpec(
            "GoDiscovery", { GoDiscoveryIE(it) }, "https://go.discovery.com/video/$displayId",
            "us1-prod-direct.go.discovery.com", "go",
        ),
        ProductSpec(
            "TravelChannel", { TravelChannelIE(it) }, "https://watch.travelchannel.com/video/$displayId",
            "us1-prod-direct.watch.travelchannel.com", "go",
        ),
        ProductSpec(
            "CookingChannel", { CookingChannelIE(it) }, "https://watch.cookingchanneltv.com/video/$displayId",
            "us1-prod-direct.watch.cookingchanneltv.com", "go",
        ),
        ProductSpec(
            "HGTVUsa", { HGTVUsaIE(it) }, "https://watch.hgtv.com/video/$displayId",
            "us1-prod-direct.watch.hgtv.com", "go",
        ),
        ProductSpec(
            "FoodNetwork", { FoodNetworkIE(it) }, "https://watch.foodnetwork.com/video/$displayId",
            "us1-prod-direct.watch.foodnetwork.com", "go",
        ),
        ProductSpec(
            "DestinationAmerica", { DestinationAmericaIE(it) }, "https://www.destinationamerica.com/video/$displayId",
            "us1-prod-direct.destinationamerica.com", "go",
        ),
        ProductSpec(
            "InvestigationDiscovery", { InvestigationDiscoveryIE(it) },
            "https://www.investigationdiscovery.com/video/$displayId",
            "us1-prod-direct.investigationdiscovery.com", "go",
        ),
        ProductSpec(
            "AmHistoryChannel", { AmHistoryChannelIE(it) }, "https://www.ahctv.com/video/$displayId",
            "us1-prod-direct.ahctv.com", "go",
        ),
        ProductSpec(
            "ScienceChannel", { ScienceChannelIE(it) }, "https://www.sciencechannel.com/video/$displayId",
            "us1-prod-direct.sciencechannel.com", "go",
        ),
        ProductSpec(
            "DiscoveryLife", { DiscoveryLifeIE(it) }, "https://www.discoverylife.com/video/$displayId",
            "us1-prod-direct.discoverylife.com", "go",
        ),
        ProductSpec(
            "AnimalPlanet", { AnimalPlanetIE(it) }, "https://www.animalplanet.com/video/$displayId",
            "us1-prod-direct.animalplanet.com", "go",
        ),
        ProductSpec(
            "TLC", { TLCIE(it) }, "https://go.tlc.com/video/$displayId",
            "us1-prod-direct.tlc.com", "go",
        ),
        ProductSpec(
            "DiscoveryPlusIndia", { DiscoveryPlusIndiaIE(it) }, "https://www.discoveryplus.in/videos/$displayId",
            "ap2-prod-direct.discoveryplus.in", "dplusindia",
        ),
        ProductSpec(
            "DiscoveryPlusItaly", { DiscoveryPlusItalyIE(it) }, "https://www.discoveryplus.com/it/video/$displayId",
            "eu1-prod-direct.discoveryplus.com", "dplay",
        ),
    )

    @Test
    fun discoveryPlusProductsShareTheV3Shape() = runTest {
        for (spec in productSpecs()) {
            val transfer = transfer(*videoRoutes(spec.discoHost, playback = DiscoPlayback.V3).toTypedArray())
            val info = spec.make(http(transfer)).extract(spec.url)

            assertEquals(videoId, info.id, "${spec.name}: id")
            assertEquals("Fixture Episode", info.title, "${spec.name}: title")
            assertEquals(2, info.formats.size, "${spec.name}: formats")
            assertEquals("hls", info.formats[0].formatId, "${spec.name}: hls first")
            assertEquals("m3u8_native", info.formats[0].protocol, "${spec.name}: hls protocol")
            assertEquals("http_dash_segments", info.formats[1].protocol, "${spec.name}: dash protocol")

            val tokenRequest = transfer.requests.firstOrNull { it.url.contains("/token?realm=${spec.realm}") }
            assertTrue(tokenRequest != null, "${spec.name}: token realm ${spec.realm}")
            val videoRequest = transfer.requests.first { it.url.contains("/content/videos/") }
            assertTrue(
                videoRequest.headers.containsKey("x-disco-client"),
                "${spec.name}: x-disco-client header must be declared",
            )
        }
    }

    @Test
    fun discoveryPlusPicksTheHostByCountry() = runTest {
        val us = transfer(*videoRoutes("us1-prod-direct.discoveryplus.com", playback = DiscoPlayback.V3).toTypedArray())
        val usInfo = DiscoveryPlusIE(http(us)).extract(
            "https://www.discoveryplus.com/ca/video/fixture-show/fixture-episode",
        )
        assertEquals(videoId, usInfo.id)
        assertTrue(us.requests.any { it.url.startsWith("https://us1-prod-direct.discoveryplus.com/token?realm=go") })
        assertEquals(
            "realm=go,siteLookupKey=dplus_ca",
            us.requests.first { it.url.contains("/content/videos/") }.headers["x-disco-params"],
        )

        val eu = transfer(*videoRoutes("eu1-prod-direct.discoveryplus.com", playback = DiscoPlayback.V3).toTypedArray())
        val euInfo = DiscoveryPlusIE(http(eu)).extract(
            "https://www.discoveryplus.com/gb/video/sport/fixture-show/fixture-episode",
        )
        assertEquals(videoId, euInfo.id)
        assertTrue(eu.requests.any { it.url.startsWith("https://eu1-prod-direct.discoveryplus.com/token?realm=dplay") })
        assertEquals(
            "realm=dplay,siteLookupKey=dplus_gb",
            eu.requests.first { it.url.contains("/content/videos/") }.headers["x-disco-params"],
        )
    }

    @Test
    fun discoveryNetworksDeReadsTheLomaMetaAndThenTheDiscoApi() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://de-api.loma-cms.com/feloma/videos/fixture-episode/*",
                contentType = "application/json",
                body = """{"uid": "0123456789abcdef"}""",
            ),
            *videoRoutes(
                "eu1-prod.disco-api.com",
                display = "9abcdef",
                id = "9abcdef",
                playback = DiscoPlayback.V3,
            ).toTypedArray(),
        )
        val info = DiscoveryNetworksDeIE(http(transfer)).extract(
            "https://dmax.de/sendungen/fixture-show/fixture-episode",
        )
        assertEquals("9abcdef", info.id)
        assertTrue(transfer.requests.any { it.url.startsWith("https://eu1-prod.disco-api.com/token?realm=dmaxde") })
        assertTrue(transfer.requests.any { it.url.startsWith("https://de-api.loma-cms.com/feloma/videos/fixture-episode/") })
    }

    // ------------------------------------------------------------ show pages

    private fun showComponent(showId: String, seasons: List<String>): String {
        val options = seasons.joinToString(",") { """{"id": "$it"}""" }
        return """
            {"included": [
              {"type": "image", "attributes": {"src": "https://img.example/show.jpg"}},
              {"attributes": {"component": {
                "mandatoryParams": "filter[show.id]=$showId",
                "filters": [{"options": [$options]}]
              }}},
              {}, {}, {}
            ]}
        """.trimIndent()
    }

    @Test
    fun italyShowListsSeasonsAndReDispatchesEpisodeUrls() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://disco-api.discoveryplus.it/token*",
                contentType = "application/json",
                body = """{"data": {"attributes": {"token": "fake_value"}}}""",
            ),
            FixtureRoute(
                urlPattern = "https://disco-api.discoveryplus.it/cms/routes/programmi/fixture-show?include=default",
                contentType = "application/json",
                body = showComponent("fixtureShowId", listOf("1", "2")),
            ),
            FixtureRoute(
                urlPattern = "https://disco-api.discoveryplus.it/content/videos?sort=episodeNumber" +
                    "&filter%5BseasonNumber%5D=1*",
                contentType = "application/json",
                body = """
                    {"meta": {"totalPages": 1}, "data": [
                      {"id": "ep1", "attributes": {"path": "fixture-show/fixture-1"}}
                    ]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://disco-api.discoveryplus.it/content/videos?sort=episodeNumber" +
                    "&filter%5BseasonNumber%5D=2*",
                contentType = "application/json",
                body = """
                    {"meta": {"totalPages": 1}, "data": [
                      {"id": "ep2", "attributes": {"path": "fixture-show/fixture-2"}}
                    ]}
                """.trimIndent(),
            ),
        )
        val info = DiscoveryPlusItalyShowIE(http(transfer)).extract(
            "https://www.discoveryplus.it/programmi/fixture-show",
        )
        assertEquals("fixture-show", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("ep1", info.entries[0].id)
        assertEquals("https://www.discoveryplus.it/videos/fixture-show/fixture-1", info.entries[0].url)
        assertEquals("ep2", info.entries[1].id)
        assertEquals("fixtureShowId", transfer.requests
            .first { it.url.contains("filter%5BseasonNumber%5D=1") }
            .url.substringAfter("filter%5Bshow.id%5D=").substringBefore('&'))
    }

    @Test
    fun indiaShowUsesItsOwnRouteIndex() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://ap2-prod-direct.discoveryplus.in/token*",
                contentType = "application/json",
                body = """{"data": {"attributes": {"token": "fake_value"}}}""",
            ),
            FixtureRoute(
                urlPattern = "https://ap2-prod-direct.discoveryplus.in/cms/routes/show/fixture-show?include=default",
                contentType = "application/json",
                body = """
                    {"included": [
                      {}, {}, {}, {},
                      {"attributes": {"component": {
                        "mandatoryParams": "filter[show.id]=fixtureIndiaId",
                        "filters": [{"options": [{"id": "1"}]}]
                      }}}
                    ]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://ap2-prod-direct.discoveryplus.in/content/videos?sort=episodeNumber*",
                contentType = "application/json",
                body = """
                    {"meta": {"totalPages": 1}, "data": [
                      {"id": "india1", "attributes": {"path": "fixture-show/fixture-1"}}
                    ]}
                """.trimIndent(),
            ),
        )
        val info = DiscoveryPlusIndiaShowIE(http(transfer)).extract(
            "https://www.discoveryplus.in/show/fixture-show",
        )
        assertEquals("fixture-show", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.discoveryplus.in/videos/fixture-show/fixture-1", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun dplayIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://www.dplay.se/videos/fixture-show/fixture-episode",
            infoDict = mapOf(
                "id" to Expect.Value("13628"),
                "title" to Expect.Value("Fixture Episode"),
                "duration" to Expect.Value(1500.0),
                "formats" to Expect.Count(3),
                "formats.0.format_id" to Expect.Value("dash"),
                "formats.1.protocol" to Expect.Value("m3u8_native"),
                "thumbnails.0.url" to Expect.Value("https://img.example/thumb.jpg"),
            ),
            routes = videoRoutes("disco-api.dplay.se"),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> DPlayIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun tlcIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://go.tlc.com/video/fixture-show/fixture-episode",
            infoDict = mapOf(
                "id" to Expect.Value("13628"),
                "title" to Expect.Value("Fixture Episode"),
                "formats.0.format_id" to Expect.Value("hls"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = videoRoutes("us1-prod-direct.tlc.com", playback = DiscoPlayback.V3),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TLCIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
