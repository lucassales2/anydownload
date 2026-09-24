package com.anydownlod.desktop.engine

import com.anydownlod.core.MediaPreviewResult
import com.anydownlod.core.PreviewFailure
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YtDlpPreviewTest {

    @Test
    fun commandKeepsTheUrlAsItsOwnArgument() {
        val command = YtDlpMediaPreviewSource.previewCommand("/bin/yt-dlp", "https://example.com/watch?v=1&list=ab")

        assertEquals("/bin/yt-dlp", command.first())
        assertTrue(command.contains("--skip-download"))
        assertTrue(command.contains("--flat-playlist"))
        assertEquals("--", command[command.lastIndex - 1])
        assertEquals("https://example.com/watch?v=1&list=ab", command.last())
    }

    @Test
    fun jsonBecomesAPreview() {
        val raw = """
            {"_type":"video","title":"Big Buck Bunny","thumbnail":"https://example.com/t.jpg",
            "channel":"Blender","duration":65.4,"extractor_key":"Youtube",
            "description":"A short film.","view_count":1200,"upload_date":"20080101",
            "webpage_url":"https://example.com/watch?v=1"}
        """.trimIndent()

        val preview = YtDlpPreviewJson.parse(raw, "https://example.com/requested")!!

        assertEquals("Big Buck Bunny", preview.title)
        assertEquals("https://example.com/t.jpg", preview.thumbnailUrl)
        assertEquals("Blender", preview.channel)
        assertEquals(65L, preview.durationSeconds)
        assertEquals("Youtube", preview.extractor)
        assertEquals("2008-01-01", preview.uploadDate)
        assertEquals(1200L, preview.viewCount)
        assertFalse(preview.playlist)
    }

    @Test
    fun playlistCountAndMissingTitle() {
        val playlist = YtDlpPreviewJson.parse(
            """{"_type":"playlist","title":"Favorites","playlist_count":2,"entries":[{},{}]}""",
            "https://example.com/list",
        )!!
        assertTrue(playlist.playlist)
        assertEquals(2, playlist.entryCount)
        assertNull(YtDlpPreviewJson.parse("not json", "https://example.com/a"))
    }

    @Test
    fun missingToolDoesNotStartAProcess() = runBlocking {
        var started = false
        val source = YtDlpMediaPreviewSource(
            runner = CliProcessRunner { _, _ ->
                started = true
                error("must not start")
            },
            resolveExecutable = { null },
            workingDirectory = { Path.of(".") },
        )

        val result = source.load("https://example.com/watch?v=1")

        assertEquals(PreviewFailure.Unavailable, (result as MediaPreviewResult.Failed).failure)
        assertFalse(started)
    }

    @Test
    fun dumpedJsonIsThePreview() = runBlocking {
        val json = """{"title":"From the tool","channel":"Desk","duration":12,"extractor_key":"generic"}"""
        val source = YtDlpMediaPreviewSource(
            runner = CliProcessRunner { _, _ -> FakeCliProcess(listOf(json)) },
            resolveExecutable = { "yt-dlp" },
            workingDirectory = { Path.of(".") },
        )

        val result = source.load("https://example.com/watch?v=1")

        val ready = assertIs<MediaPreviewResult.Ready>(result)
        assertEquals("From the tool", ready.preview.title)
        assertEquals("Desk", ready.preview.channel)
    }

    @Test
    fun aTimeoutIsReported() = runBlocking {
        val source = YtDlpMediaPreviewSource(
            runner = CliProcessRunner { _, _ ->
                object : CliProcess {
                    override val stdout = java.io.BufferedReader(java.io.StringReader(""))
                    override val stderr = java.io.BufferedReader(java.io.StringReader(""))
                    override fun waitFor(timeoutMillis: Long): Int? = null
                    override fun destroyTree() = Unit
                }
            },
            resolveExecutable = { "yt-dlp" },
            workingDirectory = { Path.of(".") },
            timeoutMillis = 1,
        )

        val result = source.load("https://example.com/watch?v=1")

        assertEquals(PreviewFailure.TimedOut, (result as MediaPreviewResult.Failed).failure)
    }
}
