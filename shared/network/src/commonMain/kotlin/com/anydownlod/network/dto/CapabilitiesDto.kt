package com.anydownlod.network.dto

import kotlinx.serialization.Serializable

/** Wire shape of `GET /api/v1/capabilities` (proposed in the API outline). */
@Serializable
data class CapabilitiesDto(
    val apiVersion: String,
    val engine: EngineDto? = null,
    val mediaTypes: List<String> = emptyList(),
    val features: List<String> = emptyList(),
)

@Serializable
data class EngineDto(
    val name: String,
    val version: String? = null,
)
