/*
 * Android extractor set — AnyDownload
 *
 * The shared Kotlin extractors Android owns. Kept in the pure `engine`
 * package (no Android imports) so the JVM-equivalent tests can prove the same
 * registry the app graph builds. D7 adds the X/Twitter status extractor.
 */
package com.anydownlod.android.engine

import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.twitter.TwitterIE
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.jsc.JsRuntime
import com.anydownlod.core.platform.HttpTransfer

object AndroidExtractors {

    /**
     * The ordered registry: YouTube, then X/Twitter, with no generic fallback
     * (the route classifier sends an unmatched URL to Chaquopy instead).
     */
    fun registry(transfer: HttpTransfer, jsRuntime: JsRuntime): ExtractorRegistry = ExtractorRegistry(
        listOf(
            YoutubeIE(ExtractorHttp(transfer), jsRuntime),
            TwitterIE(ExtractorHttp(transfer)),
        ),
    )
}
