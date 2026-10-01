package com.anydownload.core.extract

import com.anydownload.core.platform.HttpRequest
import com.anydownload.core.platform.HttpResponse
import com.anydownload.core.platform.HttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractorRegistryTest {

    private class FakeIE(
        key: String,
        pattern: String? = null,
        private val redirect: String? = null,
    ) : InfoExtractor(
        ieKey = key,
        http = ExtractorHttp(UnusedTransfer),
        validUrl = pattern?.let { Regex(it) },
    ) {
        override suspend fun extract(url: String): InfoDict =
            InfoDict(id = url, extractorKey = ieKey, redirectUrl = redirect)
    }

    private object UnusedTransfer : HttpTransfer {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("unused")
    }

    @Test
    fun picksTheFirstSuitableExtractor() {
        val registry = ExtractorRegistry(
            listOf(
                FakeIE("Site", """https?://site\.example/"""),
                FakeIE("Other", """https?://other\.example/"""),
                FakeIE(ExtractorRegistry.GENERIC_KEY, """https?://"""),
            ),
        )
        assertEquals("Site", registry.suitableFor("https://site.example/watch")?.ieKey)
        assertEquals("Other", registry.suitableFor("https://other.example/watch")?.ieKey)
        assertEquals(ExtractorRegistry.GENERIC_KEY, registry.suitableFor("https://unknown.example/watch")?.ieKey)
        assertNull(registry.suitableFor("mailto:someone@example.com"))
    }

    @Test
    fun unmatchedUrlFailsTyped() = runTest {
        val registry = ExtractorRegistry(listOf(FakeIE(ExtractorRegistry.GENERIC_KEY, """https?://""")))
        assertFailsWith<ExtractionError.UnsupportedUrl> {
            registry.extract("mailto:someone@example.com")
        }
    }

    @Test
    fun genericIsRequiredToBeLast() {
        assertFailsWith<IllegalArgumentException> {
            ExtractorRegistry(
                listOf(FakeIE(ExtractorRegistry.GENERIC_KEY, """https?://"""), FakeIE("site", """https?://""")),
            )
        }
    }

    @Test
    fun matchIdReadsTheNamedGroup() {
        val ie = FakeIE("Site", """https?://x\.example/v/(?<id>[A-Za-z0-9_-]+)""")
        assertEquals("abc-123", ie.matchId("https://x.example/v/abc-123?t=1"))
        assertNull(ie.matchId("https://x.example/other"))
    }

    @Test
    fun redirectResultIsRedispatchedThroughTheRegistry() = runTest {
        val registry = ExtractorRegistry(
            listOf(
                FakeIE("Short", """https?://short\.example/""", redirect = "https://site.example/watch"),
                FakeIE("Site", """https?://site\.example/"""),
            ),
        )
        val info = registry.extract("https://short.example/x")
        assertEquals("https://site.example/watch", info.id)
        assertEquals("Site", info.extractorKey)
    }

    @Test
    fun aRedirectLoopFailsTypedAtTheBound() = runTest {
        val registry = ExtractorRegistry(
            listOf(FakeIE("Loop", """https?://loop\.example/""", redirect = "https://loop.example/again")),
        )
        val error = assertFailsWith<ExtractionError.Malformed> { registry.extract("https://loop.example/x") }
        assertTrue(error.message!!.contains("redirect"))
    }
}
