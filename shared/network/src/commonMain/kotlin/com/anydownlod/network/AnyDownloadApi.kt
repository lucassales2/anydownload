package com.anydownlod.network

import com.anydownlod.core.domain.Capabilities
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.network.dto.CapabilitiesDto
import com.anydownlod.network.dto.JobDto
import com.anydownlod.network.dto.JobListDto
import com.anydownlod.network.dto.toDomain
import com.anydownlod.network.dto.toDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Typed client for the proposed AnyDownload API
 * (`vault/02-architecture/API-outline.md`).
 *
 * This is a scaffold of the client side of the contract, not a claim that the
 * server exists or that these routes are frozen. Authentication, event streams,
 * artifact delivery and error mapping are deliberately left to the M1 vertical
 * slice (T-009/T-010).
 */
class AnyDownloadApi(
    private val http: HttpClient = defaultHttpClient(),
    baseUrl: String = DEFAULT_SERVER_BASE_URL,
) {
    private val baseUrl: String = baseUrl.trimEnd('/')

    suspend fun capabilities(): Capabilities =
        http.get("$baseUrl/api/v1/capabilities").body<CapabilitiesDto>().toDomain()

    suspend fun jobs(): List<DownloadJob> =
        http.get("$baseUrl/api/v1/jobs").body<JobListDto>().items.map(JobDto::toDomain)

    suspend fun createJob(request: DownloadRequest): DownloadJob =
        http.post("$baseUrl/api/v1/jobs") {
            contentType(ContentType.Application.Json)
            setBody(request.toDto())
        }.body<JobDto>().toDomain()

    suspend fun cancelJob(jobId: String): DownloadJob =
        http.post("$baseUrl/api/v1/jobs/$jobId/cancel").body<JobDto>().toDomain()

    fun close() {
        http.close()
    }
}
