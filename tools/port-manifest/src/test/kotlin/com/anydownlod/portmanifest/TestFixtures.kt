package com.anydownlod.portmanifest

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/** Shared fixture helpers for the port-manifest tool tests. */
internal object TestFixtures {
    const val COMMIT = "3a08beaf031ab68f966401ead017ac81fe8486cf"
    const val TAG = "2026.08.19"

    fun root(): Path = createTempDirectory("port-manifest-test")

    fun writeFile(root: Path, relative: String, content: String) {
        val path = root.resolve(relative)
        path.parent?.createDirectories()
        path.writeText(content)
    }

    fun writeUpstream(
        root: Path,
        names: List<String> = listOf("GenericIE", "YoutubeIE"),
        tag: String = TAG,
        commit: String = COMMIT,
    ) {
        val sorted = names.sorted()
        val upstream = UpstreamExtractors(
            repository = ManifestSchema.REPOSITORY,
            tag = tag,
            commit = commit,
            sourcePath = UPSTREAM_SOURCE_PATH,
            count = sorted.size,
            extractors = sorted,
        )
        writeFile(root, "port/upstream-extractors.json", ManifestJson.encodeToString(upstream) + "\n")
    }

    fun manifest(
        id: String = "GenericIE",
        kind: String = "extractor",
        status: String = "partial",
        kotlinFiles: List<String> = listOf("shared/GenericExtractor.kt"),
        portedAt: String? = "2026-09-24",
        upstreamPath: String = "yt_dlp/extractor/generic.py",
        tag: String = TAG,
        commit: String = COMMIT,
    ): PortManifest = PortManifest(
        upstream = UpstreamPin(ManifestSchema.REPOSITORY, tag, commit, ManifestSchema.LICENSE),
        modules = listOf(
            ModuleEntry(
                id = id,
                kind = kind,
                upstreamPath = upstreamPath,
                kotlinFiles = kotlinFiles,
                status = status,
                scope = "test subset",
                tasks = listOf("T-055"),
                portedAt = portedAt,
            ),
        ),
    )

    fun writeManifest(root: Path, manifest: PortManifest) {
        writeFile(root, "port/manifest.json", ManifestJson.encodeToString(manifest) + "\n")
    }

    fun writeNote(root: Path, block: String) {
        writeFile(
            root,
            "vault/01-product/Ytdlp-equivalence.md",
            "# Equivalence\n\n" + Coverage.START_MARKER + "\n" + block + "\n" + Coverage.END_MARKER + "\n",
        )
    }

    /** A valid repository skeleton with one partial extractor. */
    fun validFixture(): Path {
        val root = root()
        writeFile(root, "shared/GenericExtractor.kt", "// fixture\n")
        writeUpstream(root)
        writeManifest(root, manifest())
        writeNote(root, "_Not generated yet._")
        return root
    }
}
