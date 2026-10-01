package com.anydownload.desktop

import com.anydownload.desktop.engine.DesktopPreviewSource
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * T-116: the desktop graph owns exactly one production `ExtractorRegistry`.
 * The preview source is the graph's preview binding, so it must hold that
 * same instance — not a second list built inside the preview helper.
 */
class DesktopGraphRegistryTest {

    @Test
    fun previewsUseTheGraphRegistry() {
        val directory = Files.createTempDirectory("anydownlod-desktop-graph-")
        val desktop = DesktopApp.open(
            stateDirectory = directory,
            defaultDownloadRoot = { directory.resolve("downloads").toString() },
            resolveExecutable = { null },
        )
        try {
            val graph = assertIs<DesktopGraph>(desktop.graph)
            val previews = assertIs<DesktopPreviewSource>(graph.previews)

            assertSame(graph.extractorRegistry, previews.registry)
        } finally {
            desktop.close()
        }
    }
}
