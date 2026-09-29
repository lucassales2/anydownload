package com.anydownlod.ui.persist

import com.anydownlod.core.persist.JobDocumentStorage
import com.anydownlod.core.persist.JobDocumentStorageError
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.mkdir

/**
 * The iOS jobs document: one UTF-8 plist-free text file under Application
 * Support, which is not the Documents download root (T-103).
 *
 * Writes go through Foundation's atomic write so a crash cannot leave a
 * half-written document. A refused write returns a typed error instead of
 * throwing, and the parent directory is created on first use.
 */
@OptIn(ExperimentalForeignApi::class)
class IosJobDocumentStorage(
    private val path: String,
) : JobDocumentStorage {

    /** The file this store reads and writes; useful to assert the location. */
    val filePath: String get() = path

    init {
        createParentDirectories()
    }

    override fun read(): String? =
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) as String?

    override fun write(document: String): JobDocumentStorageError? = try {
        createParentDirectories()
        val written = (document as NSString).writeToFile(
            path = path,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        if (written) null else JobDocumentStorageError.UNAVAILABLE
    } catch (failure: Throwable) {
        JobDocumentStorageError.UNAVAILABLE
    }

    private fun createParentDirectories() {
        val directory = path.substringBeforeLast('/', "")
        if (directory.isEmpty()) return
        var current = ""
        for (segment in directory.split('/')) {
            if (segment.isEmpty()) continue
            current = "$current/$segment"
            mkdir(current, 0x1EDu) // 0755; already-exists is fine
        }
    }
}
