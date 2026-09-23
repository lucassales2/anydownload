package com.anydownlod.core

/** Result of a cookie-file operation. Never carries file contents. */
data class CookieOperationResult(val success: Boolean, val message: String? = null)

/**
 * Local cookie-file management. Shared screens only see status and messages;
 * the desktop host validates, copies, and deletes the file.
 */
interface CookieStore {
    /** Copies and validates [sourcePath]; returns a short reason on failure. */
    fun import(sourcePath: String): CookieOperationResult

    /** Removes the stored file and returns to Not configured. */
    fun delete(): CookieOperationResult

    /** The frozen stored path, or null when not configured. Never logged. */
    fun storedFilePath(): String?

    companion object {
        val Unavailable: CookieStore = object : CookieStore {
            override fun import(sourcePath: String): CookieOperationResult =
                CookieOperationResult(false, "Cookie import is only available on desktop.")

            override fun delete(): CookieOperationResult =
                CookieOperationResult(false, "Cookie management is only available on desktop.")

            override fun storedFilePath(): String? = null
        }
    }
}
