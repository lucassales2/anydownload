package com.anydownload.core.extract.kuwo

import com.anydownload.core.extract.ExtractionError
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Kuwo subset. Ids, titles, lyrics, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class KuwoIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val songId = "635632"
    private val songUrl = "http://www.kuwo.cn/yinyue/$songId/"
    private val albumUrl = "http://www.kuwo.cn/album/502294/"
    private val chartUrl = "http://yinyue.kuwo.cn/billboard_fixture.htm"
    private val singerUrl = "http://www.kuwo.cn/mingxing/FixtureSinger/"
    private val categoryUrl = "http://yinyue.kuwo.cn/yy/cinfo_86375.htm"
    private val mvUrl = "http://www.kuwo.cn/mv/6480076/"

    private val antiUrl = "http://antiserver.kuwo.cn/anti.s"
    private val ipDenyRoute = FixtureRoute(urlPattern = "$antiUrl*", contentType = "text/plain", body = "IPDeny")
    private val notAvailableRoute = FixtureRoute(urlPattern = "$antiUrl*", contentType = "text/plain", body = "N/A")

    private fun songPage() = """
        <html><head><title>Fixture Song</title></head><body>
        <p id="lrcName">Fixture Song Title</p>
        <a href="http://www.kuwo.cn/artist/content?name=FixtureArtist">FixtureArtist</a>
        <div id="lrcContent">Line one<br>Line two</div>
        <a href="http://www.kuwo.cn/album/12345/">Album</a>
        </body></html>
    """.trimIndent()

    private val songRoutes = arrayOf(
        FixtureRoute(urlPattern = songUrl, contentType = "text/html", body = songPage()),
        FixtureRoute(
            urlPattern = "http://www.kuwo.cn/album/12345/",
            contentType = "text/html",
            body = "<span>发行时间：2008-01-22</span>",
        ),
        FixtureRoute(
            urlPattern = "$antiUrl?format=ape&br=&rid=MUSIC_$songId&type=convert_url&response=url",
            contentType = "text/plain",
            body = "http://media.example/song.ape",
        ),
        FixtureRoute(
            urlPattern = "$antiUrl?format=mp3&br=320kmp3&rid=MUSIC_$songId&type=convert_url&response=url",
            contentType = "text/plain",
            body = "https://media.example/song-320.mp3",
        ),
        notAvailableRoute,
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            KuwoIE(http(transfer())) to songUrl,
            KuwoAlbumIE(http(transfer())) to albumUrl,
            KuwoChartIE(http(transfer())) to chartUrl,
            KuwoSingerIE(http(transfer())) to singerUrl,
            KuwoCategoryIE(http(transfer())) to categoryUrl,
            KuwoMvIE(http(transfer())) to mvUrl,
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(KuwoIE(http(transfer())).suitable("https://example.com/yinyue/$songId"))
    }

    // ------------------------------------------------------------------ song

    @Test
    fun songPageYieldsTheAntiFormatsAndMetadata() = runTest {
        val info = KuwoIE(http(transfer(*songRoutes))).extract(songUrl)
        assertEquals(songId, info.id)
        assertEquals("Fixture Song Title", info.title)
        assertEquals("FixtureArtist", info.uploader)
        assertEquals("Line one Line two", info.description)
        assertEquals("20080122", info.uploadDate)
        assertEquals(2, info.formats.size)
        assertEquals("ape", info.formats[0].formatId)
        assertEquals(100, info.formats[0].preference)
        assertEquals("http://media.example/song.ape", info.formats[0].url)
        assertEquals("mp3-320", info.formats[1].formatId)
        assertEquals(320.0, info.formats[1].abr)
    }

    @Test
    fun removedSongFailsTyped() = runTest {
        val removedUrl = "http://www.kuwo.cn/yinyue/111/"
        val transfer = transfer(
            FixtureRoute(urlPattern = removedUrl, redirectTo = "http://www.kuwo.cn/"),
            FixtureRoute(urlPattern = "http://www.kuwo.cn/", contentType = "text/html", body = ""),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            KuwoIE(http(transfer)).extract(removedUrl)
        }
    }

    @Test
    fun ipDenyIsATypedGeoWallForSongs() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = songUrl, contentType = "text/html", body = songPage()),
            ipDenyRoute,
        )
        assertFailsWith<ExtractionError.GeoRestricted> {
            KuwoIE(http(transfer)).extract(songUrl)
        }
    }

    // ----------------------------------------------------------------- albums

    @Test
    fun albumPageListsTheSongs() = runTest {
        val page = """
            <html><body>
            <div class="comm"><h1 title="Fixture Album">Fixture Album</h1></div>
            <div id="intro">Fixture Album简介：Fixture intro</div>
            <p class="listen"><a href="http://www.kuwo.cn/yinyue/1/">One</a></p>
            <p class="listen"><a href="http://www.kuwo.cn/yinyue/2/">Two</a></p>
            </body></html>
        """.trimIndent()
        val info = KuwoAlbumIE(http(transfer(FixtureRoute(urlPattern = albumUrl, contentType = "text/html", body = page))))
            .extract(albumUrl)
        assertEquals("502294", info.id)
        assertEquals("Fixture Album", info.title)
        assertEquals("Fixture intro", info.description)
        assertEquals(2, info.entries.size)
        assertEquals("http://www.kuwo.cn/yinyue/2/", info.entries[1].url)
    }

    // ----------------------------------------------------------------- charts

    @Test
    fun chartPageListsTheSongs() = runTest {
        val page = """
            <html><body>
            <a href="http://www.kuwo.cn/yinyue/1">One</a>
            <a href="http://www.kuwo.cn/yinyue/2">Two</a>
            </body></html>
        """.trimIndent()
        val info = KuwoChartIE(http(transfer(FixtureRoute(urlPattern = chartUrl, contentType = "text/html", body = page))))
            .extract(chartUrl)
        assertEquals("fixture", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("http://www.kuwo.cn/yinyue/1", info.entries[0].url)
    }

    // ---------------------------------------------------------------- singers

    @Test
    fun singerPageWalksThePagedAjax() = runTest {
        val page = "<html><body><h1>Fixture Singer</h1><div data-artistid=\"42\" data-page=\"2\"></div></body></html>"
        val transfer = transfer(
            FixtureRoute(urlPattern = singerUrl, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "http://www.kuwo.cn/artist/contentMusicsAjax?artistId=42&pn=0&rn=15",
                contentType = "text/html",
                body = "<div class=\"name\"><a href=\"/yinyue/7\">Seven</a></div>",
            ),
            FixtureRoute(
                urlPattern = "http://www.kuwo.cn/artist/contentMusicsAjax?artistId=42&pn=1&rn=15",
                contentType = "text/html",
                body = "<div class=\"name\"><a href=\"/yinyue/8\">Eight</a></div>",
            ),
        )
        val info = KuwoSingerIE(http(transfer)).extract(singerUrl)
        assertEquals("FixtureSinger", info.id)
        assertEquals("Fixture Singer", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("http://www.kuwo.cn/yinyue/7", info.entries[0].url)
        assertEquals("http://www.kuwo.cn/yinyue/8", info.entries[1].url)
    }

    // -------------------------------------------------------------- categories

    @Test
    fun categoryPageParsesTheJsonmList() = runTest {
        val page = """
            <html><body>
            <h1 title="Fixture Category">Fixture Category</h1>
            <div id="intro">Fixture Category简介：Fixture category desc</div>
            <script>var jsonm = {"musiclist": [{"musicrid": "101"}, {"musicrid": 102}]};</script>
            </body></html>
        """.trimIndent()
        val info = KuwoCategoryIE(http(transfer(FixtureRoute(urlPattern = categoryUrl, contentType = "text/html", body = page))))
            .extract(categoryUrl)
        assertEquals("86375", info.id)
        assertEquals("Fixture Category", info.title)
        assertEquals("Fixture category desc", info.description)
        assertEquals(2, info.entries.size)
        assertEquals("http://www.kuwo.cn/yinyue/101/", info.entries[0].url)
        assertEquals("http://www.kuwo.cn/yinyue/102/", info.entries[1].url)
    }

    // --------------------------------------------------------------------- mv

    @Test
    fun mvPageToleratesIpDenyAndAppendsTheMvUrl() = runTest {
        val page = "<html><body><h1 title=\"Fixture MV\">Fixture MV<span title=\"Fixture MV Singer\"></span></h1></body></html>"
        val transfer = transfer(
            FixtureRoute(urlPattern = mvUrl, contentType = "text/html", body = page),
            ipDenyRoute,
            FixtureRoute(
                urlPattern = "http://www.kuwo.cn/yy/st/mvurl?rid=MUSIC_6480076",
                contentType = "text/plain",
                body = "http://media.example/mv.mp4",
            ),
        )
        val info = KuwoMvIE(http(transfer)).extract(mvUrl)
        assertEquals("6480076", info.id)
        assertEquals("Fixture MV", info.title)
        assertEquals("Fixture MV Singer", info.uploader)
        assertEquals(1, info.formats.size)
        assertEquals("mv", info.formats[0].formatId)
        assertEquals("mp4", info.formats[0].ext)
        assertEquals("http://media.example/mv.mp4", info.formats[0].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun songIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = songUrl,
            infoDict = mapOf(
                "id" to Expect.Value(songId),
                "title" to Expect.Value("Fixture Song Title"),
                "uploader" to Expect.Value("FixtureArtist"),
                "upload_date" to Expect.Value("20080122"),
                "formats" to Expect.Count(2),
            ),
            routes = songRoutes.toList(),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> KuwoIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun albumIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val page = """
            <html><body>
            <div class="comm"><h1 title="Fixture Album">Fixture Album</h1></div>
            <div id="intro">Fixture Album简介：Fixture intro</div>
            <p class="listen"><a href="http://www.kuwo.cn/yinyue/1/">One</a></p>
            <p class="listen"><a href="http://www.kuwo.cn/yinyue/2/">Two</a></p>
            </body></html>
        """.trimIndent()
        val case = ExtractorCase(
            url = albumUrl,
            infoDict = mapOf(
                "id" to Expect.Value("502294"),
                "title" to Expect.Value("Fixture Album"),
                "entries" to Expect.Count(2),
            ),
            routes = listOf(FixtureRoute(urlPattern = albumUrl, contentType = "text/html", body = page)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> KuwoAlbumIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
