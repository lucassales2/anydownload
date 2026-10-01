/*
 * JSON-LD discovery — AnyDownload (T-136)
 *
 * Translation of the JSON-LD block scanner and the `@graph` walk from
 * `yt_dlp/extractor/common.py` (`_yield_json_ld` and the `traverse_json_ld`
 * part of `_json_ld`) at upstream tag `2026.08.19` (commit
 * 3a08beaf031ab68f966401ead017ac81fe8486cf). `JSON_LD_RE` itself comes from
 * `yt_dlp/utils/_utils.py`. yt-dlp is released under the Unlicense:
 *   https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/LICENSE
 * The VideoObject/interaction/chapter mapping of `_json_ld` is not
 * translated; callers read the fields they need. `common.py` is not
 * vendored; see shared/core/NOTICE.md and port/manifest.json.
 */
package com.anydownload.core.extract

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object JsonLd {

    /** Upstream `JSON_LD_RE` (`_utils.py` at the pin). */
    private val jsonLdRegex = Regex(
        """(?is)<script[^>]+type=(["']?)application/ld\+json\1[^>]*>\s*(\{.+?\}|\[.+?\])\s*</script>""",
    )

    /**
     * Upstream `_yield_json_ld`: every top-level object in the page's
     * `application/ld+json` blocks, in document order. A block that parses as
     * an array contributes its object items; a block that does not parse with
     * strict JSON is retried through [ExtractorUtils.jsToJson] and skipped
     * when that fails too. Blocks without a JSON object are ignored.
     */
    fun objects(html: String): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        for (match in jsonLdRegex.findAll(html)) {
            val body = match.groupValues[2]
            val parsed = ExtractorUtils.parseJson(body)
                ?: ExtractorUtils.parseJson(ExtractorUtils.jsToJson(body))
                ?: continue
            val items = if (parsed is JsonArray) parsed.toList() else listOf(parsed)
            for (item in items) {
                if (item is JsonObject) results += item
            }
        }
        return results
    }

    /**
     * The `traverse_json_ld` walk of `_json_ld`: a top-level object needs an
     * `@context`, and a top-level `{"@context", "@graph"}` object expands its
     * graph. Nested objects are returned as they are; `expected_type`
     * filtering stays with the caller (T-137).
     */
    fun entries(html: String): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        for (obj in objects(html)) {
            if ("@context" !in obj) continue
            if (obj.keys == setOf("@context", "@graph")) {
                obj["@graph"]?.let { walk(it, results) }
            } else {
                results += obj
            }
        }
        return results
    }

    private fun walk(element: JsonElement, into: MutableList<JsonObject>) {
        when (element) {
            is JsonArray -> element.forEach { walk(it, into) }
            is JsonObject -> into += element
            else -> Unit
        }
    }
}
