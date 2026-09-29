---
id: T-008
type: task
priority: P0
milestone: M1
tags: [task, clients, kmp]
---

# T-008 — Scaffold shared Kotlin clients

[Home](../Home.md) · [Kanban](../Kanban.md) · [Architecture](../02-architecture/Architecture.md)

## Outcome

Shared app shell whose engine runs in-process. The current remote API client does not satisfy this.

## Dependencies

- [T-007](T-007-Define-UX-and-contract.md), including the M0 exit gate.

## Acceptance criteria

- [x] Pin toolchain versions and create Android, iOS, desktop, and Compose/Wasm entry points plus shared domain and UI modules.
- [x] Replace the server API client with an in-process engine boundary and platform file adapters. No JVM-only process APIs in common code.
- [x] Provide repeatable build/run instructions and CI evidence for each target. Signing for store release is out of scope.
- [x] Add shared engine/state tests and a dependency inventory. License sign-off stays in T-006.

## Evidence / notes

Draft scaffold added ahead of the M0 gate at the owner's request on 2026-09-16. On 2026-09-21 the owner required a local Kotlin engine and no backend ([ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md)). This scaffold's API client and job DTOs target a server and are not the engine.

- Pinned toolchain: Gradle 9.7.1 (checksum-pinned wrapper), Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.0.0, Ktor 3.6.0, JDK 21.
- Verified locally on 2026-09-16 (macOS 26, JDK 21.0.11, Xcode 26.5, Android SDK 37): `:shared:core:jvmTest`, `:shared:network:jvmTest`, `:apps:android:assembleDebug`, `:apps:desktop:compileKotlin`, `:apps:web:wasmJsBrowserDistribution`, `:shared:ui:linkDebugFrameworkIosArm64`, and an unsigned iOS Simulator `xcodebuild`.
- Still open: the remaining acceptance items. [T-106](T-106-Four-host-m1.md) in [Phase 8](../00-project/Phase-8-On-device-core.md) closes this card. Store signing stays out of scope. CI is `.github/workflows/client-build.yml`.
- Phase D1 (ADR-005, T-026–T-036) replaces the desktop shell and calls an installed yt-dlp from `apps/desktop` only. That does not satisfy the in-process engine criterion above. Do not move a process API into common code while doing D1.

### Closed by Phase 8 (2026-09-29)

- **In-process engine:** `shared/core` owns `DownloadEngine`, `HttpDownloadEngine`, the in-process extractor port, and the host hooks (`FileStore`, `HttpTransfer`, `MediaToolkit`). `AppGraph.engine` is a `DownloadEngine` on every host; `apps/desktop` is the only module with a process (`ProcessBuilder`), and `apps/android`'s Chaquopy adapter stays outside common code. The withdrawn `shared/network` API client is no longer used by any app; the API outline is marked withdrawn.
- **Tests and inventory:** shared engine/state tests live in `shared/core/src/commonTest` and `src/jvmTest` (502 JVM tests, 454 wasm, 465 iOS on 2026-09-29), with the host stores and gates covered in T-103/T-105/T-106. The dependency/license inventory is in [Security and licensing](../04-delivery/Security-and-licensing.md) (§License review inventory); the exact transitive audit remains a T-006/T-022 follow-up, and no final inventory is invented here.
- **CI:** `.github/workflows/client-build.yml` runs `:shared:core:jvmTest :shared:network:jvmTest`, `:apps:android:assembleDebug`, `:apps:desktop:compileKotlin`, and `:apps:web:wasmJsBrowserDistribution` on JDK 21 + Android platform 37. Store signing stays out of scope.
- **Entry points:** Android `MainActivity`, iOS `MainViewController` via the SwiftUI host, desktop `main()`, and the Compose/Wasm `main()`; shared domain and UI live in `shared/core` and `shared/ui`.
