package com.anydownload.core.extract.ximalaya

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
 * Fixture cases for the Ximalaya subset. Ids and media paths are synthesized
 * on `media.example`; the track ids are fake values. No cookie, token, or
 * signed URL appears.
 */
class XimalayaIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val soundUrl = "http://www.ximalaya.com/sound/47740352/"
    private val albumUrl = "http://www.ximalaya.com/61425525/album/5534601/"

    private val soundRoute = FixtureRoute(
        urlPattern = "http://m.ximalaya.com/tracks/47740352.json",
        contentType = "application/json",
        body = """
            {"title": "Fixture Track", "nickname": "Fixture Uploader", "uid": 61425525,
             "intro": "Line1\r\n\r\n\r\n Line2\r\nLine3", "duration": 93, "play_count": 10,
             "favorites_count": 2, "category_name": "其他",
             "cover_url": "https://media.example/cover.jpg",
             "cover_url_142": "https://media.example/cover142.jpg",
             "play_path_32": "https://media.example/32.m4a",
             "play_path_64": "https://media.example/64.m4a"}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val sound = XimalayaIE(http(transfer()))
        assertTrue(sound.suitable(soundUrl))
        assertTrue(sound.suitable("http://m.ximalaya.com/61425525/sound/47740352/"))
        assertTrue(sound.suitable("https://www.ximalaya.com/sound/562111701"))
        assertFalse(sound.suitable(albumUrl))

        val album = XimalayaAlbumIE(http(transfer()))
        assertTrue(album.suitable(albumUrl))
        assertTrue(album.suitable("https://www.ximalaya.com/album/6912905"))
        assertFalse(album.suitable(soundUrl))
        assertFalse(album.suitable("https://www.example.com/sound/47740352"))
    }

    // --------------------------------------------------------------- sound

    @Test
    fun soundYieldsThePublicPlayRows() = runTest {
        val info = XimalayaIE(http(transfer(soundRoute))).extract(soundUrl)
        assertEquals("47740352", info.id)
        assertEquals("Fixture Track", info.title)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals("Line1\nLine2\nLine3", info.description)
        assertEquals(93.0, info.duration)
        assertEquals(10L, info.viewCount)
        assertEquals(2, info.thumbnails.size)
        assertEquals("cover_url", info.thumbnails[0].id)
        assertEquals(180L, info.thumbnails[1].width)
        assertEquals(180L, info.thumbnails[1].height)
        assertEquals(2, info.formats.size)
        assertEquals("24k", info.formats[0].formatId)
        assertEquals(24.0, info.formats[0].abr)
        assertEquals("none", info.formats[0].vcodec)
        assertEquals("64k", info.formats[1].formatId)
        assertEquals("https://media.example/64.m4a", info.formats[1].url)
    }

    @Test
    fun soundIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = soundUrl,
            infoDict = mapOf(
                "id" to Expect.Value("47740352"),
                "title" to Expect.Value("Fixture Track"),
                "duration" to Expect.Value(93.0),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(soundRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> XimalayaIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun vipOnlyTrackFailsTyped() = runTest {
        val vipRoute = FixtureRoute(
            urlPattern = "http://m.ximalaya.com/tracks/47740352.json",
            contentType = "application/json",
            body = """{"title": "Fixture VIP", "is_paid": true}""",
        )
        val error = assertFailsWith<ExtractionError.NoFormats> {
            XimalayaIE(http(transfer(vipRoute))).extract(soundUrl)
        }
        assertTrue(error.message!!.contains("VIP"), error.message)
    }

    @Test
    fun vipTrackWithPublicRowsKeepsThem() = runTest {
        val vipRoute = FixtureRoute(
            urlPattern = "http://m.ximalaya.com/tracks/47740352.json",
            contentType = "application/json",
            body = """
                {"title": "Fixture VIP", "is_paid": true,
                 "play_path_32": "https://media.example/32.m4a"}
            """.trimIndent(),
        )
        val info = XimalayaIE(http(transfer(vipRoute))).extract(soundUrl)
        assertEquals(1, info.formats.size)
        assertEquals("24k", info.formats[0].formatId)
    }

    // --------------------------------------------------------------- album

    @Test
    fun albumPaginatesTheTrackEntries() = runTest {
        val firstPage = FixtureRoute(
            urlPattern = "https://www.ximalaya.com/revision/album/v1/getTracksList?albumId=5534601&pageNum=1",
            contentType = "application/json",
            body = """
                {"data": {"trackTotalCount": 3, "pageSize": 2, "tracks": [
                  {"trackId": 1, "title": "One", "url": "/sound/1", "albumTitle": "Fixture Album"},
                  {"trackId": 2, "title": "Two", "url": "/sound/2"}]}}
            """.trimIndent(),
        )
        val secondPage = FixtureRoute(
            urlPattern = "https://www.ximalaya.com/revision/album/v1/getTracksList?albumId=5534601&pageNum=2",
            contentType = "application/json",
            body = """
                {"data": {"trackTotalCount": 3, "pageSize": 2, "tracks": [
                  {"trackId": 3, "title": "Three", "url": "//www.ximalaya.com/sound/3"}]}}
            """.trimIndent(),
        )
        val info = XimalayaAlbumIE(http(transfer(firstPage, secondPage))).extract(albumUrl)
        assertEquals("5534601", info.id)
        assertEquals("Fixture Album", info.title)
        assertEquals(3, info.entries.size)
        assertEquals("https://www.ximalaya.com/sound/1", info.entries[0].url)
        assertEquals("One", info.entries[0].title)
        assertEquals("https://www.ximalaya.com/sound/3", info.entries[2].url)
    }
}
