package com.anydownload.core.extract.tunein

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
 * Fixture cases for the TuneIn subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class TuneInIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val stationUrl = "https://tunein.com/radio/Jazz24-885-s34682/"
    private val podcastUrl = "https://tunein.com/podcasts/Technology-Podcasts/Artificial-Intelligence-p1153019/"
    private val episodeUrl = podcastUrl + "?topicId=236404354"
    private val embedUrl = "https://tunein.com/embed/player/s6404/"

    private fun opmlRoute(streamId: String) = FixtureRoute(
        urlPattern = "https://opml.radiotime.com/Tune.ashx*",
        contentType = "application/json",
        body = """
            {"body": [
              {"url": "https://media.example/$streamId.mp3", "media_type": "mp3", "bitrate": 128},
              {"url": "//media.example/$streamId.m3u8", "media_type": "hls"}
            ]}
        """.trimIndent(),
    )

    private fun stationApiRoute() = FixtureRoute(
        urlPattern = "https://api.tunein.com/profiles/s34682",
        contentType = "application/json",
        body = """
            {"Item": {
              "Title": "Fixture <b>Jazz</b> Station",
              "Subtitle": "World Class Jazz",
              "Description": "Fixture station description",
              "Image": "https://media.example/logo.png",
              "Actions": {"Play": {"IsLive": true}, "Follow": {"FollowerCount": 42}},
              "Properties": {"Location": {"DisplayName": "Fixtureville"}}
            }}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            TuneInStationIE(http(transfer())) to stationUrl,
            TuneInPodcastIE(http(transfer())) to podcastUrl,
            TuneInPodcastEpisodeIE(http(transfer())) to episodeUrl,
            TuneInEmbedIE(http(transfer())) to embedUrl,
            TuneInShortenerIE(http(transfer())) to "http://tun.in/ser7s",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        // The episode URL stays off the paged program extractor.
        assertFalse(TuneInPodcastIE(http(transfer())).suitable(episodeUrl))
        assertTrue(TuneInPodcastIE(http(transfer())).suitable(podcastUrl))
        assertFalse(TuneInStationIE(http(transfer())).suitable(podcastUrl))
    }

    // ---------------------------------------------------------------- station

    @Test
    fun stationYieldsTheMp3AndHlsFormats() = runTest {
        val transfer = transfer(opmlRoute("station"), stationApiRoute())
        val info = TuneInStationIE(http(transfer)).extract(stationUrl)
        assertEquals("s34682", info.id)
        assertEquals("Fixture Jazz Station", info.title)
        assertEquals("Fixture station description", info.description)
        assertEquals(true, info.isLive)
        assertEquals("https://media.example/logo.png", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("mp3", info.formats[0].ext)
        assertEquals(128.0, info.formats[0].abr)
        assertEquals("https://media.example/station.mp3", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[1].protocol)
        assertEquals("https://media.example/station.m3u8", info.formats[1].url)
    }

    // ---------------------------------------------------------------- podcast

    @Test
    fun podcastListingPagesAndBuildsTopicEntries() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.tunein.com/profiles/p1153019/contents*",
                contentType = "application/json",
                body = """{"Items": [{"GuideId": "t236404354"}, {"GuideId": "t111"}]}""",
            ),
            FixtureRoute(
                urlPattern = "https://api.tunein.com/profiles/p1153019",
                contentType = "application/json",
                body = """{"Item": {"Title": "Fixture Podcast"}}""",
            ),
        )
        val info = TuneInPodcastIE(http(transfer)).extract(podcastUrl)
        assertEquals("p1153019", info.id)
        assertEquals("Fixture Podcast", info.title)
        assertEquals(2, info.entries.size)
        assertEquals(podcastUrl + "?topicId=236404354", info.entries[0].url)
        assertEquals(podcastUrl + "?topicId=111", info.entries[1].url)
    }

    @Test
    fun podcastListingStopsOnAShortPage() = runTest {
        val first = (0 until 20).joinToString(",") { "{\"GuideId\": \"t$it\"}" }
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.tunein.com/profiles/p1153019/contents?filter=t%3Afree&limit=20&offset=0",
                contentType = "application/json",
                body = """{"Items": [$first]}""",
            ),
            FixtureRoute(
                urlPattern = "https://api.tunein.com/profiles/p1153019/contents?filter=t%3Afree&limit=20&offset=20",
                contentType = "application/json",
                body = """{"Items": [{"GuideId": "t999"}]}""",
            ),
            FixtureRoute(
                urlPattern = "https://api.tunein.com/profiles/p1153019",
                contentType = "application/json",
                body = """{"Item": {"Title": "Fixture Podcast"}}""",
            ),
        )
        val info = TuneInPodcastIE(http(transfer)).extract(podcastUrl)
        assertEquals(21, info.entries.size)
        assertEquals(podcastUrl + "?topicId=999", info.entries[20].url)
    }

    // ---------------------------------------------------------- episode

    @Test
    fun podcastEpisodeYieldsFormatsAndMetadata() = runTest {
        val transfer = transfer(
            opmlRoute("t236404354"),
            FixtureRoute(
                urlPattern = "https://api.tunein.com/profiles/p1153019",
                contentType = "application/json",
                body = """{"Item": {"Title": "Fixture Series"}}""",
            ),
            FixtureRoute(
                urlPattern = "https://api.tunein.com/profiles/t236404354",
                contentType = "application/json",
                body = """
                    {"Item": {
                      "Title": "Fixture Episode",
                      "Description": "Fixture notes",
                      "Image": "https://media.example/episode.png",
                      "Actions": {"Play": {"Duration": 1203, "PublishTime": "2022-08-29T00:00:00+00:00"}}
                    }}
                """.trimIndent(),
            ),
        )
        val info = TuneInPodcastEpisodeIE(http(transfer)).extract(episodeUrl)
        assertEquals("t236404354", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals("Fixture notes", info.description)
        assertEquals(1203.0, info.duration)
        assertEquals("Fixture Series", info.channel)
        assertEquals("p1153019", info.channelId)
        assertEquals("20220829", info.uploadDate)
        assertEquals("https://media.example/episode.png", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
    }

    // ---------------------------------------------------------------- embed

    @Test
    fun embedFormRedirectsToTheCanonicalKindUrl() = runTest {
        val cases = listOf(
            "https://tunein.com/embed/player/s6404/" to "https://tunein.com/station/?stationid=6404",
            "https://tunein.com/embed/player/t236404354/" to "https://tunein.com/topic/?topicid=236404354",
            "https://tunein.com/embed/player/p191660/" to "https://tunein.com/program/?programid=191660",
        )
        for ((url, expected) in cases) {
            val info = TuneInEmbedIE(http(transfer())).extract(url)
            assertEquals(expected, info.redirectUrl, url)
        }
    }

    // ------------------------------------------------------------- shortener

    @Test
    fun shortenerResolvesTheRedirectAndStripsThePort() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "http://tun.in/ser7s", redirectTo = "https://tunein.com:443/radio/Jazz24-885-s34682/"),
            FixtureRoute(urlPattern = "https://tunein.com:443/radio/Jazz24-885-s34682/", contentType = "text/html", body = ""),
        )
        val info = TuneInShortenerIE(http(transfer)).extract("http://tun.in/ser7s")
        assertEquals("ser7s", info.id)
        assertEquals(stationUrl, info.redirectUrl)
    }

    @Test
    fun shortenerThatStaysOnTunInFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "http://tun.in/loop", redirectTo = "http://tun.in/final"),
            FixtureRoute(urlPattern = "http://tun.in/final", contentType = "text/html", body = ""),
        )
        assertFailsWith<ExtractionError.UnsupportedUrl> {
            TuneInShortenerIE(http(transfer)).extract("http://tun.in/loop")
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun stationIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = stationUrl,
            infoDict = mapOf(
                "id" to Expect.Value("s34682"),
                "title" to Expect.Value("Fixture Jazz Station"),
                "is_live" to Expect.Value(true),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(opmlRoute("station"), stationApiRoute()),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TuneInStationIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun podcastIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = podcastUrl,
            infoDict = mapOf(
                "id" to Expect.Value("p1153019"),
                "title" to Expect.Value("Fixture Podcast"),
                "entries" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api.tunein.com/profiles/p1153019/contents*",
                    contentType = "application/json",
                    body = """{"Items": [{"GuideId": "t236404354"}, {"GuideId": "t111"}]}""",
                ),
                FixtureRoute(
                    urlPattern = "https://api.tunein.com/profiles/p1153019",
                    contentType = "application/json",
                    body = """{"Item": {"Title": "Fixture Podcast"}}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TuneInPodcastIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
