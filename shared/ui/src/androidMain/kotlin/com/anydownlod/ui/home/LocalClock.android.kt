package com.anydownlod.ui.home

import java.time.Instant
import java.time.ZoneId

internal actual fun localUtcOffsetMillis(epochMillis: Long): Int =
    ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(epochMillis)).totalSeconds * 1000
