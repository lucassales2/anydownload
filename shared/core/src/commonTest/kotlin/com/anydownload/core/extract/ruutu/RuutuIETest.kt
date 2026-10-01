package com.anydownload.core.extract.ruutu

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
 * Fixture cases for the Ruutu subset. Ids, titles, and media paths are
 * synthesized; media lives on `media.example`, and no cookie, token, or
 * signed URL appears.
 */
class RuutuIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoUrl = "https://www.ruutu.fi/video/2058907"

    private val xml = """
        <Root>
         <Clip>
          <Files>
           <HTTPFile label="720" bitrate="2000" resolution="1280x720">https://media.example/video.mp4</HTTPFile>
           <HTTPFile>NOT-USED</HTTPFile>
           <AudioMediaFile>https://media.example/audio.mp3</AudioMediaFile>
          </Files>
          <DRM/>
          <PassthroughVariables>
            <variable name="ns_st_cds" value="free"/>
            <variable name="themes" value="Urheilu"/>
            <variable name="date_start" value="2015-05-08"/>
            <variable name="series_name" value="Superpesis"/>
            <variable name="season_number" value="5"/>
            <variable name="episode_number" value="17"/>
            <variable name="runtime" value="114"/>
          </PassthroughVariables>
          <Behavior>
            <Program program_name="Fixture Ruutu Title" description="Fixture description"/>
            <Startpicture href="https://media.example/thumb.jpg"/>
          </Behavior>
          <Runtime>114</Runtime>
          <AgeLimit>12</AgeLimit>
         </Clip>
        </Root>
    """.trimIndent()

    private val xmlRoute = FixtureRoute(
        urlPattern = "https://gatling.nelonemedia.fi/media-xml-cache?id=2058907",
        contentType = "text/xml",
        body = xml,
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val extractor = RuutuIE(http(transfer()))
        val cases = listOf(
            videoUrl,
            "http://www.ruutu.fi/video/2057306",
            "http://www.supla.fi/supla/2231370",
            "http://www.supla.fi/audio/2231370",
            "https://static.nelonenmedia.fi/player/misc/embed_player.html?nid=3618790",
        )
        for (url in cases) {
            assertTrue(extractor.suitable(url), "Ruutu must match: $url")
        }
        assertFalse(extractor.suitable("https://www.example.com/video/2058907"))
    }

    // --------------------------------------------------------------- extract

    @Test
    fun xmlYieldsTheDirectAndAudioRows() = runTest {
        val info = RuutuIE(http(transfer(xmlRoute))).extract(videoUrl)
        assertEquals("2058907", info.id)
        assertEquals("Fixture Ruutu Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(114.0, info.duration)
        assertEquals(12, info.ageLimit)
        assertEquals("20150508", info.uploadDate)
        assertEquals("Superpesis", info.channel)
        assertEquals("https://media.example/thumb.jpg", info.thumbnails.single().url)
        assertEquals(2, info.formats.size)
        assertEquals("https-720", info.formats[0].formatId)
        assertEquals(1, info.formats[0].preference)
        assertEquals(1280L, info.formats[0].width)
        assertEquals(720L, info.formats[0].height)
        assertEquals(2000.0, info.formats[0].tbr)
        assertEquals("audio", info.formats[1].formatId)
        assertEquals("none", info.formats[1].vcodec)
        assertEquals("https://media.example/audio.mp3", info.formats[1].url)
    }

    @Test
    fun drmOnlyVideoFailsTyped() = runTest {
        val drmXml = """
            <Root><Clip><Files><File>NOT-USED</File></Files><DRM/></Clip></Root>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://gatling.nelonemedia.fi/media-xml-cache?id=2058907",
                contentType = "text/xml",
                body = drmXml,
            ),
        )
        assertFailsWith<ExtractionError.Unavailable> {
            RuutuIE(http(transfer)).extract(videoUrl)
        }
    }

    @Test
    fun premiumVideoFailsTyped() = runTest {
        val premiumXml = """
            <Root><Clip><Files><File>NOT-USED</File></Files>
            <PassthroughVariables><variable name="ns_st_cds" value="premium"/></PassthroughVariables>
            </Clip></Root>
        """.trimIndent()
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://gatling.nelonemedia.fi/media-xml-cache?id=2058907",
                contentType = "text/xml",
                body = premiumXml,
            ),
        )
        val error = assertFailsWith<ExtractionError.Unavailable> {
            RuutuIE(http(transfer)).extract(videoUrl)
        }
        assertTrue(error.message!!.contains("premium"), error.message)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun videoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value("2058907"),
                "title" to Expect.Value("Fixture Ruutu Title"),
                "upload_date" to Expect.Value("20150508"),
                "age_limit" to Expect.Value(12),
                "formats" to Expect.Count(2),
            ),
            routes = listOf(xmlRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> RuutuIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
