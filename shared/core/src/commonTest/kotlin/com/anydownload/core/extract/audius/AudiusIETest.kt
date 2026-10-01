package com.anydownload.core.extract.audius

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
 * Fixture cases for the Audius subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class AudiusIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val trackUrl = "https://audius.co/voltra/radar-103692"
    private val playlistUrl = "https://audius.co/test_acc/playlist/test-playlist-22910"
    private val profileUrl = "https://audius.co/pzl/"

    private val hostsRoute = FixtureRoute(
        urlPattern = "https://api.audius.co/",
        contentType = "application/json",
        body = """{"data": ["https://api1.example"]}""",
    )

    private val trackData = """
        {"id": "KKdy2", "title": "RADAR", "description": "Fixture description", "duration": 318,
         "genre": "Trance", "play_count": 10, "favorite_count": 2, "repost_count": 1,
         "artwork": {"150x150": "https://media.example/150.jpg",
                     "480x480": "https://media.example/480.jpg"},
         "user": {"name": "voltra"}}
    """.trimIndent()

    private val resolveRoute = FixtureRoute(
        urlPattern = "https://api1.example/v1/resolve*",
        contentType = "application/json",
        body = """{"data": $trackData}""",
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val track = AudiusIE(http(transfer()))
        assertTrue(track.suitable(trackUrl))
        assertFalse(track.suitable(playlistUrl))
        assertFalse(track.suitable(profileUrl))

        val trackId = AudiusTrackIE(http(transfer()))
        assertTrue(trackId.suitable("audius:9RWlo"))
        assertTrue(trackId.suitable("audius:http://discovery.example/v1/tracks/9RWlo"))

        val playlist = AudiusPlaylistIE(http(transfer()))
        assertTrue(playlist.suitable(playlistUrl))
        assertTrue(playlist.suitable("https://audius.co/test_acc/album/test-album-1"))

        val profile = AudiusProfileIE(http(transfer()))
        assertTrue(profile.suitable(profileUrl))
        assertFalse(profile.suitable(trackUrl))
        assertFalse(profile.suitable("https://www.example.com/x/y"))
    }

    // ----------------------------------------------------------------- track

    @Test
    fun trackPageYieldsTheStreamRowAndMetadata() = runTest {
        val info = AudiusIE(http(transfer(hostsRoute, resolveRoute))).extract(trackUrl)
        assertEquals("KKdy2", info.id)
        assertEquals("RADAR", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(318.0, info.duration)
        assertEquals("voltra", info.uploader)
        assertEquals(10L, info.viewCount)
        assertEquals(2, info.thumbnails.size)
        assertEquals(150, info.thumbnails[0].preference)
        assertEquals(480, info.thumbnails[1].preference)
        assertEquals(1, info.formats.size)
        assertEquals("https://api1.example/v1/tracks/KKdy2/stream", info.formats[0].url)
        assertEquals("mp3", info.formats[0].ext)
    }

    @Test
    fun trackSchemeYieldsTheStreamRow() = runTest {
        val transfer = transfer(
            hostsRoute,
            FixtureRoute(
                urlPattern = "https://api1.example/v1/tracks/9RWlo",
                contentType = "application/json",
                body = """{"data": $trackData}""",
            ),
        )
        val info = AudiusTrackIE(http(transfer)).extract("audius:9RWlo")
        assertEquals("KKdy2", info.id)
        assertEquals("RADAR", info.title)
        assertEquals("https://api1.example/v1/tracks/KKdy2/stream", info.formats.single().url)
    }

    // -------------------------------------------------------------- playlist

    @Test
    fun playlistYieldsTheTrackEntries() = runTest {
        val transfer = transfer(
            hostsRoute,
            FixtureRoute(
                urlPattern = "https://api1.example/v1/resolve*",
                contentType = "application/json",
                body = """
                    {"data": [{"id": "DNvjN", "playlist_name": "test playlist",
                      "description": "Fixture description"}]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://api1.example/v1/playlists/DNvjN/tracks",
                contentType = "application/json",
                body = """{"data": [{"id": "t1"}, {"id": "t2"}]}""",
            ),
        )
        val info = AudiusPlaylistIE(http(transfer)).extract(playlistUrl)
        assertEquals("DNvjN", info.id)
        assertEquals("test playlist", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(2, info.entries.size)
        assertEquals("audius:t1", info.entries[0].url)
        assertEquals("audius:t2", info.entries[1].url)
    }

    // --------------------------------------------------------------- profile

    @Test
    fun profileYieldsTheTrackEntries() = runTest {
        val transfer = transfer(
            hostsRoute,
            FixtureRoute(
                urlPattern = "https://api1.example/v1/full/users/handle/pzl",
                contentType = "application/json",
                body = """{"data": [{"id": "ezRo7", "bio": "Fixture bio"}]}""",
            ),
            FixtureRoute(
                urlPattern = "https://api1.example/v1/full/users/handle/pzl/tracks",
                contentType = "application/json",
                body = """{"data": [{"id": "t1"}]}""",
            ),
        )
        val info = AudiusProfileIE(http(transfer)).extract(profileUrl)
        assertEquals("ezRo7", info.id)
        assertEquals("pzl", info.title)
        assertEquals("Fixture bio", info.description)
        assertEquals(1, info.entries.size)
        assertEquals("audius:t1", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun trackPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = trackUrl,
            infoDict = mapOf(
                "id" to Expect.Value("KKdy2"),
                "title" to Expect.Value("RADAR"),
                "duration" to Expect.Value(318.0),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(hostsRoute, resolveRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> AudiusIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun trackSchemeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "audius:9RWlo",
            infoDict = mapOf(
                "id" to Expect.Value("KKdy2"),
                "title" to Expect.Value("RADAR"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                hostsRoute,
                FixtureRoute(
                    urlPattern = "https://api1.example/v1/tracks/9RWlo",
                    contentType = "application/json",
                    body = """{"data": $trackData}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> AudiusTrackIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
