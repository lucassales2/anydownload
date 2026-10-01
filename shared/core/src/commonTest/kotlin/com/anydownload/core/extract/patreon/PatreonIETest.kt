package com.anydownload.core.extract.patreon

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
 * Fixture cases for the Patreon public-post subset. Every id, host, and
 * address is synthesized (`*.example`); no cookie, bearer token, or signed
 * media URL appears.
 */
class PatreonIETest {

    private val postUrl = "https://www.patreon.com/posts/12345678"
    private val apiPattern = "https://www.patreon.com/api/posts/12345678*"

    private val postFileResponse = """
        {
          "data": {"id": "12345678", "type": "post", "attributes": {
            "title": "Fixture Patreon post",
            "content": "<p>Synthetic <b>content</b></p>",
            "published_at": "2026-08-19T10:00:00.000+00:00",
            "current_user_can_view": true,
            "image": {"large_url": "https://img.example/large.jpg", "url": "https://img.example/small.jpg"},
            "post_file": {"name": "video.mp4", "url": "https://media.example/post-file.mp4"},
            "comment_count": 1, "like_count": 2
          }},
          "included": [
            {"type": "user", "id": "u1", "attributes": {"full_name": "Fixture Creator", "url": "https://www.patreon.com/fixture"}},
            {"type": "campaign", "id": "c1",
             "attributes": {"title": "Fixture Campaign", "url": "https://www.patreon.com/fixture", "patron_count": 10}}
          ]
        }
    """.trimIndent()

    private val mediaResponse = """
        {
          "data": {"id": "12345678", "type": "post", "attributes": {
            "title": "Multi attachment post",
            "published_at": "2026-08-19T10:00:00.000+00:00",
            "current_user_can_view": true
          }},
          "included": [
            {"type": "media", "id": "m1", "attributes": {
              "download_url": "https://media.example/one.mp4", "mimetype": "video/mp4",
              "size_bytes": 1000, "file_name": "one.mp4"}},
            {"type": "media", "id": "m2", "attributes": {
              "download_url": "https://media.example/two.m4a", "mimetype": "audio/mp4",
              "size_bytes": 2000, "file_name": "two.m4a"}},
            {"type": "user", "id": "u1", "attributes": {"full_name": "Fixture Creator"}}
          ]
        }
    """.trimIndent()

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): PatreonIE =
        PatreonIE(ExtractorHttp(transfer))

    // ------------------------------------------------------------ URL matching

    @Test
    fun postFormsMatch() {
        val ie = extractor(transfer())
        for (url in listOf(
            "https://www.patreon.com/posts/12345678",
            "https://www.patreon.com/creation?hid=12345678",
            "https://www.patreon.com/fixtureuser/posts/fixture-title-12345678",
        )) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://www.patreon.com/fixtureuser"))
        assertFalse(ie.suitable("https://www.patreon.com/posts/fixture-title"))
    }

    // -------------------------------------------------------------- extraction

    @Test
    fun postFileMapsFormatsAndMetadata() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = apiPattern, body = postFileResponse))
        val info = extractor(transfer).extract(postUrl)

        assertEquals("12345678", info.id)
        assertEquals("Fixture Patreon post", info.title)
        assertEquals("Synthetic content", info.description)
        assertEquals("20260819", info.uploadDate)
        assertEquals("Fixture Creator", info.uploader)
        assertEquals("Fixture Campaign", info.channel)
        assertEquals("c1", info.channelId)
        assertEquals("https://img.example/large.jpg", info.thumbnails.single().url)

        val format = info.formats.single()
        assertEquals("https://media.example/post-file.mp4", format.url)
        assertEquals("mp4", format.ext)
        assertEquals("https://www.patreon.com/", format.httpHeaders?.get("referer"))
        assertTrue(info.media.isEmpty())
    }

    @Test
    fun multipleMediaBecomeBoundedItems() = runTest {
        val transfer = transfer(FixtureRoute(urlPattern = apiPattern, body = mediaResponse))
        val info = extractor(transfer).extract(postUrl)

        assertEquals("Multi attachment post", info.title)
        assertEquals(2, info.media.size)
        assertEquals("m1", info.media[0].mediaId)
        assertEquals("mp4", info.media[0].formats.single().ext)
        assertEquals(1000L, info.media[0].formats.single().filesize)
        assertEquals("m2", info.media[1].mediaId)
        assertEquals("m4a", info.media[1].formats.single().ext)
        assertTrue(info.formats.isEmpty())
    }

    @Test
    fun embedOnlyPostRedirects() = runTest {
        val embed = """
            {"data":{"id":"12345678","attributes":{
              "title":"Embed only","current_user_can_view":true,
              "embed":{"url":"https://vimeo.com/12345/abcdef0123"}
            }}}
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = apiPattern, body = embed))
        val info = extractor(transfer).extract(postUrl)
        assertEquals("https://player.vimeo.com/video/12345?h=abcdef0123", info.redirectUrl)
    }

    @Test
    fun patronOnlyPostFailsTyped() = runTest {
        val locked = """
            {"data":{"id":"12345678","attributes":{
              "title":"Locked","current_user_can_view":false
            }}}
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = apiPattern, body = locked))
        assertFailsWith<ExtractionError.LoginRequired> {
            extractor(transfer).extract(postUrl)
        }
    }

    // --------------------------------------------------------------- harness

    @Test
    fun patreonIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = postUrl,
            infoDict = mapOf(
                "id" to Expect.Value("12345678"),
                "title" to Expect.Value("Fixture Patreon post"),
                "uploader" to Expect.Value("Fixture Creator"),
                "channel" to Expect.Value("Fixture Campaign"),
                "upload_date" to Expect.Value("20260819"),
                "formats" to Expect.Count(1),
                "formats.0.ext" to Expect.Value("mp4"),
            ),
            routes = listOf(FixtureRoute(urlPattern = apiPattern, body = postFileResponse)),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> PatreonIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
