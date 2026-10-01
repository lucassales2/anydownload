package com.anydownload.core.extract.theplatform

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
 * Fixture cases for the ThePlatform subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no key or token appears.
 */
class ThePlatformIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "e9I_cZgTgIPd"
    private val smilUrl = "http://link.theplatform.com/s/dJ5BDC/$videoId?mbr=true&format=SMIL"
    private val metadataUrl = "https://link.theplatform.com/s/dJ5BDC/$videoId?format=preview"

    private val smil = """
        <smil xmlns="http://www.w3.org/2001/SMIL20/Language">
          <body><switch>
            <video src="https://media.example/hls/master.m3u8" width="1280" height="720"/>
            <video src="https://media.example/video/720.mp4" width="1280" height="720"/>
            <textstream src="https://media.example/sub/en.vtt" lang="en" type="text/vtt"/>
          </switch></body>
        </smil>
    """.trimIndent()

    private val metadata = """
        {"title": "Fixture Platform", "description": "Fixture description",
         "defaultThumbnailUrl": "https://media.example/thumb.jpg",
         "duration": 247000, "pubDate": 1383239700000, "billingCode": "CBSI-NEW"}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ThePlatformIE(http(transfer())) to "http://link.theplatform.com/s/dJ5BDC/$videoId",
            ThePlatformIE(http(transfer())) to "http://player.theplatform.com/p/NnzsPC/widget/select/media/4Y0TlYUr_ZT7",
            ThePlatformIE(http(transfer())) to "theplatform:$videoId",
            ThePlatformFeedIE(http(transfer())) to
                "http://feed.theplatform.com/f/7wvmTC/msnbc_video-p-test?form=json&byGuid=n_hardball_5biden_140207",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ThePlatformFeedIE(http(transfer())).suitable("http://link.theplatform.com/s/x/y"))
    }

    // ------------------------------------------------------------------ smil

    @Test
    fun smilAndMetadataYieldFormats() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = smilUrl, contentType = "application/smil+xml", body = smil),
            FixtureRoute(urlPattern = metadataUrl, contentType = "application/json", body = metadata),
        )
        val info = ThePlatformIE(http(transfer)).extract("http://link.theplatform.com/s/dJ5BDC/$videoId")
        assertEquals(videoId, info.id)
        assertEquals("Fixture Platform", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(247.0, info.duration)
        assertEquals("20131031", info.uploadDate)
        assertEquals("CBSI-NEW", info.uploader)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("en", info.subtitles.single().language)
    }

    @Test
    fun geoExceptionFailsTyped() = runTest {
        val geoSmil = """
            <smil xmlns="http://www.w3.org/2001/SMIL20/Language">
              <body><switch>
                <ref src="https://media.example/error.mp4" abstract="Geo blocked">
                  <param name="exception" value="GeoLocationBlocked"/>
                </ref>
              </switch></body>
            </smil>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = smilUrl, contentType = "application/smil+xml", body = geoSmil),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            ThePlatformIE(http(transfer)).extract("http://link.theplatform.com/s/dJ5BDC/$videoId")
        }
    }

    // ------------------------------------------------------------------ feed

    @Test
    fun feedEntryYieldsFormatsAndMetadata() = runTest {
        val feedUrl = "http://feed.theplatform.com/f/7wvmTC/msnbc_video-p-test?form=json&byGuid=n_hardball_5biden_140207"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = feedUrl,
                contentType = "application/json",
                body = """
                    {"entries": [{"guid": "n_hardball_5biden_140207",
                      "media${'$'}availableDate": 1391824260000,
                      "media${'$'}content": [{"plfile${'$'}url": "http://link.theplatform.com/s/7wvmTC/media/guid/1234/n_hardball_5biden_140207",
                                         "plfile${'$'}format": "MPEG4",
                                         "plfile${'$'}duration": 467.0}],
                      "media${'$'}thumbnails": [{"plfile${'$'}url": "https://media.example/thumb.jpg",
                                             "plfile${'$'}width": 640, "plfile${'$'}height": 360}]}]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "http://link.theplatform.com/s/7wvmTC/media/guid/1234/n_hardball_5biden_140207?mbr=true&formats=MPEG4",
                contentType = "application/smil+xml",
                body = """
                    <smil xmlns="http://www.w3.org/2001/SMIL20/Language">
                      <body><switch>
                        <video src="https://media.example/hls/master.m3u8" width="1280" height="720"/>
                      </switch></body>
                    </smil>
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://link.theplatform.com/s/7wvmTC/n_hardball_5biden_140207?format=preview",
                contentType = "application/json",
                body = """{"title": "Fixture Feed", "duration": 467000}""",
            ),
        )
        val info = ThePlatformFeedIE(http(transfer)).extract(feedUrl)
        assertEquals("n_hardball_5biden_140207", info.id)
        assertEquals("Fixture Feed", info.title)
        assertEquals(467.0, info.duration)
        assertEquals("20140208", info.uploadDate)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals(640L, info.thumbnails.single().width)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun linkUrlIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "http://link.theplatform.com/s/dJ5BDC/$videoId",
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Platform"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = smilUrl, contentType = "application/smil+xml", body = smil),
                FixtureRoute(urlPattern = metadataUrl, contentType = "application/json", body = metadata),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ThePlatformIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
