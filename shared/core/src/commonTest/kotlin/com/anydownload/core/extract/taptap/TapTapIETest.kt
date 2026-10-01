package com.anydownload.core.extract.taptap

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
 * Fixture cases for the TapTap subset. Ids and media paths are synthesized on
 * `media.example`; the `X-UA` value is matched with a wildcard because it
 * carries a per-call random UID. No cookie, token, or signed URL appears.
 */
class TapTapIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val momentUrl = "https://www.taptap.cn/moment/194618230982052443"
    private val appUrl = "https://www.taptap.cn/app/168332"
    private val appIntlUrl = "https://www.taptap.io/app/233287"
    private val postIntlUrl = "https://www.taptap.io/post/571785"

    private fun videoRoute(urlPattern: String, videoId: String, duration: Int, thumb: String) = FixtureRoute(
        urlPattern = urlPattern,
        contentType = "application/json",
        body = """
            {"data": {"list": [{
              "play_url": {"url_h265": "https://media.example/$videoId/master.m3u8",
                           "url": "https://media.example/$videoId/fallback.m3u8"},
              "info": {"duration": $duration},
              "thumbnail": {"original_url": "$thumb"}}]}}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val moment = TapTapMomentIE(http(transfer()))
        assertTrue(moment.suitable(momentUrl))
        assertFalse(moment.suitable(appUrl))
        assertFalse(moment.suitable("https://www.example.cn/moment/194618230982052443"))

        val app = TapTapAppIE(http(transfer()))
        assertTrue(app.suitable(appUrl))
        assertFalse(app.suitable(appIntlUrl))

        val appIntl = TapTapAppIntlIE(http(transfer()))
        assertTrue(appIntl.suitable(appIntlUrl))
        assertFalse(appIntl.suitable(postIntlUrl))

        val postIntl = TapTapPostIntlIE(http(transfer()))
        assertTrue(postIntl.suitable(postIntlUrl))
        assertFalse(postIntl.suitable(appIntlUrl))
    }

    // ---------------------------------------------------------------- moment

    @Test
    fun momentYieldsOneMediaItem() = runTest {
        val detailRoute = FixtureRoute(
            urlPattern = "https://www.taptap.cn/webapiv2/moment/v3/detail?id=194618230982052443&X-UA=*",
            contentType = "application/json",
            body = """
                {"data": {"moment": {
                  "created_time": 1633453402, "edited_time": 1633453402,
                  "author": {"user": {"name": "Fixture Uploader", "id": 532896}},
                  "topic": {"title": "Fixture Moment", "summary": "Fixture summary",
                    "videos": [{"video_id": 2202584}],
                    "pin_video": {"video_id": 2202584}}}}}
            """.trimIndent(),
        )
        val video = videoRoute(
            "https://www.taptap.cn/webapiv2/video-resource/v1/multi-get?video_ids=2202584&X-UA=*",
            "2202584",
            duration = 66,
            thumb = "https://media.example/moment.jpg",
        )
        val info = TapTapMomentIE(http(transfer(detailRoute, video))).extract(momentUrl)
        assertEquals("194618230982052443", info.id)
        assertEquals("Fixture Moment", info.title)
        assertEquals("Fixture summary", info.description)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals("20211005", info.uploadDate)
        assertEquals(1, info.media.size)
        assertEquals("2202584", info.media[0].mediaId)
        assertEquals("Fixture Moment", info.media[0].title)
        assertEquals(66.0, info.media[0].duration)
        assertEquals("https://media.example/moment.jpg", info.media[0].thumbnails.single().url)
        assertEquals("https://media.example/2202584/master.m3u8", info.media[0].formats.single().url)
        assertEquals("m3u8_native", info.media[0].formats.single().protocol)
    }

    @Test
    fun momentIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val detailRoute = FixtureRoute(
            urlPattern = "https://www.taptap.cn/webapiv2/moment/v3/detail?id=194618230982052443&X-UA=*",
            contentType = "application/json",
            body = """
                {"data": {"moment": {
                  "created_time": 1633453402,
                  "author": {"user": {"name": "Fixture Uploader", "id": 532896}},
                  "topic": {"title": "Fixture Moment", "summary": "Fixture summary",
                    "videos": [{"video_id": 2202584}]}}}}
            """.trimIndent(),
        )
        val video = videoRoute(
            "https://www.taptap.cn/webapiv2/video-resource/v1/multi-get?video_ids=2202584&X-UA=*",
            "2202584",
            duration = 66,
            thumb = "https://media.example/moment.jpg",
        )
        val case = ExtractorCase(
            url = momentUrl,
            infoDict = mapOf(
                "id" to Expect.Value("194618230982052443"),
                "title" to Expect.Value("Fixture Moment"),
                "upload_date" to Expect.Value("20211005"),
            ),
            routes = listOf(detailRoute, video),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TapTapMomentIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    // ------------------------------------------------------------------- app

    @Test
    fun appDeduplicatesTheVideoLists() = runTest {
        val detailRoute = FixtureRoute(
            urlPattern = "https://www.taptap.cn/webapiv2/app/v4/detail?id=168332&X-UA=*",
            contentType = "application/json",
            body = """
                {"data": {"title": "Fixture App",
                  "description": {"text": "<p>Fixture <b>description</b></p>"},
                  "app_videos": [{"video_id": 4058443}, {"video_id": 4058462}],
                  "videos": [{"video_id": 4058443}]}}
            """.trimIndent(),
        )
        val first = videoRoute(
            "https://www.taptap.cn/webapiv2/video-resource/v1/multi-get?video_ids=4058443&X-UA=*",
            "4058443",
            duration = 26,
            thumb = "https://media.example/app1.jpg",
        )
        val second = videoRoute(
            "https://www.taptap.cn/webapiv2/video-resource/v1/multi-get?video_ids=4058462&X-UA=*",
            "4058462",
            duration = 295,
            thumb = "https://media.example/app2.jpg",
        )
        val info = TapTapAppIE(http(transfer(detailRoute, first, second))).extract(appUrl)
        assertEquals("168332", info.id)
        assertEquals("Fixture App", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(2, info.media.size)
        assertEquals("4058443", info.media[0].mediaId)
        assertEquals("4058462", info.media[1].mediaId)
    }

    // ---------------------------------------------------------------- intl

    @Test
    fun appIntlUsesTheAppDataPath() = runTest {
        val detailRoute = FixtureRoute(
            urlPattern = "https://www.taptap.io/webapiv2/i/app/v5/detail?id=233287&X-UA=*",
            contentType = "application/json",
            body = """
                {"data": {"app": {"title": "Fixture Intl App",
                  "description": {"text": "Fixture intl description"},
                  "videos": [{"video_id": 2149708997}]}}}
            """.trimIndent(),
        )
        val video = videoRoute(
            "https://www.taptap.io/webapiv2/video-resource/v1/multi-get?video_ids=2149708997&X-UA=*",
            "2149708997",
            duration = 78,
            thumb = "https://media.example/intl.jpg",
        )
        val info = TapTapAppIntlIE(http(transfer(detailRoute, video))).extract(appIntlUrl)
        assertEquals("233287", info.id)
        assertEquals("Fixture Intl App", info.title)
        assertEquals("Fixture intl description", info.description)
        assertEquals(1, info.media.size)
        assertEquals("2149708997", info.media[0].mediaId)
    }

    @Test
    fun postIntlUsesTheIdStrQueryAndPostDataPath() = runTest {
        val detailRoute = FixtureRoute(
            urlPattern = "https://www.taptap.io/webapiv2/creation/post/v1/detail?id_str=571785&X-UA=*",
            contentType = "application/json",
            body = """
                {"data": {"post": {"title": "Fixture Post", "published_time": 1614664951,
                  "edited_time": 1614664951, "user": {"name": "Fixture Editor", "id": 80224473},
                  "list_fields": {"summary": "Fixture post summary"},
                  "videos": [{"video_id": 2149491903}],
                  "pin_video": {"video_id": 2149491903}}}}
            """.trimIndent(),
        )
        val video = videoRoute(
            "https://www.taptap.io/webapiv2/video-resource/v1/multi-get?video_ids=2149491903&X-UA=*",
            "2149491903",
            duration = 122,
            thumb = "https://media.example/post.jpg",
        )
        val info = TapTapPostIntlIE(http(transfer(detailRoute, video))).extract(postIntlUrl)
        assertEquals("571785", info.id)
        assertEquals("Fixture Post", info.title)
        assertEquals("Fixture post summary", info.description)
        assertEquals("Fixture Editor", info.uploader)
        assertEquals("20210302", info.uploadDate)
        assertEquals(1, info.media.size)
        assertEquals("2149491903", info.media[0].mediaId)
    }
}
