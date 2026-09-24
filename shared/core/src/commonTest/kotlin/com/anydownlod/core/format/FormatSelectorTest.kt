package com.anydownlod.core.format

import com.anydownlod.core.extract.InfoDict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Selection cases translated from `test/test_YoutubeDL.py` `TestFormatSelection`
 * and the `_build_selector_function` semantics at tag `2026.08.19`; see the
 * notice in `FormatSelector.kt`.
 */
class FormatSelectorTest {

    private fun select(formats: List<com.anydownlod.core.extract.MediaFormat>, spec: String): Selection =
        FormatSelector.select(InfoDict(formats = formats), spec)

    private fun singleId(selection: Selection): String? = assertIs<Selection.Single>(selection).format.formatId

    @Test
    fun bestAndWorstFollowTheSortedPreference() {
        val formats = listOf(
            format(id = "35", ext = "mp4", preference = 0),
            format(id = "example-with-dashes", ext = "webm", preference = 1),
            format(id = "45", ext = "webm", preference = 2),
            format(id = "47", ext = "webm", preference = 3),
            format(id = "2", ext = "flv", preference = 4),
        )
        assertEquals("2", singleId(select(formats, "best")))
        assertEquals("2", singleId(select(formats, "b")))
        assertEquals("35", singleId(select(formats, "worst")))
        assertEquals("35", singleId(select(formats, "w")))
    }

    @Test
    fun explicitIdsAndPicksWork() {
        val formats = listOf(
            format(id = "35", ext = "mp4", preference = 0),
            format(id = "47", ext = "webm", preference = 3),
        )
        assertEquals("47", singleId(select(formats, "47")))
        assertEquals(Selection.None, select(formats, "71"))
        assertEquals("35", singleId(select(formats, "worst.1")))
        assertEquals("47", singleId(select(formats, "best.1")))
        assertEquals("35", singleId(select(formats, "best.2")))
    }

    @Test
    fun videoAndAudioOnlyFiltersWork() {
        val formats = listOf(
            format(id = "audio-low", ext = "webm", vcodec = "none", preference = 1),
            format(id = "audio-high", ext = "flv", vcodec = "none", preference = 3),
            format(id = "vid", ext = "mp4", preference = 4),
            format(id = "dash-video-low", ext = "mp4", acodec = "none", preference = 1),
            format(id = "dash-video-high", ext = "mp4", acodec = "none", preference = 2),
        )
        assertEquals("audio-high", singleId(select(formats, "bestaudio")))
        assertEquals("audio-low", singleId(select(formats, "worstaudio")))
        assertEquals("dash-video-high", singleId(select(formats, "bestvideo")))
        assertEquals("dash-video-low", singleId(select(formats, "worstvideo")))
        // `bv*` may take a progressive format; `bv` may not.
        assertEquals("vid", singleId(select(formats, "bv*")))
        assertEquals("dash-video-high", singleId(select(formats, "bv")))
    }

    @Test
    fun fallbackFallsThroughToTheNextChoice() {
        val progressiveOnly = listOf(format(id = "vid", ext = "mp4", preference = 2))
        assertEquals("vid", singleId(select(progressiveOnly, "bv*+ba/b")))
        assertEquals("vid", singleId(select(progressiveOnly, "bestvideo/worst")))
    }

    @Test
    fun filtersNarrowTheCandidates() {
        val formats = listOf(
            format(id = "dash-video-low", ext = "mp4", acodec = "none", height = 480),
            format(id = "dash-video-high", ext = "mp4", acodec = "none", height = 1080),
            format(id = "avc-dot", ext = "mp4", vcodec = "avc1.123456", acodec = "none", height = 720),
        )
        assertEquals(
            "dash-video-low",
            singleId(select(formats, "bestvideo[format_id^=dash][format_id$=low]")),
        )
        assertEquals("avc-dot", singleId(select(formats, "bestvideo[vcodec=avc1.123456]")))
        assertEquals("dash-video-low", singleId(select(formats, "bestvideo[height<=480]")))
        assertEquals("dash-video-high", singleId(select(formats, "bestvideo[format_id!=dash-video-low]")))
    }

    @Test
    fun stringOperatorsMatchUpstreamCases() {
        val formats = listOf(
            format(id = "abc-cba", ext = "mp4"),
            format(id = "zxc-cxz", ext = "webm"),
        )
        assertEquals("abc-cba", singleId(select(formats, "[format_id=abc-cba]")))
        assertEquals("zxc-cxz", singleId(select(formats, "[format_id!=abc-cba]")))
        assertEquals("abc-cba", singleId(select(formats, "[format_id^=abc]")))
        assertEquals("zxc-cxz", singleId(select(formats, "[format_id!^=abc]")))
        assertEquals("abc-cba", singleId(select(formats, "[format_id$=cba]")))
        assertEquals("zxc-cxz", singleId(select(formats, "[format_id!$=cba]")))
        assertEquals("abc-cba", singleId(select(formats, "[format_id*=bc-c]")))
        assertEquals("zxc-cxz", singleId(select(formats, "[format_id~=\"^zxc\"]")))
    }

    @Test
    fun aMergeNodeYieldsMerge() {
        val formats = listOf(
            format(id = "v", vcodec = "avc1", acodec = "none"),
            format(id = "a", vcodec = "none", acodec = "opus"),
        )
        val merged = assertIs<Selection.Merge>(select(formats, "bv*+ba"))
        assertEquals("v", merged.video.formatId)
        assertEquals("a", merged.audio.formatId)
    }

    @Test
    fun incompleteFormatsFallBackForBareBest() {
        val audioOnly = listOf(
            format(id = "audio-low", vcodec = "none", sourcePreference = 0),
            format(id = "audio-high", vcodec = "none", sourcePreference = 1),
        )
        assertEquals("audio-high", singleId(select(audioOnly, "best")))
    }

    @Test
    fun drmFormatsAreNeverSelectable() {
        val formats = listOf(
            format(id = "drm", hasDrm = true, sourcePreference = 10),
            format(id = "clear", sourcePreference = 0),
        )
        assertEquals("clear", singleId(select(formats, "best")))
    }

    @Test
    fun commaListReturnsTheFirstNonEmptySelection() {
        val formats = listOf(format(id = "a"), format(id = "b"))
        // D4 has one job per spec; upstream would return both.
        assertTrue(select(formats, "a,b") is Selection.Single)
        assertEquals("a", singleId(select(formats, "a,b")))
        assertEquals("b", singleId(select(formats, "missing,b")))
    }

    @Test
    fun groupsCarryFiltersToEveryChild() {
        val formats = listOf(
            format(id = "small", height = 480, acodec = "none"),
            format(id = "big", height = 1080, acodec = "none"),
        )
        assertEquals("small", singleId(select(formats, "(bestvideo,worstvideo)[height<=720]")))
    }
}
