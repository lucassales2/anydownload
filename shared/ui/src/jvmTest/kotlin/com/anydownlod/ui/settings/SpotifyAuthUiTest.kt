package com.anydownlod.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.anydownlod.core.fake.InMemoryAppGraph
import com.anydownlod.core.music.InMemorySpotifyTokenStore
import com.anydownlod.core.music.SpotifyAuthService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T-092 Settings login: a token is stored on the device and Logout deletes it.
 * In-memory fakes only; no real Spotify login and no token in a file.
 */
@OptIn(ExperimentalTestApi::class)
class SpotifyAuthUiTest {

    @Test
    fun loginStoresTheTokenAndLogoutClearsIt() = runComposeUiTest {
        val store = InMemorySpotifyTokenStore()
        val auth = SpotifyAuthService(store)
        val graph = InMemoryAppGraph(spotifyAuth = auth)
        setContent { SettingsScreen(graph = graph, onClose = {}) }

        onNodeWithText(
            "Not logged in. Saved songs, your playlists, saved albums, and followed artists " +
                "need a Spotify login; public track links do not.",
        ).assertExists()
        assertNull(store.load())

        onNodeWithTag("spotify-token-field").performTextInput("fixture-token")
        onNodeWithTag("spotify-login").assertIsEnabled().performClick()
        assertEquals("fixture-token", store.load())
        onNodeWithText(
            "Logged in to Spotify. Liked songs, playlists, albums, and followed artists can be used.",
        ).assertExists()

        onNodeWithTag("spotify-logout").performClick()
        assertNull(store.load())
        onNodeWithText(
            "Not logged in. Saved songs, your playlists, saved albums, and followed artists " +
                "need a Spotify login; public track links do not.",
        ).assertExists()
    }
}
