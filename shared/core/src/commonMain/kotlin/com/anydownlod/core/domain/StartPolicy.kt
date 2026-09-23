package com.anydownlod.core.domain

/**
 * How a job starts after submission. [AUTOMATIC] queues it immediately;
 * [MANUAL] keeps it pending until the person presses Start.
 */
enum class StartPolicy(val wireName: String) {
    AUTOMATIC("automatic"),
    MANUAL("manual"),
}
