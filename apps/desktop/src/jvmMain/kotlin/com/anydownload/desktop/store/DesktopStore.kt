package com.anydownload.desktop.store

import com.anydownload.core.domain.AppSettings
import com.anydownload.core.domain.DownloadJob
import com.anydownload.core.domain.Subscription
import com.anydownload.core.persist.JobDocumentStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Everything the desktop host restores at startup. */
data class PersistedState(
    val jobs: List<DownloadJob>,
    val subscriptions: List<Subscription>,
    val settings: AppSettings,
)

/** Diagnostics for one jobs write. */
data class JobsSaveResult(
    val written: Int,
    val skippedStale: Int,
    val clearedExpired: Int,
)

/**
 * JSON files under one state directory, used only by `apps/desktop`.
 *
 * Layout: `jobs.json`, `subscriptions.json`, and `settings.json`. Every write
 * goes to a temporary file in the same directory and is renamed over the
 * target, so a crash cannot leave a half-written file as the only copy.
 * Cookie contents are never written: the settings document carries only the
 * status flag and, for the desktop host, a file path.
 */
class DesktopStore(
    val stateDirectory: Path,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val defaultDownloadRoot: () -> String = { defaultDownloadRootPath() },
    /** Test seam that runs after the temp file is written and before the rename. */
    private val beforeRename: ((Path) -> Unit)? = null,
) {
    private companion object {
        const val JOBS_FILE = "jobs.json"
        const val SUBSCRIPTIONS_FILE = "subscriptions.json"
        const val SETTINGS_FILE = "settings.json"
    }

    private val lock = Any()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val jobsStore = JobDocumentStore(now = now)
    private var currentSettings: AppSettings? = null
    private var cookieFilePath: String? = null

    /** Non-null when a state file was unreadable and moved aside during [load]. */
    var loadWarning: String? = null
        private set

    val jobsFile: Path get() = stateDirectory.resolve(JOBS_FILE)
    val subscriptionsFile: Path get() = stateDirectory.resolve(SUBSCRIPTIONS_FILE)
    val settingsFile: Path get() = stateDirectory.resolve(SETTINGS_FILE)

    fun load(): PersistedState = synchronized(lock) {
        loadWarning = null
        val warnings = mutableListOf<String>()

        val loadedSubscriptions = readJson(subscriptionsFile, SubscriptionsDocument(), warnings)
            ?.subscriptions?.map { it.toDomain() } ?: emptyList()
        val settingsRead = readJson(settingsFile, SettingsDocument(), warnings)
        val settings = if (settingsRead == null && currentSettings != null) {
            // T-017: an invalid reload keeps the last known-good settings
            // instead of silently reverting to defaults.
            warnings += "The settings file could not be read; keeping the last known-good configuration."
            currentSettings!!
        } else {
            val settingsDocument = settingsRead ?: SettingsDocument()
            cookieFilePath = settingsDocument.cookieFilePath
            settingsDocument.toDomain().let { loaded ->
                if (loaded.downloadRoot.isBlank()) loaded.copy(downloadRoot = defaultDownloadRoot()) else loaded
            }
        }
        currentSettings = settings

        val jobsText = readTextFile(jobsFile, warnings)
        val restored = try {
            jobsStore.restore(jobsText, settings.clearCompletedAfterSeconds)
        } catch (failure: Exception) {
            moveCorruptAside(jobsFile)
            warnings += "The previous ${jobsFile.fileName} could not be read."
            jobsStore.restore(null, settings.clearCompletedAfterSeconds)
        }
        if (restored.sanitizedDestinations > 0) {
            warnings += "Reset ${restored.sanitizedDestinations} invalid destination folder(s)."
        }
        if (restored.interruptedActive > 0) {
            warnings += "Marked ${restored.interruptedActive} interrupted job(s) for retry."
        }
        if (restored.clearedExpired > 0) {
            warnings += "Cleared ${restored.clearedExpired} expired row(s)."
        }

        loadWarning = warnings.takeIf { it.isNotEmpty() }?.joinToString(" ")

        PersistedState(jobs = restored.jobs, subscriptions = loadedSubscriptions, settings = settings)
    }

    /**
     * Writes the job list, dropping rows past the clear-completed age. A job
     * whose revision is older than the last write is replaced by the last
     * written version instead of regressing the file.
     */
    fun saveJobs(jobs: List<DownloadJob>): JobsSaveResult = synchronized(lock) {
        val saved = jobsStore.save(
            jobs = jobs,
            clearAfterSeconds = currentSettings?.clearCompletedAfterSeconds ?: 0L,
        )
        writeAtomically(jobsFile, saved.document)
        JobsSaveResult(
            written = saved.written,
            skippedStale = saved.skippedStale,
            clearedExpired = saved.clearedExpired,
        )
    }

    fun saveSubscriptions(subscriptions: List<Subscription>) = synchronized(lock) {
        writeDocument(subscriptionsFile, SubscriptionsDocument(subscriptions.map { it.toDto() }))
    }

    fun saveSettings(settings: AppSettings) = synchronized(lock) {
        currentSettings = settings
        writeDocument(settingsFile, settings.toDocument(cookieFilePath))
    }

    fun saveAll(
        jobs: List<DownloadJob>,
        subscriptions: List<Subscription>,
        settings: AppSettings,
    ) {
        saveJobs(jobs)
        saveSubscriptions(subscriptions)
        saveSettings(settings)
    }

    /** The frozen cookie file path; T-035 copies the file and sets this. */
    fun cookieFilePath(): String? = synchronized(lock) { cookieFilePath }

    fun setCookieFilePath(path: String?) = synchronized(lock) {
        cookieFilePath = path
        currentSettings?.let { settings -> writeDocument(settingsFile, settings.toDocument(path)) }
    }

    /** Reads a state file as text, moving an unreadable file aside. Blank is empty. */
    private fun readTextFile(path: Path, warnings: MutableList<String>): String? {
        if (!Files.exists(path)) return null
        val text = try {
            Files.readString(path)
        } catch (failure: Exception) {
            moveCorruptAside(path)
            warnings += "The previous ${path.fileName} could not be read."
            return null
        }
        return text.takeIf { it.isNotBlank() }
    }

    private inline fun <reified T> readJson(path: Path, fallback: T, warnings: MutableList<String>): T? {
        if (!Files.exists(path)) return fallback
        val text = try {
            Files.readString(path)
        } catch (failure: Exception) {
            moveCorruptAside(path)
            warnings += "The previous ${path.fileName} could not be read."
            return fallback
        }
        if (text.isBlank()) return fallback
        return try {
            json.decodeFromString<T>(text)
        } catch (failure: Exception) {
            moveCorruptAside(path)
            warnings += "The previous ${path.fileName} could not be read."
            // null marks an unreadable document so callers can keep their last
            // known-good value; a missing file still returns [fallback].
            null
        }
    }

    private fun moveCorruptAside(path: Path) {
        val aside = path.resolveSibling("${path.fileName}.corrupt-${now()}")
        runCatching { Files.move(path, aside, StandardCopyOption.REPLACE_EXISTING) }
    }

    private inline fun <reified T> writeDocument(path: Path, document: T) {
        writeAtomically(path, json.encodeToString(document))
    }

    private fun writeAtomically(target: Path, content: String) {
        Files.createDirectories(stateDirectory)
        val temporary = Files.createTempFile(stateDirectory, "${target.fileName}.", ".tmp")
        try {
            Files.writeString(temporary, content)
            beforeRename?.invoke(target)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (notAtomic: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

}

/** `<user home>/Downloads/AnyDownload`, used when Settings has no folder yet. */
fun defaultDownloadRootPath(home: Path = Path.of(System.getProperty("user.home"))): String =
    home.resolve("Downloads").resolve("AnyDownload").toString()

/**
 * OS app-data folder. macOS uses `~/Library/Application Support/AnyDownload`.
 * Windows uses `%APPDATA%\AnyDownload` (Roaming), which is separate from a
 * per-user install under `%LOCALAPPDATA%`.
 */
fun defaultStateDirectory(
    osName: String = System.getProperty("os.name"),
    home: Path = Path.of(System.getProperty("user.home")),
    appData: String? = System.getenv("APPDATA"),
    xdgStateHome: String? = System.getenv("XDG_STATE_HOME"),
): Path {
    val os = osName.lowercase()
    return when {
        os.contains("mac") -> home.resolve("Library/Application Support/AnyDownload")
        os.contains("win") -> Path.of(appData ?: home.toString()).resolve("AnyDownload")
        else -> {
            val base = xdgStateHome?.let { Path.of(it) } ?: home.resolve(".local/state")
            base.resolve("AnyDownload")
        }
    }
}
