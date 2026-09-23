package com.anydownlod.core.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SourceUrlValidatorTest {
    @Test
    fun acceptsHttpAndHttpsWithTrimming() {
        assertEquals(
            SourceUrlValidation.Valid("https://media.example.org/watch?v=1"),
            SourceUrlValidator.validate("  https://media.example.org/watch?v=1  "),
        )
        assertIs<SourceUrlValidation.Valid>(SourceUrlValidator.validate("http://localhost:8080/a"))
    }

    @Test
    fun rejectsBlankInput() {
        assertIs<SourceUrlValidation.Invalid>(SourceUrlValidator.validate("   "))
    }

    @Test
    fun rejectsNonHttpSchemes() {
        assertIs<SourceUrlValidation.Invalid>(SourceUrlValidator.validate("ftp://example.org/file"))
        assertIs<SourceUrlValidation.Invalid>(SourceUrlValidator.validate("file:///etc/passwd"))
        assertIs<SourceUrlValidation.Invalid>(SourceUrlValidator.validate("javascript:alert(1)"))
    }

    @Test
    fun rejectsWhitespaceAndMissingHost() {
        assertIs<SourceUrlValidation.Invalid>(SourceUrlValidator.validate("https://exam ple.org"))
        assertIs<SourceUrlValidation.Invalid>(SourceUrlValidator.validate("https:///nohost"))
    }
}
