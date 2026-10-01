package com.anydownload.core.extract.imgur

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
 * Fixture cases for the Imgur subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no user token appears
 * (the anonymous public client id is the same constant the public API
 * always receives).
 */
class ImgurIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val mediaId = "zV03bd5"

    private fun mediaJson(animated: Boolean = true) = """
        {"id": "$mediaId", "created_at": "2024-03-15T00:00:00Z",
         "account": {"username": "fixture_user"},
         "media": [{"id": "$mediaId", "type": "video", "url": "https://media.example/video.mp4",
                    "ext": "mp4", "width": 1280, "height": 720, "size": 1000,
                    "metadata": {"is_animated": $animated, "has_sound": true,
                                 "title": "Fixture Imgur", "duration": 56.92}}]}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            ImgurIE(http(transfer())) to "https://imgur.com/$mediaId",
            ImgurIE(http(transfer())) to "https://i.imgur.com/$mediaId.gifv",
            ImgurGalleryIE(http(transfer())) to "https://imgur.com/gallery/YcAQlkx",
            ImgurGalleryIE(http(transfer())) to "https://imgur.com/t/unmuted/6lAn9VQ",
            ImgurAlbumIE(http(transfer())) to "https://imgur.com/a/iX265HX",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(ImgurIE(http(transfer())).suitable("https://imgur.com/a/iX265HX"))
    }

    // ------------------------------------------------------------------ media

    @Test
    fun mediaApiYieldsFormatsAndMetadata() = runTest {
        val url = "https://imgur.com/$mediaId"
        val page = """
            <html><head>
            <meta property="og:title" content="Fixture Imgur">
            <meta property="og:description" content="Fixture description">
            <meta property="og:image" content="https://media.example/thumb.jpg">
            </head><body></body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.imgur.com/post/v1/media/$mediaId?client_id=*",
                contentType = "application/json",
                body = mediaJson(),
            ),
            FixtureRoute(urlPattern = "https://i.imgur.com/$mediaId.gifv", contentType = "text/html", body = page),
        )
        val info = ImgurIE(http(transfer)).extract(url)
        assertEquals(mediaId, info.id)
        assertEquals("Fixture Imgur", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(56.92, info.duration)
        assertEquals("20240315", info.uploadDate)
        assertEquals("fixture_user", info.channel)
        assertEquals(1, info.formats.size)
        assertEquals("https://media.example/video.mp4", info.formats.single().url)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    // ----------------------------------------------------------------- gallery

    @Test
    fun galleryAlbumYieldsEntries() = runTest {
        val url = "https://imgur.com/gallery/YcAQlkx"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.imgur.com/post/v1/albums/YcAQlkx?client_id=*",
                contentType = "application/json",
                body = """
                    {"id": "YcAQlkx", "title": "Fixture Gallery", "is_album": true,
                     "media": [{"id": "video-1", "type": "video", "metadata": {"is_animated": true}},
                               {"id": "video-2", "type": "video", "metadata": {"is_animated": true}}]}
                """.trimIndent(),
            ),
        )
        val info = ImgurGalleryIE(http(transfer)).extract(url)
        assertEquals("Fixture Gallery", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("https://imgur.com/video-1", info.entries[0].url)
    }

    @Test
    fun singleVideoGalleryRedirectsToTheMedia() = runTest {
        val url = "https://imgur.com/gallery/YcAQlkx"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.imgur.com/post/v1/albums/YcAQlkx?client_id=*",
                contentType = "application/json",
                body = """
                    {"id": "YcAQlkx", "title": "Fixture Gallery", "is_album": true,
                     "media": [{"id": "video-1", "type": "video", "metadata": {"is_animated": true}}]}
                """.trimIndent(),
            ),
        )
        val info = ImgurGalleryIE(http(transfer)).extract(url)
        assertEquals("video-1", info.id)
        assertEquals("https://imgur.com/video-1", info.redirectUrl)
    }

    @Test
    fun albumKeepsTheAlbumId() = runTest {
        val url = "https://imgur.com/a/iX265HX"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://api.imgur.com/post/v1/albums/iX265HX?client_id=*",
                contentType = "application/json",
                body = """
                    {"id": "iX265HX", "title": "Fixture Album", "is_album": true,
                     "media": [{"id": "video-1", "type": "video", "metadata": {"is_animated": true}}]}
                """.trimIndent(),
            ),
        )
        val info = ImgurAlbumIE(http(transfer)).extract(url)
        assertEquals("iX265HX", info.id)
        assertEquals(1, info.entries.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun mediaIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://imgur.com/$mediaId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(mediaId),
                "title" to Expect.Value("Fixture Imgur"),
                "formats" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://api.imgur.com/post/v1/media/$mediaId?client_id=*",
                    contentType = "application/json",
                    body = mediaJson(),
                ),
                FixtureRoute(
                    urlPattern = "https://i.imgur.com/$mediaId.gifv",
                    contentType = "text/html",
                    body = """<html><head><meta property="og:title" content="Fixture Imgur"></head></html>""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> ImgurIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
