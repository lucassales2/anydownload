package com.anydownload.core.extract.cnn

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
 * Fixture cases for the CNN subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and the app id is a fake
 * value read from the page (never stored by the port).
 */
class CnnIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val mediaId = "med0e97ad0d154f56e29aa96e57192a14226734b6b"

    private fun page() = """
        <html><head><script>window.env = {"TOP_AUTH_SERVICE_APP_ID": "fake_app_id"};</script></head>
        <body><div data-component-name="video-player" data-media-id="$mediaId"
          data-video-resource-parent-uri="urn:cnn:fixture"
          data-headline="Fixture CNN Video" data-description="Fixture description"
          data-duration="3:13" data-publish-date="2024-05-31T00:00:00Z"
          data-poster-image-override='{"big": {"uri": "https://media.example/thumb.jpg"}}'></div>
        </body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            CNNIE(http(transfer())) to "https://www.cnn.com/2024/05/31/sport/video/fixture-spt-intl",
            CNNIE(http(transfer())) to "https://edition.cnn.com/2024/06/11/politics/video/fixture-digvid",
            CNNIE(http(transfer())) to "https://cnnespanol.cnn.com/video/fixture-trax",
            CNNIndonesiaIE(http(transfer())) to
                "https://www.cnnindonesia.com/ekonomi/20220909212635-89-845885/fixture-article",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(CNNIE(http(transfer())).suitable("https://www.cnnindonesia.com/ekonomi/20220909212635-89-845885/fixture-article"))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun videoPlayerScanYieldsDirectAndHlsFormats() = runTest {
        val url = "https://www.cnn.com/2024/05/31/sport/video/fixture-spt-intl"
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page()),
            FixtureRoute(
                urlPattern = "https://fave.api.cnn.io/v1/video?id=$mediaId&stellarUri=*",
                contentType = "application/json",
                body = """
                    {"files": [{"fileUri": "https://media.example/video-1280x720_2000k.mp4"}],
                     "closedCaptions": {"types": [{"track": {"url": "https://media.example/cc/en.vtt",
                                                             "lang": "en", "label": "English"}}]},
                     "headline": "Fixture CNN Video", "dateCreated": {"uts": 1717148586}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://medium.ngtv.io/v2/media/$mediaId/desktop?appId=*",
                contentType = "application/json",
                body = """
                    {"media": {"desktop": {"unprotected": {"unencrypted": {
                      "url": "https://media.example/hls/master.m3u8"}}}}}
                """.trimIndent(),
            ),
        )
        val info = CNNIE(http(transfer)).extract(url)
        assertEquals(mediaId, info.id)
        assertEquals("Fixture CNN Video", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(193.0, info.duration)
        assertEquals("20240531", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("direct", info.formats[0].formatId)
        assertEquals(720L, info.formats[0].height)
        assertEquals("m3u8_native", info.formats[1].protocol)
        assertEquals("en", info.subtitles.single().language)
    }

    // -------------------------------------------------------------- indonesia

    @Test
    fun indonesiaArticleRedirectsToTheEmbed() = runTest {
        val url = "https://www.cnnindonesia.com/ekonomi/20220909212635-89-845885/fixture-article"
        val page = """
            <html><head><script type="application/ld+json">
            {"@type": "VideoObject", "embedUrl": "https://www.cnnindonesia.com/embed/845885"}
            </script></head></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = CNNIndonesiaIE(http(transfer)).extract(url)
        assertEquals("845885", info.id)
        assertEquals("20220909", info.uploadDate)
        assertEquals("https://www.cnnindonesia.com/embed/845885", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.cnn.com/2024/05/31/sport/video/fixture-spt-intl"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(mediaId),
                "title" to Expect.Value("Fixture CNN Video"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = url, contentType = "text/html", body = page()),
                FixtureRoute(
                    urlPattern = "https://fave.api.cnn.io/v1/video?id=$mediaId&stellarUri=*",
                    contentType = "application/json",
                    body = """{"files": [{"fileUri": "https://media.example/video-1280x720_2000k.mp4"}]}""",
                ),
                FixtureRoute(
                    urlPattern = "https://medium.ngtv.io/v2/media/$mediaId/desktop?appId=*",
                    contentType = "application/json",
                    body = """
                        {"media": {"desktop": {"unprotected": {"unencrypted": {
                          "url": "https://media.example/hls/master.m3u8"}}}}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> CNNIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
