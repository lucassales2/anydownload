package com.anydownlod.android.engine

/**
 * Where an Android job goes when it is submitted.
 *
 * [DIRECT_FILE] is fetched by the shared Kotlin HTTP engine. [CHAQUOPY] goes
 * to the embedded pinned yt-dlp adapter, which exists only under
 * `apps/android`.
 */
enum class AndroidRoute {
    DIRECT_FILE,
    CHAQUOPY,
}