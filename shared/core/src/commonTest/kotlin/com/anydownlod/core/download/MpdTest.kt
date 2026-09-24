/*
 * DASH MPD parser tests — AnyDownload (T-073)
 *
 * Cases translated from `yt_dlp/test/test_InfoExtractor.py` and the MPD
 * fixture shapes in `yt_dlp/extractor/common.py` at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-24.
 * Unlicense; see shared/core/NOTICE.md. All URLs and bytes are synthetic.
 */
package com.anydownlod.core.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MpdTest {

    private val url = "https://media.example/dash/manifest.mpd"

    @Test
    fun segmentTemplateBecomesFragments() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <MPD mediaPresentationDuration="PT30S">
              <Period>
                <AdaptationSet mimeType="video/mp4" codecs="avc1.4d401f">
                  <Representation id="v1" bandwidth="2000000" width="1280" height="720">
                    <SegmentTemplate timescale="1000" duration="10000" startNumber="1"
                      initialization="init-${'$'}RepresentationID${'$'}.mp4"
                      media="seg-${'$'}RepresentationID${'$'}-${'$'}Number${'$'}.m4s"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val result = assertIs<MpdResult.Formats>(Mpd.parse(url, xml))
        assertEquals(1, result.formats.size)
        val format = result.formats[0]
        assertEquals("v1", format.formatId)
        assertEquals("http_dash_segments", format.protocol)
        assertEquals(2000.0, format.tbr)
        assertEquals(1280L, format.width)
        assertEquals("avc1.4d401f", format.vcodec)
        val fragments = format.fragments!!
        assertEquals(4, fragments.size)
        assertEquals("https://media.example/dash/init-v1.mp4", fragments[0].url)
        assertEquals("https://media.example/dash/seg-v1-1.m4s", fragments[1].url)
        assertEquals("https://media.example/dash/seg-v1-3.m4s", fragments[3].url)
    }

    @Test
    fun segmentTimeTemplateUsesTimeline() {
        val xml = """
            <MPD mediaPresentationDuration="PT20S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="v1" bandwidth="1000000">
                    <SegmentTemplate timescale="1000" duration="10000" presentationTimeOffset="5000"
                      media="chunk-${'$'}Time${'$'}.m4s"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val result = assertIs<MpdResult.Formats>(Mpd.parse(url, xml))
        val fragments = result.formats[0].fragments!!
        assertEquals(2, fragments.size)
        assertEquals("https://media.example/dash/chunk-5000.m4s", fragments[0].url)
        assertEquals("https://media.example/dash/chunk-15000.m4s", fragments[1].url)
    }

    @Test
    fun segmentListKeepsRanges() {
        val xml = """
            <MPD mediaPresentationDuration="PT20S">
              <Period>
                <AdaptationSet mimeType="audio/mp4">
                  <Representation id="a1" bandwidth="128000">
                    <SegmentList>
                      <SegmentURL media="a1-0.m4s" mediaRange="0-999"/>
                      <SegmentURL media="a1-1.m4s"/>
                    </SegmentList>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val result = assertIs<MpdResult.Formats>(Mpd.parse(url, xml))
        val fragments = result.formats[0].fragments!!
        assertEquals(2, fragments.size)
        assertEquals(0L, fragments[0].rangeStart)
        assertEquals(999L, fragments[0].rangeEnd)
        assertEquals(null, fragments[1].rangeStart)
    }

    @Test
    fun drmManifestFailsTyped() {
        val xml = """
            <MPD mediaPresentationDuration="PT10S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <ContentProtection schemeIdUri="urn:mpeg:dash:mp4protection:2011"/>
                  <Representation id="v1" bandwidth="1000000">
                    <SegmentTemplate duration="10000" media="seg-${'$'}Number${'$'}.m4s"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val result = assertIs<MpdResult.Failed>(Mpd.parse(url, xml))
        assertTrue(result.reason.contains("DRM"), result.reason)
    }

    @Test
    fun dynamicManifestFailsTyped() {
        val xml = """<MPD type="dynamic" mediaPresentationDuration="PT10S"><Period/></MPD>"""
        val result = assertIs<MpdResult.Failed>(Mpd.parse(url, xml))
        assertTrue(result.reason.contains("Live"), result.reason)
    }
}
