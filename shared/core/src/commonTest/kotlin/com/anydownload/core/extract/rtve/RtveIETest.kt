package com.anydownload.core.extract.rtve

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
 * Fixture cases for the RTVE subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie or token
 * appears. The PNG fixture is built in-test with the inverse of the
 * translated cipher.
 */
class RtveIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "3088905"

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            RTVEALaCartaIE(http(transfer())) to
                "http://www.rtve.es/alacarta/videos/fixture/fixture-capitulo/$videoId/",
            RTVEAudioIE(http(transfer())) to
                "https://www.rtve.es/play/audios/fixture/fixture-capitulo/6082623/",
            RTVELiveIE(http(transfer())) to "http://www.rtve.es/directo/la-1/",
            RTVETelevisionIE(http(transfer())) to
                "http://www.rtve.es/television/fixture/fixture-capitulo/$videoId.shtml",
            RTVEProgramIE(http(transfer())) to "https://www.rtve.es/play/videos/fixture-program/",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(RTVEProgramIE(http(transfer())).suitable("http://www.rtve.es/alacarta/videos/x/y/1/"))
    }

    // -------------------------------------------------------------- a la carta

    @Test
    fun alaCartaYieldsPngFormatsAndSubtitles() = runTest {
        val url = "http://www.rtve.es/alacarta/videos/fixture/fixture-capitulo/$videoId/"
        val png = encryptedPng(listOf("HD_FULL" to "https://media.example/hls/master.m3u8"))
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "http://www.rtve.es/api/videos/$videoId/config/alacarta_videos.json",
                contentType = "application/json",
                body = """
                    {"page": {"items": [{"title": "Fixture RTVE", "description": "<p>Fixture description</p>",
                      "dateOfEmission": "2021-11-13 00:00:00", "duration": 96000,
                      "thumbnail": "https://media.example/thumb.png", "live": false}]}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "http://www.rtve.es/ztnr/movil/thumbnail/rtveplayw/videos/$videoId.png?q=v2",
                contentType = "image/png",
                body = png,
            ),
            FixtureRoute(
                urlPattern = "http://www.rtve.es/ztnr/movil/thumbnail/default/videos/$videoId.png?q=v2",
                contentType = "image/png",
                body = png,
            ),
            FixtureRoute(
                urlPattern = "https://api2.rtve.es/api/videos/$videoId/subtitulos.json",
                contentType = "application/json",
                body = """
                    {"page": {"items": [{"language": "es", "src": "https://media.example/sub/es.srt"}]}}
                """.trimIndent(),
            ),
        )
        val info = RTVEALaCartaIE(http(transfer)).extract(url)
        assertEquals(videoId, info.id)
        assertEquals("Fixture RTVE", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("20211113", info.uploadDate)
        assertEquals(96.0, info.duration)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats.first { it.protocol == "m3u8_native" }.protocol)
        assertEquals("es", info.subtitles.single().language)
    }

    // -------------------------------------------------------------- television

    @Test
    fun televisionPageRedirectsToThePlayUrl() = runTest {
        val url = "http://www.rtve.es/television/fixture/fixture-capitulo/$videoId.shtml"
        val page = """
            <html><head><meta name="contentUrl" content="https://www.rtve.es/play/videos/fixture/$videoId/"></head></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = RTVETelevisionIE(http(transfer)).extract(url)
        assertEquals("https://www.rtve.es/play/videos/fixture/$videoId/", info.redirectUrl)
    }

    // --------------------------------------------------------------- program

    @Test
    fun programListYieldsEntries() = runTest {
        val url = "https://www.rtve.es/play/videos/fixture-program/"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.rtve.es/api/programas/fixture-program/videos*",
                contentType = "application/json",
                body = """
                    {"page": {"items": [{"id": "$videoId", "longTitle": "Fixture Episode",
                      "htmlUrl": "https://www.rtve.es/play/videos/fixture/$videoId/"}]}}
                """.trimIndent(),
            ),
        )
        val info = RTVEProgramIE(http(transfer)).extract(url)
        assertEquals("fixture-program", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("https://www.rtve.es/play/videos/fixture/$videoId/", info.entries.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun alaCartaIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val url = "http://www.rtve.es/alacarta/videos/fixture/fixture-capitulo/$videoId/"
        val png = encryptedPng(listOf("HD_FULL" to "https://media.example/hls/master.m3u8"))
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture RTVE"),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "http://www.rtve.es/api/videos/$videoId/config/alacarta_videos.json",
                    contentType = "application/json",
                    body = """{"page": {"items": [{"title": "Fixture RTVE"}]}}""",
                ),
                FixtureRoute(
                    urlPattern = "http://www.rtve.es/ztnr/movil/thumbnail/rtveplayw/videos/$videoId.png?q=v2",
                    contentType = "image/png",
                    body = png,
                ),
                FixtureRoute(
                    urlPattern = "http://www.rtve.es/ztnr/movil/thumbnail/default/videos/$videoId.png?q=v2",
                    contentType = "image/png",
                    body = png,
                ),
                FixtureRoute(
                    urlPattern = "https://api2.rtve.es/api/videos/$videoId/subtitulos.json",
                    contentType = "application/json",
                    body = """{"page": {"items": []}}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RTVEALaCartaIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    // ------------------------------------------------------- fixture cipher

    /** Builds a PNG whose tEXt chunk decrypts to the given quality/url pairs. */
    private fun encryptedPng(entries: List<Pair<String, String>>): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ:/.?=-_"
        val bytes = mutableListOf<Byte>()
        bytes.addAll(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()).toList())
        bytes.addAll(byteArrayOf(0x0D, 0x0A, 0x1A, 0x0A).toList())
        for ((quality, url) in entries) {
            val data = (alphabetData(alphabet) + "#" + quality + "%%" + encodeDigits(alphabet, url))
                .map { (it.code and 0xff).toByte() }.toByteArray()
            bytes.addAll(chunk("tEXt", data))
        }
        bytes.addAll(chunk("IEND", ByteArray(0)))
        return kotlin.io.encoding.Base64.Default.encode(bytes.toByteArray())
    }

    private fun chunk(type: String, data: ByteArray): List<Byte> {
        val out = mutableListOf<Byte>()
        val length = data.size
        out.addAll(
            byteArrayOf(
                ((length shr 24) and 0xff).toByte(),
                ((length shr 16) and 0xff).toByte(),
                ((length shr 8) and 0xff).toByte(),
                (length and 0xff).toByte(),
            ).toList(),
        )
        out.addAll(type.map { (it.code and 0xff).toByte() }.toByteArray().toList())
        out.addAll(data.toList())
        out.addAll(byteArrayOf(0, 0, 0, 0).toList())
        return out
    }

    private fun alphabetData(alphabet: String): String {
        val sb = StringBuilder()
        var e = 0
        alphabet.forEachIndexed { index, c ->
            if (index > 0) repeat(e) { sb.append('x') }
            sb.append(c)
            e = (e + 1) % 4
        }
        return sb.toString()
    }

    private fun encodeDigits(alphabet: String, url: String): String {
        val digits = StringBuilder()
        var e = 3
        var b = 1
        for (char in url) {
            val target = alphabet.indexOf(char)
            require(target >= 0) { "character not in alphabet: $char" }
            digits.append(target / 10)
            repeat(e) { digits.append('0') }
            digits.append(target % 10)
            e = (b + 3) % 4
            b++
        }
        return digits.toString()
    }
}
