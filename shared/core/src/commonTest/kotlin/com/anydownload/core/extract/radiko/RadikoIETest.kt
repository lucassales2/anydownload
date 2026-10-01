package com.anydownload.core.extract.radiko

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Radiko subset. Both classes match their URL forms and
 * fail typed at the X-Radiko auth wall; no cookie, token, or signed URL
 * appears.
 */
class RadikoIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    private val timeFreeUrl = "https://radiko.jp/#!/ts/QRR/20210425101300"
    private val liveUrl = "https://radiko.jp/#!/live/QRR"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val timeFree = RadikoIE(http())
        assertTrue(timeFree.suitable(timeFreeUrl))
        assertTrue(timeFree.suitable("https://www.radiko.jp/#!/ts/FMT/20210810150000"))
        assertTrue(timeFree.suitable("https://radiko.jp/#!/ts/JOAK-FM/20210509090000"))
        assertFalse(timeFree.suitable(liveUrl))
        assertFalse(timeFree.suitable("https://www.example.com/#!/ts/QRR/20210425101300"))

        val live = RadikoRadioIE(http())
        assertTrue(live.suitable(liveUrl))
        assertTrue(live.suitable("https://radiko.jp/#!/live/JOAK-FM"))
        assertFalse(live.suitable(timeFreeUrl))
    }

    // ----------------------------------------------------------------- walls

    @Test
    fun timeFreeFailsTypedAtTheAuthWall() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            RadikoIE(http()).extract(timeFreeUrl)
        }
        assertTrue(error.message!!.contains("X-Radiko"), error.message)
    }

    @Test
    fun liveFailsTypedAtTheAuthWall() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            RadikoRadioIE(http()).extract(liveUrl)
        }
        assertTrue(error.message!!.contains("X-Radiko"), error.message)
    }
}
