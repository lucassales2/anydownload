package com.anydownload.desktop

import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.JobErrorCode
import com.anydownload.core.domain.JobState
import com.anydownload.core.engine.HttpDownloadEngine
import com.anydownload.core.fake.InMemorySettingsRepository
import com.anydownload.core.platform.FileHandle
import com.anydownload.core.platform.FileStore
import com.anydownload.core.platform.HttpBody
import com.anydownload.core.platform.HttpRequest
import com.anydownload.core.platform.HttpResponse
import com.anydownload.core.platform.HttpTransfer
import com.anydownload.core.postprocess.MediaFilePath
import com.anydownload.desktop.engine.DesktopFileStore
import com.anydownload.desktop.store.DesktopStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T-105 M1 desktop gate, without a live site: the shared HTTP engine writes a
 * fixture file in chunks, a bad URL becomes a redacted failed row, and a new
 * store/engine built from the same state directory restores both rows. A
 * retry of the completed row writes nothing again.
 */
class DesktopHttpRestartGateTest {

    private val fixtureUrl = "https://fixtures.example.com/files/clip.mp4"
    private val failureUrl = "https://fixtures.example.com/private?token=SECRET"

    /** One process's desktop HTTP stack: shared store, engine, and file root. */
    private class DesktopProcess(
        stateDirectory: Path,
        downloadRoot: Path,
        transfer: HttpTransfer,
        private val scope: CoroutineScope,
    ) {
        private var idCounter = 0
        val store = DesktopStore(stateDirectory, defaultDownloadRoot = { downloadRoot.toString() })
        val fileStore = CountingFileStore(DesktopFileStore { downloadRoot.toString() })
        val engine: HttpDownloadEngine

        init {
            val persisted = store.load()
            val settings = InMemorySettingsRepository(persisted.settings)
            engine = HttpDownloadEngine(
                transfer = transfer,
                fileStore = fileStore,
                settings = settings,
                scope = scope,
                idGenerator = { "gate-${++idCounter}" },
                persist = { jobs -> store.saveJobs(jobs) },
                seedJobs = persisted.jobs,
            )
        }

        fun close() {
            store.saveJobs(engine.jobs.value)
            scope.cancel()
        }
    }

    /** Counts every write the engine makes to a temp file. */
    private class CountingFileStore(private val delegate: FileStore) : FileStore {
        var writes = 0
            private set

        fun noteWrite() {
            writes++
        }

        override fun createTempFile(): FileHandle = CountingHandle(delegate.createTempFile(), this)

        override fun createTempFile(extension: String): FileHandle =
            CountingHandle(delegate.createTempFile(extension), this)

        override fun mediaFilePath(temp: FileHandle): MediaFilePath =
            delegate.mediaFilePath((temp as CountingHandle).delegate)

        override fun mediaFilePath(relativePath: String): MediaFilePath = delegate.mediaFilePath(relativePath)

        override fun publish(temp: FileHandle, relativePath: String): String =
            delegate.publish((temp as CountingHandle).delegate, relativePath)

        override fun delete(relativePath: String): Boolean = delegate.delete(relativePath)

        override fun size(relativePath: String): Long? = delegate.size(relativePath)
    }

    private class CountingHandle(
        val delegate: FileHandle,
        private val store: CountingFileStore,
    ) : FileHandle {
        override fun write(data: ByteArray, length: Int) {
            store.noteWrite()
            delegate.write(data, length)
        }

        override fun close() = delegate.close()

        override fun discard() = delegate.discard()
    }

