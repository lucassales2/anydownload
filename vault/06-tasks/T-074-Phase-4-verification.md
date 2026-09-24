---
id: T-074
type: task
priority: P0
milestone: D4
tags: [task, verification]
---

# T-074 — Phase 4 verification

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md) · [yt-dlp equivalence](../01-product/Ytdlp-equivalence.md)

## Outcome

Phase D4 is shown on all four families with and without the JavaScript runtime, or a host is recorded as blocked. This is YouTube single video at single-file quality plus the extractor core, not the catalog and not the media toolkit.

## Dependencies

- [T-068](T-068-Gate-jsless-youtube-four-hosts.md) through [T-073](T-073-Native-hls-dash-downloaders.md).

## Work

Use one public, permanently available video. Do not paste private URLs, tokens, or `googlevideo` URLs.

1. Desktop: preview and download with the embedded runtime (formats from both clients), then with the runtime disabled (stage 1); unmatched site URL still via CLI; a local HLS fixture downloads as one file.
2. Android: same, emulator or the recorded JVM-equivalent evidence plus APK assemble.
3. iOS: simulator; suspension behavior still documented.
4. Web: real Chromium with the extension, solver in the page; without the extension Add refuses; page fetches nothing.
5. Opt-in runs: `-PliveExtractorTests=true`, `-PytDlpOracle=true` (both clients). Record dates and redacted results.
6. Shared tests: `:shared:core:jvmTest`, `:shared:ui:jvmTest`, `:apps:desktop:test`, `:apps:android-engine-tests:test`, `node --test`, `:shared:core:iosSimulatorArm64Test`, the Wasm browser test.
7. `./gradlew :tools:port-manifest:run`; the coverage block shows `YoutubeIE` partial (single video, visionos + web) and the core/downloader/jsc modules.
8. README status to D4 with the four-host table, the honest limits, and the commands. Update Roadmap, Home, Decision log, Phase 4 note status.
9. Honest limits to record: single-file quality only (no merge), MP3/WAV/FLAC unsupported until the toolkit, PO-token formats dropped, no playlists/live/subtitles/cookies, iOS foreground-only, web progress is the browser's, the QuickJS version and solve time per host, MV3 refused headers, and that YouTube behavior changes upstream faster than this pin.

## Acceptance criteria

- [x] Four-host table with pass/fail/blocked, versions, runtime name/version, and format counts with and without the runtime.
- [x] Live harness and oracle results recorded.
- [x] Coverage block regenerated and README matches D4.
- [x] No private URL, cookie, token, `googlevideo` URL, or media file added to the repository.
- [x] No FFmpeg, MediaMuxer, or AVFoundation postprocessing landed; no runtime EJS download landed.

## Evidence / notes

Done 2026-09-24. Public video only (T-060's Big Buck Bunny); no URLs, cookies, tokens, or media recorded.

| Target | Runtime | With runtime | Without runtime | Result |
| --- | --- | --- | --- | --- |
| Desktop | Zipline QuickJS 2021-03-27 | 27 formats (web + visionos), needsJs 0 | 27 (visionos), needsJs 0 | **Pass** — live Kotlin M4A download `state=COMPLETED ... artifacts=1` with the no-CLI-process assertion; oracle diffs 0; unmatched URLs still CLI; local HLS fixture byte-exact. |
| Android | Zipline QuickJS 2021-03-27 | shared engine | shared engine | **Pass (JVM-equivalent)** — `apps/android-engine-tests` 14 tests + `:apps:android:assembleDebug`; no emulator/AVD in this environment. |
| iOS | Zipline QuickJS 2021-03-27 | shared engine | shared engine | **Pass (fixtures)** — `:shared:core:iosSimulatorArm64Test` 279 tests; the simulator's NSURLSession cannot reach YouTube (typed timeout, T-066/T-068), so the live YouTube row is **blocked** with the host live paths proving the code. Foreground-only. |
| Web | The page's own JavaScript (Worker) | 27 via the web client | 27 via visionos | **Pass** — Brave 153.1.95.104 (Chromium 153.0.8010.53): with the extension the page installed the solver hook, ran the solver in its own Worker, and made **0** YouTube requests; without the extension `extensionPresent=false` and the page made 0 requests, so Add refuses through the typed unavailable path. |

Commands and observed output:

- Live harness: `./gradlew :shared:core:jvmTest -PliveExtractorTests=true --tests "com.anydownlod.core.extract.youtube.YoutubeLiveTest" --rerun-tasks` → `live youtube: formats=27, needsJs=0, titleLength=14`; `live youtube stage2: stage1Formats=27 stage1NeedsJs=0 stage2Formats=27 stage2NeedsJs=0 quickjs=2021-03-27`.
- Desktop live Kotlin: `./gradlew :apps:desktop:test -PliveExtractorTests=true --tests "...DesktopLiveKotlinDownloadTest" --rerun-tasks` → `live desktop kotlin: state=COMPLETED formats=0 titleLength=14 artifacts=1` (no yt-dlp process started).
- Oracle: `./gradlew :apps:desktop:test -PytDlpOracle=true --tests "...YtDlpOracleTest" --rerun-tasks` → `oracle youtube: kotlinFormats=27 oracleFormats=27 diffs=0`; `oracle youtube web,visionos: 27/27 diffs=0`; `oracle youtube audio-only: kotlin=10 oracle=10 diffs=0`.
- Shared suites: `:shared:core:jvmTest` 297, `:shared:ui:jvmTest` 78, `:apps:desktop:test` 113, `:apps:android-engine-tests:test` 14, `:shared:core:iosSimulatorArm64Test` 279, `node --test apps/web-extension/test/bridge.test.mjs` 22 pass. Compiles: `:shared:core:compileTestKotlinWasmJs`, `:apps:web:compileKotlinWasmJs`, `:apps:android:compileDebugKotlin`, `:shared:core:compileKotlinIosSimulatorArm64`, plus `:apps:web:wasmJsBrowserDistribution` for the Chromium runs.
- Web runs: the extension run re-ran the T-072 CDP check (`page solver hook: installed`, `page solver result: kind=error bytes=34` for the synthetic input, `page youtube requests: 0`); the no-extension run reported `solverHook=true extensionPresent=false youtubeRequests=0`.
- Coverage: `./gradlew :tools:port-manifest:run :tools:port-manifest:check` regenerated and validated the block; the table shows `YoutubeIE` partial (single video, visionos + web) plus the core/downloader/jsc modules.
- Docs: README status set to D4 with the four-host table, limits, and commands; Phase 4 note marked done with the verification table; Roadmap, Home, and the decision log updated.
- Repository hygiene: no `googlevideo` URL outside the redaction-harness tests (synthetic only), no media/binary files added (`git status` scan), no cookies or tokens in fixtures/logs, no `ffmpeg` postprocessing (only the existing `ToolProbe` availability field), no `MediaMuxer`/`AVFoundation`, and no runtime EJS download path (the 0.8.0 scripts stay bundled and hash-verified).
- Honest limits: single-file quality only (typed failure or disabled choice when a merge/transcode is needed; MP3/WAV/FLAC disabled); PO-token formats dropped; no playlists/live/subtitles/cookies; iOS foreground-only; web progress is the browser's; QuickJS 2021-03-27 is older than the version yt-dlp-ejs recommends; YouTube changes faster than the `2026.08.19` pin.
