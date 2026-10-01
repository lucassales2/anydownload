package com.anydownload.core.extract.agora

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
 * Fixture cases for the Agora subset. Ids and media paths are synthesized on
 * `media.example`; the device id is a random fake value. No cookie, token, or
 * signed URL appears.
 */
class AgoraIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl = "https://wyborcza.pl/video/26207634"
    private val podcastUrl = "https://wyborcza.pl/podcast/0,172673.html?podcast=100720"
    private val tokfmUrl =
        "https://audycje.tokfm.pl/podcast/91275,-Systemowy-rasizm-Czy-zamieszki-w-USA"
    private val auditionUrl = "https://audycje.tokfm.pl/audycja/218,Analizy"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val video = WyborczaVideoIE(http(transfer()))
        assertTrue(video.suitable("wyborcza:video:26207634"))
        assertTrue(video.suitable(videoUrl))
        assertTrue(video.suitable("https://wyborcza.pl/api-video/26207634"))
        assertFalse(video.suitable(podcastUrl))

        val podcast = WyborczaPodcastIE(http(transfer()))
        assertTrue(podcast.suitable(podcastUrl))
        assertTrue(podcast.suitable("https://www.wysokieobcasy.pl/wysokie-obcasy/0,176631.html?podcast=100673"))
        assertTrue(podcast.suitable("https://wyborcza.pl/podcast"))

        val tokfm = TokFMPodcastIE(http(transfer()))
        assertTrue(tokfm.suitable(tokfmUrl))
        assertTrue(tokfm.suitable("tokfm:podcast:91275"))
        assertFalse(tokfm.suitable(auditionUrl))

        val audition = TokFMAuditionIE(http(transfer()))
        assertTrue(audition.suitable(auditionUrl))
        assertTrue(audition.suitable("tokfm:audition:218"))
        assertFalse(audition.suitable(tokfmUrl))
    }

    // -------------------------------------------------------------- video

    @Test
    fun wyborczaVideoYieldsTheQualityRows() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://wyborcza.pl/api-video/26207634",
            contentType = "application/json",
            body = """
                {"redirector": "http://cdn.example", "basePath": "/media/",
                 "files": {"standard": "p480x.mp4", "high": "p720x.mp4", "dash": "manifest.mpd"},
                 "title": "Fixture Video", "lead": "Fixture lead", "signature": "Fixture Author",
                 "imageUrl": "https://media.example/thumb.jpg", "duration": 2474}
            """.trimIndent(),
        )
        val info = WyborczaVideoIE(http(transfer(route))).extract(videoUrl)
        assertEquals("26207634", info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Fixture lead", info.description)
        assertEquals("Fixture Author", info.uploader)
        assertEquals(2474.0, info.duration)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(3, info.formats.size)
        assertEquals("standard", info.formats[0].formatId)
        assertEquals("https://cdn.example/media/p480x.mp4", info.formats[0].url)
        assertEquals(480L, info.formats[0].height)
        assertEquals("high", info.formats[1].formatId)
        assertEquals(720L, info.formats[1].height)
        assertEquals("dash", info.formats[2].formatId)
        assertEquals("mpd", info.formats[2].protocol)
    }

    @Test
    fun wyborczaVideoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://wyborcza.pl/api-video/26207634",
            contentType = "application/json",
            body = """
                {"redirector": "http://cdn.example", "basePath": "/media/",
                 "files": {"standard": "p480x.mp4"}, "title": "Fixture Video", "duration": 2474}
            """.trimIndent(),
        )
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("26207634"),
                "title" to Expect.Value("Fixture Video"),
                "duration" to Expect.Value(2474.0),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(route),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> WyborczaVideoIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    // ------------------------------------------------------------ podcast

    @Test
    fun wyborczaPodcastYieldsTheAudioRowAndDate() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://wyborcza.pl/api/podcast?guid=100720",
            contentType = "application/json",
            body = """
                {"url": "https://media.example/podcast.mp3", "title": "Fixture Podcast",
                 "description": "Fixture description", "imageUrl": "https://media.example/pod.jpg",
                 "duration": "01:01:24", "author": "Fixture Host",
                 "publishedDate": "17 stycznia 2021"}
            """.trimIndent(),
        )
        val info = WyborczaPodcastIE(http(transfer(route))).extract(podcastUrl)
        assertEquals("100720", info.id)
        assertEquals("Fixture Podcast", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("Fixture Host", info.uploader)
        assertEquals("20210117", info.uploadDate)
        assertEquals(3684.0, info.duration)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/podcast.mp3", info.formats[0].url)
        assertEquals("mp3", info.formats[0].ext)
    }

    @Test
    fun wyborczaPodcastPlaylistDispatchesToTokFM() = runTest {
        val seriesRoute = FixtureRoute(
            urlPattern = "https://api.podcast.radioagora.pl/api4/getSeries?series_id=334",
            contentType = "application/json",
            body = """[{"series_name": "Gościnnie: Wyborcza, 8:10"}]""",
        )
        val firstPage = FixtureRoute(
            urlPattern = "https://api.podcast.radioagora.pl/api4/getPodcasts?series_id=334&limit=30&offset=0*",
            contentType = "application/json",
            body = """
                [{"podcast_sharing_url": "https://audycje.tokfm.pl/podcast/1,x", "podcast_name": "One"}]
            """.trimIndent(),
        )
        val secondPage = FixtureRoute(
            urlPattern = "https://api.podcast.radioagora.pl/api4/getPodcasts?series_id=334&limit=30&offset=30*",
            contentType = "application/json",
            body = """[]""",
        )
        val info = WyborczaPodcastIE(http(transfer(seriesRoute, firstPage, secondPage))).extract(
            "https://wyborcza.pl/podcast",
        )
        assertEquals("334", info.id)
        assertEquals("Gościnnie: Wyborcza, 8:10", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://audycje.tokfm.pl/podcast/1,x", info.entries[0].url)
    }

    // --------------------------------------------------------------- tokfm

    @Test
    fun tokfmPodcastYieldsTheMp3Row() = runTest {
        val metadataRoute = FixtureRoute(
            urlPattern = "https://audycje.tokfm.pl/getp/391275",
            contentType = "application/json",
            body = """[{"podcast_name": "Fixture Episode", "series_name": "Fixture Series"}]""",
        )
        val songRoute = FixtureRoute(
            urlPattern = "https://api.podcast.radioagora.pl/api4/getSongUrl?*",
            contentType = "application/json",
            body = """{"link_ssl": "https://media.example/episode.mp3"}""",
        )
        val info = TokFMPodcastIE(http(transfer(metadataRoute, songRoute))).extract(tokfmUrl)
        assertEquals("91275", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/episode.mp3", info.formats[0].url)
        assertEquals("mp3", info.formats[0].ext)
        assertEquals("none", info.formats[0].vcodec)
    }

    @Test
    fun tokfmPodcastWithoutMetadataFailsTyped() = runTest {
        val route = FixtureRoute(
            urlPattern = "https://audycje.tokfm.pl/getp/391275",
            contentType = "application/json",
            body = """[]""",
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            TokFMPodcastIE(http(transfer(route))).extract(tokfmUrl)
        }
        assertTrue(error.message!!.contains("No such podcast"), error.message)
    }

    @Test
    fun tokfmAuditionYieldsThePodcastEntries() = runTest {
        val seriesRoute = FixtureRoute(
            urlPattern = "https://api.podcast.radioagora.pl/api4/getSeries?series_id=218",
            contentType = "application/json",
            body = """[{"series_name": "Analizy"}]""",
        )
        val firstPage = FixtureRoute(
            urlPattern = "https://api.podcast.radioagora.pl/api4/getPodcasts?series_id=218&limit=30&offset=0*",
            contentType = "application/json",
            body = """
                [{"podcast_sharing_url": "https://audycje.tokfm.pl/podcast/1,x", "podcast_name": "One"},
                 {"podcast_sharing_url": "https://audycje.tokfm.pl/podcast/2,y", "podcast_name": "Two"}]
            """.trimIndent(),
        )
        val secondPage = FixtureRoute(
            urlPattern = "https://api.podcast.radioagora.pl/api4/getPodcasts?series_id=218&limit=30&offset=30*",
            contentType = "application/json",
            body = """[]""",
        )
        val info = TokFMAuditionIE(http(transfer(seriesRoute, firstPage, secondPage))).extract(auditionUrl)
        assertEquals("218", info.id)
        assertEquals("Analizy", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://audycje.tokfm.pl/podcast/1,x", info.entries[0].url)
        assertEquals("One", info.entries[0].title)
    }
}