    private class FixtureTransfer(
        private val fixtureUrl: String,
        private val payload: ByteArray,
        private val chunkBytes: Int,
    ) : HttpTransfer {
        val requested = mutableListOf<String>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requested += request.url
            return if (request.url == fixtureUrl) {
                HttpResponse.Final(
                    statusCode = 200,
                    contentType = "application/octet-stream",
                    totalBytes = payload.size.toLong(),
                    body = ChunkedBody(payload, chunkBytes),
                )
            } else {
                // A typed temporary source failure; the message never echoes
                // the URL or its query.
                HttpResponse.Final(statusCode = 503, contentType = "text/plain")
            }
        }
    }

    /** Serves [bytes] in fixed-size reads so the engine writes more than once. */
    private class ChunkedBody(
        private val bytes: ByteArray,
        private val chunkBytes: Int,
    ) : HttpBody {
        private var position = 0

        override suspend fun readNext(buffer: ByteArray): Int {
            if (position >= bytes.size) return -1
            val count = minOf(chunkBytes, bytes.size - position, buffer.size)
            bytes.copyInto(buffer, 0, position, position + count)
            position += count
            return count
        }

        override suspend fun close() = Unit
    }

    private fun regularFiles(root: Path): List<Path> =
        Files.list(root).use { stream -> stream.filter { Files.isRegularFile(it) }.toList() }

    @Test
    fun fixtureCompletesFailureIsRedactedAndRelaunchRestoresBothRows() = runBlocking {
        val stateDirectory = Files.createTempDirectory("anydownlod-gate-state-")
        val downloadRoot = Files.createTempDirectory("anydownlod-gate-downloads-")
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }

        val firstTransfer = FixtureTransfer(fixtureUrl, payload, chunkBytes = 4 * 1024)
        val first = DesktopProcess(stateDirectory, downloadRoot, firstTransfer, CoroutineScope(SupervisorJob() + Dispatchers.Default))

        val completedJob = first.engine.submit(DownloadRequest(sourceUrl = fixtureUrl, idempotencyKey = "gate-fixture"))
        val failedJob = first.engine.submit(DownloadRequest(sourceUrl = failureUrl, idempotencyKey = "gate-failure"))

        val terminal = withTimeout(30_000) {
            first.engine.jobs.first { jobs -> jobs.filter { it.state.isTerminal }.size == 2 }
        }
        val completed = terminal.first { it.id == completedJob.id }
        val failed = terminal.first { it.id == failedJob.id }

        // 1. One fixture file inside the download root, written in chunks.
        assertEquals(JobState.COMPLETED, completed.state, completed.error?.message)
        val artifact = completed.artifacts.single()
        assertEquals("clip.mp4", artifact.relativePath)
        val written = downloadRoot.resolve(artifact.relativePath)
        assertTrue(Files.isRegularFile(written), "the fixture file must exist in the download root")
        assertTrue(Files.readAllBytes(written).contentEquals(payload))
        assertTrue(first.fileStore.writes > 1, "the body must be streamed in more than one write")

        // 2. The bad URL is FAILED with a redacted message and no file.
        assertEquals(JobState.FAILED, failed.state)
        assertEquals(JobErrorCode.NETWORK_FAILURE, failed.error?.code)
        val message = failed.error?.message.orEmpty()
        assertFalse(message.contains("token"), "the error must not echo the query: $message")
        assertFalse(message.contains("SECRET"), "the error must not echo the query: $message")
        assertFalse(message.contains(failureUrl), "the error must not echo the URL: $message")
        assertFalse(message.lowercase().contains("cookie"), "the error must not mention cookies: $message")
        assertTrue(failed.artifacts.isEmpty())
        assertEquals(listOf(written), regularFiles(downloadRoot))

        first.close()

        // 3. A new process loads both rows from the same state directory and
        // does not start either job again.
        val secondTransfer = FixtureTransfer(fixtureUrl, payload, chunkBytes = 4 * 1024)
        val second = DesktopProcess(stateDirectory, downloadRoot, secondTransfer, CoroutineScope(SupervisorJob() + Dispatchers.Default))

        val reloaded = second.engine.jobs.value.associateBy { it.id }
        assertEquals(setOf(completedJob.id, failedJob.id), reloaded.keys)
        assertEquals(JobState.COMPLETED, reloaded.getValue(completedJob.id).state)
        assertEquals("clip.mp4", reloaded.getValue(completedJob.id).artifacts.single().relativePath)
        assertEquals(JobState.FAILED, reloaded.getValue(failedJob.id).state)
        assertTrue(secondTransfer.requested.isEmpty(), "restoring a row must not start work")

        val retried = second.engine.retry(completedJob.id)
        assertEquals(JobState.COMPLETED, retried?.state)
        assertTrue(secondTransfer.requested.isEmpty(), "a retry of a completed job must not download")
        assertEquals(1, reloaded.getValue(completedJob.id).artifacts.size)
        assertEquals(listOf(written), regularFiles(downloadRoot), "no second copy of the completed file")

        second.close()
    }
}
