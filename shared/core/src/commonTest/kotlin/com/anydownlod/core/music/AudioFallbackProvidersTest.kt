package com.anydownlod.core.music

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.harness.FixtureHttpTransfer
import com.anydownlod.core.extract.harness.FixtureRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four opt-in fallback providers over synthesized public API shapes.
 * Every URL is a public fixture host; no live scrape and no Spotify URL.
 */
class AudioFallbackProvidersTest {

    private val query = "Fixture Artist - Fixture Song"

    private fun route(pattern: String, body: String, contentType: String = "application/json") = FixtureRoute(
        urlPattern = pattern,
        contentType = contentType,
        body = body,
    )

    // ------------------------------------------------------------- soundcloud

    private val soundcloudPage = """
        <html><body><script>window.__sc_hydration = [
        {"hydratable":"anonymousId","data":"fixture"},
        {"hydratable":"apiClient","data":{"id":"fixture-client-id","isExpiring":false}}
        ];</script></body></html>
    """.trimIndent()

    private val soundcloudSearch = """
        {"collection":[
        {"id":1,"title":"Fixture Song","permalink_url":"https://soundcloud.com/fixture-artist/fixture-song",
        "duration":214000,"playback_count":100,"user":{"username":"Fixture Artist","verified":true}},
        {"id":2,"title":"Fixture Preview","permalink_url":"https://soundcloud.com/fixture-artist/preview/fixture",
        "duration":30000,"playback_count":5,"user":{"username":"Someone","verified":false}}
        ]}
    """.trimIndent()

