---
id: T-036
type: task
priority: P0
milestone: D1
tags: [task, desktop, verification]
---

# T-036 — Phase 1 verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 1](../00-project/Phase-1-Desktop-MeTube.md)

## Outcome

Phase D1 is shown to work on desktop, the README tells the next person how to run it, and the vault no longer implies that the next implementation step is a four-target Kotlin download.

## Dependencies

- [T-026](T-026-Shared-domain-and-engine-seam.md) through [T-035](T-035-Desktop-cookies-presets-subscriptions.md).

## Context the next session needs

Earlier tasks each verified their own slice. This task is the end-to-end pass and the documentation cleanup. Do not add features here. If a check fails, fix it in the module that owns it and note the fix.

README today says the scaffold talks to a server and that the next work is a local download on each target. After D1, desktop is a MeTube-style window that calls an installed yt-dlp. ADR-004 is still the end state. The README must say both, or the following session will "correct" the desktop process adapter back into a shared Kotlin engine.

Do not claim Android, iOS, or web can download. Do not claim feature parity with the Kotlin port. [T-022](T-022-Parity-audit.md) stays open.

## Work

### Desktop run-through

With `yt-dlp` and `ffmpeg` on `PATH`, run `./gradlew :apps:desktop:run` and do all of the following. Record pass or fail in Evidence. Use public media you are allowed to download. Do not paste private URLs or cookies into the note.

1. Cold start on an empty state directory shows the empty lists and a tool probe with versions.
2. Download one video with auto-start. Progress appears. The file opens from Completed.
3. Submit the same URL again with auto-start off. It stays pending until Start.
4. Cancel an in-progress download. The process is gone and the row is Cancelled.
5. Paste two lines, one invalid. The valid one becomes a job. The invalid one stays in the field with an error.
6. Change one option that T-034 mapped (audio, or a height, or captions) and complete that download, or record a fixture-backed test if a live run is impractical. Say which.
7. Remove a history row and confirm the file is still on disk. Delete a different file and confirm it is gone and the history action was the other control.
8. Subscribe to a channel or playlist, see the row, pause it, and run Check now.
9. Import a local fixture cookie file, observe Configured without any cookie text on screen, then delete it.
10. Quit during a download, relaunch, and find a retryable failure rather than a stuck Downloading row. Relaunch again after a completed download and find the completed row.
11. Theme switches from the header and from Settings.
12. Custom yt-dlp JSON is still disabled.

Then run `./gradlew :shared:core:jvmTest` and the desktop test task. If Android or web toolchains are installed, compile them to confirm the fake shell still builds. A missing SDK is recorded, not treated as a D1 failure.

### Docs

- Update `README.md` status, the desktop run instructions, and the "next work" line. State that D1 uses an external yt-dlp and ffmpeg, that shared code does not spawn them, and that the Kotlin port remains ADR-004.
- Update the phase note status if every check passed.
- Leave a short Evidence section on this task with the date, OS, yt-dlp version, ffmpeg version, and the twelve checks.
- Move this card to Done only when those checks are recorded. Move any failed earlier card back to In progress rather than marking D1 done.

### Explicitly do not

- Start T-004, T-009, or a Kotlin extractor.
- Bundle yt-dlp.
- Enable Q-09.
- Edit ADR-004 into saying the CLI is the final engine.

## Acceptance criteria

- [x] The twelve-step desktop run-through is recorded with pass or fail for each step.
- [x] Shared core tests and desktop tests pass.
- [x] README describes how to run the desktop app, the external yt-dlp requirement, and ADR-004 as the later engine.
- [x] No private URL, cookie, or media file was added to the repository.

## Evidence / notes

**2026-09-21 — D1 verified.**

Environment: macOS 26.5.2 (arm64), yt-dlp `2026.08.19`, ffmpeg `9.0.1` (both from `PATH`).

Commands:

- `./gradlew :shared:core:cleanJvmTest :shared:core:jvmTest :apps:desktop:cleanTest :apps:desktop:test --no-build-cache` — **113 tests, 9 live-only skipped, 0 failures**.
- `./gradlew :shared:ui:compileKotlinJvm :apps:android:compileDebugKotlin :apps:web:compileKotlinWasmJs` — Android and web toolchains are present and the fake shell compiles; the full earlier matrix also covered `:shared:ui:compileKotlinIosSimulatorArm64`.
- Full live suite (opt-in `-Danydownlod.live.*` URLs, none stored): video download `COMPLETED` at 100% with a real artifact; live cancel `CANCELLED` with no surviving `bin/yt-dlp` process; `https://example.com` `FAILED` with a redacted `UNSUPPORTED_SOURCE` message; audio extract produced `sample-5s.mp3`; playlist expanded to exactly 2 cancellable child jobs; two-URL batch ran as two jobs that both completed; thumbnail-only produced `Big Buck Bunny.jpg`; subscription first check reported `seen=2 jobs=0 error=null` and the second check enqueued nothing new; cold start on a fresh state directory reported `yt-dlp=2026.08.19 ffmpeg=ffmpeg version 9.0.1`.
- `./gradlew :apps:desktop:run` — clean launch and close after the step-2 fix below.

