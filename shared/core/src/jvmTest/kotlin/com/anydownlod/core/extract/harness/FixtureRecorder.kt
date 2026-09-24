package com.anydownlod.core.extract.harness

import java.nio.file.Files
import java.nio.file.Path

/**
 * JVM-only, opt-in fixture recorder. Call it explicitly from a live test that
 * took a real response (never from default CI), and always through
 * [FixtureRedactor]: signed URLs, tokens, visitor data, and cookies are
 * replaced before the file lands in
 * `shared/core/src/commonTest/resources/fixtures/<extractor>/`.
 *
 * Headers are not written at all; a `Set-Cookie` therefore cannot leak. The
 * extractor's test author reviews the file with the checklist in
 * `FixtureRedactor` before committing it.
 */
class FixtureRecorder(private val repoRoot: Path) {

    data class Response(
        val method: String,
        val url: String,
        val statusCode: Int,
        val contentType: String?,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
    )

    /** Writes the redacted body and returns the path that was written. */
    fun record(extractor: String, fileName: String, response: Response): Path {
        require(extractor.isNotBlank() && !extractor.contains("..") && !extractor.startsWith('/')) {
            "unsafe extractor fixture folder"
        }
        require(fileName.isNotBlank() && !fileName.contains("..") && !fileName.startsWith('/')) {
            "unsafe fixture file name"
        }
        val directory = repoRoot.resolve("shared/core/src/commonTest/resources/fixtures/$extractor")
            .toAbsolutePath()
            .normalize()
        Files.createDirectories(directory)
        val target = directory.resolve(fileName).normalize()
        require(target.startsWith(directory)) { "fixture path escapes the fixtures directory" }
        Files.writeString(target, FixtureRedactor.redact(response.body))
        return target
    }
}
