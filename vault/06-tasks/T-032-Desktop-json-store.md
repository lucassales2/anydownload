---
id: T-032
type: task
priority: P0
milestone: D1
tags: [task, desktop, storage]
---

# T-032 — Desktop JSON store

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md) · [Lifecycle](../02-architecture/Download-lifecycle.md)

## Outcome

Quit and relaunch the desktop app and the queue, history, subscriptions, settings, and presets are still there. The store is JSON files on disk, used only by `apps/desktop`. The in-memory fake remains the default for other hosts and for tests.

## Dependencies

- [T-026](T-026-Shared-domain-and-engine-seam.md).

Wire it into `apps/desktop` `main` even if the screens are only partly built. Once T-027 has an `App` graph parameter, pass this store instead of `InMemoryAppGraph`.

## Context the next session needs

MeTube persists `queue.json`, `pending.json`, `completed.json`, and `subscriptions.json` under a state directory. D1 can use one directory of JSON files with the same split, or one file per aggregate, as long as the layout is documented in Evidence and a crash mid-write cannot leave a half-written file as the only copy. Write to a temporary file in the same directory and rename it over the target.

Default state directory: the OS app-data folder for AnyDownload (`~/Library/Application Support/AnyDownload` on macOS is the right default for this machine). The download root is different: it is the folder the user picked in Settings, and it defaults to a `Downloads/AnyDownload` directory under the user home if unset. State JSON does not live inside the download root, so a "delete file" cannot erase the queue.

Do not put cookie file contents in these JSON files. The settings document may store the cookie file path. T-035 decides the path rules. This task may store a path string and must not log it at info level if you add logging. Prefer no logging of the path.

Jobs in `DOWNLOADING`, `RESOLVING`, `QUEUED`, or `POSTPROCESSING` at process start are not still running. On load, mark them `FAILED` with `JobError.code` for an interrupted engine and `retryable = true`, message that the app closed before the download finished. Do not automatically restart them. `PENDING` and `SCHEDULED` stay as they were. This matches the user flow: reopening restores the queue and marks interrupted work for retry.

`DownloadEngine` in the desktop graph is still the in-memory engine until T-033, but its jobs must be seeded from and written back to this store. Implement that as a desktop `PersistingEngine` that delegates to the fake or, cleaner, a single `JsonDownloadEngine` in `apps/desktop` that copies the fake's behavior and adds the file. Do not modify the shared fake to know about files.

Concurrency: the UI and a future worker share this store. Serialize writes. A revision on each job, already on `DownloadJob`, increments on every change so a stale write can be detected in tests.

Clear-completed-after: if the setting is greater than zero, drop completed and failed rows older than that age when the app loads and when it saves. Cancelled rows follow the same timer. Do not delete artifact files as part of that clear.

## Work

`com.anydownlod.desktop.store`:

- Load and save jobs, subscriptions, and settings.
- Atomic replace.
- Migration story for a missing directory (create it) and an empty file (treat as empty state). A corrupt file is renamed aside with a timestamp suffix and the app starts empty, with a Settings or shell banner that the previous state could not be read. Do not crash the window.
- Path checks from T-026 still apply to destination folders stored on jobs.

Tests are JVM tests in `:apps:desktop` (add a test source set if the module does not have one). Use a temporary directory. Cover: round-trip a job in every state, interrupted active job becomes failed-retryable, pending survives, atomic write leaves the previous file intact if the writer throws before rename, corrupt file does not crash the loader, cookie JSON has no `value` or `contents` field.

Desktop `main` constructs the store, passes it into `App`, and on window close requests a final save. Closing must return quickly. Do not wait on a download in this task.

## Verification

`./gradlew :apps:desktop:jvmTest` or the test task you added. Then run the app, create a pending job and change the download folder, quit, relaunch, and confirm both survived. Record the state directory path pattern in Evidence, not a machine-specific home path that includes a private username if you can avoid it. `Application Support/AnyDownload` is enough.

