package com.anydownload.core.extract.mixcloud

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
 * Fixture cases for the Mixcloud subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and the stream URLs are
 * XOR/base64 ciphertext (the upstream public constant key), never signed
 * URLs.
 */
class MixcloudIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // XOR(key, "https://media.example/hls/master.m3u8") then base64.
    private val encryptedHls = "ITItPyZtbmE5MSwsIHwxMTI5IzgqaC04I24kJTc7Kz16KXwidg=="
    // XOR(key, "https://media.example/audio/stream.m4a") then base64.
    private val encryptedHttp = "ITItPyZtbmE5MSwsIHwxMTI5IzgqaCQhNCgmazc7PCo1KWE6ei0="

    private fun cloudcastRoute() = FixtureRoute(
        urlPattern = "https://app.mixcloud.com/graphql?query=*cloudcastLookup*",
        contentType = "application/json",
        body = """
            {"data": {"cloudcastLookup": {"name": "Fixture Cloudcast",
              "description": "Fixture description", "audioLength": 3723,
              "publishDate": "2011-11-15T00:00:00Z", "plays": 42, "isExclusive": false,
              "owner": {"displayName": "Fixture Uploader", "username": "fixtureuser",
                        "url": "https://www.mixcloud.com/fixtureuser/"},
              "picture": {"url": "https://media.example/thumb.jpg"},
              "streamInfo": {"url": "$encryptedHttp", "hlsUrl": "$encryptedHls"}}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            MixcloudIE(http(transfer())) to "https://www.mixcloud.com/dholbach/cryptkeeper/",
            MixcloudIE(http(transfer())) to "https://beta.mixcloud.com/RedLightRadio/fixture-mix/",
            MixcloudUserIE(http(transfer())) to "https://www.mixcloud.com/dholbach/",
            MixcloudUserIE(http(transfer())) to "https://www.mixcloud.com/dholbach/uploads/",
            MixcloudPlaylistIE(http(transfer())) to "https://www.mixcloud.com/maxvibes/playlists/fixture-playlist/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(MixcloudIE(http(transfer())).suitable("https://www.mixcloud.com/dholbach/uploads/"))
    }

    // -------------------------------------------------------------- cloudcast

    @Test
    fun cloudcastYieldsDecryptedFormats() = runTest {
        val url = "https://www.mixcloud.com/dholbach/cryptkeeper/"
        val transfer = transfer(cloudcastRoute())
        val info = MixcloudIE(http(transfer)).extract(url)
        assertEquals("dholbach_cryptkeeper", info.id)
        assertEquals("Fixture Cloudcast", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(3723.0, info.duration)
        assertEquals("20111115", info.uploadDate)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals(42L, info.viewCount)
        assertEquals(2, info.formats.size)
        assertEquals("https://media.example/audio/stream.m4a", info.formats[0].url)
        assertEquals("https://media.example/hls/master.m3u8", info.formats[1].url)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    @Test
    fun exclusiveTrackFailsTyped() = runTest {
        val url = "https://www.mixcloud.com/dholbach/cryptkeeper/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://app.mixcloud.com/graphql?query=*cloudcastLookup*",
                contentType = "application/json",
                body = """{"data": {"cloudcastLookup": {"name": "Fixture", "isExclusive": true, "streamInfo": {}}}}""",
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            MixcloudIE(http(transfer)).extract(url)
        }
    }

    // ------------------------------------------------------------------ user

    @Test
    fun userListingYieldsEntries() = runTest {
        val url = "https://www.mixcloud.com/dholbach/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://app.mixcloud.com/graphql?query=*userLookup*",
                contentType = "application/json",
                body = """
                    {"data": {"userLookup": {"displayName": "Daniel Holbach", "biog": "Fixture biog",
                      "uploads": {"edges": [{"node": {"slug": "cryptkeeper",
                        "url": "https://www.mixcloud.com/dholbach/cryptkeeper/",
                        "owner": {"username": "dholbach"}}}],
                        "pageInfo": {"endCursor": "cursor-1", "hasNextPage": false}}}}}
                """.trimIndent(),
            ),
        )
        val info = MixcloudUserIE(http(transfer)).extract(url)
        assertEquals("dholbach_uploads", info.id)
        assertEquals("Daniel Holbach (uploads)", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.mixcloud.com/dholbach/cryptkeeper/", info.entries.single().url)
    }

    // -------------------------------------------------------------- playlist

    @Test
    fun playlistListingYieldsEntries() = runTest {
        val url = "https://www.mixcloud.com/maxvibes/playlists/fixture-playlist/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://app.mixcloud.com/graphql?query=*playlistLookup*",
                contentType = "application/json",
                body = """
                    {"data": {"playlistLookup": {"name": "Ness Radio sessions",
                      "description": "Fixture description",
                      "items": {"edges": [{"node": {"cloudcast": {"slug": "episode-1",
                        "url": "https://www.mixcloud.com/maxvibes/episode-1/",
                        "owner": {"username": "maxvibes"}}}}],
                        "pageInfo": {"endCursor": "cursor-1", "hasNextPage": false}}}}}
                """.trimIndent(),
            ),
        )
        val info = MixcloudPlaylistIE(http(transfer)).extract(url)
        assertEquals("maxvibes_fixture-playlist", info.id)
        assertEquals("Ness Radio sessions", info.title)
        assertEquals(1, info.entries.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun cloudcastIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.mixcloud.com/dholbach/cryptkeeper/"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("dholbach_cryptkeeper"),
                "title" to Expect.Value("Fixture Cloudcast"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(cloudcastRoute()),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MixcloudIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
