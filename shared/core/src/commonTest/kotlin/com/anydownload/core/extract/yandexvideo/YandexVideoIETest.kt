package com.anydownload.core.extract.yandexvideo

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
 * Fixture cases for the Yandex Video / Dzen subset. Ids, titles, and media
 * paths are synthesized; media lives on `media.example`, and no token
 * appears.
 */
class YandexVideoIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "4dbb36ec4e0526d58f9f2dc8f0ecf374"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            YandexVideoIE(http(transfer())) to "https://yandex.ru/portal/video?stream_id=$videoId",
            YandexVideoIE(http(transfer())) to "https://frontend.vh.yandex.ru/player/$videoId",
            YandexVideoPreviewIE(http(transfer())) to "https://yandex.ru/video/preview/?filmId=10682852472978372885",
            ZenYandexIE(http(transfer())) to "https://dzen.ru/video/watch/6002240ff8b1af50bb2da5e3",
            ZenYandexChannelIE(http(transfer())) to "https://dzen.ru/jony_me",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ZenYandexChannelIE(http(transfer())).suitable("https://dzen.ru/video/watch/x"))
    }

    // --------------------------------------------------------------- player

    @Test
    fun playerGraphqlYieldsFormatsAndMetadata() = runTest {
        val url = "https://yandex.ru/portal/video?stream_id=$videoId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://frontend.vh.yandex.ru/graphql",
                method = "POST",
                contentType = "application/json",
                body = """
                    {"data": {"player": {"content": {
                      "title": "Fixture Yandex", "description": "Fixture description",
                      "duration": 5575, "release_date": 1549972939, "restriction_age": 18,
                      "views_count": 1000, "thumbnail": "https://media.example/thumb.jpg",
                      "program_title": "Fixture Series",
                      "streams": [{"url": "https://media.example/hls/master.m3u8"}]}}}}
                """.trimIndent(),
            ),
        )
        val info = YandexVideoIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Yandex", info.title)
        assertEquals("20190212", info.uploadDate)
        assertEquals(18, info.ageLimit)
        assertEquals("Fixture Series", info.channel)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // ---------------------------------------------------------------- dzen

    @Test
    fun dzenSsrDataYieldsFormatsAndMetadata() = runTest {
        val url = "https://dzen.ru/video/watch/6002240ff8b1af50bb2da5e3"
        val page = """
            <html><body><script>
            var _params = ({"ssrData": {"videoMetaResponse": {
              "title": "Fixture Dzen", "description": "Fixture description",
              "publicationDate": 1611378221, "image": "https://media.example/thumb.jpg",
              "source": {"title": "Fixture Source"},
              "video": {"id": "video-1", "duration": 243, "views": 100,
                        "streams": [{"url": "https://media.example/hls/master.m3u8?ct=8"}]}}}});
            </script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = ZenYandexIE(http(transfer)).extract(url)
        assertEquals("6002240ff8b1af50bb2da5e3", info.id)
        assertEquals("Fixture Dzen", info.title)
        assertEquals("Fixture Source", info.uploader)
        assertEquals(243.0, info.duration)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun dzenChannelFeedYieldsEntries() = runTest {
        val url = "https://dzen.ru/jony_me"
        val page = """
            <html><body><script>
            var _params = ({"ssrData": {"exportResponse": {
              "channel": {"source": {"title": "Fixture Channel", "description": "Fixture description"}},
              "feedData": {"items": [{"id": "item-1", "title": "Fixture Video",
                                      "link": "https://dzen.ru/video/watch/item-1"}],
                           "more": {"link": "https://dzen.ru/more?next_page_id=2"}}}}});
            </script></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://dzen.ru/more?next_page_id=2",
                contentType = "application/json",
                body = """{"items": [{"id": "item-2", "title": "Second", "link": "https://dzen.ru/video/watch/item-2"}]}""",
            ),
        )
        val info = ZenYandexChannelIE(http(transfer)).extract(url)
        assertEquals("jony_me", info.id)
        assertEquals("Fixture Channel", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://dzen.ru/video/watch/item-2", info.entries[1].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun dzenVideoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://dzen.ru/video/watch/6002240ff8b1af50bb2da5e3"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("6002240ff8b1af50bb2da5e3"),
                "title" to Expect.Value("Fixture Dzen"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = url,
                    contentType = "text/html",
                    body = """
                        <html><body><script>
                        var _params = ({"ssrData": {"videoMetaResponse": {
                          "title": "Fixture Dzen",
                          "video": {"id": "video-1",
                                    "streams": [{"url": "https://media.example/hls/master.m3u8?ct=8"}]}}}});
                        </script></body></html>
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ZenYandexIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
