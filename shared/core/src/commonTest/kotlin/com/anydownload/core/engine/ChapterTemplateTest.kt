package com.anydownload.core.engine

import com.anydownload.core.domain.AppSettingsDefaults
import com.anydownload.core.extract.Chapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** T-016 chapter template rendering and path confinement. */
class ChapterTemplateTest {

    private val chapter = Chapter(startTime = 30.0, endTime = 60.0, title = "Intro: The Start")

    @Test
    fun theDefaultTemplateRendersTheSectionNumberAndTitle() {
        val path = ChapterTemplate.render(
            template = AppSettingsDefaults.CHAPTER_TEMPLATE,
            mediaTitle = "Fixture Clip",
            chapter = chapter,
            sectionNumber = 2,
            ext = "mp4",
        )

        assertEquals("Fixture Clip - 02 - Intro_ The Start.mp4", path)
    }

    @Test
    fun aMissingSectionTitleFallsBackToTheNumber() {
        val path = ChapterTemplate.render(
            template = "%(section_title)s.%(ext)s",
            mediaTitle = "Clip",
            chapter = Chapter(startTime = 0.0, title = null),
            sectionNumber = 3,
            ext = "mp4",
        )

        assertEquals("Chapter 3.mp4", path)
    }

    @Test
    fun aTemplateWithoutTheExtensionGetsOne() {
        val path = ChapterTemplate.render(
            template = "%(title)s - %(section_number)02d",
            mediaTitle = "Clip",
            chapter = chapter,
            sectionNumber = 1,
            ext = "mkv",
        )

        assertEquals("Clip - 01.mkv", path)
    }

    @Test
    fun folderTemplatesStayInsideTheRoot() {
        val path = ChapterTemplate.render(
            template = "chapters/%(title)s/%(section_number)s - %(section_title)s.%(ext)s",
            mediaTitle = "Clip",
            chapter = chapter,
            sectionNumber = 1,
            ext = "mp4",
        )

        assertEquals("chapters/Clip/1 - Intro_ The Start.mp4", path)
    }

    @Test
    fun traversalAndUnknownFieldsReturnNull() {
        assertNull(
            ChapterTemplate.render("../%(section_title)s.%(ext)s", "Clip", chapter, 1, "mp4"),
        )
        assertNull(
            ChapterTemplate.render("/absolute/%(section_title)s.%(ext)s", "Clip", chapter, 1, "mp4"),
        )
        assertNull(
            ChapterTemplate.render("%(unknown_field)s.%(ext)s", "Clip", chapter, 1, "mp4"),
        )
        assertNull(
            ChapterTemplate.render("%(section_title)s.%(ext)s", "Clip", chapter, 1, null),
        )
    }
}
