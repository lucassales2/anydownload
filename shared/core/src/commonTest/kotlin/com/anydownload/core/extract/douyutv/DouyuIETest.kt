package com.anydownload.core.extract.douyutv

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import com.anydownload.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Douyu subset. The stream signature is a JS wall, so
 * the tests assert the typed failures and the page-level live checks; no
 * cookie, token, or signed URL appears anywhere.
 */
class DouyuIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val roomUrl = "https://www.douyu.com/pigff"
    private val showUrl = "https://v.douyu.com/show/mPyq7oVNe5Yv1gLY"

    private fun page(body: String) = "<html><body><script>$body</script></body></html>"

    private fun roomPage() =
        page("\$ROOM.room_id = 24422; \$ROOM.show_status = 1; \"videoLoop\": 0;")

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val tv = DouyuTVIE(http(transfer()))
        val tvCases = listOf(
            roomUrl,
            "http://www.douyutv.com/85982",
            "https://www.douyu.com/topic/ydxc?rid=6560603",
            "http://www.douyu.com/t/xiaocang",
        )
        for (url in tvCases) {
            assertTrue(tv.suitable(url), "DouyuTV must match: $url")
        }
        assertFalse(tv.suitable("https://www.example.com/pigff"))

        val show = DouyuShowIE(http(transfer()))
        assertTrue(show.suitable(showUrl))
        assertTrue(show.suitable("https://vmobile.douyu.com/show/rjNBdvnVXNzvE2yw"))
        assertFalse(show.suitable(roomUrl))
    }

    // ------------------------------------------------------------------ room

    @Test
    fun autoPlayingVodsFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = roomUrl, contentType = "text/html", body = page("\$ROOM.room_id = 24422; \"videoLoop\": 1")),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            DouyuTVIE(http(transfer)).extract(roomUrl)
        }
    }

    @Test
    fun offlineRoomFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = roomUrl,
                contentType = "text/html",
                body = page("\$ROOM.room_id = 24422; \"videoLoop\": 0; \$ROOM.show_status = 2;"),
            ),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            DouyuTVIE(http(transfer)).extract(roomUrl)
        }
        assertTrue(error.message!!.contains("not live"), error.message)
    }

    @Test
    fun liveRoomFailsTypedAtTheJsSigningWall() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = roomUrl, contentType = "text/html", body = roomPage()))
        val error = assertFailsWith<ExtractionError.Unavailable> {
            DouyuTVIE(http(transfer)).extract(roomUrl)
        }
        assertTrue(error.message!!.contains("JS"), error.message)
    }

    @Test
    fun roomPageWithoutARoomIdFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = roomUrl, contentType = "text/html", body = page("no room data")),
        )
        assertFailsWith<ExtractionError.Malformed> {
            DouyuTVIE(http(transfer)).extract(roomUrl)
        }
    }

    // ------------------------------------------------------------------ show

    @Test
    fun showPageFailsTypedAtTheJsSigningWall() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = showUrl,
                contentType = "text/html",
                body = page("window.\$DATA = {\"ROOM\": {\"point_id\": 123}};"),
            ),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            DouyuShowIE(http(transfer)).extract(showUrl)
        }
        assertTrue(error.message!!.contains("JS"), error.message)
    }
}
