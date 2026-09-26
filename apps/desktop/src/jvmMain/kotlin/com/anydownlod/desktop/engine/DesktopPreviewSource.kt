package com.anydownlod.desktop.engine

import com.anydownlod.core.CompositeMediaPreviewSource
import com.anydownlod.core.ExtractorMediaPreviewSource
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.youtube.YoutubeIE
import com.anydownlod.core.extract.twitter.TwitterIE
import com.anydownlod.core.platform.HttpTransfer
import com.anydownlod.core.platform.JavaNetHttpTransfer
import java.nio.file.Path

/**
 * T-063 desktop preview routing: the shared Kotlin extractor first, the
 * installed CLI only for URLs no extractor matches. A URL the Kotlin registry
 * matches never reaches the process, so YouTube metadata comes from the port.
 */
internal object DesktopPreviewSource {

    fun create(
        runner: CliProcessRunner,
        resolveExecutable: (String) -> String?,
        workingDirectory: () -> Path,
        transfer: HttpTransfer = JavaNetHttpTransfer(),
        kotlinTimeoutMillis: Long = 20_000,
        jsRuntime: com.anydownlod.core.jsc.JsRuntime = com.anydownlod.core.jsc.NoJsRuntime,
    ): MediaPreviewSource {
        val registry = ExtractorRegistry(
            listOf(YoutubeIE(ExtractorHttp(transfer), jsRuntime), TwitterIE(ExtractorHttp(transfer))),
        )
        return CompositeMediaPreviewSource(
            primary = ExtractorMediaPreviewSource(registry, timeoutMillis = kotlinTimeoutMillis),
            fallback = YtDlpMediaPreviewSource(
                runner = runner,
                resolveExecutable = resolveExecutable,
                workingDirectory = workingDirectory,
            ),
        )
    }
}
