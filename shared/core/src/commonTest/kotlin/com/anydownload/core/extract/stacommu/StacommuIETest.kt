package com.anydownload.core.extract.stacommu

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the Stacommu / Theater Complex Town subset. All four
 * classes match their URL forms and fail typed at the Firebase token + RSA
 * wall; no API key, device id, token, or media URL appears.
 */
class StacommuIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    private val vodUrl = "https://www.stacommu.jp/videos/episodes/aXcVKjHyAENEjard61soZZ"
    private val liveUrl = "https://www.stacommu.jp/live/d2FJ3zLnndegZJCAEzGM3m"
    private val theaterVodUrl = "https://www.theater-complex.town/videos/episodes/hoxqidYNoAn7bP92DN6p78"
    private val theaterPpvUrl = "https://www.theater-complex.town/ppv/wytW3X7khrjJBUpKuV3jen"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val vod = StacommuVODIE(http())
        assertTrue(vod.suitable(vodUrl))
        assertTrue(vod.suitable("https://www.stacommu.jp/en/videos/episodes/aXcVKjHyAENEjard61soZZ"))
        assertFalse(vod.suitable(liveUrl))
        assertFalse(vod.suitable("https://www.example.com/videos/episodes/x"))

        val live = StacommuLiveIE(http())
        assertTrue(live.suitable(liveUrl))
        assertTrue(live.suitable("https://www.stacommu.jp/en/live/d2FJ3zLnndegZJCAEzGM3m"))
        assertFalse(live.suitable(vodUrl))

        val theaterVod = TheaterComplexTownVODIE(http())
        assertTrue(theaterVod.suitable(theaterVodUrl))
        assertTrue(theaterVod.suitable("https://www.theater-complex.town/en/videos/episodes/6QT7XYwM9dJz5Gf9VB6K5y"))
        assertTrue(theaterVod.suitable("https://www.theater-complex.town/ja/videos/episodes/hoxqidYNoAn7bP92DN6p78"))
        assertFalse(theaterVod.suitable(theaterPpvUrl))

        val theaterPpv = TheaterComplexTownPPVIE(http())
        assertTrue(theaterPpv.suitable(theaterPpvUrl))
        assertTrue(theaterPpv.suitable("https://www.theater-complex.town/en/live/79akNM7bJeD5Fi9EP39aDp"))
        assertFalse(theaterPpv.suitable(theaterVodUrl))
    }

    // ----------------------------------------------------------------- walls

    @Test
    fun stacommuVodFailsTyped() = runTest {
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            StacommuVODIE(http()).extract(vodUrl)
        }
        assertTrue(error.message!!.contains("RSA"), error.message)
    }

    @Test
    fun stacommuLiveFailsTyped() = runTest {
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            StacommuLiveIE(http()).extract(liveUrl)
        }
        assertTrue(error.message!!.contains("RSA"), error.message)
    }

    @Test
    fun theaterVodFailsTyped() = runTest {
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            TheaterComplexTownVODIE(http()).extract(theaterVodUrl)
        }
        assertTrue(error.message!!.contains("RSA"), error.message)
    }

    @Test
    fun theaterPpvFailsTyped() = runTest {
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            TheaterComplexTownPPVIE(http()).extract(theaterPpvUrl)
        }
        assertTrue(error.message!!.contains("RSA"), error.message)
    }
}
