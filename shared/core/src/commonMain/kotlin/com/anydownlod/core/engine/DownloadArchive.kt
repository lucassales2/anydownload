/*
 * Download archive — AnyDownload (T-017, E-25)
 *
 * Translation of the `--download-archive` bookkeeping in
 * `yt_dlp/YoutubeDL.py` (`in_download_archive`, `record_download_archive`) at
 * upstream tag `2026.08.19` (commit 3a08beaf031ab68f966401ead017ac81fe8486cf),
 * read 2026-09-30. Unlicense; see shared/core/NOTICE.md.
 *
 * The archive records one line per downloaded media as `<extractor> <id>`
 * (upstream's format). It carries no cookie, token, signed URL, or private
 * path. The file itself lives beside app history and is owned by the host;
 * commonMain only asks [contains] and calls [add].
 */
package com.anydownlod.core.engine

/** One downloaded media's archive identity: the extractor key and media id. */
data class ArchiveEntry(
    val extractorKey: String,
    val mediaId: String,
) {
    /** Upstream's line shape: `youtube abcdefghijk`. */
    fun line(): String = "$extractorKey $mediaId"

    companion object {
        fun of(extractorKey: String?, mediaId: String?): ArchiveEntry? {
            val key = extractorKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val id = mediaId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (key.contains(' ') || id.contains(' ') || id.contains('\n')) return null
            return ArchiveEntry(key, id)
        }
    }
}

/**
 * The host-owned archive. A host without a file uses [NoDownloadArchive] and
 * every job downloads normally. Implementations may do bounded I/O and must
 * treat an unreadable file as empty rather than failing a job.
 */
interface DownloadArchive {
    fun contains(entry: ArchiveEntry): Boolean
    fun add(entry: ArchiveEntry)
}

/** The default: nothing is archived, every job downloads. */
object NoDownloadArchive : DownloadArchive {
    override fun contains(entry: ArchiveEntry): Boolean = false
    override fun add(entry: ArchiveEntry) = Unit
}
