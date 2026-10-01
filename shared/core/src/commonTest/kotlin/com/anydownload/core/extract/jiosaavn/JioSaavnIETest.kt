package com.anydownload.core.extract.jiosaavn

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the JioSaavn subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class JioSaavnIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val songId = "rYLBEve2z3U_"

    private val songData = """
        {"id": "$songId", "song": "Fixture Song", "image": "https://media.example/song-150x150.jpg",
         "play_count": 100, "more_info": {"duration": 240, "label": "Fixture Label",
           "release_date": "2021-05-11", "encrypted_media_url": "fake_encrypted_value"}}
    """.trimIndent()

    private fun tokenRoute() = FixtureRoute(
        urlPattern = "https://www.jiosaavn.com/api.php",
        method = "POST",
        contentType = "application/json",
        body = """{"auth_url": "https://media.example/audio/song.m4a", "type": "mp4"}""",
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            JioSaavnSongIE(http(transfer())) to "https://www.jiosaavn.com/song/fixture/$songId",
            JioSaavnShowIE(http(transfer())) to "https://www.jiosaavn.com/shows/fixture/epc1hdugbka",
            JioSaavnAlbumIE(http(transfer())) to "https://www.jiosaavn.com/album/fixture/$songId",
            JioSaavnPlaylistIE(http(transfer())) to "https://www.jiosaavn.com/featured/fixture/$songId",
            JioSaavnShowPlaylistIE(http(transfer())) to "https://www.jiosaavn.com/shows/talking-music/1/$songId",
            JioSaavnArtistIE(http(transfer())) to "https://www.jiosaavn.com/artist/krsna-songs/$songId",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(JioSaavnArtistIE(http(transfer())).suitable("https://www.jiosaavn.com/album/x/y"))
    }

    // ------------------------------------------------------------------ song

    @Test
    fun songApiYieldsFormatsAndMetadata() = runTest {
        val url = "https://www.jiosaavn.com/song/fixture/$songId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.jiosaavn.com/api.php?__call=webapi.get*type=song",
                contentType = "application/json",
                body = """{"songs": [$songData]}""",
            ),
            tokenRoute(),
        )
        val info = JioSaavnSongIE(http(transfer)).extract(url)
        assertEquals(songId, info.id)
        assertEquals("Fixture Song", info.title)
        assertEquals(240.0, info.duration)
        assertEquals("Fixture Label", info.channel)
        assertEquals("20210511", info.uploadDate)
        assertEquals("https://media.example/song-500x500.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("m4a", info.formats[0].ext)
        assertEquals(128.0, info.formats[0].abr)
    }

    // ----------------------------------------------------------------- album

    @Test
    fun albumApiYieldsEntries() = runTest {
        val url = "https://www.jiosaavn.com/album/fixture/$songId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.jiosaavn.com/api.php?__call=webapi.get*type=album",
                contentType = "application/json",
                body = """
                    {"title": "Fixture Album",
                     "songs": [{"id": "song-1", "song": "One",
                                "perma_url": "https://www.jiosaavn.com/song/one/song-1"}]}
                """.trimIndent(),
            ),
        )
        val info = JioSaavnAlbumIE(http(transfer)).extract(url)
        assertEquals(songId, info.id)
        assertEquals("Fixture Album", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.jiosaavn.com/song/one/song-1", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun songIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.jiosaavn.com/song/fixture/$songId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(songId),
                "title" to Expect.Value("Fixture Song"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://www.jiosaavn.com/api.php?__call=webapi.get*type=song",
                    contentType = "application/json",
                    body = """{"songs": [$songData]}""",
                ),
                tokenRoute(),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> JioSaavnSongIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
