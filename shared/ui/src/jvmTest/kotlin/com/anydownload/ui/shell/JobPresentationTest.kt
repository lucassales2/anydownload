package com.anydownload.ui.shell

import com.anydownload.core.domain.JobState
import com.anydownload.ui.generated.resources.Res
import com.anydownload.ui.generated.resources.job_postprocessing
import com.anydownload.ui.generated.resources.job_waiting
import com.anydownload.ui.i18n.UiText
import kotlin.test.Test
import kotlin.test.assertEquals

class JobPresentationTest {

    @Test
    fun bytesUseDecimalUnits() {
        assertEquals("999 B", formatBytes(999))
        assertEquals("1.0 KB", formatBytes(1000))
        assertEquals("1.5 MB", formatBytes(1_500_000))
        assertEquals("1.2 MB/s", formatSpeed(1_200_000.0))
    }

    @Test
    fun etaIsCompact() {
        assertEquals("45s", formatEta(45))
        assertEquals("1m 30s", formatEta(90))
        assertEquals("1h 02m", formatEta(3720))
    }

    @Test
    fun dueTimeIsStaticUtc() {
        assertEquals("1970-01-01 00:00 UTC", formatDueTime(0))
        assertEquals("2026-01-01 00:00 UTC", formatDueTime(1_767_225_600_000))
    }

    @Test
    fun stateLabelsKeepUnknownRaw() {
        assertEquals(UiText.raw("unknown"), JobState.UNKNOWN.displayLabel())
        assertEquals(UiText.of(Res.string.job_postprocessing), JobState.POSTPROCESSING.displayLabel())
        assertEquals(UiText.of(Res.string.job_waiting), JobState.SCHEDULED.displayLabel())
    }
}
