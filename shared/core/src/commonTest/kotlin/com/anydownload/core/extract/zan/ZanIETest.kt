package com.anydownload.core.extract.zan

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import com.anydownload.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Z-aN subset. The play page fails typed at the session
 * token + refused header wall, and the geo error element maps to a typed
 * GeoRestricted(JP). No cookie, token, or signed URL appears.
 */
class ZanIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val playUrl = "https://www.zan-live.com/en/live/play/1797/663"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val ie = ZanIE(http(transfer()))
        assertTrue(ie.suitable(playUrl))
        assertTrue(ie.suitable("https://www.zan-live.com/ja/live/play/6910/4268"))
        assertFalse(ie.suitable("https://www.example.com/en/live/play/1797/663"))
        assertFalse(ie.suitable("https://www.zan-live.com/en/live/play/1797"))
    }

    // ----------------------------------------------------------------- walls

    @Test
    fun playPageFailsTypedAtTheTokenWall() = runTest {
        val page = FixtureRoute(
            urlPattern = "https://www.zan-live.com/en/live/play/1797/663",
            contentType = "text/html",
            body = """
                <html><head>
                <meta name="csrf-token" content="fake_value">
                <meta name="vod-pct" content="fake_value">
                <meta name="live-player-token" content="fake_value">
                </head><body></body></html>
            """.trimIndent(),
        )
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            ZanIE(http(transfer(page))).extract(playUrl)
        }
        assertTrue(error.message!!.contains("X-Csrf-Token"), error.message)
    }

    @Test
    fun geoErrorPageFailsTyped() = runTest {
        val page = FixtureRoute(
            urlPattern = "https://www.zan-live.com/en/live/play/1797/663",
            contentType = "text/html",
            body = """
                <html><body>
                <p class="p-common_message__headline--error">This content is not available in your region.</p>
                </body></html>
            """.trimIndent(),
        )
        val error = assertFailsWith<ExtractionError.GeoRestricted> {
            ZanIE(http(transfer(page))).extract(playUrl)
        }
        assertEquals(listOf("JP"), error.countries)
    }
}
