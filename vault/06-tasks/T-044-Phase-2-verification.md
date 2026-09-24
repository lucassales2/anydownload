---
id: T-044
type: task
priority: P0
milestone: D2
tags: [task, verification]
---

# T-044 — Phase 2 verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 2](../00-project/Phase-2-Local-Kotlin-Engine.md) · [T-004](T-004-Validate-KMP-targets.md)

## Outcome

Phase D2 is shown on all four families, or a host is recorded as blocked. This evidence closes T-004 for the HTTP-only slice. It does not claim a yt-dlp extractor port.

## Dependencies

- [T-038](T-038-Shared-http-engine.md) through [T-043](T-043-Web-extension-download.md).
- [T-006](T-006-Review-security-licensing.md) and the Q-09 close on [T-003](T-003-Approve-product-scope.md).

## Work

For each host, record pass or fail. Use a tiny public or locally served file you are allowed to download. Do not paste private URLs.

1. Desktop: direct file via Kotlin HTTP; one yt-dlp site URL still via CLI (or record that yt-dlp is missing).
2. Android: direct file via Kotlin HTTP; one site URL via Chaquopy, or a written Chaquopy blocker.
3. iOS: direct file in the sandbox; site URL shows extractor-not-implemented.
4. Web: extension present → direct file saves; extension absent → Add refuses; page does not fetch the origin.
5. Shared tests: `./gradlew :shared:core:jvmTest` and the host tests that exist.
6. Update README: D2 status, extension load, Chaquopy note, ADR-004 still later.
7. Fill T-004 acceptance boxes from this evidence, including toolchain versions and which desktop OS was run.
8. Document iOS suspension and browser tab-close honestly.

## Acceptance criteria

- [x] Four-host table with pass/fail/blocked and versions.
- [x] T-004 checkboxes updated from this run.
- [x] README matches D2, not D1-only.
- [x] No private URL, cookie, or media file was added to the repository.

## Evidence / notes

Run on 2026-09-23. Toolchain: macOS 26.5 (arm64), Gradle 9.7.1, Kotlin 2.4.20, Compose MP 1.12.0, AGP 9.0.0, JDK 21, Xcode 26.5, Android compileSdk 37/minSdk 26, iOS 16 target, Node 26, Chromium 153 (Brave). yt-dlp and ffmpeg are installed at `/opt/homebrew/bin`.

### Four-host table

| Host | Direct HTTP(S) file | Site/HTML URL | Result |
| --- | --- | --- | --- |
| Desktop (macOS) | **Pass** - shared `HttpDownloadEngine` + JavaNet transfer/store; 8 MiB local-server stream verified and routing tests prove no process is spawned | **Pass (unchanged D1)** - installed yt-dlp CLI on PATH; path covered by D1 fakes + routing test `siteOrHtmlUrlStaysOnTheCliPath` | Tests: `:apps:desktop:test` 92 (0 failures), `:shared:core:jvmTest` 101 (0) |
| Android | **Pass (engine)** - `AndroidRoutingEngine`/`HttpDownloadEngine` wired in `AndroidAppGraph`; JVM-equivalent tests (`:apps:android-engine-tests:test` 7) cover direct-file success, cancel, classifier | **Blocked (APK)** - Chaquopy port + config validated (`-DchaquopyVersion=17.0.0`, pinned `yt-dlp==2026.8.19`), site-URL dispatch tested with a fake port; the Debug APK cannot assemble in this environment | Pre-existing blocker: androidx Compose 1.12.0 AARs require AGP >= 9.1.0 (alpha-only) vs catalog AGP 9.0.0 (T-041) |
| iOS | **Pass** - real NSURLSession path: 1 MiB local fixture downloaded in the simulator, job COMPLETED, artifact in the sandbox; `:shared:core:iosSimulatorArm64Test` 91 (0), live fixture run, unsigned xcodebuild build + `simctl` launch | **Pass** - `htmlContentFailsWithTypedExtractorError` -> typed `EXTRACTION_FAILURE`; no Python/CLI | Foreground-only (suspension note below) |
| Web | **Pass** - MV3 extension: Brave/Chromium service worker fetched the fixture (`GET /files/tiny.bin 200` in server log) and saved `tiny.bin` (1,048,576 bytes) via `chrome.downloads`; queue lifecycle proven by `WebExtensionEngineTest` (7) + extension node tests (8) | **Pass** - HTML -> typed `EXTRACTION_FAILURE` (extension refuses; engine maps) | Extension absent -> Add refuses (`ENGINE_UNAVAILABLE`), page performs no fetch (`grep 'fetch('` empty) |

### Honest limits recorded

- iOS suspension / web tab-close: iOS is foreground-only and its store is still in-memory, so an interrupted job is never re-marked Completed on a later launch; closing the web tab ends the page queue while the browser's own download continues in `chrome.downloads` (saved to the browser's default downloads folder).
- Only Chromium was exercised for the web (Safari/Firefox left open, best-effort gap). The extension currently uses static `http(s)://*/*` host permissions.
- Android and the final APK: blocked by the recorded AGP/Compose mismatch, not by the engine. Chaquopy is MIT since 12.0.1 (T-006) and remains an `apps/android`-only adapter; ADR-004's Kotlin port is still later.

### Sweep

`./gradlew :shared:core:jvmTest` (101), `:shared:ui:jvmTest` (77), `:apps:desktop:test` (92), `:apps:android-engine-tests:test` (7), `:shared:core:iosSimulatorArm64Test` (91) - all 0 failures; wasm compiles; web dist builds; README updated to D2 with extension-load, Chaquopy, and ADR-004-still-later notes. T-004 checklist updated from this evidence; no private URLs, cookies, or media files were added (only `example.com`/`fixtures.example.com` fixture strings and ephemeral `/tmp` fixtures).
