package com.anydownload.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.anydownload.core.domain.JobState
import com.anydownload.ui.history.HistoryRow
import com.anydownload.ui.i18n.UiText
import com.anydownload.ui.queue.QueueRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadLibraryTest {
    @Test
    fun finishedRowsGroupByMinuteThenHourThenDay() {
        val now = 20_000L * DAY + 18 * HOUR + 6 * MINUTE
        val minuteNew = now - 20_000L
        val minuteOld = now - 80_000L
        val hourNew = now - 90 * MINUTE
        val hourOld = now - 100 * MINUTE
        val older = now - 3 * DAY
        val items = libraryItems(
            filter = DownloadFilter.ALL,
            active = emptyList(),
            done = listOf(
                row("hour-new", "Hour new", hourNew),
                row("old", "Old", older),
                row("minute-old", "Minute old", minuteOld),
                row("minute-new", "Minute new", minuteNew),
                row("hour-old", "Hour old", hourOld),
            ),
            sort = LibrarySort.NEWEST,
            nowEpochMillis = now,
            offsetMillis = 0,
        )
        assertEquals(listOf("18:05", "18:04", "16:00", utcDateLabel(older)), headings(items))
        assertEquals(
            listOf("minute-new", "minute-old", "hour-new", "hour-old", "old"),
            doneIds(items),
        )
    }

    @Test
    fun oldestReversesTheGroups() {
        val now = 20_000L * DAY + 12 * HOUR
        val recent = now - 2 * MINUTE
        val duringDay = now - 3 * HOUR
        val older = now - 2 * DAY
        val items = libraryItems(
            filter = DownloadFilter.ALL,
            active = emptyList(),
            done = listOf(
                row("recent", "Recent", recent),
                row("day", "During day", duringDay),
                row("old", "Old", older),
            ),
            sort = LibrarySort.OLDEST,
            nowEpochMillis = now,
            offsetMillis = 0,
        )
        assertEquals(listOf(utcDateLabel(older), "09:00", "11:58"), headings(items))
        assertEquals(listOf("old", "day", "recent"), doneIds(items))
    }

    @Test
    fun ageBoundariesPickTheGrain() {
        val now = 20_000L * DAY + 18 * HOUR + 6 * MINUTE
        assertEquals(TimeGrain.MINUTE, timeGroup(now - HOUR + 1, now).grain)
        assertEquals(TimeGrain.HOUR, timeGroup(now - HOUR, now).grain)
        assertEquals("17:00", timeGroup(now - HOUR, now).label)
        assertEquals(TimeGrain.HOUR, timeGroup(now - DAY + 1, now).grain)
        assertEquals(TimeGrain.DAY, timeGroup(now - DAY, now).grain)
        assertEquals(TimeGrain.MINUTE, timeGroup(now + MINUTE, now).grain)
    }

    @Test
    fun minuteThatCrossesMidnightKeepsTheDate() {
        val now = 20_000L * DAY + 20 * MINUTE
        val previousEvening = now - 30 * MINUTE
        val label = timeGroup(previousEvening, now).label
        val date = utcDateLabel(previousEvening).substringBeforeLast(",")
        assertEquals("$date, 23:50", label)
    }

    @Test
    fun localOffsetShiftsTheClockAndTheDay() {
        val now = 20_000L * DAY + 18 * HOUR + 6 * MINUTE
        val recent = now - 30_000L
        val offset = (-3 * HOUR).toInt()
        assertEquals("15:05", timeGroup(recent, now, offset).label)

        val acrossMidnight = 20_000L * DAY + 3 * HOUR + 20 * MINUTE
        val previousEvening = acrossMidnight - 30 * MINUTE
        val shifted = timeGroup(previousEvening, acrossMidnight, offset)
        val date = utcDateLabel(previousEvening + offset).substringBeforeLast(",")
        assertEquals("$date, 23:50", shifted.label)

        val aged = acrossMidnight - 2 * DAY
        assertEquals(utcDateLabel(aged + offset), timeGroup(aged, acrossMidnight, offset).label)
    }

    @Test
    fun titleSortIsAlphabeticalAndDropsHeadings() {
        val now = 20_000L * DAY
        val items = libraryItems(
            filter = DownloadFilter.ALL,
            active = listOf(active("zeta", "zeta"), active("Alpha", "alpha")),
            done = listOf(
                row("b", "banana", now - HOUR),
                row("a", "Apple", now - DAY),
                row("a2", "apple", now),
            ),
            sort = LibrarySort.TITLE,
            nowEpochMillis = now,
            offsetMillis = 0,
        )
        assertTrue(items.none { it is LibraryItem.Heading })
        assertEquals(listOf("Alpha", "zeta"), items.filterIsInstance<LibraryItem.Active>().map { it.row.id })
        assertEquals(listOf("a", "a2", "b"), doneIds(items))
    }

    @Test
    fun filtersKeepActiveRowsOnAllAndDropUndatedHeadings() {
        val now = 20_000L * DAY + 8 * HOUR
        val done = listOf(
            row("saved", "Saved", now - 2 * DAY, JobState.COMPLETED),
            row("broken", "Broken", now - MINUTE, JobState.FAILED),
            row("stopped", "Stopped", null, JobState.CANCELLED),
        )
        val all = libraryItems(DownloadFilter.ALL, listOf(active("live")), done, LibrarySort.NEWEST, now, 0)
        assertEquals(listOf("live"), all.filterIsInstance<LibraryItem.Active>().map { it.row.id })
        assertEquals(listOf("broken", "saved", "stopped"), doneIds(all))
        assertEquals(1, all.count { it is LibraryItem.Heading && it.label == utcDateLabel(now - 2 * DAY) })

        val failed = libraryItems(DownloadFilter.FAILED, listOf(active("live")), done, LibrarySort.NEWEST, now, 0)
        assertTrue(failed.none { it is LibraryItem.Active })
        assertEquals(listOf("broken"), doneIds(failed))

        val complete = libraryItems(DownloadFilter.COMPLETE, emptyList(), done, LibrarySort.NEWEST, now, 0)
        assertEquals(listOf("saved"), doneIds(complete))
    }
}

