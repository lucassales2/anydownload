package com.anydownlod.core.extract

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Helper cases translated from yt-dlp's own tests
 * (`test/test_utils.py`) and from the helper behavior in
 * `yt_dlp/utils/_utils.py` at tag `2026.08.19`; see the notice in
 * `ExtractorUtils.kt`.
 */
class ExtractorUtilsTest {

    @Test
    fun searchRegexReturnsTheRequestedGroupOrDefault() {
        assertEquals("123", ExtractorUtils.searchRegex("([a-z]+)(\\d+)", "abc123", group = 2))
        assertEquals("abc", ExtractorUtils.searchRegex("([a-z]+)(\\d+)", "abc123"))
        assertNull(ExtractorUtils.searchRegex("([a-z]+)(\\d+)", "abc123", group = 9))
        assertNull(ExtractorUtils.searchRegex("nope", "abc123"))
        assertEquals("fallback", ExtractorUtils.searchRegex("nope", "abc123", default = "fallback"))
        assertEquals("ABC", ExtractorUtils.searchRegex("abc", "ABC", flags = setOf(RegexOption.IGNORE_CASE), group = 0))
    }

    @Test
    fun htmlSearchMetaReadsNameAndPropertyAndUnescapes() {
        val html = """
            <html><head>
            <meta property="og:title" content="A &amp; B">
            <meta name="description" content="Fixture description">
            <meta itemprop="duration" content="PT1M">
            </head></html>
        """.trimIndent()
        assertEquals("A & B", ExtractorUtils.htmlSearchMeta(html, "og:title", "title"))
        assertEquals("Fixture description", ExtractorUtils.htmlSearchMeta(html, "description"))
        assertEquals("PT1M", ExtractorUtils.htmlSearchMeta(html, "duration"))
        assertNull(ExtractorUtils.htmlSearchMeta(html, "missing"))
    }

    @Test
    fun parseJsonIsLenientUnlessFatal() {
        assertEquals(JsonPrimitive(1), (ExtractorUtils.parseJson("""{"a": 1}""") as kotlinx.serialization.json.JsonObject)["a"])
        val wrapped = ExtractorUtils.parseJson("""prefix({"a": 2})suffix""") as kotlinx.serialization.json.JsonObject
        assertEquals(JsonPrimitive(2), wrapped["a"])
        assertNull(ExtractorUtils.parseJson("not json at all"))
        assertNull(ExtractorUtils.parseJson(null))
        assertFailsWith<ExtractionError.Malformed> { ExtractorUtils.parseJson("not json at all", fatal = true) }
    }

    @Test
    fun traverseWalksKeysIndicesValuesAndFilters() {
        val root = ExtractorUtils.parseJson(
            """{"a": {"b": [{"c": 1}, {"c": 2}]}, "d": "text"}""",
        )
        val firstC = ExtractorUtils.traverse(
            root,
            listOf(
                TraverseStep.Key("a"),
                TraverseStep.Key("b"),
                TraverseStep.Index(0),
                TraverseStep.Key("c"),
            ),
        )
        assertEquals(listOf("1"), firstC.map { (it as JsonPrimitive).content })

        val allC = ExtractorUtils.traverse(
            root,
            listOf(
                TraverseStep.Key("a"),
                TraverseStep.Key("b"),
                TraverseStep.Values,
                TraverseStep.Key("c"),
            ),
        )
        assertEquals(listOf("1", "2"), allC.map { (it as JsonPrimitive).content })

        assertEquals(
            1,
            ExtractorUtils.traverse(root, listOf(TraverseStep.Key("a"), TraverseStep.Filter(TraverseFilters.isObject))).size,
        )
        assertEquals(
            2,
            ExtractorUtils.traverse(
                root,
                listOf(
                    TraverseStep.Key("a"),
                    TraverseStep.Key("b"),
                    TraverseStep.Values,
                    TraverseStep.Filter(TraverseFilters.isObject),
                ),
            ).size,
        )
        assertEquals(1, ExtractorUtils.traverse(root, listOf(TraverseStep.Key("d"))).size)
        assertTrue(ExtractorUtils.traverse(root, listOf(TraverseStep.Key("missing"))).isEmpty())
        assertTrue(ExtractorUtils.traverse(null, listOf(TraverseStep.Key("a"))).isEmpty())
    }

    @Test
    fun urlOrNoneAcceptsOnlyAbsoluteUrls() {
        assertEquals("https://example.com/a", ExtractorUtils.urlOrNone(" https://example.com/a "))
        assertEquals("ftp://example.com/file", ExtractorUtils.urlOrNone("ftp://example.com/file"))
        assertNull(ExtractorUtils.urlOrNone("//example.com/a"))
        assertNull(ExtractorUtils.urlOrNone("not a url"))
        assertNull(ExtractorUtils.urlOrNone(""))
        assertNull(ExtractorUtils.urlOrNone(null))
        assertNull(ExtractorUtils.urlOrNone("https://exam ple.com/a"))
    }

