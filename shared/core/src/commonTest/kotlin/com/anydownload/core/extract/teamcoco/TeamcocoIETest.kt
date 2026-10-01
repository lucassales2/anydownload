package com.anydownload.core.extract.teamcoco

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Team Coco subset. Ids and media paths are synthesized
 * on `media.example`; the GraphQL token is a fake value. No cookie, token, or
 * signed URL appears.
 */
class TeamcocoIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val teamcocoUrl = "http://teamcoco.com/video/mary-kay-remote"
    private val conanUrl = "https://conanclassic.com/video/ice-cube-kevin-hart-conan-share-lyft"

    private val teamcocoPage = FixtureRoute(
        urlPattern = "http://teamcoco.com/video/mary-kay-remote",
        contentType = "text/html",
        body = """
            <html><body><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"pageData":{
              "blocks": [
                {"name": "meta-tags", "props": {"title": "Fixture Title",
                  "descriptionHtml": "<p>Fixture <b>description</b></p>",
                  "publishedOn": "2014-04-02", "image": "/image/thumb?id=80187"}},
                {"name": "video-player", "props": {"src": [
                  {"label": "hls", "src": "https://media.example/master.m3u8",
                   "type": "application/x-mpegURL"},
                  {"label": "hd", "src": "https://media.example/720.mp4", "type": "video/mp4"},
                  {"label": "low", "src": "/mp4:protected/x.mp4"}]}}]}}}}</script></body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val teamcoco = TeamcocoIE(http(transfer()))
        assertTrue(teamcoco.suitable(teamcocoUrl))
        assertTrue(teamcoco.suitable("http://teamcoco.com/italy/conan-jordan-schlansky-hit-the-streets-of-florence"))
        assertFalse(teamcoco.suitable(conanUrl))
        assertFalse(teamcoco.suitable("https://www.example.com/video/x"))

        val conan = ConanClassicIE(http(transfer()))
        assertTrue(conan.suitable(conanUrl))
        assertTrue(conan.suitable("https://conan25.teamcoco.com/video/ice-cube-kevin-hart-conan-share-lyft"))
        assertFalse(conan.suitable(teamcocoUrl))
    }

    // ------------------------------------------------------------ teamcoco

    @Test
    fun teamcocoYieldsTheMergedInfoAndRows() = runTest {
        val info = TeamcocoIE(http(transfer(teamcocoPage))).extract(teamcocoUrl)
        assertEquals("80187", info.id)
        assertEquals("Fixture Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("20140402", info.uploadDate)
        assertEquals("https://teamcoco.com/image/thumb?id=80187", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("hls", info.formats[0].formatId)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("hd", info.formats[1].formatId)
        assertEquals(1280L, info.formats[1].width)
        assertEquals(720L, info.formats[1].height)
    }

    @Test
    fun teamcocoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = teamcocoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("80187"),
                "title" to Expect.Value("Fixture Title"),
                "upload_date" to Expect.Value("20140402"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(teamcocoPage),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TeamcocoIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    // -------------------------------------------------------- conan classic

    @Test
    fun conanClassicUsesTheGraphqlAndNgtvInfo() = runTest {
        val conanPage = FixtureRoute(
            urlPattern = "https://conanclassic.com/video/*",
            contentType = "text/html",
            body = """
                <html><body><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"pageData":{
                  "blocks": [{"props": {"fieldDefs": [
                    {"name": "incomingVideoId", "value": "74709"}]}}]}}}}</script></body></html>
            """.trimIndent(),
        )
        val graphQlRoute = FixtureRoute(
            urlPattern = "https://conanclassic.com/api/legacy/graphql",
            method = "POST",
            contentType = "application/json",
            body = """
                {"data": {
                  "findRecord": {"title": "Fixture Conan", "teaser": "Fixture teaser",
                    "thumb": {"preview": "https://media.example/conan.jpg"},
                    "duration": "00:09:30", "publishOn": "2013-12-11"},
                  "findRecordVideoMetadata": {"turnerMediaId": "media123",
                    "turnerMediaAuthToken": "fake_value"}}}
            """.trimIndent(),
        )
        val ngtvRoute = FixtureRoute(
            urlPattern = "https://medium.ngtv.io/media/media123/tv",
            contentType = "application/json",
            body = """
                {"media": {"tv": {"unprotected": {"url": "https://media.example/ngtv.m3u8",
                  "totalRuntime": 570}}}}
            """.trimIndent(),
        )
        val info = ConanClassicIE(http(transfer(conanPage, graphQlRoute, ngtvRoute))).extract(conanUrl)
        assertEquals("74709", info.id)
        assertEquals("Fixture Conan", info.title)
        assertEquals("Fixture teaser", info.description)
        assertEquals(570.0, info.duration)
        assertEquals("20131211", info.uploadDate)
        assertEquals("https://media.example/conan.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/ngtv.m3u8", info.formats[0].url)
        assertEquals("m3u8_native", info.formats[0].protocol)
    }

    @Test
    fun conanClassicFallsBackToTheMetadataSrcRows() = runTest {
        val conanPage = FixtureRoute(
            urlPattern = "https://conanclassic.com/video/*",
            contentType = "text/html",
            body = """
                <html><body><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"pageData":{
                  "blocks": [{"props": {"fields": {"incomingVideoRecord": {"id": "12345"}}}}]}}}}</script>
                </body></html>
            """.trimIndent(),
        )
        val graphQlRoute = FixtureRoute(
            urlPattern = "https://conanclassic.com/api/legacy/graphql",
            method = "POST",
            contentType = "application/json",
            body = """
                {"data": {"findRecord": {"title": "Fixture Fallback"},
                  "findRecordVideoMetadata": {"src": [
                    {"label": "hd", "src": "https://media.example/720.mp4", "type": "video/mp4"}]}}}
            """.trimIndent(),
        )
        val info = ConanClassicIE(http(transfer(conanPage, graphQlRoute))).extract(conanUrl)
        assertEquals("12345", info.id)
        assertEquals("Fixture Fallback", info.title)
        assertEquals(1, info.formats.size)
        assertEquals("hd", info.formats[0].formatId)
        assertEquals("https://media.example/720.mp4", info.formats[0].url)
    }
}
