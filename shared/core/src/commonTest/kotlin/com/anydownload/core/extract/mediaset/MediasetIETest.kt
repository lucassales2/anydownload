package com.anydownload.core.extract.mediaset

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
 * Fixture cases for the Mediaset subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class MediasetIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val guid = "F310575103000102"

    private val metadataJson = """
        {"title": "Fixture Mediaset", "description": "Fixture description",
         "duration": 2682000, "pubDate": 1622413946000}
    """.trimIndent()

    private fun smilXml() = """
        <smil><body><switch>
        <video src="https://media.example/video/720.mp4" width="1280" height="720"/>
        <video src="https://media.example/hls/master.m3u8" type="video/mp4"/>
        <textstream src="https://media.example/cc/it.vtt" lang="it" type="text/vtt"/>
        </switch></body></smil>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            MediasetIE(http(transfer())) to
                "https://mediasetinfinity.mediaset.it/video/mrwronglezionidamore/episodio-1_$guid",
            MediasetIE(http(transfer())) to "mediaset:$guid",
            MediasetShowIE(http(transfer())) to
                "https://mediasetinfinity.mediaset.it/programmi-tv/leiene/leiene_SE000000000061",
            MediasetShowIE(http(transfer())) to
                "https://mediasetinfinity.mediaset.it/programmi-tv/leiene/iservizi_SE000000000061,ST000000002763,sb100013375",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(MediasetIE(http(transfer())).suitable("https://mediasetinfinity.mediaset.it/programmi-tv/leiene/leiene_SE000000000061"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun smilManifestsYieldFormatsAndMetadata() = runTest {
        val url = "mediaset:$guid"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://link.theplatform.eu/s/PR1GhC/media/guid/2702976343/$guid?format=preview",
                contentType = "application/json",
                body = metadataJson,
            ),
            FixtureRoute(
                urlPattern = "http://link.theplatform.eu/s/PR1GhC/media/guid/2702976343/$guid?*formats=MPEG4*",
                contentType = "text/xml",
                body = smilXml(),
            ),
            FixtureRoute(
                urlPattern = "https://feed.entertainment.tv.theplatform.eu/f/PR1GhC/mediaset-prod-all-programs-v2/guid/-/$guid",
                contentType = "application/json",
                body = """
                    {"description": "Fixture feed description",
                     "mediasetprogram${'$'}publishInfo": {"description": "Canale 5", "channel": "C5"},
                     "mediasetprogram${'$'}numberOfViews": "42",
                     "thumbnails": {"image_keyframe_poster-1": {"url": "https://media.example/thumb.jpg"}}}
                """.trimIndent(),
            ),
        )
        val info = MediasetIE(http(transfer)).extract(url)
        assertEquals(guid, info.id)
        assertEquals("Fixture Mediaset", info.title)
        assertEquals(2682.0, info.duration)
        assertEquals("20210530", info.uploadDate)
        assertEquals("Canale 5", info.uploader)
        assertEquals(42L, info.viewCount)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[1].protocol)
        assertEquals("it", info.subtitles.single().language)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    // ------------------------------------------------------------------- show

    @Test
    fun showPageYieldsSeasonEntries() = runTest {
        val url = "https://mediasetinfinity.mediaset.it/programmi-tv/leiene/leiene_SE000000000061"
        val page = """
            <html><head><title>Le Iene 2022/2023 | Mediaset Infinity</title></head>
            <body><a href="/programmi-tv/leiene/episodio_SE000000000061,ST000000002763,sb100013375">x</a></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = MediasetShowIE(http(transfer)).extract(url)
        assertEquals("000000000061", info.id)
        assertEquals("Le Iene 2022/2023", info.title)
        assertEquals(1, info.entries.size)
    }

    @Test
    fun subBrandListingYieldsPagedEntries() = runTest {
        val url = "https://mediasetinfinity.mediaset.it/programmi-tv/leiene/iservizi_SE000000000061,ST000000002763,sb100013375"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://feed.entertainment.tv.theplatform.eu/f/PR1GhC/mediaset-prod-all-programs-v2?byCustomValue=*range=1-25",
                contentType = "application/json",
                body = """
                    {"entries": [{"guid": "$guid",
                      "mediasetprogram${'$'}subBrandDescription": "I servizi"}]}
                """.trimIndent(),
            ),
        )
        val info = MediasetShowIE(http(transfer)).extract(url)
        assertEquals("100013375", info.id)
        assertEquals("I servizi", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("mediaset:$guid", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "mediaset:$guid"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(guid),
                "title" to Expect.Value("Fixture Mediaset"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://link.theplatform.eu/s/PR1GhC/media/guid/2702976343/$guid?format=preview",
                    contentType = "application/json",
                    body = metadataJson,
                ),
                FixtureRoute(
                    urlPattern = "http://link.theplatform.eu/s/PR1GhC/media/guid/2702976343/$guid?*formats=MPEG4*",
                    contentType = "text/xml",
                    body = smilXml(),
                ),
                FixtureRoute(
                    urlPattern = "https://feed.entertainment.tv.theplatform.eu/f/PR1GhC/mediaset-prod-all-programs-v2/guid/-/$guid",
                    contentType = "application/json",
                    body = """{"description": "Fixture feed description"}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MediasetIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
