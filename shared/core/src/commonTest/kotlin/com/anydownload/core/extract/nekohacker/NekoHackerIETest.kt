package com.anydownload.core.extract.nekohacker

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
 * Fixture cases for the NekoHacker subset. Ids and media paths are synthesized
 * on `media.example`; the track ids are fake values. No cookie, token, or
 * signed URL appears.
 */
class NekoHackerIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val albumUrl = "https://nekohacker.com/nekoverse/"

    private val albumPage = FixtureRoute(
        urlPattern = "https://nekohacker.com/nekoverse/",
        contentType = "text/html",
        body = """
            <html><body>
            <script>var srp_player_params_ab12 = {"artwork": "https://media.example/artwork.jpg"};</script>
            <ul class="playlist">
              <li data-audiopath="https://media.example/01-Spaceship.mp3" data-trackid="1712"
                  data-tracktitle="Spaceship" data-albumtitle="Nekoverse"
                  data-tracktime="3:15" data-releasedate="2022.11.01"></li>
              <li data-audiopath="https://media.example/02-City-Runner.mp3" data-trackid="1713"
                  data-tracktitle="City Runner" data-albumtitle="Nekoverse"
                  data-tracktime="2:28" data-releasedate="2022.11.01"></li>
            </ul>
            </body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val ie = NekoHackerIE(http(transfer()))
        assertTrue(ie.suitable(albumUrl))
        assertTrue(ie.suitable("https://www.nekohacker.com/susume/"))
        assertFalse(ie.suitable("https://nekohacker.com/free-dl/some-track"))
        assertFalse(ie.suitable("https://www.example.com/nekoverse/"))
    }

    // ------------------------------------------------------------- playlist

    @Test
    fun playlistYieldsTheTrackMediaItems() = runTest {
        val info = NekoHackerIE(http(transfer(albumPage))).extract(albumUrl)
        assertEquals("nekoverse", info.id)
        assertEquals("Nekoverse", info.title)
        assertEquals(2, info.media.size)
        assertEquals("1712", info.media[0].mediaId)
        assertEquals("Spaceship", info.media[0].title)
        assertEquals(195.0, info.media[0].duration)
        assertEquals("https://media.example/artwork.jpg", info.media[0].thumbnails.single().url)
        assertEquals("https://media.example/01-Spaceship.mp3", info.media[0].formats.single().url)
        assertEquals("mp3", info.media[0].formats.single().ext)
        assertEquals("none", info.media[0].formats.single().vcodec)
        assertEquals("mp3", info.media[0].formats.single().acodec)
        assertEquals("1713", info.media[1].mediaId)
        assertEquals(148.0, info.media[1].duration)
    }

    @Test
    fun playlistIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = albumUrl,
            infoDict = mapOf(
                "id" to Expect.Value("nekoverse"),
                "title" to Expect.Value("Nekoverse"),
            ),
            routes = listOf(albumPage),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NekoHackerIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    // ---------------------------------------------------------------- embeds

    @Test
    fun spotifyEmbedFailsTyped() = runTest {
        val page = FixtureRoute(
            urlPattern = "https://nekohacker.com/nekoverse/",
            contentType = "text/html",
            body = """
                <html><body><iframe src="https://open.spotify.com/embed/album/abc"></iframe></body></html>
            """.trimIndent(),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            NekoHackerIE(http(transfer(page))).extract(albumUrl)
        }
        assertTrue(error.message!!.contains("Spotify embeds are not supported"), error.message)
    }

    @Test
    fun otherEmbedYieldsOneChildEntry() = runTest {
        val page = FixtureRoute(
            urlPattern = "https://nekohacker.com/nekoverse/",
            contentType = "text/html",
            body = """
                <html><body><iframe src="https://player.example/embed/abc"></iframe></body></html>
            """.trimIndent(),
        )
        val info = NekoHackerIE(http(transfer(page))).extract(albumUrl)
        assertEquals(1, info.entries.size)
        assertEquals("https://player.example/embed/abc", info.entries[0].url)
    }

    @Test
    fun pageWithoutPlaylistOrEmbedFailsTyped() = runTest {
        val page = FixtureRoute(
            urlPattern = "https://nekohacker.com/nekoverse/",
            contentType = "text/html",
            body = """<html><body><p>Nothing here.</p></body></html>""",
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            NekoHackerIE(http(transfer(page))).extract(albumUrl)
        }
        assertTrue(error.message!!.contains("No playlist or embed"), error.message)
    }
}