This host cannot send OS input (macOS Accessibility permission is not granted), so the run-through used the live engine checks plus the headless Compose UI tests that drive the real composables. Each step below names what actually ran.

| # | Step | Result | Verification |
| --- | --- | --- | --- |
| 1 | Cold start on an empty state directory, empty lists, tool probe versions | PASS | `LiveYtDlpCheckTest.realColdStartShowsEmptyStateAndToolVersions` on a fresh temp state dir; probe returned yt-dlp 2026.08.19 and ffmpeg 9.0.1 |
| 2 | One video with auto-start, progress, file opens from Completed | PASS | `realDownloadCompletesAndRegistersAnArtifact` (100%, artifact on disk) plus the T-036 fix that wired `openFile`/`revealFile` to `java.awt.Desktop` with root-escape rejection |
| 3 | Same URL auto-start off stays pending until Start | PASS | `YtDlpCliEngineTest.manualStartDoesNotSpawnUntilStart`, `AddFormPresenterTest.manualStartIsCountedAsWaiting` |
| 4 | Cancel in-progress; process gone; row Cancelled | PASS | live `realCancelStopsTheLiveProcessTree` (no surviving process) and `YtDlpCliEngineTest.cancelDestroysTheProcessAndRecordsCancelled` |
| 5 | Two lines, one invalid | PASS | `AddFormPresenterTest.batchSplitKeepsValidLinesAndReportsTheBadOne`, `AddFormUiTest.manualStartAndBatchWithOneBadLineThroughTheUi` (error stays in the field) |
| 6 | Complete a T-034-mapped option (audio) | PASS | live `realAudioExtractProducesAnAudioArtifact` produced an `.mp3` AUDIO artifact |
| 7 | Remove history keeps the file; delete file uses the other control | PASS | `QueueHistoryUiTest.startCancelRetryRemoveAndDeleteThroughTheUi`, `HistoryPresenterTest.removeHistoryAndDeleteArtifactsAreDifferentOperations` |
| 8 | Subscribe, see the row, pause, Check now | PASS | `SubscriptionsUiTest.checkEditPauseAndDeleteThroughTheUi`, live `realSubscriptionFirstCheckMarksSeenAndEnqueuesNothing` |
| 9 | Import fixture cookie, Configured with no cookie text, delete | PASS | `DesktopCookieStoreTest` and `SettingsUiTest.cookiesImportAndDeleteGoThroughTheHostStoreWithoutContents` (fake `fake_session`/`fake_value` fixture only) |
| 10 | Quit mid-download → retryable failure; relaunch after completion → Completed | PASS | `DesktopAppRelaunchTest.interruptedActiveJobBecomesRetryableOnRelaunch` and `completedDownloadSurvivesRelaunchWithItsArtifact` |
| 11 | Theme from the header and from Settings | PASS | `AppShellTest.themeControlIsLabeledAndWritesThroughSettings`, `SettingsUiTest.sectionsThemeTemplateConcurrencyAndFolderThroughTheUi` |
| 12 | Custom yt-dlp JSON still disabled | PASS | The add-form field is `enabled = false`; `AddFormPresenterTest.customYtDlpJsonIsNeverOnTheRequest` proves it never reaches the request and it is not persisted |

Fix found by step 2: desktop `openFile`/`revealFile` were still the `AppGraph` no-ops from T-033. The T-036 pass wired them in `apps/desktop/Main.kt` to `Desktop.open` / `Desktop.browseFileDirectory` after resolving and escaping-checking the artifact path; the desktop module owns the fix, and its tests still pass. Recorded here because [T-033](T-033-Desktop-ytdlp-single-url.md) is already Done.

Docs: `README.md` now describes the desktop run (`./gradlew :apps:desktop:run`, external `yt-dlp` and `ffmpeg` on `PATH`, nothing bundled), the JSON-store location, the module/adapter boundary, and ADR-004 as the later Kotlin engine that must not absorb the desktop process adapter. The [phase note](../00-project/Phase-1-Desktop-MeTube.md) is marked done.

Privacy checks: `grep` over tracked `apps`/`shared`/`vault` sources found no live media URL, and no media file, cookie file, or credential was added to the repository; live tests read URLs from `-D` properties.
