---
id: T-104
type: task
priority: P0
milestone: D8
tags: [task, engine, queue]
---

# T-104 — Concurrency, cancel, and retry

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md)

## Outcome

The shared engine keeps one finished file per completed job, applies the concurrency setting, and lets cancel win a race with completion. A source that is not available yet waits in `SCHEDULED`.

## Dependencies

- [T-102](T-102-Shared-job-document.md).

## Context the next session needs

`HttpDownloadEngine` already submits, downloads, cancels, and retries. Its semaphore is built from `maxConcurrentDownloads` once. `DownloadEngine.retry` appends an attempt. `InMemoryDownloadEngine` does not download. Idempotency keys already return the original job.

## Work

- Read the concurrency limit when a worker starts, so a settings change applies to later jobs. Never run more downloads than that limit.
- Cancel of an active job discards the temp file and lands on `CANCELLED`. If completion and cancel race, exactly one terminal state is stored. A cancel that loses to a commit that already published the artifact leaves the job `COMPLETED` and does not delete that file.
- `retry` on `FAILED` or `CANCELLED` appends an attempt and queues work. `retry` on `COMPLETED` returns the same job and does not write another file. A second submit with the same idempotency key returns the original job.
- When extraction reports the media is not available yet, set `SCHEDULED`, download nothing, and wait for `start`. Do not add a timer, a countdown, or a live playlist.
- Bulk start and bulk cancel operate on the ids the caller passes. Unknown ids are skipped. One failure does not roll back the others.
- Do not change playlist behavior here.

## Tests

- Two jobs and a limit of 1: the second stays queued until the first finishes.
- Cancel during the body write leaves no completed artifact.
- A completion that wins the race keeps one file and `COMPLETED`.
- Retry of a failed job downloads once; retry of a completed job does not download again.
- A not-yet-available extraction becomes `SCHEDULED` and performs no media GET until `start`.

## Acceptance criteria

- [x] Concurrency is the settings value at the time work starts, covered by a test.
- [x] Cancel versus complete has one persisted winner, and a finished file survives a late cancel.
- [x] Retry does not duplicate a completed artifact. Idempotency returns the original job.
- [x] Not-yet-available is `SCHEDULED` with no timer and no media GET.

## Evidence / notes

2026-09-29.

Engine changes in `shared/core`:

- `HttpDownloadEngine` no longer builds a fixed `Semaphore`. `acquireWorkerSlot` reads `maxConcurrentDownloads` from the current settings and counts active workers; `releaseWorkerSlot` bumps a version any waiter wakes on. A lowered limit still never lets a new worker exceed it, and a limit raised before a worker starts applies to that worker.
- `cancel` now makes its terminal transition conditional: if completion already recorded `COMPLETED` (or the row is `UNKNOWN`), cancel returns it unchanged. `confirmCancelled` also refuses to overwrite `COMPLETED`, so a cancel that loses to a publish cannot replace the winner.
- `ExtractionError.NotYetAvailable` is new. `HttpDownloadEngine` and `WebExtensionEngine` catch it and call `schedule`, which stores `SCHEDULED` with no error, no progress, and no media request; `start` still moves `SCHEDULED` to `QUEUED`.
- Bulk start/cancel already live in `QueuePresenter`; the presenter test now pins unknown-id skipping and no rollback.

Tests added:

- `HttpDownloadEngineTest`: `concurrencyLimitOneQueuesTheSecondJobUntilTheFirstFinishes`, `concurrencyLimitIsReadWhenAWorkerStarts`, `retryOfCompletedJobDoesNotDownloadAgain`, `lateCancelAfterCompletionKeepsTheCompletedFile`, `cancelRacingThePublishLetsCompletionWinAndKeepsOneFile`.
- `EngineExtractionTest`: `notYetAvailableSchedulesAndWaitsForStartWithoutAMediaGet`.
- `WebExtensionEngineTest`: `aNotYetAvailableExtractionSchedulesWithoutDownloading`.
- `QueuePresenterTest`: `bulkActionsOperateOnKnownIdsOnlyAndDoNotRollBack`.

Verification run:

- `./gradlew :shared:core:jvmTest :shared:ui:jvmTest` — BUILD SUCCESSFUL; core 502 tests, ui 91 tests, 0 failures.
- `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:wasmJsBrowserTest :shared:core:iosSimulatorArm64Test` — BUILD SUCCESSFUL; wasm 454 tests, iOS 465 tests, 0 failures.
- `./gradlew :apps:desktop:test :apps:android-engine-tests:test :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs` — BUILD SUCCESSFUL; desktop 130 tests, android-engine-tests 25 tests. One desktop run of `DesktopSpotifyDownloadGateTest.aTwoSongListWritesAnM3uInOrderAndArchivesTheSongs` failed with an FFmpeg `POSTPROCESSING_FAILURE` only while the wasm/Gradle tasks ran on the same machine in parallel; the class passed 3 clean isolated reruns (`:apps:desktop:cleanTest :apps:desktop:test --tests ...`) and the full desktop suite passed when run without the parallel load. No code change was needed.
