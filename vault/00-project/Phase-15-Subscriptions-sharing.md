---
type: phase
status: done
milestone: D15
tags: [project, engine, product]
---

# Phase 15 — Subscriptions and sharing

[Home](../Home.md) · [Kanban](../Kanban.md) · [Schedule](Full-engine-schedule.md) · [ADR-014](../03-decisions/ADR-014-Full-kotlin-engine-schedule.md)

**Done 2026-09-30. 6 engineer-days planned.** Depends on D14. Cumulative raw time through this phase: 53 days, about 11 weeks. YouTube, cookies, postprocessing, and the MeTube workflows run in Kotlin. Every other site still uses the desktop CLI or Android Chaquopy.

## Done looks like

Channel and playlist subscriptions scan while the app is open, with a seen-id cap and an explicit initial-backlog policy. iOS and the browser say that suspension stops a scan. Share entry points are Android share, the iOS share sheet, desktop paste, and the existing web extension. F-14 through F-23 have fixture evidence. F-20 stays the allowlist difference. F-24 and F-25 stay withdrawn.

## Tasks

[T-019](../06-tasks/T-019-Subscriptions.md) and [T-020](../06-tasks/T-020-Sharing-and-UX.md).

## Verification

Landed 2026-09-30 with T-019 and T-020: the shared subscription scanner (first check marks seen and downloads nothing, explicit backlog choice, capped seen ids, backoff/jitter, downtime dueIds) persisted on all four hosts; Android `ACTION_SEND` share, the iOS share extension plus `anydownload://` scheme, desktop paste, and the existing web extension; adaptive shell and accessibility basis recorded; no MeTube protocol client or new integrations; F-24/F-25 withdrawn and F-20 the allowlist difference.

Commands (sequential; the fixture suites are timing-sensitive when Gradle runs projects in parallel):

- `./gradlew --no-parallel :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :tools:port-manifest:test --rerun-tasks` — BUILD SUCCESSFUL; core 740, ui 108, desktop 146 (15 live-only skipped), android-engine 41, port-manifest 25; 0 failures.
- `:shared:core` wasm/iOS main+test, `:apps:android:compileDebugKotlin`, `:apps:web:compileKotlinWasmJs`, `:shared:ui:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL.
- iOS: `xcodegen generate` and `xcodebuild -target AnyDownloadShare -sdk iphonesimulator` — BUILD SUCCEEDED.

Limits: scans run only while the app is open (iOS and web say so); a failed download is retried from the queue rather than re-enqueued by the scanner; no device screen-reader/font-scale/contrast lab run was performed across all four hosts.
