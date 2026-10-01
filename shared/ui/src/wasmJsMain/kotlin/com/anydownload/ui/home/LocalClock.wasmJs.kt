package com.anydownload.ui.home

internal actual fun localUtcOffsetMillis(epochMillis: Long): Int = localOffset(epochMillis.toDouble())

/** `getTimezoneOffset` is minutes to add to local time to reach UTC, so the sign flips. */
private fun localOffset(epochMillis: Double): Int =
    js("(-new Date(epochMillis).getTimezoneOffset() * 60000) | 0")
