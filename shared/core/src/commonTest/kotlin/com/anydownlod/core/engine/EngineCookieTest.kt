package com.anydownlod.core.engine

import com.anydownlod.core.cookies.CookieJar
import com.anydownlod.core.cookies.CookieJarSource
import com.anydownlod.core.domain.AppSettings
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.extract.ExtractorHttp
import com.anydownlod.core.extract.ExtractorRegistry
import com.anydownlod.core.extract.InfoDict
import com.anydownlod.core.extract.InfoExtractor
import com.anydownlod.core.extract.MediaFormat
import com.anydownlod.core.fake.InMemorySettingsRepository
import com.anydownlod.core.persist.JobDocumentCodec
import com.anydownlod.core.platform.ByteArrayHttpBody
import com.anydownlod.core.platform.FileHandle
import com.anydownlod.core.platform.FileStore
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The T-018 engine gate: a job with `useCookies` resolves one jar snapshot
 * and every media request reads it; a missing, empty, or fully expired file
 * fails typed before any request; and neither the job document nor an error
 * message carries a cookie name, value, or path. Synthetic jar text only.
 */
class EngineCookieTest {

    private val mediaUrl = "https://media.example.com/files/tiny.bin"

    private val cookieFile = "# Netscape HTTP Cookie File\n" +
        ".example.com\tTRUE\t/\tFALSE\t0\tfake_session\tfake_value\n"

    private val twoHostCookieFile = "# Netscape HTTP Cookie File\n" +
        "media.example.com\tFALSE\t/\tFALSE\t0\tfake_session\tfake_value\n" +
        "cdn.example.com\tFALSE\t/\tFALSE\t0\tfake_cdn\tfake_cdn_value\n"

    private var idCounter = 0

    private fun request(
        useCookies: Boolean,
        url: String = mediaUrl,
        key: String = "cookie-key",
    ) = DownloadRequest(
        sourceUrl = url,
        options = DownloadOptions(useCookies = useCookies),
        idempotencyKey = key,
    )

    private fun fileResponse(): HttpResponse = HttpResponse.Final(
        statusCode = 200,
        contentType = "application/octet-stream",
        totalBytes = 4,
        body = ByteArrayHttpBody(byteArrayOf(1, 2, 3, 4)),
    )

