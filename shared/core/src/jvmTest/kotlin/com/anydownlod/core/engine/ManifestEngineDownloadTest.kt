/*
 * HLS/DASH engine download tests — AnyDownload (T-073)
 *
 * A local `HttpServer` serves a master + media playlist, an AES-128 variant,
 * and an MPD `SegmentTemplate`; the engine must assemble byte-exact files
 * through the fragment downloader and never save playlist text. Unlicense;
 * see shared/core/NOTICE.md. All bytes and URLs are synthetic.
 */
package com.anydownlod.core.engine

import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.platform.JavaNetFileStore
import com.anydownlod.core.platform.JavaNetHttpTransfer
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ManifestEngineDownloadTest {

    private val tsFragments = listOf(
        "FRAGMENT-ZERO".encodeToByteArray(),
        "FRAGMENT-ONE".encodeToByteArray(),
        "FRAGMENT-TWO".encodeToByteArray(),
    )
    private val keyBytes = ByteArray(16) { it.toByte() }
    private val ivHex = "101112131415161718191a1b1c1d1e1f"
    private val encFragments = listOf(
        "8997c6837d7190199fa790420922462e301bfa64f5465b96e68a47d365e8ace2",
        "e2b686daaf86fd514e3d7706c3ed4374c59f650e806fe9de13afb9a35926e862",
    )

    private fun hex(value: String) = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun respond(exchange: HttpExchange, contentType: String, bytes: ByteArray, status: Int = 200) {
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun server(): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/master.m3u8") { exchange ->
            respond(
                exchange,
                "application/vnd.apple.mpegurl",
                """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e"
                    media.m3u8
                """.trimIndent().encodeToByteArray(),
            )
        }
        server.createContext("/media.m3u8") { exchange ->
            respond(
                exchange,
                "application/vnd.apple.mpegurl",
                """
                    #EXTM3U
                    #EXT-X-TARGETDURATION:6
                    #EXT-X-MEDIA-SEQUENCE:0
                    #EXTINF:5.0,
                    f0.ts
                    #EXTINF:5.0,
                    f1.ts
                    #EXTINF:5.0,
                    f2.ts
                    #EXT-X-ENDLIST
                """.trimIndent().encodeToByteArray(),
            )
        }
        for ((index, bytes) in tsFragments.withIndex()) {
            server.createContext("/f$index.ts") { exchange -> respond(exchange, "video/mp2t", bytes) }
        }
        server.createContext("/enc.m3u8") { exchange ->
            respond(
                exchange,
                "application/x-mpegurl",
                """
                    #EXTM3U
                    #EXT-X-TARGETDURATION:6
                    #EXT-X-MEDIA-SEQUENCE:0
                    #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x$ivHex
                    #EXTINF:5.0,
                    e0.ts
                    #EXTINF:5.0,
                    e1.ts
                    #EXT-X-ENDLIST
                """.trimIndent().encodeToByteArray(),
            )
        }
        server.createContext("/key.bin") { exchange -> respond(exchange, "application/octet-stream", keyBytes) }
        for ((index, value) in encFragments.withIndex()) {
            server.createContext("/e$index.ts") { exchange -> respond(exchange, "video/mp2t", hex(value)) }
        }
        server.createContext("/dash/manifest.mpd") { exchange ->
            respond(
                exchange,
                "application/dash+xml",
                """
                    <?xml version="1.0"?>
                    <MPD mediaPresentationDuration="PT20S">
                      <Period>
                        <AdaptationSet mimeType="video/mp4" codecs="avc1.4d401f">
                          <Representation id="v1" bandwidth="1500000" width="1280" height="720">
                            <SegmentTemplate timescale="1000" duration="10000" startNumber="1"
                              initialization="init.m4s" media="seg-${'$'}Number${'$'}.m4s"/>
                          </Representation>
                        </AdaptationSet>
                      </Period>
                    </MPD>
                """.trimIndent().encodeToByteArray(),
            )
        }
        server.createContext("/dash/init.m4s") { exchange -> respond(exchange, "video/mp4", "INIT".encodeToByteArray()) }
        server.createContext("/dash/seg-1.m4s") { exchange -> respond(exchange, "video/mp4", "SEG-1".encodeToByteArray()) }
        server.createContext("/dash/seg-2.m4s") { exchange -> respond(exchange, "video/mp4", "SEG-2".encodeToByteArray()) }
        server.createContext("/slow.m3u8") { exchange ->
            respond(
                exchange,
                "application/vnd.apple.mpegurl",
                """
                    #EXTM3U
                    #EXT-X-TARGETDURATION:6
                    #EXTINF:5.0,
                    s0.ts
                    #EXTINF:5.0,
                    s1.ts
                    #EXTINF:5.0,
                    s2.ts
                    #EXT-X-ENDLIST
                """.trimIndent().encodeToByteArray(),
            )
        }
        server.createContext("/s0.ts") { exchange -> respond(exchange, "video/mp2t", "S0".encodeToByteArray()) }
        server.createContext("/s1.ts") { exchange ->
            Thread.sleep(4_000)
            respond(exchange, "video/mp2t", "S1".encodeToByteArray())
        }
        server.createContext("/s2.ts") { exchange -> respond(exchange, "video/mp2t", "S2".encodeToByteArray()) }
        server.start()
        return server
    }

    private fun engine(server: HttpServer, root: Path): HttpDownloadEngine {
        val base = "http://127.0.0.1:${server.address.port}"
        return HttpDownloadEngine(
            transfer = JavaNetHttpTransfer(),
            fileStore = JavaNetFileStore(root),
            settings = InMemorySettingsRepository(AppSettings(downloadRoot = root.toString())),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
            urlCheck = { url -> if (url.startsWith(base)) UrlCheck.Allowed(url) else UrlPolicy.check(url) },
        )
    }

    private suspend fun waitForTerminal(engine: HttpDownloadEngine, jobId: String): DownloadJob =
        withTimeout(60_000) {
            engine.jobs.first { jobs -> jobs.any { it.id == jobId && it.state.isTerminal } }
                .first { it.id == jobId }
        }

    private fun submit(engine: HttpDownloadEngine, url: String, key: String) = engine.submit(
        DownloadRequest(sourceUrl = url, options = DownloadOptions(startPolicy = StartPolicy.AUTOMATIC), idempotencyKey = key),
    )

    @Test
    fun masterPlaylistAssemblesTsFragments() = runBlocking {
        val server = server()
        val root = Files.createTempDirectory("anydownlod-hls")
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val engine = engine(server, root)
            val job = submit(engine, "$base/master.m3u8", "hls-key")
            val finished = waitForTerminal(engine, job.id)
            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertEquals("master.ts", finished.artifacts.single().relativePath)
            val bytes = Files.readAllBytes(root.resolve("master.ts"))
            assertEquals(tsFragments.joinToString("") { it.decodeToString() }, bytes.decodeToString())
            assertFalse(bytes.decodeToString().contains("#EXTM3U"))
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun aes128PlaylistIsDecrypted() = runBlocking {
        val server = server()
        val root = Files.createTempDirectory("anydownlod-hls-aes")
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val engine = engine(server, root)
            val job = submit(engine, "$base/enc.m3u8", "hls-aes-key")
            val finished = waitForTerminal(engine, job.id)
            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertEquals(
                "TS-FRAGMENT-ONE\nTS-FRAGMENT-TWO\n",
                Files.readAllBytes(root.resolve("enc.ts")).decodeToString(),
            )
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun mpdSegmentTemplateAssemblesMp4Fragments() = runBlocking {
        val server = server()
        val root = Files.createTempDirectory("anydownlod-dash")
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val engine = engine(server, root)
            val job = submit(engine, "$base/dash/manifest.mpd", "dash-key")
            val finished = waitForTerminal(engine, job.id)
            assertEquals(JobState.COMPLETED, finished.state, finished.error?.message)
            assertEquals("manifest.mp4", finished.artifacts.single().relativePath)
            assertEquals("INITSEG-1SEG-2", Files.readAllBytes(root.resolve("manifest.mp4")).decodeToString())
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancelMidPlaylistLeavesNoFile() = runBlocking {
        val server = server()
        val root = Files.createTempDirectory("anydownlod-hls-cancel")
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val engine = engine(server, root)
            val job = submit(engine, "$base/slow.m3u8", "hls-cancel-key")
            withTimeout(30_000) {
                engine.jobs.first { jobs ->
                    jobs.any { it.id == job.id && it.progress?.phase?.contains("1/3") == true }
                }
            }
            engine.cancel(job.id)
            val finished = waitForTerminal(engine, job.id)
            assertEquals(JobState.CANCELLED, finished.state, finished.error?.message)
            assertTrue(finished.artifacts.isEmpty())
            var published = Files.list(root).use { it.toList() }
            for (attempt in 0 until 25) {
                if (published.isEmpty()) break
                delay(200)
                published = Files.list(root).use { it.toList() }
            }
            assertTrue(published.isEmpty(), "cancel published ${published.map { it.fileName }}")
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }
}
