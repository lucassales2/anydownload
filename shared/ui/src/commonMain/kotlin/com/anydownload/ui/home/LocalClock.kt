package com.anydownload.ui.home

/** Milliseconds to add to a UTC instant so calendar fields match local time. */
internal expect fun localUtcOffsetMillis(epochMillis: Long): Int
