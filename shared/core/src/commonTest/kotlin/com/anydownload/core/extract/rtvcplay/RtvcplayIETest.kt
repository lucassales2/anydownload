package com.anydownload.core.extract.rtvcplay

import com.anydownload.core.extract.ExtractorHttp
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
 * Fixture cases for the RTVC Play subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class RtvcplayIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val liveUrl = "https://www.rtvcplay.co/en-vivo/canal-institucional"
    private val vodUrl = "https://www.rtvcplay.co/peliculas-ficcion/senoritas"
    private val playlistUrl = "https://www.rtvcplay.co/competencias-basicas/profe-en-tu-casa"
    private val embedUrl = "https://www.rtvcplay.co/embed/72b0e699-248b-4929-a4a8-3782702fa7f9"
    private val kalturaUrl = "https://media.rtvc.gov.co/kalturartvc/indexSC.html"

    private fun hydrationPage(state: String) =
        "<html><body><script>window.__RTVCPLAY_STATE__ = $state;</script></body></html>"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val play = RTVCPlayIE(http(transfer()))
        assertTrue(play.suitable(liveUrl))
        assertTrue(play.suitable(vodUrl))
        assertTrue(play.suitable("https://www.rtvcplay.co/peliculas-documentales/llinas"))
        assertFalse(play.suitable(embedUrl))

        assertTrue(RTVCPlayEmbedIE(http(transfer())).suitable(embedUrl))
        assertTrue(RTVCKalturaIE(http(transfer())).suitable(kalturaUrl))
        assertFalse(RTVCKalturaIE(http(transfer())).suitable(liveUrl))
    }

    // ------------------------------------------------------------------ live

    @Test
    fun livePageYieldsTheHlsRow() = runTest {
        val state = """
            {"content": {"currentContent": {"title": "Canal Institucional",
              "description": "Fixture description",
              "channel": {"hls": "https://media.example/live.m3u8",
                "image": {"logo": {"path": "https://media.example/logo.png"}}}}}}
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = "$liveUrl*", contentType = "text/html", body = hydrationPage(state)))
        val info = RTVCPlayIE(http(transfer)).extract(liveUrl)
        assertEquals("canal-institucional", info.id)
        assertEquals("Canal Institucional", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(true, info.isLive)
        assertEquals("https://media.example/logo.png", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
    }

    // ------------------------------------------------------------ vod/asset

    @Test
    fun vodAssetIdFillsTheHlsTemplate() = runTest {
        val state = """
            {"content": {"currentContent": {"title": "Señoritas", "description": "Fixture",
              "base_url_hls": "https://media.example/hls/[node:field_asset_id]/master.m3u8",
              "video": {"assetid": "123"}}}}
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = "$vodUrl*", contentType = "text/html", body = hydrationPage(state)))
        val info = RTVCPlayIE(http(transfer)).extract(vodUrl)
        assertEquals("senoritas", info.id)
        assertEquals("Señoritas", info.title)
        assertEquals(false, info.isLive)
        assertEquals("https://media.example/hls/123/master.m3u8", info.formats.single().url)
    }

    @Test
    fun programPageYieldsTheSeasonEntries() = runTest {
        val state = """
            {"content": {"currentContent": {"title": "Profe en tu casa", "widgets": [
              {"type": "seasonList", "contents": [
                {"title": "Season 1", "season": 1, "contents": [
                  {"slug": "episode-1", "title": "Ep 1", "chapter_number": 1}
                ]}
              ]}
            ]}}}
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = "$playlistUrl*", contentType = "text/html", body = hydrationPage(state)))
        val info = RTVCPlayIE(http(transfer)).extract(playlistUrl)
        assertEquals("profe-en-tu-casa", info.id)
        assertEquals("Profe en tu casa", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("$playlistUrl/episode-1", info.entries[0].url)
    }

    // ----------------------------------------------------------------- embed

    @Test
    fun embedPlayerConfigYieldsTheRowsAndCmsMetadata() = runTest {
        val page = """
            <html><body><script>var config = {"sources": [
              {"url": "https://media.example/master.m3u8", "mimetype": "application/x-mpegURL"}
            ], "rtvcplay": {"assetid": "asset1"}};</script></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "$embedUrl*", contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://cms.rtvcplay.co/api/v1/video/asset-id/asset1",
                contentType = "application/json",
                body = """
                    {"title": "Tráiler: Señoritas", "description": "Fixture",
                     "image": [{"thumbnail": {"path": "https://media.example/t.jpg"}}]}
                """.trimIndent(),
            ),
        )
        val info = RTVCPlayEmbedIE(http(transfer)).extract(embedUrl)
        assertEquals("72b0e699-248b-4929-a4a8-3782702fa7f9", info.id)
        assertEquals("Tráiler: Señoritas", info.title)
        assertEquals("https://media.example/t.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
    }

    // ---------------------------------------------------------------- kaltura

    @Test
    fun kalturaPageMergesTheChannelHls() = runTest {
        val page = """
            <html><body><script>var config = {"sources": [
              {"url": "https://media.example/player.m3u8", "mimetype": "application/x-mpegURL"}
            ], "rtvcplay": {"channelId": "ch1"}};</script></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "$kalturaUrl*", contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://cms.rtvcplay.co/api/v1/taxonomy_term/streaming/ch1",
                contentType = "application/json",
                body = """
                    {"title": "Señal Colombia", "description": "Fixture",
                     "channel": {"hls": "https://media.example/channel.m3u8",
                       "image": {"logo": {"path": "https://media.example/logo.png"}}}}
                """.trimIndent(),
            ),
        )
        val info = RTVCKalturaIE(http(transfer)).extract(kalturaUrl)
        assertEquals("indexSC", info.id)
        assertEquals("Señal Colombia", info.title)
        assertEquals(true, info.isLive)
        assertEquals(2, info.formats.size)
        assertEquals("https://media.example/channel.m3u8", info.formats[1].url)
        assertEquals("https://media.example/logo.png", info.thumbnails.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun liveIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val state = """
            {"content": {"currentContent": {"title": "Canal Institucional",
              "description": "Fixture description",
              "channel": {"hls": "https://media.example/live.m3u8",
                "image": {"logo": {"path": "https://media.example/logo.png"}}}}}}
        """.trimIndent()
        val case = ExtractorCase(
            url = liveUrl,
            infoDict = mapOf(
                "id" to Expect.Value("canal-institucional"),
                "title" to Expect.Value("Canal Institucional"),
                "is_live" to Expect.Value(true),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(FixtureRoute(urlPattern = "$liveUrl*", contentType = "text/html", body = hydrationPage(state))),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RTVCPlayIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun embedIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val page = """
            <html><body><script>var config = {"sources": [
              {"url": "https://media.example/master.m3u8", "mimetype": "application/x-mpegURL"}
            ], "rtvcplay": {"assetid": "asset1"}};</script></body></html>
        """.trimIndent()
        val case = ExtractorCase(
            url = embedUrl,
            infoDict = mapOf(
                "id" to Expect.Value("72b0e699-248b-4929-a4a8-3782702fa7f9"),
                "title" to Expect.Value("Tráiler: Señoritas"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "$embedUrl*", contentType = "text/html", body = page),
                FixtureRoute(
                    urlPattern = "https://cms.rtvcplay.co/api/v1/video/asset-id/asset1",
                    contentType = "application/json",
                    body = """{"title": "Tráiler: Señoritas", "description": "Fixture", "image": []}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RTVCPlayEmbedIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
