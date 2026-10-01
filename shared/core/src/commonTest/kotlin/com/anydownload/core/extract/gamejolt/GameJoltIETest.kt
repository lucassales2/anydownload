package com.anydownload.core.extract.gamejolt

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
 * Fixture cases for the Game Jolt subset. Every id, host, and media address is
 * synthesized (`*.example`); no cookie, token, or signed URL appears.
 */
class GameJoltIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private fun payload(json: String): String = """{"payload": $json}"""

    private val postRoute = FixtureRoute(
        urlPattern = "https://gamejolt.com/site-api/web/posts/view/c6achnzu",
        contentType = "application/json",
        body = payload(
            """
                {"post": {"hash": "c6achnzu", "leadStr": "Fixture Game Jolt Title",
                  "added_on": 1633499590000,
                  "user": {"display_name": "Fixture Uploader", "username": "fixture-uploader"},
                  "videos": {"view_count": 100, "media": [
                    {"img_url": "https://media.example/gj/master.m3u8",
                     "filetype": "application/vnd.apple.mpegurl", "type": "video"},
                    {"img_url": "https://img.example/gj.png", "filetype": "image/png",
                     "type": "thumb", "width": 1280, "height": 720}
                  ]}}}
            """.trimIndent(),
        ),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            GameJoltIE(http(transfer())) to
                "https://gamejolt.com/p/introducing-ramses-jackson-fo-c6achnzu",
            GameJoltUserIE(http(transfer())) to "https://gamejolt.com/@BlazikenSuperStar",
            GameJoltGameIE(http(transfer())) to "https://gamejolt.com/games/Friday4Fun/655124",
            GameJoltGameSoundtrackIE(http(transfer())) to
                "https://gamejolt.com/get/soundtrack?foo=bar&game=657899",
            GameJoltCommunityIE(http(transfer())) to "https://gamejolt.com/c/fnf/videos",
            GameJoltSearchIE(http(transfer())) to "https://gamejolt.com/search?foo=bar&q=%23fnf",
            GameJoltSearchIE(http(transfer())) to "https://gamejolt.com/search/games?q=roblox",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(GameJoltGameIE(http(transfer())).suitable("https://gamejolt.com/@user"))
        assertFalse(GameJoltIE(http(transfer())).suitable("https://gamejolt.com/games/x/1"))
    }

    // ------------------------------------------------------------- GameJoltIE

    @Test
    fun postMapsTheVideoMedia() = runTest {
        val transfer = transfer(postRoute)
        val info = GameJoltIE(http(transfer)).extract(
            "https://gamejolt.com/p/introducing-ramses-jackson-fo-c6achnzu",
        )
        assertEquals("c6achnzu", info.id)
        assertEquals("Fixture Game Jolt Title", info.title)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals("fixture-uploader", info.channelId)
        assertEquals(100L, info.viewCount)
        assertEquals("20211006", info.uploadDate)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://img.example/gj.png", info.thumbnails.single().url)
    }

    @Test
    fun gifPostBecomesChildEntries() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://gamejolt.com/site-api/web/posts/view/gif12345",
                contentType = "application/json",
                body = payload(
                    """
                        {"post": {"hash": "gif12345", "leadStr": "GIF post",
                          "media": [{"img_url": "https://media.example/gj.gif",
                                     "filetype": "image/gif", "hash": "gifhash",
                                     "filename": "fixture.gif"}]}}
                    """.trimIndent(),
                ),
            ),
        )
        val info = GameJoltIE(http(transfer)).extract("https://gamejolt.com/p/gif-post-gif12345")
        assertEquals(1, info.entries.size)
        assertEquals("https://media.example/gj.gif", info.entries[0].url)
        assertEquals("fixture", info.entries[0].title)
    }

    // ------------------------------------------------------------- listings

    @Test
    fun userListsThePosts() = runTest {
        val userId = "fixture-user"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://gamejolt.com/site-api/web/profile/@$userId",
                contentType = "application/json",
                body = payload(
                    """{"user": {"id": 6116784, "display_name": "S. Blaze", "bio_content": "{}"}}""",
                ),
            ),
            FixtureRoute(
                urlPattern = "https://gamejolt.com/site-api/web/posts/fetch/user/@$userId?tab=active",
                contentType = "application/json",
                body = payload(
                    """
                        {"items": [{"action_resource_model": {"hash": "c6achnzu",
                          "leadStr": "Fixture Game Jolt Title",
                          "videos": {"media": []}}}]}
                    """.trimIndent(),
                ),
            ),
        )
        val info = GameJoltUserIE(http(transfer)).extract("https://gamejolt.com/@$userId")
        assertEquals("6116784", info.id)
        assertEquals("S. Blaze", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("c6achnzu", info.entries[0].id)
    }

    @Test
    fun gameSoundtrackListsTheSongs() = runTest {
        val gameId = "657899"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://gamejolt.com/site-api/web/discover/games/overview/$gameId",
                contentType = "application/json",
                body = payload(
                    """
                        {"microdata": {"name": "Fixture Soundtrack"},
                         "songs": [{"id": 184434, "title": "Gettin' Lucky",
                                    "url": "https://media.example/songs/menu.mp3"}]}
                    """.trimIndent(),
                ),
            ),
        )
        val info = GameJoltGameSoundtrackIE(http(transfer)).extract(
            "https://gamejolt.com/get/soundtrack?foo=bar&game=$gameId",
        )
        assertEquals(gameId, info.id)
        assertEquals("Fixture Soundtrack", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://media.example/songs/menu.mp3", info.entries[0].url)
    }

    @Test
    fun communityListsTheChannelPosts() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://gamejolt.com/site-api/web/communities/view/fnf",
                contentType = "application/json",
                body = payload(
                    """{"community": {"name": "Friday Night Funkin'", "description_content": "{}"}}""",
                ),
            ),
            FixtureRoute(
                urlPattern = "https://gamejolt.com/site-api/web/communities/view-channel/fnf/videos",
                contentType = "application/json",
                body = payload("""{"channel": {"display_title": "Videos"}}"""),
            ),
            FixtureRoute(
                urlPattern = "https://gamejolt.com/site-api/web/posts/fetch/community/fnf" +
                    "?channels[]=new&channels[]=videos",
                contentType = "application/json",
                body = payload(
                    """
                        {"items": [{"action_resource_model": {"hash": "c6achnzu",
                          "leadStr": "Fixture Game Jolt Title", "videos": {"media": []}}}]}
                    """.trimIndent(),
                ),
            ),
        )
        val info = GameJoltCommunityIE(http(transfer)).extract("https://gamejolt.com/c/fnf/videos")
        assertEquals("fnf/videos", info.id)
        assertEquals("Friday Night Funkin' - Videos", info.title)
        assertEquals(1, info.entries.size)
    }

    @Test
    fun searchWithAFilterListsTheResults() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://gamejolt.com/site-api/web/search/games?q=roblox",
                contentType = "application/json",
                body = payload(
                    """{"count": 1, "perPage": 20,
                        "games": [{"slug": "fixture-game", "id": 123}]}""",
                ),
            ),
            FixtureRoute(
                urlPattern = "https://gamejolt.com/site-api/web/search/games?q=roblox&page=1",
                contentType = "application/json",
                body = payload(
                    """{"count": 1, "perPage": 20,
                        "games": [{"slug": "fixture-game", "id": 123}]}""",
                ),
            ),
        )
        val info = GameJoltSearchIE(http(transfer)).extract(
            "https://gamejolt.com/search/games?q=roblox",
        )
        assertEquals("roblox", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://gamejolt.com/games/fixture-game/123", info.entries[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun gameJoltIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://gamejolt.com/p/introducing-ramses-jackson-fo-c6achnzu",
            infoDict = mapOf(
                "id" to Expect.Value("c6achnzu"),
                "title" to Expect.Value("Fixture Game Jolt Title"),
                "uploader" to Expect.Value("Fixture Uploader"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(postRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> GameJoltIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun soundtrackIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val gameId = "657899"
        val case = ExtractorCase(
            url = "https://gamejolt.com/get/soundtrack?game=$gameId",
            infoDict = mapOf(
                "id" to Expect.Value(gameId),
                "title" to Expect.Value("Fixture Soundtrack"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://gamejolt.com/site-api/web/discover/games/overview/$gameId",
                    contentType = "application/json",
                    body = payload(
                        """
                            {"microdata": {"name": "Fixture Soundtrack"},
                             "songs": [{"id": 1, "title": "Song", "url": "https://media.example/song.mp3"}]}
                        """.trimIndent(),
                    ),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> GameJoltGameSoundtrackIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
