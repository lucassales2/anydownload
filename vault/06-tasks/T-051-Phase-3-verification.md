---
id: T-051
type: task
priority: P0
milestone: D3
tags: [task, verification]
---

# T-051 — Phase 3 verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 3](../00-project/Phase-3-Generic-Extractor.md)

## Outcome

Phase D3 is shown on all four families, or a host is recorded as blocked. This is one HTML fixture, not a yt-dlp site catalog and not YouTube.

## Dependencies

- [T-045](T-045-Generic-extractor-subset.md) through [T-050](T-050-Web-extension-generic.md).

## Work

Use a local HTML page with one media element and a local media file. Do not paste private URLs.

1. Desktop: matching page via Kotlin; one non-matching site URL still via CLI (or record that yt-dlp is missing).
2. Android: matching page via Kotlin; one unresolved site URL via Chaquopy, or the existing APK blocker plus the JVM-equivalent test.
3. iOS: matching page in the sandbox; unresolved HTML shows extractor-not-implemented.
4. Web: extension present saves the media; extension absent refuses; page does not fetch the origin.
5. Shared tests: `./gradlew :shared:core:jvmTest` and the host tests that exist.
6. Update README status to D3. State that the media toolkit is recorded and not built, and that YouTube is still later.
7. Document iOS suspension and browser tab-close honestly, including that an in-flight extension download can outlive the tab.

## Acceptance criteria

- [x] Four-host table with pass/fail/blocked and versions.
- [x] README matches D3.
- [x] No private URL, cookie, or media file was added to the repository.
- [x] No FFmpeg, MediaMuxer, or AVFoundation postprocessing landed in this phase.

## Evidence / notes

Done 2026-09-24. One local HTML fixture (one `<video>/<audio>/<source>`) and a local media file; no private URL, cookie, or media file appears anywhere in the repository (only fixture hosts `example.com/org/net` and loopback servers; grep swept). The media toolkit is recorded in ADR-007 and not built; YouTube stays later.

### Four-host table (2026-09-24)

| Host | D3 fixture HTML route | Result / blocker | Versions |
| --- | --- | --- | --- |
| Desktop | `DesktopRouteClassifier` bounded page probe → shared engine; unresolved → CLI | **Pass** — `htmlFixtureRoutedByClassifierCompletesWithoutProcess` (COMPLETED, no process); `nonMatchingHtmlPageStillReachesTheCliPath`; direct files never spawn a process | Gradle 9.7.1 / Kotlin 2.4.20 / Compose 1.12.0 / JDK 21; installed `yt-dlp` 2026.08.19 on PATH |
| Android | `AndroidRouteClassifier` probe → shared engine; unresolved → Chaquopy port | **Pass (JVM-equivalent)** — `:apps:android-engine-tests:test` 11 tests (`htmlFixtureCompletesThroughHttpEngineWithoutPython`, `unresolvedHtmlStillDispatchesIntoTheFakeChaquopyPort`, cancel, direct-file); APK assemble **blocked** (pre-existing AGP 9.0.0 vs Compose 1.12 AAR metadata mismatch — T-041) | Chaquopy 17.0.0 (opt-in), pinned `yt-dlp==2026.8.19` (tag `2026.08.19`), compileSdk 37 / minSdk 26 |
| iOS | Shared engine (bounded page read + extractor) with NSURLSession + sandbox store | **Pass** — live simulator run: `realNSURLSessionDownloadsAMatchingHtmlPageAndFailsUnresolvedTyped` — COMPLETED, `clip.bin` (4096 B) in the sandbox; unresolved page typed `EXTRACTION_FAILURE`; foreground-only, no background `URLSession` | Xcode 26.5, iOS 16 target; Kotlin/Native simulator tests (124 total; opt-in live test runs with a fixture endpoint file) |
| Web | Extension fetches the page; page runs the Kotlin extractor; extension saves the media | **Pass** — `node --test` 12/12; `WebExtensionEngineTest` HTML cases; real Chromium (Brave 153.1.95.104) run: extension SW fetched `GET /watch` + `GET /media/clip.bin` (server log), download state `complete`, file saved (262144 B); without the extension `ENGINE_UNAVAILABLE` with zero bridge calls; page source has no `fetch(` | Chromium 153 (Brave), extension v0.1.0 (MV3) |

### Shared and host tests run (all 0 failures)

- `./gradlew :shared:core:jvmTest` — 127 tests (extractor 15, engine HTML route 7, web bridge cases).
- `./gradlew :shared:ui:jvmTest` — 71 tests (link-only home, validate-then-preview, collapsible edit).
- `./gradlew :apps:desktop:test` — 101 tests (routing + classifier fixtures over a local `HttpServer`; one pre-existing timing flake in `YtDlpCliEngineTest.playlistScan...` passed on immediate rerun, unrelated to D3 code).
- `./gradlew :apps:android-engine-tests:test` — 11 tests.
- `node --test apps/web-extension/test/bridge.test.mjs` — 12 tests.
- `./gradlew :shared:core:iosSimulatorArm64Test` — 124 native tests; live configured once with a fixture endpoint → both opt-in live tests passed and the fixture server request log proved the simulator's GETs.

### Honest limits (also in README)

- iOS: foreground-only; suspension suspends transfers, no completed file claimed on relaunch (D1 clause).
- Web: closing the tab ends the page's queue view; an in-flight extension download can finish after the tab closes, so a saved file can outlive the tab.
- Desktop: the classifier fetches one bounded page body per submit to route HTML; probe failures fall back to the CLI.
- Android: APK blocked in this env; JVM-equivalent tests are the evidence; Chaquopy stays opt-in under `apps/android`.

README status updated: D3 verified, four-host table, toolkit recorded-not-built, YouTube later, honest gaps listed.
