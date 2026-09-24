/*
 * Extractor test harness — AnyDownload
 *
 * Translation of the matcher semantics from yt-dlp's `test/test_download.py`
 * (`expect_info_dict`) and `test/helpers.py` (`expect_value`) at upstream tag
 * `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf), read
 * 2026-09-24. Unlicense; see shared/core/NOTICE.md. The Python runner is not
 * translated and no Python is read at test time; cases are Kotlin
 * declarations, fixtures are synthesized or recorded-then-redacted.
 */
package com.anydownlod.core.extract.harness

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.extract.Thumbnail

/**
 * One upstream-style expectation. `md5:`, `re:`, `count:`, `startswith:`,
 * `mincount:`, and `maxcount:` strings in a case are written with the helpers
 * in the companion object.
 */
sealed interface Expect {
    /** Returns null on success, or a redacted failure reason. */
    fun check(field: String, actual: Any?): String?

    data class Value(val value: Any?) : Expect {
        override fun check(field: String, actual: Any?): String? =
            if (valuesEqual(value, actual)) null else "$field: expected ${safe(value)}, got ${safe(actual)}"
    }

    data object IntType : Expect {
        override fun check(field: String, actual: Any?): String? =
            if (actual is Number && actual !is Boolean && actual.toDouble() == actual.toLong().toDouble()) {
                null
            } else {
                "$field: expected int, got ${safe(actual)}"
            }
    }

    data object FloatType : Expect {
        override fun check(field: String, actual: Any?): String? =
            if (actual is Number && actual !is Boolean) null else "$field: expected float, got ${safe(actual)}"
    }

    data object StringType : Expect {
        override fun check(field: String, actual: Any?): String? =
            if (actual is String) null else "$field: expected str, got ${safe(actual)}"
    }

    data class Md5(val hex: String) : Expect {
        override fun check(field: String, actual: Any?): String? {
            if (actual !is String) return "$field: expected a string for md5, got ${safe(actual)}"
            return if (md5Hex(actual.encodeToByteArray()).equals(hex, ignoreCase = true)) {
                null
            } else {
                "$field: md5 mismatch"
            }
        }
    }

    data class RegexMatch(val pattern: String) : Expect {
        override fun check(field: String, actual: Any?): String? {
            if (actual !is String) return "$field: expected a string for re:, got ${safe(actual)}"
            return if (runCatching { Regex(pattern).containsMatchIn(actual) }.getOrDefault(false)) {
                null
            } else {
                "$field: does not match re:$pattern"
            }
        }
    }

    data class StartsWith(val prefix: String) : Expect {
        override fun check(field: String, actual: Any?): String? =
            if (actual is String && actual.startsWith(prefix)) null else "$field: does not start with $prefix"
    }

    data class Count(val count: Int) : Expect {
        override fun check(field: String, actual: Any?): String? =
            if (countOf(actual) == count) null else "$field: expected $count items, got ${countOf(actual)}"
    }

    data class MinCount(val min: Int) : Expect {
        override fun check(field: String, actual: Any?): String? {
            val size = countOf(actual)
            return if (size != null && size >= min) null else "$field: expected at least $min items, got $size"
        }
    }

    data class MaxCount(val max: Int) : Expect {
        override fun check(field: String, actual: Any?): String? {
            val size = countOf(actual)
            return if (size != null && size <= max) null else "$field: expected at most $max items, got $size"
        }
    }

    /** A nested map check, mirroring upstream's nested expected dicts. */
    data class Nested(val fields: Map<String, Expect>) : Expect {
        override fun check(field: String, actual: Any?): String? {
            val map = actual as? Map<*, *> ?: return "$field: expected an object, got ${safe(actual)}"
            return checkFields(field, map, fields)
        }
    }

    companion object {
        fun md5(value: String): Expect = Md5(value)
        fun re(pattern: String): Expect = RegexMatch(pattern)
        fun count(value: Int): Expect = Count(value)
        fun startswith(value: String): Expect = StartsWith(value)
        fun mincount(value: Int): Expect = MinCount(value)
        fun maxcount(value: Int): Expect = MaxCount(value)
    }
}

private fun valuesEqual(expected: Any?, actual: Any?): Boolean = when {
    expected == null -> actual == null
    expected is Boolean || actual is Boolean -> expected == actual
    expected is Number && actual is Number -> expected.toDouble() == actual.toDouble()
    else -> expected == actual
}

private fun countOf(actual: Any?): Int? = when (actual) {
    is Collection<*> -> actual.size
    is List<*> -> actual.size
    else -> null
}

private fun safe(value: Any?): String {
    val text = value?.toString() ?: "null"
    return if (text.length > 64 || text.contains("://")) "<redacted>" else text
}

/** One upstream-style `_TESTS` case. */
data class ExtractorCase(
    val url: String,
    val infoDict: Map<String, Expect> = emptyMap(),
    val onlyMatching: Boolean = false,
    val skipReason: String? = null,
    /** Upstream `params`; D4 extractors take no params, kept for shape. */
    val params: Map<String, String> = emptyMap(),
    val routes: List<FixtureRoute> = emptyList(),
    /** True for the opt-in live run; only URLs listed by the task note. */
    val live: Boolean = false,
)

sealed interface CaseResult {
    data class Passed(val id: String?) : CaseResult
    data class Skipped(val reason: String) : CaseResult
    data class Failed(val reason: String) : CaseResult
}

