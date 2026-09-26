package com.anydownlod.core

import com.anydownlod.core.extract.ExtractionError
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.InfoDict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Metadata preview from the shared Kotlin extractor.
 *
 * A URL no registered extractor matches fails with
 * [PreviewFailure.Unavailable] so a host can fall through to its own source
 * (the desktop CLI, Android Chaquopy). A matched URL never falls through: a
 * failure there is [PreviewFailure.Failed], because a host must not send a
 * matched URL to a fallback. The lookup is bounded and cancellable, and it
 * only reads metadata; the media URL is never requested.
 */
class ExtractorMediaPreviewSource(
    private val registry: ExtractorRegistry,
    private val timeoutMillis: Long = 20_000,
) : MediaPreviewSource {

    override suspend fun load(url: String): MediaPreviewResult {
        registry.suitableFor(url) ?: return MediaPreviewResult.Failed(PreviewFailure.Unavailable)
        return try {
            val info = withTimeout(timeoutMillis) { registry.extract(url) }
            MediaPreviewResult.Ready(previewOf(info, url))
        } catch (timeout: TimeoutCancellationException) {
            MediaPreviewResult.Failed(PreviewFailure.TimedOut)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ExtractionError) {
            MediaPreviewResult.Failed(PreviewFailure.Failed)
        } catch (_: Exception) {
            MediaPreviewResult.Failed(PreviewFailure.Failed)
        }
    }

    private fun previewOf(info: InfoDict, url: String): MediaPreview = MediaPreview(
        pageUrl = info.webpageUrl ?: url,
        title = info.title?.takeIf { it.isNotBlank() } ?: url,
        thumbnailUrl = info.thumbnails.lastOrNull()?.url
            ?: info.media.firstOrNull()?.thumbnails?.lastOrNull()?.url,
        channel = info.channel ?: info.uploader,
        durationSeconds = info.duration?.toLong(),
        extractor = info.extractorKey,
        description = info.description,
        viewCount = info.viewCount,
        uploadDate = formatUploadDate(info.uploadDate),
        playlist = false,
        entryCount = null,
        availableFormats = FormatChoices.from(info),
        videos = info.media.map { media ->
            MediaPreviewVideo(
                mediaId = media.mediaId,
                title = media.title,
                durationSeconds = media.duration?.toLong(),
                thumbnailUrl = media.thumbnails.lastOrNull()?.url,
            )
        },
    )

    /** Upstream `YYYYMMDD` to the display form the CLI preview also uses. */
    private fun formatUploadDate(value: String?): String? {
        val text = value?.trim() ?: return null
        if (text.length == 8 && text.all { it.isDigit() }) {
            return "${text.substring(0, 4)}-${text.substring(4, 6)}-${text.substring(6, 8)}"
        }
        return text.ifEmpty { null }
    }
}

/**
 * Tries [primary] first and [fallback] only when the primary has no extractor
 * for the URL. A matched URL that fails metadata never reaches the fallback.
 */
class CompositeMediaPreviewSource(
    private val primary: MediaPreviewSource,
    private val fallback: MediaPreviewSource,
) : MediaPreviewSource {
    override suspend fun load(url: String): MediaPreviewResult {
        val first = primary.load(url)
        return if (first is MediaPreviewResult.Failed && first.failure == PreviewFailure.Unavailable) {
            fallback.load(url)
        } else {
            first
        }
    }
}
