package com.anydownlod.core.postprocess

import com.anydownlod.core.domain.CaptionPreference
import com.anydownlod.core.extract.SubtitleTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-015 caption selection: language, manual/auto preference, and fallback. */
class CaptionSelectorTest {

    private fun track(language: String, automatic: Boolean = false) =
        SubtitleTrack(language = language, automatic = automatic)

    private val manual = listOf(track("en"), track("pt-BR"))
    private val automatic = listOf(track("en", automatic = true), track("de", automatic = true))

    @Test
    fun manualPreferencePicksTheManualTrack() {
        val selection = CaptionSelector.select(manual, automatic, "en", CaptionPreference.MANUAL)!!

        assertEquals("en", selection.track.language)
        assertTrue(!selection.track.automatic)
        assertTrue(!selection.fallback)
    }

    @Test
    fun automaticPreferenceFallsBackToManualWithTheFallbackFlag() {
        val selection = CaptionSelector.select(manual, automatic, "pt", CaptionPreference.AUTOMATIC)!!

        assertEquals("pt-BR", selection.track.language)
        assertTrue(!selection.track.automatic)
        assertTrue(selection.fallback, "a manual track answered an automatic request")
    }

    @Test
    fun automaticPreferencePicksTheAutomaticTrackWhenPresent() {
        val selection = CaptionSelector.select(manual, automatic, "de", CaptionPreference.AUTOMATIC)!!

        assertEquals("de", selection.track.language)
        assertTrue(selection.track.automatic)
        assertTrue(!selection.fallback)
    }

    @Test
    fun aLanguagePrefixMatchesItsRegionalCode() {
        val selection = CaptionSelector.select(manual, automatic, "pt", CaptionPreference.MANUAL)!!

        assertEquals("pt-BR", selection.track.language)
        assertTrue(!selection.fallback)
    }

    @Test
    fun aMissingLanguageReturnsNull() {
        assertNull(CaptionSelector.select(manual, automatic, "ja", CaptionPreference.MANUAL))
    }

    @Test
    fun aBlankLanguageTakesTheFirstPreferredTrackWithoutFallback() {
        val selection = CaptionSelector.select(manual, automatic, null, CaptionPreference.MANUAL)!!

        assertEquals("en", selection.track.language)
        assertTrue(!selection.fallback)
    }

    @Test
    fun noTracksAtAllReturnsNull() {
        assertNull(CaptionSelector.select(emptyList(), emptyList(), "en", CaptionPreference.MANUAL))
    }
}
