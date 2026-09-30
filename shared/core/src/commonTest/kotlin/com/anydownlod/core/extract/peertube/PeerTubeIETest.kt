package com.anydownlod.core.extract.peertube

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.CaseResult
import com.anydownlod.core.extract.harness.Expect
import com.anydownlod.core.extract.harness.ExtractorCase
import com.anydownlod.core.extract.harness.ExtractorTestRun
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import com.anydownlod.core.extract.harness.runCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the PeerTube subset. Every id, host, and address is
 * synthesized (`*.example`); no cookie, bearer token, or signed media URL
 * appears.
 */
class PeerTubeIETest {

    private val uuid = "01234567-89ab-cdef-0123-456789abcdef"
    private val shortUuid = "AbCdEfGhIjKlMnOpQrStUv"

    private val videoJson = """
        {
          "id": "$uuid",
          "name": "Synthetic PeerTube fixture",
          "description": "A short synthetic description.",
          "thumbnailPath": "/lazy-static/thumbnails/abc.jpg",
          "publishedAt": "2026-08-19T12:00:00.000Z",
          "duration": 90,
          "views": 12,
          "nsfw": false,
          "account": {"displayName": "Fixture Account", "id": 7, "url": "https://instance.example/a/fixture"},
          "channel": {"displayName": "Fixture Channel", "id": 3, "url": "https://instance.example/c/fixture"},
          "files": [
            {"fileUrl": "https://instance.example/static/web-videos/fixture-480.mp4",
             "resolution": {"id": 480, "label": "480p"}, "fps": 30, "size": 1000},
            {"fileUrl": "https://instance.example/static/web-videos/fixture-720.mp4",
             "resolution": {"id": 720, "label": "720p"}, "fps": 30, "size": 2000},
            {"fileUrl": "https://instance.example/static/web-videos/fixture-audio.mp4",
             "resolution": {"id": 0, "label": "0p"}, "size": 500}
          ],
          "streamingPlaylists": [
            {"playlistUrl": "https://instance.example/static/streaming-playlists/hls/master.m3u8",
             "files": [{"fileUrl": "https://instance.example/static/streaming-playlists/fixture-1080-frag.mp4",
                        "resolution": {"id": 1080, "label": "1080p"}, "fps": 60, "size": 3000}]}
          ]
        }
    """.trimIndent()

    private val captionsJson = """
        {"data":[{"language":{"id":"en"},"captionPath":"/lazy-static/captions/abc.vtt"}]}
    """.trimIndent()

    private val apiBase = "https://instance.example/api/v1/videos/$uuid"

