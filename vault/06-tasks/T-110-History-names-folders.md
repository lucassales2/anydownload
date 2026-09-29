---
id: T-110
type: task
priority: P1
milestone: D8
tags: [task, storage, history]
---

# T-110 — Names, folders, and history actions

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md)

## Outcome

Finished files stay inside the download root under a safe name. Remove history, delete file, and cancel stay distinct, including on web where the page cannot delete a browser download.

## Dependencies

- [T-106](T-106-Four-host-m1.md).

## Context the next session needs

`ArtifactName` already sanitizes a file name. `DownloadEngine.removeHistory` drops the row and does not delete files. `deleteArtifacts` deletes or marks files and keeps the row. `cancel` does not delete a finished file. `RelativePathValidator` rejects traversal. Desktop clear-completed already exists and moved into the shared codec in T-102.

## Work

- Names: the published relative path is the sanitized title plus extension, or the existing name when the extractor has none. A collision appends ` (2)`, then ` (3)`, and does not overwrite unless `OverwriteMode` already says so. Reject `..`, an absolute path, and a name that is only dots. Unicode letters in the title survive.
- Disk: when the file store reports it cannot write, the job fails `DISK_EXHAUSTED`, the temp is discarded, and the message does not include the absolute path.
- History UI: the remove control and the delete-file control use different labels and different confirmations. Remove does not call delete. Delete does not call remove. Cancel is only on non-terminal rows.
- Web: `deleteArtifacts` reports the browser-download limitation in the result failures list and leaves the history row. Do not pretend the file is gone.
- Cover traversal, a collision, a Unicode title, disk failure, and the three actions with tests.

## Acceptance criteria

- [x] A path that escapes the root is rejected, and a colliding name gets a numeric suffix.
- [x] Disk failure is `DISK_EXHAUSTED` with no absolute path in the message.
- [x] Remove, delete, and cancel are separate in the UI and in tests.
- [x] Web delete reports that the page cannot remove a browser download, and the row remains.

## Evidence / notes

2026-09-29.

- **Names and collisions:** `HttpDownloadEngine.artifactPath` now routes an extractor-derived name through `uniqueArtifactPath`: `FORCE` keeps the desired name, every other mode checks the file store and appends ` (2)`, ` (3)`, ... until the path is free, so a collision never overwrites. A requested/template path (Spotify) stays exact so `handleExistingArtifact` still applies skip/metadata. `ArtifactName` already sanitizes `/`, `\`, `:`, control characters, and leading dots and keeps Unicode letters; the `OverwriteMode` KDoc was updated to describe the suffix behavior.
- **Disk:** the engine's new `writeChunk` wraps every temp write (`streamToTemp`, `streamChunkedToTemp`, and the HLS/DASH fragment `onChunk`). A host write failure fails the job `DISK_EXHAUSTED` with the fixed message "The download folder could not be written." and the temp is discarded; no absolute path is included. `createTempFile` failures already typed `DISK_EXHAUSTED`.
- **History actions:** `removeHistory` drops the row and never touches the file; `deleteArtifacts` deletes/marks files and keeps the row; `cancel` never deletes a finished file. The history UI already uses different labels and confirmations for remove and delete, and cancel exists only on non-terminal queue rows.
- **Web:** `WebExtensionEngine.deleteArtifacts` no longer marks artifacts removed. It returns `deletedCount = 0` with the pending file names in `failures` and leaves the row and its artifacts unchanged, so the UI reports the browser-download limitation instead of pretending the file is gone.

Tests:

- `HttpDownloadEngineTest`: `aCollisionGetsANumericSuffixAndDoesNotOverwrite` (a seeded `tiny.bin` and `tiny (2).bin` produce `tiny (3).bin` and leave both files), `aTraversalRequestedPathFailsTypedWithoutWriting` (`../escape.mp4` fails `INVALID_URL_OPTIONS`, no publish), `aHostWriteFailureIsDiskExhaustedAndDiscardsTheTemp` (`DISK_EXHAUSTED`, no `/` in the message, temp discarded, no publish), `removeHistoryDropsTheRowButKeepsTheFile`.
- `ArtifactNameTest.keepsUnicodeLettersInTitles` (`Café 日本語.mp4`, `naïve - résumé.mp3`, `música/Canción.mp4`).
- `WebExtensionEngineTest.deleteArtifactsReportsTheBrowserLimitationAndKeepsTheRow`.
- The existing `QueueHistoryUiTest.startCancelRetryRemoveAndDeleteThroughTheUi` covers the distinct UI labels/confirmations; the existing engine tests cover cancel-versus-complete and delete-keeps-row.

Verification run:

- `./gradlew :shared:core:jvmTest --tests "...HttpDownloadEngineTest" --tests "...WebExtensionEngineTest" --tests "...ArtifactNameTest"` — BUILD SUCCESSFUL; 35 + 18 + 7 tests, 0 failures.
- Cross-target: `CHROME_BIN=... ./gradlew :shared:core:jvmTest :shared:core:wasmJsBrowserTest :shared:core:iosSimulatorArm64Test :shared:ui:jvmTest :apps:android-engine-tests:test :apps:web:wasmJsBrowserTest :apps:android:compileDebugKotlin` — BUILD SUCCESSFUL; core jvm 519, core wasm 465, core iOS 476, ui jvm 96, android-engine-tests 26, web wasm 6, all 0 failures. `:apps:desktop:cleanTest :apps:desktop:test` alone — 131 tests, 0 failures.
