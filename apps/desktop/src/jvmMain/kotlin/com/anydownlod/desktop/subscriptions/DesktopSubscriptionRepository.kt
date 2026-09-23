package com.anydownlod.desktop.subscriptions

import com.anydownlod.core.DownloadEngine
import com.anydownlod.core.SettingsRepository
import com.anydownlod.core.SubscriptionRepository
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobError
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.domain.Subscription
import com.anydownlod.core.fake.InMemorySubscriptionRepository
import com.anydownlod.desktop.engine.CliProcessRunner
import com.anydownlod.desktop.engine.ExecutableOnPath
import com.anydownlod.desktop.engine.JavaCliProcessRunner
import com.anydownlod.desktop.store.DesktopStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

private const val MILLIS_PER_MINUTE = 60_000L
private const val DEFAULT_SCAN_LIMIT = 50
private const val MAX_SEEN_IDS = 50_000
private const val ENTRY_PREFIX = "ENTRY|"

/** One flat-listing row: id, item URL, title, availability. */
data class ScannedEntry(
    val id: String,
    val url: String,
    val title: String,
    val availability: String,
) {
    fun isMembersOnly(): Boolean {
        val text = availability.lowercase()
        return "subscriber" in text || "premium" in text || "member" in text
    }
}

/**
 * Desktop subscription repository: CRUD over the shared fake plus real
 * yt-dlp flat checks. First check policy is future items only: the current
 * listing is marked seen and nothing is downloaded.
 */
