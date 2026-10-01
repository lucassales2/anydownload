package com.anydownload.core.extract.youtube

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.CaseResult
import com.anydownload.core.extract.harness.ClasspathFixtureStore
import com.anydownload.core.extract.harness.Expect
import com.anydownload.core.extract.harness.ExtractorCase
import com.anydownload.core.extract.harness.ExtractorTestRun
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import com.anydownload.core.extract.harness.FixtureRoute
import com.anydownload.core.extract.harness.runCase
import com.anydownload.core.platform.ByteArrayHttpBody
import com.anydownload.core.platform.HttpRequest
import com.anydownload.core.platform.HttpResponse
import com.anydownload.core.platform.HttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The saved playlist browse documents under
 * `commonTest/resources/fixtures/youtube-playlist/`. They follow the public
 * innertube shape but keep only flat video ids and titles: no thumbnails, no
 * visitor data, no continuation token, and no signed URL.
 */
class YoutubeTabIEFixturesTest {

    private val playlistUrl = "https://www.youtube.com/playlist?list=PLfixture"

    private fun transfer(resource: String): FixtureHttpTransfer = FixtureHttpTransfer(
        listOf(
            FixtureRoute(
                urlPattern = "https://www.youtube.com/youtubei/v1/browse*",
                method = "POST",
                contentType = "application/json",
                bodyResource = "fixtures/youtube-playlist/$resource",
            ),
        ),
        ClasspathFixtureStore,
    )

    private fun ie(resource: String): YoutubeTabIE = YoutubeTabIE(ExtractorHttp(transfer(resource)))

    @Test
    fun harnessCaseReadsTheFixtureEntriesInOrder() = runTest {
        val case = ExtractorCase(
            url = playlistUrl,
            infoDict = mapOf(
                "id" to Expect.Value("PLfixture"),
                "title" to Expect.Value("Fixture Playlist"),
                "extractor_key" to Expect.Value(YoutubeTabIE.IE_KEY),
                "entries" to Expect.Count(3),
                "entries.0.id" to Expect.Value("AAAAAAAAAAA"),
                "entries.0.title" to Expect.Value("Fixture One"),
                "entries.1.id" to Expect.Value("BBBBBBBBBBB"),
                "entries.1.title" to Expect.Value("Fixture Two"),
                "entries.2.id" to Expect.Value("CCCCCCCCCCC"),
                "entries.2.title" to Expect.Value("Fixture Three"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://www.youtube.com/youtubei/v1/browse*",
                    method = "POST",
                    contentType = "application/json",
                    bodyResource = "fixtures/youtube-playlist/playlist_page.json",
                ),
            ),
        )

        val result = runCase(case, ExtractorTestRun(fixtures = ClasspathFixtureStore)) { http -> YoutubeTabIE(http) }

        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun continuationPagesAreFollowedInOrder() = runTest {
        val first = """
            {"contents":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[{"playlistVideoListRenderer":{"contents":[
              {"playlistVideoRenderer":{"videoId":"AAAAAAAAAAA","title":{"simpleText":"One"}}},
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"CONT1"}}}}
            ]}}]}}]}},
            "metadata":{"playlistMetadataRenderer":{"title":"Fixture Playlist"}}}
        """.trimIndent()
        val second = """
            {"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
              {"playlistVideoRenderer":{"videoId":"BBBBBBBBBBB","title":{"simpleText":"Two"}}},
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"CONT2"}}}}
            ]}}]}
        """.trimIndent()
        val third = """
            {"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
              {"playlistVideoRenderer":{"videoId":"CCCCCCCCCCC","title":{"simpleText":"Three"}}}
            ]}}]}
        """.trimIndent()
        val bodies = ArrayDeque(listOf(first, second, third))
        val requests = mutableListOf<String>()
        val transfer = object : HttpTransfer {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                requests += request.body?.decodeToString().orEmpty()
                val body = bodies.removeFirst()
                val bytes = body.encodeToByteArray()
                return HttpResponse.Final(
                    statusCode = 200,
                    contentType = "application/json",
                    totalBytes = bytes.size.toLong(),
                    body = ByteArrayHttpBody(bytes),
                )
            }
        }

        val info = YoutubeTabIE(ExtractorHttp(transfer)).extract(playlistUrl)

        assertEquals(listOf("AAAAAAAAAAA", "BBBBBBBBBBB", "CCCCCCCCCCC"), info.entries.map { it.id })
        assertEquals(3, requests.size)
        assertTrue(requests[1].contains("CONT1"), "second page uses the first continuation token")
        assertTrue(requests[2].contains("CONT2"), "third page uses the second continuation token")
    }

