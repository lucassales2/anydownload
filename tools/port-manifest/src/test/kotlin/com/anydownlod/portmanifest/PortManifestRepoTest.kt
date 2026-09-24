package com.anydownlod.portmanifest

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the committed port data itself. These run against the real repository
 * files (the root is found by walking up to `port/manifest.json`).
 */
class PortManifestRepoTest {
    private val root = repoRoot()
    private val tool = PortManifestTool(root)

    @Test
    fun committedManifestValidatesAgainstTheUpstreamExtractorList() {
        val result = tool.validate(tool.loadManifest(), tool.loadUpstream())
        assertTrue(result.ok, result.errors.joinToString("\n"))
    }

    @Test
    fun committedCoverageBlockIsCurrent() {
        val manifest = tool.loadManifest()
        val upstream = tool.loadUpstream()
        val result = tool.verifyCoverage(manifest, upstream)
        assertTrue(result.ok, result.errors.joinToString("\n"))
    }

    @Test
    fun committedUpstreamListIsConsistentData() {
        val upstream = tool.loadUpstream()
        assertEquals(upstream.count, upstream.extractors.size)
        assertEquals(upstream.extractors.size, upstream.extractors.distinct().size)
        assertEquals(upstream.extractors.sorted(), upstream.extractors)
        assertEquals(UPSTREAM_SOURCE_PATH, upstream.sourcePath)
        assertTrue(upstream.extractors.isNotEmpty())
    }

    @Test
    fun committedManifestSeedsTheGenericSubsetAndNamesNoUnknownExtractors() {
        val manifest = tool.loadManifest()
        val ids = manifest.modules.map { it.id }
        assertTrue("GenericIE" in ids, "GenericIE must stay recorded after T-045")
        val upstreamNames = tool.loadUpstream().extractors.toSet()
        val unknown = manifest.modules
            .filter { it.kind == ManifestSchema.EXTRACTOR_KIND }
            .filterNot { it.id in upstreamNames }
        assertTrue(unknown.isEmpty(), "unknown extractor ids: ${unknown.map { it.id }}")
    }

    companion object {
        fun repoRoot(): Path {
            System.getProperty("portManifest.root")?.let { return Path.of(it).toAbsolutePath().normalize() }
            var candidate: Path? = Path.of("").toAbsolutePath().normalize()
            while (candidate != null) {
                if (Files.isRegularFile(candidate.resolve("port/manifest.json"))) return candidate
                candidate = candidate.parent
            }
            error("could not find port/manifest.json above the test working directory")
        }
    }
}
