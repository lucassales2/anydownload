package com.anydownload.core.extract.rai

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
 * Fixture cases for the RAI subset. Every id, host, and media address is
 * synthesized (`*.example`); no cookie, token, or signed URL appears.
 */
class RaiIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val uuid = "cb27157f-9dd0-4aee-b788-b1f67643a391"

    private fun relinkerXml(contentUrl: String): String = """
        <relinker>
          <license_url>{}</license_url>
          <is_live>N</is_live>
          <duration>00:05:30</duration>
          <geoprotection>N</geoprotection>
          <bitrate>1500</bitrate>
          <url type="content"><![CDATA[$contentUrl]]></url>
        </relinker>
    """.trimIndent()

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            RaiPlayIE(http(transfer())) to "https://www.raiplay.it/video/2014/04/fixture-$uuid.html",
            RaiPlayLiveIE(http(transfer())) to "https://www.raiplay.it/dirette/rainews24",
            RaiPlayPlaylistIE(http(transfer())) to "https://www.raiplay.it/programmi/fixture-show",
            RaiPlaySoundIE(http(transfer())) to "https://www.raiplaysound.it/audio/2024/01/fixture-$uuid.html",
            RaiPlaySoundLiveIE(http(transfer())) to "https://www.raiplaysound.it/radio2",
            RaiPlaySoundPlaylistIE(http(transfer())) to
                "https://www.raiplaysound.it/programmi/ilruggitodelconiglio",
            RaiIE(http(transfer())) to "https://www.rai.it/dl/RaiTV/programmi/media/ContentItem-$uuid.html",
            RaiNewsIE(http(transfer())) to "https://www.rainews.it/video/2024/01/fixture-$uuid.html",
            RaiCulturaIE(http(transfer())) to "https://www.raicultura.it/letteratura/2018/12/fixture-$uuid.html",
            RaiSudtirolIE(http(transfer())) to "https://raisudtirol.rai.it/tv/media=12345",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(RaiPlayPlaylistIE(http(transfer())).suitable("https://www.raiplay.it/video/fixture-$uuid.html"))
        assertFalse(RaiNewsIE(http(transfer())).suitable("https://www.rainews.it/articoli/fixture.html"))
    }

    // -------------------------------------------------------------- RaiPlayIE

    @Test
    fun raiPlayMapsMetadataAndTheRelinker() = runTest {
        val base = "https://www.raiplay.it/video/2014/04/fixture-$uuid"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$base.json",
                contentType = "application/json",
                body = """
                    {"id": "ContentItem-$uuid", "name": "Fixture RaiPlay Title",
                     "description": "Fixture description",
                     "date_published": "2014-04-07", "time_published": "21:00",
                     "program_info": {"channel": "Rai 3"},
                     "images": {"landscape": "https://img.example/thumb.jpg"},
                     "video": {"content_url": "https://relinker.example/media",
                               "duration": "00:05:30",
                               "subtitlesArray": [{"url": "/subs.vtt", "language": "it"}]}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://relinker.example/media?output=64",
                contentType = "application/xml",
                body = relinkerXml("https://media.example/hls/master.m3u8"),
            ),
        )
        val info = RaiPlayIE(http(transfer)).extract("$base.html")

        assertEquals(uuid, info.id)
        assertEquals("Fixture RaiPlay Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals("Rai 3", info.uploader)
        assertEquals(330.0, info.duration)
        assertEquals("20140407", info.uploadDate)
        assertEquals("https://img.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("it", info.subtitles.single().language)
        assertTrue(transfer.requests.any { it.headers["user-agent"] == "Rai" })
    }

    @Test
    fun raiPlayDrmFailsTyped() = runTest {
        val base = "https://www.raiplay.it/video/2014/04/fixture-$uuid"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$base.json",
                contentType = "application/json",
                body = """
                    {"id": "ContentItem-$uuid", "rights_management": {"rights": {"drm": true}},
                     "video": {"content_url": "https://relinker.example/media"}}
                """.trimIndent(),
            ),
        )
        assertFailsWith<ExtractionError.NoFormats> {
            RaiPlayIE(http(transfer)).extract("$base.html")
        }
    }

    @Test
    fun raiPlayPlaylistListsTheSetItems() = runTest {
        val base = "https://www.raiplay.it/programmi/fixture-show"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$base.json",
                contentType = "application/json",
                body = """
                    {"name": "Fixture Show", "program_info": {"description": "Fixture program"},
                     "blocks": [{"name": "Stagione 1", "sets": [{"id": "set1", "name": "Episodi"}]}]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "$base/set1.json",
                contentType = "application/json",
                body = """
                    {"items": [{"path_id": "/video/2014/04/fixture-$uuid.html"}]}
                """.trimIndent(),
            ),
        )
        val info = RaiPlayPlaylistIE(http(transfer)).extract(base)
        assertEquals("fixture-show", info.id)
        assertEquals("Fixture Show", info.title)
        assertEquals(1, info.entries.size)
        assertEquals(uuid, info.entries[0].id)
    }

    // ---------------------------------------------------------- RaiPlaySoundIE

    @Test
    fun raiPlaySoundMapsTheAudioRelinker() = runTest {
        val base = "https://www.raiplaysound.it/audio/2024/01/fixture-$uuid"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$base.json",
                contentType = "application/json",
                body = """
                    {"uniquename": "ContentItem-$uuid", "title": "Fixture Sound Title",
                     "description": "Fixture sound description",
                     "create_date": "2024-01-05", "create_time": "10:00",
                     "downloadable_audio": {"url": "https://relinker.example/audio"},
                     "podcast_info": {"title": "Fixture Series",
                                      "images": {"square": "https://img.example/sound.jpg"}}}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://relinker.example/audio?output=64",
                contentType = "application/xml",
                body = relinkerXml("https://media.example/audio.mp3"),
            ),
        )
        val info = RaiPlaySoundIE(http(transfer)).extract("$base.html")
        assertEquals(uuid, info.id)
        assertEquals("Fixture Sound Title", info.title)
        assertEquals("20240105", info.uploadDate)
        assertEquals("https-mp3", info.formats.single().formatId)
        assertEquals("mp3", info.formats.single().ext)
        assertEquals("none", info.formats.single().vcodec)
    }

    // ----------------------------------------------------------------- RaiIE

    @Test
    fun raiContentItemAudioMapsADirectFormat() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://www.rai.tv/dl/RaiTV/programmi/media/ContentItem-$uuid.html?json",
                contentType = "application/json",
                body = """
                    {"type": "Audio", "name": "Fixture RAI Audio", "desc": "Fixture",
                     "audioUrl": "https://media.example/audio.mp3", "formatoAudio": "mp3",
                     "date": "2014-04-07", "length": "00:03:00",
                     "author": "RAI", "image": "https://img.example/rai.jpg"}
                """.trimIndent(),
            ),
        )
        val info = RaiIE(http(transfer)).extract(
            "https://www.rai.it/dl/RaiTV/programmi/media/ContentItem-$uuid.html",
        )
        assertEquals(uuid, info.id)
        assertEquals("Fixture RAI Audio", info.title)
        assertEquals("https-mp3", info.formats.single().formatId)
        assertEquals("mp3", info.formats.single().acodec)
        assertEquals("20140407", info.uploadDate)
    }

    // -------------------------------------------------------------- RaiNewsIE

    @Test
    fun raiNewsReadsThePlayerData() = runTest {
        val url = "https://www.rainews.it/video/2024/01/fixture-$uuid.html"
        val page = """
            <html><body>
            <rainews-player data='{"title": "Fixture News", "track_info": {"date": "2018-12-06", "editor": "raicultura"}, "mediapolis": {"content_url": "/relinker/news"}}'></rainews-player>
            </body></html>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(urlPattern = url, contentType = "text/html", body = page),
            FixtureRoute(
                urlPattern = "https://www.rainews.it/relinker/news?output=64",
                contentType = "application/xml",
                body = relinkerXml("https://media.example/news/master.m3u8"),
            ),
        )
        val info = RaiNewsIE(http(transfer)).extract(url)
        assertEquals(uuid, info.id)
        assertEquals("Fixture News", info.title)
        assertEquals("raicultura", info.uploader)
        assertEquals("20181206", info.uploadDate)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    // ----------------------------------------------------------- RaiSudtirolIE

    @Test
    fun raiSudtirolReadsThePageSources() = runTest {
        val url = "https://raisudtirol.rai.it/tv/media=12345"
        val page = """
            <html><body>
            <span class="med_title">Fixture Sudtirol</span>
            <span class="med_data">2024-01-05</span>
            <script>sources: [{file: "https://media.example/sudtirol/master.m3u8"}]</script>
            <script>image: 'https://img.example/sudtirol.jpg'</script>
            </body></html>
        """.trimIndent()
        val transfer = transfer(FixtureRoute(urlPattern = url, contentType = "text/html", body = page))
        val info = RaiSudtirolIE(http(transfer)).extract(url)
        assertEquals("12345", info.id)
        assertEquals("Fixture Sudtirol - 2024-01-05", info.title)
        assertEquals("20240105", info.uploadDate)
        assertEquals("m3u8_native", info.formats.single().protocol)
        assertEquals("https://img.example/sudtirol.jpg", info.thumbnails.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun raiPlayIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val base = "https://www.raiplay.it/video/2014/04/fixture-$uuid"
        val case = ExtractorCase(
            url = "$base.html",
            infoDict = mapOf(
                "id" to Expect.Value(uuid),
                "title" to Expect.Value("Fixture RaiPlay Title"),
                "duration" to Expect.Value(330.0),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "$base.json",
                    contentType = "application/json",
                    body = """
                        {"id": "ContentItem-$uuid", "name": "Fixture RaiPlay Title",
                         "video": {"content_url": "https://relinker.example/media"}}
                    """.trimIndent(),
                ),
                FixtureRoute(
                    urlPattern = "https://relinker.example/media?output=64",
                    contentType = "application/xml",
                    body = relinkerXml("https://media.example/hls/master.m3u8"),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RaiPlayIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun raiPlaySoundIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val base = "https://www.raiplaysound.it/audio/2024/01/fixture-$uuid"
        val case = ExtractorCase(
            url = "$base.html",
            infoDict = mapOf(
                "id" to Expect.Value(uuid),
                "title" to Expect.Value("Fixture Sound Title"),
                "formats.0.ext" to Expect.Value("mp3"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "$base.json",
                    contentType = "application/json",
                    body = """
                        {"uniquename": "ContentItem-$uuid", "title": "Fixture Sound Title",
                         "downloadable_audio": {"url": "https://relinker.example/audio"}}
                    """.trimIndent(),
                ),
                FixtureRoute(
                    urlPattern = "https://relinker.example/audio?output=64",
                    contentType = "application/xml",
                    body = relinkerXml("https://media.example/audio.mp3"),
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RaiPlaySoundIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
