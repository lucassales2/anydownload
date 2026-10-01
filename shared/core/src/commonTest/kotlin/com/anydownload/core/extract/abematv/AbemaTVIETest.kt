package com.anydownload.core.extract.abematv

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
 * The AbemaTV URL surface is a typed wall: the device token needs a secret
 * application key and the streams are DRM-protected, so no fixture can pass.
 */
class AbemaTVIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            AbemaTVIE(http) to "https://abema.tv/now-on-air/abema-news",
            AbemaTVIE(http) to "https://abema.tv/video/episode/90-1055_s1_p1",
            AbemaTVIE(http) to "https://abema.tv/channels/abema-news/slots/fixture-slot",
            AbemaTVTitleIE(http) to "https://abema.tv/video/title/90-1055",
            AbemaTVTitleIE(http) to "https://abema.tv/video/title/90-1055?s=90-1055_s1",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(AbemaTVIE(http).suitable("https://abema.tv/video/title/90-1055"))
    }

    @Test
    fun everyClassFailsTypedOnTheTokenAndDrmWalls() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            AbemaTVIE(http) to "https://abema.tv/video/episode/90-1055_s1_p1",
            AbemaTVTitleIE(http) to "https://abema.tv/video/title/90-1055",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.Unavailable> { extractor.extract(url) }
            assertTrue(error.message!!.contains("DRM"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
