package com.anydownlod.core.platform

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.engine.HttpDownloadEngine
import com.anydownlod.core.engine.UrlCheck
import com.anydownlod.core.engine.UrlPolicy
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Real JVM transport + file-store tests against a local mock HTTP server.
 *
 * The engine refuses loopback destinations by default (T-006 threat notes).
 * These tests pass a fixture exception that allows exactly this one test
 * server origin and delegates everything else to [UrlPolicy] — the notes
 * require such fixtures to be strictly test-only, and here they are.
 */
class JavaNetPlatformTest {

    private fun testServer(): HttpServer =
        HttpServer.create(InetSocketAddress(0), 0).also { it.start() }

    private fun baseUrl(server: HttpServer) = "http://127.0.0.1:${server.address.port}"

    private fun fixtureCheck(base: String): (String) -> UrlCheck = { url ->
        if (url.startsWith(base)) UrlCheck.Allowed(url) else UrlPolicy.check(url)
    }

    private fun engine(
        server: HttpServer,
        fileStore: FileStore,
        root: Path,
        check: (String) -> UrlCheck,
    ): HttpDownloadEngine = HttpDownloadEngine(
        transfer = JavaNetHttpTransfer(),
        fileStore = fileStore,
        settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString())),
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
        urlCheck = check,
        chunkSize = 64 * 1024,
    )

    @Test
    fun streamsEightMibWithoutBufferingWholeFile() = runBlocking {
        val payload = ByteArray(8 * 1024 * 1024) { (it % 253).toByte() }
        val server = testServer()
        server.createContext("/files/tiny.bin") { exchange ->
            exchange.responseHeaders.set("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { out -> out.write(payload) }
        }
        val root = Files.createTempDirectory("anydownload-root-test")
        try {
            val base = baseUrl(server)
            val store = JavaNetFileStore(root)
            val engine = engine(server, store, root, fixtureCheck(base))

            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = "$base/files/tiny.bin",
                    options = DownloadOptions(startPolicy = StartPolicy.AUTOMATIC),
                    idempotencyKey = "jvm-stream-key",
                )
            )
            waitForTerminal(engine, job.id)

            val finished = engine.jobs.value.first { it.id == job.id }
            assertEquals(JobState.COMPLETED, finished.state)
            // The engine reads in 64 KiB chunks; total must equal the streamed
            // size, proving nothing was buffered whole-file.
            assertEquals(payload.size.toLong(), finished.progress?.downloadedBytes)
            assertEquals(payload.size.toLong(), finished.progress?.totalBytes)

            val artifact = finished.artifacts.single()
            val written = root.resolve(artifact.relativePath)
            assertTrue(Files.isRegularFile(written))
            assertEquals(payload.size.toLong(), Files.size(written))
            assertTrue(Files.readAllBytes(written).contentEquals(payload))
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancelMidStreamDeletesTheTempFile() = runBlocking {
        val payload = ByteArray(4 * 1024 * 1024) { 3 }
        val hold = CountDownLatch(1)
        val server = testServer()
        server.createContext("/files/stall.bin") { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            val out: OutputStream = exchange.responseBody
            val firstBlock = 256 * 1024
            out.write(payload, 0, firstBlock)
            out.flush()
            hold.await(15, TimeUnit.SECONDS) // block until the test cancels
            out.write(payload, firstBlock, payload.size - firstBlock)
            out.close()
        }
        val root = Files.createTempDirectory("anydownload-root-cancel")
        try {
            val base = baseUrl(server)
            val store = JavaNetFileStore(root)
            val engine = engine(server, store, root, fixtureCheck(base))

            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = "$base/files/stall.bin",
                    options = DownloadOptions(startPolicy = StartPolicy.AUTOMATIC),
                    idempotencyKey = "jvm-cancel-key",
                )
            )

            // Wait until at least one chunk reached disk and the server is
            // parked on the latch.
            withTimeout(10_000) {
                while (engine.jobs.value.first { it.id == job.id }.progress?.downloadedBytes == null) {
                    delay(10)
                }
            }

            val cancelled = engine.cancel(job.id)
            assertNotNull(cancelled)
            assertEquals(JobState.CANCELLED, cancelled.state)

            hold.countDown()
            waitForTerminal(engine, job.id)
            awaitCleanRoot(root) // cancel() marks CANCELLED before the worker cleans up

            val finished = engine.jobs.value.first { it.id == job.id }
            assertEquals(JobState.CANCELLED, finished.state)
            assertEquals(JobErrorCode.CANCELLED, finished.error?.code)
            assertTrue(finished.artifacts.isEmpty())
            // No published artifact and the temp file was discarded.
            assertEquals(0L, Files.list(root).use { it.count() })
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun deleteArtifactsRemovesTheFileUnderTheRoot() = runBlocking {
        val payload = ByteArray(1_024) { 9 }
        val server = testServer()
        server.createContext("/files/delete.bin") { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { out -> out.write(payload) }
        }
        val root = Files.createTempDirectory("anydownload-root-delete")
        try {
            val base = baseUrl(server)
            val store = JavaNetFileStore(root)
            val engine = engine(server, store, root, fixtureCheck(base))

            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = "$base/files/delete.bin",
                    options = DownloadOptions(startPolicy = StartPolicy.AUTOMATIC),
                    idempotencyKey = "jvm-delete-key",
                )
            )
            waitForTerminal(engine, job.id)
            assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
            assertTrue(Files.isRegularFile(root.resolve("delete.bin")))

            val result = engine.deleteArtifacts(job.id)

            assertEquals(1, result.deletedCount)
            assertTrue(!Files.exists(root.resolve("delete.bin")))
            assertTrue(engine.jobs.value.first { it.id == job.id }.artifacts.single().removed)
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun followsRelativeLocationResolvedByThePlatform() = runBlocking {
        val server = testServer()
        server.createContext("/start") { exchange ->
            exchange.responseHeaders.set("Location", "/files/tiny.bin")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/files/tiny.bin") { exchange ->
            exchange.sendResponseHeaders(200, 16)
            exchange.responseBody.use { out -> out.write(ByteArray(16) { 1 }) }
        }
        val root = Files.createTempDirectory("anydownload-root-redir")
        try {
            val base = baseUrl(server)
            val store = JavaNetFileStore(root)
            val engine = engine(server, store, root, fixtureCheck(base))

            val job = engine.submit(
                DownloadRequest(
                    sourceUrl = "$base/start",
                    options = DownloadOptions(startPolicy = StartPolicy.AUTOMATIC),
                    idempotencyKey = "jvm-redir-key",
                )
            )
            waitForTerminal(engine, job.id)

            val finished = engine.jobs.value.first { it.id == job.id }
            assertEquals(JobState.COMPLETED, finished.state)
            // The artifact name comes from the final hop, courtesy of the
            // platform resolving the relative Location.
            assertEquals("tiny.bin", finished.artifacts.single().relativePath)
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun fileStoreRejectsTraversalOutsideTheRoot() {
        val root = Files.createTempDirectory("anydownload-root-traversal")
        try {
            val store = JavaNetFileStore(root)
            val handle = store.createTempFile()
            try {
                assertFailsWith<IllegalArgumentException> { store.publish(handle, "../evil.bin") }
                assertFailsWith<IllegalArgumentException> { store.publish(handle, "a/../../evil.bin") }
                assertFailsWith<IllegalArgumentException> { store.publish(handle, "/absolute.bin") }
            } finally {
                handle.discard()
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun fileStoreWritesAndDeletesUnderTheRoot() {
        val root = Files.createTempDirectory("anydownload-root-rw")
        try {
            val store = JavaNetFileStore(root)
            val handle = store.createTempFile()
            handle.write(ByteArray(4096) { 5 }, 4096)
            handle.close()
            val relative = store.publish(handle, "folder/file.bin")
            assertEquals("folder/file.bin", relative)
            assertEquals(4096L, store.size(relative))
            assertTrue(Files.isRegularFile(root.resolve(relative)))

            assertTrue(store.delete(relative))
            assertFalse(Files.exists(root.resolve(relative)))
            assertEquals(null, store.size(relative))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private suspend fun waitForTerminal(engine: HttpDownloadEngine, jobId: String): DownloadJob =
        withTimeout<DownloadJob>(30_000) {
            while (true) {
                val job = engine.jobs.value.first { it.id == jobId }
                if (job.state.isTerminal) return@withTimeout job
                delay(10)
            }
            error("Unreachable")
        }

    private suspend fun awaitCleanRoot(root: Path) = withTimeout<Unit>(30_000) {
        while (Files.list(root).use { it.count() } != 0L) {
            delay(10)
        }
    }
}