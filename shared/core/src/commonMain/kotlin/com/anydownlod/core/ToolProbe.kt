package com.anydownlod.core

import com.anydownlod.core.domain.ToolStatus

/**
 * Reports whether `yt-dlp` and `ffmpeg` are available on the local machine.
 *
 * Shared code must not spawn a process: the fake reports both missing, and the
 * desktop host replaces it with a PATH probe in T-033.
 */
interface ToolProbe {
    suspend fun probe(): ToolStatus
}
