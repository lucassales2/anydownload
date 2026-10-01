package com.anydownload.core.extract.japandiet

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
 * Fixture cases for the Japanese Diet subset. The pages are UTF-8 fixtures
 * (the port's HTTP layer decodes UTF-8; upstream reads EUC-JP). Media paths
 * are synthesized on `media.example`. No cookie, token, or signed URL
 * appears.
 */
class JapanDietIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val indexUrl = "https://www.shugiintv.go.jp/jp/index.php"
    private val roomUrl = "https://www.shugiintv.go.jp/jp/index.php?room_id=room01"
    private val vodUrl = "https://www.shugiintv.go.jp/jp/index.php?ex=VL&media_type=&deli_id=53846"
    private val sangiinUrl = "https://www.webtv.sangiin.go.jp/webtv/detail.php?sid=7052"

    private val indexPage = FixtureRoute(
        urlPattern = "https://www.shugiintv.go.jp/jp/index.php",
        contentType = "text/html",
        body = """
            <html><body>
            <a href="/jp/index.php?room_id=room01" class="play_live"><div class="s12_14">内閣委員会</td>
            <a href="/jp/index.php?room_id=room11" class="play_live"><div class="s12_14">外務委員会</td>
            </body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val live = ShugiinItvLiveIE(http(transfer()))
        assertTrue(live.suitable(indexUrl))
        assertTrue(live.suitable("https://www.shugiintv.go.jp/jp"))
        assertFalse(live.suitable(roomUrl), "the index class must yield room URLs")
        assertFalse(live.suitable(vodUrl), "the index class must yield VOD URLs")

        val room = ShugiinItvLiveRoomIE(http(transfer()))
        assertTrue(room.suitable(roomUrl))
        assertFalse(room.suitable(indexUrl))

        val vod = ShugiinItvVodIE(http(transfer()))
        assertTrue(vod.suitable(vodUrl))
        assertTrue(vod.suitable("https://www.shugiintv.go.jp/en/index.php?ex=VL&media_type=&deli_id=53846"))
        assertFalse(vod.suitable(indexUrl))

        val sangiin = SangiinIE(http(transfer()))
        assertTrue(sangiin.suitable(sangiinUrl))
        assertFalse(sangiin.suitable(indexUrl))

        val instruction = SangiinInstructionIE(http(transfer()))
        assertTrue(instruction.suitable("https://www.webtv.sangiin.go.jp/webtv/index.php"))
        assertFalse(instruction.suitable(sangiinUrl))
        assertFalse(instruction.suitable("https://www.example.com/webtv/index.php"))
    }

    // ----------------------------------------------------------- live rooms

    @Test
    fun indexYieldsTheRoomEntries() = runTest {
        val info = ShugiinItvLiveIE(http(transfer(indexPage))).extract(indexUrl)
        assertEquals("All proceedings for today", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("room01", info.entries[0].id)
        assertEquals("内閣委員会", info.entries[0].title)
        assertEquals(roomUrl, info.entries[0].url)
        assertEquals("room11", info.entries[1].id)
    }

    @Test
    fun liveRoomYieldsTheHlsRowAndTitle() = runTest {
        val info = ShugiinItvLiveRoomIE(http(transfer(indexPage))).extract(roomUrl)
        assertEquals("room01", info.id)
        assertEquals("内閣委員会", info.title)
        assertEquals(true, info.isLive)
        assertEquals(1, info.formats.size)
        assertEquals(
            "https://hlslive.shugiintv.go.jp/room01/amlst:room01/playlist.m3u8",
            info.formats[0].url,
        )
        assertEquals("m3u8_native", info.formats[0].protocol)
    }

    @Test
    fun liveRoomIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = roomUrl,
            infoDict = mapOf(
                "id" to Expect.Value("room01"),
                "title" to Expect.Value("内閣委員会"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(indexPage),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ShugiinItvLiveRoomIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    // ------------------------------------------------------------------ VOD

    @Test
    fun vodYieldsTheHlsRowChaptersAndDate() = runTest {
        val vodPage = FixtureRoute(
            urlPattern = "https://www.shugiintv.go.jp/jp/index.php?ex=VL*",
            contentType = "text/html",
            body = """
                <html><body>
                <input id="vtag_src_base_vod" value="http://media.example/master.m3u8">
                <td align="left">Fixture VOD(30分)
                開会日</td><td>x</td><TD>2022年3月23日</TD>
                <a HREF="https://www.shugiintv.go.jp/jp/index.php?ex=VL&time=0" class="play_vod">Opening</a>
                <a HREF="https://www.shugiintv.go.jp/jp/index.php?ex=VL&time=120" class="play_vod">Main part</a>
                <TR class="s14_24"><TD>x</TD><TD>2分</TD></TR>
                </body></html>
            """.trimIndent(),
        )
        val info = ShugiinItvVodIE(http(transfer(vodPage))).extract(vodUrl)
        assertEquals("53846", info.id)
        assertEquals("Fixture VOD", info.title)
        assertEquals("20220323", info.uploadDate)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/master.m3u8", info.formats[0].url)
        assertEquals(2, info.chapters.size)
        assertEquals(0.0, info.chapters[0].startTime)
        assertEquals("Opening", info.chapters[0].title)
        assertEquals(120.0, info.chapters[1].startTime)
        assertEquals(240.0, info.chapters[1].endTime)
    }

    // -------------------------------------------------------------- Sangiin

    @Test
    fun sangiinYieldsTheHlsRowAndMetadata() = runTest {
        val page = FixtureRoute(
            urlPattern = "https://www.webtv.sangiin.go.jp/webtv/detail.php?sid=7052",
            contentType = "text/html",
            body = """
                <html><body>
                <dt>開会日</dt><dd>2022年10月7日</dd>
                <dt>会議名</dt><dd>本会議</dd>
                会議の経過</h3><span>Fixture description</span>
                <script>var videopath = "https://media.example/sangiin.m3u8";</script>
                </body></html>
            """.trimIndent(),
        )
        val info = SangiinIE(http(transfer(page))).extract(sangiinUrl)
        assertEquals("7052", info.id)
        assertEquals("2022年10月7日 本会議", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("20221007", info.uploadDate)
        assertEquals(false, info.isLive)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/sangiin.m3u8", info.formats[0].url)
    }

    @Test
    fun instructionPageFailsTyped() = runTest {
        val error = assertFailsWith<ExtractionError.Unavailable> {
            SangiinInstructionIE(http(transfer())).extract(
                "https://www.webtv.sangiin.go.jp/webtv/index.php",
            )
        }
        assertTrue(error.message!!.contains("Copy the link"), error.message)
    }
}
