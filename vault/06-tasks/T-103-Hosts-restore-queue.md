---
id: T-103
type: task
priority: P0
milestone: D8
tags: [task, platforms, persistence]
---

# T-103 — Android, iOS, and web restore the queue

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md)

## Outcome

A job list written on Android, iOS, or web is the same list after a new engine instance starts. The state location is not the download root.

## Dependencies

- [T-102](T-102-Shared-job-document.md).

## Context the next session needs

Desktop already wraps the engine with `PersistingDownloadEngine`. The other hosts build `InMemoryDownloadEngine` or the HTTP engine with an empty list. `IosFileStore` and the Android file helpers write downloads into a root. Web saves through `chrome.downloads` and cannot delete that file later.

## Work

- Android: a state file under the app files directory, separate from the download root. Load it into the engine at startup and save after each mutation.
- iOS: the same, inside the sandbox, separate from the download root. Foreground only. Do not add a background transfer.
- Web: `localStorage` (or the extension's local storage if the page cannot use `localStorage`) holds the job document only. Cap the document. If a write would exceed the cap, keep the in-memory list, surface a typed storage error, and do not drop jobs already on screen. Do not put media bytes in web storage.
- Each host's load uses the shared codec, so an active row becomes the retryable failure from T-102.
- A test on each host constructs a store, saves one completed job and one downloading job, builds a second store from the same bytes, and sees the completed artifact still registered and the downloading job failed-retryable.

## Acceptance criteria

- [x] Android, iOS, and web each round-trip a job document through a location that is not the download root.
- [x] An in-progress job reloads as retryable `FAILED`, and a completed artifact is still listed.
- [x] Web refuses to store media bytes and has a tested over-cap failure that does not wipe the on-screen list.
- [x] Desktop behavior from T-102 is unchanged.

## Evidence / notes

2026-09-29.

Shared additions in `com.anydownlod.core.persist`:

- `JobDocumentStorage` (read/write bytes) and `JobDocumentStorageError` (`FULL`, `UNAVAILABLE`); `JobDocumentStore.restore(storage, clearAfterSeconds)` reads through a host store.
- `PersistingDownloadEngine` wraps a host engine, writes after every mutation, exposes `storageError: StateFlow<JobDocumentStorageError?>`, and keeps the engine's rows untouched when a write is refused. `jobsSnapshot` covers Android's asynchronously merged routing flow.

Host wiring:

- **Android:** `AndroidJobDocumentStorage` writes `context.getDir("state", MODE_PRIVATE)/jobs.json`, which is outside the `filesDir` download root. `AndroidAppGraph` restores through the shared codec, splits restored rows with the new network-free `AndroidRouteClassifier.resumeRoute` (HTTP vs Chaquopy), seeds both engines, wraps the router, and exposes a startup warning for interrupted rows.
- **iOS:** `IosJobDocumentStorage` writes `Library/Application Support/AnyDownload/jobs.json` (Foundation atomic write), separate from the Documents download root. `IosAppGraph` restores, seeds the HTTP engine, and wraps it. Foreground only; no background transfer was added.
- **Web:** `WebJobDocumentStorage` uses one `localStorage` key (`anydownload.jobs.document`) through a small `WebKeyValueStorage` seam. The document is capped at 512 KiB; an over-cap write returns `FULL` without touching the stored value, inline `data:` media markers return `UNAVAILABLE`, and `WebAppGraph` exposes the typed `jobStorageError` while the in-memory list stays intact.

Verification run:

- `./gradlew :shared:core:jvmTest :apps:android-engine-tests:test` — BUILD SUCCESSFUL; `PersistingDownloadEngineTest` 3/3, `AndroidJobDocumentStorageTest` 3/3 (completed artifact survives, downloading row reloads `FAILED` + `ENGINE_UNAVAILABLE` retryable, state path outside the download root, typed unwritable error, resume split).
- `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :apps:web:wasmJsBrowserTest` — BUILD SUCCESSFUL; `WebJobDocumentStorageTest` 5/5 (localStorage round-trip, over-cap keeps the previous document, over-cap keeps the on-screen rows, inline media refused, real browser `localStorage` binding).
- `./gradlew :shared:ui:compileKotlinIosSimulatorArm64 :shared:ui:iosSimulatorArm64Test` — BUILD SUCCESSFUL; `IosJobDocumentStorageTest` 2/2.
- `./gradlew :apps:android:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew :apps:desktop:test :shared:core:jvmTest :shared:ui:jvmTest :shared:ui:compileKotlinIosArm64` — BUILD SUCCESSFUL; desktop 130 tests, 0 failures, so T-102 desktop behavior is unchanged.
