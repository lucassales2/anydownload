package com.anydownlod.core.extract.theplatform

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit cases for the translated ThePlatform metadata parser and SMIL reader.
 * Every id, host, and address is synthesized (`*.example`); no token, cookie,
 * or signed URL appears.
 */
class ThePlatformBaseIETest {

    /** A concrete subclass so the protected parser can be called. */
    private class FixtureThePlatformIE(http: ExtractorHttp) : ThePlatformBaseIE(
        ieKey = "fixturetheplatform",
        http = http,
    ) {
        fun parse(json: JsonObject?): ThePlatformMetadata = parseTheplatformMetadata(json)

        override suspend fun extract(url: String): com.anydownlod.core.extract.InfoDict =
            throw com.anydownlod.core.extract.ExtractionError.UnsupportedUrl()
    }

    private fun ie(): FixtureThePlatformIE =
        FixtureThePlatformIE(ExtractorHttp(FixtureHttpTransfer(emptyList())))

    private val metadata = """
        {
          "title": "Fixture Episode",
          "description": "Fixture description",
          "defaultThumbnailUrl": "https://img.example/thumb.jpg",
          "duration": 236504,
          "pubDate": 1424246400000,
          "billingCode": "NBCU-COM",
          "ratings": [{"rating": "TV-14"}],
          "captions": [
            {"src": "https://media.example/captions.vtt", "lang": "en", "type": "text/vtt"}
          ],
          "chapters": [
            {"startTime": 0, "endTime": 10000},
            {"startTime": 10000, "endTime": 20000}
          ]
        }
    """.trimIndent()

    @Test
    fun metadataMapsTheModeledFields() {
        val parsed = ie().parse(
            com.anydownlod.core.extract.ExtractorUtils.parseJson(metadata) as JsonObject,
        )
        assertEquals("Fixture Episode", parsed.title)
        assertEquals("Fixture description", parsed.description)
        assertEquals("https://img.example/thumb.jpg", parsed.thumbnailUrl)
        assertEquals(236.504, parsed.durationSeconds)
        assertEquals("20150218", parsed.uploadDate)
        assertEquals("NBCU-COM", parsed.uploader)
        assertEquals(14, parsed.ageLimit)
        assertEquals(2, parsed.chapters.size)
        assertEquals(0.0, parsed.chapters[0].startTime)
        assertEquals(20.0, parsed.chapters[1].endTime)
        assertEquals("en", parsed.subtitles.single().language)
        assertEquals("vtt", parsed.subtitles.single().formats.single().ext)
        assertEquals("https://media.example/captions.vtt", parsed.subtitles.single().formats.single().url)
    }

    @Test
    fun singleChapterWithoutAnEndIsDroppedAndRatingsMap() {
        val single = """{"ratings": [{"rating": "G"}], "chapters": [{"startTime": 0}]}"""
        val parsed = ie().parse(com.anydownlod.core.extract.ExtractorUtils.parseJson(single) as JsonObject)
        assertEquals(0, parsed.ageLimit)
        assertTrue(parsed.chapters.isEmpty(), "a single open-ended chapter is dropped")
    }

    private val smil = """
        <?xml version="1.0" encoding="utf-8"?>
        <smil xmlns="http://www.w3.org/2005/SMIL21/Language">
          <body>
            <switch>
              <video src="https://media.example/hls/master.m3u8" dur="236504ms"
                     type="application/x-mpegURL" width="1280" height="720"/>
              <textstream src="https://media.example/captions.vtt" lang="en" type="text/vtt"/>
            </switch>
          </body>
        </smil>
    """.trimIndent()

    @Test
    fun smilReaderFindsVideosAndTextStreams() {
        val videos = SmilManifest.videos(smil)
        assertEquals(1, videos.size)
        assertEquals("https://media.example/hls/master.m3u8", videos[0].src)
        assertEquals(236504.0, videos[0].durationMs)
        assertEquals(1280L, videos[0].width)
        assertEquals(720L, videos[0].height)
        assertEquals(listOf("https://media.example/hls/master.m3u8"), SmilManifest.videoSources(smil))
        assertEquals(
            Triple("https://media.example/captions.vtt", "en", "text/vtt"),
            SmilManifest.textStreams(smil).single(),
        )
    }

    @Test
    fun smilReaderFindsTheExceptionAndAbstract() {
        val blocked = """
            <smil xmlns="http://www.w3.org/2005/SMIL21/Language">
              <body>
                <ref src="https://media.example/error" abstract="Geo blocked">
                  <param name="exception" value="GeoLocationBlocked"/>
                </ref>
              </body>
            </smil>
        """.trimIndent()
        assertEquals("GeoLocationBlocked", SmilManifest.exceptionValue(blocked))
        assertEquals("Geo blocked", SmilManifest.refAbstract(blocked))
        assertTrue(SmilManifest.videos(blocked).isEmpty())
    }
}
