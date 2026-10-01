package com.anydownload.core.extract.nhk

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the NHK subset. Every id, host, and media address is
 * synthesized (`*.example`); no cookie, token, or signed URL appears.
 */
class NhkIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            NhkVodIE(http(transfer())) to "https://www3.nhk.or.jp/nhkworld/en/shows/2049165/",
            NhkVodIE(http(transfer())) to
                "https://www3.nhk.or.jp/nhkworld/en/shows/audio/fixture-20200101-ab12",
            NhkVodProgramIE(http(transfer())) to "https://www3.nhk.or.jp/nhkworld/en/shows/sumo/",
            NhkVodProgramIE(http(transfer())) to
                "https://www3.nhk.or.jp/nhkworld/en/shows/audio/programs/livinginjapan/",
            NhkForSchoolBangumiIE(http(transfer())) to
                "https://www2.nhk.or.jp/school/movie/bangumi.cgi?das_id=D0005150191_00000",
            NhkForSchoolSubjectIE(http(transfer())) to "https://www.nhk.or.jp/school/sougou/",
            NhkForSchoolProgramListIE(http(transfer())) to "https://www.nhk.or.jp/school/sougou/q/",
            NhkRadiruIE(http(transfer())) to
                "https://www.nhk.or.jp/radio/player/ondemand.html?p=LG96ZW5KZ4_01_4251382",
            NhkRadiruIE(http(transfer())) to
                "https://www.nhk.or.jp/radio/ondemand/detail.html?p=Z9L1V2M24L_01",
            NhkRadioNewsPageIE(http(transfer())) to "https://www.nhk.or.jp/radionews/",
            NhkRadiruLiveIE(http(transfer())) to "https://www.nhk.or.jp/radio/player/?ch=r1",
            NhkRadiruLiveIE(http(transfer())) to "https://www.nhk.or.jp/radio/player/?ch=fm",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(NhkVodProgramIE(http(transfer())).suitable("https://www3.nhk.or.jp/nhkworld/en/shows/2049165/"))
        assertFalse(NhkRadiruLiveIE(http(transfer())).suitable("https://www.nhk.or.jp/radio/player/?ch=r3"))
    }

    // ----------------------------------------------------------------- NhkVod

    @Test
    fun vodVideoMapsTheApiEpisode() = runTest {
        val url = "https://www3.nhk.or.jp/nhkworld/en/shows/2049165/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.nhkworld.jp/showsapi/v1/en/video_episodes/2049165",
                contentType = "application/json",
                body = """
                    {"id": "2049165", "lang": "en", "title": "Fixture Episode",
                     "video_program": {"title": "Fixture Program"},
                     "description": "Fixture description",
                     "first_broadcasted_at": "2020-01-01T00:00:00Z",
                     "images": [{"url": "/thumb.jpg", "width": 1280, "height": 720}],
                     "video": {"url": "https://media.example/hls/master.m3u8", "duration": 120}}
                """.trimIndent(),
            ),
        )
        val info = NhkVodIE(http(transfer)).extract(url)

        assertEquals("2049165-en", info.id)
        assertEquals("Fixture Program - Fixture Episode", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(120.0, info.duration)
        assertEquals("20200101", info.uploadDate)
        assertEquals("https://www3.nhk.or.jp/thumb.jpg", info.thumbnails.single().url)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun vodAudioUsesTheVodStreamPath() = runTest {
        val url = "https://www3.nhk.or.jp/nhkworld/en/shows/audio/fixture-20200101-ab12"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.nhkworld.jp/showsapi/v1/en/audio_episodes/fixture-20200101-ab12",
                contentType = "application/json",
                body = """
                    {"id": "fixture-20200101-ab12", "lang": "en", "title": "Fixture Audio",
                     "audio": {"url": "https://media.example/audio/fixture.m4a"}}
                """.trimIndent(),
            ),
        )
        val info = NhkVodIE(http(transfer)).extract(url)
        assertEquals("m4a", info.formats.single().ext)
        assertEquals("https://media.example/audio/fixture/index.m3u8", info.formats.single().url)
        assertEquals("en", info.formats.single().language)
    }

    @Test
    fun vodProgramListsEpisodesAndScrapesTheTitle() = runTest {
        val url = "https://www3.nhk.or.jp/nhkworld/en/shows/sumo/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.nhkworld.jp/showsapi/v1/en/video_programs/sumo/video_episodes",
                contentType = "application/json",
                body = """
                    {"items": [{"url": "/nhkworld/en/shows/2049165/", "id": "2049165", "lang": "en",
                                "title": "Fixture Episode",
                                "video": {"url": "https://media.example/hls/master.m3u8"}}]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = """
                    <html><body>
                    <p class="pProgramHero__logoText">Fixture Program</p>
                    <p class="pProgramHero__description">Fixture program description</p>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val info = NhkVodProgramIE(http(transfer)).extract(url)
        assertEquals("sumo", info.id)
        assertEquals("Fixture Program", info.title)
        assertEquals("Fixture program description", info.description)
        assertEquals(1, info.entries.size)
        assertEquals("2049165-en", info.entries[0].id)
    }

    // --------------------------------------------------------------- school

    @Test
    fun schoolBangumiMapsVariablesAndChapters() = runTest {
        val url = "https://www2.nhk.or.jp/school/movie/bangumi.cgi?das_id=D0005150191_00000"
        val page = """
            <html><body><script>
            var r_version = "00003";
            var r_duration = "9:59.999";
            var r_upload = "2014-04-02";
            programObj.name = "にている かな";
            chapterTime.push('0:00');
            chapterTime.push('0:30');
            </script>
            <div class="cpTitle"><span>scene 1</span>First Scene</div>
            <div class="cpTitle"><span></span>Second Scene</div>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = NhkForSchoolBangumiIE(http(transfer)).extract(url)

        assertEquals("D0005150191_00003", info.id)
        assertEquals("にている かな", info.title)
        assertEquals(599.999, info.duration)
        assertEquals("20140402", info.uploadDate)
        assertEquals(
            "https://nhks-vh.akamaihd.net/i/das/D0005150/D0005150191_00003_V_000.f4v/master.m3u8",
            info.formats.single().url,
        )
        assertEquals(2, info.chapters.size)
        assertEquals(0.0, info.chapters[0].startTime)
        assertEquals(30.0, info.chapters[0].endTime)
        assertEquals("scene 1 First Scene", info.chapters[0].title)
        assertEquals(599.999, info.chapters[1].endTime)
    }

    @Test
    fun schoolSubjectScansProgramLinks() = runTest {
        val url = "https://www.nhk.or.jp/school/sougou/"
        val page = """
            <html><body>
            <span class="subjectName"><img src="x">総合的な学習の時間</span>
            <a href="/school/sougou/q/">Q</a>
            <a href="/school/sougou/r/">R</a>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = NhkForSchoolSubjectIE(http(transfer)).extract(url)
        assertEquals("sougou", info.id)
        assertEquals("総合的な学習の時間", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://www.nhk.or.jp/school/sougou/q/", info.entries[0].url)
    }

    @Test
    fun schoolProgramListReadsTheProgramJson() = runTest {
        val url = "https://www.nhk.or.jp/school/sougou/q/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = """
                    <html><head><title>Ｑ～こどものための哲学 | NHK for School</title></head>
                    <body><div class="programDetail "><p>Fixture description</p></div></body></html>
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://www.nhk.or.jp/school/sougou/q/meta/program.json",
                contentType = "application/json",
                body = """{"part": [{"part-video-dasid": "D0001"}, {"part-video-dasid": "D0002"}]}""",
            ),
        )
        val info = NhkForSchoolProgramListIE(http(transfer)).extract(url)
        assertEquals("sougou/q", info.id)
        assertEquals("Ｑ～こどものための哲学", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://www2.nhk.or.jp/school/movie/bangumi.cgi?das_id=D0001", info.entries[0].url)
    }

    // --------------------------------------------------------------- Radiru

    @Test
    fun radiruSeriesListsEpisodesAndExtractsOne() = runTest {
        val seriesJson = """
            {"title": "Fixture Series", "corner_name": "Corner", "radio_broadcast": "FM",
             "thumbnail_url": "https://img.example/series.jpg",
             "series_description": "Fixture series description",
             "episodes": [{"id": 4251382, "program_title": "Fixture Episode",
                           "program_sub_title": "Fixture subtitle",
                           "stream_url": "https://media.example/radio/index.m3u8",
                           "aa_contents_id": "a;b;c;d;2025-07-07T10:00:00_2025-07-07T10:01:00_2025-07-07T11:00:00"}]}
        """.trimIndent()
        val playlistUrl = "https://www.nhk.or.jp/radio/ondemand/detail.html?p=LG96ZW5KZ4_01"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.nhk.or.jp/radio-api/app/v1/web/ondemand/series" +
                    "?site_id=LG96ZW5KZ4&corner_site_id=01",
                contentType = "application/json",
                body = seriesJson,
            ),
        )
        val playlist = NhkRadiruIE(http(transfer)).extract(playlistUrl)
        assertEquals("LG96ZW5KZ4_01", playlist.id)
        assertEquals("Fixture Series Corner", playlist.title)
        assertEquals(1, playlist.entries.size)
        assertEquals(
            "https://www.nhk.or.jp/radio/ondemand/detail.html?p=LG96ZW5KZ4_01_4251382",
            playlist.entries[0].url,
        )

        val episodeTransfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.nhk.or.jp/radio-api/app/v1/web/ondemand/series" +
                    "?site_id=LG96ZW5KZ4&corner_site_id=01",
                contentType = "application/json",
                body = seriesJson,
            ),
        )
        val episode = NhkRadiruIE(http(episodeTransfer)).extract(
            "https://www.nhk.or.jp/radio/player/ondemand.html?p=LG96ZW5KZ4_01_4251382",
        )
        assertEquals("LG96ZW5KZ4_01_4251382", episode.id)
        assertEquals("Fixture Episode", episode.title)
        assertEquals("20250707", episode.uploadDate)
        assertEquals("m3u8_native", episode.formats.single().protocol)
    }

    @Test
    fun radiruNewsBranchMapsTheHeadline() = runTest {
        val url = "https://www.nhk.or.jp/radio/ondemand/detail.html?p=18439M2W42_01_4251212"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.nhk.or.jp/s-media/news/news-site/list/v1/all.json",
                contentType = "application/json",
                body = """
                    {"main": {"program_name": "NHK Radio News", "media_name": "NHK R1",
                      "site_detail": "Fixture",
                      "detail_list": [{"headline_id": "4251212",
                        "headline_image": "https://img.example/news.jpg",
                        "file_list": [{"file_name": "https://media.example/news/index.m3u8",
                                       "file_title": "Fixture News",
                                       "file_title_sub": "Fixture sub",
                                       "open_time": "2025-07-08T00:00:00Z",
                                       "aa_vinfo4": "2025-07-08T00:00:00_x"}]}]}}
                """.trimIndent(),
            ),
        )
        val info = NhkRadiruIE(http(transfer)).extract(url)
        assertEquals("18439M2W42_01_4251212", info.id)
        assertEquals("Fixture News", info.title)
        assertEquals("20250708", info.uploadDate)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun radioNewsPageRedispatchesToRadiru() = runTest {
        val info = NhkRadioNewsPageIE(http(transfer())).extract("https://www.nhk.or.jp/radionews/")
        assertEquals(
            "https://www.nhk.or.jp/radio/ondemand/detail.html?p=18439M2W42_01",
            info.redirectUrl,
        )
    }

    @Test
    fun radiruLiveReadsTheConfigAndNoaJson() = runTest {
        val url = "https://www.nhk.or.jp/radio/player/?ch=r1"
        val config = """
            <config>
              <url_program_noa>//www.nhk.or.jp/radio-api/app/v1/web/noa?area={area}</url_program_noa>
              <data><area>tokyo</area><areakey>130</areakey></data>
              <data><area>osaka</area><areakey>270</areakey></data>
              <r1hls>https://media.example/live/r1.m3u8</r1hls>
            </config>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.nhk.or.jp/radio/config/config_web.xml",
                contentType = "application/xml",
                body = config,
            ),
            FixtureRoute(
                urlPattern = "https://www.nhk.or.jp/radio-api/app/v1/web/noa?area=130",
                contentType = "application/json",
                body = """
                    {"r1": {"publishedOn": {"id": "bs-r1-130",
                      "broadcastDisplayName": "NHKラジオ第1・東京",
                      "logo": [{"url": "https://img.example/r1.svg", "width": 100, "height": 100}]}}}
                """.trimIndent(),
            ),
        )
        val info = NhkRadiruLiveIE(http(transfer)).extract(url)
        assertEquals("bs-r1-130", info.id)
        assertEquals("NHKラジオ第1・東京", info.title)
        assertEquals(true, info.isLive)
        assertEquals("https://media.example/live/r1.m3u8", info.formats.single().url)
        assertEquals("https://img.example/r1.svg", info.thumbnails.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun vodIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www3.nhk.or.jp/nhkworld/en/shows/2049165/"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("2049165-en"),
                "title" to Expect.Value("Fixture Program - Fixture Episode"),
                "duration" to Expect.Value(120.0),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api.nhkworld.jp/showsapi/v1/en/video_episodes/2049165",
                    contentType = "application/json",
                    body = """
                        {"id": "2049165", "lang": "en", "title": "Fixture Episode",
                         "video_program": {"title": "Fixture Program"},
                         "video": {"url": "https://media.example/hls/master.m3u8", "duration": 120}}
                    """.trimIndent(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NhkVodIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun radiruLiveIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://www.nhk.or.jp/radio/player/?ch=r1"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value("bs-r1-130"),
                "is_live" to Expect.Value(true),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://www.nhk.or.jp/radio/config/config_web.xml",
                    contentType = "application/xml",
                    body = """
                        <config>
                          <url_program_noa>//www.nhk.or.jp/noa?area={area}</url_program_noa>
                          <data><area>tokyo</area><areakey>130</areakey></data>
                          <r1hls>https://media.example/live/r1.m3u8</r1hls>
                        </config>
                    """.trimIndent(),
                ),
                FixtureRoute(
                    urlPattern = "https://www.nhk.or.jp/noa?area=130",
                    contentType = "application/json",
                    body = """{"r1": {"publishedOn": {"id": "bs-r1-130"}}}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> NhkRadiruLiveIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
