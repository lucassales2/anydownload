package com.anydownload.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** T-016 clip parsing: explicit fields win over a URL timestamp. */
class ClipRangeParserTest {

    @Test
    fun explicitStartAndEndParse() {
        val result = assertIs<ClipRangeParser.Result.Ok>(
            ClipRangeParser.parse("00:01:30", "01:02:03.500", null),
        )
        assertEquals(ClipRange(90_000L, 3_723_500L), result.range)
    }

    @Test
    fun plainSecondsAndCompactFormsParse() {
        assertEquals(90_000L, ClipRangeParser.parseMillis("90"))
        assertEquals(90_000L, ClipRangeParser.parseMillis("1:30"))
        assertEquals(90_000L, ClipRangeParser.parseMillis("00:01:30"))
        assertEquals(3_723_500L, ClipRangeParser.parseMillis("01:02:03.5"))
        assertEquals(3_723_000L, ClipRangeParser.parseMillis("1h2m3s"))
    }

    @Test
    fun anExplicitFieldWinsOverTheUrlTimestamp() {
        val result = assertIs<ClipRangeParser.Result.Ok>(
            ClipRangeParser.parse("10", null, "https://youtu.be/abcdefghijk?t=99"),
        )
        assertEquals(ClipRange(10_000L, null), result.range)
    }

    @Test
    fun aUrlTimestampIsTheFallbackStart() {
        val result = assertIs<ClipRangeParser.Result.Ok>(
            ClipRangeParser.parse(null, null, "https://youtu.be/abcdefghijk?t=99s"),
        )
        assertEquals(ClipRange(99_000L, null), result.range)
    }

    @Test
    fun noClipAtAllIsOkWithNull() {
        val result = assertIs<ClipRangeParser.Result.Ok>(
            ClipRangeParser.parse("", "", "https://youtu.be/abcdefghijk"),
        )
        assertNull(result.range)
    }

    @Test
    fun invalidRangesAreRejectedWithAReason() {
        assertIs<ClipRangeParser.Result.Invalid>(ClipRangeParser.parse("nope", null, null))
        assertIs<ClipRangeParser.Result.Invalid>(ClipRangeParser.parse("10", "nope", null))
        assertIs<ClipRangeParser.Result.Invalid>(ClipRangeParser.parse("10", "5", null))
        assertIs<ClipRangeParser.Result.Invalid>(ClipRangeParser.parse("10", "10", null))
    }
}
