package com.anydownlod.core

import com.anydownlod.core.domain.AudioContainer
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.InfoMedia
import com.anydownlod.core.extract.Thumbnail
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import com.anydownlod.core.extract.youtube.YoutubeIE
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The extractor-backed preview and the composite fallback. Fixtures only; the
 * player JSON is synthesized and no media URL is ever requested.
 */
class ExtractorMediaPreviewSourceTest {

    private val videoId = "YE7VzlLtp-4"
    private val watchUrl = "https://www.youtube.com/watch?v=$videoId"
    private val mediaUrl = "https://cdn.fixtures.example.net/clip.mp4"

    private fun transfer(playerJson: String = playerJson()): FixtureHttpTransfer = FixtureHttpTransfer(
        listOf(
            FixtureRoute(
                urlPattern = "https://www.youtube.com/watch*",
                contentType = "text/html",
                body = "<html><head><meta itemprop=\"uploadDate\" content=\"2008-05-29T04:24:26-07:00\"></head></html>",
            ),
            FixtureRoute(
                urlPattern = "https://www.youtube.com/youtubei/v1/player*",
                method = "POST",
                contentType = "application/json",
                body = playerJson,
            ),
        ),
    )

    private fun source(playerJson: String = playerJson(), fixture: FixtureHttpTransfer = transfer(playerJson)): Pair<ExtractorMediaPreviewSource, FixtureHttpTransfer> {
        val registry = ExtractorRegistry(listOf(YoutubeIE(ExtractorHttp(fixture))))
        return ExtractorMediaPreviewSource(registry) to fixture
    }

    @Test
    fun readyPreviewCarriesExtractorFieldsAndChoices() = runTest {
        val (previewSource, fixture) = source()
        val result = assertIs<MediaPreviewResult.Ready>(previewSource.load(watchUrl))
        val preview = result.preview

        assertEquals("Big Buck Bunny", preview.title)
        assertEquals("Fixture Channel", preview.channel)
        assertEquals(596L, preview.durationSeconds)
        assertEquals("2008-05-29", preview.uploadDate)
        assertEquals("Youtube", preview.extractor)
        assertEquals(123_456L, preview.viewCount)
        assertEquals("https://i.example/large.jpg", preview.thumbnailUrl)
        assertEquals(watchUrl, preview.pageUrl)
        assertTrue(preview.videos.isEmpty(), "a YouTube preview has no selectable video list")

        val choices = assertIs<FormatChoices>(preview.availableFormats)
        assertEquals(listOf(720, 360), choices.videoHeights)
        assertEquals(listOf(1080), choices.mergeableVideoHeights)
        assertTrue(choices.hasAudioOnly)
        assertTrue(choices.hasSplitStreams)
        assertEquals(setOf(AudioContainer.M4A, AudioContainer.OPUS), choices.audioContainers)
        assertEquals(2, choices.formatsNeedingJs)

        // The preview only read the watch page and the player endpoint; the
        // selected media URLs were never requested.
        assertTrue(fixture.requests.none { it.url == mediaUrl }, "the preview must not fetch the media")
        assertTrue(fixture.requests.none { it.url.contains("clip.mp4") })
        assertTrue(fixture.requests.any { it.method == "POST" && it.url.contains("/youtubei/v1/player") })
    }

    @Test
    fun anUnmatchedUrlIsUnavailableSoHostsCanFallThrough() = runTest {
        val (previewSource, _) = source()
        val result = assertIs<MediaPreviewResult.Failed>(previewSource.load("https://example.org/article"))
        assertEquals(PreviewFailure.Unavailable, result.failure)
    }

    @Test
    fun aMatchedUrlThatFailsDoesNotReportUnavailable() = runTest {
        val (previewSource, _) = source(playerJson(status = "LOGIN_REQUIRED", reason = "Sign in"))
        val result = assertIs<MediaPreviewResult.Failed>(previewSource.load(watchUrl))
        assertEquals(PreviewFailure.Failed, result.failure)
    }

    @Test
    fun theCompositeFallsThroughOnlyWhenThereIsNoExtractor() = runTest {
        var fallbackCalls = 0
        val fallback = object : MediaPreviewSource {
            override suspend fun load(url: String): MediaPreviewResult {
                fallbackCalls++
                return MediaPreviewResult.Ready(MediaPreview(pageUrl = url, title = "Fallback"))
            }
        }

        val (unmatchedPrimary, _) = source()
        val composed = CompositeMediaPreviewSource(unmatchedPrimary, fallback)
        assertIs<MediaPreviewResult.Ready>(composed.load("https://example.org/article"))
        assertEquals(1, fallbackCalls)

        val (failingPrimary, _) = source(playerJson(status = "LOGIN_REQUIRED", reason = "Sign in"))
        val composed2 = CompositeMediaPreviewSource(failingPrimary, fallback)
        assertIs<MediaPreviewResult.Failed>(composed2.load(watchUrl))
        assertEquals(1, fallbackCalls, "a matched URL must never reach the fallback")
    }

    @Test
    fun formatChoicesDeriveHeightsContainersAndTheJsCount() {
        val choices = FormatChoices.from(
            com.anydownlod.core.extract.InfoDict(
                formats = listOf(
                    format(id = "18", ext = "mp4", vcodec = "avc1", acodec = "mp4a", height = 360),
                    format(id = "22", ext = "mp4", vcodec = "avc1", acodec = "mp4a", height = 720),
                    format(id = "137", ext = "mp4", vcodec = "avc1", acodec = "none", height = 1080),
                    format(id = "140", ext = "m4a", vcodec = "none", acodec = "mp4a.40.2"),
                    format(id = "251", ext = "webm", vcodec = "none", acodec = "opus"),
                ),
                formatsNeedingJs = 3,
            ),
        )
        assertEquals(listOf(720, 360), choices.videoHeights)
        assertEquals(720, choices.bestVideoHeight)
        assertEquals(listOf(1080), choices.mergeableVideoHeights)
        assertTrue(choices.hasAudioOnly)
        assertTrue(choices.hasSplitStreams)
        assertEquals(setOf(AudioContainer.M4A, AudioContainer.OPUS), choices.audioContainers)
        assertEquals(3, choices.formatsNeedingJs)
        assertTrue(choices.hasSingleFileVideo)
    }

