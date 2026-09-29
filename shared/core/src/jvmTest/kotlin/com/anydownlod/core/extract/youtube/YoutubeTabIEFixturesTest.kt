package com.anydownlod.core.extract.youtube

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.CaseResult
import com.anydownlod.core.extract.harness.ClasspathFixtureStore
import com.anydownlod.core.extract.harness.Expect
import com.anydownlod.core.extract.harness.ExtractorCase
import com.anydownlod.core.extract.harness.ExtractorTestRun
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import com.anydownlod.core.extract.harness.runCase
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
            "https://www.youtube.com/watch?v=AAAAAAAAAAA&list=PLfixture",
            "https://www.youtube.com/playlist?list=RDfixture",
            "https://www.youtube.com/channel/UCfixture",
            "https://www.youtube.com/@fixture",
            "https://www.youtube.com/results?search_query=fixture",
            "https://www.youtube.com/live/AAAAAAAAAAA",
        )
        unsupported.forEach { assertFalse(ie.suitable(it), "must stay unsupported: $it") }
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
