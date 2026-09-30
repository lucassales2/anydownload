package com.anydownlod.core.postprocess

import com.anydownlod.core.domain.CaptionFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T-015 caption conversion: json3/srv1-3/ttml/vtt sources to SRT, VTT, TTML,
 * and TXT. All bodies are synthetic; Unicode text is covered.
 */
class CaptionConverterTest {

    private val json3 = """
        {"events":[
          {"tStartMs":0,"dDurationMs":2000,"segs":[{"utf8":"Olá, "},{"utf8":"mundo"}]},
          {"tStartMs":2000,"dDurationMs":1500,"segs":[{"utf8":"Ação & café"}]}
        ]}
    """.trimIndent()

    @Test
    fun json3ConvertsToSrtWithUnicode() {
        val srt = CaptionConverter.convert("json3", CaptionFormat.SRT, json3)

        assertEquals(
            "1\n00:00:00,000 --> 00:00:02,000\nOlá, mundo\n\n" +
                "2\n00:00:02,000 --> 00:00:03,500\nAção & café\n\n",
            srt,
        )
    }

    @Test
    fun json3ConvertsToVtt() {
        val vtt = CaptionConverter.convert("json3", CaptionFormat.VTT, json3)!!

        assertTrue(vtt.startsWith("WEBVTT\n\n"))
        assertTrue(vtt.contains("00:00:00.000 --> 00:00:02.000\nOlá, mundo"))
    }

    @Test
    fun json3ConvertsToTxtWithoutTimestamps() {
        assertEquals("Olá, mundo\nAção & café\n", CaptionConverter.convert("json3", CaptionFormat.TXT, json3))
    }

    @Test
    fun srv3ConvertsToSrt() {
        val srv3 = """
            <?xml version="1.0" encoding="utf-8" ?><timedtext>
              <body><p t="1000" d="2000"><s>Hello</s> <s>world</s></p>
              <p t="3000" d="500">Second</p></body>
            </timedtext>
        """.trimIndent()

        val srt = CaptionConverter.convert("srv3", CaptionFormat.SRT, srv3)!!

        assertTrue(srt.contains("00:00:01,000 --> 00:00:03,000\nHello world"))
        assertTrue(srt.contains("00:00:03,000 --> 00:00:03,500\nSecond"))
    }

    @Test
    fun srv1SecondsConvertToMillis() {
        val srv1 = """<timedtext><body><text start="1.5" dur="2.5">Hi</text></body></timedtext>"""

        val vtt = CaptionConverter.convert("srv1", CaptionFormat.VTT, srv1)!!

        assertTrue(vtt.contains("00:00:01.500 --> 00:00:04.000\nHi"))
    }

    @Test
    fun ttmlConvertsToTxtAndKeepsLineBreaks() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
              <p begin="00:00:01.000" end="00:00:03.000">Hello<br/>world</p>
            </div></body></tt>
        """.trimIndent()

        assertEquals("Hello world\n", CaptionConverter.convert("ttml", CaptionFormat.TXT, ttml))
        val ttmlOut = CaptionConverter.convert("ttml", CaptionFormat.TTML, ttml)!!
        assertTrue(ttmlOut.contains("<p begin=\"00:00:01.000\" end=\"00:00:03.000\">Hello<br/>world</p>"))
    }

    @Test
    fun vttConvertsToSrt() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:03.000
            First line

            00:00:03.000 --> 00:00:04.500
            Second line
        """.trimIndent()

        val srt = CaptionConverter.convert("vtt", CaptionFormat.SRT, vtt)!!

        assertTrue(srt.contains("1\n00:00:01,000 --> 00:00:03,000\nFirst line"))
        assertTrue(srt.contains("2\n00:00:03,000 --> 00:00:04,500\nSecond line"))
    }

    @Test
    fun malformedInputReturnsNullInsteadOfThrowing() {
        assertNull(CaptionConverter.convert("json3", CaptionFormat.SRT, "not json"))
        assertNull(CaptionConverter.convert("srv3", CaptionFormat.SRT, "<html></html>"))
        assertNull(CaptionConverter.convert("unknown", CaptionFormat.SRT, "whatever"))
    }

    @Test
    fun anEmptyCueListStillRendersAValidFile() {
        val srt = CaptionConverter.render(CaptionFormat.SRT, emptyList())
        val vtt = CaptionConverter.render(CaptionFormat.VTT, emptyList())
        val ttml = CaptionConverter.render(CaptionFormat.TTML, emptyList())

        assertEquals("", srt)
        assertEquals("WEBVTT\n\n", vtt)
        assertTrue(ttml.startsWith("<?xml"))
    }
}
