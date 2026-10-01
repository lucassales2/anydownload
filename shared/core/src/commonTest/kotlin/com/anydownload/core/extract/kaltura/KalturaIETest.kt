package com.anydownload.core.extract.kaltura

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
 * Fixture cases for the Kaltura subset. Every id, host, and media address is
 * synthesized (`*.example`); the widget session key is the literal
 * `fake_ks`, and no cookie or signed media URL appears.
 */
class KalturaIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val multirequestRoute = FixtureRoute(
        urlPattern = "https://cdnapisec.kaltura.com/api_v3/service/multirequest",
        method = "POST",
        contentType = "application/json",
        body = """
            [
              {"objectType": "KalturaStartWidgetSessionResponse", "ks": "fake_ks"},
              {"objects": [{"id": "1_1jc2y3e4", "name": "Fixture Kaltura Title",
                            "description": "Fixture description",
                            "dataUrl": "https://cdnapisec.kaltura.com/p/269692/sp/26969200/playManifest/entryId/1_1jc2y3e4/format/url/protocol/http",
                            "duration": 100, "createdAt": 1387419540, "plays": 42,
                            "thumbnailUrl": "https://img.example/kaltura.jpg",
                            "userId": "fixture-user"}], "totalCount": 1},
              [{"id": "flavor1", "status": 2, "fileExt": "mp4", "bitrate": 1500,
                "frameRate": 25, "size": 1048576, "containerFormat": "isom",
                "height": 720, "width": 1280, "videoCodecId": "avc1"}],
              {"objects": [{"id": "cap1", "status": 2, "languageCode": "en",
                            "format": 3, "fileExt": "vtt"}]}
            ]
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            KalturaIE(http(transfer())) to "kaltura:269692:1_1jc2y3e4",
            KalturaIE(http(transfer())) to "kaltura:269692:1_1jc2y3e4:html5",
            KalturaIE(http(transfer())) to
                "http://www.kaltura.com/index.php/kwidget/cache_st/1300318621/wid/_269692/uiconf_id/3873291/entry_id/1_1jc2y3e4",
            KalturaIE(http(transfer())) to
                "https://cdnapisec.kaltura.com/html5/html5lib/v2.30.2/mwEmbedFrame.php/p/1337/uiconf_id/20540612/entry_id/1_sf5ovm7u?wid=_243342",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(KalturaIE(http(transfer())).suitable("https://example.com/watch/1"))
    }

    // ------------------------------------------------------------ extraction

    @Test
    fun keywordFormMapsTheMultirequestResponse() = runTest {
        val transfer = transfer(multirequestRoute)
        val info = KalturaIE(http(transfer)).extract("kaltura:269692:1_1jc2y3e4")

        assertEquals("1_1jc2y3e4", info.id)
        assertEquals("Fixture Kaltura Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(100.0, info.duration)
        assertEquals("20131219", info.uploadDate)
        assertEquals("fixture-user", info.uploader)
        assertEquals(42L, info.viewCount)
        assertEquals("https://img.example/kaltura.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("mp4-1500", info.formats[0].formatId)
        assertEquals(720L, info.formats[0].height)
        assertEquals("hls", info.formats[1].formatId)
        assertEquals("m3u8_native", info.formats[1].protocol)
        assertTrue(info.formats[0].url!!.endsWith("/flavorId/flavor1"))
        assertEquals("en", info.subtitles.single().language)
        assertEquals("vtt", info.subtitles.single().formats.single().ext)
    }

    @Test
    fun indexPhpPathFormReadsTheEntryId() = runTest {
        val url = "https://cdnapisec.kaltura.com/index.php/kwidget/wid/_269692/uiconf_id/3873291/" +
            "entry_id/1_1jc2y3e4"
        val transfer = transfer(multirequestRoute)
        val info = KalturaIE(http(transfer)).extract(url)
        assertEquals("1_1jc2y3e4", info.id)
        assertEquals("Fixture Kaltura Title", info.title)
    }

    @Test
    fun playlistIframeDataBecomesChildEntries() = runTest {
        val url = "https://cdnapisec.kaltura.com/index.php/kwidget/wid/_269692/uiconf_id/3873291/" +
            "flashvars[playlistAPI.kpl0Id]/pl1"
        val page = """
            <html><script>window.kalturaIframePackageData = {"playlistResult":
              {"pl1": {"name": "Fixture Playlist", "items": [{"id": "1_abc"}]}}};</script></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = KalturaIE(http(transfer)).extract(url)
        assertEquals("pl1", info.id)
        assertEquals("Fixture Playlist", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("kaltura:269692:1_abc:html5", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun kalturaIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "kaltura:269692:1_1jc2y3e4",
            infoDict = mapOf(
                "id" to Expect.Value("1_1jc2y3e4"),
                "title" to Expect.Value("Fixture Kaltura Title"),
                "duration" to Expect.Value(100.0),
                "upload_date" to Expect.Value("20131219"),
                "formats.1.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(multirequestRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> KalturaIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