/**
 * The environment for one test run: whether live mode is on, which URLs the
 * task note allows live, and where fixture files come from.
 */
class ExtractorTestRun(
    val live: Boolean = false,
    val allowedLiveUrls: Set<String> = emptySet(),
    val fixtures: FixtureStore = FixtureStore.Empty,
    /** The real transport for the opt-in live run; null fails typed. */
    val liveHttp: ExtractorHttp? = null,
)

/**
 * Runs [case] through an extractor built from the mode-appropriate HTTP seam.
 * Fixture mode never touches the network; live mode needs [ExtractorTestRun.live],
 * an allowlisted URL, and a real transport.
 */
suspend fun runCase(
    case: ExtractorCase,
    run: ExtractorTestRun = ExtractorTestRun(),
    extractor: (ExtractorHttp) -> InfoExtractor,
): CaseResult {
    case.skipReason?.let { return CaseResult.Skipped(it) }

    if (case.live) {
        if (!run.live) return CaseResult.Skipped("live extractor tests are disabled (-PliveExtractorTests=true)")
        if (case.url !in run.allowedLiveUrls) return CaseResult.Failed("live URL is not on this run's allowlist")
    }

    val liveHttp = run.liveHttp
    val http = if (case.live) {
        liveHttp ?: return CaseResult.Failed("live run has no HTTP transfer")
    } else {
        ExtractorHttp(FixtureHttpTransfer(case.routes, run.fixtures))
    }
    val ie = extractor(http)
    if (!ie.suitable(case.url)) {
        return CaseResult.Failed("extractor '${ie.ieKey}' does not match the case URL")
    }
    if (case.onlyMatching) return CaseResult.Passed(null)

    val info = try {
        ie.extract(case.url)
    } catch (error: ExtractionError) {
        return CaseResult.Failed("${error::class.simpleName}: ${error.message}")
    } catch (error: Throwable) {
        return CaseResult.Failed("${error::class.simpleName}: ${safe(error.message)}")
    }

    val values = CaseFieldValues.of(info)
    val failures = checkFields("info", values, case.infoDict)
    return if (failures == null) CaseResult.Passed(info.id) else CaseResult.Failed(failures)
}

/** Checks a map of expectations against actual values, dotted paths included. */
private fun checkFields(prefix: String, actual: Map<*, *>, expected: Map<String, Expect>): String? {
    val failures = mutableListOf<String>()
    for ((field, expect) in expected) {
        val path = if (prefix == "info") field else "$prefix.$field"
        val value = CaseFieldValues.lookup(actual, field)
        expect.check(path, value)?.let { failures += it }
    }
    return if (failures.isEmpty()) null else failures.joinToString("; ")
}

/** Maps [InfoDict] fields to upstream-style names for case matching. */
internal object CaseFieldValues {
    fun of(info: InfoDict): Map<String, Any?> = mapOf(
        "id" to info.id,
        "title" to info.title,
        "ext" to info.ext,
        "url" to info.url,
        "formats" to info.formats.map(::formatMap),
        "thumbnails" to info.thumbnails.map(::thumbnailMap),
        "duration" to info.duration,
        "uploader" to info.uploader,
        "channel" to info.channel,
        "channel_id" to info.channelId,
        "upload_date" to info.uploadDate,
        "view_count" to info.viewCount,
        "description" to info.description,
        "webpage_url" to info.webpageUrl,
        "extractor" to info.extractor,
        "extractor_key" to info.extractorKey,
        "age_limit" to info.ageLimit,
        "is_live" to info.isLive,
        "availability" to info.availability,
    )

    fun formatMap(format: MediaFormat): Map<String, Any?> = mapOf(
        "format_id" to format.formatId,
        "url" to format.url,
        "ext" to format.ext,
        "protocol" to format.protocol,
        "vcodec" to format.vcodec,
        "acodec" to format.acodec,
        "width" to format.width,
        "height" to format.height,
        "fps" to format.fps,
        "tbr" to format.tbr,
        "abr" to format.abr,
        "vbr" to format.vbr,
        "asr" to format.asr,
        "filesize" to format.filesize,
        "filesize_approx" to format.filesizeApprox,
        "audio_channels" to format.audioChannels,
        "dynamic_range" to format.dynamicRange,
        "language" to format.language,
        "quality" to format.quality,
        "preference" to format.preference,
        "source_preference" to format.sourcePreference,
        "format_note" to format.formatNote,
        "has_drm" to format.hasDrm,
        "manifest_url" to format.manifestUrl,
        "fragment_base_url" to format.fragmentBaseUrl,
    )

    fun thumbnailMap(thumbnail: Thumbnail): Map<String, Any?> = mapOf(
        "url" to thumbnail.url,
        "id" to thumbnail.id,
        "width" to thumbnail.width,
        "height" to thumbnail.height,
        "preference" to thumbnail.preference,
    )

    /** Dotted-path lookup with upstream snake_case names accepted. */
    fun lookup(root: Map<*, *>, path: String): Any? {
        var current: Any? = root
        for (segment in path.split('.')) {
            current = when (val node = current) {
                is Map<*, *> -> node.entries.firstOrNull { normalize(it.key.toString()) == normalize(segment) }?.value
                is List<*> -> segment.toIntOrNull()?.let { node.getOrNull(it) }
                else -> null
            }
            if (current == null) return null
        }
        return current
    }

    private fun normalize(name: String): String = name.replace("_", "").lowercase()
}
