package com.anydownload.core.extract.polskieradio

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
 * Fixture cases for the Polskie Radio subset. Ids, titles, and media paths
 * are synthesized; media lives on `media.example`, and no cookie or signed
 * URL appears.
 */
class PolskieRadioIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            PolskieRadioLegacyIE(http(transfer())) to
                "https://www.polskieradio.pl/8/2382/Artykul/2534482,Fixture",
            PolskieRadioIE(http(transfer())) to "https://jedynka.polskieradio.pl/artykul/1587943",
            PolskieRadioIE(http(transfer())) to "https://radiokierowcow.pl/artykul/2694529",
            PolskieRadioAuditionIE(http(transfer())) to "https://jedynka.polskieradio.pl/audycje/5102",
            PolskieRadioAuditionIE(http(transfer())) to "https://trojka.polskieradio.pl/audycja/8906",
            PolskieRadioCategoryIE(http(transfer())) to "http://www.polskieradio.pl/37,RedakcjaKatolicka/4143",
            PolskieRadioCategoryIE(http(transfer())) to "https://www.polskieradio.pl/Krzysztof-Dziuba/Tag175458",
            PolskieRadioPlayerIE(http(transfer())) to "https://player.polskieradio.pl/anteny/trojka",
            PolskieRadioPodcastListIE(http(transfer())) to "https://podcasty.polskieradio.pl/podcast/8/",
            PolskieRadioPodcastIE(http(transfer())) to
                "https://podcasty.polskieradio.pl/track/6eafe403-cb8f-4756-b896-4455c3713c32",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(PolskieRadioPlayerIE(http(transfer())).suitable("https://player.polskieradio.pl/anteny"))
        assertFalse(PolskieRadioPodcastIE(http(transfer())).suitable("https://podcasty.polskieradio.pl/track/not-a-guid"))
    }

    // ------------------------------------------------------- legacy articles

    @Test
    fun legacySingleArticleReturnsTheAuditionRecord() = runTest {
        val url = "https://www.polskieradio.pl/10/6071/Artykul/2610977,Fixture"
        val page = """
            <html><head>
            <meta property="og:title" content="Fixture Audition">
            <meta property="og:description" content="Fixture description">
            <meta property="og:image" content="https://static.prsa.pl/images/fake.jpg">
            <span id="datetime2">2020-06-20</span>
            </head><body>
            <script>source: '//static.prsa.pl/audio/fake_record.mp3'</script>
            </body></html>
        """.trimIndent()
        val info = PolskieRadioLegacyIE(http(transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))))
            .extract(url)
        assertEquals("2610977", info.id)
        assertEquals("Fixture Audition", info.title)
        assertEquals("https://static.prsa.pl/audio/fake_record.mp3", info.url)
        assertEquals("mp3", info.ext)
        assertEquals("20200620", info.uploadDate)
    }

    @Test
    fun legacyArticleBodyPlayersBecomeEntries() = runTest {
        val url = "https://www.polskieradio.pl/8/2382/Artykul/2534482,Fixture"
        val page = """
            <html><head><meta property="og:title" content="Fixture Article"></head><body>
            <div class="this-article">
              <span data-media='{"id": 2516679, "file": "//media.example/audio/fake_one.mp3",
                                 "desc": "Fixture%20Player%20One", "length": 1430, "provider": "audio"}'></span>
              <div class="tags"></div>
            </div>
            </body></html>
        """.trimIndent()
        val info = PolskieRadioLegacyIE(http(transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))))
            .extract(url)
        assertEquals(1, info.entries.size)
        assertEquals("2516679", info.entries[0].id)
        assertEquals("https://media.example/audio/fake_one.mp3", info.entries[0].url)
        assertEquals("Fixture Player One", info.entries[0].title)
    }

    @Test
    fun legacyRedirectsToTheNewArticleExtractor() = runTest {
        val url = "https://www.polskieradio.pl/8/2382/Artykul/2534482,Fixture"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                redirectTo = "https://jedynka.polskieradio.pl/artykul/1587943",
            ),
            FixtureRoute(
                urlPattern = "https://jedynka.polskieradio.pl/artykul/1587943",
                contentType = "text/html",
                body = "<html></html>",
            ),
        )
        val info = PolskieRadioLegacyIE(http(transfer)).extract(url)
        assertEquals("https://jedynka.polskieradio.pl/artykul/1587943", info.redirectUrl)
    }

    // ---------------------------------------------------------- new articles

    @Test
    fun nextJsArticleAudioAttachmentsBecomeEntries() = runTest {
        val url = "https://jedynka.polskieradio.pl/artykul/1587943"
        val page = """
            <html><head><script id="__NEXT_DATA__" type="application/json">
            {"props": {"pageProps": {"data": {"articleData": {
              "title": "Fixture Next Article", "lead": "Fixture lead",
              "attachments": [{"fileType": "Audio", "fileName": "fake.mp3",
                               "file": "https://media.example/audio/7a85d429-5356-4def-a347-925e4ae7406b.mp3",
                               "description": "Fixture Episode"}]
            }}}}}
            </script></head></html>
        """.trimIndent()
        val info = PolskieRadioIE(http(transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))))
            .extract(url)
        assertEquals("1587943", info.id)
        assertEquals("Fixture Next Article", info.title)
        assertEquals("Fixture lead", info.description)
        assertEquals(1, info.entries.size)
        assertEquals("7a85d429-5356-4def-a347-925e4ae7406b", info.entries[0].id)
    }

    @Test
    fun nextJsArticleFallsBackToBodyPlayers() = runTest {
        val url = "https://trojka.polskieradio.pl/artykul/2589163,Fixture"
        val page = """
            <html><head><script id="__NEXT_DATA__" type="application/json">
            {"props": {"pageProps": {"post": {"data": {
              "title": "Fixture Legacy Body", "attachments": [],
              "content": "<span data-media='{\"id\": 2577880, \"file\": \"//media.example/audio/fake_two.mp3\", \"desc\": \"Fixture%20Two\", \"length\": 321, \"provider\": \"audio\"}'></span>"
            }}}}}
            </script></head></html>
        """.trimIndent()
        val info = PolskieRadioIE(http(transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))))
            .extract(url)
        assertEquals(1, info.entries.size)
        assertEquals("Fixture Two", info.entries[0].title)
    }

    // ------------------------------------------------------------- auditions

    @Test
    fun auditionListFailsTypedOnTheApiKey() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            PolskieRadioAuditionIE(http(transfer())).extract("https://jedynka.polskieradio.pl/audycje/5102")
        }
        assertTrue(error.message!!.contains("x-api-key"))
    }

    // ------------------------------------------------------------- categories

    @Test
    fun categoryFirstPageListsArticlesAndMedia() = runTest {
        val url = "http://www.polskieradio.pl/37,RedakcjaKatolicka/4143"
        val page = """
            <html><head><title>Fixture Category - Redakcja - Fixture</title></head><body>
            <article class="fixture"><a href="/8/2382/Artykul/2534482,Fixture" title="Fixture Article">One</a></article>
            <span data-media={"uid":555,"file":"//media.example/audio/fake_three.mp3","title":"Fixture%20Three","length":100}></span>
            </body></html>
        """.trimIndent()
        val info = PolskieRadioCategoryIE(http(transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))))
            .extract(url)
        assertEquals("4143", info.id)
        assertEquals("Fixture Category", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("http://www.polskieradio.pl/8/2382/Artykul/2534482,Fixture", info.entries[0].url)
        assertEquals("Fixture Three", info.entries[1].title)
    }

    // ----------------------------------------------------------------- player

    @Test
    fun playerBundleAndStationsApiYieldTheLiveStream() = runTest {
        val url = "https://player.polskieradio.pl/anteny/trojka"
        val bundle = """
            (function(){;var r="anteny",a=[{"id": 3, "name": "Fixture FM", "url": "trojka", "streamName": "Fixture FM"}]},)
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://player.polskieradio.pl/main.bundle.js",
                contentType = "application/javascript",
                body = bundle,
            ),
            FixtureRoute(
                urlPattern = "https://apipr.polskieradio.pl/api/stacje",
                contentType = "application/json",
                body = """[{"Name": "Fixture FM", "Streams": ["//media.example/live/playlist.m3u8"]}]""",
            ),
        )
        val info = PolskieRadioPlayerIE(http(transfer)).extract(url)
        assertEquals("3", info.id)
        assertEquals("Fixture FM", info.title)
        assertEquals(true, info.isLive)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/live/playlist.m3u8", info.formats.single().url)
    }

    // ---------------------------------------------------------------- podcast

    @Test
    fun podcastListPagesTheApi() = runTest {
        val url = "https://podcasty.polskieradio.pl/podcast/8/"
        val page1 = """
            {"id": 8, "title": "Fixture Podcast", "description": "Fixture description",
             "announcer": "Fixture Announcer", "itemCount": 2,
             "items": [
               {"guid": "fake-guid-1", "url": "https://media.example/audio/ep1.mp3", "title": "Episode 1",
                "description": "One", "length": 60, "publishDate": "2020-06-20T10:00:00Z",
                "image": "https://static.prsa.pl/images/fake1.jpg"},
               {"guid": "fake-guid-2", "url": "https://media.example/audio/ep2.mp3", "title": "Episode 2",
                "length": 120, "publishDate": "2020-06-21T10:00:00Z"}
             ]}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://apipodcasts.polskieradio.pl/api/Podcasts/8/?pageSize=10&page=1",
                contentType = "application/json",
                body = page1,
            ),
        )
        val info = PolskieRadioPodcastListIE(http(transfer)).extract(url)
        assertEquals("8", info.id)
        assertEquals("Fixture Podcast", info.title)
        assertEquals("Fixture Announcer", info.uploader)
        assertEquals(2, info.entries.size)
        assertEquals("fake-guid-2", info.entries[1].id)
    }

    @Test
    fun podcastTrackPostsTheGuidAndParsesTheEpisode() = runTest {
        val url = "https://podcasty.polskieradio.pl/track/6eafe403-cb8f-4756-b896-4455c3713c32"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://apipodcasts.polskieradio.pl/api/audio",
                method = "POST",
                contentType = "application/json",
                body = """
                    [{"guid": "6eafe403-cb8f-4756-b896-4455c3713c32",
                      "url": "https://media.example/audio/track.mp3", "title": "Fixture Track",
                      "description": "Fixture description", "length": 2893,
                      "publishDate": "2020-06-20T10:00:00Z",
                      "image": "https://static.prsa.pl/images/fake.jpg"}]
                """.trimIndent(),
            ),
        )
        val info = PolskieRadioPodcastIE(http(transfer)).extract(url)
        assertEquals("6eafe403-cb8f-4756-b896-4455c3713c32", info.id)
        assertEquals("Fixture Track", info.title)
        assertEquals("20200620", info.uploadDate)
        assertEquals("https://media.example/audio/track.mp3", info.formats.single().url)
        val post = transfer.requests.first { it.url.contains("/api/audio") }
        assertEquals("POST", post.method)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun legacyArticleIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.polskieradio.pl/8/2382/Artykul/2534482,Fixture"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("2534482"),
                "entries" to Expect.Count(1),
                "entries.0.url" to Expect.Value("https://media.example/audio/fake_one.mp3"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><head><meta property="og:title" content="Fixture Article"></head><body>
                        <div class="this-article">
                          <span data-media='{"id": 2516679, "file": "//media.example/audio/fake_one.mp3",
                                             "desc": "Fixture%20Player%20One", "length": 1430}'></span>
                          <div class="tags"></div>
                        </div>
                        </body></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> PolskieRadioLegacyIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun podcastTrackIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://podcasty.polskieradio.pl/track/6eafe403-cb8f-4756-b896-4455c3713c32"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("6eafe403-cb8f-4756-b896-4455c3713c32"),
                "formats.0.url" to Expect.Value("https://media.example/audio/track.mp3"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://apipodcasts.polskieradio.pl/api/audio",
                    method = "POST",
                    contentType = "application/json",
                    body = """[{"guid": "6eafe403-cb8f-4756-b896-4455c3713c32",
                                "url": "https://media.example/audio/track.mp3", "title": "Fixture Track"}]""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> PolskieRadioPodcastIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
