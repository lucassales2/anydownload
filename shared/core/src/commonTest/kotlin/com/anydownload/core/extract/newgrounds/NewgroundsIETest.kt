package com.anydownload.core.extract.newgrounds

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
 * Fixture cases for the Newgrounds subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class NewgroundsIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val audioUrl = "https://www.newgrounds.com/audio/listen/549479"
    private val portalUrl = "https://www.newgrounds.com/portal/view/1"
    private val playlistUrl = "https://www.newgrounds.com/collection/cats"
    private val userUrl = "https://burn7.newgrounds.com/audio"

    private val audioPage = """
        <html><head>
        <title>B7 - BusMode</title>
        <meta property="og:image" content="https://aicon.ngfiles.com/549/549479.png">
        <meta property="og:description" content="Fixture og description">
        </head><body>
        <h2 class="rated-e"></h2>
        <h4>Burn7</h4><em>Author</em>
        <div id="author_comments">Fixture author comments</div>
        <dt>Listens</dt><dd>1,234</dd>
        <span itemprop="uploadDate" content="2013-09-11T12:00:00Z"></span>
        <script>
        embedController([{"url":"https://media.example/audio.mp3","id":549479}], 0, 0, "video");
        "duration":"143", "filesize":"12345", "description":"Audio File",
        </script>
        </body></html>
    """.trimIndent()

    private val portalPage = """
        <html><head>
        <title>Scrotum 1</title>
        <meta property="og:image" content="https://picon.ngfiles.com/0/flash_1_card.png">
        </head><body>
        <h2 class="rated-m"></h2>
        <div id="author_comments">Scrotum plays catch.</div>
        <dt>Views</dt><dd>1,000</dd>
        <span itemprop="uploadDate" content="2000-04-07T00:00:00Z"></span>
        </body></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val media = NewgroundsIE(http(transfer()))
        val mediaCases = listOf(
            audioUrl,
            portalUrl,
            "https://www.newgrounds.com/portal/view/297383/format/flash",
        )
        for (url in mediaCases) {
            assertTrue(media.suitable(url), "Newgrounds must match: $url")
        }
        assertFalse(media.suitable("https://www.example.com/portal/view/1"))

        val playlist = NewgroundsPlaylistIE(http(transfer()))
        assertTrue(playlist.suitable(playlistUrl))
        assertTrue(playlist.suitable("http://www.newgrounds.com/audio/search/title/cats"))

        val user = NewgroundsUserIE(http(transfer()))
        assertTrue(user.suitable(userUrl))
        assertTrue(user.suitable("https://brian-beaton.newgrounds.com/movies"))
        assertFalse(user.suitable(portalUrl))
    }

    // ------------------------------------------------------------------ media

    @Test
    fun audioPageYieldsTheSourceUrlAndMetadata() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = audioUrl, contentType = "text/html", body = audioPage))
        val info = NewgroundsIE(http(transfer)).extract(audioUrl)
        assertEquals("549479", info.id)
        assertEquals("B7 - BusMode", info.title)
        assertEquals("Burn7", info.uploader)
        assertEquals(143.0, info.duration)
        assertEquals("20130911", info.uploadDate)
        assertEquals(1234L, info.viewCount)
        assertEquals(0, info.ageLimit)
        assertEquals("Fixture author comments", info.description)
        assertEquals("https://aicon.ngfiles.com/549/549479.png", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/audio.mp3", info.formats[0].url)
        assertEquals("source", info.formats[0].formatId)
        assertEquals(12345L, info.formats[0].filesize)
        assertEquals("none", info.formats[0].vcodec)
    }

    @Test
    fun portalPageYieldsTheSourcesMap() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = portalUrl, contentType = "text/html", body = portalPage),
            FixtureRoute(
                urlPattern = "https://www.newgrounds.com/portal/video/1",
                contentType = "application/json",
                body = """
                    {"author": "Brian-Beaton", "sources": {
                      "720p": [{"src": "https://media.example/720.mp4"}],
                      "480p": [{"src": "https://media.example/480.mp4"}]
                    }}
                """.trimIndent(),
            ),
        )
        val info = NewgroundsIE(http(transfer)).extract(portalUrl)
        assertEquals("1", info.id)
        assertEquals("Brian-Beaton", info.uploader)
        assertEquals(17, info.ageLimit)
        assertEquals(2, info.formats.size)
        assertEquals("720p", info.formats[0].formatId)
        assertEquals(720, info.formats[0].preference)
        assertEquals("https://media.example/480.mp4", info.formats[1].url)
    }

    // --------------------------------------------------------------- listings

    @Test
    fun collectionPageListsTheSubmissionEntries() = runTest {
        val page = """
            <html><head><title>Cats</title></head><body>
            <div class="column wide">
              <a class="item-portalsubmission" href="/portal/view/111">One</a>
              <a class="item-audiosubmission" href="/audio/listen/222">Two</a>
              <a class="other" href="/portal/view/333">Skip</a>
            </div>
            </body></html>
        """.trimIndent()
        val info = NewgroundsPlaylistIE(http(transfer(FixtureRoute(urlPattern = playlistUrl, contentType = "text/html", body = page))))
            .extract(playlistUrl)
        assertEquals("cats", info.id)
        assertEquals("Cats", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("111", info.entries[0].id)
        assertEquals("https://www.newgrounds.com/portal/view/111", info.entries[0].url)
        assertEquals("https://www.newgrounds.com/audio/listen/222", info.entries[1].url)
    }

    @Test
    fun userPageWalksThePagedJson() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$userUrl?page=1",
                contentType = "application/json",
                body = """
                    {"items": [
                      ["<a href=\"/audio/listen/333\">x</a>"],
                      ["<a href=\"/portal/view/444\">y</a>"]
                    ]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "$userUrl?page=2",
                contentType = "application/json",
                body = """{"items": []}""",
            ),
        )
        val info = NewgroundsUserIE(http(transfer)).extract(userUrl)
        assertEquals("burn7", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("333", info.entries[0].id)
        assertEquals("https://www.newgrounds.com/audio/listen/333", info.entries[0].url)
        assertEquals("https://www.newgrounds.com/portal/view/444", info.entries[1].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun audioIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = audioUrl,
            infoDict = mapOf(
                "id" to Expect.Value("549479"),
                "title" to Expect.Value("B7 - BusMode"),
                "uploader" to Expect.Value("Burn7"),
                "upload_date" to Expect.Value("20130911"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(FixtureRoute(urlPattern = audioUrl, contentType = "text/html", body = audioPage)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NewgroundsIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun collectionIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val page = """
            <html><head><title>Cats</title></head><body>
            <div class="column wide">
              <a class="item-portalsubmission" href="/portal/view/111">One</a>
              <a class="item-audiosubmission" href="/audio/listen/222">Two</a>
            </div>
            </body></html>
        """.trimIndent()
        val case = ExtractorCase(
            url = playlistUrl,
            infoDict = mapOf(
                "id" to Expect.Value("cats"),
                "title" to Expect.Value("Cats"),
                "entries" to Expect.Count(2),
            ),
            routes = listOf(FixtureRoute(urlPattern = playlistUrl, contentType = "text/html", body = page)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NewgroundsPlaylistIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
