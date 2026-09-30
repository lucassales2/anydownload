package com.anydownlod.desktop.store

import com.anydownlod.core.CookieOperationResult
import com.anydownlod.core.CookieErrorReason
import com.anydownlod.core.CookieStatus
import com.anydownlod.core.CookieStore
import com.anydownlod.core.cookies.CookieJar
import com.anydownlod.core.cookies.CookieJarState
import com.anydownlod.core.cookies.NetscapeCookieFile
import com.anydownlod.core.cookies.NetscapeCookieFileError
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Validates and stores a Netscape cookie file in the state directory. Only
 * the frozen path is persisted; contents never enter settings JSON or logs.
 */
class DesktopCookieStore(private val store: DesktopStore) : CookieStore {

    override fun import(sourcePath: String): CookieOperationResult {
        val source = runCatching { Path.of(sourcePath) }.getOrNull()
            ?: return failure("Choose an existing cookie file.")
        if (!Files.isRegularFile(source)) return failure("Choose an existing cookie file.")

        val size = runCatching { Files.size(source) }.getOrElse {
            return failure("The cookie file could not be read.")
        }
        if (size == 0L) return failure("The cookie file is empty.")
        if (size > NetscapeCookieFile.MAX_BYTES) return failure("The cookie file is larger than 1 MiB.")

        val text = runCatching { Files.readString(source) }.getOrElse {
            return failure("The cookie file could not be read.")
        }
        val validation = NetscapeCookieFile.validate(text)
        if (validation != null) return failure(messageFor(validation))

        return storeText(text)
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
        val target = storedFile()
        return runCatching {
            Files.createDirectories(store.stateDirectory)
            val temporary = Files.createTempFile(store.stateDirectory, "cookies.", ".tmp")
            try {
                Files.writeString(temporary, text)
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
            store.setCookieFilePath(target.toString())
            CookieOperationResult(success = true)
        }.getOrElse { failure("The cookie file could not be stored.") }
    }

    override fun delete(): CookieOperationResult {
        val path = store.cookieFilePath()
        if (path != null) {
            // Unix keeps an already-open descriptor alive for a running job;
            // new jobs see the deletion immediately.
            runCatching { Files.deleteIfExists(Path.of(path)) }
        }
        store.setCookieFilePath(null)
        return CookieOperationResult(success = true)
    }

    override fun storedFilePath(): String? = store.cookieFilePath()

    override fun status(): CookieStatus {
        val path = store.cookieFilePath() ?: return CookieStatus.NotConfigured
        val file = runCatching { Path.of(path) }.getOrNull() ?: return CookieStatus.NotConfigured
        if (!Files.isRegularFile(file)) return CookieStatus.NotConfigured
        val text = runCatching { Files.readString(file) }.getOrNull()
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

    private fun storedFile(): Path = store.stateDirectory.resolve(FILE_NAME)

    private fun messageFor(error: NetscapeCookieFileError): String = when (error) {
        NetscapeCookieFileError.EMPTY -> "The cookie file is empty."
        NetscapeCookieFileError.TOO_LARGE -> "The cookie file is larger than 1 MiB."
        NetscapeCookieFileError.NOT_A_COOKIE_FILE -> "That file does not look like a Netscape cookie file."
    }

    private fun failure(message: String) = CookieOperationResult(success = false, message = message)

    private companion object {
        const val FILE_NAME = "cookies.txt"
    }
}
