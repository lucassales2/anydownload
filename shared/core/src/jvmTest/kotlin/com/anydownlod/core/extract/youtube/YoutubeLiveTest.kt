package com.anydownlod.core.extract.youtube

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.harness.CaseResult
import com.anydownlod.core.extract.harness.Expect
import com.anydownlod.core.extract.harness.ExtractorCase
import com.anydownlod.core.extract.harness.ExtractorTestRun
import com.anydownlod.core.extract.harness.runCase
import com.anydownlod.core.platform.JavaNetHttpTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The opt-in live case, on the public Big Buck Bunny URL listed in T-060.
 * It runs only with `-PliveExtractorTests=true`; evidence records the format
 * count and never the URLs returned.
 */
class YoutubeLiveTest {

    @Test
    fun bigBuckBunnyLiveCase() = runTest {
        val live = System.getProperty("liveExtractorTests") == "true"
        val run = ExtractorTestRun(
            live = live,
            allowedLiveUrls = setOf(LIVE_URL),
            liveHttp = if (live) ExtractorHttp(JavaNetHttpTransfer()) else null,
        )
        val case = ExtractorCase(
            url = LIVE_URL,
            infoDict = mapOf(
                "id" to Expect.Value("YE7VzlLtp-4"),
                "title" to Expect.StringType,
                "formats" to Expect.MinCount(1),
            ),
            live = true,
        )

        val result = runCase(case, run) { http -> YoutubeIE(http) }
        assertTrue(
            result is CaseResult.Passed || result is CaseResult.Skipped,
            "live case must pass or skip, never fail: $result",
        )

        if (live && result is CaseResult.Passed) {
            // Evidence only: counts, never the returned URLs.
            val info = YoutubeIE(ExtractorHttp(JavaNetHttpTransfer())).extract(LIVE_URL)
            println(
                "live youtube: formats=${info.formats.size}, " +
                    "needsJs=${info.formatsNeedingJs}, titleLength=${info.title?.length ?: 0}",
            )
        }
    }

    @Test
    fun bigBuckBunnyWithTheZiplineRuntimeResolvesTheWebClient() = runTest {
        if (System.getProperty("liveExtractorTests") != "true") {
            println("Skipped: set -PliveExtractorTests=true to run the live stage-2 check.")
            return@runTest
        }
        val http = ExtractorHttp(JavaNetHttpTransfer())
        val stage1 = YoutubeIE(http).extract(LIVE_URL)
        val runtime = com.anydownlod.core.jsc.QuickJsRuntime()
        val stage2 = YoutubeIE(http, runtime).extract(LIVE_URL)
        println(
            "live youtube stage2: stage1Formats=${stage1.formats.size} stage1NeedsJs=${stage1.formatsNeedingJs} " +
                "stage2Formats=${stage2.formats.size} stage2NeedsJs=${stage2.formatsNeedingJs} " +
                "quickjs=${runtime.version}",
        )
        assertTrue(stage2.formats.isNotEmpty())
        assertTrue(
            stage2.formats.size >= stage1.formats.size,
            "the web client must not shrink the format set",
        )
        assertTrue(
            stage2.formatsNeedingJs <= stage1.formatsNeedingJs,
            "the runtime must not increase the hidden count",
        )
    }

    companion object {
        const val LIVE_URL: String = "https://www.youtube.com/watch?v=YE7VzlLtp-4"
    }
}
