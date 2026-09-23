package com.anydownlod.desktop.store

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.JobError
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.Subscription
import com.anydownlod.core.validation.RelativePathValidation
import com.anydownlod.core.validation.RelativePathValidator
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
    private val lastWrittenJobs = mutableMapOf<String, DownloadJob>()
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
        lastWrittenJobs.clear()
        val warnings = mutableListOf<String>()

        val loadedJobs = readJson(jobsFile, JobsDocument(), warnings)
            ?.jobs?.map { it.toDomain() } ?: emptyList()
        val loadedSubscriptions = readJson(subscriptionsFile, SubscriptionsDocument(), warnings)
            ?.subscriptions?.map { it.toDomain() } ?: emptyList()
        val settingsDocument = readJson(settingsFile, SettingsDocument(), warnings) ?: SettingsDocument()

        cookieFilePath = settingsDocument.cookieFilePath
        val settings = settingsDocument.toDomain().let { loaded ->
            if (loaded.downloadRoot.isBlank()) loaded.copy(downloadRoot = defaultDownloadRoot()) else loaded
        }
        currentSettings = settings

        var sanitized = 0
        var interrupted = 0
        val normalized = loadedJobs.map { job ->
            val cleaned = sanitizeDestination(job)
            if (cleaned !== job) sanitized++
            if (cleaned.state.isInterruptedOnStartup()) {
                interrupted++
                interrupt(cleaned, now())
            } else {
                cleaned
            }
        }
        if (sanitized > 0) warnings += "Reset $sanitized invalid destination folder(s)."
        if (interrupted > 0) warnings += "Marked $interrupted interrupted job(s) for retry."

        val (jobs, cleared) = dropExpired(normalized, settings.clearCompletedAfterSeconds, now())
        if (cleared > 0) warnings += "Cleared $cleared expired row(s)."

        jobs.forEach { lastWrittenJobs[it.id] = it }
        loadWarning = warnings.takeIf { it.isNotEmpty() }?.joinToString(" ")

        PersistedState(jobs = jobs, subscriptions = loadedSubscriptions, settings = settings)
    }

    /**
     * Writes the job list, dropping rows past the clear-completed age. A job
     * whose revision is older than the last write is replaced by the last
     * written version instead of regressing the file.
     */
    fun saveJobs(jobs: List<DownloadJob>): JobsSaveResult = synchronized(lock) {
        val (kept, cleared) = dropExpired(
            jobs = jobs,
            clearAfterSeconds = currentSettings?.clearCompletedAfterSeconds ?: 0L,
            at = now(),
        )
        val accepted = mutableListOf<DownloadJob>()
        var skipped = 0
        kept.forEach { job ->
            val last = lastWrittenJobs[job.id]
            if (last != null && job.revision < last.revision) {
                skipped++
                accepted += last
            } else {
                accepted += job
                lastWrittenJobs[job.id] = job
            }
        }
        lastWrittenJobs.keys.retainAll { id -> accepted.any { it.id == id } }
        writeDocument(jobsFile, JobsDocument(accepted.map { it.toDto() }))
        JobsSaveResult(written = accepted.size, skippedStale = skipped, clearedExpired = cleared)
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
            fallback
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

    private fun sanitizeDestination(job: DownloadJob): DownloadJob {
        val folder = job.request.options.destinationFolder ?: return job
        return when (val result = RelativePathValidator.validate(folder)) {
            is RelativePathValidation.Valid -> {
                val normalized = result.path.ifEmpty { null }
                if (normalized == folder) {
                    job
                } else {
                    job.copy(
                        request = job.request.copy(
                            options = job.request.options.copy(destinationFolder = normalized),
                        ),
                    )
                }
            }

            is RelativePathValidation.Invalid -> job.copy(
                request = job.request.copy(
                    options = job.request.options.copy(destinationFolder = null),
                ),
            )
        }
    }
}

private fun JobState.isInterruptedOnStartup(): Boolean =
    this == JobState.RESOLVING || this == JobState.QUEUED ||
        this == JobState.DOWNLOADING || this == JobState.POSTPROCESSING

private fun interrupt(job: DownloadJob, at: Long): DownloadJob {
    val error = JobError(
        code = JobErrorCode.ENGINE_UNAVAILABLE,
        message = "The app closed before the download finished. Retry to start it again.",
        retryable = true,
    )
    val attempts = if (job.attempts.isEmpty()) {
        job.attempts
    } else {
        job.attempts.dropLast(1) + job.attempts.last().copy(
            state = JobState.FAILED,
            error = error,
            finishedAtEpochMillis = at,
        )
    }
    return job.copy(
        state = JobState.FAILED,
        error = error,
        attempts = attempts,
        finishedAtEpochMillis = at,
        updatedAtEpochMillis = at,
        revision = job.revision + 1,
    )
}

private fun dropExpired(
    jobs: List<DownloadJob>,
    clearAfterSeconds: Long,
    at: Long,
): Pair<List<DownloadJob>, Int> {
    if (clearAfterSeconds <= 0) return jobs to 0
    val cutoff = at - clearAfterSeconds * 1000
    val retained = jobs.filterNot { job ->
        job.state.isTerminal && (job.finishedAtEpochMillis ?: job.updatedAtEpochMillis) < cutoff
    }
    return retained to (jobs.size - retained.size)
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
