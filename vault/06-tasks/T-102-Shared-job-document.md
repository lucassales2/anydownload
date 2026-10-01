---
id: T-102
type: task
priority: P0
milestone: D8
tags: [task, queue, persistence]
---

# T-102 — Shared job document

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md)

## Outcome

Job persistence rules live in `shared/core`, not only in `DesktopStore`. A host stores bytes. The common code decides what a restart means.

## Dependencies

- [T-101](T-101-On-device-contract.md).

## Context the next session needs

`apps/desktop/.../DesktopStore.kt` loads `jobs.json`, rejects a stale revision, sanitizes destination folders, marks active jobs interrupted, and drops rows past the clear-completed age. `PersistingDownloadEngine` writes after each mutation. Android, iOS, and web do not use this store. `JobState`, `DownloadJob`, and `Artifact` are already serializable concepts; the desktop DTO is `StoreDto.kt`.

## Work

- Add `com.anydownlod.core.persist` with a codec that reads and writes the job list as a string, plus the interrupt, stale-revision, destination-sanitize, and clear-completed rules. Unknown fields are ignored. An unknown state wire name becomes `UNKNOWN`.
- Move those rules out of `DesktopStore` so desktop calls the shared codec and still renames a temp file over `jobs.json` in the state directory. Keep the on-disk document compatible with the current desktop file, or migrate on load and cover that in a test.
- Cookie bytes never enter the document. Signed media URLs never enter it. The document is metadata only.
- Do not wire Android, iOS, or web in this task. That is T-103.

## Tests

- Common tests: an active job loads as `FAILED`, `ENGINE_UNAVAILABLE`, retryable; `PENDING`, `SCHEDULED`, `COMPLETED`, `FAILED`, and `CANCELLED` stay; a stale revision does not replace a newer row; a destination that escapes the root is cleared; clear-completed drops only the rows the setting names.
- Desktop store test still passes against the shared codec, including the existing relaunch test.

## Acceptance criteria

- [x] The interrupt, stale-revision, sanitize, and clear-completed rules have common tests and no longer exist only in `DesktopStore`.
- [x] Desktop still persists `jobs.json` by an atomic rename in the state directory.
- [x] A completed job's artifact list survives a load.
- [x] The document contains no cookie field and no media bytes.

## Evidence / notes

2026-09-29.

New shared code in `anydownload`:

- `JobDocumentCodec.kt` reads and writes the job list as a JSON string in the same shape as the previous desktop `jobs.json` (pretty printed, defaults encoded, unknown fields ignored). The private DTOs carry the domain fields the old desktop DTO dropped too: `formatsNeedingJs`, `selectedMediaIds`, and `MediaTags.lyrics`. There is no cookie value/contents field and no free-form yt-dlp JSON field. An unknown state wire name becomes `JobState.UNKNOWN`.
- `JobDocumentStore.kt` owns the restart rules: active states (`RESOLVING`, `QUEUED`, `DOWNLOADING`, `POSTPROCESSING`) become `FAILED` with `ENGINE_UNAVAILABLE`, retryable, with the open attempt closed; `PENDING`/`SCHEDULED`/terminal rows stay; an escaping destination folder is cleared; rows past `clearAfterSeconds` are dropped on restore and save; a stale revision never replaces a newer row. It keeps the same-instance revision baseline and synchronizes with `engineCriticalSection`.

Desktop changes: `DesktopStore.load` reads `jobs.json` as text and calls `JobDocumentStore.restore` (malformed JSON is still moved aside with a warning); `DesktopStore.saveJobs` calls `JobDocumentStore.save` and still writes through `writeAtomically` (temp file and rename in the state directory). Job DTOs and job mapping were removed from `StoreDto.kt`; `OptionsDto`/`ErrorDto` remain for the subscriptions document. `DesktopStoreTest.everyJobStateAndOptionRoundTripsThroughTheDto` now round-trips through `JobDocumentCodec`.

Verification run:

- `./gradlew :shared:core:jvmTest --tests "com.anydownlod.core.persist.JobDocumentStoreTest" :apps:desktop:test --tests "com.anydownlod.desktop.store.DesktopStoreTest"` — PASS (`JobDocumentStoreTest`: 9 tests, 0 failures; `DesktopStoreTest`: 12 tests, 0 failures, including `activeJobsBecomeRetryableFailuresAndPendingSurvives`, the atomic-write test, stale revision, clear-completed, unsafe destination, and the relaunch test).
- `./gradlew :shared:core:jvmTest :apps:desktop:test` — BUILD SUCCESSFUL (full JVM suites).
- `./gradlew :shared:core:compileKotlinWasmJs :shared:core:compileTestKotlinWasmJs :shared:core:compileKotlinIosSimulatorArm64 :shared:core:compileTestKotlinIosSimulatorArm64` — BUILD SUCCESSFUL (common code and tests compile on web and iOS).

Android, iOS, and web are not wired in this task; that is T-103.
