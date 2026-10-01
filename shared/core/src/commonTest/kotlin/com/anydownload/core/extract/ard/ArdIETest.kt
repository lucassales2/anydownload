package com.anydownload.core.extract.ard

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
 * Fixture cases for the ARD subset. Every id, host, and media address is
 * synthesized (`*.example`); no cookie, token, or signed URL appears.
 */
class ArdIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val betaId = "Y3JpZDovL21kci5kZS9zZW5kdW5nLzI4MjA0MC80MjIwOTEtNDAyNTM0"
    private val betaUrl = "https://www.ardmediathek.de/video/filme-im-mdr/liebe-auf-vier-pfoten/" +
        "mdr-fernsehen/$betaId"

    private fun betaJson(blockedByFsk: Boolean = false): String = """
        {"tracking": {"atiCustomVars": {"contentId": 12939099}},
         "fskRating": "FSK12",
         "widgets": [{"type": "player_ondemand", "blockedByFsk": $blockedByFsk,
           "mediaCollection": {"embedded": {
             "streams": [{"kind": "main", "media": [
               {"url": "https://media.example/hls/master.m3u8",
                "audios": [{"kind": "standard", "languageCode": "deu"}],
                "maxHResolutionPx": 1280, "maxVResolutionPx": 720, "videoCodec": "h264"},
               {"url": "https://media.example/video.mp4",
                "audios": [{"kind": "standard", "languageCode": "eng"}],
                "maxHResolutionPx": 1280, "maxVResolutionPx": 720}
             ]}],
             "subtitles": [{"languageCode": "deu",
                            "sources": [{"url": "https://media.example/subs.vtt", "kind": "webvtt"}]}],
             "pluginData": {"jumpmarks@all": {"chapterArray": [
               {"chapterTime": 0, "chapterTitle": "Intro"},
               {"chapterTime": 100, "chapterTitle": "Teil 2"}
             ]}},
             "meta": {"title": "Fixture ARD Title", "synopsis": "Fixture description",
                      "broadcastedOnDateTime": "2023-11-30T10:00:00Z",
                      "durationSeconds": 5222, "clipSourceName": "MDR",
                      "images": [{"url": "https://img.example/thumb.jpg"}]}
           }}
         }]}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ARDBetaMediathekIE(http(transfer())) to betaUrl,
            ARDBetaMediathekIE(http(transfer())) to
                "https://www.ardmediathek.de/swr/live/Y3JpZDovL3N3ci5kZS8xMzQ4MTA0Mg",
            ARDMediathekCollectionIE(http(transfer())) to
                "https://www.ardmediathek.de/sendung/tatort/Y3JpZDovL2Rhc2Vyc3RlLmRlL3RhdG9ydA",
            ARDMediathekCollectionIE(http(transfer())) to
                "https://www.ardmediathek.de/serie/quiz/staffel-1-originalversion/" +
                "Y3JpZDovL3dkci5kZS9vbmUvcXVpeg/1/OV",
            ARDAudiothekIE(http(transfer())) to
                "https://www.ardaudiothek.de/episode/urn:ard:episode:eabead1add170e93/",
            ARDAudiothekIE(http(transfer())) to
                "https://www.ardsounds.de/episode/urn:ard:extra:d2fe7303d2dcbf5d/",
            ARDAudiothekPlaylistIE(http(transfer())) to
                "https://www.ardaudiothek.de/sendung/mia-insomnia/urn:ard:show:c405aa26d9a4060a/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ARDAudiothekIE(http(transfer())).suitable("https://www.ardaudiothek.de/sendung/x"))
        assertFalse(ARDMediathekCollectionIE(http(transfer())).suitable(betaUrl))
    }

    // ------------------------------------------------------ ARDBetaMediathekIE

    @Test
    fun betaMediathekMapsStreamsSubtitlesAndChapters() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.ardmediathek.de/page-gateway/pages/ard/item/$betaId*",
                contentType = "application/json",
                body = betaJson(),
            ),
        )
        val info = ARDBetaMediathekIE(http(transfer)).extract(betaUrl)

        assertEquals("12939099", info.id)
        assertEquals("Fixture ARD Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(5222.0, info.duration)
        assertEquals("20231130", info.uploadDate)
        assertEquals("MDR", info.channel)
        assertEquals(12, info.ageLimit)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("deu", info.formats[0].language)
        assertEquals(10.0, info.formats[0].languagePreference)
        assertEquals("eng", info.formats[1].language)
        assertEquals(2, info.chapters.size)
        assertEquals("Teil 2", info.chapters[1].title)
        assertEquals("deu", info.subtitles.single().language)
        assertEquals("vtt", info.subtitles.single().formats.single().ext)
    }

    @Test
    fun fskBlockedItemFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.ardmediathek.de/page-gateway/pages/ard/item/$betaId*",
                contentType = "application/json",
                body = betaJson(blockedByFsk = true),
            ),
        )
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            ARDBetaMediathekIE(http(transfer)).extract(betaUrl)
        }
        assertTrue(error.message!!.contains("age verified"))
    }

    // ------------------------------------------------- ARDMediathekCollectionIE

    @Test
    fun collectionPagesThroughTheTeasers() = runTest {
        val playlistId = "Y3JpZDovL2Rhc2Vyc3RlLmRlL3RhdG9ydA"
        val url = "https://www.ardmediathek.de/sendung/tatort/$playlistId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.ardmediathek.de/page-gateway/widgets/ard/asset/" +
                    "$playlistId?pageNumber=0&pageSize=100",
                contentType = "application/json",
                body = """
                    {"title": "Tatort", "synopsis": "Fixture collection",
                     "teasers": [{"id": "item1", "longTitle": "Episode 1", "type": "video",
                                  "links": {"target": {"urlId": "item1"}}}]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://api.ardmediathek.de/page-gateway/widgets/ard/asset/" +
                    "$playlistId?pageNumber=1&pageSize=100",
                contentType = "application/json",
                body = """{"title": "Tatort", "teasers": []}""",
            ),
        )
        val info = ARDMediathekCollectionIE(http(transfer)).extract(url)
        assertEquals(playlistId, info.id)
        assertEquals("Tatort", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.ardmediathek.de/video/item1", info.entries[0].url)
    }

    // --------------------------------------------------------- ARDAudiothekIE

    @Test
    fun audiothekItemMapsTheAudioFormats() = runTest {
        val urn = "urn:ard:episode:eabead1add170e93"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.ardaudiothek.de/graphql",
                method = "POST",
                contentType = "application/json",
                body = """
                    {"data": {"item": {
                      "audioList": [{"href": "https://media.example/audio.mp3",
                                     "distributionType": "mp3", "audioBitrate": 128,
                                     "audioCodec": "mp3"}],
                      "show": {"title": "1LIVE Caiman Club"},
                      "image": {"url1X1": "https://img.example/cover.jpg?w=100"},
                      "programSet": {"publicationService": {"organizationName": "WDR"}},
                      "description": "Fixture description", "title": "CAIMAN CLUB",
                      "duration": 3339, "startDate": "2024-07-17T00:00:00Z", "episodeNumber": 4
                    }}}
                """.trimIndent(),
            ),
        )
        val info = ARDAudiothekIE(http(transfer)).extract(
            "https://www.ardaudiothek.de/episode/$urn/",
        )
        assertEquals(urn, info.id)
        assertEquals("CAIMAN CLUB", info.title)
        assertEquals("WDR", info.channel)
        assertEquals(3339.0, info.duration)
        assertEquals("20240717", info.uploadDate)
        assertEquals("https://img.example/cover.jpg", info.thumbnails.single().url)
        assertEquals("mp3", info.formats.single().formatId)
        assertEquals("none", info.formats.single().vcodec)
        assertEquals(128.0, info.formats.single().abr)
    }

    @Test
    fun audiothekPlaylistListsTheShowItems() = runTest {
        val urn = "urn:ard:show:c405aa26d9a4060a"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.ardaudiothek.de/graphql",
                method = "POST",
                contentType = "application/json",
                body = """
                    {"data": {"show": {"title": "Mia Insomnia", "description": "Fixture show",
                      "items": {"nodes": [
                        {"url": "https://www.ardaudiothek.de/episode/urn:ard:episode:eabead1add170e93/"}
                      ]}}}}
                """.trimIndent(),
            ),
        )
        val info = ARDAudiothekPlaylistIE(http(transfer)).extract(
            "https://www.ardaudiothek.de/sendung/mia-insomnia/$urn/",
        )
        assertEquals(urn, info.id)
        assertEquals("Mia Insomnia", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("urn:ard:episode:eabead1add170e93", info.entries[0].id)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun betaMediathekIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = betaUrl,
            infoDict = mapOf(
                "id" to Expect.Value("12939099"),
                "title" to Expect.Value("Fixture ARD Title"),
                "duration" to Expect.Value(5222.0),
                "age_limit" to Expect.Value(12),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api.ardmediathek.de/page-gateway/pages/ard/item/$betaId*",
                    contentType = "application/json",
                    body = betaJson(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ARDBetaMediathekIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun audiothekIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val urn = "urn:ard:episode:eabead1add170e93"
        val case = ExtractorCase(
            url = "https://www.ardaudiothek.de/episode/$urn/",
            infoDict = mapOf(
                "id" to Expect.Value(urn),
                "title" to Expect.Value("CAIMAN CLUB"),
                "formats.0.format_id" to Expect.Value("mp3"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api.ardaudiothek.de/graphql",
                    method = "POST",
                    contentType = "application/json",
                    body = """
                        {"data": {"item": {"audioList": [{"href": "https://media.example/audio.mp3",
                                                         "distributionType": "mp3"}],
                                           "title": "CAIMAN CLUB"}}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ARDAudiothekIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
