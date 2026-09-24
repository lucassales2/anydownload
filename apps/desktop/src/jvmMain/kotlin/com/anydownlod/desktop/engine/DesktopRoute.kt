package com.anydownlod.desktop.engine

/**
 * Where a desktop job goes when it is submitted.
 *
 * [DIRECT_FILE] jobs are fetched by the shared Kotlin HTTP engine and never
 * spawn a process. [YTDLP_CLI] jobs keep the D1 installed-yt-dlp path,
 * including its missing-tool behavior.
 */
enum class DesktopRoute {
    DIRECT_FILE,
    YTDLP_CLI,
}