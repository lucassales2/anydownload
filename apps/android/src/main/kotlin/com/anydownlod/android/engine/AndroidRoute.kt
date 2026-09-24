package com.anydownlod.android.engine

/**
 * Where an Android job goes when it is submitted.
 *
 * [DIRECT_FILE] and [KOTLIN] are fetched by the shared Kotlin HTTP engine;
 * [KOTLIN] means a registry extractor owns the URL. [CHAQUOPY] goes to the
 * embedded pinned yt-dlp adapter, which exists only under `apps/android`.
 */
enum class AndroidRoute {
    DIRECT_FILE,
    KOTLIN,
    CHAQUOPY,
}