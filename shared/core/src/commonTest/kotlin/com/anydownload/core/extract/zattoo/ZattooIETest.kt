package com.anydownload.core.extract.zattoo

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Fixture cases for the Zattoo-platform URL surface. Every URL form matches
 * and fails typed as the account wall; no request is made and no token or
 * media URL appears.
 */
class ZattooIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun everyPlatformUrlFormMatches() {
        val cases = listOf(
            ZattooIE(http()) to "https://zattoo.com/program/fixture-show/250170418",
            ZattooLiveIE(http()) to "https://zattoo.com/live/zdf",
            ZattooMoviesIE(http()) to "https://zattoo.com/vod/movies/fixture123",
            ZattooRecordingsIE(http()) to "https://zattoo.com/recordings?recording=123456",
            NetPlusTVIE(http()) to "https://netplus.tv/program/fixture-show/250170418",
            NetPlusTVLiveIE(http()) to "https://netplus.tv/live/zdf",
            NetPlusTVRecordingsIE(http()) to "https://netplus.tv/recordings?recording=123456",
            MNetTVIE(http()) to "https://tvplus.m-net.de/program/fixture-show/250170418",
            MNetTVLiveIE(http()) to "https://tvplus.m-net.de/live/zdf",
            MNetTVRecordingsIE(http()) to "https://tvplus.m-net.de/recordings?recording=123456",
            WalyTVIE(http()) to "https://player.waly.tv/program/fixture-show/250170418",
            WalyTVLiveIE(http()) to "https://player.waly.tv/live/zdf",
            WalyTVRecordingsIE(http()) to "https://player.waly.tv/recordings?recording=123456",
            BBVTVIE(http()) to "https://bbv-tv.net/program/fixture-show/250170418",
            BBVTVLiveIE(http()) to "https://bbv-tv.net/live/zdf",
            BBVTVRecordingsIE(http()) to "https://bbv-tv.net/recordings?recording=123456",
            VTXTVIE(http()) to "https://vtxtv.ch/program/fixture-show/250170418",
            VTXTVLiveIE(http()) to "https://vtxtv.ch/live/zdf",
            VTXTVRecordingsIE(http()) to "https://vtxtv.ch/recordings?recording=123456",
            GlattvisionTVIE(http()) to "https://iptv.glattvision.ch/program/fixture-show/250170418",
            GlattvisionTVLiveIE(http()) to "https://iptv.glattvision.ch/live/zdf",
            GlattvisionTVRecordingsIE(http()) to "https://iptv.glattvision.ch/recordings?recording=123456",
            SAKTVIE(http()) to "https://saktv.ch/program/fixture-show/250170418",
            SAKTVLiveIE(http()) to "https://saktv.ch/live/zdf",
            SAKTVRecordingsIE(http()) to "https://saktv.ch/recordings?recording=123456",
            EWETVIE(http()) to "https://tvonline.ewe.de/program/fixture-show/250170418",
            EWETVLiveIE(http()) to "https://tvonline.ewe.de/live/zdf",
            EWETVRecordingsIE(http()) to "https://tvonline.ewe.de/recordings?recording=123456",
            QuantumTVIE(http()) to "https://quantum-tv.com/program/fixture-show/250170418",
            QuantumTVLiveIE(http()) to "https://quantum-tv.com/live/zdf",
            QuantumTVRecordingsIE(http()) to "https://quantum-tv.com/recordings?recording=123456",
            OsnatelTVIE(http()) to "https://tvonline.osnatel.de/program/fixture-show/250170418",
            OsnatelTVLiveIE(http()) to "https://tvonline.osnatel.de/live/zdf",
            OsnatelTVRecordingsIE(http()) to "https://tvonline.osnatel.de/recordings?recording=123456",
            EinsUndEinsTVIE(http()) to "https://1und1.tv/program/fixture-show/250170418",
            EinsUndEinsTVLiveIE(http()) to "https://1und1.tv/live/zdf",
            EinsUndEinsTVRecordingsIE(http()) to "https://1und1.tv/recordings?recording=123456",
            SaltTVIE(http()) to "https://tv.salt.ch/program/fixture-show/250170418",
            SaltTVLiveIE(http()) to "https://tv.salt.ch/live/zdf",
            SaltTVRecordingsIE(http()) to "https://tv.salt.ch/recordings?recording=123456",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
    }

