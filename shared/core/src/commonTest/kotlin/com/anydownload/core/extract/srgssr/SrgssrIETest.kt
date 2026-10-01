package com.anydownload.core.extract.srgssr

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
 * Fixture cases for the SRG SSR subset. Ids and media paths are synthesized on
 * `media.example`; the AKAMAI authparams is a fake value. No cookie, token, or
 * signed URL appears.
 */
class SrgssrIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val mediaId = "28e1a57d-5b76-4399-8ab3-9097f071e6c5"
    private val srgssrUrl = "srgssr:srf:video:$mediaId"
    private val playUrl = "http://www.srf.ch/play/tv/10vor10/video/snowden-beantragt-asyl-in-russland?id=$mediaId"

    private val mediaRoute = FixtureRoute(
        urlPattern = "https://il.srgssr.ch/integrationlayer/2.0/srf/mediaComposition/video/$mediaId.json?*",
        contentType = "application/json",
        body = """
            {"chapterList": [{"id": "$mediaId", "title": "Fixture Video",
              "description": "Fixture description", "date": "2013-07-01T10:00:00Z",
              "imageUrl": "https://media.example/thumb.png", "duration": 113827, "position": 0,
              "resourceList": [
                {"url": "https://media.example/akamai.m3u8", "protocol": "HLS",
                 "encoding": "H264", "quality": "HD", "tokenType": "AKAMAI"},
                {"url": "https://media.example/video.mp4", "protocol": "HTTPS",
                 "encoding": "H264", "quality": "SD"}],
              "podcastSdUrl": "https://media.example/podcast_sd.mp3",
              "podcastHdUrl": "https://media.example/podcast_hd.mp3",
              "subtitleList": [{"url": "https://media.example/sub.vtt", "locale": "de"}]}]}
        """.trimIndent(),
    )

    private val tokenRoute = FixtureRoute(
        urlPattern = "http://tp.srgssr.ch/akahd/token?acl=*",
        contentType = "application/json",
        body = """{"token": {"authparams": "hdnts=fake_value"}}""",
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val srgssr = SRGSSRIE(http(transfer()))
        assertTrue(srgssr.suitable(srgssrUrl))
        assertTrue(srgssr.suitable("srgssr:rts:audio:6348260"))
        assertTrue(srgssr.suitable("https://tp.srgssr.ch/p/foo/bar?urn=urn:srf:video:$mediaId"))
        assertFalse(srgssr.suitable(playUrl))

        val play = SRGSSRPlayIE(http(transfer()))
        assertTrue(play.suitable(playUrl))
        assertTrue(play.suitable("https://www.srf.ch/play/tv/popupvideoplayer?id=c4dba0ca-e75b-43b2-a34f-f708a4932e01"))
        assertTrue(play.suitable("https://www.rts.ch/play/tv/19h30/video/le-19h30?urn=urn:rts:video:6348260"))
        assertFalse(play.suitable(srgssrUrl))
        assertFalse(play.suitable("https://www.example.com/play/tv/x/video/y?id=1"))
    }

    // -------------------------------------------------------------- srgssr

    @Test
    fun srgssrYieldsTheResourceRowsAndMetadata() = runTest {
        val info = SRGSSRIE(http(transfer(mediaRoute, tokenRoute))).extract(srgssrUrl)
        assertEquals(mediaId, info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("20130701", info.uploadDate)
        assertEquals(113.827, info.duration)
        assertEquals("https://media.example/thumb.png", info.thumbnails.single().url)
        assertEquals(4, info.formats.size)
        assertEquals("HLS-H264-HD", info.formats[0].formatId)
        assertEquals("https://media.example/akamai.m3u8?hdnts=fake_value", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("HTTPS-H264-SD", info.formats[1].formatId)
        assertEquals("0", info.formats[1].quality)
        assertEquals("PODCAST-SD", info.formats[2].formatId)
        assertEquals("PODCAST-HD", info.formats[3].formatId)
        assertEquals(1, info.subtitles.size)
        assertEquals("de", info.subtitles[0].language)
        assertEquals("https://media.example/sub.vtt", info.subtitles[0].formats.single().url)
    }

    @Test
    fun srgssrIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = srgssrUrl,
            infoDict = mapOf(
                "id" to Expect.Value(mediaId),
                "title" to Expect.Value("Fixture Video"),
                "duration" to Expect.Value(113.827),
                "formats" to Expect.Count(4),
            ),
            routes = listOf(mediaRoute, tokenRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> SRGSSRIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun geoBlockFailsTyped() = runTest {
        val geoRoute = FixtureRoute(
            urlPattern = "https://il.srgssr.ch/integrationlayer/2.0/srf/mediaComposition/video/$mediaId.json?*",
            contentType = "application/json",
            body = """{"chapterList": [{"id": "$mediaId", "blockReason": "GEOBLOCK"}]}""",
        )
        val error = assertFailsWith<ExtractionError.GeoRestricted> {
            SRGSSRIE(http(transfer(geoRoute))).extract(srgssrUrl)
        }
        assertEquals(listOf("CH"), error.countries)
    }

    @Test
    fun legalBlockFailsTyped() = runTest {
        val legalRoute = FixtureRoute(
            urlPattern = "https://il.srgssr.ch/integrationlayer/2.0/srf/mediaComposition/video/$mediaId.json?*",
            contentType = "application/json",
            body = """{"chapterList": [{"id": "$mediaId", "blockReason": "LEGAL"}]}""",
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            SRGSSRIE(http(transfer(legalRoute))).extract(srgssrUrl)
        }
        assertTrue(error.message!!.contains("cannot be transmitted"), error.message)
    }

    // --------------------------------------------------------------- play

    @Test
    fun playDispatchesToTheSrgssrId() = runTest {
        val info = SRGSSRPlayIE(http(transfer(mediaRoute, tokenRoute))).extract(playUrl)
        assertEquals(mediaId, info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals(playUrl, info.webpageUrl)
        assertEquals(4, info.formats.size)
    }
}
