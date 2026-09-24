package com.anydownlod.portmanifest

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatorTest {
    private fun validate(root: Path): ValidationResult {
        val tool = PortManifestTool(root)
        return tool.validate(tool.loadManifest(), tool.loadUpstream())
    }

    @Test
    fun validManifestPasses() {
        val root = TestFixtures.validFixture()
        val result = validate(root)
        assertTrue(result.ok, result.errors.joinToString("\n"))
    }

    @Test
    fun missingKotlinFileFails() {
        val root = TestFixtures.validFixture()
        TestFixtures.writeManifest(
            root,
            TestFixtures.manifest(kotlinFiles = listOf("shared/DoesNotExist.kt")),
        )
        val result = validate(root)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("shared/DoesNotExist.kt") && it.contains("missing") }, "${result.errors}")
    }

    @Test
    fun unknownExtractorIdFails() {
        val root = TestFixtures.validFixture()
        TestFixtures.writeManifest(root, TestFixtures.manifest(id = "NopeIE"))
        val result = validate(root)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("NopeIE") && it.contains("not an extractor class") }, "${result.errors}")
    }

    @Test
    fun plannedModuleWithKotlinFilesFails() {
        val root = TestFixtures.validFixture()
        TestFixtures.writeManifest(
            root,
            TestFixtures.manifest(status = "planned", kotlinFiles = listOf("shared/GenericExtractor.kt"), portedAt = null),
        )
        val result = validate(root)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("lists kotlinFiles") }, "${result.errors}")
    }

    @Test
    fun partialModuleWithoutFilesFails() {
        val root = TestFixtures.validFixture()
        TestFixtures.writeManifest(root, TestFixtures.manifest(kotlinFiles = emptyList()))
        val result = validate(root)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("no kotlinFiles") }, "${result.errors}")
    }

    @Test
    fun pathTraversalFails() {
        val root = TestFixtures.validFixture()
        TestFixtures.writeManifest(root, TestFixtures.manifest(kotlinFiles = listOf("../escape.kt")))
        val result = validate(root)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("../escape.kt") }, "${result.errors}")
    }

    @Test
    fun duplicateModuleIdFails() {
        val root = TestFixtures.validFixture()
        val single = TestFixtures.manifest()
        TestFixtures.writeManifest(root, single.copy(modules = single.modules + single.modules))
        val result = validate(root)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("duplicate module id") }, "${result.errors}")
    }

    @Test
    fun pinMismatchFails() {
        val root = TestFixtures.validFixture()
        TestFixtures.writeUpstream(root, tag = "2099.01.01", commit = "f".repeat(40))
        val result = validate(root)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("does not match") }, "${result.errors}")
    }

    @Test
    fun unknownKindAndStatusFail() {
        val root = TestFixtures.validFixture()
        TestFixtures.writeManifest(root, TestFixtures.manifest(kind = "plugin", status = "maybe"))
        val result = validate(root)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("unknown kind") }, "${result.errors}")
        assertTrue(result.errors.any { it.contains("unknown status") }, "${result.errors}")
    }

    @Test
    fun nonExtractorModuleIdIsNotCheckedAgainstTheCatalog() {
        val root = TestFixtures.validFixture()
        TestFixtures.writeManifest(root, TestFixtures.manifest(id = "HttpTransfer", kind = "core"))
        val result = validate(root)
        assertTrue(result.ok, result.errors.joinToString("\n"))
    }
}