    @Test
    fun everyPlatformUrlFormFailsTypedAsTheAccountWall() = runTest {
        val cases = listOf(
            ZattooIE(http()) to "https://zattoo.com/program/fixture-show/250170418",
            ZattooLiveIE(http()) to "https://zattoo.com/live/zdf",
            ZattooMoviesIE(http()) to "https://zattoo.com/vod/movies/fixture123",
            ZattooRecordingsIE(http()) to "https://zattoo.com/?recording=123456",
            NetPlusTVIE(http()) to "https://netplus.tv/program/fixture-show/250170418",
            NetPlusTVLiveIE(http()) to "https://netplus.tv/live/zdf",
            NetPlusTVRecordingsIE(http()) to "https://netplus.tv/?recording=123456",
            MNetTVIE(http()) to "https://tvplus.m-net.de/program/fixture-show/250170418",
            MNetTVLiveIE(http()) to "https://tvplus.m-net.de/live/zdf",
            MNetTVRecordingsIE(http()) to "https://tvplus.m-net.de/?recording=123456",
            WalyTVIE(http()) to "https://player.waly.tv/program/fixture-show/250170418",
            WalyTVLiveIE(http()) to "https://player.waly.tv/live/zdf",
            WalyTVRecordingsIE(http()) to "https://player.waly.tv/?recording=123456",
            BBVTVIE(http()) to "https://bbv-tv.net/program/fixture-show/250170418",
            BBVTVLiveIE(http()) to "https://bbv-tv.net/live/zdf",
            BBVTVRecordingsIE(http()) to "https://bbv-tv.net/?recording=123456",
            VTXTVIE(http()) to "https://vtxtv.ch/program/fixture-show/250170418",
            VTXTVLiveIE(http()) to "https://vtxtv.ch/live/zdf",
            VTXTVRecordingsIE(http()) to "https://vtxtv.ch/?recording=123456",
            GlattvisionTVIE(http()) to "https://iptv.glattvision.ch/program/fixture-show/250170418",
            GlattvisionTVLiveIE(http()) to "https://iptv.glattvision.ch/live/zdf",
            GlattvisionTVRecordingsIE(http()) to "https://iptv.glattvision.ch/?recording=123456",
            SAKTVIE(http()) to "https://saktv.ch/program/fixture-show/250170418",
            SAKTVLiveIE(http()) to "https://saktv.ch/live/zdf",
            SAKTVRecordingsIE(http()) to "https://saktv.ch/?recording=123456",
            EWETVIE(http()) to "https://tvonline.ewe.de/program/fixture-show/250170418",
            EWETVLiveIE(http()) to "https://tvonline.ewe.de/live/zdf",
            EWETVRecordingsIE(http()) to "https://tvonline.ewe.de/?recording=123456",
            QuantumTVIE(http()) to "https://quantum-tv.com/program/fixture-show/250170418",
            QuantumTVLiveIE(http()) to "https://quantum-tv.com/live/zdf",
            QuantumTVRecordingsIE(http()) to "https://quantum-tv.com/?recording=123456",
            OsnatelTVIE(http()) to "https://tvonline.osnatel.de/program/fixture-show/250170418",
            OsnatelTVLiveIE(http()) to "https://tvonline.osnatel.de/live/zdf",
            OsnatelTVRecordingsIE(http()) to "https://tvonline.osnatel.de/?recording=123456",
            EinsUndEinsTVIE(http()) to "https://1und1.tv/program/fixture-show/250170418",
            EinsUndEinsTVLiveIE(http()) to "https://1und1.tv/live/zdf",
            EinsUndEinsTVRecordingsIE(http()) to "https://1und1.tv/?recording=123456",
            SaltTVIE(http()) to "https://tv.salt.ch/program/fixture-show/250170418",
            SaltTVLiveIE(http()) to "https://tv.salt.ch/live/zdf",
            SaltTVRecordingsIE(http()) to "https://tv.salt.ch/?recording=123456",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.LoginRequired>("${extractor.ieKey}: $url") {
                extractor.extract(url)
            }
            assertTrue(
                error.message!!.contains("subscription account"),
                "${extractor.ieKey}: the reason must name the account wall",
            )
        }
    }
}
