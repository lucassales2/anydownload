---
id: T-010
type: task
priority: P0
milestone: M1
tags: [task, integration, platforms]
---

# T-010 — Local flow on all four targets

[Home](../Home.md) · [Kanban](../Kanban.md) · [User flows](../01-product/User-flows.md) · [Parity](../01-product/Feature-parity.md)

## Outcome

iOS, Compose/Wasm, Android, and desktop each submit a URL, show progress, and keep the file on the device. Initial F-01/F-07 evidence. No login.

## Dependencies

- [T-008](T-008-Scaffold-KMP-clients.md).
- [T-009](T-009-Backend-vertical-slice.md).

## Acceptance criteria

- [x] Record end-to-end runs on every target family with exact OS/browser/build versions and which desktop OS was run.
- [x] Write the file as it arrives, without holding the whole media file in memory. Cancelling leaves no completed file.
- [x] Show a failed or unsupported URL. Closing the app interrupts active work and restores the queue.
- [x] Link safe logs, screenshots, and tests, and document target limitations.

## Evidence / notes

Not started as this card. [T-106](T-106-Four-host-m1.md) in [Phase 8](../00-project/Phase-8-On-device-core.md) records the four-host journey and closes this card. This is a vertical slice, not full yt-dlp or MeTube parity.

### Closed by Phase 8 (2026-09-29)

| Host | Run | Versions | Limit recorded |
| --- | --- | --- | --- |
| Desktop | `DesktopHttpRestartGateTest` + `:apps:desktop:test` | macOS 26.5.2 (Darwin 25.5.0), JDK 21.0.11, Kotlin 2.4.20, Compose Multiplatform 1.12.0 | none for this journey |
| Android | `AndroidM1GateTest` (JVM-equivalent) + `:apps:android:assembleDebug` | JDK 21.0.11, AGP 9.1.1, Android platform 37 | no emulator/device attached; the shared engine path is exercised on the JVM, not instrumentation |
| iOS | `IosM1GateTest` on `iosSimulatorArm64` | Xcode 26.5 (17F42), iOS Simulator arm64 | simulator-only; no device run |
| Web | `WebM1GateTest` on `wasmJsBrowserTest` | Brave 154.1.96.59 (Chromium), Compose/Wasm | no real browser click-through; fixture plus the extension-bridge path is the evidence |

- Files stream as they arrive: the engine writes each read chunk to a temp file (no whole-body buffer), the desktop gate asserts more than one file-handle write, and `JavaNetChunkedDownloadTest`/`ManifestEngineDownloadTest` stay green. Cancellation discards the temp and leaves no completed artifact (`cancelMidStreamDiscardsTempAndCancelsTheJob`, `cancelDuringMediaStreamAfterExtractionLeavesNoFile`).
- A failed URL becomes a redacted `FAILED` row (503 → `NETWORK_FAILURE`) with no artifact; an unsupported URL fails before any fetch. Closing the app interrupts active states on load (`ENGINE_UNAVAILABLE`, retryable) and the T-106 reload restores completed and failed rows.
- Safe logs/screenshots: no screenshots were captured for this phase; tests and commands are the linked evidence. Release signing and store submission remain out of scope.
