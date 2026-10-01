package com.anydownload.core.extract.wistia

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
 * Fixture cases for the Wistia subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class WistiaIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val mediaId = "a6ndpko1wg"

    private val mediaConfig = """
        {"media": {"hashedId": "$mediaId", "name": "Fixture Wistia",
          "seoDescription": "Fixture description", "duration": 966.0, "createdAt": 1616614369,
          "assets": [
            {"type": "original", "url": "https://media.example/video/original.mp4", "status": 2,
             "width": 1280, "height": 720, "codec": "h264", "ext": "mp4"},
            {"type": "hls_video", "url": "https://media.example/hls/master.bin", "status": 2,
             "display_name": "720p", "container": "m3u8"},
            {"type": "still_image", "url": "https://media.example/thumb.bin", "width": 640, "height": 360}],
          "captions": [{"language": "en"}]}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            WistiaIE(http(transfer())) to "wistia:$mediaId",
            WistiaIE(http(transfer())) to "http://fast.wistia.net/embed/iframe/$mediaId",
            WistiaIE(http(transfer())) to "http://fast.wistia.net/embed/medias/$mediaId.json",
            WistiaPlaylistIE(http(transfer())) to "https://fast.wistia.net/embed/playlists/aodt9etokc",
            WistiaChannelIE(http(transfer())) to "https://fast.wistia.net/embed/channel/3802iirk0l",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(WistiaIE(http(transfer())).suitable("https://example.com/embed/medias/$mediaId"))
    }

    // ----------------------------------------------------------------- media

    @Test
    fun mediaConfigYieldsFormatsThumbnailsAndCaptions() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "http://fast.wistia.net/embed/medias/$mediaId.json",
                contentType = "application/json",
                body = mediaConfig,
            ),
        )
        val info = WistiaIE(http(transfer)).extract("wistia:$mediaId")
        assertEquals(mediaId, info.id)
        assertEquals("Fixture Wistia", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(966.0, info.duration)
        assertEquals("20210324", info.uploadDate)
        assertEquals(3, info.formats.size)
        assertEquals(1, info.formats.first { it.formatId == "original" }.preference)
        assertEquals("ts", info.formats.first { it.url?.endsWith(".ts") == true }.ext)
        assertEquals("m3u8_native", info.formats.first { it.protocol == "m3u8_native" }.protocol)
        assertEquals("https://media.example/thumb.bin", info.thumbnails.single().url)
        assertEquals("en", info.subtitles.single().language)
    }

    // -------------------------------------------------------------- playlist

    @Test
    fun playlistConfigYieldsEntries() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "http://fast.wistia.net/embed/playlists/aodt9etokc.json",
                contentType = "application/json",
                body = """
                    [{"medias": [{"embed_config": $mediaConfig}]}]
                """.trimIndent(),
            ),
        )
        val info = WistiaPlaylistIE(http(transfer)).extract("https://fast.wistia.net/embed/playlists/aodt9etokc")
        assertEquals("aodt9etokc", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("wistia:$mediaId", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun mediaIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "wistia:$mediaId",
            infoDict = mapOf(
                "id" to Expect.Value(mediaId),
                "title" to Expect.Value("Fixture Wistia"),
                "duration" to Expect.Value(966.0),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "http://fast.wistia.net/embed/medias/$mediaId.json",
                    contentType = "application/json",
                    body = """
                        {"media": {"hashedId": "$mediaId", "name": "Fixture Wistia", "duration": 966.0,
                          "assets": [{"type": "original", "url": "https://media.example/video/original.mp4",
                                      "status": 2, "ext": "mp4"}]}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> WistiaIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
