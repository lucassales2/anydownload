package com.anydownlod.core.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchUrlValidatorTest {
    @Test
    fun blankLinesAreDroppedAndValidLinesAreKept() {
        val result = BatchUrlValidator.validate(
            "\n  https://example.com/watch?v=1  \n\nhttp://example.com/a\n   \n"
        )

        assertEquals(
            listOf("https://example.com/watch?v=1", "http://example.com/a"),
            result.validUrls,
        )
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun oneBadLineIsReportedWithoutDiscardingTheGoodOnes() {
        val result = BatchUrlValidator.validate(
            """
                https://example.com/watch?v=1
                not-a-url
                https://example.com/watch?v=2
            """.trimIndent()
        )

        assertEquals(
            listOf("https://example.com/watch?v=1", "https://example.com/watch?v=2"),
            result.validUrls,
        )
        assertEquals(1, result.invalid.size)
        assertEquals("not-a-url", result.invalid.single().raw)
        assertTrue(result.invalid.single().reason.isNotBlank())
        assertTrue(result.hasInvalid)
    }

    @Test
    fun aMultiLineStringIsNeverOneUrl() {
        val result = BatchUrlValidator.validate("https://example.com/a https://example.com/b")

        assertTrue(result.validUrls.isEmpty())
        assertEquals(1, result.invalid.size)
    }

    @Test
    fun carriageReturnsAreHandled() {
        val result = BatchUrlValidator.validate("https://example.com/a\r\nhttps://example.com/b\r\n")

        assertEquals(
            listOf("https://example.com/a", "https://example.com/b"),
            result.validUrls,
        )
    }

    @Test
    fun blankInputProducesNoEntries() {
        val result = BatchUrlValidator.validate("   \n  \n")

        assertTrue(result.isEmpty)
        assertTrue(result.validUrls.isEmpty())
        assertTrue(result.invalid.isEmpty())
    }
}
