package com.anydownload.core.extract.redgifs

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
 * Fixture cases for the RedGifs subset. Ids and media paths are synthesized on
 * `media.example`; the temporary token is a fake value. No cookie, real
 * token, or signed URL appears.
 */
class RedGifsIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val watchUrl = "https://www.redgifs.com/watch/squeakyhelplesswisent"
    private val searchUrl = "https://www.redgifs.com/browse?tags=Lesbian"
    private val userUrl = "https://www.redgifs.com/users/lamsinka89"

    private val tokenRoute = FixtureRoute(
        urlPattern = "https://api.redgifs.com/v2/auth/temporary",
        contentType = "application/json",
        body = """{"token": "fake_value"}""",
    )

    private val gifRoute = FixtureRoute(
        urlPattern = "https://api.redgifs.com/v2/gifs/squeakyhelplesswisent?views=yes",
        contentType = "application/json",
        body = """
            {"gif": {"id": "squeakyhelplesswisent", "tags": ["Fixture", "Tag"],
              "createDate": 1636287915, "userName": "ignored52", "duration": 16,
              "views": 10, "likes": 2, "width": 1000, "height": 500,
              "urls": {"gif": "https://media.example/gif.mp4",
                       "sd": "https://media.example/sd.mp4",
                       "hd": "https://media.example/hd.mp4"}}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val gif = RedGifsIE(http(transfer()))
        assertTrue(gif.suitable(watchUrl))
        assertTrue(gif.suitable("https://thumbs2.redgifs.com/SqueakyHelplessWisent-mobile.mp4#t=0"))
        assertTrue(gif.suitable("https://www.redgifs.com/ifr/squeakyhelplesswisent"))
        assertFalse(gif.suitable(searchUrl))
        assertFalse(gif.suitable("https://www.example.com/watch/foo"))

        val search = RedGifsSearchIE(http(transfer()))
        assertTrue(search.suitable(searchUrl))
        assertTrue(search.suitable("https://www.redgifs.com/browse?type=g&order=latest&tags=Lesbian"))
        assertFalse(search.suitable(userUrl))

        val user = RedGifsUserIE(http(transfer()))
        assertTrue(user.suitable(userUrl))
        assertTrue(user.suitable("https://www.redgifs.com/users/lamsinka89?order=best&type=g"))
        assertFalse(user.suitable(searchUrl))
    }

    // ------------------------------------------------------------------ gif

    @Test
    fun watchYieldsTheFormatRowsAndMetadata() = runTest {
        val info = RedGifsIE(http(transfer(tokenRoute, gifRoute))).extract(watchUrl)
        assertEquals("squeakyhelplesswisent", info.id)
        assertEquals("Fixture Tag", info.title)
        assertEquals("ignored52", info.uploader)
        assertEquals("20211107", info.uploadDate)
        assertEquals(16.0, info.duration)
        assertEquals(10L, info.viewCount)
        assertEquals(18, info.ageLimit)
        assertEquals(3, info.formats.size)
        assertEquals("gif", info.formats[0].formatId)
        assertEquals(250L, info.formats[0].height)
        assertEquals(500L, info.formats[0].width)
        assertEquals("0", info.formats[0].quality)
        assertEquals("sd", info.formats[1].formatId)
        assertEquals(480L, info.formats[1].height)
        assertEquals(960L, info.formats[1].width)
        assertEquals("1", info.formats[1].quality)
        assertEquals("hd", info.formats[2].formatId)
        assertEquals(500L, info.formats[2].height)
        assertEquals(1000L, info.formats[2].width)
        assertEquals("2", info.formats[2].quality)
    }

    @Test
    fun watchIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = watchUrl,
            infoDict = mapOf(
                "id" to Expect.Value("squeakyhelplesswisent"),
                "title" to Expect.Value("Fixture Tag"),
                "upload_date" to Expect.Value("20211107"),
                "formats" to Expect.Count(3),
            ),
            routes = listOf(tokenRoute, gifRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RedGifsIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun apiErrorBecomesTypedUnavailable() = runTest {
        val errorRoute = FixtureRoute(
            urlPattern = "https://api.redgifs.com/v2/gifs/squeakyhelplesswisent?views=yes",
            contentType = "application/json",
            body = """{"error": "Fixture error"}""",
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            RedGifsIE(http(transfer(tokenRoute, errorRoute))).extract(watchUrl)
        }
        assertTrue(error.message!!.contains("RedGifs said: Fixture error"), error.message)
    }

    // --------------------------------------------------------------- search

    @Test
    fun searchPaginatesTheGifs() = runTest {
        val firstPage = FixtureRoute(
            urlPattern = "https://api.redgifs.com/v2/gifs/search?search_text=Lesbian&order=trending&page=1",
            contentType = "application/json",
            body = """{"gifs": [{"id": "one"}, {"id": "two"}]}""",
        )
        val secondPage = FixtureRoute(
            urlPattern = "https://api.redgifs.com/v2/gifs/search?search_text=Lesbian&order=trending&page=2",
            contentType = "application/json",
            body = """{"gifs": []}""",
        )
        val info = RedGifsSearchIE(http(transfer(tokenRoute, firstPage, secondPage))).extract(searchUrl)
        assertEquals("tags=Lesbian", info.id)
        assertEquals("Lesbian", info.title)
        assertEquals("RedGifs search for Lesbian, ordered by trending", info.description)
        assertEquals(2, info.entries.size)
        assertEquals("https://redgifs.com/watch/one", info.entries[0].url)
        assertEquals("https://redgifs.com/watch/two", info.entries[1].url)
    }

    @Test
    fun searchWithPageFetchesOnlyThatPage() = runTest {
        val pageRoute = FixtureRoute(
            urlPattern = "https://api.redgifs.com/v2/gifs/search?*page=2",
            contentType = "application/json",
            body = """{"gifs": [{"id": "three"}]}""",
        )
        val info = RedGifsSearchIE(http(transfer(tokenRoute, pageRoute))).extract(
            "https://www.redgifs.com/browse?type=g&order=latest&tags=Lesbian&page=2",
        )
        assertEquals("type=g&order=latest&tags=Lesbian&page=2", info.id)
        assertEquals("RedGifs search for Lesbian, ordered by latest", info.description)
        assertEquals(1, info.entries.size)
        assertEquals("https://redgifs.com/watch/three", info.entries[0].url)
    }

    @Test
    fun searchWithoutTagsFailsTyped() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            RedGifsSearchIE(http(transfer())).extract("https://www.redgifs.com/browse?order=latest")
        }
        assertTrue(error.message!!.contains("Invalid query tags"), error.message)
    }

    // ----------------------------------------------------------------- user

    @Test
    fun userPaginatesTheGifs() = runTest {
        val firstPage = FixtureRoute(
            urlPattern = "https://api.redgifs.com/v2/users/lamsinka89/search?order=recent&page=1",
            contentType = "application/json",
            body = """{"gifs": [{"id": "one"}]}""",
        )
        val secondPage = FixtureRoute(
            urlPattern = "https://api.redgifs.com/v2/users/lamsinka89/search?order=recent&page=2",
            contentType = "application/json",
            body = """{"gifs": []}""",
        )
        val info = RedGifsUserIE(http(transfer(tokenRoute, firstPage, secondPage))).extract(userUrl)
        assertEquals("lamsinka89", info.id)
        assertEquals("lamsinka89", info.title)
        assertEquals("RedGifs user lamsinka89, ordered by recent", info.description)
        assertEquals(1, info.entries.size)
        assertEquals("https://redgifs.com/watch/one", info.entries[0].url)
    }
}
