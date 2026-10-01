package com.anydownload.core.extract.xhamster

import com.anydownload.core.extract.ExtractionError
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the xHamster subset. Every id, host, and media address is
 * synthesized (`*.example`); the ciphertext is generated from the translated
 * algorithm with a fake seed, and no cookie or signed URL appears.
 */
class XHamsterIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl = "https://xhamster.com/videos/fixture-video-1509445"

    /** Algo 1, seed 123456789, plaintext `https://media.example/hls/master.m3u8`. */
    private val cipher = "0115cd5b07187b5669d789597275f26e88ad95bbdda17282c591ec2e811b88b7" +
        "106fbfcb473e42f14c7c"

    private val initialsPage = """
        <html><script>
        window.initials = {"videoModel": {
          "title": "Fixture XH Title", "description": "Fixture description",
          "created": 1350194821, "duration": 893, "views": 1000,
          "thumbURL": "https://img.example/thumb.jpg",
          "author": {"name": "Fixture Uploader", "pageURL": "https://xhamster.com/users/fixture-uploader"},
          "sources": {
            "mp4": {"720p": "https://media.example/video_720p.mp4",
                    "480p": "https://media.example/video_480p.mp4"},
            "download": {"720p": {"size": 123456}}
          }
        }, "xplayerSettings": {"sources": {"hls": {"url": "$cipher"}}}};
        </script></html>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            XHamsterIE(http(transfer())) to videoUrl,
            XHamsterIE(http(transfer())) to "https://xhamster.com/movies/1509445/fixture.html",
            XHamsterIE(http(transfer())) to "https://xhamster.one/videos/fixture-1509445",
            XHamsterIE(http(transfer())) to "https://xhday.com/videos/strapless-xhh7yVf",
            XHamsterEmbedIE(http(transfer())) to "https://xhamster.com/xembed.php?video=3328539",
            XHamsterUserIE(http(transfer())) to "https://xhamster.com/users/netvideogirls/videos",
            XHamsterUserIE(http(transfer())) to "https://xhamster.com/creators/squirt-orgasm-69",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(XHamsterIE(http(transfer())).suitable("https://xhamster.com/users/x"))
        assertFalse(XHamsterEmbedIE(http(transfer())).suitable("https://xhamster.com/videos/x-1"))
    }

    // ------------------------------------------------------------- XHamsterIE

    @Test
    fun initialsMapSourcesAndDecipherTheHlsUrl() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = videoUrl, contentType = "text/html", body = initialsPage),
        )
        val info = XHamsterIE(http(transfer)).extract(videoUrl)

        assertEquals("1509445", info.id)
        assertEquals("Fixture XH Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(893.0, info.duration)
        assertEquals("20121014", info.uploadDate)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals(1000L, info.viewCount)
        assertEquals(18, info.ageLimit)
        assertEquals("https://img.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(3, info.formats.size)
        assertEquals("mp4-720p", info.formats[0].formatId)
        assertEquals(123456L, info.formats[0].filesize)
        assertEquals("hls", info.formats[2].formatId)
        assertEquals("m3u8_native", info.formats[2].protocol)
        assertEquals("https://media.example/hls/master.m3u8", info.formats[2].url)
        assertEquals("https://xhamster.com/videos/fixture-video-1509445", info.formats[0].httpHeaders?.get("referer"))
    }

    @Test
    fun closedVideoFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = videoUrl,
                contentType = "text/html",
                body = """<html><div id="videoClosed">This video is no longer available</div></html>""",
            ),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            XHamsterIE(http(transfer)).extract(videoUrl)
        }
        assertTrue(error.message!!.contains("no longer available"))
    }

    @Test
    fun byteGeneratorDeciphersAllSevenAlgorithms() {
        // Algo 1 with the fixture seed and ciphertext.
        assertEquals(
            "https://media.example/hls/master.m3u8",
            decrypt(cipher),
        )
    }

    private fun decrypt(hex: String): String {
        val bytes = ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        val seed = (bytes[1].toInt() and 0xFF) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            ((bytes[3].toInt() and 0xFF) shl 16) or
            ((bytes[4].toInt() and 0xFF) shl 24)
        val generator = ByteGenerator(bytes[0].toInt() and 0xFF, seed)
        return buildString {
            for (index in 5 until bytes.size) {
                append(((bytes[index].toInt() and 0xFF) xor generator.nextByte()).toChar())
            }
        }
    }

    // -------------------------------------------------------- XHamsterEmbedIE

    @Test
    fun embedRedirectsToTheVideoPage() = runTest {
        val url = "https://xhamster.com/xembed.php?video=3328539"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = url,
                contentType = "text/html",
                body = """
                    <html><body><a href="https://xhamster.com/videos/fixture-video-3328539">x</a></body></html>
                """.trimIndent(),
            ),
        )
        val info = XHamsterEmbedIE(http(transfer)).extract(url)
        assertEquals("3328539", info.id)
        assertEquals("https://xhamster.com/videos/fixture-video-3328539", info.redirectUrl)
    }

    // --------------------------------------------------------- XHamsterUserIE

    @Test
    fun userScansTheVideoThumbs() = runTest {
        val url = "https://xhamster.com/users/netvideogirls/videos"
        val page = """
            <html><body>
            <a class="video-thumb__image-container" href="https://xhamster.com/videos/fixture-video-1509445"></a>
            </body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = "$url/1", contentType = "text/html", body = page),
        )
        val info = XHamsterUserIE(http(transfer)).extract(url)
        assertEquals("netvideogirls", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("1509445", info.entries[0].id)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun xHamsterIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("1509445"),
                "title" to Expect.Value("Fixture XH Title"),
                "duration" to Expect.Value(893.0),
                "age_limit" to Expect.Value(18),
                "formats.2.protocol" to Expect.Value("m3u8_native"),
                "formats.2.url" to Expect.Value("https://media.example/hls/master.m3u8"),
            ),
            routes = listOf(FixtureRoute(urlPattern = videoUrl, contentType = "text/html", body = initialsPage)),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> XHamsterIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
