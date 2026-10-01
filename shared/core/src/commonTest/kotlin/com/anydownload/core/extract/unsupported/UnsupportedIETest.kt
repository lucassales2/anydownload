package com.anydownload.core.extract.unsupported

import com.anydownload.core.extract.ExtractionError
import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixture cases for the unsupported subset. The URLs are the upstream test
 * URLs; no request is made, so no fixture is needed and no media URL
 * appears.
 */
class UnsupportedIETest {

    private fun http(): ExtractorHttp = ExtractorHttp(FixtureHttpTransfer(emptyList()))

    // ------------------------------------------------------------ URL matching

    @Test
    fun drmUrlsMatchAndFailTyped() = runTest {
        val urls = listOf(
            "https://peacocktv.com/watch/playback/vod/GMO_00000000073159_01/f9d03003",
            "https://www.channel4.com/programmes/gurren-lagann/on-demand/69960-001",
            "https://www.channel5.com/show/uk-s-strongest-man-2021/season-2021/episode-1",
            "https://www.disneyplus.com",
            "https://open.spotify.com",
            "https://www.tvnz.co.nz/shows/ice-airport-alaska/episodes/s1-e1",
            "https://www.artstation.com/learning/courses/dqQ/introduction",
            "https://www.philo.com/player/player/vod/Vk9EOjYwODU0ODg5OTY0ODY0OTQ5NA",
            "https://www.mech-plus.com/player/24892/stream?assetType=episodes",
            "https://www.aha.video/player/movie/lucky-man",
            "https://mubi.com/films/the-night-doctor",
            "https://www.vootkids.com/movies/chhota-bheem-the-rise-of-kirmada/764459",
            "https://www.nowtv.it/watch/home/asset/and-just-like-that",
            "https://tv.apple.com/it/show/loot",
            "https://www.joyn.de/play/serien/clannad/1-1",
            "https://music.amazon.co.jp/albums/B088Y368TK",
            "https://www.amazon.co.jp/gp/video/detail/B09X5HBYRS/",
            "https://www.primevideo.com/region/eu/detail/0H3DDB4KBJFNDCKKLHNRLRLVKQ/",
            "https://resource.inkryptvideos.com/v2-a83ns52/iframe/index.html",
            "https://www.hulu.com/movie/anthem-6b25fac9",
            "https://watch.njpwworld.com/player/36447/series?assetType=series",
            "https://www.qub.ca/vrai/l-effet-bocuse-d-or/saison-1/fixture",
            "https://www.crunchyroll.com/watch/GY2P1Q98Y/to-the-future",
            "https://www.viki.com/videos/1175236v-fixture",
            "http://www.deezer.com/playlist/176747451",
            "https://www.b-ch.com/titles/8203/001",
            "https://www.ctv.ca/shows/masterchef-53506/the-audition-battles-s15e1",
            "https://www.noovo.ca/emissions/fixture-s10e1",
            "https://www.tsn.ca/video/relaxed-oilers-look",
            "https://www.paramountplus.com/shows/fixture/",
            "https://www.crackle.com/watch/fixture",
            "https://www.cwtv.com/shows/fixture/",
            "https://www.6play.fr/fixture",
            "https://www.rtlplay.be/fixture",
            "https://play.rtl.hr/fixture",
            "https://rtlmost.hu/fixture",
            "https://plus.rtl.de/fixture",
            "https://www.mediasetinfinity.es/fixture",
            "https://www.tv5mondeplus.com/fixture",
            "https://tv.rakuten.co.jp/fixture",
            "https://www.web.nhk/tv/an/72hours/pl/series",
            "https://fod.fujitv.co.jp/title/709f/709f130001/",
            "https://www.zee5.com/",
        )
        for (url in urls) {
            assertTrue(KnownDRMIE(http()).suitable(url), "KnownDRM must match: $url")
            assertFailsWith<ExtractionError.Unavailable> {
                KnownDRMIE(http()).extract(url)
            }
        }
        assertFalse(KnownDRMIE(http()).suitable("https://www.example.com/watch"))
    }

    @Test
    fun piracyUrlsMatchAndFailTyped() = runTest {
        val urls = listOf(
            "http://dood.to/e/5s1wmbdacezb",
            "https://thisav.com/en/terms",
            "https://gofile.io/d/",
            "https://www.einthusan.tv/movie/fixture",
            "https://sxyprn.com/post/fixture",
            "https://vidlo.us/fixture",
        )
        for (url in urls) {
            assertTrue(KnownPiracyIE(http()).suitable(url), "KnownPiracy must match: $url")
            assertFailsWith<ExtractionError.Unavailable> {
                KnownPiracyIE(http()).extract(url)
            }
        }
        assertFalse(KnownPiracyIE(http()).suitable("https://www.example.com/watch"))
    }

    @Test
    fun liabilityUrlsMatchAndFailTyped() = runTest {
        val urls = listOf(
            "https://motherless.com/",
            "https://suno.com/song/",
            "https://www.udio.com/songs/",
        )
        for (url in urls) {
            assertTrue(KnownLiabilityIE(http()).suitable(url), "KnownLiability must match: $url")
            assertFailsWith<ExtractionError.Unavailable> {
                KnownLiabilityIE(http()).extract(url)
            }
        }
        assertFalse(KnownLiabilityIE(http()).suitable("https://www.example.com/watch"))
    }
}
