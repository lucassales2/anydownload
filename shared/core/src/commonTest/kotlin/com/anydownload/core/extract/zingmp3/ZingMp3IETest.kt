package com.anydownload.core.extract.zingmp3

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
 * The Zing MP3 URL surface is a typed wall: the signed API embeds a secret
 * key, so no fixture can pass. The cases prove matching plus the failure.
 */
class ZingMp3IETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ZingMp3IE(http) to "https://zingmp3.vn/bai-hat/Fixture-Song/ZWZB9WAB.html",
            ZingMp3IE(http) to "https://mp3.zing.vn/video-clip/Fixture-Video/ZWZB9WAC.html",
            ZingMp3IE(http) to "https://zingmp3.vn/embed/Fixture-Song/ZWZB9WAB.html",
            ZingMp3AlbumIE(http) to "https://zingmp3.vn/album/Fixture-Album/ZWZB9WAD.html",
            ZingMp3AlbumIE(http) to "https://zingmp3.vn/playlist/Fixture-List/ZWZB9WAE.html",
            ZingMp3ChartHomeIE(http) to "https://zingmp3.vn/zing-chart",
            ZingMp3ChartHomeIE(http) to "https://zingmp3.vn/top100",
            ZingMp3WeekChartIE(http) to "https://zingmp3.vn/zing-chart-tuan/Fixture/ZWZB9WAF.html",
            ZingMp3ChartMusicVideoIE(http) to "https://zingmp3.vn/the-loai-video/Fixture-Region/ZWZB9WAG",
            ZingMp3UserIE(http) to "https://zingmp3.vn/fixture-artist/bai-hat",
            ZingMp3HubIE(http) to "https://zingmp3.vn/hub/Fixture-Hub/ZWZB9WAH",
            ZingMp3LiveRadioIE(http) to "https://zingmp3.vn/liveradio/ZWZB9WAI.html",
            ZingMp3PodcastEpisodeIE(http) to "https://zingmp3.vn/pgr/Fixture-Program/ZWZB9WAJ.html",
            ZingMp3PodcastIE(http) to "https://zingmp3.vn/top-podcast",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ZingMp3IE(http).suitable("https://zingmp3.vn/album/Fixture/ZWZB9WAD.html"))
        assertFalse(ZingMp3AlbumIE(http).suitable("https://zingmp3.vn/bai-hat/Fixture/ZWZB9WAB.html"))
    }

    @Test
    fun everyClassFailsTypedOnTheSignedApi() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ZingMp3IE(http) to "https://zingmp3.vn/bai-hat/Fixture-Song/ZWZB9WAB.html",
            ZingMp3AlbumIE(http) to "https://zingmp3.vn/album/Fixture-Album/ZWZB9WAD.html",
            ZingMp3ChartHomeIE(http) to "https://zingmp3.vn/zing-chart",
            ZingMp3WeekChartIE(http) to "https://zingmp3.vn/zing-chart-tuan/Fixture/ZWZB9WAF.html",
            ZingMp3ChartMusicVideoIE(http) to "https://zingmp3.vn/the-loai-video/Fixture-Region/ZWZB9WAG",
            ZingMp3UserIE(http) to "https://zingmp3.vn/fixture-artist/bai-hat",
            ZingMp3HubIE(http) to "https://zingmp3.vn/hub/Fixture-Hub/ZWZB9WAH",
            ZingMp3LiveRadioIE(http) to "https://zingmp3.vn/liveradio/ZWZB9WAI.html",
            ZingMp3PodcastEpisodeIE(http) to "https://zingmp3.vn/pgr/Fixture-Program/ZWZB9WAJ.html",
            ZingMp3PodcastIE(http) to "https://zingmp3.vn/top-podcast",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.Unavailable> { extractor.extract(url) }
            assertTrue(error.message!!.contains("HMAC-SHA512"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
