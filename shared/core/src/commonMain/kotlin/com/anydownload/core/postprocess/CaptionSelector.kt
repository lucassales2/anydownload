/*
 * Caption selection — AnyDownload (T-015)
 *
 * Translation of the `--sub-langs` / manual-vs-automatic preference behavior
 * in `yt_dlp/YoutubeDL.py` (`_write_subtitles`, `select_subtitles`) at
 * upstream tag `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf),
 * read 2026-09-30. Unlicense; see shared/core/NOTICE.md.
 *
 * The port keeps the typed single-language field the add form has: an exact
 * code (`en`, `pt-BR`) or a prefix (`en` matches `en-US`). A requested
 * language with no exact or prefix match falls back to the first available
 * track, and the selection records that fallback so the engine can report it
 * truthfully.
 */
package com.anydownload.core.postprocess

import com.anydownload.core.domain.CaptionPreference
import com.anydownload.core.extract.SubtitleTrack

object CaptionSelector {

    /** The chosen track and whether it is a fallback from the request. */
    data class Selection(
        val track: SubtitleTrack,
        val fallback: Boolean,
    )

    /**
     * Picks a track for [language] (null/blank means any) honoring
     * [preference]. Returns null when the info dict has no tracks at all.
     */
    fun select(
        subtitles: List<SubtitleTrack>,
        automaticCaptions: List<SubtitleTrack>,
        language: String?,
        preference: CaptionPreference?,
    ): Selection? {
        if (subtitles.isEmpty() && automaticCaptions.isEmpty()) return null
        val requested = language?.trim()?.takeIf { it.isNotEmpty() }
        val preferred = when (preference ?: CaptionPreference.MANUAL) {
            CaptionPreference.MANUAL, CaptionPreference.EITHER -> subtitles
            CaptionPreference.AUTOMATIC -> automaticCaptions
        }
        val other = if (preferred === subtitles) automaticCaptions else subtitles

        val preferredMatch = preferred.firstOrNull { matches(it.language, requested) }
        if (preferredMatch != null) return Selection(preferredMatch, fallback = false)

        val otherMatch = other.firstOrNull { matches(it.language, requested) }
        if (otherMatch != null) return Selection(otherMatch, fallback = true)

        // A requested language with no manual or automatic match writes no
        // caption at all; the fallback flag only covers kind preference.
        if (requested != null) return null
        val any = preferred.firstOrNull() ?: other.firstOrNull() ?: return null
        return Selection(any, fallback = false)
    }

    /** Exact code, or a prefix match at a `-` boundary (`en` matches `en-US`). */
    private fun matches(trackLanguage: String, requested: String?): Boolean {
        if (requested == null) return true
        val actual = trackLanguage.trim().lowercase()
        val wanted = requested.lowercase()
        return actual == wanted || actual.startsWith("$wanted-") || wanted.startsWith("$actual-")
    }
}