class DesktopSubscriptionRepository(
    private val delegate: InMemorySubscriptionRepository,
    private val store: DesktopStore,
    private val engine: DownloadEngine,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val runner: CliProcessRunner = JavaCliProcessRunner,
    private val resolveExecutable: (String) -> String? = ExecutableOnPath::find,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val scanLimit: Int = DEFAULT_SCAN_LIMIT,
) : SubscriptionRepository by delegate {

    override fun add(
        sourceUrl: String,
        displayName: String,
        downloadOptions: DownloadOptions,
        checkIntervalMinutes: Int,
        titleFilterRegex: String,
        skipMembersOnly: Boolean,
    ): Subscription = delegate.add(
        sourceUrl = sourceUrl,
        displayName = displayName,
        downloadOptions = downloadOptions,
        checkIntervalMinutes = checkIntervalMinutes,
        titleFilterRegex = titleFilterRegex,
        skipMembersOnly = skipMembersOnly,
    ).also { persist() }

    override fun update(
        id: String,
        displayName: String,
        checkIntervalMinutes: Int,
        titleFilterRegex: String,
        skipMembersOnly: Boolean,
    ): Boolean = delegate.update(id, displayName, checkIntervalMinutes, titleFilterRegex, skipMembersOnly)
        .also { persist() }

    override fun pause(id: String): Boolean = delegate.pause(id).also { persist() }

    override fun resume(id: String): Boolean = delegate.resume(id).also { persist() }

    override fun delete(id: String): Boolean = delegate.delete(id).also { persist() }

    override fun checkNow(id: String): Boolean {
        if (delegate.subscriptions.value.none { it.id == id }) return false
        scope.launch { runCheck(id) }
        return true
    }

    override fun checkAll() {
        delegate.subscriptions.value.filterNot { it.paused }.forEach { checkNow(it.id) }
    }

    /** Runs one flat scan. Exposed for tests and for the scheduler. */
    suspend fun runCheck(subscriptionId: String) {
        val subscription = delegate.subscriptions.value.firstOrNull { it.id == subscriptionId } ?: return
        val executable = resolveExecutable("yt-dlp")
        if (executable == null) {
            recordFailure(
                subscriptionId,
                JobError(JobErrorCode.ENGINE_UNAVAILABLE, "yt-dlp was not found on PATH. Install it, then retry."),
            )
            return
        }

        val command = buildList {
            add(executable)
            add("--flat-playlist")
            add("--skip-download")
            add("--no-warnings")
            add("--playlist-end")
            add(scanLimit.toString())
            if (subscription.downloadOptions.useCookies) {
                store.cookieFilePath()?.let { path ->
                    add("--cookies")
                    add(path)
                }
            }
            add("--print")
            add("$ENTRY_PREFIX%(id)s|%(url)s|%(title)s|%(availability)s")
            add("--")
            add(subscription.sourceUrl)
        }
        val workingDirectory = runCatching {
            Path.of(settings.settings.value.downloadRoot.ifBlank { System.getProperty("user.home") })
        }.getOrDefault(Path.of("."))

        val process = try {
            withContext(ioDispatcher) { runner.start(command, workingDirectory) }
        } catch (failure: Exception) {
            recordFailure(subscriptionId, JobError(JobErrorCode.NETWORK_FAILURE, "The subscription check could not start."))
            return
        }

        val entries = mutableListOf<ScannedEntry>()
        try {
            withContext(ioDispatcher) {
                process.stdout.useLines { lines ->
                    lines.forEach { line -> parseEntry(line)?.let { entries += it } }
                }
            }
            val exit = process.waitFor(30_000)
            if (exit != null && exit != 0 && entries.isEmpty()) {
                recordFailure(subscriptionId, JobError(JobErrorCode.EXTRACTION_FAILURE, "The subscription check failed."))
                return
            }
        } finally {
            process.destroyTree()
        }
        applyScan(subscriptionId, entries)
    }

    private fun applyScan(subscriptionId: String, entries: List<ScannedEntry>) {
        val subscription = delegate.subscriptions.value.firstOrNull { it.id == subscriptionId } ?: return
        val firstCheck = subscription.lastCheckedAtEpochMillis == null
        val filtered = entries
            .filter { entry -> matchesFilter(subscription, entry) }
            .filterNot { entry -> subscription.skipMembersOnly && entry.isMembersOnly() }

        if (!firstCheck) {
            filtered
                .filterNot { entry -> entry.id in subscription.seenIds }
                .forEach { entry -> enqueue(subscription, entry) }
        }

        val seen = (subscription.seenIds + entries.map { it.id }).distinct()
        val bounded = if (seen.size > MAX_SEEN_IDS) seen.takeLast(MAX_SEEN_IDS) else seen
        delegate.updateSubscription(subscriptionId) { current ->
            val at = now()
            current.copy(
                lastCheckedAtEpochMillis = at,
                nextCheckAtEpochMillis = at + current.checkIntervalMinutes * MILLIS_PER_MINUTE,
                lastError = null,
                seenIds = bounded,
            )
        }
        persist()
    }

    private fun enqueue(subscription: Subscription, entry: ScannedEntry) {
        val request = DownloadRequest(
            sourceUrl = entry.url,
            options = subscription.downloadOptions.copy(
                startPolicy = StartPolicy.AUTOMATIC,
                playlistItemLimit = 0,
            ),
            idempotencyKey = "sub:${subscription.id}:${entry.id}",
        )
        runCatching { engine.submit(request) }
    }

    private fun matchesFilter(subscription: Subscription, entry: ScannedEntry): Boolean {
        val pattern = subscription.titleFilterRegex
        if (pattern.isEmpty()) return true
        // Length caps keep a pathological pattern or title from hanging a check.
        if (pattern.length > 200) return false
        val regex = runCatching { Regex(pattern) }.getOrNull() ?: return true
        return regex.containsMatchIn(entry.title.take(500))
    }

    private fun recordFailure(subscriptionId: String, error: JobError) {
        delegate.updateSubscription(subscriptionId) { current ->
            val at = now()
            current.copy(
                lastCheckedAtEpochMillis = at,
                nextCheckAtEpochMillis = at + current.checkIntervalMinutes * MILLIS_PER_MINUTE,
                lastError = error,
            )
        }
        persist()
    }

    private fun persist() {
        store.saveSubscriptions(delegate.subscriptions.value)
    }
}

/** Calls due, unpaused subscriptions while the window is open. */
class SubscriptionScheduler(
    private val repository: SubscriptionRepository,
    private val scope: CoroutineScope,
    private val intervalMillis: Long = 60_000,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun start() {
        scope.launch {
            while (true) {
                val due = repository.subscriptions.value
                    .filter { !it.paused && (it.nextCheckAtEpochMillis ?: 0L) <= now() }
                due.forEach { repository.checkNow(it.id) }
                kotlinx.coroutines.delay(intervalMillis)
            }
        }
    }
}

private fun parseEntry(line: String): ScannedEntry? {
    if (!line.startsWith(ENTRY_PREFIX)) return null
    val parts = line.removePrefix(ENTRY_PREFIX).split('|')
    if (parts.size < 3) return null
    val id = parts[0].trim()
    val url = parts[1].trim()
    if (id.isEmpty() || url.isEmpty()) return null
    return ScannedEntry(
        id = id,
        url = url,
        title = parts.getOrNull(2).orEmpty(),
        availability = parts.getOrNull(3).orEmpty(),
    )
}
