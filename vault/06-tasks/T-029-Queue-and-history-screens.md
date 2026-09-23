---
id: T-029
type: task
priority: P0
milestone: D1
tags: [task, ui, queue]
---

# T-029 — Queue and history screens

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md) · [User flows](../01-product/User-flows.md)

## Outcome

**Downloading** and **Completed** are real lists bound to `DownloadEngine`. A person can start, cancel, retry, open the source, remove a history row, and delete a file record. The three destructive meanings stay distinct. The fake is still the engine.

## Dependencies

- [T-027](T-027-Desktop-app-shell.md). The lists should show jobs created by [T-028](T-028-Add-form.md) if that task has landed. If it has not, seed the fake so this screen can still be built.

## Context the next session needs

MeTube's first list is the active work: pending, preparing, downloading, and anything waiting. The second list is finished work, and it includes failures, not only successes. D1 uses the same split:

- **Downloading** shows jobs whose `JobState` is not terminal: `RESOLVING`, `PENDING`, `SCHEDULED`, `QUEUED`, `DOWNLOADING`, `POSTPROCESSING`.
- **Completed** shows `COMPLETED`, `FAILED`, and `CANCELLED`.

`UNKNOWN` is shown on Completed with the raw label, so a future state does not disappear.

User-flow rules that are easy to get wrong:

- Cancel removes the job from active work. It does not delete an artifact that already exists.
- Remove history drops the row. It does not delete the file.
- Delete file deletes the artifact. It asks a separate confirm. If the file is already gone, say so. The history row can remain.
- Closing the app is not a button on this screen. T-033 defines what happens to a live process. This task only needs a visible state for an interrupted job if the fake exposes one. Do not add pause/resume. MeTube's reviewed baseline has no generic pause/resume.

Progress: when `JobProgress.percent` is null, show an indeterminate indicator and the phase name. Do not display `0%`. When speed or ETA is null, omit them. Bytes can render as a known amount over an unknown total.

`SCHEDULED` rows show the due time from the job if the model has one. Do not tick a fake countdown that implies calendar scheduling. Copy can say the item is waiting until the source is available.

## Work

Implement `com.anydownlod.ui.queue` and `com.anydownlod.ui.history`. Observe the engine's job flow. Rows update when the fake changes state. Selecting a tab does not reset progress.

Downloading row:

- Checkbox, title (URL until a title exists), source host, state label, phase.
- Determinate or indeterminate progress, speed, ETA, and byte counts when present.
- **Start** on `PENDING` and `SCHEDULED` only. Calls `DownloadEngine.start`.
- **Cancel** on every non-terminal row. Confirm only when the job is `DOWNLOADING` or `POSTPROCESSING`, because that throws away in-progress work. Pending cancel can be immediate. Confirm copy says this stops the download and does not delete a finished file.
- **Open source** uses the desktop host to open the URL in the browser. Add a small callback on the app graph (`openUrl`) with a no-op default so Android/iOS/web still compile. Desktop implements it with `java.awt.Desktop` if supported. This is not `ProcessBuilder` around yt-dlp. If that API is uncomfortable in common code, define the callback in `shared/ui` as a function the desktop `main` passes in.

Completed row:

- Title, file name and size from artifacts, state, and the redacted error message for failures.
- **Open file** and **Reveal in folder** call host callbacks (`openFile`, `revealFile`) that no-op until the desktop store has real paths (T-032/T-033). Disabled when the job has no artifact.
- **Retry** on `FAILED` and `CANCELLED`. Calls `retry`. Hidden on `COMPLETED`.
- **Copy error** copies `JobError.message` only.
- **Remove** confirms: "Remove this entry from history? The file stays on disk."
- **Delete file** confirms with the file name: "Delete this file from the download folder?" Report partial failure if one of several artifacts fails. The fake can succeed and clear artifact paths.

Bulk bar on each list, disabled when nothing is selected:

- Downloading: Start selected, Cancel selected.
- Completed: Retry failed among the selection, Remove selected. Delete-file stays per row so a bulk delete cannot be accidental. A "select all" checkbox uses the master/slave pattern MeTube has: off, on, and indeterminate when some rows are selected.

Empty states from T-027 remain when the filtered list is empty.

