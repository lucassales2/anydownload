---
id: T-093
type: task
priority: P0
milestone: D6
tags: [task, verification]
---

# T-093 — Phase 6 verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 6](../00-project/Phase-6-SpotDL-Parity.md) · [Feature parity](../01-product/Feature-parity.md)

## Outcome

Phase D6 is shown on all four families, or a host gap is written down. This is spotDL's operations, not X/Twitter and not a second web server.

## Dependencies

- [T-086](T-086-Spotify-download.md) through [T-092](T-092-Spotify-library.md).

## Work

1. Desktop: one public track downloads and is tagged; save, url, meta, and a fixture sync pass. State whether the live track was opt-in.
2. Android: the same fixture path through the JVM-equivalent tests, plus APK assemble if no emulator exists. State which.
3. iOS: simulator fixture. No live YouTube. Foreground-only stays documented.
4. Web: Spotify and provider calls go through the extension. An origin the extension cannot reach is a recorded gap, not a page fetch.
5. Shared tests from T-083's command list stay green.
6. README status names D6 and the honest limits. Update Roadmap, Home, the decision log, and the Phase 6 note status.
7. F-27 through F-34 each have a result or a gap. T-037 is Done.

## Acceptance criteria

- [x] Four-host table with pass, fail, or gap for download, save, sync, and library login.
- [x] No spotDL package, cookie, token, private URL, or media file added to the repository.
- [x] F-27 through F-34 updated. Spotify audio is still not a source.
- [x] Web does not fetch Spotify or YouTube from the page itself.

## Evidence / notes

Done 2026-09-25. Public fixtures and public hosts only; no URL, cookie, token, media file, or binary is added.

Four-host table:

| Host | Public Spotify download | save / url / meta | sync | Library login | Result |
| --- | --- | --- | --- | --- | --- |
| Desktop | **Pass** — a public fixture track becomes a tagged MP3 inside the root; `ffprobe` shows title/artist/album and the cover (T-086 gate). Live opt-in was not used. | **Pass** — `.spotdl` write/reload, one URL per song, in-place retag with no media fetch (T-090). | **Pass** — fixture add/delete, sync-without-deleting, foreign file untouched, LRC sibling removed (T-091). | **Pass** — Settings login/logout and fixture-token queries (T-092). | **Pass** |
| Android | **Pass (JVM-equivalent)** — shared tests plus `assembleDebug`; the public path is wired in `AndroidAppGraph`; no emulator/AVD exists here. Tags are skipped (no tag writer). | **Gap** — no on-device list store is wired yet. | **Gap** — same. | **Gap** — no on-device token store; the query fails typed. | **Pass (JVM-equivalent, gaps recorded)** |
| iOS | **Pass (simulator)** — shared tests plus `iosSimulatorArm64Test`; the public path is wired in `IosAppGraph`; no live YouTube; foreground-only. Tags are skipped. | **Gap** — no on-device list store. | **Gap** — same. | **Gap** — no on-device token store. | **Pass (simulator, gaps recorded)** |
| Web | **Gap** — the public path is wired through the extension, but there is no browser end-to-end Spotify run; the page makes 0 Spotify/YouTube requests (node check). Tags are skipped. | **Gap** — no list store on web. | **Gap** — same. | **Gap** — no token store on web. | **Gap recorded** |

Evidence per host:

- Desktop: `DesktopSpotifyDownloadGateTest` (tagged MP3 with `ffprobe`, m3u in list order plus archive, and the meta retag with no media fetch), `DesktopFfmpegToolkitTest` (tags and lyrics), and the shared music tests. Live track: not used; the anonymous Spotify Web API returned `429 QUOTA_EXCEEDED` from this network, and no live call is recorded.
- Android: `:apps:android-engine-tests:test` (20) and the shared core tests; `:apps:android:assembleDebug` and `:apps:android:compileDebugAndroidTestKotlin` pass. No emulator/AVD exists here, so the JVM-equivalent route is the evidence; `AndroidAppGraph` wires the public Spotify service.
- iOS: `:shared:core:iosSimulatorArm64Test` (428) and `:shared:ui:iosSimulatorArm64Test` (4); `IosAppGraph` wires the public Spotify service. No live YouTube; foreground-only stays documented.
- Web: `CHROME_BIN=… ./gradlew :shared:core:wasmJsBrowserTest` (417) and `node --test apps/web-extension/test/bridge.test.mjs` (23), including `the Compose/Wasm page source performs no fetch call`; the extension request allowlist carries `authorization`; `WebAppGraph` wires the public Spotify service over the extension. No browser end-to-end Spotify run is recorded, so the web Spotify row is a gap.
- Shared T-083 command list: core JVM 465, core iOS 428, core wasm 417, UI JVM 85, UI iOS 4, desktop 128 (14 opt-in skipped), android-engine 20, extension node 23; `:tools:port-manifest:check` ok.
- F-27 through F-34 are updated in the [parity matrix](../01-product/Feature-parity.md) with the D6 result or gap. Spotify audio is still not a source: no Spotify CDN or preview URL appears in the music code, and the providers return only matched provider URLs.
- Hygiene: no spotDL package, vendored Python, or `spotdl` subprocess; no cookie, token, private URL, or media file added; the only token-shaped fixture value is the repository's `REDACTED` placeholder; common code has no `ProcessBuilder`.
- Docs updated: README status and the D6 section, Roadmap, Home, Decision log, [ADR-010](../03-decisions/ADR-010-Spotdl-parity-phase.md) accepted, the [Phase 6](../00-project/Phase-6-SpotDL-Parity.md) note marked done, and F-27–F-34.

Commands run (2026-09-25, all green):

- `./gradlew :shared:core:jvmTest :shared:core:iosSimulatorArm64Test :shared:ui:jvmTest :shared:ui:iosSimulatorArm64Test :apps:desktop:test :apps:android-engine-tests:test :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin :tools:port-manifest:check :apps:web:wasmJsBrowserDistribution` — BUILD SUCCESSFUL.
- `CHROME_BIN="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser" ./gradlew :shared:core:wasmJsBrowserTest` — BUILD SUCCESSFUL.
- `node --test apps/web-extension/test/bridge.test.mjs` — 23 tests, 0 failures.
- Noted: the desktop FFmpeg tests have a pre-existing intermittent flake under repeated full-suite runs; each test passes in isolation and the recorded run passed.
