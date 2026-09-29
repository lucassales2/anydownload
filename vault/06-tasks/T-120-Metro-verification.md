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

- [ ] The ten-command suite passes; the commands, counts, and any rerun notes are in Evidence.
- [ ] `shared/core` commonMain has no `ProcessBuilder`.
- [ ] `App()` still takes `AppGraph`.
- [ ] `QueueScreen` does not call `engine.start`, `engine.cancel`, or read `engine.jobs`.
- [ ] ADR-013 is accepted and the Decision log names D9 verified.
- [ ] No commit was made.

## Evidence / notes

Not started.
