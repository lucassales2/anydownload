---
id: T-120
type: task
priority: P0
milestone: D9
tags: [task, metro, verification]
---

# T-120 — Metro verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) · [Loop prompt](../00-project/Metro-Loop-prompt.md)

## Outcome

The Metro migration is verified end to end: the ten-command Gradle suite passes, the three invariants hold, and [ADR-013](../03-decisions/ADR-013-Metro-graphs-and-viewmodels.md) is accepted.

## Dependencies

- [T-122](T-122-Queue-view-model.md).

## Work

- Run:

  ```
  ./gradlew :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test :apps:android-engine-tests:test :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin :apps:web:wasmJsBrowserDistribution :tools:port-manifest:check
  ```

- Confirm `shared/core` commonMain has no `ProcessBuilder` (`rg -n "ProcessBuilder" shared/core/src/commonMain` is empty).
- Confirm `App()` still takes the core `AppGraph`.
- Confirm `QueueScreen` does not call `engine.start`, `engine.cancel`, or read `engine.jobs` (`rg -n "engine\.(start|cancel|jobs)" shared/ui/src/commonMain/kotlin/com/anydownlod/ui/queue/QueueScreen.kt` is empty).
- Accept ADR-013 with today's date and the commands in Evidence, and update the Decision log row. Do not commit.
- If a check fails, fix it before leaving the task. If a check cannot pass, move the card to Blocked with the failure and the next action.

## Acceptance criteria

- [x] The ten-command suite passes; the commands, counts, and any rerun notes are in Evidence.
- [x] `shared/core` commonMain has no `ProcessBuilder`.
- [x] `App()` still takes `AppGraph`.
- [x] `QueueScreen` does not call `engine.start`, `engine.cancel`, or read `engine.jobs`.
- [x] ADR-013 is accepted and the Decision log names D9 verified.
- [x] No commit was made.

## Evidence / notes

2026-09-29, macOS 26.5.2 (Darwin 25.5.0).

- **First full combined run:** `./gradlew --console=plain :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test :apps:android-engine-tests:test :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin :apps:web:wasmJsBrowserDistribution :tools:port-manifest:check` reached `:apps:desktop:test` and failed there with 2 failures: `DesktopAppRelaunchTest.interruptedActiveJobBecomesRetryableOnRelaunch` (empty job list on reopen) and `DesktopFfmpegToolkitTest.embedTagsWritesLyricsForM4a` (unreadable probe output). Both are the documented load-sensitive desktop behavior from T-112, not product regressions.
- **Desktop alone (the T-112 procedure):** `./gradlew --console=plain :apps:desktop:cleanTest :apps:desktop:test` → BUILD SUCCESSFUL in 15s; 132 tests, 0 failures, 15 skipped (opt-in live checks).
- **Remaining nine tasks, clean run:** `./gradlew --console=plain :shared:core:jvmTest :shared:ui:jvmTest :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test :apps:android-engine-tests:test :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin :apps:web:wasmJsBrowserDistribution :tools:port-manifest:check` → BUILD SUCCESSFUL in 4m 49s. Test XML totals: core JVM 527, UI JVM 103, core iOS 484, UI iOS 8, Android engine 26 — all 0 failures. The Android debug APK was written to `apps/android/build/outputs/apk/debug/android-debug.apk`; the web production bundle to `apps/web/build/dist/wasmJs/productionExecutable/index.html`.
- **Invariants:** `rg -n "ProcessBuilder" shared/core/src/commonMain` → no matches; `App.kt` still declares `fun App(graph: AppGraph = remember { InMemoryAppGraph() }, viewModelFactory: MetroViewModelFactory? = null)`; `rg -n "engine\.(start|cancel|jobs)" shared/ui/src/commonMain/kotlin/com/anydownlod/ui/queue/QueueScreen.kt` → no matches.
- **ADR-013:** status changed to `accepted`; the Validation section records the ten-command result, the counts, and the three invariants. The Decision log row is now “Accepted; D9 verified 2026-09-29” and the log paragraph says T-113–T-122 are Done.
- No commit was made.
