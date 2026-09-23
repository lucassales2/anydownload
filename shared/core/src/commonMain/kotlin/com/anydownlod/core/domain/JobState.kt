package com.anydownlod.core.domain

/**
 * States for a local download job.
 *
 * [RESOLVING], [QUEUED], [DOWNLOADING], and [POSTPROCESSING] are active work.
 * [PENDING] waits for a manual start and [SCHEDULED] waits for the source to
 * become available. [COMPLETED], [FAILED], and [CANCELLED] are terminal.
 * [UNKNOWN] keeps a row written by a newer store or engine visible instead of
 * hiding it.
 */
enum class JobState(val wireName: String) {
    RESOLVING("resolving"),
    PENDING("pending"),
    SCHEDULED("scheduled"),
    QUEUED("queued"),
    DOWNLOADING("downloading"),
    POSTPROCESSING("postprocessing"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    UNKNOWN("unknown"),
    ;

    /** No further state transitions are expected. */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED

    /** An engine worker may be doing work; the job can still make progress. */
    val isActive: Boolean
        get() = this == RESOLVING || this == QUEUED || this == DOWNLOADING || this == POSTPROCESSING

    companion object {
        fun fromWire(value: String): JobState =
            entries.firstOrNull { it.wireName == value.lowercase() } ?: UNKNOWN
    }
}
