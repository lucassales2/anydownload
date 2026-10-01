package com.anydownload.ui.export

import com.anydownload.core.domain.DownloadJob

/**
 * Explicit source-URL export for the queue and history (T-109).
 *
 * The text is built only from the URL the user submitted. It never derives a
 * media URL, never adds request headers, and never reads cookie material. A
 * copy happens only when a screen calls these functions from an explicit
 * action; nothing here runs on row render.
 */
object JobSourceUrls {

    /** One URL per selected row, in engine row order, failed rows included. */
    fun forSelected(jobs: List<DownloadJob>, ids: Set<String>): List<String> =
        jobs.filter { it.id in ids }.map { it.request.sourceUrl }

    /**
     * Every row that shares a `parentBatchId` with a selected row, failed
     * children included, in engine row order. Rows outside those batches are
     * excluded; a selection with no batch id has nothing to copy.
     */
    fun forBatches(jobs: List<DownloadJob>, ids: Set<String>): List<String> {
        val batches = jobs.filter { it.id in ids }.mapNotNull { it.parentBatchId }.toSet()
        if (batches.isEmpty()) return emptyList()
        return jobs.filter { it.parentBatchId in batches }.map { it.request.sourceUrl }
    }

    /** Newline text for the clipboard; empty when there is nothing to copy. */
    fun text(urls: List<String>): String = urls.joinToString("\n")
}
