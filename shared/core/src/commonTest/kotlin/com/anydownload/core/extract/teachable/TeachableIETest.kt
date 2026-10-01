package com.anydownload.core.extract.teachable

import com.anydownload.core.extract.ExtractionError
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Teachable subset. Ids and media paths are synthesized;
 * the Wistia URLs use the public embed host and a fake media id. No cookie,
 * token, or signed URL appears.
 */
class TeachableIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val lectureUrl = "https://gns3.teachable.com/courses/gns3-certified-associate/lectures/6842364"
    private val courseUrl = "http://v1.upskillcourses.com/courses/essential-web-developer-course/"

    private val lecturePage = FixtureRoute(
        urlPattern = "https://gns3.teachable.com/courses/*/lectures/6842364",
        contentType = "text/html",
        body = """
            <html><head><meta property="og:title" content="Fixture Lecture"></head>
            <body>
            <iframe src="//fast.wistia.net/embed/iframe/untlgzk1v7"></iframe>
            <li data-lecture-id="6842364" data-ss-position="1"><a href="/x">Overview</a></li>
            <div class="section-title">Welcome</div>
            </body></html>
        """.trimIndent(),
    )

    private val coursePage = FixtureRoute(
        urlPattern = "http://v1.upskillcourses.com/courses/essential-web-developer-course/",
        contentType = "text/html",
        body = """
            <html><body>
            <h1 class="course-title">Fixture Course</h1>
            <li class="section-item"><a href="/courses/essential-web-developer-course/lectures/111">
              <span class="lecture-name">Fixture Lecture One</span></a><i class="fa-youtube-play"></i></li>
            <li class="section-item"><a href="/courses/essential-web-developer-course/lectures/222">
              <span class="lecture-name">Fixture Lecture Two</span></a>12:34</li>
            <li class="section-item"><a href="/courses/essential-web-developer-course/lectures/333">
              <span class="lecture-name">Skipped</span></a></li>
            </body></html>
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val lecture = TeachableIE(http(transfer()))
        assertTrue(lecture.suitable(lectureUrl))
        assertTrue(lecture.suitable("http://v1.upskillcourses.com/courses/119763/lectures/1747100"))
        assertTrue(lecture.suitable(
            "teachable:https://v1.upskillcourses.com/courses/essential-web-developer-course/lectures/1747100",
        ))
        assertFalse(lecture.suitable(courseUrl))
        assertFalse(lecture.suitable("https://www.example.com/courses/x/lectures/1"))

        val course = TeachableCourseIE(http(transfer()))
        assertTrue(course.suitable(courseUrl))
        assertTrue(course.suitable("https://gns3.teachable.com/courses/enrolled/423415"))
        assertTrue(course.suitable("teachable:https://learn.vrdev.school/p/gear-vr-developer-mini"))
        assertFalse(course.suitable(lectureUrl), "the course class must yield lecture URLs")
    }

    // -------------------------------------------------------------- lecture

    @Test
    fun lectureYieldsTheWistiaEntry() = runTest {
        val info = TeachableIE(http(transfer(lecturePage))).extract(lectureUrl)
        assertEquals("6842364", info.id)
        assertEquals("Fixture Lecture", info.title)
        assertEquals(1, info.entries.size)
        assertEquals("https://fast.wistia.net/embed/iframe/untlgzk1v7", info.entries[0].url)
    }

    @Test
    fun lectureIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = lectureUrl,
            infoDict = mapOf(
                "id" to Expect.Value("6842364"),
                "title" to Expect.Value("Fixture Lecture"),
            ),
            routes = listOf(lecturePage),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> TeachableIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun prefixedLectureUsesTheWistiaIdRegex() = runTest {
        val prefixedPage = FixtureRoute(
            urlPattern = "https://v1.upskillcourses.com/courses/*/lectures/1747100",
            contentType = "text/html",
            body = """
                <html><head><meta property="og:title" content="Fixture Prefixed"></head>
                <body><script>Wistia.embed('abcdefghij')</script></body></html>
            """.trimIndent(),
        )
        val info = TeachableIE(http(transfer(prefixedPage))).extract(
            "teachable:https://v1.upskillcourses.com/courses/essential-web-developer-course/lectures/1747100",
        )
        assertEquals("1747100", info.id)
        assertEquals(1, info.entries.size)
        assertEquals("wistia:abcdefghij", info.entries[0].url)
    }

    @Test
    fun lockedLectureFailsTyped() = runTest {
        val lockedPage = FixtureRoute(
            urlPattern = "https://gns3.teachable.com/courses/*/lectures/6842364",
            contentType = "text/html",
            body = """
                <html><body><div class="lecture-contents-locked">Locked</div></body></html>
            """.trimIndent(),
        )
        val error = assertFailsWith<ExtractionError.LoginRequired> {
            TeachableIE(http(transfer(lockedPage))).extract(lectureUrl)
        }
        assertTrue(error.message!!.contains("Lecture contents locked"), error.message)
    }

    // --------------------------------------------------------------- course

    @Test
    fun courseYieldsTheLectureEntries() = runTest {
        val info = TeachableCourseIE(http(transfer(coursePage))).extract(courseUrl)
        assertEquals("essential-web-developer-course", info.id)
        assertEquals("Fixture Course", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("111", info.entries[0].id)
        assertEquals("Fixture Lecture One", info.entries[0].title)
        assertEquals(
            "https://v1.upskillcourses.com/courses/essential-web-developer-course/lectures/111",
            info.entries[0].url,
        )
        assertEquals("222", info.entries[1].id)
        assertEquals(
            "https://v1.upskillcourses.com/courses/essential-web-developer-course/lectures/222",
            info.entries[1].url,
        )
    }

    @Test
    fun prefixedCourseKeepsThePrefixOnEntries() = runTest {
        val prefixedPage = FixtureRoute(
            urlPattern = "https://learn.vrdev.school/p/gear-vr-developer-mini",
            contentType = "text/html",
            body = """
                <html><body>
                <h1 class="course-title">Fixture Prefixed Course</h1>
                <li class="section-item"><a href="https://learn.vrdev.school/courses/x/lectures/555">
                  <span class="lecture-name">Fixture Prefixed Lecture</span></a>05:00</li>
                </body></html>
            """.trimIndent(),
        )
        val info = TeachableCourseIE(http(transfer(prefixedPage))).extract(
            "teachable:https://learn.vrdev.school/p/gear-vr-developer-mini",
        )
        assertEquals("gear-vr-developer-mini", info.id)
        assertEquals(1, info.entries.size)
        assertEquals(
            "teachable:https://learn.vrdev.school/courses/x/lectures/555",
            info.entries[0].url,
        )
    }
}
