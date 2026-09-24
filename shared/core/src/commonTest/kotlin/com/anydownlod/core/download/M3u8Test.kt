/*
 * HLS parser tests — AnyDownload (T-073)
 *
 * Cases translated from `yt_dlp/test/test_InfoExtractor.py` and the m3u8
 * fixture shapes in `yt_dlp/extractor/common.py` at upstream tag `2026.08.19`
 * (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read 2026-09-24.
 * Unlicense; see shared/core/NOTICE.md. All URLs and bytes are synthetic.
 */
package com.anydownlod.core.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class M3u8Test {

    private val base = "https://media.example/playlists/master.m3u8"

    @Test
    fun masterPlaylistBecomesVariants() {
        val text = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",URI="audio.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2",AUDIO="aac"
            low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000,RESOLUTION=1280x720,CODECS="avc1.4d401f"
            high.m3u8
        """.trimIndent()
        val result = assertIs<ManifestResult.Master>(M3u8.parse(base, text))
        assertEquals(2, result.formats.size)
        val low = result.formats[0]
        assertEquals("https://media.example/playlists/low.m3u8", low.url)
        assertEquals("m3u8_native", low.protocol)
        assertEquals(1280.0, low.tbr)
        assertEquals(640L, low.width)
        assertEquals(360L, low.height)
        assertEquals("avc1.4d401e", low.vcodec)
        assertEquals("mp4a.40.2", low.acodec)
        val high = result.formats[1]
        assertEquals("https://media.example/playlists/high.m3u8", high.url)
        assertEquals(2560.0, high.tbr)
        assertEquals(null, high.acodec)
    }

    @Test
    fun mediaPlaylistBecomesFragments() {
        val text = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:5
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x000102030405060708090A0B0C0D0E0F
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
            #EXTINF:9.0,
            seg0.ts
            #EXTINF:9.0,
            seg1.ts
            #EXT-X-BYTERANGE:1024@2048
            #EXTINF:9.0,
            seg2.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val result = assertIs<ManifestResult.Media>(M3u8.parse(base, text))
        assertEquals(3, result.fragments.size)
        assertEquals("https://media.example/playlists/seg0.ts", result.fragments[0].url)
        assertEquals(null, result.fragments[0].rangeStart)
        assertEquals(5L, result.fragments[0].sequence)
        assertEquals(6L, result.fragments[1].sequence)
        assertEquals(2048L, result.fragments[2].rangeStart)
        assertEquals(3071L, result.fragments[2].rangeEnd)
        assertEquals(7L, result.fragments[2].sequence)
        assertEquals("https://media.example/playlists/init.mp4", result.initSegment?.url)
        assertEquals(0L, result.initSegment?.rangeStart)
        assertEquals(719L, result.initSegment?.rangeEnd)
        assertEquals("https://media.example/playlists/key.bin", result.key?.uri)
        assertEquals(16, result.key?.iv?.size)
    }

    @Test
    fun byteRangeWithoutOffsetContinuesFromThePreviousFragment() {
        val text = """
            #EXTM3U
            #EXT-X-BYTERANGE:100@0
            #EXTINF:4.0,
            a.ts
            #EXT-X-BYTERANGE:50
            #EXTINF:4.0,
            b.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val result = assertIs<ManifestResult.Media>(M3u8.parse(base, text))
        assertEquals(0L, result.fragments[0].rangeStart)
        assertEquals(99L, result.fragments[0].rangeEnd)
        assertEquals(100L, result.fragments[1].rangeStart)
        assertEquals(149L, result.fragments[1].rangeEnd)
    }

    @Test
    fun livePlaylistFailsTyped() {
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:9.0,
            seg0.ts
        """.trimIndent()
        val result = assertIs<ManifestResult.Failed>(M3u8.parse(base, text))
        assertTrue(result.reason.contains("Live"), result.reason)
    }

    @Test
    fun unsupportedKeyMethodFailsTyped() {
        val text = """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="key.bin"
            #EXTINF:9.0,
            seg0.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val result = assertIs<ManifestResult.Failed>(M3u8.parse(base, text))
        assertTrue(result.reason.contains("SAMPLE-AES"), result.reason)
    }
}
