package com.anydownlod.portmanifest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverageTest {
    private val upstream = UpstreamExtractors(
        repository = ManifestSchema.REPOSITORY,
        tag = TestFixtures.TAG,
        commit = TestFixtures.COMMIT,
        sourcePath = UPSTREAM_SOURCE_PATH,
        count = 1751,
        extractors = buildList {
            add("GenericIE")
            add("YoutubeIE")
            for (index in 0 until 1749) add("Synthetic${index}IE")
        }.sorted(),
    )

    private val manifest = PortManifest(
        upstream = UpstreamPin(ManifestSchema.REPOSITORY, TestFixtures.TAG, TestFixtures.COMMIT, ManifestSchema.LICENSE),
        modules = listOf(
            ModuleEntry(
                id = "GenericIE",
                kind = "extractor",
                upstreamPath = "yt_dlp/extractor/generic.py",
                kotlinFiles = listOf("shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/GenericExtractor.kt"),
                status = "partial",
                scope = "HTML5 media subset",
                tasks = listOf("T-045"),
                portedAt = "2026-09-24",
            ),
            ModuleEntry(
                id = "YoutubeIE",
                kind = "extractor",
                upstreamPath = "yt_dlp/extractor/youtube/_video.py",
                kotlinFiles = emptyList(),
                status = "planned",
                scope = "Single video",
                tasks = listOf("T-060"),
                portedAt = null,
            ),
        ),
    )

    @Test
    fun countsMatchTheSeededManifest() {
        val counts = Coverage.counts(manifest, upstream.count)
        assertEquals(0, counts.ported)
        assertEquals(1, counts.partial)
        assertEquals(1, counts.planned)
        assertEquals(1749, counts.notStarted)
        assertEquals(1751, counts.total)
    }

    @Test
    fun countingMoreNamedExtractorsThanUpstreamFails() {
        assertFailsWith<IllegalArgumentException> { Coverage.counts(manifest, 1) }
    }

    @Test
    fun renderedBlockCarriesTheCountsAndEveryNamedModule() {
        val rendered = Coverage.render(manifest, upstream)
        assertTrue(rendered.contains("**1,751** extractor classes"), rendered)
        assertTrue(rendered.contains("| Not started | 1,749 |"), rendered)
        assertTrue(rendered.contains("`GenericIE`"), rendered)
        assertTrue(rendered.contains("`YoutubeIE`"), rendered)
        assertTrue(rendered.contains("T-060"), rendered)
        assertTrue(rendered.contains("../../shared/core/src/commonMain/kotlin/com/anydownlod/core/extract/GenericExtractor.kt"), rendered)
        assertFalse(rendered.contains("Synthetic0IE"), "not-started extractors must not be listed")
    }

    @Test
    fun blockReturnsTheContentBetweenTheMarkers() {
        val note = "# Note\n\n${Coverage.START_MARKER}\ninner text\n${Coverage.END_MARKER}\ntail\n"
        assertEquals("\ninner text\n", Coverage.block(note))
    }

    @Test
    fun blockFailsWithoutMarkers() {
        assertFailsWith<IllegalArgumentException> { Coverage.block("# Note\nno markers\n") }
    }

    @Test
    fun replaceBlockRewritesOnlyTheBlock() {
        val note = "head\n${Coverage.START_MARKER}\nold\n${Coverage.END_MARKER}\ntail\n"
        val updated = Coverage.replaceBlock(note, "new line\n")
        assertEquals("head\n${Coverage.START_MARKER}\nnew line\n${Coverage.END_MARKER}\ntail\n", updated)
    }

    @Test
    fun formatCountGroupsDigitsWithoutLocale() {
        assertEquals("0", Coverage.formatCount(0))
        assertEquals("999", Coverage.formatCount(999))
        assertEquals("1,000", Coverage.formatCount(1000))
        assertEquals("1,751", Coverage.formatCount(1751))
    }
}
