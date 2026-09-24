/*
 * Port manifest CLI — AnyDownload repository tooling (T-055).
 *
 * Usage:
 *   port-manifest                     validate, then rewrite the coverage block
 *   port-manifest --check             validate and verify the block is current
 *   port-manifest --upstream-source=<fetched _extractors.py>
 *                                     regenerate port/upstream-extractors.json first
 *
 * The repository root comes from --root=, then the portManifest.root system
 * property (set by the Gradle tasks), then the working directory.
 */
package com.anydownlod.portmanifest

import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val checkOnly = args.contains("--check")
    val rootArg = args.firstOrNull { it.startsWith("--root=") }?.substringAfter('=')
    val upstreamSourceArg = args.firstOrNull { it.startsWith("--upstream-source=") }?.substringAfter('=')
    val root = Path.of(
        rootArg
            ?: System.getProperty("portManifest.root")
            ?: System.getenv("PORT_MANIFEST_ROOT")
            ?: ".",
    )

    try {
        val tool = PortManifestTool(root)
        if (!upstreamSourceArg.isNullOrBlank()) {
            val generated = tool.generateUpstreamExtractors(Path.of(upstreamSourceArg))
            println(
                "Wrote port/upstream-extractors.json: ${generated.count} extractor classes " +
                    "from ${generated.sourcePath} at ${generated.tag} (${generated.commit}).",
            )
        }

        val manifest = tool.loadManifest()
        val upstream = tool.loadUpstream()
        val validation = tool.validate(manifest, upstream)
        val staleness = if (checkOnly) tool.verifyCoverage(manifest, upstream) else ValidationResult(emptyList())
        val errors = validation.errors + staleness.errors
        if (errors.isNotEmpty()) {
            System.err.println("port-manifest: ${errors.size} problem(s):")
            errors.forEach { System.err.println("  - $it") }
            exitProcess(1)
        }

        if (checkOnly) {
            println("port-manifest: ok — ${summary(manifest, upstream)}")
        } else {
            tool.writeCoverage(manifest, upstream)
            println("port-manifest: ok — ${summary(manifest, upstream)}")
            println("Wrote the coverage block in vault/01-product/Ytdlp-equivalence.md.")
        }
    } catch (error: Exception) {
        System.err.println("port-manifest: ${error.message ?: error.toString()}")
        exitProcess(1)
    }
}

private fun summary(manifest: PortManifest, upstream: UpstreamExtractors): String {
    val counts = Coverage.counts(manifest, upstream.extractors.size)
    return "${Coverage.formatCount(counts.total)} upstream classes, " +
        "${Coverage.formatCount(counts.ported)} ported, " +
        "${Coverage.formatCount(counts.partial)} partial, " +
        "${Coverage.formatCount(counts.planned)} planned, " +
        "${Coverage.formatCount(counts.notStarted)} not started"
}
