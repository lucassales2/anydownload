/*
 * Port manifest model — AnyDownload repository tooling (T-055).
 *
 * This is project tooling, not a translation of upstream code. It describes
 * which yt-dlp modules are translated and where they land. The pin lives in
 * port/manifest.json and follows vault/03-decisions/ADR-008.
 */
package com.anydownlod.portmanifest

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UpstreamPin(
    val repository: String,
    val tag: String,
    val commit: String,
    val license: String,
)

@Serializable
data class ModuleEntry(
    val id: String,
    val kind: String,
    val upstreamPath: String,
    val kotlinFiles: List<String> = emptyList(),
    val status: String,
    val scope: String,
    val tasks: List<String> = emptyList(),
    val portedAt: String? = null,
)

@Serializable
data class PortManifest(
    val upstream: UpstreamPin,
    val modules: List<ModuleEntry>,
)

/** Values fixed by the task note and ADR-008. */
object ManifestSchema {
    const val REPOSITORY = "yt-dlp/yt-dlp"
    const val LICENSE = "Unlicense"

    val KINDS = setOf("extractor", "core", "downloader", "jsc", "utils")
    val STATUSES = setOf("ported", "partial", "planned", "not-started")
    val PORTED_STATUSES = setOf("ported", "partial")

    /** The subset of upstream `_extractors.py` the coverage counts are about. */
    const val EXTRACTOR_KIND = "extractor"
}

val ManifestJson: Json = Json {
    prettyPrint = true
    encodeDefaults = true
}
