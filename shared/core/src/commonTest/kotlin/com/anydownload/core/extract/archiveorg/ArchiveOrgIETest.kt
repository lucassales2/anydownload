package com.anydownload.core.extract.archiveorg

import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.harness.CaseResult
import com.anydownload.core.extract.harness.Expect
import com.anydownload.core.extract.harness.ExtractorCase
import com.anydownload.core.extract.harness.ExtractorTestRun
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import com.anydownload.core.extract.harness.FixtureRoute
import com.anydownload.core.extract.harness.runCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the archive.org subset. Every id, host, and address is
 * synthesized (`*.example`); no private file or signed URL appears.
 */
class ArchiveOrgIETest {

    private val itemUrl = "https://archive.org/details/fixture-item"
    private val embedUrl = "https://archive.org/embed/fixture-item"
    private val metadataUrl = "https://archive.org/metadata/fixture-item"

    private val singlePlaylist =
        """<html><body>
        <play-av playlist='[{"orig":"fixture.mp4","title":"Fixture track","artist":"Fixture artist","tracks":[{"kind":"subtitles","label":"en","file":"/subs/fixture.srt"}]}]'></play-av>
        </body></html>"""

    private val multiPlaylist =
        """<html><body>
        <play-av playlist='[{"orig":"one.mp4","title":"First"},{"orig":"two.mp4","title":"Second"}]'></play-av>
        </body></html>"""

    private val metadata = """
        {
          "metadata": {
            "identifier": "fixture-item",
            "title": "Fixture archive item",
            "description": "<p>A synthetic item</p>",
            "uploader": "Fixture Uploader",
            "publicdate": "2026-08-19T10:00:00Z"
          },
          "files": [
            {"name": "fixture.mp4", "format": "h.264", "size": "1000", "width": "640", "height": "360", "source": "original"},
            {"name": "thumb.jpg", "format": "Thumbnail", "width": "320", "height": "180"},
            {"name": "notes.txt", "format": "Text"}
          ]
        }
    """.trimIndent()

    private val multiMetadata = """
        {
          "metadata": {"identifier": "fixture-item", "title": "Multi item", "publicdate": "2026-08-19T10:00:00Z"},
          "files": [
            {"name": "one.mp4", "format": "h.264", "size": "1000"},
            {"name": "two.mp4", "format": "h.264", "size": "2000"},
            {"name": "thumb.jpg", "format": "Thumbnail", "width": "320"}
          ]
        }
    """.trimIndent()

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun extractor(transfer: FixtureHttpTransfer): ArchiveOrgIE =
        ArchiveOrgIE(ExtractorHttp(transfer))

    // ------------------------------------------------------------ URL matching

    @Test
    fun itemFormsMatch() {
        val ie = extractor(transfer())
        for (url in listOf(
            "https://archive.org/details/fixture-item",
            "https://www.archive.org/details/fixture-item?start=1",
            "https://archive.org/embed/fixture-item",
            "https://archive.org/details/fixture-item/entry.mp4",
        )) {
            assertTrue(ie.suitable(url), "URL must match: $url")
        }
        assertFalse(ie.suitable("https://archive.org/search?query=fixture"))
        assertFalse(ie.suitable("https://archive.org/advancedsearch.php"))
    }

    // -------------------------------------------------------------- extraction

    @Test
    fun singleEntryMapsFormatsAndMetadata() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = singlePlaylist),
            FixtureRoute(urlPattern = metadataUrl, body = metadata),
        )
        val info = extractor(transfer).extract(itemUrl)

        assertEquals("fixture-item", info.id)
        assertEquals("Fixture archive item", info.title)
        assertEquals("A synthetic item", info.description)
        assertEquals("Fixture Uploader", info.uploader)
        assertEquals("20260819", info.uploadDate)
        assertTrue(info.entries.isEmpty())

        val format = info.formats.single()
        assertEquals("https://archive.org/download/fixture-item/fixture.mp4", format.url)
        assertEquals("mp4", format.ext)
        assertEquals("h.264", format.formatId)
        assertEquals(640L, format.width)
        assertEquals(360L, format.height)
        assertEquals(1000L, format.filesize)
        assertEquals(0, format.sourcePreference)

        assertEquals("https://archive.org/download/fixture-item/thumb.jpg", info.thumbnails.single().url)
        assertEquals(320L, info.thumbnails.single().width)

        val subtitle = info.subtitles.single()
        assertEquals("en", subtitle.language)
        assertEquals("srt", subtitle.formats.single().ext)
        assertEquals("https://archive.org/subs/fixture.srt", subtitle.formats.single().url)
    }

    @Test
    fun multiEntryItemBecomesChildJobs() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = multiPlaylist),
            FixtureRoute(urlPattern = metadataUrl, body = multiMetadata),
        )
        val info = extractor(transfer).extract(itemUrl)

        assertEquals("Multi item", info.title)
        assertTrue(info.formats.isEmpty())
        assertEquals(2, info.entries.size)
        assertEquals("https://archive.org/details/fixture-item/one.mp4", info.entries[0].url)
        assertEquals("First", info.entries[0].title)
        assertEquals("https://archive.org/details/fixture-item/two.mp4", info.entries[1].url)
    }

    @Test
    fun entryUrlExtractsOnlyThatEntry() = runTest {
        val transfer = transfer(
            FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = multiPlaylist),
            FixtureRoute(urlPattern = metadataUrl, body = multiMetadata),
        )
        val info = extractor(transfer).extract("https://archive.org/details/fixture-item/two.mp4")

        assertTrue(info.entries.isEmpty())
        assertEquals("https://archive.org/download/fixture-item/two.mp4", info.formats.single().url)
    }

    // --------------------------------------------------------------- harness

    @Test
    fun archiveOrgIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = itemUrl,
            infoDict = mapOf(
                "id" to Expect.Value("fixture-item"),
                "title" to Expect.Value("Fixture archive item"),
                "uploader" to Expect.Value("Fixture Uploader"),
                "upload_date" to Expect.Value("20260819"),
                "formats" to Expect.Count(1),
                "formats.0.ext" to Expect.Value("mp4"),
                "formats.0.width" to Expect.Value(640L),
            ),
            routes = listOf(
                FixtureRoute(urlPattern = embedUrl, contentType = "text/html", body = singlePlaylist),
                FixtureRoute(urlPattern = metadataUrl, body = metadata),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { http -> ArchiveOrgIE(http) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
