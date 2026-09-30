---
id: T-134
type: task
priority: P0
milestone: D10
tags: [task, engine, kmp, downloads]
---

# T-134 — Resume inside one attempt

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 10](../00-project/Phase-10-Shared-downloader.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

A ranged download that dies mid-body continues from the bytes already in the temp file when the server accepts ranges. A relaunch still marks the job `FAILED` / `ENGINE_UNAVAILABLE` and does not resume. Covers E-07.

## Dependencies

- [T-133](T-133-Format-lists.md) — Done 2026-09-29.

## Context the next session needs

- `HttpDownloadEngine.downloadDirectFile` owns the single-file path and `streamChunkedToTemp` (the `http_chunk_size` ranged reader); `streamToTemp` owns the non-ranged body. A mid-body transport failure, a premature `-1`, or a retryable HTTP status currently abandons the temp (the `finally` discards on failure).
- `HttpRequest.range`, `ContentRange`, and `writeChunk`/the `FileHandle` are the pieces a same-attempt range continuation uses.
- Relaunch behavior belongs to `PersistingDownloadEngine` and the restore path: active work restored from disk becomes `FAILED` / `ENGINE_UNAVAILABLE`, retryable, and does not auto-resume (ADR-012, T-104). Do not change it.

## Work

- When a ranged fetch ends mid-body inside one attempt, take the current temp size and, when the server supports it, continue with `Range: bytes=<size>-` instead of restarting from zero. Require a `206` whose `Content-Range` start equals the temp size; append from there. Redirect handling stays as it is.
- If the server ignores the range (`200`) or answers with a mismatched `Content-Range`, never append the body: restart from zero under a fresh temp or fail typed. Pick one behavior and pin it with a test.
- Cover both the chunked reader (`http_chunk_size`) and the single-body path when the first response was a range.
- Keep retries bounded per attempt. On exhaustion keep today's typed failure and discard the temp. Cancel still discards. Disk-write failures stay typed.
- Do not add cross-attempt resume, a `.part` file that survives a relaunch, or any change to relaunch semantics.

## Acceptance criteria

- [x] A local `HttpServer` that closes mid-body and then accepts `Range` at the offset produces a byte-exact artifact, and the test shows the retry started at the temp size.
- [x] A server that answers `200` to the range request never yields a corrupt file; the chosen restart-or-fail behavior is pinned by a test.
- [x] Retry exhaustion yields the same typed error and discards the temp; cancel mid-body discards it.
- [x] A relaunch test shows the job is `FAILED` / `ENGINE_UNAVAILABLE` and no bytes are reused across launches.
- [x] `:shared:core:jvmTest` is green; `:shared:core:compileKotlinWasmJs` and `:shared:core:compileKotlinIosSimulatorArm64` succeed.
- [x] No commit.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **Implementation:** `streamToTemp` and `streamChunkedToTemp` now hand each body to a shared `readBodyToTemp` reader; when the body drops — transport exception, a known total not reached, a stopped ranged body, or a zero-byte chunk — they continue with `resumeFromOffset`, which requires a `206` whose `Content-Range` start equals the temp size and appends only that body. A `200`, a mismatched `Content-Range`, a redirect, or a transport failure fails typed and the `finally` discards the temp. The continuation budget is `MAX_RESUME_ATTEMPTS = 3` per streaming call; a fresh attempt always creates a fresh temp, so no bytes cross attempts or launches. `ContentRange.parse`/`startByte` was added to `HttpRequest.kt`.
- **Chosen range-ignored behavior:** fail `NETWORK_FAILURE` (retryable) with “The server ignored the resume request, so the file cannot be continued safely.” and never append the `200` body. Pinned by `aRangeIgnoringServerFailsTypedRatherThanAppending`; the mismatched-range branch is pinned by `aMismatchedContentRangeOnTheResumeNeverAppends`.
- **Tests:** `JavaNetChunkedDownloadTest` gained `midBodyDropResumesFromTheTempSize` (chunked path; the retry request is exactly `bytes=1000-3047` at the 1000-byte temp size, artifact byte-exact), `singleBodyDropResumesWithARangedRetry` (non-chunked path; the resume request is `bytes=3000-8999` and the artifact is byte-exact), `aRangeIgnoringServerFailsTypedRatherThanAppending`, `aMismatchedContentRangeOnTheResumeNeverAppends`, `resumeExhaustionFailsTypedAndDiscardsTheTemp` (initial chunk + `MAX_RESUME_ATTEMPTS` requests, temp discarded), and `aFailedAttemptLeavesNoPartAndTheNextAttemptStartsAtZero` (no part survives; the next attempt's first request is `bytes=0-2047`). `HttpRequestTest.contentRangeParsesInclusiveRanges` pins the new parser.
- **Relaunch:** unchanged; the existing `PersistingDownloadEngineTest` cases (`wrapperWritesAfterEachMutationAndAReplacementStoreSeesTheRow`, `completedArtifactStaysRegisteredAndAnActiveRowReloadsRetryable`) still assert `FAILED` / `ENGINE_UNAVAILABLE`, retryable, with no auto-resume, and no `.part` file exists anywhere in the engine.
- **Commands:** `./gradlew --console=plain :shared:core:jvmTest` → BUILD SUCCESSFUL, 541 tests, 0 failures, 0 errors. `./gradlew --console=plain :shared:core:compileKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64` → BUILD SUCCESSFUL. `./gradlew --console=plain :shared:core:iosSimulatorArm64Test` → BUILD SUCCESSFUL, 492 tests, 0 failures, 0 errors.
- Cancel mid-body still discards the temp (`cancelMidStreamDiscardsTempAndCancelsTheJob`, `cancelBetweenChunksLeavesNoFile`); disk writes stay typed (`aHostWriteFailureIsDiskExhaustedAndDiscardsTheTemp`).
- No commit.
