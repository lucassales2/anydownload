package com.anydownload.core.extract.neteasemusic

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the NetEase Music URL surface. Every URL form matches and
 * fails typed as the eapi wall; no request is made and no token or media URL
 * appears.
 */
class NetEaseMusicIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<String, String>>(
            NetEaseMusicIE.IE_KEY to "https://music.163.com/#/song?id=550136151",
            NetEaseMusicIE.IE_KEY to "https://y.music.163.com/m/song?id=550136151",
            NetEaseMusicAlbumIE.IE_KEY to "https://music.163.com/album?id=35023209",
            NetEaseMusicSingerIE.IE_KEY to "https://music.163.com/artist?id=6452",
            NetEaseMusicListIE.IE_KEY to "https://music.163.com/playlist?id=2654646265",
            NetEaseMusicListIE.IE_KEY to "https://music.163.com/discover/toplist?id=19723756",
            NetEaseMusicMvIE.IE_KEY to "https://music.163.com/mv?id=5432399",
            NetEaseMusicProgramIE.IE_KEY to "https://music.163.com/dj?id=1367665101",
            NetEaseMusicProgramIE.IE_KEY to "https://music.163.com/program?id=1367665101",
            NetEaseMusicDjRadioIE.IE_KEY to "https://music.163.com/djradio?id=42631",
        )
        val extractors = mapOf(
            NetEaseMusicIE.IE_KEY to NetEaseMusicIE(http()),
            NetEaseMusicAlbumIE.IE_KEY to NetEaseMusicAlbumIE(http()),
            NetEaseMusicSingerIE.IE_KEY to NetEaseMusicSingerIE(http()),
            NetEaseMusicListIE.IE_KEY to NetEaseMusicListIE(http()),
            NetEaseMusicMvIE.IE_KEY to NetEaseMusicMvIE(http()),
            NetEaseMusicProgramIE.IE_KEY to NetEaseMusicProgramIE(http()),
            NetEaseMusicDjRadioIE.IE_KEY to NetEaseMusicDjRadioIE(http()),
        )
        for ((key, url) in cases) {
            assertTrue(extractors.getValue(key).suitable(url), "$key must match: $url")
        }
        assertFalse(NetEaseMusicAlbumIE(http()).suitable("https://music.163.com/#/song?id=1"))
        assertFalse(NetEaseMusicDjRadioIE(http()).suitable("https://music.163.com/dj?id=1"))
        assertFalse(NetEaseMusicIE(http()).suitable("https://example.com/song?id=1"))
    }

    @Test
    fun everyUrlFormFailsTypedAsTheEapiWall() = runTest {
        val cases = listOf<Pair<NetEaseMusicBaseIE, String>>(
            NetEaseMusicIE(http()) to "https://music.163.com/#/song?id=550136151",
            NetEaseMusicAlbumIE(http()) to "https://music.163.com/album?id=35023209",
            NetEaseMusicSingerIE(http()) to "https://music.163.com/artist?id=6452",
            NetEaseMusicListIE(http()) to "https://music.163.com/playlist?id=2654646265",
            NetEaseMusicMvIE(http()) to "https://music.163.com/mv?id=5432399",
            NetEaseMusicProgramIE(http()) to "https://music.163.com/dj?id=1367665101",
            NetEaseMusicDjRadioIE(http()) to "https://music.163.com/djradio?id=42631",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.Unavailable>("${extractor.ieKey}: $url") {
                extractor.extract(url)
            }
            assertTrue(
                error.message!!.contains("eapi request cipher"),
                "${extractor.ieKey}: the reason must name the eapi cipher",
            )
        }
    }

    @Test
    fun theReasonIsOneSentence() {
        assertTrue(NetEaseMusicBaseIE.EAPI_WALL_MESSAGE.startsWith("The NetEase player API"))
        assertTrue(NetEaseMusicBaseIE.EAPI_WALL_MESSAGE.contains("not translated."))
    }
}
