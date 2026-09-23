package com.anydownlod.network

import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnyDownloadApiTest {
    private fun api(engine: MockEngine): AnyDownloadApi =
        AnyDownloadApi(
            http = HttpClient(engine) { applyAnyDownloadDefaults() },
            baseUrl = "https://server.example.org/",
        )

    @Test
    fun capabilitiesAreParsedAndUnknownValuesAreIgnored() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/capabilities", request.url.encodedPath)
            assertEquals(HttpMethod.Get, request.method)
            respond(
                content = """
                    {
                      "apiVersion": "1",
                      "engine": {"name": "yt-dlp", "version": "2026.09.16"},
                      "mediaTypes": ["video", "audio", "playlist"],
                      "features": ["captions"],
                      "newCapability": true
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val capabilities = api(engine).capabilities()

        assertEquals("1", capabilities.apiVersion)
        assertEquals("yt-dlp", capabilities.engine?.name)
        assertEquals(setOf(MediaType.VIDEO, MediaType.AUDIO), capabilities.mediaTypes)
        assertEquals(setOf("captions"), capabilities.features)
    }

    @Test
    fun jobsAreMappedIncludingForwardCompatibleStates() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "items": [
                        {
                          "id": "job-1",
                          "state": "downloading",
                          "mediaType": "audio",
                          "revision": 4,
                          "progress": {"percent": 42.5, "downloadedBytes": 1024, "etaSeconds": 7}
                        },
                        {"id": "job-2", "state": "paused_by_engine"},
                        {"id": "job-3", "state": "failed", "mediaType": "captions"}
                      ],
                      "nextCursor": null
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val jobs = api(engine).jobs()

        assertEquals(3, jobs.size)
        assertEquals(JobState.DOWNLOADING, jobs[0].state)
        assertEquals(MediaType.AUDIO, jobs[0].request.options.mediaType)
        assertEquals(42.5, jobs[0].progress?.percent)
        assertEquals(4, jobs[0].revision)
        assertEquals(JobState.UNKNOWN, jobs[1].state)
        assertNull(jobs[1].progress)
        assertEquals(JobState.FAILED, jobs[2].state)
    }

    @Test
    fun cancelPostsToTheJobCancelEndpoint() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Post, request.method)
            respond(
                content = """{"id": "job-9", "state": "cancelled", "revision": 2}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val job = api(engine).cancelJob("job-9")

        assertEquals("/api/v1/jobs/job-9/cancel", seenPath)
        assertEquals(JobState.CANCELLED, job.state)
    }

    @Test
    fun createJobPostsAJsonBody() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/jobs", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            respond(
                content = """{"id": "job-10", "state": "queued", "revision": 1}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val job = api(engine).createJob(DownloadRequest(sourceUrl = "https://media.example.org/a"))

        assertEquals("job-10", job.id)
        assertEquals(JobState.QUEUED, job.state)
    }
}
