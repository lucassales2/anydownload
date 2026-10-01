package com.anydownload.core.extract.streaks

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
 * Fixture cases for the STREAKS subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class StreaksIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val mediaId = "ba2c253508914d9ea061a5f26bc58b20"
    private val playersUrl =
        "https://players.streaks.jp/tipness/08155cd19dc14c12bebefb69b92eafcc/index.html?m=$mediaId"
    private val playbackUrl = "https://playback.api.streaks.jp/v1/projects/tipness/medias/$mediaId"
    private val liveUrl = "https://playback.api.streaks.jp/v1/projects/tbs/medias/ref:simul-02"

    private val mediaJson = """
        {"id": "$mediaId", "type": "file", "name": "Fixture Streaks Title",
         "description": "<p>Fixture description</p>", "duration": 265.344,
         "created_at": "2023-09-08T00:00:00Z", "updated_at": "2023-09-08T00:00:00Z",
         "thumbnail": {"src": "https://media.example/thumb.jpg"},
         "sources": [{"id": "s1", "src": "https://media.example/master.m3u8",
           "type": "application/x-mpegURL"}],
         "tracks": [{"kind": "captions", "src": "https://media.example/captions.vtt", "srclang": "JA"}]}
    """.trimIndent()

    private val playbackRoute = FixtureRoute(
        urlPattern = "https://playback.api.streaks.jp/v1/projects/tipness/medias/$mediaId",
        contentType = "application/json",
        body = mediaJson,
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val extractor = StreaksIE(http(transfer()))
        val cases = listOf(
            playersUrl,
            playbackUrl,
            "https://players.streaks.jp/ktv-web/0298e8964c164ab384c07ef6e08c444b/index.html?m=ref:mycoffeetime_250317",
        )
        for (url in cases) {
            assertTrue(extractor.suitable(url), "Streaks must match: $url")
        }
        assertFalse(extractor.suitable("https://www.example.com/v1/projects/tipness/medias/$mediaId"))
    }

    // --------------------------------------------------------------- playback

    @Test
    fun playbackApiYieldsTheHlsRowAndMetadata() = runTest {
        val info = StreaksIE(http(transfer(playbackRoute))).extract(playbackUrl)
        assertEquals(mediaId, info.id)
        assertEquals("Fixture Streaks Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(265.344, info.duration)
        assertEquals("20230908", info.uploadDate)
        assertEquals("tipness", info.channelId)
        assertEquals(false, info.isLive)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals(1, info.subtitles.size)
        assertEquals("ja", info.subtitles[0].language)
        assertEquals("https://media.example/captions.vtt", info.subtitles[0].formats.single().url)
    }

    @Test
    fun liveSourceMergesTheSsaiSessionQuery() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = liveUrl,
                contentType = "application/json",
                body = """
                    {"id": "c4e83a7b48f4409a96adacec674b4e22", "type": "live", "name": "Live Fixture",
                     "sources": [{"id": "src1", "src": "https://media.example/live/master.m3u8",
                       "type": "application/x-mpegURL", "ssai": {"enabled": true}}]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://ssai.api.streaks.jp/v1/projects/tbs/medias/c4e83a7b48f4409a96adacec674b4e22/ssai/session",
                method = "POST",
                contentType = "application/json",
                body = """[{"query": {"token": "fake_value"}}]""",
            ),
        )
        val info = StreaksIE(http(transfer)).extract(liveUrl)
        assertEquals("c4e83a7b48f4409a96adacec674b4e22", info.id)
        assertEquals(true, info.isLive)
        assertEquals("https://media.example/live/master.m3u8?token=fake_value", info.formats.single().url)
    }

    @Test
    fun drmOnlySourcesFailTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = playbackUrl,
                contentType = "application/json",
                body = """
                    {"id": "$mediaId", "type": "file", "name": "Fixture",
                     "sources": [{"src": "https://media.example/drm.m3u8", "type": "application/x-mpegURL",
                       "key_systems": {"widevine": {"license_url": "https://media.example/license"}}}]}
                """.trimIndent(),
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            StreaksIE(http(transfer)).extract(playbackUrl)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun playbackIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = playbackUrl,
            infoDict = mapOf(
                "id" to Expect.Value(mediaId),
                "title" to Expect.Value("Fixture Streaks Title"),
                "upload_date" to Expect.Value("20230908"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(playbackRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> StreaksIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun playersPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = playersUrl,
            infoDict = mapOf(
                "id" to Expect.Value(mediaId),
                "title" to Expect.Value("Fixture Streaks Title"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(playbackRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> StreaksIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
