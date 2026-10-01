package com.anydownload.core.extract.idagio

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
 * Fixture cases for the IDAGIO subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class IdagioIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val trackUrl = "https://app.idagio.com/recordings/30576934?trackId=30576943"
    private val recordingUrl = "https://app.idagio.com/recordings/30576934"
    private val albumUrl = "https://app.idagio.com/albums/elgar-fixture"
    private val playlistUrl = "https://app.idagio.com/playlists/beethoven-fixture"
    private val personalUrl = "https://app.idagio.com/playlists/personal/99dad72e-7b3a-45a4-b216-867c08046ed8"

    private fun tracks(vararg ids: String) = ids.joinToString(",") { """{"id": "$it", "recording": {"id": "r$it"}}""" }

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val track = IdagioTrackIE(http(transfer()))
        assertTrue(track.suitable(trackUrl))
        assertTrue(track.suitable("https://app.idagio.com/de/recordings/20514467?trackId=20514478&utm_source=pcl"))
        assertFalse(track.suitable(recordingUrl))

        val recording = IdagioRecordingIE(http(transfer()))
        assertTrue(recording.suitable(recordingUrl))
        assertTrue(recording.suitable("https://app.idagio.com/de/recordings/20514467"))
        assertFalse(recording.suitable(trackUrl))

        assertTrue(IdagioAlbumIE(http(transfer())).suitable(albumUrl))
        assertTrue(IdagioPlaylistIE(http(transfer())).suitable(playlistUrl))
        assertTrue(IdagioPersonalPlaylistIE(http(transfer())).suitable(personalUrl))
        assertFalse(IdagioPlaylistIE(http(transfer())).suitable(personalUrl))
        assertFalse(IdagioAlbumIE(http(transfer())).suitable("https://www.example.com/albums/fixture"))
    }

    // ------------------------------------------------------------------ track

    @Test
    fun trackYieldsTheMp3Row() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.idagio.com/v2.0/metadata/tracks/30576943",
                contentType = "application/json",
                body = """
                    {"result": {"piece": {"title": "Theme. Andante"}, "duration": 82,
                     "recording": {"created_at": 1554474370000}}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://api.idagio.com/v1.8/content/track/30576943*",
                contentType = "application/json",
                body = """{"url": "https://media.example/track.mp3"}""",
            ),
        )
        val info = IdagioTrackIE(http(transfer)).extract(trackUrl)
        assertEquals("30576943", info.id)
        assertEquals("Theme. Andante", info.title)
        assertEquals(82.0, info.duration)
        assertEquals("20190405", info.uploadDate)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/track.mp3", info.formats[0].url)
        assertEquals("mp3", info.formats[0].ext)
        assertEquals("none", info.formats[0].vcodec)
    }

    @Test
    fun locationBlockedTrackFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.idagio.com/v2.0/metadata/tracks/30576943",
                contentType = "application/json",
                body = """{"error_code": "idagio.error.blocked.location"}""",
            ),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            IdagioTrackIE(http(transfer)).extract(trackUrl)
        }
    }

    // --------------------------------------------------------------- playlists

    @Test
    fun recordingYieldsTheTrackEntries() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.idagio.com/v2.0/metadata/recordings/30576934",
                contentType = "application/json",
                body = """
                    {"result": {"work": {"title": "Variations on an Original Theme"},
                     "created_at": 1554474370000, "tracks": [${tracks("1", "2")}]}}
                """.trimIndent(),
            ),
        )
        val info = IdagioRecordingIE(http(transfer)).extract(recordingUrl)
        assertEquals("30576934", info.id)
        assertEquals("Variations on an Original Theme", info.title)
        assertEquals("20190405", info.uploadDate)
        assertEquals(2, info.entries.size)
        assertEquals("https://app.idagio.com/recordings/r1?trackId=1", info.entries[0].url)
    }

    @Test
    fun albumYieldsTheMetadataAndEntries() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.idagio.com/v2.0/metadata/albums/elgar-fixture",
                contentType = "application/json",
                body = """
                    {"result": {"id": "a9f139b8-f70d-4b8a-a9a4-5fe8d35eaf9c", "title": "Elgar Fixture",
                     "publishDate": "2019-03-29T00:00:00Z", "imageUrl": "https://media.example/album.jpg",
                     "description": "Fixture album", "tracks": [${tracks("10")}]}}
                """.trimIndent(),
            ),
        )
        val info = IdagioAlbumIE(http(transfer)).extract(albumUrl)
        assertEquals("a9f139b8-f70d-4b8a-a9a4-5fe8d35eaf9c", info.id)
        assertEquals("Elgar Fixture", info.title)
        assertEquals("Fixture album", info.description)
        assertEquals("20190329", info.uploadDate)
        assertEquals("https://media.example/album.jpg", info.thumbnails.single().url)
        assertEquals(1, info.entries.size)
    }

    @Test
    fun editorialPlaylistYieldsTheMetadataAndEntries() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.idagio.com/v2.0/playlists/beethoven-fixture",
                contentType = "application/json",
                body = """
                    {"result": {"id": "31652bec-8c5b-460e-a3f0-cf1f69817f53",
                     "title": "Beethoven Fixture", "imageUrl": "https://media.example/playlist.jpg",
                     "description": "Fixture playlist", "tracks": [${tracks("20")}]}}
                """.trimIndent(),
            ),
        )
        val info = IdagioPlaylistIE(http(transfer)).extract(playlistUrl)
        assertEquals("31652bec-8c5b-460e-a3f0-cf1f69817f53", info.id)
        assertEquals("Beethoven Fixture", info.title)
        assertEquals("Fixture playlist", info.description)
        assertEquals(1, info.entries.size)
    }

    @Test
    fun personalPlaylistYieldsTheMetadataAndEntries() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.idagio.com/v1.0/personal-playlists/99dad72e-7b3a-45a4-b216-867c08046ed8",
                contentType = "application/json",
                body = """
                    {"result": {"title": "Test", "image_url": "https://media.example/personal.jpg",
                     "created_at": 1602859138000, "tracks": [${tracks("7")}]}}
                """.trimIndent(),
            ),
        )
        val info = IdagioPersonalPlaylistIE(http(transfer)).extract(personalUrl)
        assertEquals("99dad72e-7b3a-45a4-b216-867c08046ed8", info.id)
        assertEquals("Test", info.title)
        assertEquals("20201016", info.uploadDate)
        assertEquals("https://media.example/personal.jpg", info.thumbnails.single().url)
        assertEquals(1, info.entries.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun trackIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = trackUrl,
            infoDict = mapOf(
                "id" to Expect.Value("30576943"),
                "title" to Expect.Value("Theme. Andante"),
                "upload_date" to Expect.Value("20190405"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api.idagio.com/v2.0/metadata/tracks/30576943",
                    contentType = "application/json",
                    body = """
                        {"result": {"piece": {"title": "Theme. Andante"}, "duration": 82,
                         "recording": {"created_at": 1554474370000}}}
                    """.trimIndent(),
                ),
                FixtureRoute(
                    urlPattern = "https://api.idagio.com/v1.8/content/track/30576943*",
                    contentType = "application/json",
                    body = """{"url": "https://media.example/track.mp3"}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> IdagioTrackIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun albumIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = albumUrl,
            infoDict = mapOf(
                "id" to Expect.Value("a9f139b8-f70d-4b8a-a9a4-5fe8d35eaf9c"),
                "title" to Expect.Value("Elgar Fixture"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api.idagio.com/v2.0/metadata/albums/elgar-fixture",
                    contentType = "application/json",
                    body = """
                        {"result": {"id": "a9f139b8-f70d-4b8a-a9a4-5fe8d35eaf9c", "title": "Elgar Fixture",
                         "publishDate": "2019-03-29T00:00:00Z", "imageUrl": "https://media.example/album.jpg",
                         "description": "Fixture album", "tracks": [${tracks("10")}]}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> IdagioAlbumIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
