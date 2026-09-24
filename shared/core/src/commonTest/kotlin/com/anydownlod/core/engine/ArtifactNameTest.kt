package com.anydownlod.core.engine

import com.anydownlod.core.domain.DownloadOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArtifactNameTest {

    private fun build(url: String, options: DownloadOptions = DownloadOptions()) =
        ArtifactName.build(url, options)

    @Test
    fun usesTheLastPathSegment() {
        assertEquals("tiny.bin", build("https://fixtures.example.com/files/tiny.bin"))
        assertEquals("video.mp4", build("https://fixtures.example.com/media/video.mp4"))
        // Query and fragment text is not a path segment and is never used.
        assertEquals("track", build("https://fixtures.example.com/track?song=song.mp3"))
        assertEquals("archive.zip", build("https://fixtures.example.com/archive.zip#section"))
    }

    @Test
    fun fallsBackWhenThereIsNoSegment() {
        assertEquals("download", build("https://fixtures.example.com"))
        assertEquals("download", build("https://fixtures.example.com/"))
        assertEquals("download", build("https://fixtures.example.com/files/.."))
    }

    @Test
    fun appliesDestinationFolderWithoutEscaping() {
        val options = DownloadOptions(destinationFolder = "music/albums")
        assertEquals("music/albums/song.mp3", build("https://fixtures.example.com/song.mp3", options))
    }

    @Test
    fun appliesFilenamePrefix() {
        val options = DownloadOptions(filenamePrefix = "live")
        assertEquals("live tiny.bin", build("https://fixtures.example.com/tiny.bin", options))
    }

    @Test
    fun rejectsTraversalInDestinationFolder() {
        assertFailsWith<IllegalArgumentException> {
            build("https://fixtures.example.com/a.bin", DownloadOptions(destinationFolder = "../escape"))
        }
        assertFailsWith<IllegalArgumentException> {
            build("https://fixtures.example.com/a.bin", DownloadOptions(destinationFolder = "music/../../escape"))
        }
        assertFailsWith<IllegalArgumentException> {
            build("https://fixtures.example.com/a.bin", DownloadOptions(destinationFolder = "C:\\windows"))
        }
    }

    @Test
    fun sanitizesUnsafeFileNameCharacters() {
        // Percent-encoding is literal: no decoding, so traversal via %2e/%2f
        // is impossible. Leading dots in a real segment are dropped so hidden
        // files are not implied.
        assertEquals("a%2Fb.bin", build("https://fixtures.example.com/files/a%2Fb.bin"))
        assertEquals("hidden.bin", build("https://fixtures.example.com/files/..hidden.bin"))
        assertEquals("%2f", build("https://fixtures.example.com/files/..%2f"))
    }
}