package com.anydownload.core.extract.vrt

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the VRT URL surface. Every URL form matches and fails
 * typed as the player-token wall; no request is made and no token or media
 * URL appears.
 */
class VrtIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<String, String>>(
            VRTIE.IE_KEY to
                "https://www.vrt.be/vrtnws/nl/2019/05/15/beelden-van-binnenkant-notre-dame/",
            VRTIE.IE_KEY to "https://sporza.be/nl/2019/05/15/de-belgian-cats-zijn-klaar-voor-het-ek/",
            VrtNUIE.IE_KEY to "https://www.vrt.be/vrtmax/a-z/ket---doc/trailer/ket---doc-trailer-s6/",
            VrtNUIE.IE_KEY to "https://www.vrt.be/vrtnu/a-z/meisjes/6/meisjes-s6a5/",
            DagelijkseKostIE.IE_KEY to
                "https://dagelijksekost.een.be/gerechten/hachis-parmentier-met-witloof",
            Radio1BeIE.IE_KEY to
                "https://radio1.be/luister/select/de-ochtend/komt-n-va-volgend-jaar-op-in-wallonie",
            Radio1BeIE.IE_KEY to "https://radio1.be/lees/europese-unie-wil-pauze",
        )
        val extractors = mapOf(
            VRTIE.IE_KEY to VRTIE(http()),
            VrtNUIE.IE_KEY to VrtNUIE(http()),
            DagelijkseKostIE.IE_KEY to DagelijkseKostIE(http()),
            Radio1BeIE.IE_KEY to Radio1BeIE(http()),
        )
        for ((key, url) in cases) {
            assertTrue(extractors.getValue(key).suitable(url), "$key must match: $url")
        }
        assertFalse(DagelijkseKostIE(http()).suitable("https://dagelijksekost.een.be/"))
        assertFalse(Radio1BeIE(http()).suitable("https://radio1.be/lees"))
    }

    @Test
    fun everyUrlFormFailsTypedAsTheTokenWall() = runTest {
        val cases = listOf<Pair<VRTBaseIE, String>>(
            VRTIE(http()) to
                "https://www.vrt.be/vrtnws/nl/2019/05/15/beelden-van-binnenkant-notre-dame/",
            VrtNUIE(http()) to "https://www.vrt.be/vrtmax/a-z/ket---doc/trailer/ket---doc-trailer-s6/",
            DagelijkseKostIE(http()) to
                "https://dagelijksekost.een.be/gerechten/hachis-parmentier-met-witloof",
            Radio1BeIE(http()) to
                "https://radio1.be/luister/select/de-ochtend/komt-n-va-volgend-jaar-op-in-wallonie",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.Unavailable>("${extractor.ieKey}: $url") {
                extractor.extract(url)
            }
            assertTrue(
                error.message!!.contains("vrtPlayerToken"),
                "${extractor.ieKey}: the reason must name the player token",
            )
        }
    }

    @Test
    fun theReasonIsOneSentence() {
        assertTrue(VRTBaseIE.TOKEN_WALL_MESSAGE.startsWith("The VRT media-services API"))
        assertTrue(VRTBaseIE.TOKEN_WALL_MESSAGE.contains("not translated."))
    }
}
