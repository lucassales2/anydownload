package com.anydownload.core.extract.cbs

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
 * Fixture cases for the CBS subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class CBSIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val contentId = "xrUyNLtl9wd8D_RWWAg9NU2F_V6QpB3R"
    private val cbsUrl = "https://www.cbs.com/shows/video/$contentId/"
    private val youtubeUrl = "https://www.paramountpressexpress.com/paramount-plus/yt-video/?watch=OX9wJWOcqck"
    private val brightcoveUrl = "https://www.paramountpressexpress.com/cbs-entertainment/shows/survivor/video/?watch=pnzew7e2hx"

    private val itemsXml = """
        <items>
          <item>
            <videoTitle>Fixture CBS Title</videoTitle>
            <assetType>HLS</assetType>
            <seriesTitle>Fixture Series</seriesTitle>
            <seasonNumber>1</seasonNumber>
            <episodeNumber>2</episodeNumber>
            <videoLength>2588000</videoLength>
            <previewImageURL>https://media.example/preview.jpg</previewImageURL>
          </item>
        </items>
    """.trimIndent()

    private val metadataJson = """
        {"title": "Fixture Meta Title", "description": "Fixture description",
         "defaultThumbnailUrl": "https://media.example/meta.jpg", "duration": 2588000,
         "pubDate": 1639015200000, "billingCode": "CBSI-NEW"}
    """.trimIndent()

    private val smilXml = """
        <smil><body><switch>
          <video src="https://media.example/video.mp4" height="720" width="1280"/>
          <param name="webVTTCaptionURL" value="https://media.example/captions.vtt"/>
        </switch></body></smil>
    """.trimIndent()

    private val itemsRoute = FixtureRoute(urlPattern = "https://can.cbs.com/thunder/player/videoPlayerService.php*", contentType = "text/xml", body = itemsXml)
    private val metadataRoute = FixtureRoute(
        urlPattern = "https://link.theplatform.com/s/dJ5BDC/media/guid/2198311517/$contentId?format=preview",
        contentType = "application/json",
        body = metadataJson,
    )
    private val smilRoute = FixtureRoute(
        urlPattern = "https://link.theplatform.com/s/dJ5BDC/media/guid/2198311517/$contentId?mbr=true&assetTypes=HLS*",
        contentType = "text/xml",
        body = smilXml,
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cbs = CBSIE(http(transfer()))
        assertTrue(cbs.suitable(cbsUrl))
        assertTrue(cbs.suitable("cbs:$contentId"))
        assertTrue(cbs.suitable("http://colbertlateshow.com/video/8GmB0oY0McANFvp2aEffk9jZZZ2YyXxy/the-colbeard/"))
        assertFalse(cbs.suitable("https://www.example.com/shows/video/$contentId/"))

        val press = ParamountPressExpressIE(http(transfer()))
        assertTrue(press.suitable(youtubeUrl))
        assertTrue(press.suitable(brightcoveUrl))
        assertFalse(press.suitable("https://www.example.com/video/?watch=pnzew7e2hx"))
    }

    // -------------------------------------------------------------------- cbs

    @Test
    fun cbsVideoYieldsTheSmilRowAndMetadata() = runTest {
        val info = CBSIE(http(transfer(itemsRoute, metadataRoute, smilRoute))).extract(cbsUrl)
        assertEquals(contentId, info.id)
        assertEquals("Fixture CBS Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(2588.0, info.duration)
        assertEquals("20211209", info.uploadDate)
        assertEquals("CBSI-NEW", info.uploader)
        assertEquals("Fixture Series", info.channel)
        assertEquals("https://media.example/preview.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/video.mp4", info.formats[0].url)
        assertEquals(720L, info.formats[0].height)
        assertEquals(1, info.subtitles.size)
        assertEquals("vtt", info.subtitles[0].formats.single().ext)
        assertEquals("https://media.example/captions.vtt", info.subtitles[0].formats.single().url)
    }

    @Test
    fun drmOnlyAssetTypesFailTyped() = runTest {
        val drmXml = """
            <items><item><videoTitle>Fixture</videoTitle><assetType>DASH_CENC</assetType></item></items>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "https://can.cbs.com/thunder/player/videoPlayerService.php*", contentType = "text/xml", body = drmXml),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            CBSIE(http(transfer)).extract(cbsUrl)
        }
    }

    @Test
    fun geoBlockedSmilFailsTyped() = runTest {
        val blocked = """
            <smil><body><param name="exception" value="GeoLocationBlocked"/>
            <ref abstract="Blocked outside the US."/></body></smil>
        """.trimIndent()
        val transfer = transfer(
            itemsRoute,
            metadataRoute,
            FixtureRoute(
                urlPattern = "https://link.theplatform.com/s/dJ5BDC/media/guid/2198311517/$contentId?mbr=true&assetTypes=HLS*",
                contentType = "text/xml",
                body = blocked,
            ),
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            CBSIE(http(transfer)).extract(cbsUrl)
        }
    }

    // ------------------------------------------------------ press express

    @Test
    fun youtubeWatchBecomesARedirect() = runTest {
        val info = ParamountPressExpressIE(http(transfer())).extract(youtubeUrl)
        assertEquals("OX9wJWOcqck", info.id)
        assertEquals("https://www.youtube.com/watch?v=OX9wJWOcqck", info.redirectUrl)
    }

    @Test
    fun brightcoveWatchBecomesARedirect() = runTest {
        val page = """
            <html><body>
            <div id="vcbrightcoveplayer" data-account="999" data-player="P1" data-embed="default"></div>
            <script>video_id = "6322981580112",</script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = "$brightcoveUrl*", contentType = "text/html", body = page))
        val info = ParamountPressExpressIE(http(transfer)).extract(brightcoveUrl)
        assertEquals("pnzew7e2hx", info.id)
        assertEquals(
            "https://players.brightcove.net/999/P1_default/index.html?videoId=6322981580112",
            info.redirectUrl,
        )
    }

    // --------------------------------------------------------------- harness

    @Test
    fun cbsIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = cbsUrl,
            infoDict = mapOf(
                "id" to Expect.Value(contentId),
                "title" to Expect.Value("Fixture CBS Title"),
                "upload_date" to Expect.Value("20211209"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(itemsRoute, metadataRoute, smilRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> CBSIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun youtubeWatchIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = youtubeUrl,
            infoDict = mapOf("id" to Expect.Value("OX9wJWOcqck")),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ParamountPressExpressIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
