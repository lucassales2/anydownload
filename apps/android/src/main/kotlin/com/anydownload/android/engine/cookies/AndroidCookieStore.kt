/*
 * Android cookie store — AnyDownload (T-018)
 *
 * One `cookies.txt` in app-private storage. Import validates with the shared
 * Netscape rules, rejects anything else without a partial import, overwrites
 * on replace, and deletes on request. The file is plain text: there is no
 * Android Keystore wrapper around it (owner decision, 2026-09-30). The
 * manifest excludes it from Auto Backup and device transfer.
 *
 * The class uses only `java.io`/`java.nio` so `:apps:android-engine-tests`
 * compiles and tests it on the JVM. `MainActivity` supplies the picked file
 * copy; shared/common code never reads the file.
 */
package com.anydownload.android.engine.cookies

import com.anydownload.core.CookieOperationResult
import com.anydownload.core.CookieErrorReason
import com.anydownload.core.CookieStatus
import com.anydownload.core.CookieStore
import com.anydownload.core.cookies.CookieJar
import com.anydownload.core.cookies.CookieJarSource
import com.anydownload.core.cookies.CookieJarState
import com.anydownload.core.cookies.NetscapeCookieFile
import com.anydownload.core.cookies.NetscapeCookieFileError
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Validates and stores [FILE_NAME] under [stateDirectory]. [importDirectory]
 * is the app cache the platform picker copied the user's chosen document
 * into; a source inside it is deleted after the import so the credential
 * copy does not linger.
 */
class AndroidCookieStore(
    private val stateDirectory: Path,
    private val importDirectory: Path? = null,
) : CookieStore {

    /** The one stored file; callers never log this path with cookie contents. */
    val filePath: Path get() = stateDirectory.resolve(FILE_NAME)

    override fun import(sourcePath: String): CookieOperationResult {
        val source = runCatching { Path.of(sourcePath) }.getOrNull()
            ?: return failure("Choose an existing cookie file.")
        if (!Files.isRegularFile(source)) return failure("Choose an existing cookie file.")

        val size = runCatching { Files.size(source) }.getOrElse {
            deleteImportedCopy(source)
            return failure("The cookie file could not be read.")
        }
        if (size == 0L) {
            deleteImportedCopy(source)
            return failure("The cookie file is empty.")
        }
        if (size > NetscapeCookieFile.MAX_BYTES) {
            deleteImportedCopy(source)
            return failure("The cookie file is larger than 1 MiB.")
        }

        val text = runCatching { Files.readString(source) }.getOrElse {
            deleteImportedCopy(source)
            return failure("The cookie file could not be read.")
        }
        val validation = NetscapeCookieFile.validate(text)
        if (validation != null) {
            deleteImportedCopy(source)
            return failure(messageFor(validation))
        }

        val result = storeText(text)
        deleteImportedCopy(source)
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

    private fun storeText(text: String): CookieOperationResult = runCatching {
        Files.createDirectories(stateDirectory)
        val target = filePath
        val temporary = Files.createTempFile(stateDirectory, "cookies.", ".tmp")
        try {
            Files.writeString(temporary, text)
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
        CookieOperationResult(success = true)
    }.getOrElse { failure("The cookie file could not be stored.") }

    override fun delete(): CookieOperationResult {
        // An open stream keeps the bytes for an in-flight job; a new job sees
        // the deletion immediately, exactly like the desktop store.
        runCatching { Files.deleteIfExists(filePath) }
        return CookieOperationResult(success = true)
    }

    override fun storedFilePath(): String? = filePath.takeIf { Files.isRegularFile(it) }?.toString()

    override fun status(): CookieStatus {
        val path = filePath
        if (!Files.isRegularFile(path)) return CookieStatus.NotConfigured
        val text = runCatching { Files.readString(path) }.getOrNull()
            ?: return CookieStatus.Error(CookieErrorReason.UNREADABLE)
        if (NetscapeCookieFile.validate(text) != null) {
            return CookieStatus.Error(CookieErrorReason.UNREADABLE)
        }
        return when (CookieJar.fromText(text).stateAt(System.currentTimeMillis() / 1000L)) {
            CookieJarState.READY -> CookieStatus.Configured
            CookieJarState.ALL_EXPIRED -> CookieStatus.Error(CookieErrorReason.ALL_EXPIRED)
            CookieJarState.EMPTY -> CookieStatus.Error(CookieErrorReason.NO_COOKIES)
        }
    }

    /** The parsed jar for one engine job snapshot; null when unreadable. */
    fun currentJar(): CookieJar? {
        val path = filePath
        if (!Files.isRegularFile(path)) return null
        val text = runCatching { Files.readString(path) }.getOrNull() ?: return null
        return CookieJar.fromText(text)
    }

    private fun deleteImportedCopy(source: Path) {
        val directory = importDirectory ?: return
        val sourceParent = runCatching { source.toAbsolutePath().normalize().parent }.getOrNull() ?: return
        val importRoot = runCatching { directory.toAbsolutePath().normalize() }.getOrNull() ?: return
        if (sourceParent == importRoot) {
            runCatching { Files.deleteIfExists(source) }
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
class AndroidCookieJarSource(private val store: AndroidCookieStore) : CookieJarSource {
    override suspend fun currentJar(): CookieJar? = store.currentJar()
}
