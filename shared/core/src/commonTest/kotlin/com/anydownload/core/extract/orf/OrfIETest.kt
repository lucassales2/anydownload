package com.anydownload.core.extract.orf

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
 * Fixture cases for the ORF subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class OrfIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ORFRadioIE(http(transfer())) to "https://ooe.orf.at/player/20220801/OGMO",
            ORFRadioIE(http(transfer())) to "https://radiothek.orf.at/ooe/20220801/OGMO",
            ORFPodcastIE(http(transfer())) to "https://sound.orf.at/podcast/oe3/fixture-show/fixture-episode",
            ORFIPTVIE(http(transfer())) to "http://iptv.orf.at/stories/2275236/",
            ORFFM4StoryIE(http(transfer())) to "http://fm4.orf.at/stories/2865738/",
            ORFONIE(http(transfer())) to "https://on.orf.at/video/14210000/fixture",
            ORFONIE(http(transfer())) to "https://on.orf.at/video/14226549/15639808/fixture",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ORFPodcastIE(http(transfer())).suitable("https://sound.orf.at/podcast/xx/a/b"))
    }

    // ----------------------------------------------------------------- radio

    @Test
    fun radioBroadcastYieldsTheLoopstreamEntries() = runTest {
        val url = "https://ooe.orf.at/player/20220801/OGMO"
        val api = """
            {"title": "Fixture Show", "subtitle": "<b>Fixture</b> subtitle",
             "streams": [{"loopStreamId": "2022-08-01_0459_tl_66_7DaysMon1_319062.mp3",
                          "start": 1659322789000, "end": 1659340000000}]}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "http://audioapi.orf.at/ooe/api/json/current/broadcast/OGMO/20220801",
                contentType = "application/json",
                body = api,
            ),
        )
        val info = ORFRadioIE(http(transfer)).extract(url)
        assertEquals("OGMO", info.id)
        assertEquals("Fixture Show", info.title)
        assertEquals("Fixture subtitle", info.description)
        assertEquals(1, info.entries.size)
        assertEquals("2022-08-01_0459_tl_66_7DaysMon1_319062", info.entries[0].id)
        assertEquals(
            "https://loopstream01.apa.at/?channel=oe2o&id=2022-08-01_0459_tl_66_7DaysMon1_319062.mp3",
            info.entries[0].url,
        )
    }

    // --------------------------------------------------------------- podcast

    @Test
    fun podcastYieldsTheEnclosure() = runTest {
        val url = "https://sound.orf.at/podcast/oe3/fixture-show/fixture-episode"
        val api = """
            {"payload": {"enclosures": [{"url": "https://media.example/podcast/ep.mp3",
                                         "type": "audio/mpeg"}],
                         "title": "Fixture Episode", "description": "<p>Fixture</p>",
                         "duration": 3396000, "podcast": {"title": "Fixture Podcast"}}}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://audioapi.orf.at/radiothek/api/2.0/podcast/oe3/fixture-show/fixture-episode",
                contentType = "application/json",
                body = api,
            ),
        )
        val info = ORFPodcastIE(http(transfer)).extract(url)
        assertEquals("fixture-episode", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals("Fixture", info.description)
        assertEquals(3396.0, info.duration)
        assertEquals("https://media.example/podcast/ep.mp3", info.url)
        assertEquals("mp3", info.ext)
    }

    // ---------------------------------------------------------- iptv / fm4

    @Test
    fun iptvStoryYieldsTheRenditionFormats() = runTest {
        val url = "http://iptv.orf.at/stories/2275236/"
        val page = """
            <html><head>
            <meta property="og:title" content="Fixture Story - iptv.ORF.at">
            <meta property="og:description" content="Fixture description">
            <meta name="dc.date" content="2015-04-25">
            </head><body><div data-video="350612"></div></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "http://iptv.orf.at/stories/2275236",
                contentType = "text/html",
                body = page,
            ),
            FixtureRoute(
                urlPattern = "http://bits.orf.at/filehandler/static-api/json/current/data.json?file=350612",
                contentType = "application/json",
                body = """
                    [{"duration": 68197, "sources": {"default": {
                       "loadBalancerUrl": "https://media.example/lb",
                       "audioBitrate": 128000, "bitrate": 800000, "videoFps": 25,
                       "videoWidth": 640, "videoHeight": 360,
                       "preview": "https://media.example/thumb.jpg"}}}]
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://media.example/lb",
                contentType = "application/json",
                body = """
                    {"redirect": {"hls1": "https://media.example/hls/master.m3u8",
                                  "rtmp": "rtmp://media.example/live"}}
                """.trimIndent(),
            ),
        )
        val info = ORFIPTVIE(http(transfer)).extract(url)
        assertEquals("350612", info.id)
        assertEquals("Fixture Story", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(68.197, info.duration)
        assertEquals("20150425", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats.first { it.formatId == "hls1" }.protocol)
    }

    @Test
    fun fm4StoryYieldsOneMediaPerVideoId() = runTest {
        val url = "http://fm4.orf.at/stories/2865738/"
        val page = """
            <html><head><meta property="og:title" content="Fixture Session - fm4.ORF.at"></head><body>
            <div data-video="547792"></div>
            <div data-video="547798"></div>
            </body></html>
        """.trimIndent()
        fun dataJson(id: String) = """
            [{"duration": 1748520, "sources": {"q8c": {
               "loadBalancerUrl": "https://media.example/lb-$id",
               "preview": "https://media.example/thumb-$id.jpg"}}}]
        """.trimIndent()
        fun renditionJson(id: String) = """
            {"redirect": {"hls1": "https://media.example/hls/$id.m3u8"}}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "http://bits.orf.at/filehandler/static-api/json/current/data.json?file=547792",
                contentType = "application/json",
                body = dataJson("547792"),
            ),
            FixtureRoute(
                urlPattern = "http://bits.orf.at/filehandler/static-api/json/current/data.json?file=547798",
                contentType = "application/json",
                body = dataJson("547798"),
            ),
            FixtureRoute(urlPattern = "https://media.example/lb-547792", contentType = "application/json", body = renditionJson("547792")),
            FixtureRoute(urlPattern = "https://media.example/lb-547798", contentType = "application/json", body = renditionJson("547798")),
        )
        val info = ORFFM4StoryIE(http(transfer)).extract(url)
        assertEquals(2, info.media.size)
        assertEquals("547792", info.media[0].mediaId)
        assertEquals("Fixture Session", info.media[0].title)
        assertEquals("Fixture Session (2)", info.media[1].title)
        assertEquals("https://media.example/hls/547798.m3u8", info.media[1].formats.single().url)
    }

    // ------------------------------------------------------------------ ORF ON

    @Test
    fun orfOnEpisodeYieldsFormatsAndSubtitles() = runTest {
        val url = "https://on.orf.at/video/14210000/fixture"
        val api = """
            {"id": 14210000, "title": "Fixture Episode", "description": "Fixture description",
             "exact_duration": 2651080, "age_classification": 12, "date": "2024-01-29T12:00:00Z",
             "_embedded": {
               "segments": [],
               "image": {"public_urls": {"highlight_teaser": {"url": "https://media.example/thumb.jpg"}}},
               "subtitle": {"vtt_url": "https://media.example/sub.vtt"}
             },
             "sources": {"hls": [{"src": "https://media.example/hls/master.m3u8"}],
                         "dash": [{"src": "https://media.example/dash/manifest.mpd"}]}}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api-tvthek.orf.at/api/v4.3/public/episode/encrypted/*",
                contentType = "application/json",
                body = api,
            ),
        )
        val info = ORFONIE(http(transfer)).extract(url)
        assertEquals("14210000", info.id)
        assertEquals("Fixture Episode", info.title)
        assertEquals(2651.08, info.duration)
        assertEquals(12, info.ageLimit)
        assertEquals("20240129", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats.first { it.formatId == "hls" }.protocol)
        assertEquals("de", info.subtitles.single().language)
        assertEquals("https://media.example/sub.vtt", info.subtitles.single().formats.single().url)
    }

    @Test
    fun orfOnSegmentUrlSelectsTheSegment() = runTest {
        val url = "https://on.orf.at/video/14226549/15639808/fixture"
        val api = """
            {"id": 14226549, "title": "Fixture Episode",
             "_embedded": {"segments": [
               {"id": 15639808, "title": "Fixture Segment", "exact_duration": 97707,
                "sources": {"hls": [{"src": "https://media.example/hls/segment.m3u8"}]}}]},
             "sources": {}}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api-tvthek.orf.at/api/v4.3/public/episode/encrypted/*",
                contentType = "application/json",
                body = api,
            ),
        )
        val info = ORFONIE(http(transfer)).extract(url)
        assertEquals("15639808", info.id)
        assertEquals("Fixture Segment", info.title)
        assertEquals(1, info.formats.size)
    }

    @Test
    fun orfOnDrmFailsTyped() = runTest {
        val url = "https://on.orf.at/video/14210000/fixture"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api-tvthek.orf.at/api/v4.3/public/episode/encrypted/*",
                contentType = "application/json",
                body = """{"id": 14210000, "is_drm_protected": true}""",
            ),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            ORFONIE(http(transfer)).extract(url)
        }
        assertTrue(error.message!!.contains("DRM"))
    }

    // --------------------------------------------------------------- harness

    @Test
    fun radioPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://ooe.orf.at/player/20220801/OGMO"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("OGMO"),
                "title" to Expect.Value("Fixture Show"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "http://audioapi.orf.at/ooe/api/json/current/broadcast/OGMO/20220801",
                    contentType = "application/json",
                    body = """
                        {"title": "Fixture Show", "subtitle": "Fixture subtitle",
                         "streams": [{"loopStreamId": "fixture_stream.mp3"}]}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ORFRadioIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun orfOnIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://on.orf.at/video/14210000/fixture"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("14210000"),
                "title" to Expect.Value("Fixture Episode"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api-tvthek.orf.at/api/v4.3/public/episode/encrypted/*",
                    contentType = "application/json",
                    body = """
                        {"id": 14210000, "title": "Fixture Episode",
                         "sources": {"hls": [{"src": "https://media.example/hls/master.m3u8"}]}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ORFONIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
