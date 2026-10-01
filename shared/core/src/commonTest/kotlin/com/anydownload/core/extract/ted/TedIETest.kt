package com.anydownload.core.extract.ted

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
 * Fixture cases for the TED subset. Ids and media paths are synthesized on
 * `media.example`; the `__NEXT_DATA__` payloads are inline fixtures. No
 * cookie, token, or signed URL appears.
 */
class TedIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val talkUrl =
        "https://www.ted.com/talks/candace_parker_how_to_break_down_barriers_and_not_accept_limits"
    private val seriesUrl = "https://www.ted.com/series/small_thing_big_idea"
    private val playlistUrl = "https://www.ted.com/playlists/171/the_most_popular_talks_of_all"
    private val embedUrl =
        "https://embed.ted.com/talks/janet_stovall_how_to_get_serious_about_diversity_and_inclusion_in_the_workplace"

    private val talkPage = FixtureRoute(
        urlPattern = "https://www.ted.com/talks/*",
        contentType = "text/html",
        body = """
            <html><head>
            <meta property="og:title" content="Fixture Talk OG">
            <meta property="og:image" content="https://media.example/og.jpg">
            <meta property="og:description" content="Fixture OG description">
            </head><body>
            <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"videoData":{
              "id":"86532","title":"Fixture Talk","presenterDisplayName":"Fixture Presenter",
              "description":"Fixture description","duration":679,"viewedCount":"1,234",
              "publishedAt":"2022-01-14",
              "playerData":"{\"resources\":{\"hls\":{\"stream\":\"https://media.example/679k/master.m3u8\"},\"h264\":[{\"file\":\"https://media.example/679k.mp4\",\"bitrate\":679}]},\"thumb\":\"https://media.example/thumb.jpg?w=200\",\"targeting\":{\"tag\":\"a,b\"}}",
              "audioDownload":"https://media.example/audio.mp3"}}}}</script>
            </body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val talk = TedTalkIE(http(transfer()))
        assertTrue(talk.suitable(talkUrl))
        assertTrue(talk.suitable("https://www.ted.com/talks/lang/es/foo-bar"))
        assertFalse(talk.suitable(seriesUrl))
        assertFalse(talk.suitable(playlistUrl))
        assertFalse(talk.suitable(embedUrl))
        assertFalse(talk.suitable("https://www.example.com/talks/foo-bar"))

        val series = TedSeriesIE(http(transfer()))
        assertTrue(series.suitable(seriesUrl))
        assertTrue(series.suitable("https://www.ted.com/series/the_way_we_work#season_2"))
        assertFalse(series.suitable(talkUrl))

        val playlist = TedPlaylistIE(http(transfer()))
        assertTrue(playlist.suitable(playlistUrl))
        assertTrue(playlist.suitable("https://www.ted.com/playlists/171"))

        val embed = TedEmbedIE(http(transfer()))
        assertTrue(embed.suitable(embedUrl))
        assertTrue(embed.suitable("https://embed-ssl.ted.com/talks/foo"))
    }

    // ------------------------------------------------------------------ talk

    @Test
    fun talkYieldsTheHlsH264HttpAndAudioRows() = runTest {
        val info = TedTalkIE(http(transfer(talkPage))).extract(talkUrl)
        assertEquals("86532", info.id)
        assertEquals("Fixture Talk", info.title)
        assertEquals("Fixture Presenter", info.uploader)
        assertEquals("Fixture description", info.description)
        assertEquals(679.0, info.duration)
        assertEquals(1234L, info.viewCount)
        assertEquals("20220114", info.uploadDate)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(4, info.formats.size)
        assertEquals("hls", info.formats[0].formatId)
        assertEquals("https://media.example/679k/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("h264-679k", info.formats[1].formatId)
        assertEquals(679.0, info.formats[1].tbr)
        assertEquals("http", info.formats[2].formatId)
        assertEquals("http", info.formats[2].protocol)
        assertEquals("audio", info.formats[3].formatId)
        assertEquals("none", info.formats[3].vcodec)
    }

    @Test
    fun talkIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = talkUrl,
            infoDict = mapOf(
                "id" to Expect.Value("86532"),
                "title" to Expect.Value("Fixture Talk"),
                "duration" to Expect.Value(679.0),
                "view_count" to Expect.Value(1234L),
                "formats" to Expect.Count(4),
            ),
            routes = listOf(talkPage),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TedTalkIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun talkWithoutResourcesYieldsTheExternalEntry() = runTest {
        val externalPage = FixtureRoute(
            urlPattern = "https://www.ted.com/talks/*",
            contentType = "text/html",
            body = """
                <html><body><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"videoData":{
                  "id":"12345","title":"Fixture External",
                  "playerData":"{\"resources\":{},\"external\":{\"service\":\"Vimeo\",\"uri\":\"https://player.vimeo.com/video/111\"}}"}}}}</script></body></html>
            """.trimIndent(),
        )
        val info = TedTalkIE(http(transfer(externalPage))).extract(talkUrl)
        assertEquals("12345", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://player.vimeo.com/video/111", info.entries[0].url)
    }

    // ---------------------------------------------------------------- series

    @Test
    fun seriesYieldsTheSeasonEntries() = runTest {
        val seriesPage = FixtureRoute(
            urlPattern = "https://www.ted.com/series/*",
            contentType = "text/html",
            body = """
                <html><head><meta property="og:description" content="Fixture series description">
                </head><body><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{
                  "seasons":[
                    {"seasonNumber":1,"videos":{"nodes":[
                      {"__typename":"Video","canonicalUrl":"https://www.ted.com/talks/fixture_one"},
                      {"__typename":"Article","canonicalUrl":"https://www.ted.com/articles/x"}]}},
                    {"seasonNumber":2,"videos":{"nodes":[
                      {"__typename":"Video","canonicalUrl":"https://www.ted.com/talks/fixture_two"}]}}],
                  "series":{"id":"8","name":"Fixture Series"}}}}</script></body></html>
            """.trimIndent(),
        )
        val info = TedSeriesIE(http(transfer(seriesPage))).extract(seriesUrl)
        assertEquals("8", info.id)
        assertEquals("Fixture Series", info.title)
        assertEquals("Fixture series description", info.description)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.ted.com/talks/fixture_one", info.entries[0].url)
        assertEquals("https://www.ted.com/talks/fixture_two", info.entries[1].url)
    }

    @Test
    fun seriesWithSeasonFiltersAndNamesThePlaylist() = runTest {
        val seriesPage = FixtureRoute(
            urlPattern = "https://www.ted.com/series/*",
            contentType = "text/html",
            body = """
                <html><body><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{
                  "seasons":[
                    {"seasonNumber":1,"videos":{"nodes":[
                      {"__typename":"Video","canonicalUrl":"https://www.ted.com/talks/fixture_one"}]}},
                    {"seasonNumber":2,"videos":{"nodes":[
                      {"__typename":"Video","canonicalUrl":"https://www.ted.com/talks/fixture_two"}]}}],
                  "series":{"id":"8","name":"Fixture Series"}}}}</script></body></html>
            """.trimIndent(),
        )
        val info = TedSeriesIE(http(transfer(seriesPage))).extract("$seriesUrl#season_2")
        assertEquals("8_2", info.id)
        assertEquals("Fixture Series Season 2", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.ted.com/talks/fixture_two", info.entries[0].url)
    }

    // -------------------------------------------------------------- playlist

    @Test
    fun playlistYieldsTheVideoEntries() = runTest {
        val playlistPage = FixtureRoute(
            urlPattern = "https://www.ted.com/playlists/*",
            contentType = "text/html",
            body = """
                <html><head><meta property="og:description" content="Fixture playlist description">
                </head><body><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{
                  "playlist":{"id":"171","title":"Fixture Playlist","videos":{"nodes":[
                    {"__typename":"Video","canonicalUrl":"https://www.ted.com/talks/fixture_one"}]}}}}}</script>
                </body></html>
            """.trimIndent(),
        )
        val info = TedPlaylistIE(http(transfer(playlistPage))).extract(playlistUrl)
        assertEquals("171", info.id)
        assertEquals("Fixture Playlist", info.title)
        assertEquals("Fixture playlist description", info.description)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.ted.com/talks/fixture_one", info.entries[0].url)
    }

    // ----------------------------------------------------------------- embed

    @Test
    fun embedRewritesTheHostAndDelegatesToTheTalk() = runTest {
        val info = TedEmbedIE(http(transfer(talkPage))).extract(embedUrl)
        assertEquals("86532", info.id)
        assertEquals("Fixture Talk", info.title)
        assertEquals(4, info.formats.size)
        assertEquals(embedUrl, info.webpageUrl)
    }
}
