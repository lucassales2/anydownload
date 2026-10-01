package com.anydownload.core.extract.floatplane

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Floatplane URL surface is a typed wall: the GraphQL API needs a login
 * session cookie, so no fixture can pass.
 */
class FloatplaneIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            FloatplaneIE(http) to "https://www.floatplane.com/post/fixturePost",
            FloatplaneChannelIE(http) to "https://www.floatplane.com/channel/linustechtips/home/ltxexpo",
            FloatplaneChannelIE(http) to "https://beta.floatplane.com/channel/ShankMods/home",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(FloatplaneIE(http).suitable("https://www.floatplane.com/channel/x/home"))
    }

    @Test
    fun everyClassFailsTypedOnTheLoginWall() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            FloatplaneIE(http) to "https://www.floatplane.com/post/fixturePost",
            FloatplaneChannelIE(http) to "https://www.floatplane.com/channel/linustechtips/home",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.LoginRequired> { extractor.extract(url) }
            assertTrue(error.message!!.contains("sails.sid"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
