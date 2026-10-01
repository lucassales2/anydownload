package com.anydownload.core.extract.bluesky

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
 * Fixture cases for the Bluesky subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no token appears.
 */
class BlueskyIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val handle = "fixture.bsky.social"
    private val postId = "3l4omssdl632g"
    private val did = "did:plc:fixture123"

    private fun threadBody(post: String) = """
        {"thread": {"post": $post}}
    """.trimIndent()

    private val postBody = """
        {"uri": "at://$handle/app.bsky.feed.post/$postId",
         "author": {"did": "$did", "handle": "$handle", "displayName": "Fixture Author"},
         "record": {"text": "Fixture video post"},
         "indexedAt": "2024-09-21T00:00:00Z",
         "embed": {"${'$'}type": "app.bsky.embed.video#view", "cid": "fixture-cid",
                   "playlist": "https://media.example/hls/master.m3u8",
                   "thumbnail": "https://media.example/thumb.jpg",
                   "aspectRatio": {"width": 1280, "height": 720}},
         "labels": [{"val": "sexual"}]}
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatch() {
        val extractor = BlueskyIE(http(transfer()))
        assertTrue(extractor.suitable("https://bsky.app/profile/$handle/post/$postId"))
        assertTrue(extractor.suitable("https://main.bsky.dev/profile/$handle/post/$postId"))
        assertTrue(extractor.suitable("at://$did/app.bsky.feed.post/$postId"))
        assertFalse(extractor.suitable("https://bsky.app/profile/$handle"))
    }

    // ----------------------------------------------------------------- video

    @Test
    fun postThreadYieldsHlsAndBlobFormats() = runTest {
        val url = "https://bsky.app/profile/$handle/post/$postId"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://public.api.bsky.app/xrpc/app.bsky.feed.getPostThread*",
                contentType = "application/json",
                body = threadBody(postBody),
            ),
            FixtureRoute(
                urlPattern = "https://plc.directory/$did",
                contentType = "application/json",
                body = """
                    {"service": [{"type": "AtprotoPersonalDataServer",
                                  "serviceEndpoint": "https://pds.example"}]}
                """.trimIndent(),
            ),
        )
        val info = BlueskyIE(http(transfer)).extract(url)
        assertEquals(postId, info.id)
        assertEquals("Fixture video post", info.title)
        assertEquals("Fixture Author", info.uploader)
        assertEquals(did, info.channelId)
        assertEquals("20240921", info.uploadDate)
        assertEquals(18, info.ageLimit)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals(
            "https://pds.example/xrpc/com.atproto.sync.getBlob?did=$did&cid=fixture-cid",
            info.formats[1].url,
        )
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
    }

    @Test
    fun externalEmbedBecomesARedirect() = runTest {
        val url = "https://bsky.app/profile/$handle/post/$postId"
        val body = """
            {"uri": "at://$handle/app.bsky.feed.post/$postId",
             "author": {"did": "$did", "handle": "$handle"},
             "record": {"text": "Fixture link"},
             "embed": {"external": {"uri": "https://media.example/watch/fixture"}}}
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://public.api.bsky.app/xrpc/app.bsky.feed.getPostThread*",
                contentType = "application/json",
                body = threadBody(body),
            ),
        )
        val info = BlueskyIE(http(transfer)).extract(url)
        assertEquals("https://media.example/watch/fixture", info.redirectUrl)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun postIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "https://bsky.app/profile/$handle/post/$postId"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(postId),
                "title" to Expect.Value("Fixture video post"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://public.api.bsky.app/xrpc/app.bsky.feed.getPostThread*",
                    contentType = "application/json",
                    body = threadBody(postBody),
                ),
                FixtureRoute(
                    urlPattern = "https://plc.directory/$did",
                    contentType = "application/json",
                    body = """{"service": [{"type": "AtprotoPersonalDataServer", "serviceEndpoint": "https://pds.example"}]}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> BlueskyIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
