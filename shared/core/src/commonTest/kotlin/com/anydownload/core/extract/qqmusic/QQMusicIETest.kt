package com.anydownload.core.extract.qqmusic

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
 * The QQ Music URL surface is a typed wall: the API signs requests with a
 * login-derived g_tk, so no fixture can pass.
 */
class QQMusicIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            QQMusicIE(http) to "https://y.qq.com/n/ryqq/songDetail/004Ti8rT003TaZ",
            QQMusicSingerIE(http) to "https://y.qq.com/n/ryqq/singer/001fNHEf1SFEFN",
            QQMusicAlbumIE(http) to "https://y.qq.com/n/ryqq/albumDetail/002fRO0N4FftzY",
            QQMusicToplistIE(http) to "https://y.qq.com/n/ryqq/toplist/26",
            QQMusicPlaylistIE(http) to "https://y.qq.com/n/ryqq/playlist/7910679308",
            QQMusicVideoIE(http) to "https://y.qq.com/n/ryqq/mv/010s2GkX3ANHVX",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(QQMusicToplistIE(http).suitable("https://y.qq.com/n/ryqq/songDetail/004Ti8rT003TaZ"))
    }

    @Test
    fun everyClassFailsTypedOnTheSessionWall() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            QQMusicIE(http) to "https://y.qq.com/n/ryqq/songDetail/004Ti8rT003TaZ",
            QQMusicSingerIE(http) to "https://y.qq.com/n/ryqq/singer/001fNHEf1SFEFN",
            QQMusicAlbumIE(http) to "https://y.qq.com/n/ryqq/albumDetail/002fRO0N4FftzY",
            QQMusicToplistIE(http) to "https://y.qq.com/n/ryqq/toplist/26",
            QQMusicPlaylistIE(http) to "https://y.qq.com/n/ryqq/playlist/7910679308",
            QQMusicVideoIE(http) to "https://y.qq.com/n/ryqq/mv/010s2GkX3ANHVX",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.LoginRequired> { extractor.extract(url) }
            assertTrue(error.message!!.contains("g_tk"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
