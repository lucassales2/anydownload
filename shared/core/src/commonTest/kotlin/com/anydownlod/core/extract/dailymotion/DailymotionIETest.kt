package com.anydownlod.core.extract.dailymotion

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.CaseResult
import com.anydownlod.core.extract.harness.Expect
import com.anydownlod.core.extract.harness.ExtractorCase
import com.anydownlod.core.extract.harness.ExtractorTestRun
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import com.anydownlod.core.extract.harness.runCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Dailymotion public-metadata subset. Every id, host,
 * and address is synthesized (`*.example`); no OAuth token, client secret,
 * cookie, or signed media URL appears.
 */
class DailymotionIETest {

    private val videoUrl = "https://www.dailymotion.com/video/x5kesuj"
    private val metadataUrl =
        "https://www.dailymotion.com/player/metadata/video/x5kesuj?app=com.dailymotion.neon"

    private val metadata = """
        {
          "title": "Synthetic Dailymotion video",
          "duration": 187,
          "created_time": 1493651285,
          "explicit": false,
          "is_live": false,
          "qualities": {
            "720": [
              {"url": "https://media.example/H264-1280x720.mp4#cell=1", "type": "video/mp4"},
              {"url": "https://media.example/master.m3u8", "type": "application/x-mpegURL"}
            ],
            "240": [
              {"url": "https://media.example/H264-320x240-60.mp4", "type": "video/mp4"}
            ]
          },
          "subtitles": {"data": {"en": {"urls": ["https://media.example/en.vtt"]}}},
          "posters": {"720": "https://s1.example/poster-720.jpg", "480": "https://s1.example/poster-480.jpg"},
          "thumbnails": {"240": "https://s1.example/thumb-240.jpg"},
          "owner": {"id": "7", "screenname": "Fixture Owner"}
        }
    """.trimIndent()

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): DailymotionIE =
        DailymotionIE(ExtractorHttp(transfer))

    // ------------------------------------------------------------ URL matching

    @Test
    fun videoFormsMatch() {
        val ie = extractor(transfer())
        for (url in listOf(
            "https://dai.ly/x5kesuj",
            "http://www.dailymotion.com/video/x5kesuj_title_slug",
            "https://www.dailymotion.com/embed/video/x5kesuj",
            "https://www.dailymotion.com/swf/video/x5kesuj",
            "https://geo.dailymotion.com/player.html?video=x5kesuj",
            "https://www.lequipe.fr/video/x5kesuj",
        )) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://www.dailymotion.com/playlist/xv4bw"))
        assertFalse(ie.suitable("https://www.dailymotion.com/user/fixture"))
    }

    // -------------------------------------------------------------- extraction

    @Test
    fun metadataMapsFormatsAndThumbnails() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = metadataUrl, body = metadata))
        val info = extractor(transfer).extract(videoUrl)

        assertEquals("x5kesuj", info.id)
        assertEquals("Synthetic Dailymotion video", info.title)
        assertEquals(187.0, info.duration)
        assertEquals("20170501", info.uploadDate)
        assertEquals("Fixture Owner", info.uploader)
        assertEquals("7", info.channelId)
        assertEquals(0, info.ageLimit)
        assertEquals(false, info.isLive)

        assertEquals(3, info.formats.size)
        val http = info.formats[0]
        assertEquals("http-720", http.formatId)
        assertEquals("https://media.example/H264-1280x720.mp4", http.url)
        assertEquals(1280L, http.width)
        assertEquals(720L, http.height)

        val hls = info.formats[1]
        assertEquals("hls-720", hls.formatId)
        assertEquals("m3u8_native", hls.protocol)

        val small = info.formats[2]
        assertEquals("http-240", small.formatId)
        assertEquals(320L, small.width)
        assertEquals(240L, small.height)
        assertEquals(60.0, small.fps)

        val subtitle = info.subtitles.single()
        assertEquals("en", subtitle.language)
        assertEquals("https://media.example/en.vtt", subtitle.formats.single().url)

        assertEquals(3, info.thumbnails.size)
        assertTrue(info.thumbnails.any { it.url == "https://s1.example/poster-720.jpg" && it.height == 720L })
    }

    // ----------------------------------------------------------------- errors

    @Test
    fun geoBlockedFailsTyped() = runTest {
        val geo = """{"error":{"code":"DM007","title":"Not available in your country"}}"""
        val transfer = transfer(FixtureRoute(urlPattern = metadataUrl, body = geo))
        assertFailsWith<ExtractionError.GeoRestricted> {
            extractor(transfer).extract(videoUrl)
        }
    }

    @Test
    fun otherErrorsFailTyped() = runTest {
        val error = """{"error":{"code":"DM001","raw_message":"Unknown error"}}"""
        val transfer = transfer(FixtureRoute(urlPattern = metadataUrl, body = error))
        assertFailsWith<ExtractionError.Unavailable> {
            extractor(transfer).extract(videoUrl)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun dailymotionIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("x5kesuj"),
                "title" to Expect.Value("Synthetic Dailymotion video"),
                "uploader" to Expect.Value("Fixture Owner"),
                "upload_date" to Expect.Value("20170501"),
                "duration" to Expect.Value(187L),
                "formats" to Expect.Count(3),
                "formats.0.format_id" to Expect.Value("http-720"),
                "formats.1.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(FixtureRoute(urlPattern = metadataUrl, body = metadata)),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> DailymotionIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
