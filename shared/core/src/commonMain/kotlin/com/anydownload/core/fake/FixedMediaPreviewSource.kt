package com.anydownload.core.fake

import com.anydownload.core.MediaPreview
import com.anydownload.core.MediaPreviewResult
import com.anydownload.core.MediaPreviewSource

/**
 * A preview that does not touch the network. Non-desktop hosts and UI tests
 * use it so the preview page can render without yt-dlp.
 */
class FixedMediaPreviewSource : MediaPreviewSource {
    override suspend fun load(url: String): MediaPreviewResult = MediaPreviewResult.Ready(
        MediaPreview(
            pageUrl = url,
            title = TITLE,
            channel = "Example channel",
            durationSeconds = 65,
            extractor = "generic",
            description = "A short description of the source.",
            viewCount = 1200,
            uploadDate = "2008-01-01",
        ),
    )

    companion object {
        const val TITLE = "Preview title"
    }
}
