package com.anydownlod.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sorter cases derived from upstream `FormatSorter` behavior and
 * `test/test_YoutubeDL.py`; see the notice in `FormatSorter.kt`.
 */
class FormatSorterTest {

    private fun sortedIdList(formats: List<com.anydownlod.core.extract.MediaFormat>, sort: List<String>): List<String?> =
        FormatSorter.sort(formats, FormatSorter.order(sort)).map { it.formatId }

    @Test
    fun defaultOrderIsDeDuplicatedAndCarriesTheUpstreamFields() {
        val order = FormatSorter.order(emptyList())
        assertEquals(order.map { it.name }.distinct(), order.map { it.name })
        assertTrue(order.any { it.name == "hasvid" })
        assertTrue(order.any { it.name == "vcodec" })
        assertTrue(order.any { it.name == "hasaud" })
        assertTrue(order.any { it.name == "id" })
        val hdr = order.first { it.name == "hdr" }
        assertEquals("12", hdr.limitText)
        assertEquals(7.0, hdr.limit, "upstream resolves hdr:12 to the HDR12 preference")
    }

    @Test
    fun resolutionAndFpsSortAscending() {
        val formats = listOf(
            format(id = "low", height = 360, fps = 30.0, vcodec = "avc1", acodec = "none"),
            format(id = "high", height = 1080, fps = 60.0, vcodec = "avc1", acodec = "none"),
        )
        assertEquals(listOf("low", "high"), sortedIdList(formats, listOf("res")))
        assertEquals(listOf("low", "high"), sortedIdList(formats, listOf("fps")))
    }

    @Test
    fun vcodecAndAcodecOrderedTablesMatchUpstream() {
        val videos = listOf(
            format(id = "h264", vcodec = "avc1.640028", acodec = "none"),
            format(id = "vp9", vcodec = "vp09.00.50.08", acodec = "none"),
            format(id = "vp92", vcodec = "vp09.02.50.10.01.09.18.09.00", acodec = "none"),
            format(id = "av1", vcodec = "av01.0.08M.08", acodec = "none"),
        )
        assertEquals(listOf("h264", "vp9", "vp92", "av1"), sortedIdList(videos, listOf("vcodec")))

        val audios = listOf(
            format(id = "mp3", vcodec = "none", acodec = "mp3"),
            format(id = "aac", vcodec = "none", acodec = "mp4a.40.2"),
            format(id = "opus", vcodec = "none", acodec = "opus"),
            format(id = "flac", vcodec = "none", acodec = "flac"),
        )
        assertEquals(listOf("mp3", "aac", "opus", "flac"), sortedIdList(audios, listOf("acodec")))
    }

    @Test
    fun reverseAndLimitChangeTheOrder() {
        val formats = listOf(
            format(id = "480", height = 480, vcodec = "avc1", acodec = "none"),
            format(id = "1080", height = 1080, vcodec = "avc1", acodec = "none"),
        )
        // `+res` reverses so the highest sorts first, before the selector reverses again.
        assertEquals(listOf("1080", "480"), sortedIdList(formats, listOf("+res")))
        // `res:720` caps: values above the cap sink below in-cap values.
        assertEquals(listOf("1080", "480"), sortedIdList(formats, listOf("res:720")))
        // `res~720` is distance based.
        assertEquals(listOf("1080", "480"), sortedIdList(formats, listOf("res~720")))
    }

    @Test
    fun vp9LimitPrefersTheClosestCodec() {
        val videos = listOf(
            format(id = "av1", vcodec = "av01.0.08M.08", acodec = "none"),
            format(id = "vp92", vcodec = "vp09.02.50.10.01.09.18.09.00", acodec = "none"),
            format(id = "vp9", vcodec = "vp09.00.50.08", acodec = "none"),
            format(id = "h265", vcodec = "h265", acodec = "none"),
        )
        // Upstream: `+vcodec:vp9.2` makes the vp9.2 format the bestvideo pick.
        val ascending = sortedIdList(videos, listOf("+vcodec:vp9.2"))
        assertEquals("vp92", ascending.last(), "vp9.2 must have the highest preference")
    }

    @Test
    fun idAndExtSortLexicallyAndByTable() {
        val ids = listOf(format(id = "10"), format(id = "9"))
        assertEquals(listOf("10", "9"), sortedIdList(ids, listOf("id")))

        val exts = listOf(
            format(id = "webm", ext = "webm", vcodec = "vp9", acodec = "none"),
            format(id = "mp4", ext = "mp4", vcodec = "avc1", acodec = "none"),
        )
        assertEquals(listOf("webm", "mp4"), sortedIdList(exts, listOf("ext")))
    }

    @Test
    fun derivedBitratesFollowTheUpstreamFillRules() {
        val formats = listOf(
            format(id = "audio", vcodec = "none", acodec = "opus", tbr = 100.0),
            format(id = "video", vcodec = "avc1", acodec = "none", tbr = 200.0),
        )
        assertEquals(listOf("audio", "video"), sortedIdList(formats, listOf("br")))
    }

    @Test
    fun missingNumericFieldsSortLowest() {
        val formats = listOf(
            format(id = "unknown", vcodec = "avc1", acodec = "none"),
            format(id = "known", height = 720, vcodec = "avc1", acodec = "none"),
        )
        assertEquals(listOf("unknown", "known"), sortedIdList(formats, listOf("res")))
    }
}
