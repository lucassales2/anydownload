package com.anydownload.core.extract.naver

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Naver subset. The `now_web` API needs a fixed-key
 * HMAC-SHA1 signature the port does not embed, so both classes match and
 * fail typed; no cookie, token, or signed URL appears anywhere.
 */
class NaverIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    private val videoUrl = "http://tv.naver.com/v/81652"
    private val liveUrl = "https://tv.naver.com/l/127062"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val video = NaverIE(http())
        val videoCases = listOf(
            videoUrl,
            "https://m.tv.naver.com/v/81652",
            "http://tvcast.naver.com/v/81652",
            "https://tv.naver.com/embed/81652",
        )
        for (url in videoCases) {
            assertTrue(video.suitable(url), "Naver must match: $url")
        }
        assertFalse(video.suitable(liveUrl))
        assertFalse(video.suitable("https://www.example.com/v/81652"))

        val live = NaverLiveIE(http())
        assertTrue(live.suitable(liveUrl))
        assertTrue(live.suitable("https://m.tv.naver.com/l/54887"))
        assertFalse(live.suitable(videoUrl))
    }

    // ---------------------------------------------------------------- walls

    @Test
    fun videoFailsTypedAtTheSigningWall() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            NaverIE(http()).extract(videoUrl)
        }
        assertTrue(error.message!!.contains("HMAC"), error.message)
    }

    @Test
    fun liveFailsTypedAtTheSigningWall() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            NaverLiveIE(http()).extract(liveUrl)
        }
        assertTrue(error.message!!.contains("HMAC"), error.message)
    }
}
