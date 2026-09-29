package com.anydownlod.desktop.engine

import com.anydownlod.core.CompositeMediaPreviewSource
import com.anydownlod.core.ExtractorMediaPreviewSource
import com.anydownlod.core.MediaPreviewSource
import com.anydownlod.core.extract.ExtractorRegistry
import java.nio.file.Path

/**
 * T-063 desktop preview routing: the shared Kotlin extractor first, the
 * installed CLI only for URLs no extractor matches. A URL the Kotlin registry
 * matches never reaches the process, so YouTube metadata comes from the port.
 *
 * T-115/T-116: the registry is the graph's registry, passed in by the caller;
 * this type no longer creates its own transfer, runtime, or extractor list.
 * [registry] is exposed so the desktop host test can prove previews and the
 * routing engine share one instance.
 */
internal class DesktopPreviewSource(
    val registry: ExtractorRegistry,
    private val delegate: MediaPreviewSource,
) : MediaPreviewSource by delegate {

    companion object {
        fun create(
            registry: ExtractorRegistry,
            runner: CliProcessRunner,
            resolveExecutable: (String) -> String?,
            workingDirectory: () -> Path,
            kotlinTimeoutMillis: Long = 20_000,
        ): DesktopPreviewSource = DesktopPreviewSource(
            registry = registry,
            delegate = CompositeMediaPreviewSource(
                primary = ExtractorMediaPreviewSource(registry, timeoutMillis = kotlinTimeoutMillis),
                fallback = YtDlpMediaPreviewSource(
                    runner = runner,
                    resolveExecutable = resolveExecutable,
                    workingDirectory = workingDirectory,
                ),
            ),
        )
    }
}
