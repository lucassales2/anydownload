package com.anydownlod.core.validation

import com.anydownlod.core.di.productionExtractorRegistry
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.GenericIE
import com.anydownlod.core.jsc.NoJsRuntime
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupportedUrlValidatorTest {

    private val videoId = "YE7VzlLtp-4"
    private val statusId = "1234567890123456789"

    private val validator = SupportedUrlValidator(
        productionExtractorRegistry(ExtractorHttp(UnusedTransfer), NoJsRuntime),
    )

    @Test
    fun youtubeWatchFormsAreSupported() {
        val urls = listOf(
            "https://www.youtube.com/watch?v=$videoId",
            "https://youtube.com/watch?v=$videoId&t=10s",
            "https://youtu.be/$videoId",
            "https://youtu.be/$videoId?t=30",
            "https://www.youtube.com/shorts/$videoId",
            "https://www.youtube.com/embed/$videoId",
            "https://www.youtube.com/live/$videoId",
            "https://www.youtube-nocookie.com/embed/$videoId",
            "https://music.youtube.com/watch?v=$videoId",
            "https://m.youtube.com/watch?v=$videoId",
            "  https://www.youtube.com/watch?v=$videoId  ",
            "https://www.youtube.com/watch?v=$videoId&list=PLfixture",
        )
        urls.forEach { assertTrue(validator.isSupported(it), "must be supported: $it") }
    }

    @Test
    fun youtubePlaylistIsSupported() {
        assertTrue(validator.isSupported("https://www.youtube.com/playlist?list=PLfixture"))
    }

    @Test
    fun youtubeShapesTheExtractorsRejectAreNotSupported() {
        val urls = listOf(
            "https://www.youtube.com/channel/UCfixture",
            "https://www.youtube.com/@fixture",
            "https://www.youtube.com/results?search_query=fixture",
            "https://www.youtube.com/watch?v=tooshort",
            "https://www.youtube.com/watch?v=YE7V zlLtp-4",
        )
        urls.forEach { assertFalse(validator.isSupported(it), "must stay unsupported: $it") }
    }

    @Test
    fun twitterStatusFormsAreSupported() {
        val urls = listOf(
            "https://x.com/fixture/status/$statusId",
            "https://twitter.com/fixture/status/$statusId",
            "https://mobile.x.com/fixture/status/$statusId",
            "https://www.x.com/fixture/status/$statusId",
            "https://x.com/i/web/status/$statusId",
            "https://twitter.com/statuses/$statusId",
        )
        urls.forEach { assertTrue(validator.isSupported(it), "must be supported: $it") }
    }

    @Test
    fun twitterNonStatusUrlsAreNotSupported() {
        val urls = listOf(
            "https://x.com/fixture",
            "https://t.co/abcdef",
            "https://x.com/i/spaces/1YqKDgLqOKqJV",
            "https://x.com/fixture/status/$statusId/photo/1",
        )
        urls.forEach { assertFalse(validator.isSupported(it), "must stay unsupported: $it") }
    }

    @Test
    fun unknownDirectFileAndSpotifyAreNotSupported() {
        assertFalse(validator.isSupported("https://example.com/page"))
        assertFalse(validator.isSupported("https://cdn.example/a.mp4"))
        assertFalse(validator.isSupported("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"))
        assertFalse(validator.isSupported("spotify:track:4uLU6hMCjMI75M1A2tKUQC"))
    }

    @Test
    fun blankAndNonHttpSchemesAreNotSupported() {
        assertFalse(validator.isSupported(""))
        assertFalse(validator.isSupported("   "))
        assertFalse(validator.isSupported("ftp://example.org/file"))
        assertFalse(validator.isSupported("javascript:alert(1)"))
    }

    @Test
    fun genericFallbackDoesNotCountAsSupported() {
        val registry = ExtractorRegistry(listOf(GenericIE(ExtractorHttp(UnusedTransfer))))
        val genericOnly = SupportedUrlValidator(registry)
        assertFalse(genericOnly.isSupported("https://unknown.example/watch"))
    }

    private object UnusedTransfer : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("unused")
    }
}
