package com.anydownlod.core.extract.harness

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.GenericIE
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Harness self-tests: fixture mode with no network, dotted-path matching,
 * missing-fixture messages, and the live-mode gate. The `GenericIE` case is
 * the first migrated upstream-shape case.
 */
class ExtractorHarnessTest {

    private val page = """
        <html><head><title>Fixture &amp; Clip</title></head>
        <body><video src="https://cdn.fixtures.example.net/clip.mp4"></video></body></html>
    """.trimIndent()

    private fun genericCase(overrides: ExtractorCase.() -> ExtractorCase = { this }): ExtractorCase =
        ExtractorCase(
            url = "https://example.org/watch",
            infoDict = mapOf(
                "title" to Expect.Value("Fixture & Clip"),
                "webpage_url" to Expect.Value("https://example.org/watch"),
                "extractor_key" to Expect.Value("Generic"),
                "formats" to Expect.MinCount(1),
                "formats.0.url" to Expect.Value("https://cdn.fixtures.example.net/clip.mp4"),
                "formats.0.ext" to Expect.Value("mp4"),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://example.org/watch",
                    contentType = "text/html",
                    body = page,
                ),
            ),
        ).overrides()

    private suspend fun run(case: ExtractorCase, run: ExtractorTestRun = ExtractorTestRun()): CaseResult =
        runCase(case, run) { http -> GenericIE(http) }

    @Test
    fun fixtureModeRunsWithoutNetworkAndChecksDottedPaths() = runTest {
        val result = run(genericCase())
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun aMissingFixtureFailsWithAClearRedactedMessage() = runTest {
        val result = run(genericCase { copy(routes = emptyList()) })
        val failed = assertIs<CaseResult.Failed>(result)
        assertContains(failed.reason, "FixtureMissingException")
        assertContains(failed.reason, "No fixture for GET example.org/watch")
    }

    @Test
    fun aWrongExpectationFailsWithTheFieldPath() = runTest {
        val result = run(
            genericCase {
                copy(
                    infoDict = mapOf(
                        "title" to Expect.Value("not the title"),
                        "formats.1.url" to Expect.Value("https://cdn.fixtures.example.net/missing.mp4"),
                    ),
                )
            },
        )
        val failed = assertIs<CaseResult.Failed>(result)
        assertContains(failed.reason, "title:")
        assertContains(failed.reason, "formats.1.url:")
    }

    @Test
    fun liveModeIsOffByDefaultAndNeedsAnAllowlist() = runTest {
        val liveCase = genericCase { copy(live = true) }

        val skipped = assertIs<CaseResult.Skipped>(run(liveCase))
        assertContains(skipped.reason, "live extractor tests are disabled")

        val noAllowlist = assertIs<CaseResult.Failed>(
            run(liveCase, ExtractorTestRun(live = true)),
        )
        assertContains(noAllowlist.reason, "allowlist")

        val liveRun = ExtractorTestRun(
            live = true,
            allowedLiveUrls = setOf("https://example.org/watch"),
            liveHttp = ExtractorHttp(
                FixtureHttpTransfer(
                    listOf(FixtureRoute(urlPattern = "https://example.org/watch", body = page)),
                ),
            ),
        )
        assertIs<CaseResult.Passed>(run(liveCase, liveRun))
    }

    @Test
    fun onlyMatchingChecksTheUrlWithoutExtracting() = runTest {
        val result = run(
            genericCase {
                copy(onlyMatching = true, routes = emptyList())
            },
        )
        assertIs<CaseResult.Passed>(result)
    }

    @Test
    fun skipReasonIsReported() = runTest {
        val result = run(genericCase { copy(skipReason = "geo-blocked in CI", routes = emptyList()) })
        val skipped = assertIs<CaseResult.Skipped>(result)
        assertContains(skipped.reason, "geo-blocked")
    }

    @Test
    fun unsuitableUrlFailsBeforeExtraction() = runTest {
        val result = run(genericCase { copy(url = "mailto:someone@example.com", routes = emptyList()) })
        val failed = assertIs<CaseResult.Failed>(result)
        assertContains(failed.reason, "does not match")
    }

    @Test
    fun fixtureStoreReadsRecordedResources() = runTest {
        val store = FixtureStore.of(mapOf("fixtures/self/hello.json" to page))
        val case = ExtractorCase(
            url = "https://example.org/watch",
            infoDict = mapOf("formats" to Expect.MinCount(1)),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://example.org/watch",
                    bodyResource = "fixtures/self/hello.json",
                    contentType = "text/html",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun(fixtures = store)) { http -> GenericIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