Screen reader: progress text updates should not spam. Announce state transitions (started, failed, completed) once, not every percent tick.

Presenter tests: partition of jobs into the two lists, null percent rendering model, cancel versus remove versus delete as different calls, bulk start skipping rows that are already downloading, retry only on failed and cancelled.

## Verification

Run the desktop app with the fake seed (every state). Start a pending row, cancel a downloading row, retry a failed row, remove a history row, delete a file, and use select-all plus one bulk action. Run the presenter tests.

## Out of scope

Parsing yt-dlp output, real filesystem deletion, persistence, subscriptions, settings.

## Acceptance criteria

- [x] Downloading and Completed partition jobs by the states listed above. Failures stay on Completed.
- [x] Rows show indeterminate progress when percent is null and omit unknown speed and ETA.
- [x] Start, cancel, retry, and open source call the engine or the host callback. Cancel of an active download asks first. Cancel copy does not say the file will be deleted.
- [x] Remove history and delete file have separate confirms and call different operations.
- [x] Bulk start, bulk cancel, and bulk remove operate on the selection. Select-all supports the mixed state.
- [x] Presenter tests and a desktop click-through are recorded in Evidence.

## Evidence / notes

**2026-09-21 — implemented and verified.**

Commands run:

- `./gradlew :shared:core:jvmTest :shared:ui:cleanJvmTest :shared:ui:jvmTest :shared:ui:compileKotlinJvm :apps:desktop:compileKotlin :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs :shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL. Tests: core 47 and shared/ui 39, all 0 failures. New suites: `QueuePresenterTest` 6, `HistoryPresenterTest` 4, `QueueHistoryUiTest` 3, `JobPresentationTest` 4, plus 3 new `InMemoryDownloadEngineTest` cases.
- `./gradlew :apps:desktop:run` with `InMemoryAppGraph.seeded()` — the window opened showing the seeded rows, the log had no exception/error lines, and the window closed cleanly.

Click-through: macOS Accessibility/Screen Recording permission is still not granted, so the clicks ran headlessly against the real composables in `QueueHistoryUiTest`. Performed: Start on the pending row (becomes queued; only PENDING/SCHEDULED rows show Start); Cancel on the downloading row (a confirm appears with the exact "This stops the download. It does not delete a finished file." copy, then the row becomes CANCELLED); Retry on the failed row (moves back to the queue); Delete file on the completed row (separate confirm with the file name, artifact marked removed, row stays with "(file removed)"); Remove on the completed row (separate confirm "The file stays on disk.", row dropped); Open source calls the host `openUrl` callback with the exact URL; bulk select + Start selected and bulk Remove selected after one confirm; select-all from a partial selection checks every row. Presenter tests add the partition rules, null-percent rows keeping speed/ETA null, `UNKNOWN` staying on Completed with its raw label, bulk start skipping rows already working, and remove-history versus delete-file staying different engine calls.

What landed:

- Core seam: `DownloadEngine.removeHistory` (never touches files) and `deleteArtifacts` returning `ArtifactDeletionResult`; `Artifact.removed`; `AppGraph.openUrl`, `openFile`, and `revealFile` callbacks with no-op defaults. The in-memory engine drops rows, marks files removed, and reports partial-failure results.
- `com.anydownlod.ui.queue`: `QueueRow`/`QueuePresenter.rows` (non-terminal partition), `QueuePresenter` actions, `QueueScreen` with tri-state select-all, determinate/indeterminate progress, phase, bytes/speed/ETA only when present, static UTC due time for `SCHEDULED`, start/cancel/open-source actions, and the active-cancel confirm.
- `com.anydownlod.ui.history`: `HistoryRow`/`HistoryPresenter.rows` (terminal + `UNKNOWN`), `HistoryPresenter` actions, `HistoryScreen` with retry/copy-error/open/reveal/delete/remove actions, per-action confirms, partial-delete reporting, and bulk retry/remove.
- `com.anydownlod.ui.shell.JobPresentation` formats state labels, decimal bytes/speed, compact ETA, and static UTC due times without a platform date library. State labels are `liveRegion` text so transitions announce without percent spam.
- Desktop `main` passes a graph whose `openUrl` opens the browser through `java.awt.Desktop`; open/reveal stay no-ops until T-032/T-033 have real paths.
