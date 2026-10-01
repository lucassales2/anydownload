package com.anydownload.core.extract.ceskatelevize

import com.anydownload.core.extract.ExtractionError
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Ceská televize subset. Ids, titles, and media paths
 * are synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class CeskaTelevizeIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val poradyUrl = "http://www.ceskatelevize.cz/porady/10520528904-queer/215562210900007-bogotart/"
    private val ziveUrl = "http://www.ceskatelevize.cz/zive/ct1/"
    private val ivysilaniUrl =
        "http://www.ceskatelevize.cz/ivysilani/10441294653-hyde-park-civilizace/215411058090502/" +
            "bonus/20641-bonus-01-en"

    private val ajaxRoute = FixtureRoute(
        urlPattern = "https://www.ceskatelevize.cz/ivysilani/ajax/get-client-playlist/",
        method = "POST",
        contentType = "application/json",
        body = """{"url": "https://media.example/playlist.json"}""",
    )
    private val hashRoute = FixtureRoute(
        urlPattern = "https://www.ceskatelevize.cz/v-api/iframe-hash/",
        contentType = "text/plain",
        body = "fixture_hash",
    )
    private val playerRoute = FixtureRoute(
        urlPattern = "https://www.ceskatelevize.cz/ivysilani/embed/iFramePlayer.php*",
        contentType = "text/html",
        body = """
            <html><body><script>getPlaylistUrl([{"type":"episode","id":"fixture_episode"}], "x");</script>
            </body></html>
        """.trimIndent(),
    )

    private fun singlePlaylist(id: String, type: String = "VOD") = """
        {"playlist": [{"type": "$type", "id": "$id", "title": "Fixture Item", "duration": 1558.3,
          "previewImageUrl": "https://media.example/thumb.jpg",
          "streamUrls": {
            "1": "https://media.example/master.m3u8?playerType=flash",
            "2": "https://media.example/manifest.mpd"
          }}]}
    """.trimIndent()

    private fun page(idec: String, live: Boolean = false): String {
        val data = if (live) {
            """{"liveBroadcast": {"current": {"idec": "$idec"}}}"""
        } else {
            """{"show": {"mediaMeta": {"idec": "$idec"}}}"""
        }
        return """
            <html><head>
            <meta property="og:title" content="${if (live) "ČT1 - živé vysílání online" else "Bogotart - Queer"}">
            <meta property="og:description" content="Fixture description">
            <meta property="og:site_name" content="Česká televize">
            </head><body>
            <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"data":$data}}}</script>
            </body></html>
        """.trimIndent()
    }

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val extractor = CeskaTelevizeIE(http(transfer()))
        val cases = listOf(
            poradyUrl,
            ziveUrl,
            ivysilaniUrl,
            "http://www.ceskatelevize.cz/porady/10614999031-neviditelni/21251212048/",
        )
        for (url in cases) {
            assertTrue(extractor.suitable(url), "CeskaTelevize must match: $url")
        }
        assertFalse(extractor.suitable("https://www.example.com/porady/fixture/"))
    }

    // ------------------------------------------------------------------ porady

    @Test
    fun poradyPageYieldsTheSingleItem() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$poradyUrl*", contentType = "text/html", body = page("fixture_idec")),
            hashRoute,
            playerRoute,
            ajaxRoute,
            FixtureRoute(
                urlPattern = "https://media.example/playlist.json",
                contentType = "application/json",
                body = singlePlaylist("61924494877068022"),
            ),
        )
        val info = CeskaTelevizeIE(http(transfer)).extract(poradyUrl)
        assertEquals("61924494877068022", info.id)
        assertEquals("Bogotart - Queer", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(1558.3, info.duration)
        assertEquals(false, info.isLive)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("mpd", info.formats[1].protocol)
    }

    @Test
    fun livePageMarksTheItemLive() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "$ziveUrl*", contentType = "text/html", body = page("102", live = true)),
            hashRoute,
            playerRoute,
            ajaxRoute,
            FixtureRoute(
                urlPattern = "https://media.example/playlist.json",
                contentType = "application/json",
                body = singlePlaylist("102", type = "LIVE"),
            ),
        )
        val info = CeskaTelevizeIE(http(transfer)).extract(ziveUrl)
        assertEquals("102", info.id)
        assertEquals("ČT1 - živé vysílání online", info.title)
        assertEquals(true, info.isLive)
    }

    @Test
    fun ivysilaniPageSkipsTheNextJsStep() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$ivysilaniUrl*",
                contentType = "text/html",
                body = """
                    <html><head>
                    <meta property="og:title" content="Bonus 01 - En - Hyde Park Civilizace">
                    <meta property="og:description" content="English Subtitles">
                    </head><body>
                    <script>getPlaylistUrl([{"type":"bonus","id":"20641"}], "x");</script>
                    </body></html>
                """.trimIndent(),
            ),
            ajaxRoute,
            FixtureRoute(
                urlPattern = "https://media.example/playlist.json",
                contentType = "application/json",
                body = singlePlaylist("61924494877028507"),
            ),
        )
        val info = CeskaTelevizeIE(http(transfer)).extract(ivysilaniUrl)
        assertEquals("61924494877028507", info.id)
        assertEquals("Bonus 01 - En - Hyde Park Civilizace", info.title)
        assertEquals(2, info.formats.size)
    }

    @Test
    fun multipleItemsBecomeSelectableMedia() = runTest {
        val playlist = """
            {"playlist": [
              {"type": "VOD", "id": "111", "title": "Varování 18+", "duration": 11.9,
               "streamUrls": {"1": "https://media.example/a.m3u8?playerType=flash"}},
              {"type": "VOD", "id": "222", "title": "Queer", "duration": 1558.3,
               "streamUrls": {"1": "https://media.example/b.m3u8?playerType=flash"}}
            ]}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "$poradyUrl*", contentType = "text/html", body = page("fixture_idec")),
            hashRoute,
            playerRoute,
            ajaxRoute,
            FixtureRoute(urlPattern = "https://media.example/playlist.json", contentType = "application/json", body = playlist),
        )
        val info = CeskaTelevizeIE(http(transfer)).extract(poradyUrl)
        assertEquals(2, info.media.size)
        assertEquals("111", info.media[0].mediaId)
        assertEquals("Bogotart - Queer (Varování 18+)", info.media[0].title)
        assertEquals("222", info.media[1].mediaId)
    }

    @Test
    fun geoBlockedPlayerFailsTyped() = runTest {
        val blocked = FixtureRoute(
            urlPattern = "https://www.ceskatelevize.cz/ivysilani/embed/iFramePlayer.php*",
            contentType = "text/html",
            body = "<html><body>This content is not available at your territory due to limited copyright.</p></body></html>",
        )
        val transfer = transfer(
            FixtureRoute(urlPattern = "$poradyUrl*", contentType = "text/html", body = page("fixture_idec")),
            hashRoute,
            blocked,
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            CeskaTelevizeIE(http(transfer)).extract(poradyUrl)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun poradyIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = poradyUrl,
            infoDict = mapOf(
                "id" to Expect.Value("61924494877068022"),
                "title" to Expect.Value("Bogotart - Queer"),
                "duration" to Expect.Value(1558.3),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "$poradyUrl*", contentType = "text/html", body = page("fixture_idec")),
                hashRoute,
                playerRoute,
                ajaxRoute,
                FixtureRoute(
                    urlPattern = "https://media.example/playlist.json",
                    contentType = "application/json",
                    body = singlePlaylist("61924494877068022"),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> CeskaTelevizeIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun liveIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = ziveUrl,
            infoDict = mapOf(
                "id" to Expect.Value("102"),
                "is_live" to Expect.Value(true),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = "$ziveUrl*", contentType = "text/html", body = page("102", live = true)),
                hashRoute,
                playerRoute,
                ajaxRoute,
                FixtureRoute(
                    urlPattern = "https://media.example/playlist.json",
                    contentType = "application/json",
                    body = singlePlaylist("102", type = "LIVE"),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> CeskaTelevizeIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
