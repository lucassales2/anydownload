package com.anydownlod.core.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RelativePathValidatorTest {
    @Test
    fun acceptsNestedRelativePathsAndNormalizesSeparators() {
        assertEquals(
            RelativePathValidation.Valid("audio/2026"),
            RelativePathValidator.validate("audio/2026"),
        )
        assertEquals(
            RelativePathValidation.Valid("audio/2026"),
            RelativePathValidator.validate("audio\\2026"),
        )
    }

    @Test
    fun blankMeansTheDownloadRoot() {
        assertEquals(RelativePathValidation.Valid(""), RelativePathValidator.validate("   "))
    }

    @Test
    fun rejectsEmptySegments() {
        assertIs<RelativePathValidation.Invalid>(RelativePathValidator.validate("audio//2026"))
        assertIs<RelativePathValidation.Invalid>(RelativePathValidator.validate("audio/"))
        assertIs<RelativePathValidation.Invalid>(RelativePathValidator.validate("/audio"))
    }

    @Test
    fun rejectsDotSegments() {
        assertIs<RelativePathValidation.Invalid>(RelativePathValidator.validate("./audio"))
        assertIs<RelativePathValidation.Invalid>(RelativePathValidator.validate("audio/../video"))
        assertIs<RelativePathValidation.Invalid>(RelativePathValidator.validate(".."))
    }

    @Test
    fun rejectsAbsolutePaths() {
        assertIs<RelativePathValidation.Invalid>(RelativePathValidator.validate("/Users/example/Downloads"))
        assertIs<RelativePathValidation.Invalid>(RelativePathValidator.validate("C:\\Users\\example"))
        assertIs<RelativePathValidation.Invalid>(RelativePathValidator.validate("C:/Users/example"))
        assertIs<RelativePathValidation.Invalid>(RelativePathValidator.validate("\\\\server\\share"))
    }
}
