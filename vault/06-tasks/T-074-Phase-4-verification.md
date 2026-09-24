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

- [ ] Four-host table with pass/fail/blocked, versions, runtime name/version, and format counts with and without the runtime.
- [ ] Live harness and oracle results recorded.
- [ ] Coverage block regenerated and README matches D4.
- [ ] No private URL, cookie, token, `googlevideo` URL, or media file added to the repository.
- [ ] No FFmpeg, MediaMuxer, or AVFoundation postprocessing landed; no runtime EJS download landed.

## Evidence / notes

Not started.