@OptIn(ExperimentalTestApi::class)
class DownloadLibrarySortMenuTest {
    @Test
    fun menuSelectsAnotherSort() = runComposeUiTest {
        var sort = LibrarySort.NEWEST
        setContent {
            MaterialTheme {
                LibrarySortMenu(sort = sort, onSort = { sort = it })
            }
        }
        onNodeWithTag("download-sort").performClick()
        onNodeWithTag("download-sort-menu").assertExists()
        onNodeWithTag("download-sort-oldest").performClick()
        assertEquals(LibrarySort.OLDEST, sort)
    }
}

private const val MINUTE = 60_000L
private const val HOUR = 3_600_000L
private const val DAY = 86_400_000L

private fun headings(items: List<LibraryItem>): List<String> =
    items.filterIsInstance<LibraryItem.Heading>().map { it.label }

private fun doneIds(items: List<LibraryItem>): List<String> =
    items.filterIsInstance<LibraryItem.Done>().map { it.row.id }

private fun row(
    id: String,
    title: String,
    finishedAtEpochMillis: Long?,
    state: JobState = JobState.COMPLETED,
) = HistoryRow(
    id = id,
    title = title,
    sourceUrl = "https://example.com/$id",
    sourceHost = "example.com",
    state = state,
    stateLabel = UiText.raw(state.name),
    errorMessage = null,
    canRetry = state == JobState.FAILED,
    finishedAtEpochMillis = finishedAtEpochMillis,
    artifacts = emptyList(),
)

private fun active(id: String, title: String = id) = QueueRow(
    id = id,
    title = title,
    sourceUrl = "https://example.com/$id",
    sourceHost = "example.com",
    state = JobState.DOWNLOADING,
    stateLabel = UiText.raw("Downloading"),
    phase = null,
    percent = 10.0,
    downloadedBytes = null,
    totalBytes = null,
    speedBytesPerSecond = null,
    etaSeconds = null,
    scheduledAtEpochMillis = null,
    canStart = false,
    cancelNeedsConfirm = true,
)