    @Test
    fun numbersAndStringsCoerceLikeUpstream() {
        assertEquals(1234L, ExtractorUtils.intOrNone("1234"))
        assertEquals(1234L, ExtractorUtils.intOrNone(1234.5))
        assertEquals(1000L, ExtractorUtils.intOrNone("1e3"))
        assertNull(ExtractorUtils.intOrNone(true))
        assertNull(ExtractorUtils.intOrNone("1,000"))
        assertNull(ExtractorUtils.intOrNone(null))

        assertEquals(1262.9, ExtractorUtils.floatOrNone("1262.9"))
        assertEquals(3.0, ExtractorUtils.floatOrNone(3))
        assertNull(ExtractorUtils.floatOrNone(true))
        assertNull(ExtractorUtils.floatOrNone("1,000"))

        assertEquals("x", ExtractorUtils.strOrNone("x"))
        assertEquals("1", ExtractorUtils.strOrNone(1))
        assertNull(ExtractorUtils.strOrNone(null))
        assertNull(ExtractorUtils.strOrNone(""))
    }

    @Test
    fun unescapeHtmlDecodesEntitiesAndBackslashEscapes() {
        assertEquals("&<>\"'", ExtractorUtils.unescapeHtml("&amp;&lt;&gt;&quot;&#39;"))
        assertEquals("a/b", ExtractorUtils.unescapeHtml("a&#x2F;b"))
        assertEquals("a/b", ExtractorUtils.unescapeHtml("a\\u002Fb"))
        assertEquals("Z", ExtractorUtils.unescapeHtml("&#90;"))
        assertEquals(null, ExtractorUtils.unescapeHtml(null))
    }

    @Test
    fun parseCodecsSplitsVideoAndAudio() {
        val both = ExtractorUtils.parseCodecs("""video/mp4; codecs="avc1.640028, mp4a.40.2"""")
        assertEquals("avc1.640028", both.vcodec)
        assertEquals("mp4a.40.2", both.acodec)

        val audio = ExtractorUtils.parseCodecs("""audio/webm; codecs="opus"""")
        assertNull(audio.vcodec)
        assertEquals("opus", audio.acodec)

        val none = ExtractorUtils.parseCodecs("video/mp4")
        assertNull(none.vcodec)
        assertNull(none.acodec)
        assertNull(ExtractorUtils.parseCodecs(null).vcodec)
    }

    @Test
    fun mimetype2extMapsTheD4Containers() {
        assertEquals("mp4", ExtractorUtils.mimetype2ext("video/mp4"))
        assertEquals("m4a", ExtractorUtils.mimetype2ext("audio/mp4"))
        assertEquals("mp3", ExtractorUtils.mimetype2ext("audio/mpeg"))
        assertEquals("webm", ExtractorUtils.mimetype2ext("video/webm; codecs=\"vp9\""))
        assertEquals("m3u8", ExtractorUtils.mimetype2ext("application/x-mpegURL"))
        assertEquals("mpd", ExtractorUtils.mimetype2ext("application/dash+xml"))
        assertEquals("html", ExtractorUtils.mimetype2ext("text/html; charset=utf-8"))
        assertNull(ExtractorUtils.mimetype2ext(null))
        assertNull(ExtractorUtils.mimetype2ext(""))
    }

    @Test
    fun parseIso8601ReadsDurations() {
        assertEquals(3723L, ExtractorUtils.parseIso8601("PT1H2M3S"))
        assertEquals(93600L, ExtractorUtils.parseIso8601("P1DT2H"))
        assertEquals(45L, ExtractorUtils.parseIso8601("PT45.5S"))
        assertEquals(604800L, ExtractorUtils.parseIso8601("P1W"))
        assertNull(ExtractorUtils.parseIso8601("P"))
        assertNull(ExtractorUtils.parseIso8601("PT"))
        assertNull(ExtractorUtils.parseIso8601("nope"))
        assertNull(ExtractorUtils.parseIso8601(null))
    }

    @Test
    fun unifiedStrdateEmitsCompactDates() {
        assertEquals("20190331", ExtractorUtils.unifiedStrdate("2019-03-31"))
        assertEquals("20190331", ExtractorUtils.unifiedStrdate("2019/03/31"))
        assertEquals("20190331", ExtractorUtils.unifiedStrdate("31/03/2019"))
        assertEquals("20190331", ExtractorUtils.unifiedStrdate("31 March 2019"))
        assertEquals("20190331", ExtractorUtils.unifiedStrdate("March 31, 2019"))
        assertEquals("20190331", ExtractorUtils.unifiedStrdate("Sun, 31 Mar 2019"))
        assertNull(ExtractorUtils.unifiedStrdate("not a date"))
        assertNull(ExtractorUtils.unifiedStrdate(null))
    }

    @Test
    fun jsonBuildersKeepTheTraverseContract() {
        val built = buildJsonObject {
            put("list", buildJsonArray { add(JsonPrimitive("a")) })
        }
        val value = ExtractorUtils.traverse(
            built,
            listOf(TraverseStep.Key("list"), TraverseStep.Index(0), TraverseStep.Filter(TraverseFilters.isString)),
        )
        assertEquals(listOf("a"), value.map { (it as JsonPrimitive).content })
        assertIs<JsonPrimitive>(value.single())
    }
}
