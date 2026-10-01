package com.anydownload.core.extract.dropout

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
 * Fixture cases for the Dropout subset. Ids and media paths are synthesized;
 * the embed URL uses the public VHX host with a fake video id. No cookie,
 * token, or signed URL appears.
 */
class DropoutIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val watchUrl = "https://watch.dropout.tv/game-changer/season:2/videos/yes-or-no"
    private val seasonUrl = "https://watch.dropout.tv/dimension-20-shriek-week"

    private val watchPage = FixtureRoute(
        urlPattern = "https://watch.dropout.tv/game-changer/season:2/videos/yes-or-no",
        contentType = "text/html",
        body = """
            <html><head>
            <meta name="description" content="Fixture description">
            <meta property="og:image" content="https://media.example/thumb.jpg?crop=1">
            </head><body>
            <script>embed_url: 'https://embed.vhx.tv/videos/738153?api=1';</script>
            <div id="watch-info">
              <h1 class="video-title">Yes or No</h1>
              <div class="text"><span class="site-font-secondary-color">Season 2, Episode 6</span></div>
              <div class="series-title">Game Changer</div>
              <div data-meta-field-name="release_dates" data-meta-field-value="2020-05-08"></div>
            </div>
            </body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val watch = DropoutIE(http(transfer()))
        assertTrue(watch.suitable(watchUrl))
        assertTrue(watch.suitable("https://watch.dropout.tv/videos/misfits-magic-holiday-special"))
        assertTrue(watch.suitable("https://dropout.tv/videos/x"))
        assertFalse(watch.suitable(seasonUrl))

        val season = DropoutSeasonIE(http(transfer()))
        assertTrue(season.suitable("https://watch.dropout.tv/dimension-20-fantasy-high/season:1"))
        assertTrue(season.suitable(seasonUrl))
        assertFalse(season.suitable(watchUrl))
        assertFalse(season.suitable("https://www.example.com/dimension-20-shriek-week"))
    }

    // --------------------------------------------------------------- watch

    @Test
    fun watchYieldsTheEmbedEntryAndMetadata() = runTest {
        val info = DropoutIE(http(transfer(watchPage))).extract(watchUrl)
        assertEquals("738153", info.id)
        assertEquals("Yes or No", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals("20200508", info.uploadDate)
        assertEquals(1, info.entries.size)
        assertEquals("https://embed.vhx.tv/videos/738153?api=1", info.entries[0].url)
    }

    @Test
    fun watchIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = watchUrl,
            infoDict = mapOf(
                "id" to Expect.Value("738153"),
                "title" to Expect.Value("Yes or No"),
                "upload_date" to Expect.Value("20200508"),
            ),
            routes = listOf(watchPage),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> DropoutIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun unauthorizedPageFailsTyped() = runTest {
        val lockedPage = FixtureRoute(
            urlPattern = "https://watch.dropout.tv/game-changer/season:2/videos/yes-or-no",
            contentType = "text/html",
            body = """<html><body><div id="watch-unauthorized">Sign in</div></body></html>""",
        )
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            DropoutIE(http(transfer(lockedPage))).extract(watchUrl)
        }
        assertTrue(error.message!!.contains("sign-in"), error.message)
    }

    // -------------------------------------------------------------- season

    @Test
    fun seasonPaginatesTheBrowseItems() = runTest {
        val firstPage = FixtureRoute(
            urlPattern = "https://watch.dropout.tv/dimension-20-shriek-week?page=1",
            contentType = "text/html",
            body = """
                <html><body>
                <a class="browse-item-link" href="https://watch.dropout.tv/dimension-20-shriek-week/videos/episode-one">One</a>
                <a class="browse-item-link" href="https://watch.dropout.tv/dimension-20-shriek-week/videos/episode-two">Two</a>
                </body></html>
            """.trimIndent(),
        )
        val secondPage = FixtureRoute(
            urlPattern = "https://watch.dropout.tv/dimension-20-shriek-week?page=2",
            contentType = "text/html",
            body = """<html><body></body></html>""",
        )
        val info = DropoutSeasonIE(http(transfer(firstPage, secondPage))).extract(seasonUrl)
        assertEquals("dimension-20-shriek-week-season-1", info.id)
        assertEquals("Dimension 20 Shriek Week - Season 1", info.title)
        assertEquals(2, info.entries.size)
        assertEquals(
            "https://watch.dropout.tv/dimension-20-shriek-week/videos/episode-one",
            info.entries[0].url,
        )
        assertEquals(
            "https://watch.dropout.tv/dimension-20-shriek-week/videos/episode-two",
            info.entries[1].url,
        )
    }

    @Test
    fun seasonWithTheSeasonInTheUrlNamesThePlaylist() = runTest {
        val firstPage = FixtureRoute(
            urlPattern = "https://watch.dropout.tv/dimension-20-fantasy-high/season:3?page=1",
            contentType = "text/html",
            body = """
                <html><body>
                <a class="browse-item-link" href="https://watch.dropout.tv/dimension-20-fantasy-high/videos/episode-one">One</a>
                </body></html>
            """.trimIndent(),
        )
        val secondPage = FixtureRoute(
            urlPattern = "https://watch.dropout.tv/dimension-20-fantasy-high/season:3?page=2",
            contentType = "text/html",
            body = """<html><body></body></html>""",
        )
        val info = DropoutSeasonIE(http(transfer(firstPage, secondPage))).extract(
            "https://watch.dropout.tv/dimension-20-fantasy-high/season:3",
        )
        assertEquals("dimension-20-fantasy-high-season-3", info.id)
        assertEquals("Dimension 20 Fantasy High - Season 3", info.title)
        assertEquals(1, info.entries.size)
    }
}
