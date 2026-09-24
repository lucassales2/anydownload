package com.anydownlod.desktop.engine

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.platform.JavaNetHttpTransfer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

/**
 * Opt-in differential test (T-062): the Kotlin YouTube extractor against the
 * installed `yt-dlp -J` on the same public URL, running only with
 * `-PytDlpOracle=true` and the pinned 2026.08.19 version. The URL list here is
 * the only allowed input; diffs print field names and values, never URLs.
 */
class YtDlpOracleTest {

    private fun assumeOracle(): String {
        assumeTrue(
            "Set -PytDlpOracle=true to run the yt-dlp differential",
            System.getProperty("ytDlpOracle") == "true",
        )
        val executable = ExecutableOnPath.find("yt-dlp")
        assumeTrue("yt-dlp is not on PATH", executable != null)
        val version = YtDlpOracle.installedVersion(executable!!)
        assumeTrue(
            "yt-dlp ${version ?: "<unknown>"} is not the pinned ${YtDlpOracle.PINNED_VERSION}",
            version == YtDlpOracle.PINNED_VERSION,
        )
        return executable
    }

    @Test
    fun youtubeVisionosFieldsMatchTheOracle() = runBlocking {
        val executable = assumeOracle()
        val oracle = YtDlpOracle.infoDict(executable, BIG_BUCK_BUNNY)
        val kotlinInfo = YoutubeIE(ExtractorHttp(JavaNetHttpTransfer())).extract(BIG_BUCK_BUNNY)
        val kotlinNormalized = YtDlpOracle.normalizeKotlin(kotlinInfo)

        val diff = YtDlpOracle.diff(kotlinNormalized, oracle)
        println(
            "oracle youtube: kotlinFormats=${kotlinNormalized.formats.size} " +
                "oracleFormats=${oracle.formats.size} diffs=${diff.size}",
        )
        assertFalse(diff.joinToString("\n").contains("googlevideo"), "the diff must not carry media URLs")
        assertTrue(diff.isEmpty(), "redacted field diff:\n${diff.joinToString("\n")}")
    }

    @Test
    fun audioOnlyFormatsMatchTheOracle() = runBlocking {
        val executable = assumeOracle()
        val oracle = YtDlpOracle.infoDict(executable, BIG_BUCK_BUNNY)
        val kotlinInfo = YoutubeIE(ExtractorHttp(JavaNetHttpTransfer())).extract(BIG_BUCK_BUNNY)
        val kotlinNormalized = YtDlpOracle.normalizeKotlin(kotlinInfo)

        val oracleAudio = oracle.formats.filterValues { it.isAudioOnly }
        val kotlinAudio = kotlinNormalized.formats.filterValues { it.isAudioOnly }
        assertTrue(oracleAudio.isNotEmpty(), "the oracle found no audio-only format")
        assertTrue(kotlinAudio.isNotEmpty(), "the Kotlin extractor found no audio-only format")

        val diff = YtDlpOracle.diff(
            kotlinNormalized.copy(formats = kotlinAudio),
            oracle.copy(formats = oracleAudio),
        )
        println("oracle youtube audio-only: kotlin=${kotlinAudio.size} oracle=${oracleAudio.size} diffs=${diff.size}")
        assertFalse(diff.joinToString("\n").contains("googlevideo"), "the diff must not carry media URLs")
        assertTrue(diff.isEmpty(), "redacted audio-only diff:\n${diff.joinToString("\n")}")
    }

    @Test
    fun youtubeWebAndVisionosMatchesTheKotlinStageTwo() = runBlocking {
        val executable = assumeOracle()
        val oracle = YtDlpOracle.infoDict(executable, BIG_BUCK_BUNNY, YtDlpOracle.WEB_PLAYER_CLIENT_ARG)
        val kotlinInfo = YoutubeIE(
            ExtractorHttp(JavaNetHttpTransfer()),
            com.anydownlod.core.jsc.QuickJsRuntime(),
        ).extract(BIG_BUCK_BUNNY)
        val kotlinNormalized = YtDlpOracle.normalizeKotlin(kotlinInfo)

        val diff = YtDlpOracle.diff(kotlinNormalized, oracle)
        println(
            "oracle youtube web,visionos: kotlinFormats=${kotlinNormalized.formats.size} " +
                "oracleFormats=${oracle.formats.size} diffs=${diff.size}",
        )
        diff.forEach { println("oracle web,visionos diff: " + it.take(160)) }
        assertFalse(diff.joinToString("\n").contains("googlevideo"), "the diff must not carry media URLs")
        assertTrue(diff.isEmpty(), "redacted web,visionos diff:\n${diff.joinToString("\n")}")
    }

    companion object {
        /** The public Big Buck Bunny URL listed in T-060 and T-062. */
        const val BIG_BUCK_BUNNY: String = "https://www.youtube.com/watch?v=YE7VzlLtp-4"
    }
}
