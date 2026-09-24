package com.anydownlod.core.extract.harness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Matcher cases translated from yt-dlp's `test/test_download.py`
 * (`expect_info_dict`) and `test/helpers.py` (`expect_value`) at tag
 * `2026.08.19`; see the notice in `ExtractorTestHarness.kt`.
 */
class ExpectTest {

    @Test
    fun valueMatchesWithNumericCoercion() {
        assertNull(Expect.Value(5).check("view_count", 5L))
        assertNull(Expect.Value(5).check("view_count", 5.0))
        assertNull(Expect.Value("a").check("title", "a"))
        assertNull(Expect.Value(null).check("description", null))
        assertNull(Expect.Value(true).check("is_live", true))
        assertNotNull(Expect.Value(5).check("view_count", 6L))
        assertNotNull(Expect.Value("a").check("title", "b"))
    }

    @Test
    fun typeMatchersMirrorUpstream() {
        assertNull(Expect.IntType.check("view_count", 12L))
        assertNull(Expect.IntType.check("view_count", 12))
        assertNotNull(Expect.IntType.check("view_count", 12.5))
        assertNotNull(Expect.IntType.check("view_count", "12"))
        assertNotNull(Expect.IntType.check("view_count", true))

        assertNull(Expect.FloatType.check("duration", 12.5))
        assertNull(Expect.FloatType.check("duration", 12L))
        assertNotNull(Expect.FloatType.check("duration", "12.5"))
        assertNotNull(Expect.FloatType.check("duration", false))

        assertNull(Expect.StringType.check("title", "x"))
        assertNotNull(Expect.StringType.check("title", 1))
    }

    @Test
    fun md5MatchesTheKnownDigest() {
        // RFC 1321 test vectors.
        assertNull(Expect.Md5("900150983cd24fb0d6963f7d28e17f72").check("description", "abc"))
        assertNull(Expect.Md5("d41d8cd98f00b204e9800998ecf8427e").check("description", ""))
        assertNull(Expect.md5("9e107d9d372bb6826bd81d3542a419d6").check("description", "The quick brown fox jumps over the lazy dog"))
        assertNotNull(Expect.Md5("900150983cd24fb0d6963f7d28e17f72").check("description", "abcd"))
        assertNotNull(Expect.Md5("900150983cd24fb0d6963f7d28e17f72").check("description", 1))
    }

    @Test
    fun regexStartsWithAndCountMatchers() {
        val regex = Expect.re("^https://cdn\\.fixtures\\.example\\.net/")
        assertNull(regex.check("url", "https://cdn.fixtures.example.net/a.mp4"))
        assertNotNull(regex.check("url", "https://other.example.net/a.mp4"))
        assertNotNull(regex.check("url", 1))

        assertNull(Expect.startswith("https://").check("url", "https://example.net"))
        assertNotNull(Expect.startswith("https://").check("url", "http://example.net"))

        assertNull(Expect.count(2).check("formats", listOf(1, 2)))
        assertNotNull(Expect.count(2).check("formats", listOf(1)))
        assertNull(Expect.mincount(1).check("formats", listOf(1, 2, 3)))
        assertNotNull(Expect.mincount(2).check("formats", listOf(1)))
        assertNull(Expect.maxcount(2).check("formats", listOf(1, 2)))
        assertNotNull(Expect.maxcount(1).check("formats", listOf(1, 2)))
        assertNotNull(Expect.count(1).check("formats", "not-a-list"))
    }

    @Test
    fun nestedMapsCheckRecursively() {
        val nested = Expect.Nested(
            mapOf(
                "format_id" to Expect.Value("137"),
                "height" to Expect.Value(1080),
            ),
        )
        assertNull(nested.check("formats.0", mapOf("formatId" to "137", "height" to 1080)))
        assertNotNull(nested.check("formats.0", mapOf("formatId" to "140", "height" to 1080)))
        assertNotNull(nested.check("formats.0", "not-a-map"))
    }

    @Test
    fun failureMessagesDoNotCarryUrlsOrLongValues() {
        val url = "https://rr1---sn-real.googlevideo.com/videoplayback?sig=secret-value-here&n=abc"
        val message = Expect.Value("expected").check("url", url)
        assertNotNull(message)
        assertTrue(!message.contains("googlevideo"), message)
        assertTrue(!message.contains("secret-value-here"), message)
    }
}
