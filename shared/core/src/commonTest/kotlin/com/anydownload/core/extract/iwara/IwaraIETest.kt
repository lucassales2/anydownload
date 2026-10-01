package com.anydownload.core.extract.iwara

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
 * Fixture cases for the Iwara subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class IwaraIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "1ywe1sbkqwumpdxz5"
    private val videoUrl = "https://iwara.tv/video/$videoId/"
    private val userUrl = "https://iwara.tv/profile/user792540/videos"
    private val playlistId = "458e5486-36a4-4ac0-b233-7e9eef01025f"
    private val playlistUrl = "https://iwara.tv/playlist/$playlistId"

    private val videoJson = """
        {"title": "Fixture Iwara Title", "body": "Fixture description", "rating": "ecchi",
         "fileUrl": "https://api.iwara.tv/file/$videoId?expires=1678732213&hash=fake_value",
         "user": {"name": "Lyu ya", "username": "user792540"},
         "file": {"id": "581d12b5-46f4-4f15-beb2-cfe2cde5d13d"},
         "numViews": 100, "createdAt": "2023-03-13T00:00:00Z"}
    """.trimIndent()

    private val fileJson = """
        [{"name": "Source", "type": "video/mp4",
          "src": {"view": "https://media.example/source.mp4", "download": "https://media.example/source-dl.mp4"}},
         {"name": "540", "type": "video/mp4", "src": {"view": "//media.example/540.mp4"}}]
    """.trimIndent()

    private fun videoRoutes(video: String = videoJson) = arrayOf(
        FixtureRoute(urlPattern = "https://api.iwara.tv/video/$videoId", contentType = "application/json", body = video),
        FixtureRoute(urlPattern = "https://api.iwara.tv/file/$videoId*", contentType = "application/json", body = fileJson),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val video = IwaraIE(http(transfer()))
        val videoCases = listOf(
            videoUrl,
            "https://www.iwara.tv/video/k2ayoueezfkx6gvq",
            "https://ecchi.iwara.tv/videos/1ywe1sbkqwumpdxz5",
        )
        for (url in videoCases) {
            assertTrue(video.suitable(url), "Iwara must match: $url")
        }
        assertFalse(video.suitable("https://www.example.com/video/$videoId"))

        val user = IwaraUserIE(http(transfer()))
        assertTrue(user.suitable(userUrl))
        assertTrue(user.suitable("https://iwara.tv/profile/theblackbirdcalls"))

        val playlist = IwaraPlaylistIE(http(transfer()))
        assertTrue(playlist.suitable(playlistUrl))
        assertFalse(playlist.suitable(videoUrl))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun videoYieldsTheSignedFileList() = runTest {
        val info = IwaraIE(http(transfer(*videoRoutes()))).extract(videoUrl)
        assertEquals(videoId, info.id)
        assertEquals("Fixture Iwara Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("Lyu ya", info.uploader)
        assertEquals(100L, info.viewCount)
        assertEquals(18, info.ageLimit)
        assertEquals("20230313", info.uploadDate)
        assertEquals(
            "https://files.iwara.tv/image/thumbnail/581d12b5-46f4-4f15-beb2-cfe2cde5d13d/thumbnail-00.jpg",
            info.thumbnails.single().url,
        )
        assertEquals(2, info.formats.size)
        assertEquals("Source", info.formats[0].formatId)
        assertEquals("https://media.example/source.mp4", info.formats[0].url)
        assertEquals(3, info.formats[0].preference)
        assertEquals("540", info.formats[1].formatId)
        assertEquals("https://media.example/540.mp4", info.formats[1].url)
        assertEquals(540L, info.formats[1].height)
        assertEquals(2, info.formats[1].preference)
    }

    @Test
    fun privateVideoFailsTypedAsLoginRequired() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.iwara.tv/video/$videoId",
                contentType = "application/json",
                body = """{"message": "errors.privateVideo"}""",
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            IwaraIE(http(transfer)).extract(videoUrl)
        }
    }

    @Test
    fun notFoundVideoFailsTypedAsLoginRequired() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.iwara.tv/video/$videoId",
                contentType = "application/json",
                body = """{"message": "errors.notFound"}""",
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            IwaraIE(http(transfer)).extract(videoUrl)
        }
    }

    @Test
    fun unplayableVideoFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.iwara.tv/video/$videoId",
                contentType = "application/json",
                body = """{"title": "Fixture"}""",
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            IwaraIE(http(transfer)).extract(videoUrl)
        }
    }

    @Test
    fun embedUrlBecomesARedirect() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.iwara.tv/video/$videoId",
                contentType = "application/json",
                body = """{"embedUrl": "https://www.youtube.com/embed/dQw4w9WgXcQ"}""",
            ),
        )
        val info = IwaraIE(http(transfer)).extract(videoUrl)
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", info.redirectUrl)
    }

    // --------------------------------------------------------------- listings

    @Test
    fun userPageWalksThePagedVideos() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.iwara.tv/profile/user792540",
                contentType = "application/json",
                body = """{"user": {"id": "u1", "name": "Lyu ya"}}""",
            ),
            FixtureRoute(
                urlPattern = "https://api.iwara.tv/videos?page=0*",
                contentType = "application/json",
                body = """{"results": [{"id": "a"}, {"id": "b"}]}""",
            ),
            FixtureRoute(
                urlPattern = "https://api.iwara.tv/videos?page=1*",
                contentType = "application/json",
                body = """{"results": []}""",
            ),
        )
        val info = IwaraUserIE(http(transfer)).extract(userUrl)
        assertEquals("user792540", info.id)
        assertEquals("Lyu ya", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://iwara.tv/video/a", info.entries[0].url)
        assertEquals("https://iwara.tv/video/b", info.entries[1].url)
    }

    @Test
    fun playlistYieldsTheFirstPageAndThenPagedVideos() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.iwara.tv/playlist/$playlistId*",
                contentType = "application/json",
                body = """{"title": "Fixture Playlist", "results": [{"id": "a"}]}""",
            ),
            FixtureRoute(
                urlPattern = "https://api.iwara.tv/videos?page=1*",
                contentType = "application/json",
                body = """{"results": []}""",
            ),
        )
        val info = IwaraPlaylistIE(http(transfer)).extract(playlistUrl)
        assertEquals(playlistId, info.id)
        assertEquals("Fixture Playlist", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://iwara.tv/video/a", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Iwara Title"),
                "upload_date" to Expect.Value("20230313"),
                "formats" to Expect.Count(2),
            ),
            routes = videoRoutes().toList(),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> IwaraIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun playlistIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = playlistUrl,
            infoDict = mapOf(
                "id" to Expect.Value(playlistId),
                "title" to Expect.Value("Fixture Playlist"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api.iwara.tv/playlist/$playlistId*",
                    contentType = "application/json",
                    body = """{"title": "Fixture Playlist", "results": [{"id": "a"}]}""",
                ),
                FixtureRoute(
                    urlPattern = "https://api.iwara.tv/videos?page=1*",
                    contentType = "application/json",
                    body = """{"results": []}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> IwaraPlaylistIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
