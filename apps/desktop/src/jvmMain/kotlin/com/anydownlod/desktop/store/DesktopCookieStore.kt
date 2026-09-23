package com.anydownlod.desktop.store

import com.anydownlod.core.CookieOperationResult
import com.anydownlod.core.CookieStore
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
        if (size > MAX_BYTES) return failure("The cookie file is larger than 1 MiB.")

        val text = runCatching { Files.readString(source) }.getOrElse {
            return failure("The cookie file could not be read.")
        }
        if (!looksLikeNetscape(text)) {
            return failure("That file does not look like a Netscape cookie file.")
        }

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

    private fun storedFile(): Path = store.stateDirectory.resolve(FILE_NAME)

    private fun looksLikeNetscape(text: String): Boolean {
        val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.toList()
        val first = lines.firstOrNull().orEmpty()
        val hasHeader = first.startsWith("# Netscape HTTP Cookie File") ||
            first.startsWith("# HTTP Cookie File")
        val hasTabbedRow = lines.any { line ->
            !line.startsWith("#") && line.split('\t').size >= 7
        }
        return hasHeader || hasTabbedRow
    }

    private fun failure(message: String) = CookieOperationResult(success = false, message = message)

    private companion object {
        const val FILE_NAME = "cookies.txt"
        const val MAX_BYTES = 1024L * 1024L
    }
}
