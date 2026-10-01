package com.anydownload.core.extract.ninaprotocol

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Nina Protocol subset. Ids and media paths are
 * synthesized on `media.example`; the public keys are fake values. No cookie,
 * token, or signed URL appears.
 */
class NinaProtocolIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val releaseUrl =
        "https://www.ninaprotocol.com/releases/3SvsMM3y4oTPZ5DXFJnLkCAqkxz34hjzFxqms1vu9XBJ"

    private val releaseRoute = FixtureRoute(
        urlPattern = "https://api.ninaprotocol.com/v1/releases/3SvsMM3y4oTPZ5DXFJnLkCAqkxz34hjzFxqms1vu9XBJ",
        contentType = "application/json",
        body = """
            {"release": {
              "publicKey": "3SvsMM3y4oTPZ5DXFJnLkCAqkxz34hjzFxqms1vu9XBJ",
              "slug": "the-spatulas-march-chant",
              "datetime": "2023-12-01T10:40:10.000Z",
              "metadata": {"name": "The Spatulas - March Chant",
                "description": "Fixture description",
                "image": "https://media.example/cover.jpg",
                "properties": {"title": "The Spatulas - March Chant", "tags": ["punk"],
                  "files": [
                    {"uri": "https://media.example/track1.mp3", "track_title": "March Chant In April",
                     "type": "audio/mpeg", "track": 1, "duration": 152},
                    {"uri": "https://media.example/track2.mp3", "track_title": "Rescue Mission",
                     "type": "audio/mpeg", "track": 2, "duration": 212}]}},
              "publisherAccount": {"handle": "ppmrecs", "publicKey": "2bGjgdKUddJoj2shYGqfNcUfoSoABP21RJoiwGMZDq3A",
                                   "displayName": "Post Present Medium"},
              "hub": {"handle": "ppm", "publicKey": "4ceG4zsb7VVxBTGPtZMqDZWGHo3VUg2xRvzC2b17ymWP"}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val ie = NinaProtocolIE(http(transfer()))
        assertTrue(ie.suitable(releaseUrl))
        assertTrue(ie.suitable("https://ninaprotocol.com/releases/f-g-s-american-shield"))
        assertFalse(ie.suitable("https://www.example.com/releases/foo"))
    }

    // -------------------------------------------------------------- release

    @Test
    fun releaseYieldsTheTrackMediaItems() = runTest {
        val info = NinaProtocolIE(http(transfer(releaseRoute))).extract(releaseUrl)
        assertEquals("3SvsMM3y4oTPZ5DXFJnLkCAqkxz34hjzFxqms1vu9XBJ", info.id)
        assertEquals("The Spatulas - March Chant", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("ppmrecs", info.uploader)
        assertEquals("ppm", info.channel)
        assertEquals("4ceG4zsb7VVxBTGPtZMqDZWGHo3VUg2xRvzC2b17ymWP", info.channelId)
        assertEquals("20231201", info.uploadDate)
        assertEquals("https://media.example/cover.jpg", info.thumbnails.single().url)
        assertEquals(2, info.media.size)
        assertEquals(
            "3SvsMM3y4oTPZ5DXFJnLkCAqkxz34hjzFxqms1vu9XBJ_1",
            info.media[0].mediaId,
        )
        assertEquals("March Chant In April", info.media[0].title)
        assertEquals(152.0, info.media[0].duration)
        assertEquals("https://media.example/track1.mp3", info.media[0].formats.single().url)
        assertEquals("mp3", info.media[0].formats.single().ext)
        assertEquals("none", info.media[0].formats.single().vcodec)
        assertEquals(
            "3SvsMM3y4oTPZ5DXFJnLkCAqkxz34hjzFxqms1vu9XBJ_2",
            info.media[1].mediaId,
        )
        assertEquals("Rescue Mission", info.media[1].title)
        assertEquals(212.0, info.media[1].duration)
    }

    @Test
    fun releaseIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = releaseUrl,
            infoDict = mapOf(
                "id" to Expect.Value("3SvsMM3y4oTPZ5DXFJnLkCAqkxz34hjzFxqms1vu9XBJ"),
                "title" to Expect.Value("The Spatulas - March Chant"),
                "upload_date" to Expect.Value("20231201"),
            ),
            routes = listOf(releaseRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NinaProtocolIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun releaseFallsBackToTheUrlId() = runTest {
        val noKeyRoute = FixtureRoute(
            urlPattern = "https://api.ninaprotocol.com/v1/releases/*",
            contentType = "application/json",
            body = """
                {"release": {"metadata": {"name": "Fixture Release",
                  "properties": {"files": [{"uri": "https://media.example/track.mp3",
                                            "track_title": "Fixture Track", "type": "audio/mpeg"}]}}}}
            """.trimIndent(),
        )
        val info = NinaProtocolIE(http(transfer(noKeyRoute))).extract(
            "https://www.ninaprotocol.com/releases/f-g-s-american-shield",
        )
        assertEquals("f-g-s-american-shield", info.id)
        assertEquals(1, info.media.size)
        assertEquals("f-g-s-american-shield_1", info.media[0].mediaId)
    }
}
