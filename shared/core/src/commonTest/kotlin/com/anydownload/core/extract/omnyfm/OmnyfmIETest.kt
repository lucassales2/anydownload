package com.anydownload.core.extract.omnyfm

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
 * Fixture cases for the Omny Studio subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class OmnyfmIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val clipUrl = "https://omny.fm/shows/sleep-hub/cannabinoids-and-sleep"
    private val playlistUrl = "https://omny.fm/shows/sleep-hub/playlists/sleep-talk"
    private val listUrl = "https://omny.fm/shows/bayfm-program03/playlists"
    private val showUrl = "https://omny.fm/shows/the-origin-of-things"

    private fun page(props: String) =
        "<html><body><script id=\"__NEXT_DATA__\" type=\"application/json\">{\"props\":{\"pageProps\":$props}}</script></body></html>"

    private val clipPage = page(
        """
        {"clip": {"Title": "<p>Fixture Clip</p>", "Description": "Fixture description",
          "DurationSeconds": 1840.274, "Episode": 48, "Season": 3,
          "PublishedAudioSizeInBytes": 12345, "ModifiedAtUtc": "2024-01-01T00:00:00Z",
          "ImageUrl": "https://www.omnycontent.com/img.jpg?t=1",
          "PublishedUtc": "2019-11-17T00:00:00Z", "AudioUrl": "https://media.example/audio.mp3",
          "PublishedUrl": "https://omny.fm/shows/sleep-hub/cannabinoids-and-sleep",
          "Chapters": [{"Position": "00:00", "Name": "Introduction"},
                       {"Position": "02:18", "Name": "Theme"}],
          "Program": {"Name": "Sleep Talk", "Categories": ["Health"]}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val clip = OmnyfmIE(http(transfer()))
        assertTrue(clip.suitable(clipUrl))
        assertTrue(clip.suitable("https://omny.fm/shows/the-origin-of-things/a-song-of-hope/embed"))
        assertFalse(clip.suitable(showUrl))
        assertFalse(clip.suitable("https://www.example.com/shows/x/y"))

        val playlist = OmnyfmPlaylistIE(http(transfer()))
        assertTrue(playlist.suitable(playlistUrl))
        assertTrue(playlist.suitable(listUrl))

        val show = OmnyfmShowIE(http(transfer()))
        assertTrue(show.suitable(showUrl))
        assertFalse(show.suitable(clipUrl))
    }

    // ------------------------------------------------------------------- clip

    @Test
    fun clipYieldsTheMp3RowAndChapters() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = "$clipUrl*", contentType = "text/html", body = clipPage))
        val info = OmnyfmIE(http(transfer)).extract(clipUrl)
        assertEquals("cannabinoids-and-sleep", info.id)
        assertEquals("Fixture Clip", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(1840.274, info.duration)
        assertEquals("20191117", info.uploadDate)
        assertEquals("Sleep Talk", info.uploader)
        assertEquals("sleep-hub", info.channelId)
        assertEquals("https://www.omnycontent.com/img.jpg", info.thumbnails.single().url)
        assertEquals(2, info.chapters.size)
        assertEquals(0.0, info.chapters[0].startTime)
        assertEquals("Theme", info.chapters[1].title)
        assertEquals(138.0, info.chapters[1].startTime)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/audio.mp3", info.formats[0].url)
        assertEquals("none", info.formats[0].vcodec)
        assertEquals(clipUrl, info.webpageUrl)
    }

    // --------------------------------------------------------------- playlist

    @Test
    fun playlistYieldsThePagedClips() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$playlistUrl*",
                contentType = "text/html",
                body = page(
                    """
                    {"playlist": {"Title": "Sleep Talk", "Description": "Fixture",
                      "ArtworkUrl": "https://www.omnycontent.com/a.jpg?x=1",
                      "EmbedUrl": "https://omny.fm/shows/sleep-hub/playlists/sleep-talk/embed"}}
                    """.trimIndent(),
                ),
            ),
            FixtureRoute(
                urlPattern = "https://api.omny.fm/programs/sleep-hub/playlists/sleep-talk/clips?direction=AfterExclusive&pageSize=100",
                contentType = "application/json",
                body = """{"Clips": [{"Slug": "clip-1"}, {"Slug": "clip-2"}], "NextClipsAvailable": false}""",
            ),
        )
        val info = OmnyfmPlaylistIE(http(transfer)).extract(playlistUrl)
        assertEquals("sleep-talk", info.id)
        assertEquals("Sleep Talk", info.title)
        assertEquals("https://www.omnycontent.com/a.jpg", info.thumbnails.single().url)
        assertEquals(2, info.entries.size)
        assertEquals("https://omny.fm/shows/sleep-hub/clip-1", info.entries[0].url)
        assertEquals("https://omny.fm/shows/sleep-hub/playlists/sleep-talk", info.webpageUrl)
    }

    @Test
    fun playlistListYieldsThePlaylistEntries() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$listUrl*",
                contentType = "text/html",
                body = page(
                    """
                    {"playlistsWithClips": [{"playlist": {"Slug": "p1"}}, {"playlist": {"Slug": "p2"}}]}
                    """.trimIndent(),
                ),
            ),
        )
        val info = OmnyfmPlaylistIE(http(transfer)).extract(listUrl)
        assertEquals("bayfm-program03", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("https://omny.fm/shows/bayfm-program03/playlists/p2", info.entries[1].url)
    }

    // ------------------------------------------------------------------- show

    @Test
    fun showYieldsThePagedClips() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$showUrl*",
                contentType = "text/html",
                body = page(
                    """
                    {"program": {"Name": "The Origin Of Things", "Description": "Fixture",
                      "ArtworkUrl": "https://www.omnycontent.com/p.jpg?x=1",
                      "OrganizationId": "org1", "Id": "prog1"}}
                    """.trimIndent(),
                ),
            ),
            FixtureRoute(
                urlPattern = "https://api.omny.fm/orgs/org1/programs/prog1/clips?cursor=0&pageSize=100",
                contentType = "application/json",
                body = """{"Clips": [{"Slug": "c1"}]}""",
            ),
        )
        val info = OmnyfmShowIE(http(transfer)).extract(showUrl)
        assertEquals("the-origin-of-things", info.id)
        assertEquals("The Origin Of Things", info.title)
        assertEquals("https://www.omnycontent.com/p.jpg", info.thumbnails.single().url)
        assertEquals(1, info.entries.size)
        assertEquals("https://omny.fm/shows/the-origin-of-things/c1", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun clipIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = clipUrl,
            infoDict = mapOf(
                "id" to Expect.Value("cannabinoids-and-sleep"),
                "title" to Expect.Value("Fixture Clip"),
                "upload_date" to Expect.Value("20191117"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(FixtureRoute(urlPattern = "$clipUrl*", contentType = "text/html", body = clipPage)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> OmnyfmIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun playlistIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = playlistUrl,
            infoDict = mapOf(
                "id" to Expect.Value("sleep-talk"),
                "title" to Expect.Value("Sleep Talk"),
                "entries" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "$playlistUrl*",
                    contentType = "text/html",
                    body = page(
                        """
                        {"playlist": {"Title": "Sleep Talk", "Description": "Fixture",
                          "ArtworkUrl": "https://www.omnycontent.com/a.jpg?x=1",
                          "EmbedUrl": "https://omny.fm/shows/sleep-hub/playlists/sleep-talk/embed"}}
                        """.trimIndent(),
                    ),
                ),
                FixtureRoute(
                    urlPattern = "https://api.omny.fm/programs/sleep-hub/playlists/sleep-talk/clips?direction=AfterExclusive&pageSize=100",
                    contentType = "application/json",
                    body = """{"Clips": [{"Slug": "clip-1"}, {"Slug": "clip-2"}], "NextClipsAvailable": false}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> OmnyfmPlaylistIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
