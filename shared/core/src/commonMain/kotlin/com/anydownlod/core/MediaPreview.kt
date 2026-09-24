package com.anydownlod.core

/**
 * Metadata shown before a download starts.
 *
 * [thumbnailUrl] is a remote image address for the host to fetch. It is not
 * persisted and is not treated as a secret, but it must not be written into logs.
 */
data class MediaPreview(
    val pageUrl: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val channel: String? = null,
    val durationSeconds: Long? = null,
    val extractor: String? = null,
    val description: String? = null,
    val viewCount: Long? = null,
    /** Display date, already formatted when the source provided one. */
    val uploadDate: String? = null,
    val playlist: Boolean = false,
    val entryCount: Int? = null,
    /**
     * What the Kotlin extractor can satisfy as one download. Null for sources
     * that do not extract formats (the CLI fallback, legacy previews); the
     * Edit panel then keeps its static choices.
     */
    val availableFormats: FormatChoices? = null,
)

/** Why a preview could not be shown. The UI maps each case to a short message. */
enum class PreviewFailure {
    /** The extractor is not installed on this device. */
    Unavailable,

    /** The lookup exceeded its time limit. */
    TimedOut,

    /** The source did not return usable metadata. */
    Failed,
}

sealed interface MediaPreviewResult {
    data class Ready(val preview: MediaPreview) : MediaPreviewResult
    data class Failed(val failure: PreviewFailure) : MediaPreviewResult
}

/**
 * Bounded metadata lookup for one source URL. Implementations must not start
 * a download and must not return process output or cookie material.
 */
interface MediaPreviewSource {
    suspend fun load(url: String): MediaPreviewResult
}

/** Used where no extractor is wired. The download can still be started. */
object UnavailableMediaPreviewSource : MediaPreviewSource {
    override suspend fun load(url: String): MediaPreviewResult =
        MediaPreviewResult.Failed(PreviewFailure.Unavailable)
}
