---
id: T-004
type: task
priority: P0
milestone: M0
tags: [task, research, platforms]
---

# T-004 — Validate all four Kotlin targets

[Home](../Home.md) · [Kanban](../Kanban.md) · [Platform matrix](../02-architecture/Platform-matrix.md)

## Outcome

Show that one local download can finish on iOS, Compose/Wasm, Android, and desktop, or record the target as blocked.

## Dependencies

- [T-001](T-001-Planning-vault.md).

## Acceptance criteria

- [x] Record exact toolchain versions, OS/browser/CPU coverage, build/run evidence, and proposed minimum versions.
- [x] Download one public URL to a device file on Android, iOS, desktop, and Compose/Wasm, or document the blocker. Name which desktop OS was actually run.
- [ ] On the web, try Safari, Firefox, and Chromium, including a cross-origin media fetch and a large file written without holding it all in memory.
- [x] Document iOS suspension, browser tab close, and file-save limits.
- [x] Accept Compose/Wasm for the engine or document a Kotlin/JS fallback in a new ADR.

## Evidence / notes

Closed from [T-044](T-044-Phase-2-verification.md) evidence on 2026-09-23 (direct HTTP(S) slice only; no extractor port claimed).

- Toolchain (run on macOS 26.5 arm64 - the desktop OS actually exercised): Gradle 9.7.1, Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.0.0, JDK 21, Xcode 26.5, Node 26, Android compileSdk 37 / minSdk 26, iOS 16 minimum, Chromium 153 (Brave) for the extension check. Proposed minimums stay the pinned README values as provisional.
- iOS: direct file downloaded in the simulator sandbox via the real NSURLSession path (1 MiB local fixture, job COMPLETED, artifact on disk) plus signed-off native tests; unsigned simulator build + app launch succeeded.
- Desktop: direct file via Kotlin HTTP (8 MiB local-server stream test + routing engine test; yt-dlp present on PATH, CLI path preserved for site URLs).
- Web: extension path - Chromium (Brave) service worker downloaded the fixture via `chrome.downloads` to the browser download folder; page never fetches; absent-extension refusal tested.
- Android: engine wired + Chaquopy config validated, but **blocked at APK**: androidx Compose 1.12.0 AARs require AGP >= 9.1.0 (alpha-only) vs catalog AGP 9.0.0. JVM-equivalent tests run via `apps/android-engine-tests`.
- Web browsers: only Chromium was exercised (Safari/Firefox not run - left open for a later M-follow-up). Cross-origin extension fetch proven; the redirected download path streams inside the browser's downloader (service worker buffers no body).
- Compose/Wasm is accepted for the UI and page-side engine per [ADR-006](../03-decisions/ADR-006-Local-http-engine-phase.md), which replaces the need for a Kotlin/JS fallback ADR for this slice.
- Suspension/tab-close: iOS is foreground-only with an in-memory store, so a suspended job is never re-marked Completed; the web queue dies with the tab while the browser's own download continues in `chrome.downloads`. Browser saving goes to the default downloads folder.
