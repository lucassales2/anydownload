package com.anydownload.core.extract.bandlab

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
 * Fixture cases for the BandLab subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears.
 */
class BandlabIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val trackId = "04b37e88dba24967b9dac8eb8567ff39_07d7f906fc96ee11b75e000d3a428fff"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            BandlabIE(http(transfer())) to "https://www.bandlab.com/track/$trackId",
            BandlabIE(http(transfer())) to "https://www.bandlab.com/post/07d7f906-fc96-ee11-b75e-000d3a428fff",
            BandlabIE(http(transfer())) to "https://www.bandlab.com/revision/014de0a4-7d82-ea11-a94c-0003ffd19c0f",
            BandlabIE(http(transfer())) to "https://www.bandlab.com/embed/?id=014de0a4-7d82-ea11-a94c-0003ffd19c0f",
            BandlabPlaylistIE(http(transfer())) to
                "https://www.bandlab.com/davesnothome69/albums/89b79ea6-de42-ed11-b495-00224845aac7",
            BandlabPlaylistIE(http(transfer())) to
                "https://www.bandlab.com/embed/collection/?id=12cc6f7f-951b-ee11-907c-00224844f303",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(BandlabIE(http(transfer())).suitable("https://www.bandlab.com/albums/1234"))
    }

    // -------------------------------------------------------------- revision

    @Test
    fun revisionApiYieldsTheMixdown() = runTest {
        val revisionId = "07d7f906-fc96-ee11-b75e-000d3a428fff"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.bandlab.com/api/v1.3/revisions/$revisionId?edit=false",
                contentType = "application/json",
                body = """
                    {"id": "$revisionId", "description": "composed by fixture",
                     "createdOn": "2023-12-10T00:00:00Z",
                     "mixdown": {"file": "https://media.example/audio/mix.m4a", "duration": 54.63},
                     "song": {"name": "Fixture Track", "picture": {"url": "https://media.example/song.jpg"}},
                     "creator": {"name": "Fixture Artist", "username": "fixtureartist"},
                     "counters": {"plays": 10}}
                """.trimIndent(),
            ),
        )
        val info = BandlabIE(http(transfer)).extract("https://www.bandlab.com/revision/$revisionId")
        assertEquals(revisionId, info.id)
        assertEquals("Fixture Track", info.title)
        assertEquals("Fixture Artist", info.uploader)
        assertEquals("20231210", info.uploadDate)
        assertEquals(54.63, info.duration)
        assertEquals(10L, info.viewCount)
        assertEquals("https://media.example/audio/mix.m4a", info.formats.single().url)
    }

    @Test
    fun postApiYieldsTrackAndVideoPosts() = runTest {
        val trackPostId = "07d7f906-fc96-ee11-b75e-000d3a428fff"
        val trackTransfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.bandlab.com/api/v1.3/posts/$trackPostId",
                contentType = "application/json",
                body = """
                    {"id": "$trackPostId", "type": "Track", "caption": "Fixture caption",
                     "track": {"name": "Fixture Post Track",
                               "sample": {"audioUrl": "https://media.example/audio/sample.m4a",
                                          "duration": 42.5}},
                     "creator": {"name": "Fixture Artist"}}
                """.trimIndent(),
            ),
        )
        val track = BandlabIE(http(trackTransfer)).extract("https://www.bandlab.com/post/$trackPostId")
        assertEquals("Fixture Post Track", track.title)
        assertEquals(42.5, track.duration)
        assertEquals("https://media.example/audio/sample.m4a", track.formats.single().url)

        val videoTransfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.bandlab.com/api/v1.3/posts/$trackPostId",
                contentType = "application/json",
                body = """
                    {"id": "$trackPostId", "type": "Video", "caption": "Fixture video caption",
                     "video": {"url": "https://media.example/video/clip.mp4", "duration": 44.7}}
                """.trimIndent(),
            ),
        )
        val video = BandlabIE(http(videoTransfer)).extract("https://www.bandlab.com/post/$trackPostId")
        assertEquals("Fixture video caption", video.title)
        assertEquals("https://media.example/video/clip.mp4", video.formats.single().url)
    }

    // -------------------------------------------------------------- playlist

    @Test
    fun albumApiYieldsMediaItems() = runTest {
        val albumId = "89b79ea6-de42-ed11-b495-00224845aac7"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.bandlab.com/api/v1.3/albums/$albumId",
                contentType = "application/json",
                body = """
                    {"name": "Fixture Album", "description": "Fixture album description",
                     "creator": {"name": "Fixture Artist"},
                     "createdOn": "2022-10-03T00:00:00Z",
                     "picture": {"original": {"url": "https://media.example/album.jpg"}},
                     "counters": {"plays": 99},
                     "posts": [{"type": "Revision", "revision": {
                        "id": "rev-1", "song": {"name": "One"},
                        "mixdown": {"file": "https://media.example/audio/one.m4a", "duration": 30.0}}}]}
                """.trimIndent(),
            ),
        )
        val info = BandlabPlaylistIE(http(transfer)).extract(
            "https://www.bandlab.com/davesnothome69/albums/$albumId",
        )
        assertEquals(albumId, info.id)
        assertEquals("Fixture Album", info.title)
        assertEquals("20221003", info.uploadDate)
        assertEquals(1, info.media.size)
        assertEquals("One", info.media.single().title)
        assertEquals("https://media.example/audio/one.m4a", info.media.single().formats.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun revisionIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val revisionId = "07d7f906-fc96-ee11-b75e-000d3a428fff"
        val case = ExtractorCase(
            url = "https://www.bandlab.com/revision/$revisionId",
            infoDict = mapOf(
                "id" to Expect.Value(revisionId),
                "title" to Expect.Value("Fixture Track"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://www.bandlab.com/api/v1.3/revisions/$revisionId?edit=false",
                    contentType = "application/json",
                    body = """
                        {"id": "$revisionId", "song": {"name": "Fixture Track"},
                         "mixdown": {"file": "https://media.example/audio/mix.m4a"}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> BandlabIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
