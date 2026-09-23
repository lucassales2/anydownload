package com.anydownlod.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JobStateTest {
    @Test
    fun terminalStatesAreClassified() {
        assertTrue(JobState.COMPLETED.isTerminal)
        assertTrue(JobState.FAILED.isTerminal)
        assertTrue(JobState.CANCELLED.isTerminal)
        assertFalse(JobState.DOWNLOADING.isTerminal)
        assertFalse(JobState.UNKNOWN.isTerminal)
    }

    @Test
    fun activeStatesAreClassified() {
        assertTrue(JobState.QUEUED.isActive)
        assertTrue(JobState.DOWNLOADING.isActive)
        assertTrue(JobState.POSTPROCESSING.isActive)
        assertFalse(JobState.PENDING.isActive)
        assertFalse(JobState.SCHEDULED.isActive)
        assertFalse(JobState.COMPLETED.isActive)
    }

    @Test
    fun wireNamesMapBothWaysForKnownStates() {
        JobState.entries.filter { it != JobState.UNKNOWN }.forEach { state ->
            assertEquals(state, JobState.fromWire(state.wireName))
        }
    }

    @Test
    fun unknownWireValuesStayForwardCompatible() {
        assertEquals(JobState.UNKNOWN, JobState.fromWire("paused_by_engine"))
        assertEquals(JobState.DOWNLOADING, JobState.fromWire("DOWNLOADING"))
    }
}
