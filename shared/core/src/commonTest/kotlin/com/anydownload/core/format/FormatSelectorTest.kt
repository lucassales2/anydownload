package com.anydownload.core.format

import com.anydownload.core.extract.InfoDict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Selection cases translated from `test/test_YoutubeDL.py` `TestFormatSelection`
 * and the `_build_selector_function` semantics at tag `2026.08.19`; see the
 * notice in `FormatSelector.kt`.
 */
class FormatSelectorTest {

    private fun select(formats: List<com.anydownload.core.extract.MediaFormat>, spec: String): Selection =
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
    fun commaListReturnsEveryChildSelection() {
        val formats = listOf(format(id = "a"), format(id = "b"))
        val info = InfoDict(formats = formats)
        // Upstream `,` yields every child; `select` is the one-job view (T-133).
        assertEquals(listOf("a", "b"), FormatSelector.selectAll(info, "a,b").map(::singleId))
        assertEquals(listOf("b"), FormatSelector.selectAll(info, "missing,b").map(::singleId))
        assertEquals("a", singleId(select(formats, "a,b")))
        assertEquals("b", singleId(select(formats, "missing,b")))
    }

    @Test
    fun allSelectsEveryFormatBestFirst() {
        val formats = listOf(
            format(id = "35", ext = "mp4", preference = 0),
            format(id = "example-with-dashes", ext = "webm", preference = 1),
            format(id = "45", ext = "webm", preference = 2),
            format(id = "47", ext = "webm", preference = 3),
            format(id = "2", ext = "flv", preference = 4),
        )
        // test_YoutubeDL.py: test('all', '2', '47', '45', 'example-with-dashes', '35')
        assertEquals(
            listOf("2", "47", "45", "example-with-dashes", "35"),
            FormatSelector.selectAll(InfoDict(formats = formats), "all").map(::singleId),
        )
    }

    @Test
    fun mergeAllFoldsEveryUsableStreamBestFirst() {
        val formats = listOf(
            format(id = "35", ext = "mp4", preference = 0),
            format(id = "example-with-dashes", ext = "webm", preference = 1),
            format(id = "45", ext = "webm", preference = 2),
            format(id = "47", ext = "webm", preference = 3),
            format(id = "2", ext = "flv", preference = 4),
        )
        // test_YoutubeDL.py: test('mergeall', '2+47+45+example-with-dashes+35', multi=True)
        val merged = assertIs<Selection.MergeAll>(
            FormatSelector.selectAll(InfoDict(formats = formats), "mergeall").single(),
        )
        assertEquals(
            listOf("2", "47", "45", "example-with-dashes", "35"),
            merged.formats.map { it.formatId },
        )
    }

    @Test
    fun mergeProducesEveryVideoAudioPair() {
        val formats = listOf(
            format(id = "v-low", vcodec = "avc1", acodec = "none", preference = 0),
            format(id = "v-high", vcodec = "avc1", acodec = "none", preference = 1),
            format(id = "a-low", vcodec = "none", acodec = "opus", preference = 0),
            format(id = "a-high", vcodec = "none", acodec = "opus", preference = 1),
        )
        // Upstream `+` is itertools.product; the left side is the outer loop.
        // Both sides use `all` so each yields several formats, not just the best.
        val spec = "all[vcodec!=none]+all[acodec!=none]"
        val pairs = FormatSelector.selectAll(InfoDict(formats = formats), spec).map {
            val merge = assertIs<Selection.Merge>(it)
            "${merge.video.formatId}+${merge.audio.formatId}"
        }
        assertEquals(listOf("v-high+a-high", "v-high+a-low", "v-low+a-high", "v-low+a-low"), pairs)
    }

    @Test
    fun d4SpecsStillYieldExactlyOneSelection() {
        val formats = listOf(
            format(id = "muxed", vcodec = "avc1", acodec = "opus"),
            format(id = "v", vcodec = "avc1", acodec = "none"),
            format(id = "a", vcodec = "none", acodec = "opus"),
        )
        val info = InfoDict(formats = formats)
        // D4 specs keep the one-selection view the engine consumes.
        assertEquals(1, FormatSelector.selectAll(info, "best").size)
        assertEquals(1, FormatSelector.selectAll(info, "bv*+ba").size)
        assertEquals(0, FormatSelector.selectAll(info, "71").size)
        assertIs<Selection.Single>(select(formats, "best"))
        assertIs<Selection.Merge>(select(formats, "bv*+ba"))
        assertEquals(Selection.None, select(formats, "71"))
    }

    @Test
    fun groupPassesThroughEveryChild() {
        val formats = listOf(
            format(id = "a", height = 480),
            format(id = "b", height = 1080),
        )
        assertEquals(
            listOf("b", "a"),
            FormatSelector.selectAll(InfoDict(formats = formats), "(best,worst)").map(::singleId),
        )
    }

    @Test
    fun allAndMergeAllDropDrmFormats() {
        val formats = listOf(
            format(id = "drm", hasDrm = true, preference = 10),
            format(id = "clear-low", preference = 0),
            format(id = "clear-high", preference = 1),
        )
        val info = InfoDict(formats = formats)
        assertEquals(listOf("clear-high", "clear-low"), FormatSelector.selectAll(info, "all").map(::singleId))
        val merged = assertIs<Selection.MergeAll>(FormatSelector.selectAll(info, "mergeall").single())
        assertEquals(listOf("clear-high", "clear-low"), merged.formats.map { it.formatId })
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
