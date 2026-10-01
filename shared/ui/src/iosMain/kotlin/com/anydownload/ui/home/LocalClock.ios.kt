package com.anydownload.ui.home

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTimeZoneCopySystem
import platform.CoreFoundation.CFTimeZoneGetSecondsFromGMT

/** Seconds from 1970-01-01 to 2001-01-01, the CoreFoundation absolute-time epoch. */
private const val UNIX_TO_CF_ABSOLUTE_SECONDS = 978307200.0

@OptIn(ExperimentalForeignApi::class)
internal actual fun localUtcOffsetMillis(epochMillis: Long): Int {
    val zone = CFTimeZoneCopySystem() ?: return 0
    return try {
        val absolute = epochMillis.toDouble() / 1000.0 - UNIX_TO_CF_ABSOLUTE_SECONDS
        CFTimeZoneGetSecondsFromGMT(zone, absolute).toInt() * 1000
    } finally {
        CFRelease(zone)
    }
}