    private class RecordingTransfer(private val responder: (HttpRequest) -> HttpResponse) : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return responder(request)
        }
    }

    private class MemoryFileHandle : FileHandle {
        private val chunks = mutableListOf<ByteArray>()
        val bytes: ByteArray get() = chunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
        override fun write(bytes: ByteArray, length: Int) {
            chunks += bytes.copyOf(length)
        }

        override fun close() = Unit
        override fun discard() = Unit
    }

    private class MemoryFileStore : FileStore {
        val published = mutableMapOf<String, ByteArray>()
        override fun createTempFile(): FileHandle = MemoryFileHandle()
        override fun publish(temp: FileHandle, relativePath: String): String {
            published[relativePath] = (temp as MemoryFileHandle).bytes
            return relativePath
        }

        override fun delete(relativePath: String): Boolean = published.remove(relativePath) != null
        override fun size(relativePath: String): Long? = published[relativePath]?.size?.toLong()
    }

    private fun TestScope.engine(
        transfer: HttpTransfer,
        cookieJarSource: CookieJarSource? = null,
        registry: ExtractorRegistry? = null,
        persist: (List<DownloadJob>) -> Unit = {},
    ) = HttpDownloadEngine(
        transfer = transfer,
        fileStore = MemoryFileStore(),
        settings = InMemorySettingsRepository(AppSettings(downloadRoot = "cookie-root")),
        scope = this,
        idGenerator = { "id-${++idCounter}" },
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        registry = registry,
        cookieJarSource = cookieJarSource,
        persist = persist,
    )

    @Test
    fun directDownloadCarriesTheJarHeader() = runTest {
        val transfer = RecordingTransfer { fileResponse() }
        val jar = CookieJar.fromText(cookieFile)
        val engine = engine(transfer, cookieJarSource = CookieJarSource { jar })

        val job = engine.submit(request(useCookies = true))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.COMPLETED, finished.state)
        val sanitized = transfer.requests.single().sanitized()
        assertEquals("fake_session=fake_value", sanitized.request.headers["cookie"])
        // The trusted value never appears in the diagnostic name list.
        assertFalse(sanitized.droppedHeaders.any { it.contains("fake") })
    }

    @Test
    fun useCookiesFalseSendsNoCookieHeader() = runTest {
        val transfer = RecordingTransfer { fileResponse() }
        val engine = engine(transfer, cookieJarSource = CookieJarSource { CookieJar.fromText(cookieFile) })

        val job = engine.submit(request(useCookies = false))
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
        assertFalse(transfer.requests.single().sanitized().request.headers.containsKey("cookie"))
    }

    @Test
    fun redirectRecomputesTheHeaderPerHop() = runTest {
        val transfer = RecordingTransfer { current ->
            if (current.url.startsWith("https://media.example.com/")) {
                HttpResponse.Redirect(location = "https://cdn.example.com/files/tiny.bin", statusCode = 302)
            } else {
                fileResponse()
            }
        }
        val engine = engine(transfer, cookieJarSource = CookieJarSource { CookieJar.fromText(twoHostCookieFile) })

        val job = engine.submit(request(useCookies = true))
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
        assertEquals(2, transfer.requests.size)
        assertEquals("fake_session=fake_value", transfer.requests[0].sanitized().request.headers["cookie"])
        assertEquals("fake_cdn=fake_cdn_value", transfer.requests[1].sanitized().request.headers["cookie"])
    }

    @Test
    fun deletionDuringActiveWorkKeepsTheInFlightJarSnapshot() = runTest {
        var deleted = false
        val transfer = RecordingTransfer { current ->
            if (current.url.startsWith("https://media.example.com/")) {
                // Simulate the user deleting the file while the job is live.
                deleted = true
                HttpResponse.Redirect(location = "https://cdn.example.com/files/tiny.bin", statusCode = 302)
            } else {
                fileResponse()
            }
        }
        val jar = CookieJar.fromText(twoHostCookieFile)
        val source = CookieJarSource { if (deleted) null else jar }
        val engine = engine(transfer, cookieJarSource = source)

        val job = engine.submit(request(useCookies = true))
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
        assertEquals(2, transfer.requests.size)
        assertEquals("fake_session=fake_value", transfer.requests[0].sanitized().request.headers["cookie"])
        // The snapshot still holds the second hop's row after the delete.
        assertEquals("fake_cdn=fake_cdn_value", transfer.requests[1].sanitized().request.headers["cookie"])

        // A job started after the delete sees no file and fails typed.
        val later = engine.submit(request(useCookies = true, key = "after-delete"))
        testScheduler.advanceUntilIdle()
        assertEquals(JobState.FAILED, engine.jobs.value.first { it.id == later.id }.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, engine.jobs.value.first { it.id == later.id }.error?.code)
    }

    @Test
    fun missingJarFailsTypedWithoutARequest() = runTest {
        val transfer = RecordingTransfer { fileResponse() }
        val engine = engine(transfer, cookieJarSource = CookieJarSource { null })

        val job = engine.submit(request(useCookies = true))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        assertTrue(transfer.requests.isEmpty())
        assertFalse(finished.error?.message.orEmpty().contains("fake"))
    }

    @Test
    fun expiredJarFailsTypedWithoutARequestAndWithoutSecrets() = runTest {
        val transfer = RecordingTransfer { fileResponse() }
        val expired = CookieJar.fromText(
            "# Netscape HTTP Cookie File\n" +
                ".example.com\tTRUE\t/\tFALSE\t1\tfake_session\tfake_value\n",
        )
        val engine = engine(transfer, cookieJarSource = CookieJarSource { expired })

        val job = engine.submit(request(useCookies = true))
        testScheduler.advanceUntilIdle()

        val finished = engine.jobs.value.first { it.id == job.id }
        assertEquals(JobState.FAILED, finished.state)
        assertEquals(JobErrorCode.INVALID_URL_OPTIONS, finished.error?.code)
        assertFalse(finished.error?.retryable ?: true)
        assertTrue(transfer.requests.isEmpty())
        val message = finished.error?.message.orEmpty()
        assertFalse(message.contains("fake_session"))
        assertFalse(message.contains("fake_value"))
    }

    @Test
    fun emptyJarFailsTypedWithoutARequest() = runTest {
        val transfer = RecordingTransfer { fileResponse() }
        val engine = engine(
            transfer,
            cookieJarSource = CookieJarSource { CookieJar.fromText("# Netscape HTTP Cookie File\n") },
        )

        val job = engine.submit(request(useCookies = true))
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.FAILED, engine.jobs.value.first { it.id == job.id }.state)
        assertTrue(transfer.requests.isEmpty())
    }

    private class FetchThenReportExtractor(extractorHttp: ExtractorHttp) : InfoExtractor(
        ieKey = "FixtureFetch",
        http = extractorHttp,
        validUrl = Regex("""https://extract\.example\.com/.+"""),
    ) {
        override suspend fun extract(url: String): InfoDict {
            // Exercises the real ExtractorHttp path inside the job context.
            http.downloadWebpage(url)
            return InfoDict(
                id = "fixture",
                title = "Fixture Clip",
                webpageUrl = url,
                formats = listOf(
                    MediaFormat(
                        formatId = "18",
                        url = "https://cdn.example.com/media.mp4",
                        ext = "mp4",
                        vcodec = "avc1",
                        acodec = "mp4a",
                    ),
                ),
            )
        }
    }

    @Test
    fun extractorAndMediaRequestsCarryTheJarHeaderInsideOneJob() = runTest {
        val transfer = RecordingTransfer { current ->
            if (current.url.startsWith("https://extract.example.com/")) {
                HttpResponse.Final(
                    statusCode = 200,
                    contentType = "text/html; charset=utf-8",
                    body = ByteArrayHttpBody("<html><body>fixture</body></html>".encodeToByteArray()),
                )
            } else {
                fileResponse()
            }
        }
        val jar = CookieJar.fromText(
            "# Netscape HTTP Cookie File\n" +
                "extract.example.com\tFALSE\t/\tFALSE\t0\tfake_session\tfake_value\n" +
                "cdn.example.com\tFALSE\t/\tFALSE\t0\tfake_cdn\tfake_cdn_value\n",
        )
        val engine = engine(
            transfer = transfer,
            cookieJarSource = CookieJarSource { jar },
            registry = ExtractorRegistry(listOf(FetchThenReportExtractor(ExtractorHttp(transfer)))),
        )

        val job = engine.submit(request(useCookies = true, url = "https://extract.example.com/watch"))
        testScheduler.advanceUntilIdle()

        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)
        assertEquals(2, transfer.requests.size)
        assertEquals("fake_session=fake_value", transfer.requests[0].sanitized().request.headers["cookie"])
        assertEquals("fake_cdn=fake_cdn_value", transfer.requests[1].sanitized().request.headers["cookie"])
    }

    @Test
    fun jobDocumentCarriesTheFlagButNoCookieText() = runTest {
        val transfer = RecordingTransfer { fileResponse() }
        val documents = mutableListOf<String>()
        val engine = engine(
            transfer,
            cookieJarSource = CookieJarSource { CookieJar.fromText(cookieFile) },
            persist = { jobs -> documents += JobDocumentCodec.encode(jobs) },
        )

        val job = engine.submit(request(useCookies = true))
        testScheduler.advanceUntilIdle()
        assertEquals(JobState.COMPLETED, engine.jobs.value.first { it.id == job.id }.state)

        val document = documents.last()
        assertTrue(document.contains("\"useCookies\": true"))
        assertFalse(document.contains("fake_session"))
        assertFalse(document.contains("fake_value"))
        assertFalse(document.contains("cookies.txt"))
    }
}
