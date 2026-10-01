package com.anydownload.core.extract.turner

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoDict
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import com.anydownload.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Fixture cases for the Turner base. The base declares no registered class,
 * so the tests drive its protected helpers through a local subclass; media
 * lives on `media.example`, and the tokenizer token is a fake value.
 */
class TurnerBaseIETest {

    /** A local subclass exposing the base helpers for the tests. */
    private class TurnerTestIE(http: ExtractorHttp) : TurnerBaseIE("TurnerTest", http) {
        override suspend fun extract(url: String): InfoDict = throw ExtractionError.UnsupportedUrl()

        suspend fun ngtv(
            mediaId: String,
            tokenizerQuery: Map<String, String> = emptyMap(),
            apData: TurnerApData? = null,
        ): NgtvInfo = extractNgtvInfo(mediaId, tokenizerQuery, apData)

        fun timestamp(xml: String): Long? = extractTimestamp(xml)
    }

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    @Test
    fun ngtvUnprotectedYieldsTheHlsRowAndChapters() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://medium.ngtv.io/media/123/tv",
                contentType = "application/json",
                body = """
                    {"media": {"tv": {"unprotected": {
                      "url": "https://media.example/master.m3u8", "totalRuntime": 100.5,
                      "contentSegments": [
                        {"start": 0, "duration": 10},
                        {"start": 10, "duration": 20}
                      ]
                    }}}}
                """.trimIndent(),
            ),
        )
        val info = TurnerTestIE(http(transfer)).ngtv("123")
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals(100.5, info.duration)
        assertEquals(2, info.chapters.size)
        assertEquals(10.0, info.chapters[0].endTime)
    }

    @Test
    fun ngtvBulkaesIsTheFallback() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://medium.ngtv.io/media/456/tv",
                contentType = "application/json",
                body = """{"media": {"tv": {"bulkaes": {"url": "https://media.example/bulk.m3u8"}}}}""",
            ),
        )
        val info = TurnerTestIE(http(transfer)).ngtv("456")
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/bulk.m3u8", info.formats[0].url)
    }

    @Test
    fun speProtectedPlaylistGetsThePublicTokenizerToken() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://medium.ngtv.io/media/789/tv",
                contentType = "application/json",
                body = """
                    {"media": {"tv": {"unprotected": {
                      "secureUrl": "https://media.example/secure/master.m3u8",
                      "playlistProtection": "spe", "totalRuntime": 50
                    }}}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://token.ngtv.io/token/token_spe*",
                contentType = "text/xml",
                body = "<auth><token>fake_token</token></auth>",
            ),
        )
        val info = TurnerTestIE(http(transfer)).ngtv("789")
        assertEquals(
            "https://media.example/secure/master.m3u8?hdnea=fake_token",
            info.formats.single().url,
        )
    }

    @Test
    fun authRequiredTokenizerIsTheTypedLoginWall() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://medium.ngtv.io/media/321/tv",
                contentType = "application/json",
                body = """
                    {"media": {"tv": {"unprotected": {
                      "secureUrl": "https://media.example/secure-auth/master.m3u8",
                      "playlistProtection": "spe", "totalRuntime": 10
                    }}}}
                """.trimIndent(),
            ),
        )
        assertFailsWith<ExtractionError.LoginRequired> {
            TurnerTestIE(http(transfer)).ngtv("321", apData = TurnerApData(authRequired = true))
        }
    }

    @Test
    fun noStreamsFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://medium.ngtv.io/media/654/tv",
                contentType = "application/json",
                body = """{"media": {"tv": {}}}""",
            ),
        )
        assertFailsWith<ExtractionError.NoFormats> {
            TurnerTestIE(http(transfer)).ngtv("654")
        }
    }

    @Test
    fun timestampReadsTheUtsAttribute() {
        val timestamp = TurnerTestIE(http(transfer()))
            .timestamp("<item id=\"x\"><dateCreated uts=\"1600000000\">2020</dateCreated></item>")
        assertEquals(1600000000L, timestamp)
    }
}