    @Test
    fun soundcloudParsesTheClientIdAndTracks() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                route("https://soundcloud.com/search*", soundcloudPage, "text/html"),
                route("https://api-v2.soundcloud.com/search/tracks*", soundcloudSearch),
            ),
        )
        val provider = SoundcloudAudioProvider(ExtractorHttp(transfer))

        val results = provider.search(query)

        assertEquals(1, results.size, "the /preview/ track must be dropped")
        val first = results.single()
        assertEquals(AudioSource.SOUNDCLOUD, first.source)
        assertEquals("https://soundcloud.com/fixture-artist/fixture-song", first.url)
        assertEquals("Fixture Song", first.title)
        assertEquals(listOf("Fixture Artist"), first.artists)
        assertEquals(214.0, first.durationSeconds)
        assertEquals(true, first.verified)
        assertEquals(100L, first.viewCount)
        assertTrue(
            transfer.requests.any { it.url.contains("client_id=fixture-client-id") },
            "the public web client id rides in the API URL",
        )
    }

    @Test
    fun soundcloudWithoutAHydrationClientIdFailsTyped() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(route("https://soundcloud.com/search*", "<html></html>", "text/html")),
        )
        val provider = SoundcloudAudioProvider(ExtractorHttp(transfer))
        assertFailsWith<ExtractionError.Malformed> { provider.search(query) }
    }

    // --------------------------------------------------------------- bandcamp

    private val bandcampSearch = """
        {"results":[
        {"type":"t","band_id":111,"id":222,"name":"Fixture Song"},
        {"type":"b","band_id":333,"id":444,"name":"Fixture Artist"}
        ]}
    """.trimIndent()

    private val bandcampDetails = """
        {"id":222,"title":"Fixture Song","bandcamp_url":"https://fixture.bandcamp.com/track/fixture-song",
        "album_title":"Fixture Album","band":{"name":"Fixture Artist"},
        "tracks":[{"track_num":1,"duration":214.0,"is_streamable":true}]}
    """.trimIndent()

    @Test
    fun bandcampResolvesTrackDetails() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                route("https://bandcamp.com/api/fuzzysearch/*", bandcampSearch),
                route("https://bandcamp.com/api/mobile/*", bandcampDetails),
            ),
        )
        val provider = BandcampAudioProvider(ExtractorHttp(transfer))

        val result = provider.search(query).single()

        assertEquals(AudioSource.BANDCAMP, result.source)
        assertEquals("https://fixture.bandcamp.com/track/fixture-song", result.url)
        assertEquals("Fixture Song", result.title)
        assertEquals(listOf("Fixture Artist"), result.artists)
        assertEquals(214.0, result.durationSeconds)
        assertEquals("Fixture Album", result.album)
    }

    // ------------------------------------------------------------------ piped

    private val pipedSearch = """
        {"items":[
        {"url":"/watch?v=fixtureVideo1","title":"Fixture Song","uploaderName":"Fixture Artist","duration":214,"views":1000},
        {"url":"https://piped.video/watch?v=fixtureVideo2","title":"Fixture Other","uploaderName":"Other","duration":200,"views":5}
        ]}
    """.trimIndent()

    @Test
    fun pipedNormalizesResultsToYouTubeUrls() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(route("https://api.piped.private.coffee/search*", pipedSearch)),
        )
        val provider = PipedAudioProvider(ExtractorHttp(transfer))

        val results = provider.search(query)

        assertEquals(2, results.size)
        assertEquals("https://www.youtube.com/watch?v=fixtureVideo1", results[0].url)
        assertEquals("Fixture Song", results[0].title)
        assertEquals(214.0, results[0].durationSeconds)
        assertEquals("https://www.youtube.com/watch?v=fixtureVideo2", results[1].url)
        assertTrue(results.all { it.url.startsWith("https://www.youtube.com/") })
    }

    @Test
    fun pipedUrlNormalizationRejectsUnknownShapes() {
        assertNull(pipedToYoutubeUrl("https://example.org/audio.mp3"))
        assertEquals(
            "https://www.youtube.com/watch?v=abc",
            pipedToYoutubeUrl("https://piped.video/watch?v=abc&list=x"),
        )
    }

    // -------------------------------------------------------------- slider.kz

    private val sliderSearch = """
        {"audios":{"":[{"id":"1","url":"/files/fixture.mp3","tit_art":"Fixture Song","duration":"214"}]}}
    """.trimIndent()

    @Test
    fun sliderKzParsesDirectAudioUrls() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(route("https://hayqbhgr.slider.kz/vk_auth.php*", sliderSearch)),
        )
        val provider = SliderKzAudioProvider(ExtractorHttp(transfer))

        val result = provider.search(query).single()

        assertEquals(AudioSource.SLIDER_KZ, result.source)
        assertEquals("https://hayqbhgr.slider.kz/files/fixture.mp3", result.url)
        assertEquals("Fixture Song", result.title)
        assertEquals(214.0, result.durationSeconds)
    }

    // ---------------------------------------------------------------- factory

    @Test
    fun theFactoryOnlyBuildsEnabledFallbacksInOrder() {
        val http = ExtractorHttp(FixtureHttpTransfer(emptyList()))

        assertTrue(AudioProviders.fallbacks(http, emptyList()).isEmpty())
        assertEquals(
            listOf(AudioSource.SOUNDCLOUD, AudioSource.BANDCAMP, AudioSource.SLIDER_KZ),
            AudioProviders.fallbacks(http, listOf("slider.kz", "soundcloud", "bandcamp"))
                .map { it.source },
        )
        assertEquals(
            listOf(AudioSource.PIPED),
            AudioProviders.fallbacks(http, listOf("PIPED")).map { it.source },
        )
    }

    @Test
    fun noFallbackProviderEverReturnsASpotifyUrl() = runTest {
        val transfer = FixtureHttpTransfer(
            listOf(
                route("https://soundcloud.com/search*", soundcloudPage, "text/html"),
                route("https://api-v2.soundcloud.com/search/tracks*", soundcloudSearch),
                route("https://bandcamp.com/api/fuzzysearch/*", bandcampSearch),
                route("https://bandcamp.com/api/mobile/*", bandcampDetails),
                route("https://api.piped.private.coffee/search*", pipedSearch),
                route("https://hayqbhgr.slider.kz/vk_auth.php*", sliderSearch),
            ),
        )
        val http = ExtractorHttp(transfer)
        val all = AudioProviders.fallbacks(
            http,
            listOf("soundcloud", "bandcamp", "piped", "slider.kz"),
        ).flatMap { provider -> provider.search(query) }

        assertTrue(all.isNotEmpty())
        assertTrue(all.none { it.url.contains("spotify") || it.url.contains("scdn.co") })
    }
}
