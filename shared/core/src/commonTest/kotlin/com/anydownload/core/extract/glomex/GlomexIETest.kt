package com.anydownload.core.extract.glomex

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
 * Fixture cases for the Glomex subset. Ids and media paths are synthesized on
 * `media.example`; the integration ids are fake values. No cookie, token, or
 * signed URL appears.
 */
class GlomexIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl =
        "https://video.glomex.com/sport/v-cb24uwg77hgh-nach-2-0-sieg-guardiola-mit-mancity"
    private val embedUrl =
        "https://player.glomex.com/integration/1/iframe-player.html?integrationId=4059a013k56vb2yd&playlistId=v-cfa6lye0dkdd-sf"

    private val singleRoute = FixtureRoute(
        urlPattern = "https://integration-cloudfront-eu-west-1.mes.glomex.cloud/?*",
        contentType = "application/json",
        body = """
            {"videos": [{"clip_id": "v-cb24uwg77hgh", "title": "Fixture Video",
              "description": "Fixture description", "clip_duration": 29600,
              "created_at": 1619895017, "language": "de",
              "image": {"id": "img1", "url": "https://media.example/image"},
              "source": {"hls": "https://media.example/master.m3u8",
                         "mp4": "https://media.example/video.mp4"}}]}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val video = GlomexIE(http(transfer()))
        assertTrue(video.suitable(videoUrl))
        assertFalse(video.suitable(embedUrl))

        val embed = GlomexEmbedIE(http(transfer()))
        assertTrue(embed.suitable(embedUrl))
        assertTrue(embed.suitable(
            "https://player.glomex.com/integration/1/iframe-player.html" +
                "?origin=fullpage&integrationId=19syy24xjn1oqlpc&playlistId=rl-vcb49w1fb592p",
        ))
        assertFalse(embed.suitable(videoUrl))
        assertFalse(embed.suitable("https://www.example.com/iframe-player.html?playlistId=v-1"))
    }

    // ----------------------------------------------------------------- video

    @Test
    fun videoPageYieldsTheFormatRowsAndMetadata() = runTest {
        val info = GlomexIE(http(transfer(singleRoute))).extract(videoUrl)
        assertEquals("v-cb24uwg77hgh", info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(29600.0, info.duration)
        assertEquals("20210501", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("hls", info.formats[0].formatId)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("de", info.formats[0].language)
        assertEquals("mp4", info.formats[1].formatId)
        assertEquals("de", info.formats[1].language)
        assertEquals("https://media.example/image/profile:player-960x540", info.thumbnails.single().url)
        assertEquals(960L, info.thumbnails.single().width)
        assertEquals(540L, info.thumbnails.single().height)
    }

    @Test
    fun videoPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("v-cb24uwg77hgh"),
                "title" to Expect.Value("Fixture Video"),
                "duration" to Expect.Value(29600.0),
                "upload_date" to Expect.Value("20210501"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(singleRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> GlomexIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun embedUrlUsesItsIntegrationId() = runTest {
        val info = GlomexEmbedIE(http(transfer(singleRoute))).extract(embedUrl)
        assertEquals("v-cb24uwg77hgh", info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals(embedUrl, info.webpageUrl)
    }

    @Test
    fun embedWithoutIntegrationIdFailsTyped() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            GlomexEmbedIE(http(transfer())).extract(
                "https://player.glomex.com/integration/1/iframe-player.html?playlistId=v-cfa6lye0dkdd-sf",
            )
        }
        assertTrue(error.message!!.contains("integrationId"), error.message)
    }

    // -------------------------------------------------------------- playlist

    @Test
    fun playlistYieldsSelectableMediaItems() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://integration-cloudfront-eu-west-1.mes.glomex.cloud/?*",
            contentType = "application/json",
            body = """
                {"videos": [
                  {"clip_id": "v-one", "title": "One", "clip_duration": 10,
                   "source": {"mp4": "https://media.example/one.mp4"}},
                  {"clip_id": "v-two", "title": "Two", "clip_duration": 20,
                   "source": {"mp4": "https://media.example/two.mp4"}}]}
            """.trimIndent(),
        )
        val info = GlomexEmbedIE(http(transfer(route))).extract(
            "https://player.glomex.com/integration/1/iframe-player.html?integrationId=19syy24xjn1oqlpc&playlistId=rl-vcb49w1fb592p",
        )
        assertEquals("rl-vcb49w1fb592p", info.id)
        assertEquals(2, info.media.size)
        assertEquals("v-one", info.media[0].mediaId)
        assertEquals("One", info.media[0].title)
        assertEquals(10.0, info.media[0].duration)
        assertEquals("v-two", info.media[1].mediaId)
    }

    // ------------------------------------------------------------------ geo

    @Test
    fun geoblockedVideoFailsTyped() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://integration-cloudfront-eu-west-1.mes.glomex.cloud/?*",
            contentType = "application/json",
            body = """
                {"videos": [{"clip_id": "v-one", "error_code": "contentGeoblocked",
                             "geo_locations": ["DE"]}]}
            """.trimIndent(),
        )
        val error = assertFailsWith<ExtractionError.GeoRestricted> {
            GlomexIE(http(transfer(route))).extract(videoUrl)
        }
        assertEquals(listOf("DE"), error.countries)
    }

    @Test
    fun emptyVideoListFailsTyped() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://integration-cloudfront-eu-west-1.mes.glomex.cloud/?*",
            contentType = "application/json",
            body = """{"videos": []}""",
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            GlomexIE(http(transfer(route))).extract(videoUrl)
        }
        assertTrue(error.message!!.contains("no videos found"), error.message)
    }
}
