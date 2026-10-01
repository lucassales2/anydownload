package com.anydownload.core.extract.zdf

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
 * Fixture cases for the ZDF subset. Every id, host, and media address is
 * synthesized (`*.example`); `fake_value` stands in for the API token, and no
 * cookie, account token, or signed URL appears.
 */
class ZdfIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl = "https://www.zdf.de/video/dokus/fixture-video-100"
    private val ptmdUrl = "https://api.zdf.de/tmd/2/android_native_6/vod/ptmd/mediathek/fixture-content/3"

    private val tokenRoute = FixtureRoute(
        urlPattern = "https://zdf-prod-futura.zdf.de/mediathekV2/token",
        contentType = "application/json",
        body = """{"type": "Bearer", "token": "fake_value"}""",
    )

    private val graphqlVideoRoute = FixtureRoute(
        urlPattern = "https://api.zdf.de/graphql",
        method = "POST",
        contentType = "application/json",
        body = """
            {"data": {"videoByCanonical": {
              "canonical": "fixture-video-100",
              "title": "Fixture ZDF Title",
              "leadParagraph": "Fixture description",
              "editorialDate": "2026-05-19T00:00:00Z",
              "teaser": {"image": {"list": {
                "original": "https://img.example/original.jpg",
                "1920x1080": "https://img.example/1920x1080.jpg"
              }}},
              "currentMedia": {"nodes": [{
                "ptmdTemplate": "/tmd/2/{playerId}/vod/ptmd/mediathek/fixture-content/3",
                "aspectRatio": "16:9",
                "streamAnchorTags": {"nodes": [{"anchorOffset": 0, "anchorLabel": "Intro"}]}
              }]}
            }}}
        """.trimIndent(),
    )

    private val ptmdRoute = FixtureRoute(
        urlPattern = ptmdUrl,
        contentType = "application/json",
        body = """
            {"basename": "fixture-content",
             "attributes": {"duration": {"value": 5304000}},
             "captions": [{"uri": "https://media.example/captions.vtt", "language": "deu"}],
             "priorityList": [{"type": "h264", "formitaeten": [{"qualities": [
               {"highestVerticalResolution": 720, "mimeCodec": "avc1.4d401f,mp4a.40.2",
                "audio": {"tracks": [{"uri": "https://media.example/hls/master.m3u8",
                                      "language": "deu", "class": "main"}]}},
               {"highestVerticalResolution": 360, "mimeCodec": "avc1.4d401e,mp4a.40.2",
                "audio": {"tracks": [{"uri": "https://media.example/video_800k_360.mp4",
                                      "language": "deu", "class": "main", "filesize": 12345}]}}
             ]}]}]}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ZDFIE(http(transfer())) to videoUrl,
            ZDFIE(http(transfer())) to "https://www.zdf.de/play/dokus/fixture-video-100/fixture-video-100",
            ZDFIE(http(transfer())) to "https://www.zdf.de/dokus/fixture-video-100.html",
            ZDFIE(http(transfer())) to "https://www.zdfheute.de/politik/fixture-100.html",
            ZDFChannelIE(http(transfer())) to "https://www.zdf.de/sport/fixture-show-220",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ZDFChannelIE(http(transfer())).suitable(videoUrl))
        assertFalse(ZDFIE(http(transfer())).suitable("https://example.com/watch/1"))
    }

    // ------------------------------------------------------------------ ZDFIE

    @Test
    fun graphqlVideoMapsPtmdFormatsAndMetadata() = runTest {
        val transfer = transfer(tokenRoute, graphqlVideoRoute, ptmdRoute)
        val info = ZDFIE(http(transfer)).extract(videoUrl)

        assertEquals("fixture-video-100", info.id)
        assertEquals("Fixture ZDF Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(5304.0, info.duration)
        assertEquals("20260519", info.uploadDate)
        assertEquals(2, info.thumbnails.size)
        assertEquals("https://img.example/original.jpg", info.thumbnails[0].url)
        assertEquals(1920L, info.thumbnails[1].width)
        assertEquals(1, info.chapters.size)
        assertEquals("Intro", info.chapters[0].title)
        assertEquals("deu", info.subtitles.single().language)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mp4", info.formats[1].ext)
        assertEquals(360L, info.formats[1].height)
        assertEquals(640L, info.formats[1].width)
        assertEquals(800.0, info.formats[1].tbr)
        assertEquals("deu", info.formats[1].language)
        assertTrue(transfer.requests.any { it.headers["api-auth"] == "Bearer fake_value" })
    }

    @Test
    fun graphqlNullFallsBackToTheDocumentApi() = runTest {
        val documentUrl = "https://zdf-prod-futura.zdf.de/mediathekV2/document/fixture-doc"
        val transfer = transfer(
            tokenRoute,
            FixtureRoute(
                urlPattern = "https://api.zdf.de/graphql",
                method = "POST",
                contentType = "application/json",
                body = """{"data": {"videoByCanonical": null}}""",
            ),
            FixtureRoute(
                urlPattern = documentUrl,
                contentType = "application/json",
                body = """
                    {"document": {
                      "titel": "Fixture Doc",
                      "beschreibung": "Fixture doc description",
                      "date": "2026-05-19T00:00:00Z",
                      "streamApiUrlAndroid": "https://api.zdf.de/tmd/2/android_native_6/vod/ptmd/mediathek/fixture-doc/1",
                      "captions": [{"uri": "https://media.example/doc.vtt", "language": "deu"}],
                      "teaserBild": {"original": {"url": "https://img.example/doc.jpg",
                                                  "width": 1920, "height": 1080}}
                    }, "meta": {}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://api.zdf.de/tmd/2/android_native_6/vod/ptmd/mediathek/fixture-doc/1",
                contentType = "application/json",
                body = """
                    {"attributes": {"duration": {"value": 1000}},
                     "priorityList": [{"type": "h264", "formitaeten": [{"qualities": [
                       {"highestVerticalResolution": 720, "mimeCodec": "avc1.4d401f,mp4a.40.2",
                        "audio": {"tracks": [{"uri": "https://media.example/doc/master.m3u8",
                                              "language": "deu", "class": "main"}]}}
                     ]}]}]}
                """.trimIndent(),
            ),
        )
        val info = ZDFIE(http(transfer)).extract("https://www.zdfheute.de/politik/fixture-doc.html")
        assertEquals("fixture-doc", info.id)
        assertEquals("Fixture Doc", info.title)
        assertEquals("Fixture doc description", info.description)
        assertEquals("20260519", info.uploadDate)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://media.example/doc.vtt", info.subtitles.single().formats.single().url)
    }

    // ------------------------------------------------------------ ZDFChannelIE

    @Test
    fun channelListsSeasonEpisodes() = runTest {
        val url = "https://www.zdf.de/sport/fixture-show-220"
        val transfer = transfer(
            tokenRoute,
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = "ok",
            ),
            FixtureRoute(
                urlPattern = "https://api.zdf.de/graphql?operationName=GetSmartCollectionByCanonical*",
                contentType = "application/json",
                body = """
                    {"data": {"smartCollectionByCanonical": {
                      "title": "Fixture Show",
                      "infoText": "Fixture show description",
                      "video": null,
                      "seasons": {"seasons": [{"number": 1}]}
                    }}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://api.zdf.de/graphql?operationName=seasonByCanonical*",
                contentType = "application/json",
                body = """
                    {"data": {"smartCollectionByCanonical": {"seasons": {"nodes": [{
                      "episodes": {"nodes": [{
                        "sharingUrl": "https://www.zdf.de/video/dokus/fixture-show/episode-1",
                        "canonical": "ep-1",
                        "teaser": {"title": "Episode 1"}
                      }], "pageInfo": {"hasNextPage": false, "endCursor": "x"}}
                    }]}}}}
                """.trimIndent(),
            ),
        )
        val info = ZDFChannelIE(http(transfer)).extract(url)
        assertEquals("fixture-show-220", info.id)
        assertEquals("Fixture Show", info.title)
        assertEquals("Fixture show description", info.description)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.zdf.de/video/dokus/fixture-show/episode-1", info.entries[0].url)
        assertEquals("Episode 1", info.entries[0].title)
    }

    @Test
    fun channelSingleVideoRedirectsToZdf() = runTest {
        val url = "https://www.zdf.de/dokus/fixture-video-100"
        val transfer = transfer(
            tokenRoute,
            FixtureRoute(urlPattern = url, contentType = "text/html", body = "ok"),
            FixtureRoute(
                urlPattern = "https://api.zdf.de/graphql?operationName=GetSmartCollectionByCanonical*",
                contentType = "application/json",
                body = """
                    {"data": {"smartCollectionByCanonical": {
                      "title": "Fixture Collection",
                      "video": {"canonical": "fixture-video-100",
                                "sharingUrl": "https://www.zdf.de/video/dokus/fixture-video-100"},
                      "seasons": {"seasons": []}
                    }}}
                """.trimIndent(),
            ),
        )
        val info = ZDFChannelIE(http(transfer)).extract(url)
        assertEquals("fixture-video-100", info.id)
        assertEquals("https://www.zdf.de/video/dokus/fixture-video-100", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun zdfIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("fixture-video-100"),
                "title" to Expect.Value("Fixture ZDF Title"),
                "duration" to Expect.Value(5304.0),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
                "formats.1.height" to Expect.Value(360L),
            ),
            routes = listOf(tokenRoute, graphqlVideoRoute, ptmdRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ZDFIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun zdfChannelIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.zdf.de/sport/fixture-show-220"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("fixture-show-220"),
                "title" to Expect.Value("Fixture Show"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                tokenRoute,
                FixtureRoute(urlPattern = url, contentType = "text/html", body = "ok"),
                FixtureRoute(
                    urlPattern = "https://api.zdf.de/graphql?operationName=GetSmartCollectionByCanonical*",
                    contentType = "application/json",
                    body = """
                        {"data": {"smartCollectionByCanonical": {
                          "title": "Fixture Show", "video": null,
                          "seasons": {"seasons": [{"number": 1}]}
                        }}}
                    """.trimIndent(),
                ),
                FixtureRoute(
                    urlPattern = "https://api.zdf.de/graphql?operationName=seasonByCanonical*",
                    contentType = "application/json",
                    body = """
                        {"data": {"smartCollectionByCanonical": {"seasons": {"nodes": [{
                          "episodes": {"nodes": [{
                            "sharingUrl": "https://www.zdf.de/video/dokus/fixture-show/episode-1",
                            "canonical": "ep-1", "teaser": {"title": "Episode 1"}
                          }], "pageInfo": {"hasNextPage": false}}
                        }]}}}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ZDFChannelIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
