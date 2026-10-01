package com.anydownload.core.extract.bandcamp

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
 * Fixture cases for the Bandcamp subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or signed URL
 * appears.
 */
class BandcampIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val trackUrl = "https://fixtureartist.bandcamp.com/track/fixture-track"
    private val trackPage = """
        <html><head>
        <meta property="og:image" content="https://f4.bcbits.com/img/fake.jpg">
        <meta name="duration" content="260.877">
        </head><body>
        <script data-tralbum='{"id": 2650410135,
          "artist": "Fixture Artist",
          "trackinfo": [{"title": "Fixture Track", "track_id": 2650410135, "track_num": 1,
                         "duration": 260.877,
                         "file": {"mp3-128": "//media.example/audio/fixture-128.mp3",
                                  "m4a-192": "//media.example/audio/fixture-192.m4a"}}],
          "current": {"publish_date": "3 Apr 2014 00:00:00 GMT"}}'></script>
        <script data-embed='{"artist": "Fixture Artist", "album_title": "Fixture Album"}'></script>
        </body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            BandcampIE(http(transfer())) to trackUrl,
            BandcampAlbumIE(http(transfer())) to "https://fixtureartist.bandcamp.com/album/fixture-album",
            BandcampWeeklyIE(http(transfer())) to "https://bandcamp.com/radio?show=224",
            BandcampUserIE(http(transfer())) to "https://fixtureartist.bandcamp.com",
            BandcampUserIE(http(transfer())) to "https://fixtureartist.bandcamp.com/music",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(BandcampIE(http(transfer())).suitable("https://fixtureartist.bandcamp.com/album/x"))
        assertFalse(BandcampUserIE(http(transfer())).suitable("https://www.bandcamp.com"))
    }

    // ------------------------------------------------------------------ track

    @Test
    fun trackPageYieldsTheFileMapFormats() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = trackUrl, contentType = "text/html", body = trackPage))
        val info = BandcampIE(http(transfer)).extract(trackUrl)
        assertEquals("2650410135", info.id)
        assertEquals("Fixture Artist - Fixture Track", info.title)
        assertEquals("Fixture Artist", info.uploader)
        assertEquals(260.877, info.duration)
        assertEquals("20140403", info.uploadDate)
        assertEquals(2, info.formats.size)
        val mp3 = info.formats.first { it.formatId == "mp3-128" }
        assertEquals("mp3", mp3.ext)
        assertEquals(128.0, mp3.abr)
        assertEquals("https://media.example/audio/fixture-128.mp3", mp3.url)
    }

    @Test
    fun trackWithoutTralbumFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = trackUrl, contentType = "text/html", body = "<html></html>"),
        )
        assertFailsWith<ExtractionError.Malformed> {
            BandcampIE(http(transfer)).extract(trackUrl)
        }
    }

    // ------------------------------------------------------------------ album

    @Test
    fun albumPageListsOnlyTracksWithDuration() = runTest {
        val url = "https://fixtureartist.bandcamp.com/album/fixture-album"
        val page = """
            <html><body>
            <script data-tralbum='{"id": 111,
              "trackinfo": [
                {"title": "One", "track_id": 1, "duration": 19.335, "title_link": "/track/one"},
                {"title": "Two", "track_id": 2, "title_link": "/track/two"}],
              "current": {"title": "Fixture Album", "about": "Fixture about"}}'></script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = BandcampAlbumIE(http(transfer)).extract(url)
        assertEquals("fixture-album", info.id)
        assertEquals("Fixture Album", info.title)
        assertEquals("Fixture about", info.description)
        assertEquals(1, info.entries.size)
        assertEquals("https://fixtureartist.bandcamp.com/track/one", info.entries.single().url)
    }

    // ----------------------------------------------------------------- weekly

    @Test
    fun weeklyShowPostsTheItemId() = runTest {
        val url = "https://bandcamp.com/radio?show=224"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://bandcamp.com/api/player/2/player_data_web",
                method = "POST",
                contentType = "application/json",
                body = """
                    {"tracklist": {"subtitle": "Bandcamp Weekly", "title": "Fixture Episode",
                     "description": "Fixture description", "date": "4 Apr 2017 00:00:00 GMT",
                     "imageId": "9982549",
                     "compiledTrack": {"streamUrl": "https://media.example/radio/show.mp3?enc=mp3-128",
                                       "duration": 5829.77}}}
                """.trimIndent(),
            ),
        )
        val info = BandcampWeeklyIE(http(transfer)).extract(url)
        assertEquals("224", info.id)
        assertEquals("Bandcamp Weekly, 2017-04-04", info.title)
        assertEquals("20170404", info.uploadDate)
        assertEquals("mp3", info.formats.single().ext)
        assertEquals(128.0, info.formats.single().abr)
        assertEquals("POST", transfer.requests.first { it.url.contains("player_data_web") }.method)
    }

    // ------------------------------------------------------------------- user

    @Test
    fun userPageScansItemsAndTheMusicGrid() = runTest {
        val url = "https://fixtureartist.bandcamp.com"
        val page = """
            <html><body>
            <li data-item-id="1"><a href="/album/fixture-one">One</a></li>
            <div id="music-grid" data-client-items='[{"page_url": "/album/fixture-two"}]'></div>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = BandcampUserIE(http(transfer)).extract(url)
        assertEquals("fixtureartist", info.id)
        assertEquals("Discography of fixtureartist", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://fixtureartist.bandcamp.com/album/fixture-two", info.entries[1].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun trackPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = trackUrl,
            infoDict = mapOf(
                "id" to Expect.Value("2650410135"),
                "title" to Expect.Value("Fixture Artist - Fixture Track"),
                "formats" to Expect.Count(2),
                "formats.0.ext" to Expect.Value("mp3"),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = trackUrl, contentType = "text/html", body = trackPage),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> BandcampIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun albumPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://fixtureartist.bandcamp.com/album/fixture-album"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("fixture-album"),
                "title" to Expect.Value("Fixture Album"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><body><script data-tralbum='{"id": 111,
                          "trackinfo": [{"title": "One", "track_id": 1, "duration": 19.335, "title_link": "/track/one"}],
                          "current": {"title": "Fixture Album"}}'></script></body></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> BandcampAlbumIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
