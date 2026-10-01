/*
 * iOS cookie store — AnyDownload (T-018)
 *
 * One `cookies.txt` under Application Support. Import validates with the
 * shared Netscape rules, rejects anything else without a partial import,
 * overwrites on replace, and deletes on request. The file is plain text:
 * there is no Keychain wrapper around it (owner decision, 2026-09-30). The
 * file is marked excluded from iCloud/iTunes backup at write time.
 *
 * The document picker copies the user's chosen file into the app temporary
 * directory (`asCopy = true`); the store removes that copy after the import
 * so credential bytes do not linger.
 */
package com.anydownload.ui.cookies

import com.anydownload.core.CookieOperationResult
import com.anydownload.core.CookieErrorReason
import com.anydownload.core.CookieStatus
import com.anydownload.core.CookieStore
import com.anydownload.core.cookies.CookieJar
import com.anydownload.core.cookies.CookieJarSource
import com.anydownload.core.cookies.CookieJarState
import com.anydownload.core.cookies.NetscapeCookieFile
import com.anydownload.core.cookies.NetscapeCookieFileError
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
class IosCookieStore(
    private val stateDirectory: String,
    private val importDirectory: String? = null,
) : CookieStore {

    /** The one stored file; never logged together with cookie contents. */
    val filePath: String get() = "${stateDirectory.trimEnd('/')}/$FILE_NAME"

    override fun import(sourcePath: String): CookieOperationResult {
        val manager = NSFileManager.defaultManager
        if (!manager.fileExistsAtPath(sourcePath)) {
            return failure("Choose an existing cookie file.")
        }

        val size = fileSize(sourcePath) ?: run {
            deleteImportedCopy(sourcePath)
            return failure("The cookie file could not be read.")
        }
        if (size == 0L) {
            deleteImportedCopy(sourcePath)
            return failure("The cookie file is empty.")
        }
        if (size > NetscapeCookieFile.MAX_BYTES) {
            deleteImportedCopy(sourcePath)
            return failure("The cookie file is larger than 1 MiB.")
        }

        val text = NSString.stringWithContentsOfFile(sourcePath, NSUTF8StringEncoding, null) as String?
        if (text == null) {
            deleteImportedCopy(sourcePath)
            return failure("The cookie file could not be read.")
        }
        val validation = NetscapeCookieFile.validate(text)
        if (validation != null) {
            deleteImportedCopy(sourcePath)
            return failure(messageFor(validation))
        }

        val result = storeText(text)
        deleteImportedCopy(sourcePath)
        return result
    }

    override fun importText(text: String): CookieOperationResult {
        val bytes = text.encodeToByteArray()
        if (bytes.isEmpty()) return failure("The cookie file is empty.")
        if (bytes.size.toLong() > NetscapeCookieFile.MAX_BYTES) {
            return failure("The cookie file is larger than 1 MiB.")
        }
        val validation = NetscapeCookieFile.validate(text)
        if (validation != null) return failure(messageFor(validation))
        return storeText(text)
    }

    private fun storeText(text: String): CookieOperationResult {
        createStateDirectory()
        val written = (text as NSString).writeToFile(
            path = filePath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        if (!written) return failure("The cookie file could not be stored.")
        excludeFromBackup()
        return CookieOperationResult(success = true)
    }

    override fun delete(): CookieOperationResult {
        // An open stream keeps the bytes for an in-flight job; a new job sees
        // the deletion immediately, exactly like the desktop store.
        runCatching { NSFileManager.defaultManager.removeItemAtPath(filePath, null) }
        return CookieOperationResult(success = true)
    }

    override fun storedFilePath(): String? =
        filePath.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }

    override fun status(): CookieStatus {
        if (!NSFileManager.defaultManager.fileExistsAtPath(filePath)) {
            return CookieStatus.NotConfigured
        }
        val text = NSString.stringWithContentsOfFile(filePath, NSUTF8StringEncoding, null) as String?
            ?: return CookieStatus.Error(CookieErrorReason.UNREADABLE)
        if (NetscapeCookieFile.validate(text) != null) {
            return CookieStatus.Error(CookieErrorReason.UNREADABLE)
        }
        val nowSeconds = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000L
        return when (CookieJar.fromText(text).stateAt(nowSeconds)) {
            CookieJarState.READY -> CookieStatus.Configured
            CookieJarState.ALL_EXPIRED -> CookieStatus.Error(CookieErrorReason.ALL_EXPIRED)
            CookieJarState.EMPTY -> CookieStatus.Error(CookieErrorReason.NO_COOKIES)
        }
    }

    /** The parsed jar for one engine job snapshot; null when unreadable. */
    fun currentJar(): CookieJar? {
        if (!NSFileManager.defaultManager.fileExistsAtPath(filePath)) return null
        val text = NSString.stringWithContentsOfFile(filePath, NSUTF8StringEncoding, null) as String?
            ?: return null
        return CookieJar.fromText(text)
    }

    private fun fileSize(path: String): Long? {
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return null
        val size = attributes[NSFileSize] as? NSNumber ?: return null
        return size.longLongValue
    }

    private fun createStateDirectory() {
        runCatching {
            NSFileManager.defaultManager.createDirectoryAtPath(
                path = stateDirectory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }

    /** T-018: keep cookies.txt out of iCloud/iTunes backup where iOS allows. */
    private fun excludeFromBackup() {
        runCatching {
            val url = NSURL.fileURLWithPath(filePath)
            url.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        }
    }

    private fun deleteImportedCopy(sourcePath: String) {
        val directory = importDirectory ?: return
        val parent = sourcePath.substringBeforeLast('/', "")
        if (parent == directory.trimEnd('/')) {
            runCatching { NSFileManager.defaultManager.removeItemAtPath(sourcePath, null) }
        }
    }

    private fun messageFor(error: NetscapeCookieFileError): String = when (error) {
        NetscapeCookieFileError.EMPTY -> "The cookie file is empty."
        NetscapeCookieFileError.TOO_LARGE -> "The cookie file is larger than 1 MiB."
        NetscapeCookieFileError.NOT_A_COOKIE_FILE -> "That file does not look like a Netscape cookie file."
    }

    private fun failure(message: String) = CookieOperationResult(success = false, message = message)

    companion object {
        const val FILE_NAME = "cookies.txt"
    }
}

/** The engine's T-018 jar source over the on-device file. */
class IosCookieJarSource(private val store: IosCookieStore) : CookieJarSource {
    override suspend fun currentJar(): CookieJar? = store.currentJar()
}
