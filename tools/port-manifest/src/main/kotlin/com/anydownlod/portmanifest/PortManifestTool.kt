/*
 * Port manifest tool — AnyDownload repository tooling (T-055).
 *
 * Loads port/manifest.json and port/upstream-extractors.json, validates them,
 * regenerates the upstream name list from a fetched _extractors.py, and
 * rewrites the generated coverage block in the equivalence note.
 */
package com.anydownlod.portmanifest

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path

class PortManifestTool(private val root: Path) {
    private val normalizedRoot = root.toAbsolutePath().normalize()
    private val manifestPath = normalizedRoot.resolve("port/manifest.json")
    private val upstreamPath = normalizedRoot.resolve("port/upstream-extractors.json")
    private val notePath = normalizedRoot.resolve("vault/01-product/Ytdlp-equivalence.md")

    fun loadManifest(): PortManifest {
        require(Files.isRegularFile(manifestPath)) { "missing port/manifest.json under $normalizedRoot" }
        return ManifestJson.decodeFromString(Files.readString(manifestPath))
    }

    fun loadUpstream(): UpstreamExtractors {
        require(Files.isRegularFile(upstreamPath)) {
            "missing port/upstream-extractors.json; generate it with " +
                "./gradlew :tools:port-manifest:generateUpstreamExtractors -PupstreamExtractorsSource=<fetched _extractors.py>"
        }
        return ManifestJson.decodeFromString(Files.readString(upstreamPath))
    }

    /**
     * Reads a fetched copy of `yt_dlp/extractor/_extractors.py`, keeps only the
     * sorted class names, and writes port/upstream-extractors.json. The fetched
     * file itself is never copied into the repository.
     */
    fun generateUpstreamExtractors(sourceFile: Path): UpstreamExtractors {
        require(Files.isRegularFile(sourceFile)) { "fetched _extractors.py not found: $sourceFile" }
        val pin = loadManifest().upstream
        val names = ExtractorsPy.parseNames(Files.readString(sourceFile)).sorted()
        require(names.isNotEmpty()) { "no extractor imports found in $sourceFile" }
        val upstream = UpstreamExtractors(
            repository = pin.repository,
            tag = pin.tag,
            commit = pin.commit,
            sourcePath = UPSTREAM_SOURCE_PATH,
            count = names.size,
            extractors = names,
        )
        Files.writeString(upstreamPath, ManifestJson.encodeToString(upstream) + "\n")
        return upstream
    }

    fun validate(manifest: PortManifest, upstream: UpstreamExtractors): ValidationResult =
        PortManifestValidator.validate(normalizedRoot, manifest, upstream)

    /** Fails when the generated block in the note does not match the manifest. */
    fun verifyCoverage(manifest: PortManifest, upstream: UpstreamExtractors): ValidationResult {
        val errors = mutableListOf<String>()
        require(Files.isRegularFile(notePath)) { "missing the equivalence note: $notePath" }
        val note = Files.readString(notePath)
        val actual = try {
            Coverage.block(note)
        } catch (error: IllegalArgumentException) {
            errors += error.message ?: "invalid coverage markers"
            return ValidationResult(errors)
        }
        val expected = Coverage.render(manifest, upstream)
        if (actual.trim('\n') != expected.trim('\n')) {
            errors += "the coverage block in vault/01-product/Ytdlp-equivalence.md is out of date; " +
                "run ./gradlew :tools:port-manifest:run"
        }
        return ValidationResult(errors)
    }

    fun writeCoverage(manifest: PortManifest, upstream: UpstreamExtractors) {
        require(Files.isRegularFile(notePath)) { "missing the equivalence note: $notePath" }
        val note = Files.readString(notePath)
        val updated = Coverage.replaceBlock(note, Coverage.render(manifest, upstream))
        Files.writeString(notePath, updated)
    }
}
