package com.anydownload.core.extract.lsm

import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.harness.CaseResult
import com.anydownload.core.extract.harness.Expect
import com.anydownload.core.extract.harness.ExtractorCase
import com.anydownload.core.extract.harness.ExtractorTestRun
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import com.anydownload.core.extract.harness.FixtureRoute
import com.anydownload.core.extract.harness.runCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the LSM subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class LsmIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            LSMLREmbedIE(http(transfer())) to "https://latvijasradio.lsm.lv/lv/embed/?theme=black&id=183522",
            LSMLREmbedIE(http(transfer())) to "https://lr1.lsm.lv/lv/pleijeris/?embed=0&id=48205",
            LSMLTVEmbedIE(http(transfer())) to "https://ltv.lsm.lv/embed?c=fixture_value",
            LSMReplayIE(http(transfer())) to
                "https://replay.lsm.lv/lv/skaties/ieraksts/ltv/311130/fixture-replay",
            LSMReplayIE(http(transfer())) to
                "https://replay.lsm.lv/lv/klausies/ieraksts/lr/183522/fixture-audio",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(LSMLTVEmbedIE(http(transfer())).suitable("https://ltv.lsm.lv/other?c=x"))
    }

    // ---------------------------------------------------------------- lr embed

    @Test
    fun lrEmbedYieldsAudioAndVideoItems() = runTest {
        val url = "https://lr1.lsm.lv/lv/embed/?id=166557&show=0&theme=white&size=16x9"
        val page = """
            <html><body><script>
            LR.audio.Player({"poster": "/assets/poster.jpg"}, {"audio": [
              {"id": "a303104", "title": "Fixture Audio", "duration": 3222,
               "sources": [{"file": "https://media.example/hls/audio.m3u8"}]}],
              "video": [{"id": "v303104", "duration": 3222,
               "sources": [{"file": "https://media.example/video/version.mp4"}]}]});
            </script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = LSMLREmbedIE(http(transfer)).extract(url)
        assertEquals("166557", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("a303104", info.entries[0].id)
    }

    // -------------------------------------------------------------- ltv embed

    @Test
    fun ltvYoutubeEmbedRedirectsToYouTube() = runTest {
        val url = "https://ltv.lsm.lv/embed?c=fixture_value"
        val page = """
            <html><body><script>window.ltvEmbedPayload = {"source": {"name": "youtube",
              "id": "wUnFArIPDSY", "poster": "https://media.example/poster.jpg"},
              "parentInfo": {"title": "Fixture LTV", "duration": 5269}};</script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = LSMLTVEmbedIE(http(transfer)).extract(url)
        assertEquals("https://www.youtube.com/watch?v=wUnFArIPDSY", info.redirectUrl)
        assertEquals("Fixture LTV", info.title)
        assertEquals(5269.0, info.duration)
    }

    // ------------------------------------------------------------- nuxt replay

    @Test
    fun replayNuxtDataYieldsHlsFormats() = runTest {
        val url = "https://replay.lsm.lv/lv/klausies/ieraksts/lr/183522/fixture-audio"
        val page = """
            <html><body><script>
            window.__NUXT__ = {"data": [{"__REPLAY__": {"playback": {"type": "playable_audio_lr",
              "service": {"hls_url": "https://media.example/hls/replay.m3u8"}},
              "mediaItem": {"title": "Fixture Replay", "lead": "Fixture lead",
                            "duration": 1823, "aired_at": "2023-11-02T00:00:00Z",
                            "largeThumbnail": "https://media.example/thumb.jpg"}}}],
              "state": Object.create(null,{"x":1})};
            </script></body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = LSMReplayIE(http(transfer)).extract(url)
        assertEquals("183522", info.id)
        assertEquals("Fixture Replay", info.title)
        assertEquals(1823.0, info.duration)
        assertEquals("20231102", info.uploadDate)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun lrEmbedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://lr1.lsm.lv/lv/embed/?id=166557&show=0&theme=white&size=16x9"
        val page = """
            <html><body><script>
            LR.audio.Player({"poster": "/assets/poster.jpg"}, {"audio": [
              {"id": "a303104", "title": "Fixture Audio", "duration": 3222,
               "sources": [{"file": "https://media.example/hls/audio.m3u8"}]}]});
            </script></body></html>
        """.trimIndent()
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("a303104"),
                "title" to Expect.Value("Fixture Audio"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(FixtureRoute(urlPattern = url, contentType = "text/html", body = page)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> LSMLREmbedIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
