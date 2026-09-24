---
id: T-068
type: task
priority: P0
milestone: D4
tags: [task, verification, youtube]
---

# T-068 — Gate: JS-less YouTube single video on four hosts

[Home](../Home.md) · [Kanban](../Kanban.md) · [Phase 4](../00-project/Phase-4-Extractor-Core-and-YouTube.md)

## Outcome

The mid-phase gate from [ADR-008](../03-decisions/ADR-008-Extractor-core-and-youtube-phase.md): without any JavaScript runtime, a public YouTube single video previews and downloads on desktop, Android, iOS, and web, or a host is recorded as blocked. No EJS task starts before this is Done.

## Dependencies

- [T-055](T-055-Port-manifest-and-equivalence-matrix.md) through [T-067](T-067-Web-extension-carries-requests.md).

## Work

1. Pick one public, permanently available video (the Big Buck Bunny test URL from upstream is fine). Do not use private, age-gated, or member videos.
2. On each host: preview shows title/channel/duration/thumbnail; download audio (M4A) and the best progressive video; cancel one download; MP3 fails typed; a playlist URL fails `UNSUPPORTED_SOURCE`; a private-video fixture fails typed.
3. Desktop: assert no process spawned for the YouTube job; unmatched site URL still on the CLI.
4. Android: emulator run, or the JVM-equivalent suite plus the APK assemble, recorded honestly.
5. iOS: simulator run; unmatched HTML still typed.
6. Web: real Chromium with the extension; without it Add refuses.
7. Run `-PliveExtractorTests=true` for the YouTube live case and `-PytDlpOracle=true` on desktop; record the results and dates.
8. Regenerate the coverage table (`YoutubeIE` becomes `partial`, scope "single video, visionos client, no JS").

## Acceptance criteria

- [x] Four-host table with pass/fail/blocked, versions, and the hidden-format count reported by each host.
- [x] Oracle and live harness results recorded (pass, or the redacted field diff).
- [x] Coverage block regenerated; manifest updated.
- [x] No private URL, cookie, token, or `googlevideo` URL added to the repository.

## Evidence / notes

Gate run 2026-09-24. Video: the public Big Buck Bunny `https://www.youtube.com/watch?v=YE7VzlLtp-4` (the upstream test URL). Toolchain: macOS 26.5.2 arm64, Gradle 9.7.1, Kotlin 2.4.20, installed `yt-dlp` 2026.08.19, Xcode 26.5 (17F42), Brave 153.1.95.104 (Chromium 153.0.8010.53), AGP 9.1.1 / compileSdk 37. No JS runtime is present in any run.

| Host | Preview | M4A audio | Progressive video | Cancel | MP3 typed | Playlist | Private fixture | Hidden formats | Process/Python |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Desktop | **Pass** (Kotlin preview, T-063) | **Pass** (live run: `state=COMPLETED`, one `.m4a`, no process) | **Disabled/blocked for this video** — the visionos response has no progressive format; the integrated real-engine fixture test proves the progressive path and the Edit panel disables video choices | Pass (engine cancel tests, temp discarded) | **Pass** (`UNSUPPORTED_FORMAT`, media-toolkit message, no file/process) | Extractor level: `UnsupportedUrl` → `UNSUPPORTED_SOURCE`; desktop keeps the CLI fallback for the unmatched playlist URL | **Pass** (fixture → `UNAVAILABLE_OR_PRIVATE`) | 0 | No process for the matched job; unmatched site URL still on the CLI |
| Android | Pass (JVM-equivalent registry route; no emulator in this environment) | Not run live (no emulator/AVD/device); JVM-equivalent engine tests pass and the debug APK assembles | Not run live; shared engine + selector tests pass | Pass (JVM-equivalent cancel test) | Pass (shared compiler/selector tests) | `UNSUPPORTED_SOURCE` at the extractor; unmatched URL routes to Chaquopy | Pass (shared extractor tests) | n/a (fixtures) | No Chaquopy dispatch for matched URLs; Chaquopy only for unmatched |
| iOS | Pass (native fixture extractor route into the sandbox; host live extractor 27 formats) | **Blocked** — the simulator's NSURLSession cannot reach YouTube here (typed `TimedOut` after 4m37s); the host live M4A download succeeds, and `IosHttpTransfer` now fails typed instead of hanging | Blocked with the same simulator network | Pass (fixture cancel test) | Pass (shared engine) | `UNSUPPORTED_SOURCE` at the extractor | Pass (shared extractor tests) | n/a (fixtures) | No Python/CLI on iOS; framework + xcodebuild pass |
| Web | **Pass** (real Chromium: extension SW fetched the page and performed the innertube POST → `status=OK`, 27 formats) | **Pass** (media GET 206; offscreen saver + `chrome.downloads` `state=complete`, 3,638,963 bytes on disk) | Blocked/disabled for this video (no progressive format); the same selector rules apply | Pass (engine/bridge tests) | Pass (shared compiler/selector tests) | `UNSUPPORTED_SOURCE` at the extractor | Pass (shared extractor tests) | 0 | Page made **zero** youtube/googlevideo requests; the extension did the POST and the media GET |

Live harness and oracle (all run 2026-09-24):

- `./gradlew :shared:core:jvmTest --tests "com.anydownlod.core.extract.youtube.YoutubeLiveTest" -PliveExtractorTests=true` → `live youtube: formats=27, needsJs=0, titleLength=14`.
- `./gradlew :apps:desktop:test --tests "com.anydownlod.desktop.engine.YtDlpOracleTest" -PytDlpOracle=true` → `oracle youtube: kotlinFormats=27 oracleFormats=27 diffs=0`; `audio-only: kotlin=10 oracle=10 diffs=0`.
- `./gradlew :apps:desktop:test --tests "com.anydownlod.desktop.engine.DesktopLiveKotlinDownloadTest" -PliveExtractorTests=true` → `state=COMPLETED formats=0 titleLength=14 artifacts=1` (the `.m4a` file was larger than zero bytes; no CLI process).
- Web CDP harness (T-067): `T-067 CDP RUN OK` — watch page fetched, innertube POST OK with 27 formats, media GET 206, offscreen save complete (3,638,963 bytes), page YouTube requests 0.
- Android: `:apps:android-engine-tests:test` green and `:apps:android:assembleDebug` BUILD SUCCESSFUL; the emulator click-through is recorded as blocked because this environment has no AVD/device.
- iOS: `:shared:core:iosSimulatorArm64Test` 9/9 green; `linkDebugFrameworkIosArm64`/`IosSimulatorArm64` and `xcodebuild … iOS Simulator` BUILD SUCCEEDED; the live simulator download is recorded as blocked by the simulator's outbound YouTube access (see T-066).

Coverage: `./gradlew :tools:port-manifest:run :tools:port-manifest:check` regenerated the block and passed; `YoutubeIE` is `partial` with the scope “single video, visionos client, no JS”, and the counts are 0 ported / 2 partial / 0 planned / 1,749 not started over 1,751 upstream classes.

No private URL, cookie, token, or `googlevideo` URL was added; the fixtures and the gate record carry only counts, statuses, and file sizes.