    private fun routes(
        video: String = videoJson,
        captionsStatus: Int = 200,
        captions: String = captionsJson,
        description: String? = null,
    ): List<FixtureRoute> = buildList {
        add(FixtureRoute(urlPattern = "$apiBase/", body = video))
        if (description != null) {
            add(FixtureRoute(urlPattern = "$apiBase/description", body = description))
        }
        add(FixtureRoute(urlPattern = "$apiBase/captions", statusCode = captionsStatus, body = captions))
    }

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): PeerTubeIE =
        PeerTubeIE(ExtractorHttp(transfer))

    // ------------------------------------------------------------ URL matching

    @Test
    fun videoFormsMatch() {
        val ie = extractor(transfer())
        val urls = listOf(
            "https://instance.example/videos/watch/$uuid",
            "https://instance.example/videos/embed/$uuid",
            "https://instance.example/w/$shortUuid",
            "https://instance.example/api/v1/videos/$uuid",
            "peertube:instance.example:$uuid",
        )
        for (url in urls) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://instance.example/videos/watch/not-a-uuid"))
        assertFalse(ie.suitable("https://instance.example/a/fixture"))
        assertFalse(ie.suitable("https://instance.example/c/fixture/videos"))
        assertFalse(ie.suitable("https://instance.example/w/p/$shortUuid"))
    }

    // ---------------------------------------------------------------- video

    @Test
    fun apiVideoMapsFormatsAndMetadata() = runTest {
        val transfer = transfer(*routes().toTypedArray())
        val info = extractor(transfer).extract("https://instance.example/videos/watch/$uuid")

        assertEquals(uuid, info.id)
        assertEquals("Synthetic PeerTube fixture", info.title)
        assertEquals("A short synthetic description.", info.description)
        assertEquals(90.0, info.duration)
        assertEquals(12L, info.viewCount)
        assertEquals("Fixture Account", info.uploader)
        assertEquals("Fixture Channel", info.channel)
        assertEquals("3", info.channelId)
        assertEquals("20260819", info.uploadDate)
        assertEquals(0, info.ageLimit)
        assertEquals(false, info.isLive)
        assertEquals("https://instance.example/lazy-static/thumbnails/abc.jpg", info.thumbnails.single().url)

        val subtitle = info.subtitles.single()
        assertEquals("en", subtitle.language)
        assertEquals("https://instance.example/lazy-static/captions/abc.vtt", subtitle.formats.single().url)

        assertEquals(5, info.formats.size)
        val hls = info.formats.single { it.formatId == "hls" }
        assertEquals("m3u8_native", hls.protocol)
        assertEquals(
            "https://instance.example/static/streaming-playlists/hls/master.m3u8",
            hls.url,
        )

        val audio = info.formats.single { it.formatId == "0p" }
        assertEquals("none", audio.vcodec)
        assertEquals(500L, audio.filesize)

        val fullHd = info.formats.single { it.formatId == "1080p" }
        assertEquals(1080L, fullHd.height)
        assertEquals(60.0, fullHd.fps)
        assertEquals(3000L, fullHd.filesize)

        val sd = info.formats.single { it.formatId == "480p" }
        assertEquals(480L, sd.height)
        assertEquals(30.0, sd.fps)
    }

    @Test
    fun shortEmbedAndSchemeFormsResolve() = runTest {
        val transfer = transfer(*routes().toTypedArray())
        val byShort = extractor(transfer).extract("https://instance.example/w/$uuid")
        assertEquals(uuid, byShort.id)

        val byEmbed = extractor(transfer).extract("https://instance.example/videos/embed/$uuid")
        assertEquals(uuid, byEmbed.id)

        val byScheme = extractor(transfer).extract("peertube:instance.example:$uuid")
        assertEquals(uuid, byScheme.id)
        assertEquals("https://instance.example/videos/watch/$uuid", byScheme.webpageUrl)
    }

    @Test
    fun liveStreamingPlaylistSetsIsLive() = runTest {
        val live = """
            {
              "id": "$uuid",
              "name": "Synthetic live fixture",
              "streamingPlaylists": [
                {"playlistUrl": "https://instance.example/static/streaming-playlists/hls/live.m3u8", "files": []}
              ]
            }
        """.trimIndent()
        val transfer = transfer(*routes(video = live).toTypedArray())
        val info = extractor(transfer).extract("https://instance.example/videos/watch/$uuid")
        assertEquals(true, info.isLive)
        assertEquals(1, info.formats.size)
        assertEquals("m3u8_native", info.formats.single().protocol)
    }

    @Test
    fun longDescriptionFetchesTheFullText() = runTest {
        val shortened = """
            {
              "id": "$uuid",
              "name": "Synthetic long description",
              "description": "${"x".repeat(260)}",
              "files": []
            }
        """.trimIndent()
        val transfer = transfer(
            *routes(video = shortened, description = """{"description":"Full synthetic description."}""").toTypedArray(),
        )
        val info = extractor(transfer).extract("https://instance.example/videos/watch/$uuid")
        assertEquals("Full synthetic description.", info.description)
    }

    @Test
    fun captionsFailureLeavesSubtitlesEmpty() = runTest {
        val transfer = transfer(*routes(captionsStatus = 404).toTypedArray())
        val info = extractor(transfer).extract("https://instance.example/videos/watch/$uuid")
        assertTrue(info.subtitles.isEmpty())
        assertEquals(5, info.formats.size)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun peerTubeIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = "https://instance.example/videos/watch/$uuid",
            infoDict = mapOf(
                "id" to Expect.Value(uuid),
                "title" to Expect.Value("Synthetic PeerTube fixture"),
                "uploader" to Expect.Value("Fixture Account"),
                "channel" to Expect.Value("Fixture Channel"),
                "upload_date" to Expect.Value("20260819"),
                "duration" to Expect.Value(90L),
                "view_count" to Expect.Value(12L),
                "formats" to Expect.Count(5),
                "formats.0.format_id" to Expect.Value("hls"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = routes(),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> PeerTubeIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun unavailableApiFailsTyped() = runTest {
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "$apiBase/",
                statusCode = 404,
                body = """{"error":"not found"}""",
            ),
        )
        val failure = runCatching {
            extractor(transfer).extract("https://instance.example/videos/watch/$uuid")
        }.exceptionOrNull()
        assertIs<ExtractionError.Unavailable>(failure)
    }
}
