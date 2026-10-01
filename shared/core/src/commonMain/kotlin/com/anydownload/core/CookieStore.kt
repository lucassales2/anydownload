package com.anydownload.core

/** Result of a cookie-file operation. Never carries file contents. */
data class CookieOperationResult(val success: Boolean, val message: String? = null)

/**
 * Why a configured cookie file is in the Error state. The reason never names
 * a cookie, a value, or a path; the UI maps it to localized copy.
 */
enum class CookieErrorReason {
    /** The stored file exists but cannot be read, parsed, or validated. */
    UNREADABLE,

    /** The stored file parses but holds no rows. */
    NO_COOKIES,

    /** Rows exist, but every row is expired. */
    ALL_EXPIRED,
}

/**
 * The Settings status of the local cookie file: Not configured, Configured,
 * or Error. The value carries no cookie text, names, values, or path.
 */
sealed interface CookieStatus {
    data object NotConfigured : CookieStatus
    data object Configured : CookieStatus
    data class Error(val reason: CookieErrorReason) : CookieStatus
}

/**
 * Local cookie-file management. Shared screens only see status and messages;
 * each host validates, copies, and deletes the file in app-private storage.
 */
interface CookieStore {
    /** Copies and validates [sourcePath]; returns a short reason on failure. */
    fun import(sourcePath: String): CookieOperationResult

    /**
     * Stores already-read Netscape [text] in the same place [import] writes
     * (a desktop browser snapshot). The same size cap and validation apply
     * before anything is written. The text is credential material: it never
     * enters settings JSON, logs, a job, or history.
     */
    fun importText(text: String): CookieOperationResult

    /** Removes the stored file and returns to Not configured. */
    fun delete(): CookieOperationResult

    /** The frozen stored path, or null when not configured. Never logged. */
    fun storedFilePath(): String?

    /**
     * The current status, computed from the stored file. A missing file is
     * Not configured; an unreadable, empty, or fully expired file is Error
     * with a reason that carries no secrets. This is a local file check and
     * may do bounded I/O; it is not called per request.
     */
    fun status(): CookieStatus

    companion object {
        val Unavailable: CookieStore = object : CookieStore {
            override fun import(sourcePath: String): CookieOperationResult =
                CookieOperationResult(false, "Cookie import is only available on desktop.")

            override fun importText(text: String): CookieOperationResult =
                CookieOperationResult(false, "Cookie import is only available on desktop.")

            override fun delete(): CookieOperationResult =
                CookieOperationResult(false, "Cookie management is only available on desktop.")

            override fun storedFilePath(): String? = null

            override fun status(): CookieStatus = CookieStatus.NotConfigured
        }
    }
}
