package com.anydownlod.network.dto

import com.anydownlod.core.domain.Capabilities
import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.EngineInfo
import com.anydownlod.core.domain.JobError
import com.anydownlod.core.domain.JobErrorCode
import com.anydownlod.core.domain.JobProgress
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.domain.MediaType

internal fun CapabilitiesDto.toDomain(): Capabilities = Capabilities(
    apiVersion = apiVersion,
    engine = engine?.toDomain(),
    mediaTypes = mediaTypes.mapNotNull(MediaType::fromWire).toSet(),
    features = features.toSet(),
)

internal fun EngineDto.toDomain(): EngineInfo = EngineInfo(name = name, version = version)

internal fun JobDto.toDomain(): DownloadJob = DownloadJob(
    id = id,
    request = DownloadRequest(
        sourceUrl = "",
        options = DownloadOptions(mediaType = mediaType?.let(MediaType::fromWire) ?: MediaType.VIDEO),
    ),
    state = JobState.fromWire(state),
    progress = progress?.toDomain(),
    revision = revision,
    error = error?.toDomain(),
)

internal fun ProgressDto.toDomain(): JobProgress = JobProgress(
    phase = phase,
    percent = percent,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    etaSeconds = etaSeconds,
)

internal fun ApiErrorDto.toDomain(): JobError = JobError(
    code = JobErrorCode.fromWire(code),
    message = message,
    retryable = retryable,
)

internal fun DownloadRequest.toDto(): CreateJobRequestDto = CreateJobRequestDto(
    url = sourceUrl,
    mediaType = options.mediaType.wireName,
    profile = options.videoProfile?.wireName,
    quality = options.quality.token,
    startPolicy = options.startPolicy.wireName,
    playlist = options.playlistItemLimit.takeIf { it > 0 }
        ?.let { PlaylistOptionsDto(mode = "single", itemLimit = it) },
    destination = options.destinationFolder?.let { DestinationDto(folder = it) },
    presetIds = options.presetIds,
    credentialId = null,
)
