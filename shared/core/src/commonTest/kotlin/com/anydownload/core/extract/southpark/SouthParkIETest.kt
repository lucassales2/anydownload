package com.anydownload.core.extract.southpark

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
 * Fixture cases for the South Park subset. The site hosts are real host
 * names in the URL surface; no key, mgid, or media URL appears anywhere.
 */
class SouthParkIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            SouthParkIE(http()) to "https://southpark.cc.com/video-clips/d7wr06/south-park-you-all-agreed-to-counseling",
            SouthParkIE(http()) to "https://www.southparkstudios.com/episodes/h4o269/south-park-stunning-and-brave-season-19-ep-1",
            SouthParkEsIE(http()) to "https://www.southpark.cc.com/es/episodios/fixture-episodio",
            SouthParkDeIE(http()) to "https://www.southpark.de/videoclip/rsribv/south-park-rueckzug-zum-gummibonbon-wald",
            SouthParkDeIE(http()) to "https://www.southpark.de/en/video-clips/ct46op/south-park-tooth-fairy-cartman",
            SouthParkLatIE(http()) to "https://www.southpark.lat/en/video-clips/ct46op/south-park-tooth-fairy-cartman",
            SouthParkLatIE(http()) to "https://www.southpark.lat/episodios/9h0qbg/south-park-orgia-gatuna-temporada-3-ep-7",
            SouthParkDkIE(http()) to "https://www.southparkstudios.nu/video-clips/fixture/south-park-fixture",
            SouthParkComBrIE(http()) to "https://www.southparkstudios.com.br/video-clips/fixture/south-park-fixture",
            SouthParkCoUkIE(http()) to "https://www.southparkstudios.co.uk/episodes/fixture/south-park-fixture",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(SouthParkIE(http()).suitable("https://www.southpark.de/videoclip/x/y"))
    }

    // ------------------------------------------------------------- typed wall

    @Test
    fun everyUrlFormFailsTyped() = runTest {
        val urls = listOf(
            SouthParkIE(http()) to "https://southpark.cc.com/video-clips/d7wr06/south-park-you-all-agreed-to-counseling",
            SouthParkEsIE(http()) to "https://www.southpark.cc.com/es/episodios/fixture-episodio",
            SouthParkDeIE(http()) to "https://www.southpark.de/videoclip/rsribv/south-park-rueckzug-zum-gummibonbon-wald",
            SouthParkLatIE(http()) to "https://www.southpark.lat/en/video-clips/ct46op/south-park-tooth-fairy-cartman",
            SouthParkDkIE(http()) to "https://www.southparkstudios.nu/video-clips/fixture/south-park-fixture",
            SouthParkComBrIE(http()) to "https://www.southparkstudios.com.br/video-clips/fixture/south-park-fixture",
            SouthParkCoUkIE(http()) to "https://www.southparkstudios.co.uk/episodes/fixture/south-park-fixture",
        )
        for ((extractor, url) in urls) {
            assertFailsWith<ExtractionError.LoginRequired> {
                extractor.extract(url)
            }
        }
    }
}
