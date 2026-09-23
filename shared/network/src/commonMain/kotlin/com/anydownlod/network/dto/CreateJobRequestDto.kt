package com.anydownlod.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape of `POST /api/v1/jobs`. Field names follow the illustrative
 * request in `vault/02-architecture/API-outline.md`; the reviewed contract is
 * T-007's deliverable. Only typed, bounded options cross this boundary.
 */
@Serializable
data class CreateJobRequestDto(
    val url: String,
    val mediaType: String? = null,
    val profile: String? = null,
    val quality: String? = null,
    val startPolicy: String? = null,
    val playlist: PlaylistOptionsDto? = null,
    val destination: DestinationDto? = null,
    val presetIds: List<String> = emptyList(),
    val overrides: Map<String, String> = emptyMap(),
    val credentialId: String? = null,
)

@Serializable
data class PlaylistOptionsDto(
    val mode: String = "single",
    val itemLimit: Int? = null,
)

@Serializable
data class DestinationDto(
    val folder: String? = null,
)
