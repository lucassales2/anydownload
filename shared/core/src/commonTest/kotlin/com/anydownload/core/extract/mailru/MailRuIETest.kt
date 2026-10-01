package com.anydownload.core.extract.mailru

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
 * Fixture cases for the Mail.Ru subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class MailRuIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val metaUrl = "https://my.mail.ru/video/embed/7949340477499637815"
    private val configUrl = "http://my.mail.ru/corp/hitech/video/news_hi-tech_mail_ru/1263.html"
    private val fallbackUrl = "https://my.mail.ru/mail/720pizle/video/_myvideo/502.html"
    private val musicUrl = "https://my.mail.ru/music/songs/fixture-l-a-h-4e31f7125d0dfaef505d947642366893"
    private val searchUrl = "https://my.mail.ru/music/search/black%20shadow"

    private fun metaBody(accId: String = "46301138", itemId: String = "76", title: String = "Fixture MailRu Title.mp4") = """
        {"videos": [{"url": "https://media.example/video.mp4", "key": "720p"}],
         "meta": {"title": "$title", "poster": "https://media.example/poster.jpg",
                  "duration": 184, "timestamp": 1393235077, "accId": "$accId", "itemId": "$itemId"},
         "author": {"name": "sonypicturesrus", "id": "sonypicturesrus@mail.ru"},
         "viewsCount": 12345}
    """.trimIndent()

    private val musicTrack = """
        {"URL": "https://media.example/track.mp3", "File": "4e31f7125d0dfaef505d947642366893",
         "OwnerName": "Fixture Artist", "UploaderID": "1459196328", "DurationInSeconds": 280,
         "PlayCount": 42, "Name": "Fixture Track", "Author": "Fixture Artist", "BitRate": 320}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val video = MailRuIE(http(transfer()))
        val videoCases = listOf(
            metaUrl,
            configUrl,
            "http://my.mail.ru/video/top#video=/mail/sonypicturesrus/75/76",
            "http://m.my.mail.ru/mail/3sktvtr/video/_myvideo/138.html",
            "https://videoapi.my.mail.ru/videos/embed/mail/cloud-strife/Games/2009.html",
            "https://my.mail.ru//list//sinyutin10/video/_myvideo/4.html",
        )
        for (url in videoCases) {
            assertTrue(video.suitable(url), "MailRu must match: $url")
        }
        assertFalse(video.suitable("https://www.example.com/video/123456"))

        val music = MailRuMusicIE(http(transfer()))
        assertTrue(music.suitable(musicUrl))
        assertFalse(music.suitable(searchUrl))

        val search = MailRuMusicSearchIE(http(transfer()))
        assertTrue(search.suitable(searchUrl))
        assertFalse(search.suitable(musicUrl))
    }

    // ------------------------------------------------------------------ video

    @Test
    fun metaUrlYieldsTheVideoData() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "https://my.mail.ru/+/video/meta/7949340477499637815", contentType = "application/json", body = metaBody()),
        )
        val info = MailRuIE(http(transfer)).extract(metaUrl)
        assertEquals("46301138_76", info.id)
        assertEquals("Fixture MailRu Title", info.title)
        assertEquals("sonypicturesrus", info.uploader)
        assertEquals(184.0, info.duration)
        assertEquals("20140224", info.uploadDate)
        assertEquals(12345L, info.viewCount)
        assertEquals("https://media.example/poster.jpg", info.thumbnails.single().url)
        assertEquals(1, info.formats.size)
        assertEquals("720p", info.formats[0].formatId)
        assertEquals(720L, info.formats[0].height)
    }

    @Test
    fun pageConfigMetaUrlIsResolvedAgainstTheHost() = runTest {
        val page = "<html><body><script class=\"sp-video__page-config\">{\"metaUrl\": \"/+/video/meta/46843144\"}</script></body></html>"
        val transfer = transfer(
            FixtureRoute(urlPattern = configUrl, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://my.mail.ru/+/video/meta/46843144",
                contentType = "application/json",
                body = metaBody(accId = "46843144", itemId = "1263", title = "Fixture Config.mp4"),
            ),
        )
        val info = MailRuIE(http(transfer)).extract(configUrl)
        assertEquals("46843144_1263", info.id)
        assertEquals("Fixture Config", info.title)
    }

    @Test
    fun missingPageConfigFallsBackToTheVideoApi() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = configUrl, contentType = "text/html", body = "<html></html>"),
            FixtureRoute(
                urlPattern = "http://api.video.mail.ru/videos/corp/hitech/news_hi-tech_mail_ru/1263.json*",
                contentType = "application/json",
                body = metaBody(accId = "46843144", itemId = "1263"),
            ),
        )
        val info = MailRuIE(http(transfer)).extract(configUrl)
        assertEquals("46843144_1263", info.id)
    }

    @Test
    fun metaOnlyUrlWithoutDataFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = "https://my.mail.ru/+/video/meta/7949340477499637815", statusCode = 404, body = ""),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            MailRuIE(http(transfer)).extract(metaUrl)
        }
    }

    // ------------------------------------------------------------------ music

    @Test
    fun musicTrackYieldsTheDirectAudioRow() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = musicUrl,
                contentType = "text/html",
                body = "<html><head><meta property=\"og:title\" content=\"Fixture Track Title\"></head></html>",
            ),
            FixtureRoute(
                urlPattern = "https://my.mail.ru/cgi-bin/my/ajax*",
                contentType = "application/json",
                body = "[{\"MusicData\": [$musicTrack]}]",
            ),
        )
        val info = MailRuMusicIE(http(transfer)).extract(musicUrl)
        assertEquals("4e31f7125d0dfaef505d947642366893", info.id)
        assertEquals("Fixture Track Title", info.title)
        assertEquals("Fixture Artist", info.uploader)
        assertEquals(280.0, info.duration)
        assertEquals(42L, info.viewCount)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/track.mp3", info.formats[0].url)
        assertEquals("mp3", info.formats[0].ext)
        assertEquals(320.0, info.formats[0].abr)
        assertEquals("none", info.formats[0].vcodec)
    }

    @Test
    fun musicSearchPagesUntilAShortList() = runTest {
        val secondTrack = """
            {"URL": "https://media.example/track2.mp3", "File": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
             "OwnerName": "Fixture Artist", "Name": "Second Track", "Author": "Fixture Artist"}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://my.mail.ru/cgi-bin/my/ajax*arg_offset=0*",
                contentType = "application/json",
                body = "[{\"MusicData\": [$musicTrack, $secondTrack], \"Results\": {\"music\": {\"Total\": 2}}}]",
            ),
            FixtureRoute(
                urlPattern = "https://my.mail.ru/cgi-bin/my/ajax*arg_offset=100*",
                contentType = "application/json",
                body = "[{\"MusicData\": []}]",
            ),
        )
        val info = MailRuMusicSearchIE(http(transfer)).extract(searchUrl)
        assertEquals("black shadow", info.id)
        assertEquals(2, info.entries.size)
        assertEquals("4e31f7125d0dfaef505d947642366893", info.entries[0].id)
        assertEquals("https://media.example/track.mp3", info.entries[0].url)
        assertEquals("https://media.example/track2.mp3", info.entries[1].url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoMetaIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = metaUrl,
            infoDict = mapOf(
                "id" to Expect.Value("46301138_76"),
                "title" to Expect.Value("Fixture MailRu Title"),
                "upload_date" to Expect.Value("20140224"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://my.mail.ru/+/video/meta/7949340477499637815",
                    contentType = "application/json",
                    body = metaBody(),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MailRuIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun musicTrackIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = musicUrl,
            infoDict = mapOf(
                "id" to Expect.Value("4e31f7125d0dfaef505d947642366893"),
                "title" to Expect.Value("Fixture Track Title"),
                "duration" to Expect.Value(280.0),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = musicUrl,
                    contentType = "text/html",
                    body = "<html><head><meta property=\"og:title\" content=\"Fixture Track Title\"></head></html>",
                ),
                FixtureRoute(
                    urlPattern = "https://my.mail.ru/cgi-bin/my/ajax*",
                    contentType = "application/json",
                    body = "[{\"MusicData\": [$musicTrack]}]",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> MailRuMusicIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