    private class JsonTransfer(private val body: String) : HttpTransfer {
        val requests = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request.body?.decodeToString().orEmpty()
            val bytes = body.encodeToByteArray()
            return HttpResponse.Final(
                statusCode = 200,
                contentType = "application/json",
                totalBytes = bytes.size.toLong(),
                body = ByteArrayHttpBody(bytes),
            )
        }
    }

    @Test
    fun aChannelHandleResolvesItsGridEntries() = runTest {
        val body = """
            {"metadata":{"channelMetadataRenderer":{"title":"Fixture Channel"}},
             "contents":{"sectionListRenderer":{"contents":[
               {"itemSectionRenderer":{"contents":[
                 {"gridRenderer":{"items":[
                   {"richItemRenderer":{"content":{"videoRenderer":{"videoId":"AAAAAAAAAAA","title":{"runs":[{"text":"Fixture One"}]}}}}},
                   {"richItemRenderer":{"content":{"gridVideoRenderer":{"videoId":"BBBBBBBBBBB","title":{"simpleText":"Fixture Two"}}}}}
                 ]}}
               ]}}
             ]}}}
        """.trimIndent()
        val transfer = JsonTransfer(body)

        val info = YoutubeTabIE(ExtractorHttp(transfer)).extract("https://www.youtube.com/@FixtureChannel/videos")

        assertEquals("Fixture Channel", info.title)
        assertEquals(listOf("AAAAAAAAAAA", "BBBBBBBBBBB"), info.entries.map { it.id })
        assertEquals("Fixture One", info.entries[0].title)
        assertEquals("Fixture Two", info.entries[1].title)
        assertTrue(transfer.requests.single().contains("@FixtureChannel"))
    }

    @Test
    fun aMixListUsesThePlaylistPanelRenderer() = runTest {
        val body = """
            {"contents":{"playlistPanelRenderer":{"contents":[
              {"playlistPanelVideoRenderer":{"videoId":"CCCCCCCCCCC","title":{"simpleText":"Mix One"}}},
              {"playlistPanelVideoRenderer":{"videoId":"DDDDDDDDDDD","title":{"simpleText":"Mix Two"}}}
            ]}}}
        """.trimIndent()
        val transfer = JsonTransfer(body)

        val info = YoutubeTabIE(ExtractorHttp(transfer))
            .extract("https://www.youtube.com/watch?v=YE7VzlLtp-4&list=RDAMVMfixture")

        assertEquals("RDAMVMfixture", info.id)
        assertEquals(listOf("CCCCCCCCCCC", "DDDDDDDDDDD"), info.entries.map { it.id })
        assertTrue(transfer.requests.single().contains("VLRDAMVMfixture"))
    }

    @Test
    fun aCustomChannelUrlFailsTypedWithoutTheResolver() = runTest {
        val transfer = JsonTransfer("{}")
        assertFailsWith<ExtractionError.UnsupportedUrl> {
            YoutubeTabIE(ExtractorHttp(transfer)).extract("https://www.youtube.com/c/FixtureChannel")
        }
    }

    @Test
    fun channelHarnessCaseReadsGridEntries() = runTest {
        val body = """
            {"metadata":{"channelMetadataRenderer":{"title":"Fixture Channel"}},
             "contents":{"sectionListRenderer":{"contents":[
               {"itemSectionRenderer":{"contents":[
                 {"gridRenderer":{"items":[
                   {"richItemRenderer":{"content":{"videoRenderer":{"videoId":"AAAAAAAAAAA","title":{"runs":[{"text":"Fixture One"}]}}}}},
                   {"richItemRenderer":{"content":{"gridVideoRenderer":{"videoId":"BBBBBBBBBBB","title":{"simpleText":"Fixture Two"}}}}}
                 ]}}
               ]}}
             ]}}}
        """.trimIndent()
        val case = ExtractorCase(
            url = "https://www.youtube.com/@FixtureChannel",
            infoDict = mapOf(
                "id" to Expect.Value("@FixtureChannel"),
                "title" to Expect.Value("Fixture Channel"),
                "entries" to Expect.Count(2),
                "entries.0.id" to Expect.Value("AAAAAAAAAAA"),
                "entries.1.id" to Expect.Value("BBBBBBBBBBB"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://www.youtube.com/youtubei/v1/browse*",
                    method = "POST",
                    contentType = "application/json",
                    body = body,
                ),
            ),
        )

        val result = runCase(case, ExtractorTestRun(fixtures = ClasspathFixtureStore)) { http -> YoutubeTabIE(http) }

        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun fixturePlaylistYieldsWatchEntriesInOrder() = runTest {
        val info = ie("playlist_page.json").extract(playlistUrl)

        assertEquals("PLfixture", info.id)
        assertEquals("Fixture Playlist", info.title)
        assertEquals(
            listOf("AAAAAAAAAAA", "BBBBBBBBBBB", "CCCCCCCCCCC"),
            info.entries.map { it.id },
        )
        assertEquals(
            listOf("Fixture One", "Fixture Two", "Fixture Three"),
            info.entries.map { it.title },
        )
        assertEquals(
            listOf(
                "https://www.youtube.com/watch?v=AAAAAAAAAAA",
                "https://www.youtube.com/watch?v=BBBBBBBBBBB",
                "https://www.youtube.com/watch?v=CCCCCCCCCCC",
            ),
            info.entries.map { it.url },
        )
        assertTrue(info.formats.isEmpty(), "a playlist result must not carry a media format")
    }

    @Test
    fun privateFixtureFailsTypedWithNoEntries() = runTest {
        // The fixture has no video renderers, so the extractor refuses it.
        assertFailsWith<ExtractionError.Unavailable> {
            ie("playlist_private.json").extract(playlistUrl)
        }
    }

    @Test
    fun onlyPlaylistUrlsMatch() {
        val ie = ie("playlist_page.json")
        val supported = listOf(
            "https://www.youtube.com/playlist?list=PLfixture",
            "https://youtube.com/playlist?list=PLfixture&t=1",
            "https://m.youtube.com/playlist?list=PLfixture",
            "https://music.youtube.com/playlist?list=PLfixture",
        )
        supported.forEach { assertTrue(ie.suitable(it), "must match $it") }

        val unsupported = listOf(
            "https://www.youtube.com/results?search_query=fixture",
            "https://www.youtube.com/live/AAAAAAAAAAA",
        )
        unsupported.forEach { assertFalse(ie.suitable(it), "must stay unsupported: $it") }

        // T-124 forms now match the tab extractor. `/c/` and `/user/` match
        // the URL but fail typed in `extract` because the resolver is not
        // translated (see aCustomChannelUrlFailsTypedWithoutTheResolver).
        val newlySupported = listOf(
            "https://www.youtube.com/watch?v=AAAAAAAAAAA&list=PLfixture",
            "https://www.youtube.com/playlist?list=RDfixture",
            "https://www.youtube.com/channel/UCfixture000000000000000",
            "https://www.youtube.com/@fixture/videos",
            "https://www.youtube.com/c/FixtureChannel",
            "https://www.youtube.com/user/FixtureChannel",
        )
        newlySupported.forEach { assertTrue(ie.suitable(it), "must match $it") }
    }

    @Test
    fun savedFixturesCarryNoSignedOrSessionData() {
        for (name in listOf("playlist_page.json", "playlist_private.json")) {
            val text = ClasspathFixtureStore.read("fixtures/youtube-playlist/$name")
            assertNotNull(text, "missing fixture $name")
            for (forbidden in listOf("googlevideo", "visitorData", "continuationItemRenderer", "sqp=", "ytimg", "yt3.googleusercontent")) {
                assertTrue(forbidden !in text, "$name must not contain '$forbidden'")
            }
        }
    }
}
