package com.anydownload.core.extract.yandexmusic

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
 * The Yandex Music URL surface is a typed wall: the download URL is signed
 * with a fixed secret key, so no fixture can pass.
 */
class YandexMusicIETest {

    private val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            YandexMusicTrackIE(http) to "http://music.yandex.ru/album/540508/track/4878838",
            YandexMusicAlbumIE(http) to "https://music.yandex.ru/album/540508",
            YandexMusicPlaylistIE(http) to "https://music.yandex.ru/users/fixture/playlists/1234",
            YandexMusicArtistTracksIE(http) to "https://music.yandex.ru/artist/1234/tracks",
            YandexMusicArtistAlbumsIE(http) to "https://music.yandex.ru/artist/1234/albums",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(YandexMusicTrackIE(http).suitable("https://music.yandex.ru/album/540508"))
    }

    @Test
    fun everyClassFailsTypedOnTheSigningWall() = runTest {
        val cases = listOf<Pair<InfoExtractor, String>>(
            YandexMusicTrackIE(http) to "http://music.yandex.ru/album/540508/track/4878838",
            YandexMusicAlbumIE(http) to "https://music.yandex.ru/album/540508",
            YandexMusicPlaylistIE(http) to "https://music.yandex.ru/users/fixture/playlists/1234",
            YandexMusicArtistTracksIE(http) to "https://music.yandex.ru/artist/1234/tracks",
            YandexMusicArtistAlbumsIE(http) to "https://music.yandex.ru/artist/1234/albums",
        )
        for ((extractor, url) in cases) {
            val error = assertFailsWith<ExtractionError.Unavailable> { extractor.extract(url) }
            assertTrue(error.message!!.contains("secret key"), "${extractor.ieKey}: ${error.message}")
        }
    }
}
