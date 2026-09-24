package com.anydownlod.core.jsc

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readLines
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-069: the bundled yt-dlp-ejs files match the upstream SHA3-512 hashes and a
 * tampered copy fails verification. The build's `verifyEjsBundle` task applies
 * the same rule.
 */
class EjsBundleVerifierTest {

    private fun sha3Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA3-512").digest(bytes)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun expectedHashes(hashFile: Path): Map<String, String> =
        hashFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                parts[1].trim() to parts[0].lowercase()
            }

    private fun verify(directory: Path): List<String> {
        val expected = expectedHashes(directory.resolve("HASHES.sha512"))
        val failures = mutableListOf<String>()
        expected.forEach { (name, hash) ->
            val file = directory.resolve(name)
            if (!Files.isRegularFile(file)) {
                failures += "$name is missing"
                return@forEach
            }
            val actual = sha3Hex(Files.readAllBytes(file))
            if (actual != hash) failures += "$name hash mismatch"
        }
        return failures
    }

    private fun repoRoot(): Path {
        var candidate: Path? = Path.of("").toAbsolutePath().normalize()
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("third_party/yt-dlp-ejs/0.8.0/HASHES.sha512"))) {
                return candidate
            }
            candidate = candidate.parent
        }
        error("could not find third_party/yt-dlp-ejs/0.8.0 above the test working directory")
    }

    @Test
    fun bundledScriptsMatchTheUpstreamHashes() {
        val directory = repoRoot().resolve("third_party/yt-dlp-ejs/0.8.0")
        assertEquals(emptyList(), verify(directory))
    }

    @Test
    fun generatedConstantsMatchTheUpstreamMinifiedHashes() {
        val directory = repoRoot().resolve("third_party/yt-dlp-ejs/0.8.0")
        val expected = expectedHashes(directory.resolve("HASHES.sha512"))
        assertEquals("0.8.0", EjsScripts.VERSION)
        assertEquals(expected.getValue("yt.solver.core.min.js"), sha3Hex(EjsScripts.core.encodeToByteArray()))
        assertEquals(expected.getValue("yt.solver.lib.min.js"), sha3Hex(EjsScripts.lib.encodeToByteArray()))
        assertTrue(EjsScripts.core.isNotEmpty())
        assertTrue(EjsScripts.lib.isNotEmpty())
    }

    @Test
    fun aTamperedCopyFailsVerification() {
        val source = repoRoot().resolve("third_party/yt-dlp-ejs/0.8.0")
        val copy = createTempDirectory("ejs-tamper")
        copy.resolve("files").createDirectories()
        Files.list(source).use { files ->
            files.forEach { file ->
                Files.copy(file, copy.resolve("files").resolve(file.fileName.toString()))
            }
        }
        val target = copy.resolve("files").resolve("yt.solver.core.min.js")
        val bytes = Files.readAllBytes(target)
        bytes[0] = (bytes[0] + 1).toByte()
        target.writeBytes(bytes)

        val failures = verify(copy.resolve("files"))
        assertTrue(failures.any { it.contains("yt.solver.core.min.js") }, "tampering must be detected: $failures")
    }
}
