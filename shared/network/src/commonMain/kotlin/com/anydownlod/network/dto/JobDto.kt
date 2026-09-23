package com.anydownlod.network.dto

import kotlinx.serialization.Serializable

/** Wire shape of a job resource (`GET/POST /api/v1/jobs...`). */
@Serializable
data class JobDto(
    val id: String,
    val state: String,
    val mediaType: String? = null,
    val progress: ProgressDto? = null,
    val revision: Long = 0,
    val error: ApiErrorDto? = null,
)

@Serializable
data class ProgressDto(
    val phase: String? = null,
    val percent: Double? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val etaSeconds: Long? = null,
)

/** Cursor-paginated job list; pagination semantics are finalized in T-007/T-011. */
@Serializable
data class JobListDto(
    val items: List<JobDto> = emptyList(),
    val nextCursor: String? = null,
)

/** Safe, stable error payload. Raw engine output must never reach this shape. */
@Serializable
data class ApiErrorDto(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val correlationId: String? = null,
)
