---
id: T-109
type: task
priority: P1
milestone: D8
tags: [task, ui, playlists]
---

# T-109 — Copy or export batch and playlist URLs

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md)

## Outcome

The user can copy the source URLs of one batch or one playlist expansion, and only after an explicit action.

## Dependencies

- [T-107](T-107-Playlist-expansion.md).

## Context the next session needs

`AddFormPresenter` already splits newline input, keeps invalid lines in the field, and submits the valid ones with a shared batch key. Queue and history screens already list jobs. There is no export of those URLs.

## Work

- On the queue and on history, an explicit Copy action builds a newline list of the source URLs for the selected rows, or for every child of one `parentBatchId` when the user asks for the whole batch.
- The list includes failed children and omits nothing the user selected. It does not include cookie headers, media URLs, or signed query strings if the stored source is a watch URL that already has them: copy the URL the user submitted.
- The button is disabled when nothing is selected. A toast or inline confirmation says how many URLs were copied. Do not copy on row render.
- Add a presenter test. A UI test clicks Copy and reads the clipboard text the presenter captured (the test double is enough if the platform clipboard is not available in JVM tests).

## Acceptance criteria

- [x] Copy is explicit, disabled when the selection is empty, and returns one URL per selected job.
- [x] A playlist or batch copy includes failed children and excludes every job outside that parent.
- [x] The copied text has no cookie and no signed media URL.
- [x] Presenter or UI test covers the empty selection and the parent selection.

## Evidence / notes

2026-09-29.

- New `shared/ui/.../export/JobSourceUrls.kt`: `forSelected` returns one `request.sourceUrl` per selected row in engine row order (failed rows included); `forBatches` returns every row whose `parentBatchId` matches a selected row's batch (failed children included, unrelated rows excluded); `text` joins with newlines. It reads only the URL the user submitted — never a media URL, header, or cookie value.
- `QueuePresenter` and `HistoryPresenter` gained `selectedUrls(ids)` and `batchUrls(ids)` backed by that object.
- `QueueScreen` and `HistoryScreen` gained explicit `Copy URLs` and `Copy batch` buttons in the selection bar (tags `queue-copy-selected`/`queue-copy-batch`, `history-copy-selected`/`history-copy-batch`). Copy is disabled with an empty selection; Copy batch is disabled when no selected row has a batch. A click writes the newline text and shows a `MessageStrip` confirmation (`Copied %d URL(s)`). Nothing copies on row render. The screens accept an optional `onCopyUrls` seam; the default writes the platform clipboard, and `AppShell` passes it through so the UI test can capture the text without a JVM clipboard.
- New strings `copy_urls`, `copy_batch`, `copied_urls`.

Verification run:

- `./gradlew :shared:ui:jvmTest` — BUILD SUCCESSFUL; ui jvm 96 tests, 0 failures. New: `QueuePresenterTest` 9 (selected URLs include a failed row and an empty selection; batch URLs include every child and exclude another job; no cookie/signature/googlevideo text), `HistoryPresenterTest` 6 (same rules), `QueueHistoryUiTest` 4 (click-through: both buttons disabled with an empty selection, one selected URL, then the batch copy includes the failed child and excludes the unrelated row).
- Cross-target: `CHROME_BIN=... ./gradlew :shared:ui:jvmTest :shared:ui:iosSimulatorArm64Test :shared:ui:compileKotlinWasmJs :apps:web:wasmJsBrowserTest :apps:desktop:compileKotlin :apps:android:compileDebugKotlin` — BUILD SUCCESSFUL; ui iOS 8, web wasm 6, all 0 failures. `:apps:desktop:cleanTest :apps:desktop:test` alone — 131 tests, 0 failures.
