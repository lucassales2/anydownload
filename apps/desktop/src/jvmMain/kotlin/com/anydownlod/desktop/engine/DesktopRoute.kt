package com.anydownlod.desktop.engine

/**
 * Where a desktop job goes when it is submitted.
 *
 * [DIRECT_FILE] and [KOTLIN] jobs are handled by the shared Kotlin engine and
 * never spawn a process; [KOTLIN] means a registry extractor owns the URL.
 * [YTDLP_CLI] jobs keep the D1 installed-yt-dlp path, including its
 * missing-tool behavior.
 */
enum class DesktopRoute {
    DIRECT_FILE,
    KOTLIN,
    YTDLP_CLI,
}