## Out of scope

Spawning yt-dlp, deleting media files from disk (the history action can mark the artifact removed; unlinking the file is T-033 once paths are real), subscription polling timers.

## Acceptance criteria

- [x] Desktop relaunch restores jobs, subscriptions, settings, and presets.
- [x] Active jobs found on disk at startup become retryable failures. Pending and scheduled jobs do not.
- [x] Writes are atomic. A corrupt file is preserved aside and the app opens on empty state with a visible explanation.
- [x] State files live outside the download root and do not contain cookie contents.
- [x] JVM tests cover the cases in Work. The other hosts still use the in-memory fake.

## Evidence / notes

**2026-09-21 — implemented and verified.**

Commands run:

- `./gradlew :apps:desktop:test` — BUILD SUCCESSFUL: `DesktopStoreTest` 12 tests and `DesktopAppRelaunchTest` 2 tests, 0 failures.
- `./gradlew :shared:core:jvmTest :shared:ui:cleanJvmTest :shared:ui:jvmTest :apps:desktop:test :shared:ui:compileKotlinJvm :apps:desktop:compileKotlin :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL, **121 tests total, 0 failures** (core 47, shared/ui 60, apps/desktop 14).
- `./gradlew :apps:desktop:run` — the window opened with the store-backed graph, the log had no exception/error lines, and the app closed cleanly (the final save is wired to `onCloseRequest`).

The relaunch flow was verified with `DesktopAppRelaunchTest` instead of OS clicks because the host still lacks macOS Accessibility permission: submit a manual-start job (PENDING), change the download folder, add a subscription, and add a preset, then call the host save, reopen from the same directory, and assert all four survived. A second test leaves a QUEUED job on disk without a clean save and asserts the reopened app has it FAILED with `retryable = true`. `DesktopStoreTest` covers the rest: every state and a fully populated option set round-trip through the DTOs, pending/scheduled/terminal states survive load while active states are interrupted, atomic replace keeps the old file when the writer throws before the rename, a corrupt file is moved to `jobs.json.corrupt-<timestamp>` and produces a load warning, an empty file loads as empty state without a warning, a stale revision cannot regress the file, clear-completed drops old terminal rows on save and load, an unsafe stored destination folder is cleared, settings/subscriptions/presets round-trip, a blank download root gets the platform default, and the persisting engine writes after submit/cancel/remove.

State layout evidence (pattern, not a private path): `Application Support/AnyDownload/{jobs.json, subscriptions.json, settings.json}`. A real launch created all three; `settings.json` contained the reviewed template/concurrency defaults, the default `Downloads/AnyDownload` root, and no cookie value or contents field. State JSON lives outside the download root and jobs never persist the custom yt-dlp JSON placeholder.

What landed:

- `com.anydownlod.desktop.store`: `DesktopStore` (atomic temp-file rename, missing-dir creation, empty-file handling, corrupt-file quarantine with a timestamp and load warning, revision-aware writes, clear-completed filtering, destination sanitization, cookie path only), the `@Serializable` DTOs and mapping that keep the custom-JSON placeholder out of the files, `PersistingDownloadEngine`/`PersistingSubscriptionRepository`/`PersistingSettingsRepository` delegates over the shared in-memory fakes, and `defaultStateDirectory`/`defaultDownloadRootPath`.
- `AppGraph.startupWarning` with a null default; `AppShell` shows the warning banner when a load had to quarantine a file.
- Desktop `main` now opens the store in `Application Support/AnyDownload`, seeds the fakes from disk, persists every mutation and a final save on window close, and passes the store-backed graph into `App`; `InMemoryAppGraph.seeded()` remains only for tests and non-desktop hosts.

Interrupted jobs use `JobErrorCode.ENGINE_UNAVAILABLE` with `retryable = true` and the message that the app closed before the download finished; no other code category was added for D1.
