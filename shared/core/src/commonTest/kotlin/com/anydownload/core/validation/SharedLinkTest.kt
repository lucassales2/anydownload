package com.anydownload.core.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** T-020 shared-link intake: one compatible URL out of shared text. */
class SharedLinkTest {

    @Test
    fun aSingleUrlIsAccepted() {
        val result = assertIs<SharedLinkResult.Accepted>(SharedLink.extract("https://example.com/watch?v=abc"))
        assertEquals("https://example.com/watch?v=abc", result.url)
    }

    @Test
    fun aTitleBeforeTheUrlIsIgnored() {
        val result = assertIs<SharedLinkResult.Accepted>(
            SharedLink.extract("Fixture title https://example.com/watch?v=abc shared from an app"),
        )
        assertEquals("https://example.com/watch?v=abc", result.url)
    }

    @Test
    fun trailingPunctuationIsTrimmed() {
        val result = assertIs<SharedLinkResult.Accepted>(SharedLink.extract("See (https://example.com/watch?v=abc)."))
        assertEquals("https://example.com/watch?v=abc", result.url)
    }

    @Test
    fun emptyTextIsRejected() {
        val result = assertIs<SharedLinkResult.Rejected>(SharedLink.extract("   "))
        assertEquals(SharedLinkReason.EMPTY, result.reason)
    }

    @Test
    fun textWithoutAUrlIsRejected() {
        val result = assertIs<SharedLinkResult.Rejected>(SharedLink.extract("just some words"))
        assertEquals(SharedLinkReason.NO_URL, result.reason)
    }

    @Test
    fun twoUrlsAreRejected() {
        val result = assertIs<SharedLinkResult.Rejected>(
            SharedLink.extract("https://example.com/a https://example.com/b"),
        )
        assertEquals(SharedLinkReason.MULTIPLE_URLS, result.reason)
    }

    @Test
    fun anUnsupportedSchemeIsRejected() {
        val result = assertIs<SharedLinkResult.Rejected>(SharedLink.extract("ftp://example.com/file"))
        assertEquals(SharedLinkReason.NO_URL, result.reason)
    }
}
