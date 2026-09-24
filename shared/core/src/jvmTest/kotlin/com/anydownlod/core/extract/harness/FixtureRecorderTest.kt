package com.anydownlod.core.extract.harness

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixtureRecorderTest {

    @Test
    fun writesARedactedFixtureUnderTheExtractorFolder() {
        val root = Files.createTempDirectory("fixture-recorder")
        val recorder = FixtureRecorder(root)
        val path = recorder.record(
            extractor = "self",
            fileName = "player.json",
            response = FixtureRecorder.Response(
                method = "POST",
                url = "https://www.youtube.example/youtubei/v1/player",
                statusCode = 200,
                contentType = "application/json",
                body = """
                    {"url":"https://rr1---sn-realhost.googlevideo.com/videoplayback?sig=SECRETSIG",
                     "visitorData":"SECRETVISITOR","poToken":"SECRETPOT"}
                """.trimIndent(),
                headers = mapOf("set-cookie" to "session=SECRETCOOKIE", "content-type" to "application/json"),
            ),
        )

        assertEquals(
            root.resolve("shared/core/src/commonTest/resources/fixtures/self/player.json")
                .toAbsolutePath()
                .normalize(),
            path,
        )
        val written = Files.readString(path)
        assertFalse(written.contains("googlevideo.com"), written)
        assertFalse(written.contains("SECRETSIG"), written)
        assertFalse(written.contains("SECRETVISITOR"), written)
        assertFalse(written.contains("SECRETPOT"), written)
        assertFalse(written.contains("SECRETCOOKIE"), written)
        assertTrue(written.contains("googlevideo.example"), written)
        assertTrue(Files.isRegularFile(path))
    }

    @Test
    fun refusesAPathTraversalFileName() {
        val root = Files.createTempDirectory("fixture-recorder-bad")
        assertFailsWith<IllegalArgumentException> {
            FixtureRecorder(root).record(
                extractor = "self",
                fileName = "../escape.json",
                response = FixtureRecorder.Response("GET", "https://example.org/", 200, null, "{}"),
            )
        }
    }
}