    @Test
    fun mediaBecomesSelectableVideosInExtractionOrder() = runTest {
        val transfer = FixtureHttpTransfer(emptyList())
        val fake = object : InfoExtractor(
            ieKey = "Fake",
            http = ExtractorHttp(transfer),
            validUrl = Regex("https://x\\.com/.*"),
        ) {
            override suspend fun extract(url: String): InfoDict = InfoDict(
                id = "1",
                title = "Fixture status",
                thumbnails = emptyList(),
                media = listOf(
                    InfoMedia(
                        mediaId = "333",
                        title = "Fixture status #1",
                        duration = 5.0,
                        thumbnails = listOf(Thumbnail("https://pbs.example/media/first.jpg")),
                        formats = listOf(
                            format(id = "http-256", ext = "mp4", vcodec = null, acodec = null, height = 180),
                        ),
                    ),
                    InfoMedia(
                        mediaId = "444",
                        title = "Fixture status #2",
                        duration = 7.0,
                        thumbnails = listOf(Thumbnail("https://pbs.example/media/second.jpg")),
                        formats = listOf(
                            format(id = "http-832", ext = "mp4", vcodec = null, acodec = null, height = 360),
                        ),
                    ),
                ),
            )
        }
        val source = ExtractorMediaPreviewSource(ExtractorRegistry(listOf(fake)))
        val result = assertIs<MediaPreviewResult.Ready>(source.load("https://x.com/fixture/status/1"))
        val preview = result.preview

        assertEquals(listOf("333", "444"), preview.videos.map { it.mediaId })
        assertEquals(listOf("Fixture status #1", "Fixture status #2"), preview.videos.map { it.title })
        assertEquals(listOf(5L, 7L), preview.videos.map { it.durationSeconds })
        assertEquals("https://pbs.example/media/first.jpg", preview.videos.first().thumbnailUrl)
        // The preview thumbnail falls back to the first video's thumbnail.
        assertEquals("https://pbs.example/media/first.jpg", preview.thumbnailUrl)
        assertEquals(listOf(360, 180), preview.availableFormats?.videoHeights)
        assertTrue(transfer.requests.isEmpty(), "a preview never requests the media")
    }

    @Test
    fun aRegistryWithoutAGenericExtractorStaysUnmatched() {
        val registry = ExtractorRegistry(listOf(YoutubeIE(ExtractorHttp(FixtureHttpTransfer(emptyList())))))
        assertEquals(null, registry.suitableFor("https://example.org/article"))
        assertIs<YoutubeIE>(registry.suitableFor(watchUrl))
    }

    private fun format(
        id: String,
        ext: String?,
        vcodec: String?,
        acodec: String?,
        height: Long? = null,
    ) = com.anydownlod.core.extract.MediaFormat(
        formatId = id,
        url = "https://cdn.fixtures.example.net/$id",
        ext = ext,
        vcodec = vcodec,
        acodec = acodec,
        height = height,
    )

    private fun playerJson(status: String = "OK", reason: String? = null): String {
        val reasonField = if (reason != null) ", \"reason\": \"$reason\"" else ""
        return """
            {
              "playabilityStatus": {"status": "$status"$reasonField},
              "videoDetails": {
                "videoId": "$videoId",
                "title": "Big Buck Bunny",
                "author": "Fixture Channel",
                "channelId": "UCfixturechannel000000000",
                "lengthSeconds": "596",
                "viewCount": "123456",
                "isLiveContent": false,
                "thumbnail": {"thumbnails": [
                  {"url": "https://i.example/large.jpg", "width": 1280, "height": 720},
                  {"url": "https://i.example/small.jpg", "width": 120, "height": 90}
                ]}
              },
              "microformat": {"playerMicroformatRenderer": {"publishDate": "2008-05-29", "isFamilySafe": true}},
              "streamingData": {
                "formats": [
                  {"itag": 18, "url": "$mediaUrl", "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "height": 360, "averageBitrate": 500000},
                  {"itag": 22, "url": "$mediaUrl?itag=22", "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "height": 720, "averageBitrate": 2000000}
                ],
                "adaptiveFormats": [
                  {"itag": 137, "url": "https://cdn.fixtures.example.net/video.mp4", "mimeType": "video/mp4; codecs=\"avc1.640028\"", "height": 1080},
                  {"itag": 140, "url": "https://cdn.fixtures.example.net/audio.m4a", "mimeType": "audio/mp4; codecs=\"mp4a.40.2\""},
                  {"itag": 251, "url": "https://cdn.fixtures.example.net/audio.webm", "mimeType": "audio/webm; codecs=\"opus\""},
                  {"itag": 136, "signatureCipher": "s=REDACTED&url=https%3A%2F%2Fcdn.fixtures.example.net%2Fcipher.mp4", "mimeType": "video/mp4; codecs=\"avc1\""},
                  {"itag": 135, "url": "https://cdn.fixtures.example.net/n.mp4?n=REDACTED", "mimeType": "video/mp4; codecs=\"avc1\""}
                ]
              }
            }
        """.trimIndent()
    }
}
