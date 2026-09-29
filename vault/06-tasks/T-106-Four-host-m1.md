---
id: T-106
type: task
priority: P0
milestone: D8
tags: [task, platforms, gate]
---

# T-106 — Gate: the same journey on four hosts

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 8](../00-project/Phase-8-On-device-core.md) · [ADR-012](../03-decisions/ADR-012-On-device-core-phase.md)

## Outcome

Android, iOS, and web run the T-105 journey against the shared engine and their own state store. M1's three open cards close.

## Dependencies

- [T-105](T-105-Gate-desktop-restart.md).

## Context the next session needs

D4–D7 used JVM-equivalent Android tests when no emulator exists, an iOS simulator fixture, and wasm/node fixtures for web. A missing emulator is a recorded limit, not a skip of the shared path. Browser end-to-end UI is the same gap D6 and D7 recorded; the wasm fixture plus the extension download path is the web evidence.

## Work

- Android: JVM-equivalent test of submit, failure, and reload through the Android state file. Run `assembleDebug` if no emulator is installed. Do not send the fixture URL to Chaquopy.
- iOS: simulator test through the sandbox state file and file store. No live network.
- Web: wasm or node test that the page stores the job document, reloads it, and the media GET still goes through the extension rather than the page origin. Record that a browser click-through was not run if that is still true.
- When the three hosts pass, check the remaining acceptance boxes on T-008, T-009, and T-010, write the commands in their Evidence, and move those three cards to Done. T-008's store-signing line stays out of scope; CI already builds the clients in `.github/workflows/client-build.yml`, so point at that file instead of adding a new workflow.
- Do not start playlist work in this task.

## Acceptance criteria

- [x] Android, iOS, and web each show one completed fixture file, one failed row, and a reload that restores both.
- [x] Evidence names the host limit (no emulator, simulator-only, no browser click-through) where that is the case.
- [x] T-008, T-009, and T-010 are Done, with their boxes checked only for criteria this phase actually met.

## Evidence / notes

2026-09-29. New host gate tests, all offline with fixture transfers:

- **Android** — `apps/android/src/test/kotlin/com/anydownlod/android/engine/AndroidM1GateTest.kt` (run through `:apps:android-engine-tests:test`): shared `HttpDownloadEngine` over `JavaNetFileStore`, persisted through `AndroidJobDocumentStorage`/`PersistingDownloadEngine`; completes a 32 KiB fixture to `clip.mp4`, types a 503 failure with no secret in the message, reloads both rows into a new engine, and retry of the completed row adds no file. No emulator is installed, so this is the JVM-equivalent run; `:apps:android:assembleDebug` proves the app builds.
- **iOS** — `shared/ui/src/iosTest/kotlin/com/anydownlod/ui/persist/IosM1GateTest.kt` (run through `:shared:ui:iosSimulatorArm64Test`): the same journey over `IosFileStore` + `IosJobDocumentStorage` on the simulator. Simulator-only; no device and no live network.
- **Web** — `apps/web/src/wasmJsTest/kotlin/com/anydownlod/web/WebM1GateTest.kt` (run through `:apps:web:wasmJsBrowserTest`): the page-level `WebExtensionEngine` + `WebJobDocumentStorage` + `PersistingDownloadEngine`; the fixture media request goes through the fake `WebExtensionBridge` (the extension path), then the stored document reloads both rows and retry does not download. No real browser click-through was run (the recorded D6/D7 gap); the fixture plus extension-bridge path is the evidence.

One wiring gap was found and fixed by this gate: `PersistingDownloadEngine` only saved on public engine calls, so worker-internal states (`DOWNLOADING`, `COMPLETED`) were not written by the Android/iOS/web hosts. `WebExtensionEngine` and `ChaquopyEngine` now take the same synchronous `persist` callback `HttpDownloadEngine` already had, and the Android/iOS/web graphs wire it to the shared `JobDocumentStore`; the host gates then reload `COMPLETED` correctly.

Verification run:

- `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :apps:android-engine-tests:test :shared:ui:iosSimulatorArm64Test :apps:web:wasmJsBrowserTest` — BUILD SUCCESSFUL; `AndroidM1GateTest` 1/1, `IosM1GateTest` 1/1, `WebM1GateTest` 1/1.
- `./gradlew :apps:android:assembleDebug` — BUILD SUCCESSFUL.
- Regression: `CHROME_BIN=... ./gradlew :shared:core:jvmTest :shared:core:wasmJsBrowserTest :shared:core:iosSimulatorArm64Test :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :apps:android:assembleDebug` — BUILD SUCCESSFUL; core jvm 502, core wasm 454, core iOS 465, ui jvm 91, desktop 131, android-engine-tests 26, all 0 failures.

Host limits: Android has no emulator/device run; iOS is simulator-only; web has no real browser click-through. Desktop was run on macOS 26.5.2 (Darwin 25.5.0).
