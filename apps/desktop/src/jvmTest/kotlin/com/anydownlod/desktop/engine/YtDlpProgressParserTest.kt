package com.anydownlod.desktop.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class YtDlpProgressParserTest {

    private fun fixtureLines(): List<String> =
        checkNotNull(javaClass.getResourceAsStream("/yt-dlp-progress-fixtures.txt")) {
            "Missing parser fixture resource"
        }.bufferedReader().use { it.readLines() }

    @Test
    fun parsesTheCheckedInFixtureSequence() {
        val events = fixtureLines().mapNotNull { YtDlpProgressParser.parse(it) }

        assertEquals(7, events.size)
        assertEquals(YtDlpEvent.Title("One public video"), events[0])

        val first = assertIs<YtDlpEvent.Progress>(events[1])
        assertEquals(1_048_576L, first.downloadedBytes)
        // The exact total is NA, so the estimate is used without becoming zero.
        assertEquals(10_485_760L, first.totalBytes)
        assertEquals(524_288.0, first.speedBytesPerSecond)
        assertEquals(18L, first.etaSeconds)

        val finished = assertIs<YtDlpEvent.Progress>(events[3])
        assertEquals(10_485_760L, finished.downloadedBytes)

        val postprocess = assertIs<YtDlpEvent.Progress>(events[4])
        assertEquals(true, postprocess.postprocessing)
        assertEquals(
            YtDlpEvent.FinalFile("/downloads/One public video.mp4"),
            events.single { it is YtDlpEvent.FinalFile },
        )
    }

    @Test
    fun naAndUnknownValuesStayNull() {
        val progress = assertIs<YtDlpEvent.Progress>(
            YtDlpProgressParser.parse("DL|downloading|NA|NA|NA|NA|NA"),
        )

        assertNull(progress.downloadedBytes)
        assertNull(progress.totalBytes)
        assertNull(progress.speedBytesPerSecond)
        assertNull(progress.etaSeconds)
    }

    @Test
    fun anExactTotalBeatsTheEstimate() {
        val progress = assertIs<YtDlpEvent.Progress>(
            YtDlpProgressParser.parse("DL|downloading|100|200|300|NA|NA"),
        )

        assertEquals(200L, progress.totalBytes)
    }

    @Test
    fun unknownLinesAreIgnored() {
        assertNull(YtDlpProgressParser.parse("[youtube] Extracting URL: fixture"))
        assertNull(YtDlpProgressParser.parse(""))
    }
}
