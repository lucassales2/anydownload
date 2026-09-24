/*
 * Port manifest validation — AnyDownload repository tooling (T-055).
 *
 * Errors are plain, redacted messages about module ids and repository-relative
 * paths. Nothing here reads media or network content.
 */
package com.anydownlod.portmanifest

import java.nio.file.Files
import java.nio.file.Path

data class ValidationResult(val errors: List<String>) {
    val ok: Boolean get() = errors.isEmpty()
}

object PortManifestValidator {
    private val commitPattern = Regex("[0-9a-f]{40}")
    private val datePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val taskPattern = Regex("T-[0-9]{3}")

    fun validate(
        root: Path,
        manifest: PortManifest,
        upstream: UpstreamExtractors,
    ): ValidationResult {
        val errors = mutableListOf<String>()
        val pin = manifest.upstream
        val normalizedRoot = root.toAbsolutePath().normalize()

        if (pin.repository != ManifestSchema.REPOSITORY) {
            errors += "upstream.repository must be '${ManifestSchema.REPOSITORY}'"
        }
        if (pin.tag.isBlank()) {
            errors += "upstream.tag must not be blank"
        }
        if (!commitPattern.matches(pin.commit)) {
            errors += "upstream.commit must be a 40-character lowercase hex revision"
        }
        if (pin.license != ManifestSchema.LICENSE) {
            errors += "upstream.license must be '${ManifestSchema.LICENSE}'"
        }
        if (upstream.repository != pin.repository || upstream.tag != pin.tag || upstream.commit != pin.commit) {
            errors += "port/upstream-extractors.json pin (${upstream.repository} ${upstream.tag} ${upstream.commit}) " +
                "does not match port/manifest.json (${pin.repository} ${pin.tag} ${pin.commit})"
        }
        if (upstream.sourcePath != UPSTREAM_SOURCE_PATH) {
            errors += "port/upstream-extractors.json sourcePath must be '$UPSTREAM_SOURCE_PATH'"
        }
        if (upstream.extractors.isEmpty()) {
            errors += "port/upstream-extractors.json lists no extractor classes"
        }
        if (upstream.count != upstream.extractors.size) {
            errors += "port/upstream-extractors.json count ${upstream.count} does not match its " +
                "${upstream.extractors.size} names"
        }
        if (upstream.extractors.distinct().size != upstream.extractors.size) {
            errors += "port/upstream-extractors.json contains duplicate extractor names"
        }
        if (upstream.extractors != upstream.extractors.sorted()) {
            errors += "port/upstream-extractors.json must be sorted"
        }
        val upstreamNames = upstream.extractors.toSet()

        val seenIds = mutableSetOf<String>()
        for (module in manifest.modules) {
            val where = "module '${module.id.ifBlank { "(blank)" }}'"
            if (module.id.isBlank()) {
                errors += "module id must not be blank"
            } else if (!seenIds.add(module.id)) {
                errors += "duplicate module id '${module.id}'"
            }
            if (module.kind !in ManifestSchema.KINDS) {
                errors += "$where has unknown kind '${module.kind}'"
            }
            if (module.status !in ManifestSchema.STATUSES) {
                errors += "$where has unknown status '${module.status}'"
            }
            if (module.upstreamPath.isBlank()) {
                errors += "$where has no upstreamPath"
            }
            if (module.scope.isBlank()) {
                errors += "$where has no scope"
            }
            for (task in module.tasks) {
                if (!taskPattern.matches(task)) {
                    errors += "$where lists task '$task'; expected T-000 form"
                }
            }
            if (module.kind == ManifestSchema.EXTRACTOR_KIND && module.id !in upstreamNames) {
                errors += "$where is not an extractor class in the upstream list at ${upstream.tag}"
            }
            if (module.status in ManifestSchema.PORTED_STATUSES) {
                if (module.kotlinFiles.isEmpty()) {
                    errors += "$where is '${module.status}' but lists no kotlinFiles"
                }
                if (module.portedAt == null || !datePattern.matches(module.portedAt)) {
                    errors += "$where is '${module.status}' but portedAt is not an ISO date"
                }
            } else {
                if (module.portedAt != null) {
                    errors += "$where is '${module.status}' but sets portedAt"
                }
                if (module.kotlinFiles.isNotEmpty()) {
                    errors += "$where is '${module.status}' but lists kotlinFiles"
                }
            }
            for (file in module.kotlinFiles) {
                val relative = Path.of(file)
                if (relative.isAbsolute) {
                    errors += "$where kotlinFiles must be repository-relative: '$file'"
                    continue
                }
                val resolved = normalizedRoot.resolve(file).normalize()
                if (!resolved.startsWith(normalizedRoot)) {
                    errors += "$where kotlinFiles escapes the repository: '$file'"
                    continue
                }
                if (!Files.isRegularFile(resolved)) {
                    errors += "$where kotlinFiles is missing: '$file'"
                }
            }
        }
        return ValidationResult(errors)
    }
}
