/*
 * Spotify metadata preview — AnyDownload
 *
 * Wraps the Spotify download service's resolve step in the existing
 * [MediaPreviewSource] seam so the shared Preview screen lists the songs and
 * the App can queue them on Download. A non-Spotify input returns
 * [PreviewFailure.Unavailable] so the composite source can fall through to
 * the extractor path. No download happens here.
 */
package com.anydownlod.core

import com.anydownlod.core.music.SpotifyDownloadService
import com.anydownlod.core.music.SpotifyQueryParser
import kotlinx.coroutines.CancellationException

class SpotifyMediaPreviewSource(
    private val service: SpotifyDownloadService,
) : MediaPreviewSource {

    override suspend fun load(url: String): MediaPreviewResult {
        if (!SpotifyQueryParser.isSpotifyInput(url)) {
            return MediaPreviewResult.Failed(PreviewFailure.Unavailable)
        }
        return try {
            val preview = service.preview(url)
            MediaPreviewResult.Ready(
                MediaPreview(
                    pageUrl = url,
                    title = preview.name,
                    thumbnailUrl = preview.artworkUrl,
                    extractor = "spotify",
                    playlist = preview.isList,
                    entryCount = preview.entries.size,
                    spotify = preview,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            MediaPreviewResult.Failed(PreviewFailure.Failed)
        }
    }
}
