package com.anydownlod.core.format

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Parser and filter cases translated from `test/test_YoutubeDL.py` and the
 * `build_format_selector` grammar at tag `2026.08.19`; see the notice in
 * `FormatSpec.kt`.
 */
class FormatSpecTest {

    @Test
    fun parsesAtomsAndMergeAndFallback() {
        assertEquals(FormatSpec.Single("best"), FormatSpec.parse("best"))
        assertEquals(FormatSpec.Single("bv*"), FormatSpec.parse("bv*"))

        val merged = assertIs<FormatSpec.Merge>(FormatSpec.parse("137+140"))
        assertEquals(FormatSpec.Single("137"), merged.first)
        assertEquals(FormatSpec.Single("140"), merged.second)

        val fallback = assertIs<FormatSpec.Fallback>(FormatSpec.parse("bv*+ba/b"))
        val merge = assertIs<FormatSpec.Merge>(fallback.first)
        assertEquals(FormatSpec.Single("bv*"), merge.first)
        assertEquals(FormatSpec.Single("ba"), merge.second)
        assertEquals(FormatSpec.Single("b"), fallback.second)
    }

    @Test
    fun chainedMergeIsRightAssociativeAndFallbackRightAssociative() {
        val merge = assertIs<FormatSpec.Merge>(FormatSpec.parse("a+b+c"))
        assertEquals(FormatSpec.Single("a"), merge.first)
        assertEquals(FormatSpec.Single("b"), assertIs<FormatSpec.Merge>(merge.second).first)
        assertEquals(FormatSpec.Single("c"), assertIs<FormatSpec.Merge>(merge.second).second)

        val fallback = assertIs<FormatSpec.Fallback>(FormatSpec.parse("a/b/c"))
        assertEquals(FormatSpec.Single("a"), fallback.first)
        assertEquals(FormatSpec.Single("b"), assertIs<FormatSpec.Fallback>(fallback.second).first)
        assertEquals(FormatSpec.Single("c"), assertIs<FormatSpec.Fallback>(fallback.second).second)
    }

    @Test
    fun commaBuildsAChoiceListAndGroupsNest() {
        val choices = assertIs<FormatSpec.Choices>(FormatSpec.parse("best,worst"))
        assertEquals(listOf(FormatSpec.Single("best"), FormatSpec.Single("worst")), choices.children)

        val group = assertIs<FormatSpec.Group>(FormatSpec.parse("(a,b)"))
        assertEquals(listOf(FormatSpec.Single("a"), FormatSpec.Single("b")), group.children)

        val filteredGroup = assertIs<FormatSpec.Group>(FormatSpec.parse("(a/b)[height>100]"))
        assertEquals(1, filteredGroup.filters.size)
        assertIs<FormatFilter.Numeric>(filteredGroup.filters.single())
    }

    @Test
    fun implicitBestBacksALeadingFilter() {
        val single = assertIs<FormatSpec.Single>(FormatSpec.parse("[height<=720]"))
        assertEquals("best", single.name)
        assertEquals(1, single.filters.size)
    }

    @Test
    fun numericFiltersParseAndMatch() {
        val filter = assertIs<FormatFilter.Numeric>(parseFilter("height<=720"))
        assertEquals("height", filter.key)
        assertEquals(FormatFilter.NumericOp.LE, filter.op)
        assertEquals(720.0, filter.value)
        assertTrue(filter.matches(format(id = "x", height = 480)))
        assertTrue(!filter.matches(format(id = "x", height = 1080)))
    }

    @Test
    fun numericFilterNoneInclusiveAndFilesizeUnits() {
        val filter = assertIs<FormatFilter.Numeric>(parseFilter("filesize<100M"))
        assertEquals(100.0 * 1024 * 1024, filter.value)
        assertTrue(filter.matches(format(id = "x", filesize = 50L * 1024 * 1024)))
        assertTrue(!filter.matches(format(id = "x", filesize = 200L * 1024 * 1024)))
        // Missing filesize fails without `?` and passes with it.
        assertTrue(!filter.matches(format(id = "x")))
        assertTrue(assertIs<FormatFilter.Numeric>(parseFilter("filesize<?100M")).matches(format(id = "x")))

        assertEquals(1024.0, parseFilesize("1K"))
        assertEquals(1024.0 * 1024, parseFilesize("1MiB"))
        assertEquals(512.0, parseFilesize("512B"))
    }

    @Test
    fun stringFiltersParseAndMatch() {
        val equals = assertIs<FormatFilter.Text>(parseFilter("ext=mp4"))
        assertEquals(FormatFilter.StringOp.EQ, equals.op)
        assertTrue(equals.matches(format(id = "x", ext = "mp4")))
        assertTrue(!equals.matches(format(id = "x", ext = "webm")))

        val prefix = assertIs<FormatFilter.Text>(parseFilter("vcodec^=avc1"))
        assertTrue(prefix.matches(format(id = "x", vcodec = "avc1.640028")))
        assertTrue(!prefix.matches(format(id = "x", vcodec = "vp9")))

        val notEquals = assertIs<FormatFilter.Text>(parseFilter("format_id!=abc"))
        assertTrue(notEquals.negated)
        assertTrue(notEquals.matches(format(id = "xyz")))
        assertTrue(!notEquals.matches(format(id = "abc")))

        val regex = assertIs<FormatFilter.Text>(parseFilter("format_id~=\"^dash-.*-high$\""))
        assertEquals(FormatFilter.StringOp.REGEX, regex.op)
        assertTrue(regex.matches(format(id = "dash-video-high")))
        assertTrue(!regex.matches(format(id = "dash-video-low")))

        val quoted = assertIs<FormatFilter.Text>(parseFilter("format_id=\"a b\""))
        assertEquals("a b", quoted.value)
        assertTrue(quoted.matches(format(id = "a b")))
    }

    @Test
    fun malformedSpecsFailTyped() {
        listOf("", "a/", "a+", "a,", "(a", "a)", "a]").forEach { bad ->
            assertFailsWith<FormatSpecException>("expected '$bad' to fail") { FormatSpec.parse(bad) }
        }
        assertFailsWith<FormatSpecException> { parseFilter("height<") }
    }

    @Test
    fun parsedFilterListKeepsSourceOrder() {
        val single = assertIs<FormatSpec.Single>(FormatSpec.parse("best[height<=720][ext=mp4]"))
        assertContentEquals(
            listOf("height", "ext"),
            single.filters.map {
                when (it) {
                    is FormatFilter.Numeric -> it.key
                    is FormatFilter.Text -> it.key
                    else -> error("unknown filter")
                }
            },
        )
    }
}
