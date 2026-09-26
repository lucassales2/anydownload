package com.anydownlod.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The output template over a fully-populated record. spotDL's documented
 * variables only; no live call and no file is written.
 */
class SpotifyOutputTemplateTest {

    private val record = SongRecord(
        songId = "4uLU6hMCjMI75M1A2tKUQC",
        title = "Never Gonna Give You Up",
        artists = listOf("Rick Astley", "Second Artist"),
        album = "Whenever You Need Somebody",
        albumArtist = "Rick Astley",
        albumType = "album",
        durationMs = 213_573,
        isrc = "GBARL9300135",
        artworkUrl = "https://i.scdn.co/image/fixture-cover",
        trackNumber = 3,
        tracksCount = 10,
        discNumber = 2,
        discCount = 2,
        year = 1987,
        releaseDate = "1987-11-12",
        genres = listOf("new romantic"),
        publisher = "Fixture Records",
        explicit = false,
        spotifyUrl = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC",
        listName = "Fixture Hits",
        listUrl = "https://open.spotify.com/playlist/fixture",
        listPosition = 7,
        listLength = 12,
    )

    @Test
    fun theDefaultTemplateIsArtistsTitleAndExtension() {
        assertEquals(
            "Rick Astley, Second Artist - Never Gonna Give You Up.mp3",
            SpotifyOutputTemplate.format(record, "mp3"),
        )
    }

    @Test
    fun everyDocumentedVariableRenders() {
        val output = SpotifyOutputTemplate.format(
            record = record,
            extension = "flac",
            template = "{title}|{artists}|{artist}|{album}|{album-artist}|{genre}|" +
                "{disc-number}|{disc-count}|{duration}|{year}|{original-date}|" +
                "{track-number}|{tracks-count}|{isrc}|{track-id}|{publisher}|" +
                "{list-length}|{list-position}|{list-name}.{output-ext}",
        )

        assertTrue(output.startsWith("Never Gonna Give You Up|"), output)
        assertTrue(output.contains("Rick Astley, Second Artist|Rick Astley|"), output)
        assertTrue(output.contains("Whenever You Need Somebody|Rick Astley|new romantic|"), output)
        assertTrue(output.contains("2|2|213|1987|1987-11-12|03|10|GBARL9300135|4uLU6hMCjMI75M1A2tKUQC|"), output)
        assertTrue(output.contains("Fixture Records|12|07|Fixture Hits.flac"), output)
    }

    @Test
    fun missingValuesBecomeEmptyStrings() {
        val minimal = SongRecord(title = "Bare Title")
        assertEquals(" - Bare Title.mp3", SpotifyOutputTemplate.format(minimal, "mp3"))
    }

    @Test
    fun aTemplateWithAParentSegmentIsRejected() {
        assertFailsWith<SpotifyTemplateError.EscapesRoot> {
            SpotifyOutputTemplate.format(record, "mp3", "../{title}.{output-ext}")
        }
        assertFailsWith<SpotifyTemplateError.EscapesRoot> {
            SpotifyOutputTemplate.format(record, "mp3", "{album}/../{title}.{output-ext}")
        }
        assertFailsWith<SpotifyTemplateError.EscapesRoot> {
            SpotifyOutputTemplate.format(record, "mp3", "/absolute/{title}.{output-ext}")
        }
    }

    @Test
    fun subfoldersStayInsideTheRoot() {
        assertEquals(
            "Fixture Records/Never Gonna Give You Up.mp3",
            SpotifyOutputTemplate.format(record, "mp3", "{publisher}/{title}.{output-ext}"),
        )
    }

    @Test
    fun strictRestrictionCleansTheFileName() {
        val dotted = record.copy(title = "  Fixture Title..  ")
        assertEquals(
            "Fixture Title.mp3",
            SpotifyOutputTemplate.format(dotted, "mp3", "{title}.{output-ext}", SpotifyRestrict.STRICT),
        )
    }

    @Test
    fun asciiRestrictionFoldsAccents() {
        val accented = record.copy(title = "Beyoncé - Déjà Vu", artists = listOf("Beyoncé"))
        assertEquals(
            "Beyonce - Beyonce - Deja Vu.mp3",
            SpotifyOutputTemplate.format(accented, "mp3", "{artists} - {title}.{output-ext}", SpotifyRestrict.ASCII),
        )
    }

    @Test
    fun aTemplateWithoutAnExtensionGetsTheDefaultTail() {
        assertEquals(
            "Fixture Hits.m4a",
            SpotifyOutputTemplate.format(record, "m4a", "{list-name}"),
        )
    }
}
