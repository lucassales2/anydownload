package com.anydownload.core.extract.nexx

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
 * Fixture cases for the Nexx subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class NexxIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "1701834"
    private val arcUrl = "https://arc.nexx.cloud/api/video/$videoId.json"

    private fun arcBody(streamData: String) = """
        {"result": {"general": {"ID": $videoId, "title": "Fixture Video", "subtitle": "Fixture Sub",
                                "description": "Fixture description", "runtime": "00:12:50",
                                "studio": "Fixture Studio", "uploaded": 1595600027},
                    "streamdata": $streamData,
                    "imagedata": {"thumb": "https://media.example/thumb.jpg"},
                    "captiondata": [{"language": "de", "format": "vtt", "url": "https://media.example/sub/de.vtt"}]}}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NexxIE(http(transfer())) to "https://api.nexx.cloud/v3/748/videos/byid/128907",
            NexxIE(http(transfer())) to "nexx:741:$videoId",
            NexxIE(http(transfer())) to "nexx:$videoId",
            NexxIE(http(transfer())) to "https://arc.nexx.cloud/api/video/$videoId.json",
            NexxEmbedIE(http(transfer())) to "http://embed.nexx.cloud/748/KC1614647Z27Y7T",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NexxEmbedIE(http(transfer())).suitable("https://api.nexx.cloud/v3/748/videos/byid/1"))
    }

    // ------------------------------------------------------------------- azure

    @Test
    fun azureCdnYieldsHlsDashAndProgressiveFormats() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = arcUrl,
                contentType = "application/json",
                body = arcBody(
                    """{"cdnType": "azure", "azureLocator": "fixturelocator",
                        "cdnShieldHTTP": "shield.example/",
                        "azureFileDistribution": "1000:640x360,2000:1280x720"}""",
                ),
            ),
        )
        val info = NexxIE(http(transfer)).extract("nexx:741:$videoId")
        assertEquals(videoId, info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("Fixture Studio", info.uploader)
        assertEquals(770.0, info.duration)
        assertEquals("20200724", info.uploadDate)
        assertEquals(4, info.formats.size)
        assertEquals("http://shield.example/fixturelocator/${videoId}_src.ism/Manifest(format=m3u8-aapl)", info.formats[0].url)
        assertEquals("de", info.subtitles.single().language)
    }

    // -------------------------------------------------------------------- free

    @Test
    fun freeCdnYieldsTheCsmilMaster() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = arcUrl,
                contentType = "application/json",
                body = arcBody(
                    """{"cdnType": "free", "cdnProvider": "ak", "originalDomain": "cdn.example",
                        "applyFolderHierarchy": 1, "applyAzureStructure": 0, "hash": "fixturehash",
                        "azureFileDistribution": "1500:1500,3000:3000"}""",
                ),
            ),
        )
        val info = NexxIE(http(transfer)).extract("nexx:$videoId")
        assertEquals(1, info.formats.size)
        assertEquals(
            "http://cdn.example/43/81/$videoId/fixturehash_,1500,3000,.mp4.csmil/master.m3u8",
            info.formats.single().url,
        )
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // --------------------------------------------------------------------- 3q

    @Test
    fun threeQCdnYieldsTheManifests() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = arcUrl,
                contentType = "application/json",
                body = arcBody(
                    """{"cdnType": "3q", "qAccount": "fixtureacc", "qPrefix": "p", "qLocator": "l",
                        "qHash": "h"}""",
                ),
            ),
        )
        val info = NexxIE(http(transfer)).extract("nexx:$videoId")
        assertEquals(2, info.formats.size)
        assertEquals("3q-hls", info.formats[0].formatId)
        assertTrue(info.formats[0].url.orEmpty().contains("manifest.m3u8"))
    }

    @Test
    fun nonArcVideoFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = arcUrl,
                contentType = "application/json",
                body = """{"result": []}""",
            ),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            NexxIE(http(transfer)).extract("nexx:741:$videoId")
        }
        assertTrue(error.message!!.contains("session-init"))
    }

    // ------------------------------------------------------------------- embed

    @Test
    fun embedPageRedirectsToTheApiUrl() = runTest {
        val url = "http://embed.nexx.cloud/748/KC1614647Z27Y7T"
        val page = """
            <html><head><script src="//require.nexx.cloud/748/sdk.js"></script></head><body>
            <script>onPLAYReady(function() { _play.init('player', '161464'); });</script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = NexxEmbedIE(http(transfer)).extract(url)
        assertEquals("https://api.nexx.cloud/v3/748/videos/byid/161464", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun azureVideoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "nexx:741:$videoId",
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Video"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = arcUrl,
                    contentType = "application/json",
                    body = """
                        {"result": {"general": {"ID": $videoId, "title": "Fixture Video"},
                                    "streamdata": {"cdnType": "azure", "azureLocator": "fixturelocator"}}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NexxIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun embedPageIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "http://embed.nexx.cloud/748/KC1614647Z27Y7T"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("KC1614647Z27Y7T"),
                "webpage_url" to Expect.Value(url),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><head><script src="//require.nexx.cloud/748/sdk.js"></script></head><body>
                        <script>onPLAYReady(function() { _play.init('player', '161464'); });</script>
                        </body></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NexxEmbedIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
