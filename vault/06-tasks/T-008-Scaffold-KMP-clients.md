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
- [ ] Replace the server API client with an in-process engine boundary and platform file adapters. No JVM-only process APIs in common code.
- [ ] Provide repeatable build/run instructions and CI evidence for each target. Signing for store release is out of scope.
- [ ] Add shared engine/state tests and a dependency inventory. License sign-off stays in T-006.

## Evidence / notes

Draft scaffold added ahead of the M0 gate at the owner's request on 2026-09-16. On 2026-09-21 the owner required a local Kotlin engine and no backend ([ADR-004](../03-decisions/ADR-004-Local-kotlin-engine.md)). This scaffold's API client and job DTOs target a server and are not the engine.

- Pinned toolchain: Gradle 9.7.1 (checksum-pinned wrapper), Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.4.0, Ktor 3.6.0, JDK 21.
- Verified locally on 2026-09-16 (macOS 26, JDK 21.0.11, Xcode 26.5, Android SDK 37): `:shared:core:jvmTest`, `:shared:network:jvmTest`, `:apps:android:assembleDebug`, `:apps:desktop:compileKotlin`, `:apps:web:wasmJsBrowserDistribution`, `:shared:ui:linkDebugFrameworkIosArm64`, and an unsigned iOS Simulator `xcodebuild`.
- Still open: in-process engine boundary, file adapters, CI evidence, dependency inventory.
- Phase D1 (ADR-005, T-026–T-036) replaces the desktop shell and calls an installed yt-dlp from `apps/desktop` only. That does not satisfy the in-process engine criterion above. Do not move a process API into common code while doing D1.
