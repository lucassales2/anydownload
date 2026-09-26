---
id: T-100
type: task
priority: P0
milestone: D7
tags: [task, verification, twitter]
---

# T-100 — Phase 7 verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 7](../00-project/Phase-7-X-Twitter.md) · [ADR-011](../03-decisions/ADR-011-X-twitter-phase.md) · [Ytdlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

Phase D7 is shown on all four host families, or a host gap is written down. The manifest, equivalence row, Roadmap, Home, and decision log are current, and ADR-011 is accepted. This is the public X/Twitter status video, not photos, threads, Spaces, or any other site.

## Dependencies

- [T-094](T-094-Twitter-status-extractor.md), [T-095](T-095-Preview-status-videos.md), [T-096](T-096-Gate-selected-media-download.md), [T-097](T-097-Android-routes-x-status.md), [T-098](T-098-Ios-x-status-download.md), [T-099](T-099-Web-extension-x-requests.md).

## Work

1. Desktop: a fixture status previews, a two-video selection downloads two files, an empty selection downloads nothing, and a photo-only status fails typed. State whether the opt-in live status was used and what it saw.
2. Android: the JVM-equivalent fixture path, plus `assembleDebug` or an emulator result. State which.
3. iOS: simulator fixture. No live X/Twitter. Foreground-only stays documented.
4. Web: the extension carries the guest lookup and the media GET; the page performs zero X/Twitter fetches. An unreachable origin is a recorded gap, not a page fetch.
5. Shared tests from the task notes stay green.
6. `port/manifest.json` lists `TwitterIE` as partial with the pin and the Kotlin file; the generated coverage block is regenerated and `:tools:port-manifest:check` is green. The equivalence note's named-priorities text and coverage counts reflect the new row.
7. README status names D7 and the honest limits. Update Roadmap, Home, the decision log, and the Phase 7 note status.
8. Accept [ADR-011](../03-decisions/ADR-011-X-twitter-phase.md) when the four-host table is recorded.

## Acceptance criteria

- [x] Four-host table with pass, fail, or gap for preview, selection, download, and the no-CLI/no-cookie rules.
- [x] Default CI is fixture-only; the only live X/Twitter path is the opt-in desktop test, and no live URL appears in the repo.
- [x] No cookie, guest token, signed media URL, or media file was added; no `twitter.py` was vendored.
- [x] Manifest and equivalence row updated and validated; ADR-011 accepted; Roadmap, Home, decision log, README, and phase note updated.
- [x] T-094 through T-099 are Done; T-096 was not marked Done before T-094 and T-095.

## Evidence / notes

Done 2026-09-25.

Four-host table:

| Host | Preview + selection | Download | No CLI / no cookies | Result |
| --- | --- | --- | --- | --- |
| Desktop | **Pass** — a fixture status lists its two videos and the first is preselected (`DesktopXStatusGateTest`, `StatusVideoSelectionUiTest`). | **Pass** — both selected videos land as two files with the fixture bytes; the unselected URL is never fetched; empty and stale selections fail typed; the opt-in live test exists and was skipped (no `-PxStatusUrl`). | **Pass** — the route is KOTLIN and zero CLI process starts. | **Pass** |
| Android | **Pass (JVM-equivalent)** — `AndroidXStatusTest` previews the two stable ids through the shared engine. | **Pass (JVM-equivalent)** — two files with the fixture bytes and the Chaquopy port untouched; `assembleDebug` passes; no emulator/AVD exists here. | **Pass** — the registry match routes to KOTLIN before any probe. | **Pass (JVM-equivalent, emulator gap recorded)** |
| iOS | **Pass (simulator)** — `IosXStatusTest` previews the two stable ids through the shared engine and the sandbox `IosFileStore`. | **Pass (simulator)** — two files with the fixture bytes; no live X/Twitter call; foreground-only. | **Pass** — no Python or CLI on iOS. | **Pass (simulator)** |
| Web | **Pass** — the page lists the videos and `WebAppGraph` registers `TwitterIE` over the extension request port. | **Pass (wasm + node fakes)** — one file per selected video through `chrome.downloads`; a browser end-to-end UI run is the recorded gap. | **Pass** — the page makes zero X/Twitter/syndication fetches; the extension drops the MV3-forbidden `user-agent`, and no cookie or authorization rides the lookup. | **Pass (web end-to-end gap recorded)** |

Commands run 2026-09-25 (all green):

- `./gradlew :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test :apps:android-engine-tests:test :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin :apps:web:wasmJsBrowserDistribution :tools:port-manifest:check` — BUILD SUCCESSFUL.
- `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:wasmJsBrowserTest` — BUILD SUCCESSFUL.
- `node --test apps/web-extension/test/bridge.test.mjs` — 25 tests, 0 failures.

Counts: core JVM 483 / 0; UI JVM 90 / 0; desktop 130 / 0 with 15 opt-in skips (`DesktopXStatusGateTest` 1 / 0, `DesktopXStatusLiveTest` 1 skipped); core iOS 446 / 0; UI iOS 5 / 0 (`IosXStatusTest` 1 / 0); android-engine 22 / 0 (`AndroidXStatusTest` 2 / 0); core wasm 435 / 0; node 25 / 0. `TwitterIETest` 10 / 0 and `EngineExtractionTest` 13 / 0 run on JVM, iOS, and wasm; `WebExtensionEngineTest` 16 / 0 on JVM, iOS, and wasm; `StatusVideoSelectionUiTest` 5 / 0 on JVM.

Live opt-in: no `-PxStatusUrl` was configured, so `DesktopXStatusLiveTest` was skipped and reported. It stays out of default CI, and no live X/Twitter URL is in the repository. A first consolidated Gradle attempt failed transiently; the immediate rerun and a `--rerun` of the desktop suite were green (the T-093 evidence records the same pre-existing intermittent desktop FFmpeg flake; no failure reproduced in the recorded run).

Manifest and equivalence: `port/manifest.json` lists `TwitterIE` partial (upstream `yt_dlp/extractor/twitter.py`, `TwitterIE.kt`, task T-094, `portedAt` 2026-09-25); the generated coverage block shows 0 ported, 4 partial, 0 planned, 1,747 not started; `:tools:port-manifest:check` is green. The [equivalence note](../01-product/Ytdlp-equivalence.md) records the D7 port in its named-priorities text.

Docs updated: README D7 section and status line, Roadmap D7, Home phase/start/docs map, Decision log (ADR-011 accepted), [ADR-011](../03-decisions/ADR-011-X-twitter-phase.md) accepted, and the [Phase 7](../00-project/Phase-7-X-Twitter.md) note marked done with the verified table.

Hygiene: no cookie, guest token, signed media URL, private URL, or media file was added; `twitter.py` was not vendored; common code has no `ProcessBuilder`; T-096 was marked Done only after T-094 and T-095, and T-100 was marked Done only after all earlier D7 tasks.
