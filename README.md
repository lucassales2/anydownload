# AnyDownload

**A planned Kotlin Multiplatform app for downloading video and audio from yt-dlp-supported sites on Android, iOS, desktop, and web.**

> **Status: Phase D7 verified (2026-09-25).** The [X/Twitter status video phase](vault/00-project/Phase-7-X-Twitter.md) ([ADR-011](vault/03-decisions/ADR-011-X-twitter-phase.md)) ports `TwitterIE` for a public `x.com`/`twitter.com`/`mobile.x.com`/`mobile.twitter.com` `/user/status/<id>` post: a public guest lookup, videos grouped by stable media id, a preview where the user selects them, and one file per selected video through the shared engine on desktop, Android, iOS, and web. Photo-only, protected, and deleted posts fail typed; no cookies, no vendored `twitter.py`, and no `yt-dlp` process for a matched status URL. Desktop is verified on a redacted fixture plus an opt-in live test (skipped by default); Android and iOS run the JVM-equivalent and simulator fixtures; web carries the lookup and media GET through the extension and the page makes zero X/Twitter requests. Phase D6 remains verified (2026-09-25), D5 (2026-09-25), D4 (2026-09-24), D3, D2, and D1. The yt-dlp pin stays `2026.08.19`.
>
> **Phase D4 status (2026-09-24).** The [extractor core and YouTube single video](vault/00-project/Phase-4-Extractor-Core-and-YouTube.md) ([ADR-008](vault/03-decisions/ADR-008-Extractor-core-and-youtube-phase.md)) runs on every host family: the shared Kotlin extractor core (InfoExtractor/InfoDict/MediaFormat, registry, helpers, format-spec selector, test harness, port manifest) plus YouTube for one public video, first with the JS-less `visionos` client and then with the bundled yt-dlp-ejs 0.8.0 solver on an embedded runtime (Zipline QuickJS 2021-03-27 on desktop/Android/iOS, the page's own JavaScript on web). Desktop, iOS, and Android resolve a matched YouTube URL in Kotlin without the CLI or Chaquopy; web carries every request through the Manifest V3 extension and the page runs the solver itself. HLS/DASH manifests download as one file with no FFmpeg. Installed `yt-dlp` (desktop) and Chaquopy + pinned `yt-dlp==2026.8.19` (Android) still own every URL the registry does not match and are the opt-in oracle. The media toolkit (FFmpeg/MediaMuxer/AVFoundation) landed in D5; single-file behavior stays unchanged. Prior phases: [D3](vault/00-project/Phase-3-Generic-Extractor.md) / [ADR-007](vault/03-decisions/ADR-007-Generic-extractor-phase.md), [D2](vault/00-project/Phase-2-Local-Kotlin-Engine.md) / [ADR-006](vault/03-decisions/ADR-006-Local-http-engine-phase.md), and [D1](vault/00-project/Phase-1-Desktop-MeTube.md) / [ADR-005](vault/03-decisions/ADR-005-Desktop-metube-phase.md).

The product name is **AnyDownload**. The repository is named [`anydownlod`](https://github.com/lucassales2/anydownlod) to match the original project folder.

## Product direction

A local app that ports [yt-dlp](https://github.com/yt-dlp/yt-dlp) to Kotlin and brings [MeTube](https://github.com/alexta69/metube) workflows onto the device. No backend and no app login. Store publication is out of scope; this is a portfolio project.

- Video, audio-only, captions, and thumbnail downloads; format, codec, and quality selection.
- Playlists, channels, batch URL import/export, persistent queues, live progress, cancellation, and retries.
- Completed-download history, file export, custom folders, and output naming templates.
- Cookies for content the user is authorized to access; global options, presets, and security-scoped overrides.
- Channel/playlist subscriptions, clipping, chapter splitting, and SponsorBlock options.
- Platform-specific sharing. The engine and queue live in the app.

These are **planned capabilities, not implemented features**. The [feature-parity matrix](vault/01-product/Feature-parity.md) records the reviewed MeTube baseline, delivery milestones, and deliberate differences. The first vertical slice is one local URL on each target, not full site coverage.

“Any” means **the sites the Kotlin port of yt-dlp can handle**, with yt-dlp's coverage as the goal. Availability varies with site changes, geography, and media restrictions. DRM circumvention is not a goal. Download only content you own or have permission to download, subject to applicable law and platform terms.

## Platform strategy

| Target | Execution (D3) | D3 fixture HTML result (2026-09-24) |
| --- | --- | --- |
| Desktop | Shared Kotlin HTTP engine incl. the generic subset; installed `yt-dlp` CLI stays for unresolved URLs (`apps/desktop` only). | **Pass** — fixture HTML via Kotlin, no process; unresolved page still on the CLI path (yt-dlp 2026.08.19 on PATH). |
| iOS | Shared engine + in-process NSURLSession + sandbox `Documents` store. Foreground-only; unresolved HTML fails typed “extractor not implemented”. | **Pass** — native simulator run downloaded the fixture media into the sandbox; unresolved page fails typed. |
| Android | Shared engine wired in the app graph; Chaquopy + pinned yt-dlp adapter behind an `apps/android`-only port. | **Pass (JVM-equivalent)** — `apps/android-engine-tests` proves the Kotlin route and the Chaquopy fallback. Debug APK assembles with AGP 9.1.1. |
| Web | Compose/Wasm UI. The Manifest V3 extension holds `host_permissions` + `downloads`, fetches the page bytes and saves the media; the page never fetches an origin. | **Pass** — real Chromium (Brave 153) run: extension service worker fetched the fixture page and the media; file saved; without the extension Add refuses. |

### Phase D4: extractor core and YouTube single video

- **Extractor core:** `com.anydownlod.core.extract` (InfoExtractor base, InfoDict/MediaFormat, URL registry, HTTP helper, helpers) with `com.anydownlod.core.format` (format-spec parser/evaluator, typed-options compiler) and `com.anydownlod.core.download` (HLS/DASH parsers, AES-128 in Kotlin, fragment downloader). `port/manifest.json` records every translated module with its upstream path, tag `2026.08.19` (commit `3a08beaf031ab68f966401ead017ac81fe8486cf`), and the Unlicense notice; the generated coverage table lives in the [equivalence note](vault/01-product/Ytdlp-equivalence.md).
- **YouTube:** one public video through the JS-less `visionos` client and, with the runtime, the `web` client; the bundled yt-dlp-ejs 0.8.0 scripts are hash-verified at build time and never downloaded at runtime. No playlists, channels, live streams, comments, subtitles, cookies, PO-token providers, or other sites.
- **Hosts (verified 2026-09-24):**

| Target | Runtime | Formats with runtime | Formats without runtime | Result |
| --- | --- | --- | --- | --- |
| Desktop | Zipline QuickJS 2021-03-27 | 27 (web + visionos, needsJs 0) | 27 (visionos, needsJs 0) | **Pass** — live Kotlin M4A download completed with no CLI process; oracle diffs 0/0/0; unmatched URLs still go to the installed CLI; a local HLS fixture downloads byte-exact as one file. |
| Android | Zipline QuickJS 2021-03-27 | same shared engine | same shared engine | **Pass (JVM-equivalent)** — `apps/android-engine-tests` (14 tests) plus the Debug APK with AGP 9.1.1; no emulator/AVD is available in this environment. |
| iOS | Zipline QuickJS 2021-03-27 | same shared engine | same shared engine | **Pass (fixtures)** — 279 simulator tests; the simulator's NSURLSession cannot reach YouTube (typed timeout, recorded in T-066/T-068), so the live YouTube row is **blocked** with the host live paths proving the code. Foreground-only. |
| Web | The page's own JavaScript (Worker) | 27 via the web client | 27 via visionos | **Pass** — Brave 153.1.95.104 (Chromium 153.0.8010.53): the extension service worker carried every network request, the page ran the solver in its own Worker, the page made **0** YouTube requests, and without the extension Add refuses. 22 extension node tests. |

- **Live and oracle runs (2026-09-24, public video only):** live stage 1 = 27 formats/needsJs 0; live stage 2 = 27 formats/needsJs 0, no regression, QuickJS 2021-03-27; oracle visionos 27/27 diffs 0, web+visionos 27/27 diffs 0, audio-only 10/10 diffs 0. No URLs, cookies, tokens, or media are recorded.
- **Honest limits (recorded, not fixed here):**
  - Single-file quality only: a selection that needs a merge or a transcode fails typed (`UNSUPPORTED_FORMAT`), and MP3/WAV/FLAC stay disabled until the media toolkit exists. No FFmpeg, MediaMuxer, or AVFoundation postprocessing is added in D4.
  - PO-token-gated formats are dropped and counted as needing JavaScript; playlists, channels, live streams, comments, subtitles, and cookies are not ported.
  - iOS downloads are foreground-only, and the iOS simulator cannot reach YouTube here; the live evidence is the host live paths.
  - Web download progress is the browser downloader's; MV3 `fetch` refuses forbidden request-header names (the extension reports the effective names); the Compose/Wasm page never fetches an origin.
  - The embedded QuickJS (2021-03-27) is older than the version yt-dlp-ejs recommends (2025-04-26); solve time varies per host and is bounded by the runtime timeout. YouTube changes faster than the pin; the port is pinned to yt-dlp `2026.08.19`.
- **Commands:** `./gradlew :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :apps:android-engine-tests:test :shared:core:iosSimulatorArm64Test :apps:android:assembleDebug :tools:port-manifest:check`, opt-in live `-PliveExtractorTests=true`, opt-in oracle `-PytDlpOracle=true`, extension tests `node --test apps/web-extension/test/bridge.test.mjs`, and the wasm production bundle `:apps:web:wasmJsBrowserDistribution`.

### Phase D5: media toolkit

- **Contract and engine:** `com.anydownlod.core.postprocess.MediaToolkit` (merge, extract audio, capabilities) in common code. `HttpDownloadEngine` downloads one video stream and one audio stream to temps, merges them through the host toolkit, and publishes one artifact; audio extraction downloads the best audio stream and writes the requested container. The typed-options compiler emits at most one `+` merge and only when the host says it can merge. A missing toolkit fails typed (`UNSUPPORTED_FORMAT`), never a silent one-stream save.
- **Hosts (verified 2026-09-25):**

| Target | Tool | Merge | M4A / Opus | MP3 / WAV / FLAC | Result |
| --- | --- | --- | --- | --- | --- |
| Desktop | `ffmpeg`/`ffprobe` on `PATH` | Stream copy to one MP4 | Stream copy | Transcode, `ffprobe`-checked | **Pass** — a local split fixture merges to one probed file; an opt-in public YouTube merge completed with one artifact and no yt-dlp process; MP3, WAV, and FLAC were produced from a local fixture. |
| Android | `MediaMuxer`/`MediaExtractor` | Remux | Stream copy (Opus in WebM) | Disabled | **Pass (JVM-equivalent)** — fake-port tests plus `assembleDebug` and a compiled instrumented test; no emulator/AVD exists here. |
| iOS | `AVFoundation` (`AVAssetExportPresetPassthrough`) | Passthrough remux | M4A copy; Opus copies to CAF | Disabled | **Pass (simulator)** — local fixtures merged with one video and one audio track; no live YouTube and no subprocess. |
| Web | none | Disabled, host gap | Only a native single-file stream; no toolkit extract | Disabled, host gap | **Pass** — Edit disables the merge-only height with the host-gap reason, and the page ships no process or platform muxer (node check). |

- **Honest limits (recorded, not fixed here):**
  - Web has no merge and no transcode; those choices stay disabled with the host-gap reason. A native M4A or Opus stream still downloads as a single file.
  - Android and iOS keep MP3, WAV, and FLAC disabled: `MediaMuxer` writes only MP4/WebM and has no PCM/WAV muxer, and `AVAssetExportSession` has no WAV/PCM preset. No third-party encoder is bundled.
  - iOS Opus copies into a Core Audio Format file because AVFoundation has no Ogg writer; the `.opus`/Ogg container is not produced.
  - Android platform muxers reject some codec pairs FFmpeg would remux, so the same URL can succeed on desktop and fail typed on a phone. That is the capability query, not a silent downgrade.
  - E-05 and E-17 are Partial in the [equivalence note](vault/01-product/Ytdlp-equivalence.md); [T-013](vault/06-tasks/T-013-Media-formats.md) stays open. Captions, thumbnails, embed metadata, clips, chapters, SponsorBlock, playlists, live, cookies, and X/Twitter stay out.
- **Commands:** `./gradlew :shared:core:jvmTest :shared:core:iosSimulatorArm64Test :shared:ui:jvmTest :shared:ui:iosSimulatorArm64Test :apps:desktop:test :apps:android-engine-tests:test :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin :tools:port-manifest:check`, `CHROME_BIN=… ./gradlew :shared:core:wasmJsBrowserTest`, `node --test apps/web-extension/test/bridge.test.mjs`, and the opt-in live merge `./gradlew :apps:desktop:test -PliveExtractorTests=true --tests "…DesktopLiveKotlinMergeTest"`.

### Phase D6: spotDL parity

- **Operations:** `com.anydownlod.core.music` resolves public Spotify metadata (track, album, playlist, artist, and text search) with the unauthenticated client by default and the official Web API only when a stored client id and secret exist; `AudioMatcher` matches YouTube Music then YouTube (SoundCloud, Bandcamp, Piped, and slider.kz are opt-in fallbacks); `SpotifyDownloadService` queues child jobs through the existing engine, embeds title/artists/album/artwork through the D5 toolkit, and adds lyrics, `.spotdl` save/load, url, meta, sync, and the user library behind an on-device Spotify login. No spotDL package, no Python, no `spotdl` subprocess, and no Spotify audio stream.
- **Hosts (verified 2026-09-25):**

| Target | Public Spotify download | save / url / meta | sync | Library login |
| --- | --- | --- | --- | --- |
| Desktop | **Pass** — a public fixture track becomes a tagged MP3 inside the root; `ffprobe` shows title/artist/album and the cover. No live track was used (the anonymous Spotify Web API is quota-limited from this network); the gate allows an opt-in live URL. | **Pass** — `.spotdl` write/reload, one URL per song, and in-place FFmpeg retag on a fixture file with no media fetch. | **Pass** — fixture add/delete, sync-without-deleting, foreign files untouched, and the LRC sibling removed with its audio. | **Pass** — Settings login stores the token on the device, Logout deletes it, and fixture-token queries resolve. |
| Android | **Pass (JVM-equivalent)** — the shared tests run on the JVM and the public Spotify path is wired through the shared engine; `assembleDebug` passes. No emulator/AVD exists here. Tags are a gap (no tag writer). | **Gap** — no on-device list store is wired yet; the shared tests cover the logic. | **Gap** — same. | **Gap** — no on-device token store is wired yet; the query fails typed. |
| iOS | **Pass (simulator)** — the shared tests run on the simulator and the public path is wired through the AVFoundation host; no live YouTube. Tags are a gap. Foreground-only. | **Gap** — no on-device list store is wired yet. | **Gap** — same. | **Gap** — no on-device token store is wired yet. |
| Web | **Gap** — the public path is wired through the extension and the page makes 0 Spotify/YouTube requests (node check), but the Spotify flow has no browser end-to-end run. Tags and toolkit extract are gaps. | **Gap** — no list store on web. | **Gap** — same. | **Gap** — no token store on web. |

- **Honest limits (recorded, not fixed here):**
  - The unauthenticated Spotify client uses the anonymous session the public embed page carries and the public Web API; the API is rate-limited or blocked on some networks, so a live metadata run is not guaranteed. The official Web API is opt-in with a stored client id and secret.
  - The Settings login stores a token the user supplies from their own Spotify login; the full browser PKCE redirect capture is a host follow-up. No AnyDownload account exists.
  - Spotify audio streams, previews, and DRM are never a source; the audio comes from the matched YouTube Music/YouTube/fallback URL.
  - Mobile and web do not embed tags or lyrics yet (no toolkit writer or no list/token store), so the audio is kept and the job records `tagsEmbedded=false`/`lyricsEmbedded=false`. The four-host gaps are in the table above.
  - The archive is written when songs are queued, not after every download completes. OPUS/native-container jobs keep the engine's own name because the extension is only known after extraction; the templated path applies to MP3 and M4A.
  - Fallback providers are opt-in (`AppSettings.spotifyFallbackProviders`, empty by default); slider.kz's live service is shut down, so it normally misses.
- **Commands:** the T-083 list above plus `node --test apps/web-extension/test/bridge.test.mjs`; the Spotify tests live in `shared/core/src/commonTest/kotlin/com/anydownlod/core/music` and the desktop gate in `apps/desktop/src/jvmTest/kotlin/com/anydownlod/desktop/engine/DesktopSpotifyDownloadGateTest.kt`.

### Phase D7: X/Twitter status video

- **Extractor:** `com.anydownlod.core.extract.twitter.TwitterIE` translates the single-status media subset of `yt_dlp/extractor/twitter.py` at tag `2026.08.19`. It uses the public `cdn.syndication.twimg.com/tweet-result` guest lookup (no cookie, no bearer, no stored guest token), maps the status's `mediaDetails` videos into `InfoDict.media` grouped by stable media id, and fails typed for photo-only, protected, and deleted posts. Photos, quoted tweets, cards, Spaces, broadcasts, profiles, `t.co`, and the GraphQL/legacy API stay out; `twitter.py` is not vendored.
- **Preview and download:** the preview lists one selectable row per video (first preselected), and `DownloadRequest.selectedMediaIds` carries the selection. The engine re-extracts at download time, resolves each selected media's format, and writes one file per selected video inside the download root on the status job; an empty or stale selection fails typed before any media request. The Edit panel keeps choosing quality.
- **Hosts (verified 2026-09-25):**

| Target | Preview + selection | Download | No CLI / no cookies |
| --- | --- | --- | --- |
| Desktop | **Pass** — a fixture status lists its two videos; the first is preselected. | **Pass** — both selected videos land as two files; the unselected URL is never fetched; the opt-in live test exists and was skipped (no URL configured). | **Pass** — the route is KOTLIN and zero `yt-dlp` processes start. |
| Android | **Pass (JVM-equivalent)** — `AndroidXStatusTest` previews the two stable ids through the shared engine; no emulator/AVD exists here. | **Pass (JVM-equivalent)** — two files with the fixture bytes; the Chaquopy port receives nothing. | **Pass** — the registry match routes to KOTLIN before any probe. |
| iOS | **Pass (simulator)** — `IosXStatusTest` previews the two stable ids through the shared engine and the sandbox `IosFileStore`. | **Pass (simulator)** — two files with the fixture bytes; no live X/Twitter. Foreground-only. | **Pass** — no Python or CLI on iOS. |
| Web | **Pass** — the page lists the videos and `WebAppGraph` registers `TwitterIE` over the extension request port. | **Pass (wasm + node fakes)** — one file per selected video through `chrome.downloads`; a browser end-to-end UI run is the same gap as D6. | **Pass** — the page makes zero X/Twitter/syndication fetches; the extension drops the MV3-forbidden `user-agent`, and no cookie or authorization rides the lookup. |

- **Honest limits (recorded, not fixed here):**
  - Only the public single-status video path is ported. Photos, threads and quoted posts, cards (including a card that is only a YouTube link), Spaces, broadcasts, profiles, `t.co`, and every other class in `twitter.py` are out.
  - The lookup is the public syndication endpoint; if it changes shape or rate-limits, the extractor fails typed. No cookies and no login are offered, so a protected or login-only post stays a typed failure.
  - The live X/Twitter check is opt-in on desktop only (`-PxStatusUrl=<public status>`) and never runs in default CI. Android, iOS, and web run redacted fixtures.
  - Web drops the declared `user-agent` (MV3 forbidden) and has no browser end-to-end X/Twitter run; the wasm and node fixtures are the web evidence.
- **Commands:** `./gradlew :shared:core:jvmTest :shared:ui:jvmTest :apps:desktop:test :shared:core:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test :apps:android-engine-tests:test :apps:android:assembleDebug :apps:android:compileDebugAndroidTestKotlin :apps:web:wasmJsBrowserDistribution :tools:port-manifest:check`, `CHROME_BIN=… ./gradlew :shared:core:wasmJsBrowserTest`, `node --test apps/web-extension/test/bridge.test.mjs`, and the opt-in live status `./gradlew :apps:desktop:test -PxStatusUrl=<public status> --tests "…DesktopXStatusLiveTest"`.

### Phase D3: one generic-extractor subset

- **Home screen:** the idle screen is the paste-link field alone — no clipboard permission dialog, no automatic clipboard read, no options chrome. A compatible HTTP(S) link opens the metadata preview with **Download** plus a collapsible **Edit download** (video or audio, quality, format; captions, clips, cookies, and the disabled custom yt-dlp JSON stay out). Invalid and multi-line input stays on the field with a typed message.
- **Extractor:** `com.anydownlod.core.extract.GenericExtractor` (package `shared/core/src/commonMain/kotlin/com/anydownlod/core/extract`) is a translated subset of yt-dlp's `generic.py` at tag `2026.08.19` (the Android pin `yt-dlp==2026.8.19`), with the Unlicense notice and `shared/core/NOTICE.md`. `<video src>`, `<audio src>`, and nested `<source src>` candidates resolve against the page URL, pass `UrlPolicy`, and exactly one survivor wins; zero or several fail typed and redacted. `generic.py` is not vendored; no process and no Python in common code.
- **Engine:** `HttpDownloadEngine` reads a bounded 512 KiB page body, runs the extractor, then streams the chosen media URL with the same direct-file path (policy on every hop, redirects, cancel discards the temp file). The media hop never re-extracts; HTML again fails typed.
- **Honest limits (recorded, not fixed here):**
  - iOS downloads are foreground-only; suspending the app suspends the transfer and no completed file is claimed on relaunch. No background `URLSession` is added in this phase.
  - On web, closing the tab ends the page's queue view of the job; the browser's downloader (started through the extension) can continue and finish the file after the tab closes, so a saved file can outlive the tab. The page shows completion only while it can still hear the extension.
  - The desktop classifier fetches up to one bounded page body per submit to route HTML; a HEAD-only host that breaks on GET still falls back to the CLI path.
  - Web request port (`HttpTransfer` over the extension, T-056): the extension applies the same request-header allowlist, but MV3 `fetch` refuses forbidden names — `Origin`, `Referer`, `User-Agent`, `Cookie`, `Host`, `Content-Length`, `Accept-Encoding`, `Sec-*`, and `Proxy-*`. The extension reports the effective header set it actually sent (names only) so the extractor can report the difference; the page still never fetches an origin itself. For YouTube's innertube `POST`, MV3 forces `Origin` to the extension origin and YouTube answers 403 to any non-YouTube origin, so a static `declarativeNetRequest` rule (`apps/web-extension/rules.json`) sets `Origin: https://www.youtube.com` and the visionos user agent on `youtube.com/youtubei/*`. Web download progress is the browser downloader's: the page shows unknown progress until `chrome.downloads` reports completion. The page runs the bundled EJS solver in its own JavaScript (a Worker built from a Blob URL, T-072) with the CSP `default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; worker-src 'self' blob:; connect-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'` — no remote script origin, and the extension never runs the solver.
  - Chaquopy config stays opt-in (`-DchaquopyVersion=17.0.0`, `yt-dlp==2026.8.19`), `apps/android` only. The JVM-equivalent `apps/android-engine-tests` suite is the Android engine evidence.
- **Later, explicitly:** the rest of the generic extractor and site extractors, YouTube / yt-dlp-ejs, merge / audio extraction / clips (toolkit recorded in ADR-007, not built), Spotify matching, free-form yt-dlp JSON, and retiring the desktop CLI / Chaquopy.

Toolchain (verified 2026-09-24, macOS 26.5.2 arm64): Gradle 9.7.1, Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.1.1, JDK 21, Xcode 26.5, Android `compileSdk` 37 / `minSdk` 26, iOS 16 deployment target, Chromium 153 (Brave 153.1.95.104) for the extension check, installed `yt-dlp` 2026.08.19 on PATH for the desktop CLI fallback.

The later engine is shared Kotlin and does not shell out to the Python yt-dlp CLI ([ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md)). Until that port exists, the desktop D1 adapter calls the installed CLI from `apps/desktop` only.

[`YtDlp-kt`](https://github.com/dinaraparanid/YtDlp-kt) was reviewed: it is archived, JVM-only, GPL-3.0, and depends on an external CLI. It is not a drop-in shared KMP engine. See the [upstream review](vault/05-research/Upstream-review.md).

## How D1 is put together

`shared/core` owns the domain, the `DownloadEngine` / `SubscriptionRepository` / `SettingsRepository` interfaces, validation, and the in-memory fakes every host can run. `shared/ui` owns the Compose screens and depends only on those interfaces. `apps/desktop` owns the JSON store and the only `ProcessBuilder` use, in `com.anydownlod.desktop.engine`; it resolves `yt-dlp` and `ffmpeg` from `PATH` and never bundles them. `shared/network` remains in the tree but is not part of the app path. The in-process Kotlin engine from [ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md) is still the later target; do not move the desktop process adapter into shared code.

| Module | Purpose |
| --- | --- |
| `shared/core` | Domain, `DownloadEngine` / repository interfaces, validation, **`HttpDownloadEngine`** + URL policy/classifier + **`GenericExtractor`** (yt-dlp subset, Unlicense notice), platform ports (`HttpTransfer`, `FileStore`, [`WebExtensionBridge`]), in-memory fakes |
| `shared/network` | Withdrawn server client. Kept in the tree, unused |
| `shared/ui` | Compose Multiplatform screens; depends on core interfaces, no process APIs |
| `apps/android` | Android application: real graph (HTTP engine + Chaquopy port) |
| `apps/android-engine-tests` | JVM-equivalent tests for the Android engine sources (variant-unblocked) |
| `apps/desktop` | Desktop host: JSON store, yt-dlp/ffmpeg adapter, routing engine, Compose window |
| `apps/web` | Compose/Wasm application: `WindowExtensionBridge` + `WebExtensionEngine`; never fetches an origin |
| `apps/web-extension` | Manifest V3 extension: host permissions + `downloads`, probes pages, fetches the HTML for the Kotlin extractor, saves the chosen media |
| `apps/ios` | SwiftUI/Xcode host for the `AnyDownloadKit` framework; real iOS graph |

Provisional pinned toolchain: Gradle 9.7.1 (wrapper, checksum-pinned), Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.1.1, Ktor 3.6.0, JDK 21, Android `compileSdk` 37 / `minSdk` 26, iOS 16 deployment target. Minimum platform versions are **not decided**; T-004 and T-006 own that. Intel iOS simulators are unsupported because Compose Multiplatform 1.12 no longer publishes an `iosX64` variant.

### Run the desktop app

`yt-dlp` and `ffmpeg` must be installed and on `PATH`; nothing is bundled. Downloads write under the folder chosen in Settings (default `~/Downloads/AnyDownload`). Queue, history, subscriptions, and settings persist outside the download root in the OS app-data folder (`~/Library/Application Support/AnyDownload` on macOS).

```sh
./gradlew :apps:desktop:run
./gradlew :apps:desktop:test    # store, engine, parser, and headless UI tests
```

```sh
./gradlew :shared:core:jvmTest                       # shared engine + platform tests
./gradlew :shared:core:iosSimulatorArm64Test         # native iOS tests (runs in the simulator)
./gradlew :apps:desktop:test                         # desktop store/engine/UI tests
./gradlew :apps:android-engine-tests:test            # JVM-equivalent Android engine tests
./gradlew :tools:port-manifest:run                   # regenerate the extractor coverage block from port/manifest.json
./gradlew :tools:port-manifest:check                 # validate port/manifest.json and the coverage block
node --test apps/web-extension/test/bridge.test.mjs  # extension logic tests
node tools/fixture-server/server.mjs --port 8123      # opt-in local fixture server for the live native/desktop checks
./gradlew :apps:web:wasmJsBrowserDevelopmentRun      # browser app (install the extension for downloads)
./gradlew :apps:web:wasmJsBrowserDistribution        # production web bundle
./gradlew :apps:android:assembleDebug                # blocked in this env (AGP/Compose mismatch, see above)
xcodebuild -project apps/ios/AnyDownload.xcodeproj -scheme AnyDownload \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Android builds need `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`. Regenerate `apps/ios/AnyDownload.xcodeproj` with [XcodeGen](https://github.com/yonaskolb/XcodeGen) after changing `apps/ios/project.yml`.

## Documentation

| Start here | Purpose |
| --- | --- |
| [Vault home](vault/Home.md) | Documentation index and current priorities |
| [Kanban board](vault/Kanban.md) | Task status; each card links to acceptance criteria and dependencies |
| [Product brief](vault/01-product/Product-brief.md) | Goals, audience, scope, and success criteria |
| [MeTube feature parity](vault/01-product/Feature-parity.md) | Traceable feature inventory |
| [Architecture](vault/02-architecture/Architecture.md) | Local engine and the withdrawn remote design |
| [Platform matrix](vault/02-architecture/Platform-matrix.md) | What can be shared and what must be platform-specific |
| [Roadmap](vault/00-project/Roadmap.md) | Milestones and exit gates, without invented deadlines |
| [Open questions](vault/00-project/Open-questions.md) | Accepted decisions and what is still open |
| [Security and licensing](vault/04-delivery/Security-and-licensing.md) | Trust boundaries, privacy, dependency licenses, and distribution risks |

## Open the Obsidian vault

1. Clone the repository:
   ```sh
   git clone https://github.com/lucassales2/anydownlod.git
   cd anydownlod
   ```
2. In [Obsidian](https://obsidian.md), choose **Open folder as vault** and select this repository's **`vault/`** folder, not the repository root.
3. Open **`Home.md`** for the documentation index.
4. For the visual task board, go to **Settings → Community plugins**, enable community plugins for this vault if you trust them, and install/enable **Kanban** (`obsidian-kanban`, originally by mgmeyers, now maintained under the Obsidian community archive).
5. Open **`Kanban.md`**. If it opens as text, use the command palette's Kanban board-view command.

The board also works as an ordinary Markdown checklist on GitHub or in any editor. The checked-in plugin list does **not** install plugin binaries on a fresh clone. Plugin code and machine-specific workspaces are intentionally excluded from Git.

See the [documentation guide](vault/00-project/Documentation-guide.md) for board rules, templates, and privacy precautions.

## Repository layout

```text
README.md
CONTRIBUTING.md
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/                    # Wrapper and version catalog (libs.versions.toml)
port/                      # Port manifest and the upstream extractor name list (data only)
tools/port-manifest/       # Build-time Gradle task: validate the manifest, generate the coverage block
shared/
  core/                    # Domain, interfaces, validation, in-memory fakes
  network/                 # Withdrawn server client (unused by D1)
  ui/                      # Compose Multiplatform screens and iOS framework
apps/
  android/                 # Android application (fake-backed shell)
  desktop/                 # Desktop host: JSON store, yt-dlp adapter, window
  web/                     # Compose/Wasm application (fake-backed shell)
  ios/                     # SwiftUI/Xcode host for AnyDownloadKit (fake-backed shell)
vault/                     # Open this folder as an Obsidian vault
  Home.md
  Kanban.md
  00-project/              # Roadmap, questions, documentation workflow, glossary
  01-product/              # Product scope, parity, user journeys
  02-architecture/         # Platform constraints, domain, API proposal
  03-decisions/            # Architecture decision records; ADR-004 is accepted
  04-delivery/             # Testing, risks, security, licensing
  05-research/             # Dated upstream findings and pinned references
  06-tasks/                # One note per task
  99-templates/            # Task, ADR, and research templates
  .obsidian/               # Portable vault settings only
```

Treat module names, the `com.anydownlod` package, and pinned versions as provisional; minimum platform versions are still open. Phases D1 through D7 are complete: desktop runs the MeTube workflows through an installed yt-dlp, every host downloads one direct file, one generic-extractor subset runs on all four families, the extractor core plus YouTube single video runs with and without the embedded JavaScript runtime, the media toolkit merges a split video/audio pair and copies or converts audio on desktop, Android, and iOS, D6 implements spotDL v4.5.2 parity ([ADR-010](vault/03-decisions/ADR-010-Spotdl-parity-phase.md)), and D7 ports the X/Twitter status video on all four families ([ADR-011](vault/03-decisions/ADR-011-X-twitter-phase.md)) with the opt-in live status and the web end-to-end gaps recorded ([ADR-004](vault/03-decisions/ADR-004-Local-kotlin-engine.md) remains the end state). The [T-022](vault/06-tasks/T-022-Parity-audit.md) parity audit and the target work in [T-004](vault/06-tasks/T-004-Validate-KMP-targets.md) still stand. Do not move the desktop process adapter into shared code.

## Contributing and licensing

Read [CONTRIBUTING.md](CONTRIBUTING.md). The Obsidian board is the task-status source of truth; GitHub Issues may be linked later, but are not automatically synchronized.

This project is licensed under the [MIT License](LICENSE). Copyright © 2026 Lucas Sales.

MIT covers this repository. It does not relicense MeTube, yt-dlp, YtDlp-kt, FFmpeg, or other upstream projects. Do not copy their implementation into this tree until their obligations are recorded. This project is not affiliated with them.
