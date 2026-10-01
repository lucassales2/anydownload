package com.anydownload.core.extract.vidyard

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
 * Fixture cases for the Vidyard subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class VidyardIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val playerId = "oTDMPlUv--51Th455G5u7Q"
    private val facadeUuid = "iDqTwWGrd36vaLuaCY3nTs"

    private fun playerBody(chapters: String) = """
        {"payload": {"playerUuid": "player-uuid-1", "name": "Fixture Player",
                     "chapters": [$chapters]}}
    """.trimIndent()

    private fun chapterBody(title: String) = """
        {"facadeUuid": "$facadeUuid", "name": "$title",
         "description": "Fixture &amp; description",
         "sources": {"hls": [{"profile": "auto", "url": "https://media.example/hls/master.m3u8"}],
                     "mp4": [{"profile": "720p", "url": "https://media.example/video/720p.mp4",
                              "mimeType": "video/mp4"}]},
         "captions": [{"language": "en", "name": "English", "vttUrl": "https://media.example/sub/en.vtt"}]}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val extractor = VidyardIE(http(transfer()))
        assertTrue(extractor.suitable("https://vyexample03.hubs.vidyard.com/watch/$playerId"))
        assertTrue(extractor.suitable("https://share.vidyard.com/share/$playerId"))
        assertTrue(extractor.suitable("https://play.vidyard.com/$playerId"))
        assertTrue(extractor.suitable("https://play.vidyard.com/player/$playerId.json"))
        assertFalse(extractor.suitable("https://example.com/watch/$playerId"))
    }

    // ---------------------------------------------------------------- single

    @Test
    fun singleChapterYieldsFormatsCaptionsAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://play.vidyard.com/player/$playerId.json",
                contentType = "application/json",
                body = playerBody(chapterBody("Fixture Video")),
            ),
            FixtureRoute(
                urlPattern = "https://play.vidyard.com/video/$facadeUuid",
                contentType = "application/json",
                body = """
                    {"name": "Fixture Video", "seconds": 99,
                     "thumbnailUrl": "https://media.example/thumb.jpg",
                     "videoSections": [{"title": "Intro", "milliseconds": 1000},
                                       {"title": "Body", "milliseconds": 5000}]}
                """.trimIndent(),
            ),
        )
        val info = VidyardIE(http(transfer)).extract("https://play.vidyard.com/$playerId")
        assertEquals(facadeUuid, info.id)
        assertEquals("Fixture Video", info.title)
        assertEquals("Fixture & description", info.description)
        assertEquals(99.0, info.duration)
        assertEquals(2, info.formats.size, info.formats.joinToString { "${it.formatId}|${it.url}" })
        assertEquals("m3u8_native", info.formats.first { it.formatId == "hls" }.protocol)
        val mp4 = info.formats.first { it.formatId == "http-mp4-720p" }
        assertEquals(720L, mp4.height)
        assertEquals(1280L, mp4.width)
        assertEquals("en", info.subtitles.single().language)
        assertEquals(2, info.chapters.size)
        assertEquals(1.0, info.chapters[0].startTime)
        assertEquals(1, info.thumbnails.size)
    }

    // ------------------------------------------------------------- multi

    @Test
    fun multiChapterPlayersBecomeMediaItems() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://play.vidyard.com/player/$playerId.json",
                contentType = "application/json",
                body = playerBody("${chapterBody("Part One")}, ${chapterBody("Part Two")}"),
            ),
            FixtureRoute(
                urlPattern = "https://play.vidyard.com/video/$facadeUuid",
                contentType = "application/json",
                body = """{"seconds": 30}""",
            ),
        )
        val info = VidyardIE(http(transfer)).extract("https://share.vidyard.com/share/$playerId")
        assertEquals("player-uuid-1", info.id)
        assertEquals(2, info.media.size)
        assertEquals("Part One", info.media[0].title)
        assertEquals(2, info.media[1].formats.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun singleChapterIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://play.vidyard.com/$playerId",
            infoDict = mapOf(
                "id" to Expect.Value(facadeUuid),
                "title" to Expect.Value("Fixture Video"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://play.vidyard.com/player/$playerId.json",
                    contentType = "application/json",
                    body = playerBody(chapterBody("Fixture Video")),
                ),
                FixtureRoute(
                    urlPattern = "https://play.vidyard.com/video/$facadeUuid",
                    contentType = "application/json",
                    body = """{"seconds": 99}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> VidyardIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
