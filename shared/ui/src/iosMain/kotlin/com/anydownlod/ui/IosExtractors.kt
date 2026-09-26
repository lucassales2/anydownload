/*
 * iOS extractor set — AnyDownload
 *
 * The shared Kotlin extractors iOS owns. The app graph builds its registry
 * through here so the simulator test can prove the same wiring with a fixture
 * transfer. D7 adds the X/Twitter status extractor.
 */
package com.anydownlod.ui

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.twitter.TwitterIE
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.jsc.JsRuntime
import com.anydownlod.core.platform.HttpTransfer

object IosExtractors {

    /**
     * The ordered registry: YouTube, then X/Twitter. iOS has no Python or CLI
     * fallback, so an unmatched URL keeps the engine's typed unsupported error.
     */
    fun registry(transfer: HttpTransfer, jsRuntime: JsRuntime): ExtractorRegistry = ExtractorRegistry(
        listOf(
            YoutubeIE(ExtractorHttp(transfer), jsRuntime),
            TwitterIE(ExtractorHttp(transfer)),
        ),
    )
}
