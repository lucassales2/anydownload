package com.anydownlod.ui.persist

import com.anydownlod.core.persist.JobDocumentStorageError
import com.anydownlod.core.persist.SubscriptionDocumentStorage
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.mkdir

/**
 * The iOS subscriptions document: one UTF-8 file under Application Support,
 * beside the jobs document (T-019).
 */
@OptIn(ExperimentalForeignApi::class)
class IosSubscriptionDocumentStorage(
    private val path: String,
) : SubscriptionDocumentStorage {

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
            mkdir(current, 0x1EDu)
        }
    }
}
